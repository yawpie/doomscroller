package dev.autoscroll.nativeapp.practice

import dev.autoscroll.nativeapp.gesture.*
import kotlin.math.abs

internal data class PracticePointerSample(
    val feedback: PointerFeedback = PointerFeedback(),
    val firstCorner: PointerPoint? = null,
    val area: PointerArea? = null,
    val canCapture: Boolean = false,
    val calibrationMessage: String = "Start practice and hold your pointing finger steady.",
    val objectPosition: PointerPoint = PointerPoint(.2, .83),
    val taps: Int = 0,
    val holds: Int = 0,
    val drops: Int = 0,
    val misses: Int = 0,
    val holdMs: Long = 0,
    val lastTarget: Int? = null,
    val lastResult: String = "No touch yet",
)

/** Pure local hit tests; there is deliberately no accessibility adapter. */
internal class PointerPracticeFeedback(config: PointerConfig = PointerConfig()) {
    val calibration = PointerCalibration()
    val detector = PointerDetector(config)
    var state = PracticePointerSample(); private set
    private var lastCalibration: PointerArea? = null
    private var radiusX = .075
    private var radiusY = .06
    private var pressedTarget: Int? = null
    private var grabbing = false
    private var objectStart = state.objectPosition
    private var dragged = false
    private var held = false

    fun setBounds(width: Int, height: Int, targetPixels: Double) {
        if (width <= 0 || height <= 0 || !targetPixels.isFinite() || targetPixels <= 0) return
        radiusX = targetPixels / (width * 2); radiusY = targetPixels / (height * 2)
    }

    fun process(result: HandTrackingResult, nowMs: Long = result.timestampMs): PracticePointerSample {
        calibration.process(result, nowMs)
        val terminal = if (calibration.area != lastCalibration) {
            lastCalibration = calibration.area
            detector.calibrate(calibration.area).action
        } else null
        val feedback = detector.process(result, nowMs).let { if (terminal == PointerAction.CANCEL) it.copy(action = terminal) else it }
        val point = feedback.position
        when (feedback.action) {
            PointerAction.BEGIN -> {
                pressedTarget = point?.let { p -> targets.indexOfFirst { contains(it, p) }.takeIf { it >= 0 } }
                grabbing = point?.let { contains(state.objectPosition, it) } == true
                objectStart = state.objectPosition
                // Store signed offset separately; PointerPoint deliberately permits only normalized coordinates.
                offsetX = if (grabbing) state.objectPosition.x - point!!.x else 0.0
                offsetY = if (grabbing) state.objectPosition.y - point!!.y else 0.0
                dragged = false; held = false
            }
            PointerAction.MOVE -> {
                dragged = true
                if (grabbing && point != null) state = state.copy(objectPosition = PointerPoint(
                    (point.x + offsetX).coerceIn(radiusX.coerceAtMost(.5), (1 - radiusX).coerceAtLeast(.5)),
                    (point.y + offsetY).coerceIn(radiusY.coerceAtMost(.5), (1 - radiusY).coerceAtLeast(.5))))
            }
            PointerAction.END -> {
                state = when {
                    grabbing && dragged && contains(dropTarget, state.objectPosition) -> state.copy(drops = state.drops + 1, lastResult = "Drop hit")
                    dragged -> state.copy(misses = state.misses + 1, lastResult = "Drop miss")
                    held || pressedTarget == 4 && !dragged && feedback.heldMs >= 600 ->
                        state.copy(holds = state.holds + 1, lastResult = "Hold hit")
                    pressedTarget != null -> state.copy(taps = state.taps + 1, lastResult = "Tap hit", lastTarget = pressedTarget)
                    else -> state.copy(misses = state.misses + 1, lastResult = "Tap miss")
                }
                grabbing = false; pressedTarget = null
            }
            PointerAction.CANCEL -> {
                state = state.copy(objectPosition = if (grabbing) objectStart else state.objectPosition,
                    lastResult = "Touch cancelled", holdMs = 0)
                grabbing = false; pressedTarget = null
            }
            null -> Unit
        }
        if (pressedTarget == 4 && !dragged && feedback.heldMs >= 600) held = true
        state = state.copy(feedback = feedback, firstCorner = calibration.firstCorner, area = calibration.area,
            canCapture = calibration.canCapture, calibrationMessage = calibration.message,
            holdMs = if (pressedTarget == 4 && !dragged) feedback.heldMs else 0)
        return state
    }

    fun capture(nowMs: Long): Boolean {
        val captured = calibration.capture(nowMs)
        if (captured && calibration.area != null) {
            lastCalibration = calibration.area
            detector.calibrate(calibration.area)
        }
        state = state.copy(firstCorner = calibration.firstCorner, area = calibration.area,
            canCapture = calibration.canCapture, calibrationMessage = calibration.message, feedback = detector.state)
        return captured
    }

    fun reset() {
        calibration.reset(); detector.reset(); lastCalibration = null
        grabbing = false; pressedTarget = null; held = false; dragged = false
        state = PracticePointerSample()
    }

    private var offsetX = 0.0
    private var offsetY = 0.0
    private fun contains(center: PointerPoint, point: PointerPoint) =
        abs(center.x - point.x) <= radiusX && abs(center.y - point.y) <= radiusY

    companion object {
        val targets = (0..8).map { PointerPoint(.2 + it % 3 * .3, .15 + it / 3 * .2) }
        val dropTarget = PointerPoint(.8, .83)
    }
}
