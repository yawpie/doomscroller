package dev.autoscroll.nativeapp.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.Display
import androidx.annotation.MainThread
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import dev.autoscroll.nativeapp.gesture.HandTrackingResult
import dev.autoscroll.nativeapp.gesture.HandPose
import dev.autoscroll.nativeapp.mediapipe.HandLandmarkerEngine
import dev.autoscroll.nativeapp.mediapipe.InferenceMetrics
import dev.autoscroll.nativeapp.mediapipe.LatencyWindow
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One analysis pipeline. Callbacks are delivered on main; create a new instance after close. */
class CameraController(
    context: Context,
    private val config: TrackingConfig,
    private val onResult: (HandTrackingResult) -> Unit,
    private val onMetrics: (InferenceMetrics) -> Unit,
    private val onError: (Throwable) -> Unit,
) : AutoCloseable {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(2), { runnable -> Thread(runnable, "HandInference") })
    private val pendingResult = AtomicReference<FrameResult?>(null)
    private val deliveryPosted = AtomicBoolean(false)
    @Volatile private var closed = false
    private var started = false
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var cameraState: androidx.lifecycle.LiveData<CameraState>? = null
    private val displays = this.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var observingDisplay = false
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (!closed && displayId == Display.DEFAULT_DISPLAY) analysis?.targetRotation = displayRotation()
        }
    }
    private val cameraObserver = Observer<CameraState> { state ->
        state.error?.let { fail(IllegalStateException("Camera error ${it.code}", it.cause)) }
    }
    private var engine: HandLandmarkerEngine? = null
    private var lastAcceptedNs = -1L
    private var windowStartedNs = 0L
    private var deliveredInWindow = 0L
    private var processedInWindow = 0L
    private var cameraFps = 0.0
    private var inferenceFps = 0.0
    private var throttledFrames = 0L
    private var processedFrames = 0L
    private var staleFrames = 0L
    private val latencies = LatencyWindow()
    private var latencySummary = LatencyWindow.Summary()
    private var lastSummaryNs = -1L
    private var lastMetricsMs = 0L
    private var cleanupComplete = false
    private val closeCallbacks = mutableListOf<() -> Unit>()

    internal data class FrameResult(val pose: HandPose?, val metrics: InferenceMetrics) {
        fun resultAt(nowMs: Long, lostAfterMs: Long) = HandTrackingResult(metrics.inputTimestampMs,
            if (nowMs - metrics.inputTimestampMs < lostAfterMs) pose else null)
    }

    @MainThread
    fun start(owner: LifecycleOwner) {
        checkMainThread()
        check(!closed) { "Camera controller is closed" }
        if (started) return
        started = true
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            fail(IllegalStateException("Camera lifecycle is destroyed"))
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail(SecurityException("Camera permission is required"))
            return
        }
        worker.execute {
            try {
                if (closed) return@execute
                engine = HandLandmarkerEngine(context, config)
                main.post { if (!closed) bindCamera(owner) }
            } catch (error: Exception) {
                fail(error)
            } catch (error: LinkageError) {
                fail(error)
            }
        }
    }

    private fun bindCamera(owner: LifecycleOwner) {
        try {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (closed) return@addListener
                try {
                    check(owner.lifecycle.currentState != Lifecycle.State.DESTROYED) { "Camera lifecycle is destroyed" }
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    check(cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) { "No front-facing camera" }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .setTargetRotation(displayRotation())
                        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(Size(config.analysisWidth, config.analysisHeight),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                        .build()
                    analysis = imageAnalysis
                    imageAnalysis.setAnalyzer(worker, ::analyze)
                    val camera = cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)
                    displays.registerDisplayListener(displayListener, main)
                    observingDisplay = true
                    cameraState = camera.cameraInfo.cameraState.also { it.observe(owner, cameraObserver) }
                } catch (error: Exception) {
                    fail(error)
                } catch (error: LinkageError) {
                    fail(error)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (error: Exception) {
            fail(error)
        }
    }

    private fun analyze(frame: ImageProxy) {
        try {
            if (closed) return
            val inputNs = SystemClock.elapsedRealtimeNanos()
            if (windowStartedNs == 0L) windowStartedNs = inputNs
            deliveredInWindow++
            if (lastAcceptedNs >= 0 && inputNs - lastAcceptedNs < config.frameIntervalNs) {
                throttledFrames++
                return
            }
            lastAcceptedNs = inputNs
            // ponytail: preserve nearest-neighbor sampling; upgrade only after device accuracy measurements.
            val sample = (maxOf(frame.width, frame.height) + config.maximumInferenceDimension - 1) /
                config.maximumInferenceDimension
            val argb = YuvFrame.toUprightArgb(frame.width, frame.height,
                frame.planes.map { YuvFrame.Plane(it.buffer, it.rowStride, it.pixelStride) },
                sample, frame.imageInfo.rotationDegrees)
            val bitmap = Bitmap.createBitmap(argb.pixels, argb.width, argb.height, Bitmap.Config.ARGB_8888)
            val pose = try {
                checkNotNull(engine).detect(bitmap, inputNs / 1_000_000)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            val completionNs = SystemClock.elapsedRealtimeNanos()
            val latencyMs = (completionNs - inputNs) / 1_000_000.0
            latencies.add(latencyMs)
            if (lastSummaryNs < 0 || completionNs - lastSummaryNs >= 1_000_000_000L) {
                latencySummary = latencies.summary()
                lastSummaryNs = completionNs
            }
            val stale = latencyMs >= config.lostAfterMs
            if (stale) staleFrames++
            processedFrames++
            processedInWindow++
            val windowNs = completionNs - windowStartedNs
            if (windowNs >= 1_000_000_000L) {
                cameraFps = deliveredInWindow * 1_000_000_000.0 / windowNs
                inferenceFps = processedInWindow * 1_000_000_000.0 / windowNs
                windowStartedNs = completionNs
                deliveredInWindow = 0
                processedInWindow = 0
            }
            if (closed) return
            val completionMs = completionNs / 1_000_000
            pendingResult.set(FrameResult(if (stale) null else pose,
                InferenceMetrics(cameraFps, inferenceFps, latencyMs, frame.imageInfo.timestamp,
                    inputNs / 1_000_000, completionMs, throttledFrames, processedFrames, staleFrames,
                    latencySummary.average, latencySummary.p50, latencySummary.p95, latencySummary.samples)))
            postDelivery()
        } catch (error: Exception) {
            fail(error)
        } catch (error: LinkageError) {
            fail(error)
        } finally {
            frame.close()
        }
    }

    private fun postDelivery() {
        if (!closed && deliveryPosted.compareAndSet(false, true)) main.post {
            val sample = pendingResult.getAndSet(null)
            deliveryPosted.set(false)
            if (!closed && sample != null) {
                val now = SystemClock.elapsedRealtime()
                onResult(sample.resultAt(now, config.lostAfterMs))
                if (!closed && (lastMetricsMs == 0L || now - lastMetricsMs >= 1000)) {
                    lastMetricsMs = now
                    onMetrics(sample.metrics)
                }
            }
            if (pendingResult.get() != null) postDelivery()
        }
    }

    private fun fail(error: Throwable) {
        Log.e("HandInference", "Camera/MediaPipe failed", error)
        main.post {
            if (!closed) {
                close()
                onError(error)
            }
        }
    }

    @MainThread
    override fun close() = closeInternal(null)

    /** Completion runs on main after the inference worker has released MediaPipe. */
    @MainThread
    fun close(onClosed: () -> Unit) = closeInternal(onClosed)

    private fun closeInternal(onClosed: (() -> Unit)?) {
        checkMainThread()
        if (cleanupComplete) {
            onClosed?.invoke()
            return
        }
        if (onClosed != null) closeCallbacks.add(onClosed)
        if (closed) return
        closed = true
        pendingResult.set(null)
        if (observingDisplay) displays.unregisterDisplayListener(displayListener)
        observingDisplay = false
        cameraState?.removeObserver(cameraObserver)
        cameraState = null
        try {
            analysis?.clearAnalyzer()
        } catch (error: Exception) {
            Log.e("HandInference", "Analyzer cleanup failed", error)
        } finally {
            try {
                analysis?.let { provider?.unbind(it) }
            } catch (error: Exception) {
                Log.e("HandInference", "Camera cleanup failed", error)
            } finally {
                analysis = null
                provider = null
                worker.execute {
                    try {
                        engine?.close()
                    } catch (error: Exception) {
                        Log.e("HandInference", "MediaPipe cleanup failed", error)
                    } finally {
                        engine = null
                        main.post {
                            cleanupComplete = true
                            val callbacks = closeCallbacks.toList()
                            closeCallbacks.clear()
                            callbacks.forEach { it() }
                        }
                    }
                }
                worker.shutdown()
            }
        }
    }

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper()) {
        "Camera start/close must run on the main thread"
    }

    private fun displayRotation(): Int = displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
}
