package dev.autoscroll.nativeapp.session

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.autoscroll.nativeapp.GestureApplication
import dev.autoscroll.nativeapp.MainActivity
import dev.autoscroll.nativeapp.accessibility.AccessibilityScrollAdapter
import dev.autoscroll.nativeapp.accessibility.GestureAccessibilityService
import dev.autoscroll.nativeapp.accessibility.SwipeConfig
import dev.autoscroll.nativeapp.camera.CameraController
import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.overlay.OverlayController
import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.scrolling.ScrollActionCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class GestureCameraService : LifecycleService() {
    private val app get() = application as GestureApplication
    private val store get() = app.session
    private val machine get() = store.machine
    private val adapter = AccessibilityScrollAdapter(overlayBounds = { overlay?.screenBounds() })
    private lateinit var actions: ScrollActionCoordinator
    private var gestures: GestureCoordinator? = null
    private var camera: CameraController? = null
    private var overlay: OverlayController? = null
    private var watchdog: Job? = null
    private var manualSwipe: Job? = null
    private var overlayInteracting = false
    private var lastResultMs = 0L
    private var lastDiagnosticMs = 0L
    private var diagnostics = SessionDiagnostics()
    private var foreground = false
    private var destroyed = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) pauseSession()
        }
    }

    override fun onCreate() {
        super.onCreate()
        store.serviceRunning = true
        actions = ScrollActionCoordinator(adapter, lifecycleScope, SystemClock::elapsedRealtime)
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycleScope.launch {
            GestureAccessibilityService.readiness.collect { ready ->
                if (!ready && (machine.state == GestureSessionState.Active || machine.state == GestureSessionState.Starting)) {
                    fail("Accessibility disconnected. Enable it in Settings, then start again.")
                }
            }
        }
        lifecycleScope.launch {
            store.visibility.collect { visible -> overlay?.visible(!visible) }
        }
        lifecycleScope.launch {
            app.settings.state.collect { settings ->
                if (!settings.overlayEnabled) { overlay?.close(); overlay = null }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> if (store.acceptsStart(intent.getLongExtra(EXTRA_START_REQUEST, -1))) startSession()
                else if (!foreground) stopSelf(startId)
            ACTION_PAUSE -> pauseSession()
            ACTION_PREPARE_PRACTICE -> { pauseSession(); store.practicePrepared() }
            ACTION_STOP -> stopSession()
            ACTION_FORWARD -> requestManual(ScrollAction.FORWARD)
            ACTION_BACKWARD -> requestManual(ScrollAction.BACKWARD)
            else -> stopSession()
        }
        return START_NOT_STICKY
    }

    private fun prerequisites(): String? = when {
        !store.activityVisible -> "Open Scroll Vision and press Start or Resume to activate the camera."
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED -> "Camera permission is required."
        !app.settings.accessibilityConsent -> "Confirm the accessibility disclosure first."
        !adapter.isReady -> "Enable Scroll Vision gestures in Android accessibility settings."
        app.settings.state.value.overlayEnabled && !Settings.canDrawOverlays(this) -> "Allow the floating widget, or disable it in the app."
        else -> null
    }

    private fun startSession() {
        if (store.practiceOpen) { if (!foreground) stopSelf(); return }
        if (machine.state == GestureSessionState.Active || machine.state == GestureSessionState.Starting) return
        val error = prerequisites()
        if (error != null) { fail(error); return }
        val token = machine.start() ?: return
        try {
            val notification = notification()
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            else startForeground(NOTIFICATION_ID, notification)
            foreground = true
            val settings = app.settings.state.value
            actions.configure(settings.scroll, settings.inverted)
            adapter.configure(SwipeConfig(startY = 0.5 + settings.swipeDistance / 2,
                endY = 0.5 - settings.swipeDistance / 2, durationMs = settings.swipeDurationMs))
            gestures = GestureCoordinator(ContinuousMotionDetector(settings.motion), FlickDetector(settings.flick),
                HandPoseDetector(settings.pose), settings.mode)
            if (settings.overlayEnabled) createOverlay()
            publish()
            val begin = {
                if (!destroyed && !store.practiceOpen && machine.generation == token && machine.state == GestureSessionState.Starting) {
                    lastResultMs = SystemClock.elapsedRealtime()
                    val controller = CameraController(this, settings.tracking,
                        onResult = { result ->
                            if (machine.ready(token)) { actions.setSessionActive(true); publish() }
                            if (machine.accepts(token)) {
                                lastResultMs = SystemClock.elapsedRealtime()
                                if (overlayInteracting || manualSwipe?.isActive == true) return@CameraController
                                actions.setSessionActive(true)
                                val wasDetected = diagnostics.handDetected
                                val decision = gestures!!.process(result)
                                actions.process(decision, result.timestampMs)
                                diagnostics = diagnostics.copy(handDetected = result.handDetected,
                                    pose = gestures!!.poseDetector.currentPose.name,
                                    posePhase = gestures!!.poseDetector.phase.name,
                                    lastFlick = decision.flick?.direction?.name ?: diagnostics.lastFlick,
                                    lastPose = decision.pose?.pose?.name ?: diagnostics.lastPose,
                                    trackingLossCount = diagnostics.trackingLossCount + if (wasDetected && !result.handDetected) 1 else 0)
                                publishDiagnostics()
                            }
                        },
                        onMetrics = { metrics -> if (machine.generation == token) { diagnostics = diagnostics.copy(metrics = metrics); publishDiagnostics() } },
                        onError = { problem -> if (machine.generation == token) fail(problem.message ?: "Camera or MediaPipe failed") },
                    )
                    camera = controller
                    controller.start(this)
                    armWatchdog(token)
                }
            }
            val teardown = store.cameraReleased
            if (teardown.isCompleted) begin()
            else lifecycleScope.launch { teardown.await(); begin() }
        } catch (problem: Exception) { fail(problem.message ?: "Cannot start camera foreground service") }
    }

    private fun armWatchdog(token: Long) {
        watchdog?.cancel()
        watchdog = lifecycleScope.launch {
            while (machine.generation == token) {
                delay(250)
                val settings = app.settings.state.value
                when {
                    ContextCompat.checkSelfPermission(this@GestureCameraService, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED -> fail("Camera permission was revoked.")
                    settings.overlayEnabled && !Settings.canDrawOverlays(this@GestureCameraService) -> fail("Floating widget permission was revoked.")
                    machine.state == GestureSessionState.Starting && SystemClock.elapsedRealtime() - lastResultMs > 15_000 -> fail("Camera did not produce results. Check the camera privacy switch and other camera apps.")
                    machine.state == GestureSessionState.Active && SystemClock.elapsedRealtime() - lastResultMs > settings.tracking.lostAfterMs -> {
                        actions.setSessionActive(false)
                        gestures?.loseTracking()
                        diagnostics = diagnostics.copy(handDetected = false, rejectionReason = "Tracking results stopped")
                        store.diagnostics(diagnostics)
                        if (SystemClock.elapsedRealtime() - lastResultMs > 5_000) fail("Camera results stopped. Resume from the app.")
                    }
                    machine.state == GestureSessionState.Active && !overlayInteracting && manualSwipe?.isActive != true -> actions.setSessionActive(true)
                }
            }
        }
    }

    private fun createOverlay() {
        if (overlay != null) return
        overlay = OverlayController(this,
            onPause = ::pauseSession, onStop = ::stopSession, onOpenApp = { openActivity() },
            onInteraction = { pressed ->
                overlayInteracting = pressed
                adapter.suspendForInteraction()
                actions.setSessionActive(false)
                gestures?.reset()
            },
        ).also { it.show(); it.visible(!store.activityVisible) }
    }

    private fun openActivity() {
        adapter.suspendForInteraction()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    private fun releaseSession() {
        manualSwipe?.cancel(); manualSwipe = null
        overlayInteracting = false
        watchdog?.cancel(); watchdog = null
        actions.setSessionActive(false)
        gestures?.reset(); gestures = null
        camera?.let { previous ->
            camera = null
            val teardown = CompletableDeferred<Unit>()
            store.cameraReleased = teardown
            previous.close { teardown.complete(Unit) }
        }
    }

    private fun requestManual(action: ScrollAction) {
        if (store.practiceOpen || machine.state != GestureSessionState.Active) return
        manualSwipe?.cancel()
        val token = machine.generation
        actions.setSessionActive(false)
        gestures?.reset()
        manualSwipe = lifecycleScope.launch {
            try {
                delay(850)
                if (machine.accepts(token) && !overlayInteracting) {
                    actions.setSessionActive(true)
                    actions.manual(action)
                    actions.state.first { !it.busy }
                    publishDiagnostics()
                }
            } finally {
                if (machine.accepts(token) && !overlayInteracting) {
                    gestures?.reset()
                    actions.setSessionActive(true)
                }
            }
        }
    }

    private fun pauseSession() {
        machine.pause()
        releaseSession()
        publish()
    }

    private fun stopSession() {
        machine.stop()
        releaseSession()
        overlay?.close(); overlay = null
        store.publish()
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun fail(reason: String) {
        machine.fail(reason)
        releaseSession()
        publish()
        if (!foreground) stopSelf()
    }

    private fun publish() {
        store.publish()
        overlay?.update(machine.state)
        if (foreground) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun publishDiagnostics() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDiagnosticMs < 500) return
        lastDiagnosticMs = now
        val action = actions.state.value
        diagnostics = diagnostics.copy(lastAction = "${action.lastAction ?: "None"} / ${action.lastResult ?: "pending"}",
            rejectionReason = action.rejectionReason, gestureToActionMs = action.lastGestureToActionLatencyMs)
        store.diagnostics(diagnostics)
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Hand gesture session", NotificationManager.IMPORTANCE_LOW))
        val activity = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun command(action: String, id: Int) = PendingIntent.getService(this, id,
            Intent(this, GestureCameraService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = when (val state = machine.state) {
            GestureSessionState.Active -> "Camera active · hand gestures control vertical swipes"
            GestureSessionState.Starting -> "Starting camera and hand recognition"
            GestureSessionState.Paused -> "Paused · camera released · open app to resume"
            GestureSessionState.Stopped -> "Stopped"
            is GestureSessionState.Error -> state.reason
        }
        val builder = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Scroll Vision").setContentText(text).setContentIntent(activity)
            .setOnlyAlertOnce(true).setOngoing(true)
        if (machine.state == GestureSessionState.Active || machine.state == GestureSessionState.Starting)
            builder.addAction(Notification.Action.Builder(null, "Pause", command(ACTION_PAUSE, 1)).build())
        else builder.addAction(Notification.Action.Builder(null, "Open / Resume", activity).build())
        return builder.addAction(Notification.Action.Builder(null, "Stop", command(ACTION_STOP, 2)).build()).build()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.reposition()
    }

    override fun onDestroy() {
        destroyed = true
        releaseSession()
        store.serviceRunning = false
        store.practicePrepared()
        overlay?.close(); overlay = null
        unregisterReceiver(screenReceiver)
        if (machine.state !is GestureSessionState.Error) machine.stop()
        store.publish()
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.autoscroll.nativeapp.START"
        const val EXTRA_START_REQUEST = "startRequest"
        const val ACTION_PAUSE = "dev.autoscroll.nativeapp.PAUSE"
        const val ACTION_PREPARE_PRACTICE = "dev.autoscroll.nativeapp.PREPARE_PRACTICE"
        const val ACTION_STOP = "dev.autoscroll.nativeapp.STOP"
        const val ACTION_FORWARD = "dev.autoscroll.nativeapp.FORWARD"
        const val ACTION_BACKWARD = "dev.autoscroll.nativeapp.BACKWARD"
        private const val CHANNEL = "hand_gesture_camera"
        private const val NOTIFICATION_ID = 41
    }
}
