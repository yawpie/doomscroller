package dev.autoscroll.nativeapp.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.autoscroll.nativeapp.gesture.GestureMode
import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.session.SessionDiagnostics
import dev.autoscroll.nativeapp.settings.NativeSettings
import dev.autoscroll.nativeapp.settings.ControlMode

@Composable
internal fun HandScrollScreen(
    state: GestureSessionState,
    settings: NativeSettings,
    draft: NativeSettings,
    onDraftChange: (NativeSettings) -> Unit,
    diagnostics: SessionDiagnostics,
    accessibility: Boolean,
    consent: Boolean,
    cameraGranted: Boolean,
    cameraBlocked: Boolean,
    cameraExplanation: String?,
    overlayGranted: Boolean,
    notificationRequired: Boolean,
    manualRunning: Boolean,
    manualResult: String,
    permissionError: String?,
    onCameraPermission: () -> Unit,
    onOverlayPermission: () -> Unit,
    onNotificationPermission: () -> Unit,
    onAccessibilityConsent: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSettingsChange: (NativeSettings) -> Unit,
    onTestAction: (ScrollAction) -> Unit,
    onPractice: (() -> Unit)? = null,
    pointerConsent: Boolean = false,
    pointerCalibrated: Boolean = false,
    onPointerConsent: () -> Unit = {},
    pointerContactActive: Boolean = false,
    pointerRecoveryRequired: Boolean = false,
    onPointerRecovery: () -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf("Home") }
    var disclosure by rememberSaveable { mutableStateOf(false) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var applyMessage by remember { mutableStateOf<String?>(null) }
    val pointerMode = settings.controlMode == ControlMode.POINTER
    val ready = readyToStart(cameraGranted, accessibility, consent, settings.overlayEnabled, overlayGranted,
        settings.controlMode, pointerConsent, pointerCalibrated || state == GestureSessionState.Active || state == GestureSessionState.Starting)
    val dirty = draft != settings
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Text("Scroll Vision", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
            PrimaryScrollableTabRow(selectedTabIndex = listOf("Home", "Gestures", "Settings").indexOf(page), edgePadding = 0.dp) {
                listOf("Home", "Gestures", "Settings").forEach { title ->
                    Tab(selected = page == title, onClick = { page = title }, text = { Text(title, maxLines = 1) })
                }
            }
            // Each tab keeps its own scroll position; switching tabs never applies the settings draft.
            val homeScroll = rememberScrollState()
            val gesturesScroll = rememberScrollState()
            val settingsScroll = rememberScrollState()
            Column(Modifier.weight(1f).verticalScroll(when (page) {
                "Home" -> homeScroll
                "Gestures" -> gesturesScroll
                else -> settingsScroll
            }).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (page) {
                    "Home" -> {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(status(state), style = MaterialTheme.typography.titleLarge)
                                Text("Control mode: ${if (pointerMode) "Pointer" else "Scroll"}")
                                Text(if (ready) "Ready to use" else "Finish the required setup below")
                                if (pointerRecoveryRequired) {
                                    Text("Touch release could not be confirmed", style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error)
                                    Text("Touch the screen with your finger to clear any held contact. Then acknowledge below. Tracking stays stopped; Pointer needs a fresh calibration and a separate Start.")
                                    OutlinedButton(onClick = onPointerRecovery,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("I touched the screen · clear recovery") }
                                } else if (pointerContactActive && state != GestureSessionState.Active) {
                                    Text("The previous touch is ending. Wait for it to finish, then press Start again.")
                                }
                                if (pointerMode && state != GestureSessionState.Active && state != GestureSessionState.Starting) {
                                    Text(if (pointerCalibrated) "Practice calibration is ready for one new Start."
                                        else "Calibrate in Practice, then choose Use calibration. Return here and press Start explicitly.")
                                    if (!pointerCalibrated && onPractice != null) OutlinedButton(onClick = onPractice,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Calibrate in Practice") }
                                }
                                if (pointerMode && diagnostics.pointer != null) {
                                    Text("Pointer: ${diagnostics.pointer.phase.name.lowercase().replace('_', ' ')}")
                                    diagnostics.pointer.message?.let { Text(it) }
                                }
                                if (state is GestureSessionState.Error) Text(state.reason, color = MaterialTheme.colorScheme.error)
                                Button(onClick = { if (state == GestureSessionState.Active) onPause() else onStart() },
                                    enabled = state != GestureSessionState.Starting && (state == GestureSessionState.Active ||
                                        ready && !pointerContactActive && !pointerRecoveryRequired),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                    Text(when (state) {
                                        GestureSessionState.Active -> "Pause tracking"
                                        GestureSessionState.Paused -> "Resume tracking"
                                        GestureSessionState.Starting -> "Starting…"
                                        else -> "Start tracking"
                                    })
                                }
                                TextButton(onClick = onStop, enabled = state != GestureSessionState.Stopped,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Stop") }
                                Text(if (pointerMode) {
                                    if (state == GestureSessionState.Active) "Switch to your target app. The cursor hides over Scroll Vision and blocked screens. Pause releases the camera and clears calibration."
                                    else "Pointer needs a fresh Practice calibration after Pause, Stop, Apply or a screen-size/orientation change. All hand tracking stays on this device."
                                } else if (state == GestureSessionState.Active) "Switch to your feed when ready. Pause releases the camera."
                                    else "Start or Resume here before switching to your feed. All hand tracking stays on this device.",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Quick gesture guide", style = MaterialTheme.typography.titleMedium)
                                Text(compactGuide(settings))
                                TextButton(onClick = { page = "Gestures" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("See gesture cards") }
                            }
                        }
                        if (!ready || showSetup) {
                            Text("Permission checklist", style = MaterialTheme.typography.titleLarge)
                            Text(if (pointerMode) "Complete camera, pointer accessibility consent, cursor overlay access and Practice calibration. Floating controls and notifications remain optional."
                                else "Complete camera and accessibility access. Floating controls and notifications are optional.")
                            if (permissionError != null) Text(permissionError, color = MaterialTheme.colorScheme.error)
                            SetupStep("1 · Camera", cameraGranted,
                                "Your front camera detects your hand. Images are processed on your device, never recorded or sent to a server.") {
                                if (!cameraGranted) {
                                    if (cameraExplanation != null) Text(cameraExplanation)
                                    Button(onClick = onCameraPermission, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                        Text(if (cameraBlocked) "Open camera settings" else "Allow camera")
                                    }
                                }
                            }
                            SetupStep("2 · Accessibility", accessibility && consent && (!pointerMode || pointerConsent),
                                if (pointerMode) "Pointer touches use the cursor location: press, hold, drag and release. Screen content, typed text and passwords are not read."
                                else "Sends the swipes and taps you request. Screen content, typed text and passwords are not read.") {
                                if (!accessibility || !consent || pointerMode && !pointerConsent) Text("Read and agree to the disclosure, then enable Scroll Vision gestures in Android Settings. If Android restricts access, open App info and review its restricted settings option.")
                                OutlinedButton(onClick = { disclosure = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(if (accessibility && consent && (!pointerMode || pointerConsent)) "Review access" else "Review and enable accessibility")
                                }
                            }
                            SetupStep(if (pointerMode) "3 · Cursor overlay · required" else "3 · Floating controls · optional",
                                if (pointerMode) overlayGranted else !settings.overlayEnabled || overlayGranted,
                                if (pointerMode) "The pointer cursor needs display-over-other-apps access, even when Floating controls are off. Floating controls stay an independent option."
                                else if (!settings.overlayEnabled) "Skipped. You can enable these later in Settings."
                                else "Pause or Stop over your feed. Open the app to Resume. You can drag the status bubble.") {
                                if ((settings.overlayEnabled || pointerMode) && !overlayGranted) {
                                    OutlinedButton(onClick = onOverlayPermission, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (pointerMode) "Allow cursor overlay" else "Allow floating controls") }
                                    if (!pointerMode) {
                                    TextButton(onClick = {
                                        onDraftChange(draft.copy(overlayEnabled = false))
                                        onSettingsChange(settings.copy(overlayEnabled = false))
                                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Skip floating controls") }
                                    }
                                }
                            }
                            SetupStep("4 · Notifications · optional", !notificationRequired,
                                "A session notice offers Pause and Stop. Android shows camera use. Denying notifications does not block tracking.") {
                                if (notificationRequired) OutlinedButton(onClick = onNotificationPermission,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Allow notifications") }
                            }
                        }
                        TextButton(onClick = { showSetup = !showSetup }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(if (showSetup && ready) "Hide setup checklist" else "Review setup checklist")
                        }
                    }
                    "Gestures" -> {
                        Text("Learn your gestures", style = MaterialTheme.typography.titleLarge)
                        Text(if (pointerMode) "Current mode: Pointer. Keep one whole hand visible and well lit."
                            else "Current mode: ${modeLabel(settings.mode)}${if (settings.inverted) " · direction reversed" else ""}. Keep your whole hand visible and well lit.")
                        if (onPractice != null) Button(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Practice safely") }
                        if (pointerMode) {
                            Text("Calibrate two comfortable corners in Practice with the screen buttons, then choose Use calibration. Home Start is always a separate action.")
                            Text("Extend your index finger to aim. Pinch thumb and index finger to press; keep pinched to hold; move while pinched to drag; separate to release. Start with fingers separated and a steady hand.")
                            Text("The cursor hides on tracking loss, your floating controls, Scroll Vision, screen lock and blocked apps. A target-app change requests release of the current touch. Reacquire with separated fingers; never resume a held pinch automatically.")
                        } else {
                        if (settings.mode == GestureMode.HYBRID || settings.mode == GestureMode.POSE) {
                            GestureCard(if (settings.inverted) "Previous item" else "Next item", HandDrawing.DOWN,
                                "Point your index finger down. Curl the other fingers.", "Holding this pose can repeat after the rearm delay and fresh confirmation.")
                            GestureCard(if (settings.inverted) "Next item" else "Previous item", HandDrawing.UP,
                                "Point your index finger up. Curl the other fingers.", "Holding this pose can repeat after the rearm delay and fresh confirmation.")
                            GestureCard("Tap video", HandDrawing.OPEN,
                                "Show an open hand for one tap at the center of your feed. This may toggle play or pause, depending on the app.",
                                "Tracking stays on. Holding your hand open sends no extra taps and blocks movement gestures. Show a different visible pose before another open-hand tap; losing tracking alone does not rearm it.")
                            GestureCard("Double tap", HandDrawing.THUMB,
                                "Show a thumbs-up with the other four fingers curled. Sends two center taps; the result depends on your feed.",
                                "Holding thumbs-up can repeat after the rearm delay and fresh confirmation.")
                        }
                        if (settings.mode == GestureMode.HYBRID || settings.mode == GestureMode.FLICK) {
                            GestureCard("Flick to navigate", HandDrawing.FLICK,
                                "Move your palm quickly down or up to ${if (settings.inverted) "go back or forward" else "go forward or back"}. In Hybrid, curl your fingers and keep your thumb relaxed to avoid a pose tap.",
                                "Settle your hand before the next flick; cooldown and rearm prevent immediate repeats.")
                        }
                        if (settings.mode == GestureMode.HYBRID || settings.mode == GestureMode.CONTINUOUS) {
                            GestureCard("Move to scroll", HandDrawing.MOVEMENT,
                                "Move your palm vertically for continuous scrolling. Direction follows hand movement${if (settings.inverted) " with reversal enabled" else ""}. In Hybrid, curl your fingers and keep your thumb relaxed to avoid a pose tap.",
                                "Hold still to stop scrolling. In Hybrid mode, poses and flicks take priority.")
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Pause tracking", style = MaterialTheme.typography.titleMedium)
                                Text("Press Pause tracking on Home, or Pause in the floating controls or notification. It releases the camera and stops gesture actions. Open Home and press Resume tracking to continue.")
                                Text("Tap video and Pause tracking do different things. The open-hand gesture taps your feed; it does not turn tracking off.")
                            }
                        }
                        Text("Pause before sensitive interactions: gesture actions can interrupt a finger touch. System settings, permission screens, launcher and Scroll Vision are blocked targets. Screen lock pauses tracking; process restart stays stopped.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    else -> {
                        Text(if (advanced) "Advanced" else "Settings", style = MaterialTheme.typography.titleLarge)
                        Text("Edit a draft, then apply once. Your changes stay in the draft until you apply or discard them.")
                        Text(if (settingsChangePauses(state, settings, draft)) "Applying changes pauses tracking and releases the camera. Return to Home and press Resume tracking."
                            else "Applying while tracking is active or starting will pause it. Resume from Home when ready.")
                        Button(onClick = {
                            val pauses = settingsChangePauses(state, settings, draft)
                            onSettingsChange(draft)
                            applyMessage = if (pauses) "Changes applied. Tracking is paused; return to Home and press Resume tracking." else "Changes applied."
                        }, enabled = dirty, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Apply changes") }
                        OutlinedButton(onClick = { onDraftChange(settings); applyMessage = "Draft discarded." }, enabled = dirty,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Discard changes") }
                        Text(if (dirty) "Unsaved changes" else applyMessage ?: "All changes saved")
                        if (advanced) {
                            TextButton(onClick = { advanced = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Back to everyday settings") }
                            AdvancedSettingsPanel(draft, state, diagnostics,
                                canTest = !pointerMode && accessibility && consent && state == GestureSessionState.Stopped,
                                manualRunning = manualRunning, manualResult = manualResult,
                                update = { onDraftChange(it); applyMessage = null }, test = onTestAction)
                        } else {
                            SettingsPanel(draft) { onDraftChange(it); applyMessage = null }
                            OutlinedButton(onClick = { advanced = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Advanced") }
                            Text("Recognition tuning, camera settings, diagnostics and external action tests.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
    if (disclosure) AlertDialog(
        onDismissRequest = { disclosure = false },
        title = { Text(if (pointerMode) "Allow pointer controlled screen actions" else "Allow gesture controlled screen actions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(if (pointerMode) "Scroll Vision uses Android Accessibility to send a touch at the pointer cursor: pinch to press, keep pinched to hold, move while pinched to drag, and separate to release. A short press becomes a tap. Other apps decide how these touches behave. It receives only the current app's package name to block unsafe targets; it does not read screen content, text or passwords. Touches may interrupt manual input. Pause, Stop, tracking loss, lock or an app change requests release; an already submitted segment may finish first. Releasing a short touch can still activate its target, and a long-press action cannot always be undone. If release cannot be confirmed, new actions stop: touch the screen manually, acknowledge recovery in Home, then recalibrate and explicitly Start. Camera images stay on your device. You can pause, stop or disable the service in Android Settings at any time."
                    else "Scroll Vision uses Android Accessibility to send vertical swipes, center single taps, and center double taps requested by your hand gestures or external test buttons in other apps. It receives only the current app's package name to block unsafe targets. It does not read screen content, text, or passwords. Actions may interrupt an ongoing touch. You can pause, stop, or disable the service in Android Settings at any time.")
            }
        },
        confirmButton = { TextButton(onClick = { disclosure = false; if (pointerMode) onPointerConsent() else onAccessibilityConsent() },
            modifier = Modifier.heightIn(min = 48.dp)) { Text("I agree · open Settings") } },
        dismissButton = { TextButton(onClick = { disclosure = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}

@Composable
private fun SetupStep(title: String, complete: Boolean, explanation: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(if (complete) "Complete" else if (title.contains("optional")) "Your choice" else "Required · not ready")
            Text(explanation)
            content()
        }
    }
}

@Composable
private fun GestureCard(title: String, drawing: HandDrawing, instruction: String, repeat: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HandIllustration(drawing)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(instruction)
            Text(repeat, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private enum class HandDrawing { UP, DOWN, OPEN, THUMB, FLICK, MOVEMENT }

@Composable
private fun HandIllustration(drawing: HandDrawing) {
    val fill = MaterialTheme.colorScheme.primaryContainer
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(Modifier.fillMaxWidth().height(144.dp).semantics {
        contentDescription = when (drawing) {
            HandDrawing.UP -> "Hand illustration: index finger pointing up, other fingers curled"
            HandDrawing.DOWN -> "Hand illustration: index finger pointing down, other fingers curled"
            HandDrawing.THUMB -> "Hand illustration: thumbs-up, four fingers curled"
            HandDrawing.OPEN -> "Hand illustration: open palm"
            else -> "Hand illustration: curled fingers, relaxed thumb, vertical movement arrows"
        }
    }) {
        val factor = size.height / 140f
        scale(factor, factor, pivot = center) {
            rotate(if (drawing == HandDrawing.DOWN) 180f else 0f, pivot = center) {
                val left = center.x - 32f
                val top = center.y - 5f
                fun finger(x: Float, y: Float, width: Float, height: Float) {
                    drawRoundRect(fill, Offset(x, y), Size(width, height), CornerRadius(8f))
                    drawRoundRect(ink, Offset(x, y), Size(width, height), CornerRadius(8f), style = Stroke(2f))
                }
                finger(left + 12f, top + 42f, 38f, 22f)
                finger(left, top, 66f, 48f)
                when (drawing) {
                    HandDrawing.UP, HandDrawing.DOWN -> {
                        finger(left + 2f, top - 54f, 14f, 65f)
                        for (i in 1..3) finger(left + i * 15f, top - 8f, 13f, 26f)
                        finger(left - 10f, top + 14f, 22f, 15f)
                    }
                    HandDrawing.THUMB -> {
                        finger(left - 14f, top - 44f, 16f, 63f)
                        for (i in 0..3) finger(left + i * 15f, top - 8f, 14f, 30f)
                    }
                    HandDrawing.FLICK, HandDrawing.MOVEMENT -> {
                        for (i in 0..3) finger(left + i * 15f, top - 8f, 14f, 30f)
                        finger(left - 8f, top + 18f, 22f, 14f)
                    }
                    else -> {
                        for (i in 0..3) finger(left + i * 17f, top - (42f + if (i == 1) 12f else if (i == 3) -6f else 0f), 14f,
                            55f + if (i == 1) 12f else if (i == 3) -6f else 0f)
                        rotate(-38f, Offset(left, top + 30f)) { finger(left - 14f, top - 4f, 17f, 45f) }
                    }
                }
            }
            if (drawing == HandDrawing.FLICK || drawing == HandDrawing.MOVEMENT) {
                val x = center.x + 60f
                drawLine(ink, Offset(x, center.y - 46f), Offset(x, center.y + 42f), 4f, StrokeCap.Round)
                drawLine(ink, Offset(x, center.y + 42f), Offset(x - 10f, center.y + 30f), 4f, StrokeCap.Round)
                drawLine(ink, Offset(x, center.y + 42f), Offset(x + 10f, center.y + 30f), 4f, StrokeCap.Round)
                drawLine(ink, Offset(x, center.y - 46f), Offset(x - 10f, center.y - 34f), 4f, StrokeCap.Round)
                drawLine(ink, Offset(x, center.y - 46f), Offset(x + 10f, center.y - 34f), 4f, StrokeCap.Round)
            }
        }
    }
}

private fun compactGuide(settings: NativeSettings) = if (settings.controlMode == ControlMode.POINTER)
    "Point to aim. Pinch to press, keep pinched to hold, move while pinched to drag, separate to release. Calibrate in Practice before each new external session."
    else when (settings.mode) {
    GestureMode.POSE -> "Point down/up to navigate. Open hand: tap video once. Thumbs-up: double tap. Pointing and thumbs-up can repeat."
    GestureMode.HYBRID -> "Point down/up or flick to navigate; move your palm to scroll. Open hand: tap video once. Thumbs-up: double tap."
    GestureMode.FLICK -> "Flick your palm down/up to navigate. Settle your hand before repeating. Pose taps are off in this mode."
    GestureMode.CONTINUOUS -> "Move your palm vertically to scroll. Hold still to stop scrolling. Pose taps are off in this mode."
} + if (settings.inverted) " Scroll direction is reversed." else ""

internal fun modeLabel(mode: GestureMode) = when (mode) {
    GestureMode.HYBRID -> "Hybrid · poses and movement"
    GestureMode.POSE -> "Poses only"
    GestureMode.FLICK -> "Flicks only"
    GestureMode.CONTINUOUS -> "Continuous movement only"
}

internal fun status(state: GestureSessionState) = when (state) {
    GestureSessionState.Stopped -> "Stopped · camera off"
    GestureSessionState.Starting -> "Starting camera…"
    GestureSessionState.Active -> "Tracking · camera on"
    GestureSessionState.Paused -> "Paused · camera off"
    is GestureSessionState.Error -> "Needs attention · camera off"
}
