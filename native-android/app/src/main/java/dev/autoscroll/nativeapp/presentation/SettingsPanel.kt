package dev.autoscroll.nativeapp.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.autoscroll.nativeapp.gesture.GestureMode
import dev.autoscroll.nativeapp.settings.NativeSettings
import java.util.Locale
import kotlin.math.roundToLong

@Composable
internal fun SettingsPanel(value: NativeSettings, enabled: Boolean, update: (NativeSettings) -> Unit) {
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
    Text("Pointing and tap poses · timed rearm", style = MaterialTheme.typography.titleMedium)
    NumberSetting("Confirm (ms)", value.pose.confirmationDurationMs.toDouble(), 100.0..500.0, enabled, 19) { update(value.copy(pose = value.pose.copy(confirmationDurationMs = it.toLong()))) }
    NumberSetting("Rearm delay (ms)", value.pose.cooldownMs.toDouble(), 200.0..3000.0, enabled, 55) {
        update(value.copy(pose = value.pose.copy(cooldownMs = it.roundToLong())))
    }
    Text("Wait this long after an action, then confirm the next pose. Open hand sends one pause tap per presentation.")
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
