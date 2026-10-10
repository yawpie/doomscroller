package dev.autoscroll.nativeapp.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.practice.PracticeSample
import dev.autoscroll.nativeapp.practice.PracticeState
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.settings.NativeSettings
import dev.autoscroll.nativeapp.settings.ControlMode
import dev.autoscroll.nativeapp.practice.PracticePointerSample
import kotlin.math.roundToInt

@Composable
internal fun PracticeScreen(state: PracticeState, settings: NativeSettings, cameraGranted: Boolean,
    cameraBlocked: Boolean, cameraExplanation: String?, permissionError: String?,
    onCameraPermission: () -> Unit, onStart: () -> Unit, onStop: () -> Unit, onExit: () -> Unit,
    onCaptureCorner: () -> Unit = {}, onResetCalibration: () -> Unit = {}, onUseCalibration: () -> Unit = {},
    onPointerBoardSize: (Int, Int, Double) -> Unit = { _, _, _ -> }) {
    BackHandler(onBack = onExit)
    var showHandTracking by rememberSaveable { mutableStateOf(true) }
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            val wide = maxWidth > maxHeight
            val cameraHeight = (maxHeight * if (wide) .45f else .20f).coerceIn(64.dp, if (wide) 200.dp else 160.dp)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PracticeCameraPreview(state, showHandTracking,
                            Modifier.weight(1f).height(cameraHeight))
                        PracticeLiveFeedback(state, settings, Modifier.weight(1f))
                    }
                } else {
                    PracticeCameraPreview(state, showHandTracking,
                        Modifier.fillMaxWidth().height(cameraHeight))
                    PracticeLiveFeedback(state, settings, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to Gestures") }
                    Text("Practice safely", style = MaterialTheme.typography.headlineSmall)
                    Text("Only this local practice responds. No actions go to other apps. Camera images stay on your device.")
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = showHandTracking, role = Role.Switch, onValueChange = { showHandTracking = it }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Show hand tracking", modifier = Modifier.weight(1f))
                        Switch(checked = showHandTracking, onCheckedChange = null)
                    }
                    Text("Show or hide the hand landmarks. Tracking keeps running until you stop practice.",
                        style = MaterialTheme.typography.bodyMedium)
                    if (permissionError != null) Text(permissionError, color = MaterialTheme.colorScheme.error)
                    if (!cameraGranted) {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Camera access needed", style = MaterialTheme.typography.titleMedium)
                                Text(cameraExplanation ?: "Allow the front camera to detect your hand. Accessibility and floating controls are not needed for practice.")
                                Button(onClick = onCameraPermission, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(if (cameraBlocked) "Open camera settings" else "Allow camera")
                                }
                            }
                        }
                    }
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.message != null) Text(state.message,
                                color = if (state.session is GestureSessionState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            if (state.session == GestureSessionState.Active && !state.sample.handDetected)
                                Text("Show your whole hand in front of the camera, with good light.")
                            Button(onClick = onStart, enabled = cameraGranted && state.session != GestureSessionState.Active &&
                                state.session != GestureSessionState.Starting && permissionError == null,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start practice") }
                            TextButton(onClick = onStop, enabled = state.session == GestureSessionState.Active || state.session == GestureSessionState.Starting,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Stop practice") }
                            Text("Leaving or locking stops practice. To control another app, return to Home and press Start or Resume.",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (settings.controlMode == ControlMode.POINTER) {
                        PointerPracticePanel(state.sample.pointer ?: PracticePointerSample(),
                            state.session == GestureSessionState.Active, onCaptureCorner, onResetCalibration,
                            onUseCalibration, onPointerBoardSize)
                    } else {
                    Text("Current mode: ${modeLabel(settings.mode)}${if (settings.inverted) " · direction reversed" else ""}")
                    SampleFeed(state.sample)
                    Text("Try a gesture", style = MaterialTheme.typography.titleMedium)
                    if (settings.mode == GestureMode.POSE || settings.mode == GestureMode.HYBRID) {
                        Text("Point down for ${if (settings.inverted) "previous" else "next"}; point up for ${if (settings.inverted) "next" else "previous"}. Holding a pointing pose or thumbs-up can repeat after the saved rearm delay plus fresh confirmation.")
                        Text("Open hand: Tap video once. It toggles this sample's playback, while tracking stays on. A held open hand will not tap again, even after tracking loss. Show a different visible pose before another open-hand tap.")
                        Text("Thumbs-up: Double tap. This sample counts it; another app decides what a double tap does.")
                    }
                    if (settings.mode == GestureMode.FLICK || settings.mode == GestureMode.HYBRID)
                        Text("Flick your palm down or up to navigate, then settle it before repeating. In Hybrid, curl your fingers and relax your thumb to avoid a pose tap.")
                    if (settings.mode == GestureMode.CONTINUOUS || settings.mode == GestureMode.HYBRID)
                        Text("Move your palm vertically to move the sample feed; hold still to stop. In Hybrid, curl your fingers and relax your thumb. Poses and flicks take priority.")
                    Text("Tap video changes playback. Stop practice releases the camera. Local movement is a demonstration; real feeds may respond differently.",
                        style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeLiveFeedback(state: PracticeState, settings: NativeSettings, modifier: Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Live feedback", style = MaterialTheme.typography.titleMedium)
            Text(when (state.session) {
                GestureSessionState.Starting -> "Starting practice…"
                GestureSessionState.Active -> if (state.sample.handDetected) "Hand detected" else "No hand detected"
                is GestureSessionState.Error -> "Practice needs attention"
                else -> "Practice stopped"
            }, style = MaterialTheme.typography.titleMedium)
            if (settings.controlMode == ControlMode.POINTER) {
                val pointer = state.sample.pointer
                Text(pointerPhaseLabel(pointer?.feedback?.phase ?: PointerPhase.UNCALIBRATED), style = MaterialTheme.typography.bodyMedium)
                Text(pointer?.feedback?.message ?: "Point with your index finger. Pinch to press, move to drag, separate to release.")
                Text("Last result: ${pointer?.lastResult ?: "No touch yet"}", style = MaterialTheme.typography.bodyMedium)
            } else {
            Text("Last action: ${state.sample.lastAction}", style = MaterialTheme.typography.bodyMedium)
            Text("Confirmed actions: ${state.sample.actionCount} · Double taps: ${state.sample.doubleTaps}",
                style = MaterialTheme.typography.bodyMedium)
            if (settings.mode == GestureMode.POSE || settings.mode == GestureMode.HYBRID) {
                Text("Pose: ${poseLabel(state.sample.pose)}", style = MaterialTheme.typography.bodyMedium)
                Text(when (state.sample.phase) {
                    HandPosePhase.CONFIRMING -> "Hold steady · confirming"
                    HandPosePhase.LATCHED -> if (state.sample.cooldownMs > 0) "Rearm delay: ${state.sample.cooldownMs} ms"
                        else "Ready for fresh confirmation"
                    HandPosePhase.WAITING_REARM -> "Waiting for the configured release pose"
                    HandPosePhase.READY -> "Ready for a gesture"
                }, style = MaterialTheme.typography.bodyMedium)
                if (state.sample.phase == HandPosePhase.CONFIRMING)
                    LinearProgressIndicator(progress = { state.sample.confirmation.toFloat() },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Pose confirmation progress" })
            }
            if (state.sample.velocity != 0.0) Text("Feed moves while your palm moves. Hold still to stop.",
                style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SampleFeed(sample: PracticeSample) {
    Text("Sample feed · item ${(sample.position.roundToInt() + 1).coerceIn(1, 5)} of 5",
        style = MaterialTheme.typography.titleMedium)
    val cardHeight = 208.dp
    val colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.primaryContainer)
    val inks = listOf(MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(cardHeight).clipToBounds().semantics {
            contentDescription = "Local sample feed. ${if (sample.videoPlaying) "Video playing" else "Video paused"}. Double taps: ${sample.doubleTaps}"
        }) {
            Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                .offset { IntOffset(0, -(sample.position * cardHeight.toPx()).roundToInt()) }) {
                listOf("Morning walk", "Fresh coffee", "Ocean view", "City lights", "Mountain trail").forEachIndexed { index, title ->
                    Column(Modifier.fillMaxWidth().height(cardHeight).background(colors[index]).padding(20.dp),
                        verticalArrangement = Arrangement.Center) {
                        Text(title, color = inks[index], style = MaterialTheme.typography.headlineSmall)
                        Text("Sample ${index + 1}", color = inks[index])
                    }
                }
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (sample.videoPlaying) "Video playing" else "Video paused", style = MaterialTheme.typography.titleMedium)
            Text("Double taps: ${sample.doubleTaps}")
        }
    }
}

private fun poseLabel(pose: RecognizedHandPose) = when (pose) {
    RecognizedHandPose.POINTING_UP -> "Pointing up"
    RecognizedHandPose.POINTING_DOWN -> "Pointing down"
    RecognizedHandPose.NEUTRAL -> "Open hand · Tap video"
    RecognizedHandPose.THUMBS_UP -> "Thumbs-up · Double tap"
    RecognizedHandPose.CLOSED_HAND -> "Curled hand"
    RecognizedHandPose.UNKNOWN -> "Not recognized"
}
