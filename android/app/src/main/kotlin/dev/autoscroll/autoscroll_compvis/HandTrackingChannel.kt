package dev.autoscroll.autoscroll_compvis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import io.flutter.FlutterInjector
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class HandTrackingChannel(
    private val context: Context,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler {
    private val channel = MethodChannel(messenger, "autoscroll/hand_tracking")
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private var landmarker: HandLandmarker? = null
    private var lastTimestamp = -1L
    private var closed = false

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (closed) {
            result.error("closed", "Tracking channel is closed", null)
            return
        }
        if (call.method !in setOf("initialize", "detect", "dispose")) {
            result.notImplemented()
            return
        }
        // The Dart side gates frames too. Defend the native boundary against other callers.
        if (call.method == "detect" && !busy.compareAndSet(false, true)) {
            result.error("busy", "Drop this frame; inference is already running", null)
            return
        }
        worker.execute {
            try {
                val response: Any? = when (call.method) {
                    "initialize" -> { initialize(call); null }
                    "detect" -> detect(call)
                    else -> { release(); null }
                }
                main.post { result.success(response) }
            } catch (error: Exception) {
                fail(result, error)
            } catch (error: LinkageError) {
                fail(result, error)
            } finally {
                if (call.method == "detect") busy.set(false)
            }
        }
    }

    private fun fail(result: MethodChannel.Result, error: Throwable) {
        Log.e("HandTracking", "Native tracking failed", error)
        val message = error.cause?.message ?: error.message ?: error.javaClass.simpleName
        main.post { result.error("hand_tracking", message, null) }
    }

    private fun confidence(call: MethodCall, key: String): Float {
        val value = requireNotNull(call.argument<Number>(key)).toFloat()
        require(value.isFinite() && value in 0f..1f) { "Invalid $key" }
        return value
    }

    private fun initialize(call: MethodCall) {
        release()
        val asset = FlutterInjector.instance().flutterLoader()
            .getLookupKeyForAsset("assets/models/hand_landmarker.task")
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(asset).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .setMinHandDetectionConfidence(confidence(call, "detectionConfidence"))
            .setMinHandPresenceConfidence(confidence(call, "presenceConfidence"))
            .setMinTrackingConfidence(confidence(call, "trackingConfidence"))
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    private fun detect(call: MethodCall): Map<String, Any?> {
        val tracker = checkNotNull(landmarker) { "Initialize tracking first" }
        val width = requireNotNull(call.argument<Int>("width"))
        val height = requireNotNull(call.argument<Int>("height"))
        require(width in 1..1920 && height in 1..1920) { "Unsupported frame size" }
        val rotation = requireNotNull(call.argument<Int>("rotation"))
        require(rotation in setOf(0, 90, 180, 270)) { "Invalid rotation" }
        val timestamp = requireNotNull(call.argument<Number>("timestampMs")).toLong()
        require(timestamp >= 0 && timestamp > lastTimestamp) { "Timestamps must increase" }
        val planes = requireNotNull(call.argument<List<Map<String, Any>>>("planes"))
        val pixels = YuvFrame.toArgb(width, height, planes)
        val raw = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        var upright: Bitmap? = null
        try {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            upright = Bitmap.createBitmap(raw, 0, 0, width, height, matrix, false)
            val image = BitmapImageBuilder(upright).build()
            try {
                val detection = tracker.detectForVideo(image, timestamp)
                lastTimestamp = timestamp
                val landmarks = detection.landmarks().firstOrNull().orEmpty().map { point ->
                    // x is mirrored to match the selfie preview; y is upright.
                    listOf(
                        (1.0 - point.x().toDouble()).coerceIn(0.0, 1.0),
                        point.y().toDouble().coerceIn(0.0, 1.0),
                        point.z().toDouble(),
                    )
                }
                val handedness = detection.handedness().firstOrNull()?.firstOrNull()
                return mapOf(
                    "landmarks" to landmarks,
                    "handedness" to handedness?.categoryName(),
                    "handednessConfidence" to handedness?.score()?.toDouble(),
                )
            } finally {
                image.close()
            }
        } finally {
            if (upright != null && upright !== raw) upright.recycle()
            raw.recycle()
        }
    }

    private fun release() {
        landmarker?.close()
        landmarker = null
        lastTimestamp = -1L
    }

    fun close() {
        if (closed) return
        closed = true
        channel.setMethodCallHandler(null)
        worker.execute { release() }
        worker.shutdown()
    }
}
