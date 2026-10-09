package dev.autoscroll.nativeapp.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.autoscroll.nativeapp.gesture.GestureMode
import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.session.SessionDiagnostics
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.settings.NativeSettings
import dev.autoscroll.nativeapp.settings.SettingsGroup
import dev.autoscroll.nativeapp.settings.reset
import java.util.Locale
import kotlin.math.roundToLong

@Composable
internal fun SettingsPanel(value: NativeSettings, update: (NativeSettings) -> Unit) {
    SettingSwitch("Reverse scroll direction", "Swap which way pointing and hand movement scroll.", value.inverted) {
        update(value.copy(inverted = it))
    }
    SettingSwitch("Floating controls", "Show status, Pause and Stop over your feed. Optional permission is needed.", value.overlayEnabled) {
        update(value.copy(overlayEnabled = it))
    }
}

@Composable
private fun SettingSwitch(title: String, explanation: String, checked: Boolean, change: (Boolean) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = change).padding(16.dp)
            .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(explanation, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked, onCheckedChange = null)
        }
    }
}

@Composable
internal fun AdvancedSettingsPanel(value: NativeSettings, state: GestureSessionState, diagnostics: SessionDiagnostics,
    canTest: Boolean, manualRunning: Boolean, manualResult: String, update: (NativeSettings) -> Unit,
    test: (ScrollAction) -> Unit) {
    Text("Tune recognition only if needed. Reset changes one group in this draft; use Apply changes to save.")
    AdvancedGroup("Recognition mode", { update(value.reset(SettingsGroup.RECOGNITION)) }) {
        GestureMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth().selectable(value.mode == mode, role = Role.RadioButton,
                onClick = { update(value.copy(mode = mode)) }).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(value.mode == mode, onClick = null)
                Text(when (mode) {
                    GestureMode.HYBRID -> "Hybrid · poses and movement"
                    GestureMode.POSE -> "Poses only"
                    GestureMode.FLICK -> "Flicks only"
                    GestureMode.CONTINUOUS -> "Continuous movement only"
                }, Modifier.weight(1f))
            }
        }
    }
    AdvancedGroup("Swipe", { update(value.reset(SettingsGroup.SWIPE)) }) {
        NumberSetting("Duration (ms)", value.swipeDurationMs.toDouble(), 100.0..1000.0, 17) {
            update(value.copy(swipeDurationMs = it.toLong(), scroll = value.scroll.copy(flickDurationMs = it.toLong())))
        }
        NumberSetting("Screen distance", value.swipeDistance, 0.1..0.8) { update(value.copy(swipeDistance = it)) }
    }
    AdvancedGroup("Camera", { update(value.reset(SettingsGroup.CAMERA)) }) {
        NumberSetting("Inference FPS", value.tracking.inferenceFps.toDouble(), 5.0..30.0, 24) {
            update(value.copy(tracking = value.tracking.copy(inferenceFps = it.toInt())))
        }
        NumberSetting("Maximum image dimension", value.tracking.maximumInferenceDimension.toDouble(), 192.0..640.0, 13) {
            update(value.copy(tracking = value.tracking.copy(maximumInferenceDimension = it.toInt())))
        }
    }
    AdvancedGroup("Pointing and tap poses", { update(value.reset(SettingsGroup.POSES)) }) {
        NumberSetting("Confirm (ms)", value.pose.confirmationDurationMs.toDouble(), 100.0..500.0, 19) {
            update(value.copy(pose = value.pose.copy(confirmationDurationMs = it.toLong())))
        }
        NumberSetting("Rearm delay (ms)", value.pose.cooldownMs.toDouble(), 200.0..3000.0, 55) {
            update(value.copy(pose = value.pose.copy(cooldownMs = it.roundToLong())))
        }
        Text("Pointing and thumbs-up can repeat after this delay and a fresh confirmation. Open hand sends one tap per presentation.")
        NumberSetting("Extended index / thumb angle", value.pose.minimumIndexAngle, 150.0..175.0) {
            update(value.copy(pose = value.pose.copy(minimumIndexAngle = it)))
        }
        NumberSetting("Curled finger angle", value.pose.maximumCurledAngle, 80.0..125.0) {
            update(value.copy(pose = value.pose.copy(maximumCurledAngle = it)))
        }
        NumberSetting("Vertical alignment", value.pose.minimumVerticalAlignment, 0.70..1.0) {
            update(value.copy(pose = value.pose.copy(minimumVerticalAlignment = it)))
        }
    }
    AdvancedGroup("Flick", { update(value.reset(SettingsGroup.FLICK)) }) {
        NumberSetting("Minimum speed (heights/s)", value.flick.minimumVelocity, 0.31..1.5) {
            update(value.copy(flick = value.flick.copy(minimumVelocity = it)))
        }
        NumberSetting("Minimum displacement", value.flick.minimumDisplacement, 0.05..0.30) {
            update(value.copy(flick = value.flick.copy(minimumDisplacement = it)))
        }
        NumberSetting("Flick cooldown (ms)", value.flick.cooldownMs.toDouble(), 200.0..1500.0, 25) {
            update(value.copy(flick = value.flick.copy(cooldownMs = it.toLong())))
        }
        NumberSetting("Flick rearm (ms)", value.flick.rearmDurationMs.toDouble(), 100.0..500.0, 19) {
            update(value.copy(flick = value.flick.copy(rearmDurationMs = it.toLong())))
        }
    }
    AdvancedGroup("Continuous movement", { update(value.reset(SettingsGroup.MOVEMENT)) }) {
        NumberSetting("Sensitivity", value.scroll.pixelsPerNormalizedUnit, 300.0..4000.0) {
            update(value.copy(scroll = value.scroll.copy(pixelsPerNormalizedUnit = it)))
        }
        NumberSetting("Motion smoothing", value.motion.smoothingAlpha, 0.1..1.0) {
            update(value.copy(motion = value.motion.copy(smoothingAlpha = it)))
        }
        NumberSetting("Movement threshold", value.motion.movementThreshold, 0.008..0.08) {
            update(value.copy(motion = value.motion.copy(movementThreshold = it)))
        }
    }
    AdvancedGroup("Diagnostics") { Text(status(state)); DiagnosticsPanel(diagnostics) }
    AdvancedGroup("External action tests") {
        Text("These tests act on another app, unlike Practice. Open a feed, return here and select a test. Scroll Vision goes to the background and sends it after one second. Camera stays off; taps land at screen center.")
        Text("Stop tracking and finish accessibility setup first. Actions may interrupt a finger touch. Avoid sensitive screens.")
        Text("Tests use your applied settings; Apply changes first to test a new draft.")
        listOf(ScrollAction.FORWARD to "Test swipe forward", ScrollAction.BACKWARD to "Test swipe backward",
            ScrollAction.SINGLE_TAP to "Test center tap", ScrollAction.DOUBLE_TAP to "Test center double tap").forEach { (action, label) ->
            OutlinedButton(onClick = { test(action) }, enabled = canTest && !manualRunning,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
        }
        Text(manualResult)
    }
}

@Composable
private fun AdvancedGroup(title: String, reset: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("$title · ${if (expanded) "Hide" else "Show"}", style = MaterialTheme.typography.titleMedium)
        }
        if (expanded) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
            if (reset != null) OutlinedButton(onClick = reset, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reset $title") }
        }
    }
}

@Composable
private fun NumberSetting(label: String, value: Double, range: ClosedFloatingPointRange<Double>,
    steps: Int = 0, change: (Double) -> Unit) {
    Text("$label: ${decimal(value, 3)}")
    Slider(value = value.toFloat().coerceIn(range.start.toFloat(), range.endInclusive.toFloat()),
        onValueChange = { change(it.toDouble().coerceIn(range.start, range.endInclusive)) },
        valueRange = range.start.toFloat()..range.endInclusive.toFloat(), steps = steps,
        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label })
}

@Composable
private fun DiagnosticsPanel(diagnostics: SessionDiagnostics) {
    val metrics = diagnostics.metrics
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
    Text("CameraX internal drops are not exposed. Completed dispatch does not guarantee a feed response. Diagnostics stay in memory and clear on process restart.")
}

private fun decimal(value: Double, places: Int = 1) = String.format(Locale.US, "%.${places}f", value)
