package dev.autoscroll.nativeapp.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.session.SessionDiagnostics
import dev.autoscroll.nativeapp.settings.NativeSettings
import java.util.Locale

@Composable
internal fun HandScrollScreen(
    state: GestureSessionState,
    settings: NativeSettings,
    diagnostics: SessionDiagnostics,
    accessibility: Boolean,
    consent: Boolean,
    cameraGranted: Boolean,
    overlayGranted: Boolean,
    notificationRequired: Boolean,
    manualRunning: Boolean,
    manualResult: String,
    onCameraPermission: () -> Unit,
    onOverlayPermission: () -> Unit,
    onNotificationPermission: () -> Unit,
    onAccessibilityConsent: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSettingsChange: (NativeSettings) -> Unit,
    onTestAction: (ScrollAction) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf("Dashboard") }
    var disclosure by remember { mutableStateOf(false) }
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
                        if (state is GestureSessionState.Error) Text(state.reason,
                            color = MaterialTheme.colorScheme.error)
                        Text("Camera permission: ${if (cameraGranted) "allowed" else "needed"}")
                        Text("Accessibility: ${if (accessibility) "connected" else "not enabled"}")
                        Text("Floating widget: ${if (!settings.overlayEnabled) "disabled" else if (overlayGranted) "allowed" else "permission needed"}")
                        Text("Mode: ${settings.mode.name}")
                        if (!cameraGranted) Button(onClick = { onCameraPermission() }) { Text("Allow camera") }
                        Text("Camera: your front camera detects your hand on this device. Images are not recorded or sent to a server.")
                        if (settings.overlayEnabled && !overlayGranted) Button(onClick = {
                            onOverlayPermission()
                        }) { Text("Allow floating widget") }
                        Text("Floating widget: lets you pause, resume, stop, and move the controls over another app. It is optional.")
                        Button(onClick = { disclosure = true }) { Text(if (accessibility) "Accessibility disclosure / settings" else "Set up accessibility") }
                        Text("Accessibility: performs the vertical swipes, single taps, and double taps you request. Screen contents and input text are not read. You enable it yourself in Android Settings.")
                        if (notificationRequired)
                            OutlinedButton(onClick = { onNotificationPermission() }) { Text("Allow session notifications") }
                        Text("A session notification provides Pause and Stop. Android also shows camera use. Notification permission is recommended; denial does not prevent the foreground service.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = prerequisites && state != GestureSessionState.Active && state != GestureSessionState.Starting,
                                onClick = onStart) { Text(if (state == GestureSessionState.Paused) "Resume" else "Start") }
                            OutlinedButton(enabled = state == GestureSessionState.Active || state == GestureSessionState.Starting,
                                onClick = onPause) { Text("Pause") }
                            OutlinedButton(enabled = state != GestureSessionState.Stopped, onClick = onStop) { Text("Stop") }
                        }
                        Text("Wait for Active, then switch to your feed. In HYBRID or POSE mode: point down/up to navigate, show an open hand for one pause tap, or a thumbs-up for a double tap. Navigation and thumbs-up repeat after the rearm delay and fresh confirmation. No open or closed hand is needed to rearm.")
                        HorizontalDivider()
                        Text("Test an external screen action", style = MaterialTheme.typography.titleMedium)
                        Text("Open a feed app first, return here, then tap a test. Hand Scroll moves to the background and sends the action after one second. Taps land at the center of the screen. Camera stays off.")
                        val canTest = accessibility && consent && state == GestureSessionState.Stopped
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = canTest && !manualRunning,
                                onClick = { onTestAction(ScrollAction.FORWARD) }) { Text("Swipe forward") }
                            OutlinedButton(enabled = canTest && !manualRunning,
                                onClick = { onTestAction(ScrollAction.BACKWARD) }) { Text("Swipe backward") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = canTest && !manualRunning,
                                onClick = { onTestAction(ScrollAction.SINGLE_TAP) }) { Text("Single tap") }
                            OutlinedButton(enabled = canTest && !manualRunning,
                                onClick = { onTestAction(ScrollAction.DOUBLE_TAP) }) { Text("Double tap") }
                        }
                        Text(manualResult)
                    }
                    "Settings" -> {
                        Text("Changing a setting pauses an active session. Resume from this screen's Dashboard when ready.")
                        SettingsPanel(settings, canEdit, onSettingsChange)
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
                        Text("4. In HYBRID or POSE mode, point with the index finger and curl the others to navigate. A thumbs-up with the four other fingers curled sends a double tap. Rearm uses only elapsed time; adjust Rearm delay in Settings. After the delay each action needs fresh confirmation, even if you keep holding the pose.")
                        Text("Open hand is only a pause tap, never a rearm signal. Holding it sends one center tap and blocks movement gestures. Show another visible pose before another open-hand tap; tracking loss alone cannot repeat the tap.")
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
            disclosure = false
            onAccessibilityConsent()
        }) { Text("I agree · open Settings") } },
        dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Cancel") } },
    )
}

private fun status(state: GestureSessionState) = when (state) {
    GestureSessionState.Stopped -> "Stopped · camera off"
    GestureSessionState.Starting -> "Starting camera…"
    GestureSessionState.Active -> "Active · camera on"
    GestureSessionState.Paused -> "Paused · camera off"
    is GestureSessionState.Error -> "Session error · camera off"
}
private fun decimal(value: Double) = String.format(Locale.US, "%.1f", value)

