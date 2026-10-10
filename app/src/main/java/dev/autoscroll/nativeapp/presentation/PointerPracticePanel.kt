package dev.autoscroll.nativeapp.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.autoscroll.nativeapp.gesture.PointerPhase
import dev.autoscroll.nativeapp.gesture.PointerPoint
import dev.autoscroll.nativeapp.practice.PointerPracticeFeedback
import dev.autoscroll.nativeapp.practice.PracticePointerSample
import kotlin.math.roundToInt

@Composable
internal fun PointerPracticePanel(sample: PracticePointerSample, active: Boolean, capture: () -> Unit,
    reset: () -> Unit, use: () -> Unit, sizeChanged: (Int, Int, Double) -> Unit, openBoard: () -> Unit) {
    Text("Pointer practice", style = MaterialTheme.typography.titleLarge)
    Text(sample.calibrationMessage)
    if (sample.area == null) {
        Button(onClick = capture, enabled = active && sample.canCapture,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (sample.firstCorner == null) "Capture upper-left corner" else "Capture lower-right corner")
        }
        Text("Use the screen button with your other hand. Keep thumb and index separate; no pinch is needed for calibration.")
    } else {
        Button(onClick = openBoard, enabled = active, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Open practice board")
        }
        Button(onClick = use, enabled = active, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Use calibration")
        }
        Text("This stops practice. On Home, press Start to control another app with this calibration.")
    }
    OutlinedButton(onClick = reset, enabled = active, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reset calibration") }
    Text("Tap grid · Hold center for 600 ms", style = MaterialTheme.typography.titleMedium)
    Text("Pinch and move to drag", style = MaterialTheme.typography.titleMedium)
    Text("Move the square to Drop, then separate your fingers. Hold before moving to practice long press then drag.")
    PointerPracticeBoard(sample, sizeChanged, Modifier.fillMaxWidth().height(380.dp))
    Text("Tap hits: ${sample.taps} · Hold hits: ${sample.holds} · Drops: ${sample.drops} · Misses: ${sample.misses}")
    Text("Center hold: ${sample.holdMs} ms · ${sample.lastResult}")
    Text("Tracking loss cancels the local touch. Leaving, locking, rotating, or stopping clears calibration. Other apps decide their own long press and drag timing.")
}

@Composable
internal fun PointerPracticeBoard(sample: PracticePointerSample, sizeChanged: (Int, Int, Double) -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    val targetPixels = with(density) { 48.dp.toPx().toDouble() }
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.clipToBounds()
        .background(colors.surfaceContainer).onSizeChanged { sizeChanged(it.width, it.height, targetPixels) }
        .semantics { contentDescription = "Local pointer practice board" }) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        @Composable fun Target(point: PointerPoint, text: String, description: String, filled: Boolean = false) {
            Box(Modifier.offset { IntOffset((point.x * width - targetPixels / 2).roundToInt(),
                (point.y * height - targetPixels / 2).roundToInt()) }.size(48.dp)
                .semantics { contentDescription = description }
                .background(if (filled) colors.primaryContainer else colors.surface)
                .border(2.dp, colors.outline), contentAlignment = Alignment.Center) {
                Text(text, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.wrapContentSize(unbounded = true), softWrap = false,
                    color = if (filled) colors.onPrimaryContainer else colors.onSurface)
            }
        }
        PointerPracticeFeedback.targets.forEachIndexed { index, point -> Target(point, if (index == 4) "Hold" else "${index + 1}", "Pointer target ${index + 1}") }
        Target(PointerPracticeFeedback.dropTarget, "Drop", "Drop zone")
        Target(sample.objectPosition, "Drag", "Drag object", true)
        if (sample.feedback.visible) sample.feedback.position?.let { point ->
            val contact = sample.feedback.phase in listOf(PointerPhase.PRESSED, PointerPhase.DRAGGING, PointerPhase.RELEASING)
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Local pointer cursor" }) {
                val center = Offset(point.x.toFloat() * size.width, point.y.toFloat() * size.height)
                drawCircle(colors.onSurface, 11.dp.toPx(), center)
                drawCircle(if (contact) colors.tertiaryContainer else colors.primaryContainer, 8.dp.toPx(), center)
            }
        }
    }
}

internal fun pointerPhaseLabel(phase: PointerPhase) = when (phase) {
    PointerPhase.UNCALIBRATED -> "Calibrate your pointing area"
    PointerPhase.WAITING_RELEASE -> "Separate thumb and index, then point steadily"
    PointerPhase.ACQUIRING -> "Hold your pointing finger steady"
    PointerPhase.TRACKING -> "Pointing · ready to pinch"
    PointerPhase.CONFIRMING -> "Confirming pinch · start position frozen"
    PointerPhase.PRESSED -> "Touch held · move to drag"
    PointerPhase.DRAGGING -> "Dragging · separate fingers to drop"
    PointerPhase.RELEASING -> "Confirming release · final position frozen"
    PointerPhase.LOST -> "Tracking lost · separate fingers before retrying"
}
