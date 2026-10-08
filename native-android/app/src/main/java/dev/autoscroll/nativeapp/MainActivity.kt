package dev.autoscroll.nativeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.autoscroll.nativeapp.accessibility.AccessibilityScrollAdapter
import dev.autoscroll.nativeapp.accessibility.GestureAccessibilityService
import dev.autoscroll.nativeapp.accessibility.SwipeConfig
import dev.autoscroll.nativeapp.gesture.GestureMode
import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.scrolling.ScrollActionCoordinator
import dev.autoscroll.nativeapp.session.GestureCameraService
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.settings.NativeSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val app get() = application as GestureApplication
    private var permissionRevision by mutableIntStateOf(0)
    private var manualTest: Job? = null
    private var manualResult by mutableStateOf("No screen action test yet")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by app.session.state.collectAsStateWithLifecycle()
                val settings by app.settings.state.collectAsStateWithLifecycle()
                val diagnostics by app.session.diagnostics.collectAsStateWithLifecycle()
                val accessibility by GestureAccessibilityService.readiness.collectAsStateWithLifecycle()
                var page by rememberSaveable { mutableStateOf("Dashboard") }
                var disclosure by remember { mutableStateOf(false) }
                var consent by remember { mutableStateOf(app.settings.accessibilityConsent) }
                val cameraRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
                val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
                val cameraGranted = remember(permissionRevision) { hasCamera() }
                val overlayGranted = remember(permissionRevision) { Settings.canDrawOverlays(this) }
                val canEdit = state != GestureSessionState.Starting
                val prerequisites = cameraGranted && accessibility && consent && (!settings.overlayEnabled || overlayGranted)
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        Text("Hand Scroll Native", style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(16.dp))
                        PrimaryScrollableTabRow(selectedTabIndex = listOf("Dashboard", "Settings", "Diagnostics", "Help").indexOf(page)) {
                            listOf("Dashboard", "Settings", "Diagnostics", "Help").forEach { title ->
                                Tab(selected = page == title, onClick = { page = title }, text = { Text(title) })
                            }
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (page) {
                                "Dashboard" -> {
                                    Text(status(state), style = MaterialTheme.typography.titleLarge)
                                    if (state is GestureSessionState.Error) Text((state as GestureSessionState.Error).reason,
                                        color = MaterialTheme.colorScheme.error)
                                    Text("Camera permission: ${if (cameraGranted) "allowed" else "needed"}")
                                    Text("Accessibility: ${if (accessibility) "connected" else "not enabled"}")
                                    Text("Floating widget: ${if (!settings.overlayEnabled) "disabled" else if (overlayGranted) "allowed" else "permission needed"}")
                                    Text("Mode: ${settings.mode.name}")
                                    if (!cameraGranted) Button(onClick = { cameraRequest.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                                    Text("Camera: your front camera detects your hand on this device. Images are not recorded or sent to a server.")
                                    if (settings.overlayEnabled && !overlayGranted) Button(onClick = {
                                        stopBeforePermissionSettings()
                                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
                                    }) { Text("Allow floating widget") }
                                    Text("Floating widget: lets you pause, resume, stop, and move the controls over another app. It is optional.")
                                    Button(onClick = { disclosure = true }) { Text(if (accessibility) "Accessibility disclosure / settings" else "Set up accessibility") }
                                    Text("Accessibility: performs the vertical swipes, single taps, and double taps you request. Screen contents and input text are not read. You enable it yourself in Android Settings.")
                                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                        OutlinedButton(onClick = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow session notifications") }
                                    Text("A session notification provides Pause and Stop. Android also shows camera use. Notification permission is recommended; denial does not prevent the foreground service.")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(enabled = prerequisites && state != GestureSessionState.Active && state != GestureSessionState.Starting,
                                            onClick = ::startSession) { Text(if (state == GestureSessionState.Paused) "Resume" else "Start") }
                                        OutlinedButton(enabled = state == GestureSessionState.Active || state == GestureSessionState.Starting,
                                            onClick = ::pauseSession) { Text("Pause") }
                                        OutlinedButton(enabled = state != GestureSessionState.Stopped, onClick = ::stopSession) { Text("Stop") }
                                    }
                                    Text("Wait for Active, then switch to your feed. In HYBRID or POSE mode: point down/up to navigate, show an open hand for one center tap (pause/play), or a thumbs-up for a double tap. Close your hand briefly before repeating a pose.")
                                    HorizontalDivider()
                                    Text("Test an external screen action", style = MaterialTheme.typography.titleMedium)
                                    Text("Open a feed app first, return here, then tap a test. Hand Scroll moves to the background and sends the action after one second. Taps land at the center of the screen. Camera stays off.")
                                    val canTest = accessibility && consent && state == GestureSessionState.Stopped
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(enabled = canTest && manualTest?.isActive != true,
                                            onClick = { testAction(ScrollAction.FORWARD) }) { Text("Swipe forward") }
                                        OutlinedButton(enabled = canTest && manualTest?.isActive != true,
                                            onClick = { testAction(ScrollAction.BACKWARD) }) { Text("Swipe backward") }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(enabled = canTest && manualTest?.isActive != true,
                                            onClick = { testAction(ScrollAction.SINGLE_TAP) }) { Text("Single tap") }
                                        OutlinedButton(enabled = canTest && manualTest?.isActive != true,
                                            onClick = { testAction(ScrollAction.DOUBLE_TAP) }) { Text("Double tap") }
                                    }
                                    Text(manualResult)
                                }
                                "Settings" -> {
                                    Text("Changing a setting pauses an active session. Resume from this screen's Dashboard when ready.")
                                    SettingsPanel(settings, canEdit) { updateSettings(it) }
                                }
                                "Diagnostics" -> {
                                    val metrics = diagnostics.metrics
                                    Text(status(state), style = MaterialTheme.typography.titleLarge)
                                    Text("MediaPipe: ${if (metrics.processedFrames > 0) "initialized / completed inference" else "waiting for session"}")
                                    Text("Hand: ${if (diagnostics.handDetected) "detected" else "not detected"}")
                                    Text("Pose: ${diagnostics.pose} · ${diagnostics.posePhase}")
                                    Text("Last pose: ${diagnostics.lastPose} · flick: ${diagnostics.lastFlick}")
                                    Text("Delivered camera FPS: ${decimal(metrics.cameraFps)}")
                                    Text("Inference FPS: ${decimal(metrics.inferenceFps)}")
                                    Text("Last inference + conversion: ${decimal(metrics.inferenceMilliseconds)} ms")
                                    Text("Recent ${metrics.inferenceSamples} frames: average ${decimal(metrics.inferenceAverageMilliseconds)} ms · P50 ${decimal(metrics.inferenceP50Milliseconds)} ms · P95 ${decimal(metrics.inferenceP95Milliseconds)} ms")
                                    Text("Completed: ${metrics.processedFrames} · rate skipped: ${metrics.throttledFrames} · stale: ${metrics.staleFrames}")
                                    Text("Tracking losses: ${diagnostics.trackingLossCount}")
                                    Text("Last action: ${diagnostics.lastAction}")
                                    Text("Frame callback to dispatch: ${diagnostics.gestureToActionMs?.let { "$it ms" } ?: "not measured"}")
                                    Text("Last rejection: ${diagnostics.rejectionReason ?: "None"}")
                                    Text("CameraX's internal frame drops are not exposed. Action completion means Android completed dispatch; check the feed to confirm its response.")
                                }
                                else -> {
                                    Text("Getting started", style = MaterialTheme.typography.titleLarge)
                                    Text("1. Allow camera. Optionally allow the floating widget. Read the accessibility disclosure and enable Hand Scroll in Android Settings.")
                                    Text("2. Test swipes and taps with the camera stopped.")
                                    Text("3. Start on the Dashboard and wait for Active. Switch to TikTok, Reels, Shorts, or another vertically scrolling app.")
                                    Text("4. In HYBRID or POSE mode, point with the index finger and curl the others to navigate. An open hand sends one center tap; a thumbs-up with all four other fingers curled sends a double tap. Hold briefly to confirm. Close your hand briefly before repeating; a held pose never repeats. Open hand also taps when releasing a pointing pose.")
                                    Text("A single tap toggles pause/play only in feeds that support center tapping. Double-tap behavior depends on the target app. Flicks use fast vertical palm motion. CONTINUOUS and FLICK modes keep their existing movement controls without pose taps.")
                                    Text("Tap the status bubble to expand or collapse, and drag it to move it. Pause releases the camera. Resume opens this app, where you press Resume. Stop removes the widget and notification.")
                                    Text("Locking the screen pauses the session. After a process restart the session is stopped. Camera privacy controls, another camera app, battery restrictions, and device software may interrupt capture.")
                                    Text("Keep the hand well lit and fully visible. Thresholds are experimental and need calibration on your phone. A dispatched action may be ignored by an app, and very small continuous swipes may not exceed its touch threshold.")
                                    Text("Accessibility actions can interrupt a finger touch already in progress. Stop or pause before sensitive interactions. System settings, permission screens, the launcher, and Hand Scroll itself are blocked targets.")
                                    Text("All inference stays on this device. No accounts, ads, analytics, server, or video recording. Diagnostics here are in memory and clear on process restart.")
                                }
                            }
                        }
                    }
                }
                if (disclosure) AlertDialog(
                    onDismissRequest = { disclosure = false },
                    title = { Text("Allow gesture controlled screen actions") },
                    text = { Text("Hand Scroll uses Android Accessibility to send vertical swipes, center single taps, and center double taps requested by your hand gestures or test buttons in other apps. It receives only the current app's package name to block unsafe targets. It does not read screen content, text, or passwords. Actions may interrupt an ongoing touch. You can pause, stop, or disable the service in Android Settings at any time.") },
                    confirmButton = { TextButton(onClick = {
                        app.settings.accessibilityConsent = true; consent = true; disclosure = false
                        stopBeforePermissionSettings()
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("I agree · open Settings") } },
                    dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Cancel") } },
                )
            }
        }
    }

    override fun onStart() { super.onStart(); app.session.activityVisible = true }
    override fun onResume() { super.onResume(); permissionRevision++ }
    override fun onStop() { app.session.activityVisible = false; super.onStop() }
    override fun onDestroy() { manualTest?.cancel(); super.onDestroy() }

    private fun hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun startSession() {
        if (!app.session.activityVisible) return
        manualTest?.cancel()
        try {
            ContextCompat.startForegroundService(this, Intent(this, GestureCameraService::class.java).setAction(GestureCameraService.ACTION_START))
        } catch (problem: Exception) {
            app.session.machine.fail(problem.message ?: "Cannot start foreground camera")
            app.session.publish()
        }
    }
    private fun pauseSession() {
        app.session.machine.pause(); app.session.publish()
        startService(Intent(this, GestureCameraService::class.java).setAction(GestureCameraService.ACTION_PAUSE))
    }
    private fun stopSession() {
        manualTest?.cancel()
        app.session.machine.stop(); app.session.publish()
        startService(Intent(this, GestureCameraService::class.java).setAction(GestureCameraService.ACTION_STOP))
    }
    private fun stopBeforePermissionSettings() { if (app.session.machine.state != GestureSessionState.Stopped) stopSession() }
    private fun updateSettings(settings: NativeSettings) {
        if (app.session.machine.state == GestureSessionState.Active || app.session.machine.state == GestureSessionState.Starting) pauseSession()
        app.settings.update(settings)
    }
    private fun testAction(action: ScrollAction) {
        if (!app.settings.accessibilityConsent || app.session.machine.state != GestureSessionState.Stopped) return
        manualTest?.cancel()
        manualTest = lifecycleScope.launch {
            val settings = app.settings.state.value
            val adapter = AccessibilityScrollAdapter(SwipeConfig(startY = 0.5 + settings.swipeDistance / 2,
                endY = 0.5 - settings.swipeDistance / 2, durationMs = settings.swipeDurationMs))
            val coordinator = ScrollActionCoordinator(adapter, this, SystemClock::elapsedRealtime, settings.scroll)
            coordinator.configure(inverted = settings.inverted)
            manualResult = "Waiting to test $action"
            moveTaskToBack(true)
            try {
                delay(1000)
                coordinator.setSessionActive(true)
                coordinator.manual(action)
                delay(settings.swipeDurationMs + 500)
                val result = coordinator.state.value
                manualResult = "${result.lastResult ?: "No result"}: ${result.rejectionReason ?: "check the feed's response"}"
            } finally { coordinator.setSessionActive(false) }
        }
    }
}

private fun status(state: GestureSessionState) = when (state) {
    GestureSessionState.Stopped -> "Stopped · camera off"
    GestureSessionState.Starting -> "Starting camera…"
    GestureSessionState.Active -> "Active · camera on"
    GestureSessionState.Paused -> "Paused · camera off"
    is GestureSessionState.Error -> "Session error · camera off"
}
private fun decimal(value: Double) = String.format(Locale.US, "%.1f", value)

@Composable
private fun SettingsPanel(value: NativeSettings, enabled: Boolean, update: (NativeSettings) -> Unit) {
    Text("Recognition mode", style = MaterialTheme.typography.titleMedium)
    GestureMode.entries.forEach { mode ->
        Row {
            RadioButton(selected = value.mode == mode, enabled = enabled, onClick = { update(value.copy(mode = mode)) })
            Text(mode.name, Modifier.padding(top = 12.dp))
        }
    }
    Row { Text("Invert direction", Modifier.weight(1f)); Switch(value.inverted, { update(value.copy(inverted = it)) }, enabled = enabled) }
    Row { Text("Floating widget", Modifier.weight(1f)); Switch(value.overlayEnabled, { update(value.copy(overlayEnabled = it)) }, enabled = enabled) }
    Text("Swipe", style = MaterialTheme.typography.titleMedium)
    NumberSetting("Duration (ms)", value.swipeDurationMs.toDouble(), 100.0..1000.0, enabled, 17) {
        update(value.copy(swipeDurationMs = it.toLong(), scroll = value.scroll.copy(flickDurationMs = it.toLong())))
    }
    NumberSetting("Screen distance", value.swipeDistance, 0.1..0.8, enabled) { update(value.copy(swipeDistance = it)) }
    Text("Camera", style = MaterialTheme.typography.titleMedium)
    NumberSetting("Inference FPS", value.tracking.inferenceFps.toDouble(), 5.0..30.0, enabled, 24) { update(value.copy(tracking = value.tracking.copy(inferenceFps = it.toInt()))) }
    NumberSetting("Maximum image dimension", value.tracking.maximumInferenceDimension.toDouble(), 192.0..640.0, enabled, 13) { update(value.copy(tracking = value.tracking.copy(maximumInferenceDimension = it.toInt()))) }
    Text("Pointing and tap poses · close hand to release", style = MaterialTheme.typography.titleMedium)
    NumberSetting("Confirm (ms)", value.pose.confirmationDurationMs.toDouble(), 100.0..500.0, enabled, 19) { update(value.copy(pose = value.pose.copy(confirmationDurationMs = it.toLong()))) }
    NumberSetting("Pose release (ms)", value.pose.releaseDurationMs.toDouble(), 100.0..500.0, enabled, 19) { update(value.copy(pose = value.pose.copy(releaseDurationMs = it.toLong()))) }
    NumberSetting("Pose cooldown (ms)", value.pose.cooldownMs.toDouble(), 200.0..1500.0, enabled, 25) { update(value.copy(pose = value.pose.copy(cooldownMs = it.toLong()))) }
    NumberSetting("Extended index / thumb angle", value.pose.minimumIndexAngle, 150.0..175.0, enabled) { update(value.copy(pose = value.pose.copy(minimumIndexAngle = it))) }
    NumberSetting("Curled finger angle", value.pose.maximumCurledAngle, 80.0..125.0, enabled) { update(value.copy(pose = value.pose.copy(maximumCurledAngle = it))) }
    NumberSetting("Vertical alignment", value.pose.minimumVerticalAlignment, 0.70..1.0, enabled) { update(value.copy(pose = value.pose.copy(minimumVerticalAlignment = it))) }
    Text("Flick", style = MaterialTheme.typography.titleMedium)
    NumberSetting("Minimum speed (heights/s)", value.flick.minimumVelocity, 0.31..1.5, enabled) { update(value.copy(flick = value.flick.copy(minimumVelocity = it))) }
    NumberSetting("Minimum displacement", value.flick.minimumDisplacement, 0.05..0.30, enabled) { update(value.copy(flick = value.flick.copy(minimumDisplacement = it))) }
    NumberSetting("Flick cooldown (ms)", value.flick.cooldownMs.toDouble(), 200.0..1500.0, enabled, 25) { update(value.copy(flick = value.flick.copy(cooldownMs = it.toLong()))) }
    NumberSetting("Flick rearm (ms)", value.flick.rearmDurationMs.toDouble(), 100.0..500.0, enabled, 19) { update(value.copy(flick = value.flick.copy(rearmDurationMs = it.toLong()))) }
    Text("Continuous movement", style = MaterialTheme.typography.titleMedium)
    NumberSetting("Sensitivity", value.scroll.pixelsPerNormalizedUnit, 300.0..4000.0, enabled) { update(value.copy(scroll = value.scroll.copy(pixelsPerNormalizedUnit = it))) }
    NumberSetting("Motion smoothing", value.motion.smoothingAlpha, 0.1..1.0, enabled) { update(value.copy(motion = value.motion.copy(smoothingAlpha = it))) }
    NumberSetting("Movement threshold", value.motion.movementThreshold, 0.008..0.08, enabled) { update(value.copy(motion = value.motion.copy(movementThreshold = it))) }
}

@Composable
private fun NumberSetting(label: String, value: Double, range: ClosedFloatingPointRange<Double>, enabled: Boolean,
    steps: Int = 0, change: (Double) -> Unit) {
    Text("$label: ${String.format(Locale.US, "%.3f", value)}")
    Slider(value = value.toFloat().coerceIn(range.start.toFloat(), range.endInclusive.toFloat()),
        onValueChange = { change(it.toDouble().coerceIn(range.start, range.endInclusive)) }, valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        steps = steps, enabled = enabled)
}
