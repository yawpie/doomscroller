package dev.autoscroll.nativeapp.gesture

import kotlin.math.acos
import kotlin.math.hypot

data class PointerPoint(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0) }
}
data class PointerArea(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0.0..1.0 })
        require(right - left >= .15 && bottom - top >= .15) { "Choose upper-left then lower-right corners at least 15% apart." }
    }
    fun map(point: PointerPoint) = PointerPoint(((point.x - left) / (right - left)).coerceIn(0.0, 1.0),
        ((point.y - top) / (bottom - top)).coerceIn(0.0, 1.0))
}
data class PointerConfig(val smoothingAlpha: Double = .4, val pinchEnter: Double = .22, val pinchExit: Double = .36,
    val confirmationMs: Long = 120, val minimumPinchSamples: Int = 3, val releaseMs: Long = 100, val stableMs: Long = 180,
    val maximumGapMs: Long = 300, val freshnessMs: Long = 300, val dragThreshold: Double = .015,
    val maximumJump: Double = .25) {
    init {
        require(smoothingAlpha.isFinite() && smoothingAlpha > 0 && smoothingAlpha <= 1)
        require(pinchEnter.isFinite() && pinchExit.isFinite() && pinchEnter > 0 && pinchExit > pinchEnter)
        require(confirmationMs > 0 && releaseMs > 0 && stableMs > 0 && maximumGapMs > 0 && freshnessMs > 0)
        require(minimumPinchSamples >= 2)
        require(dragThreshold.isFinite() && dragThreshold > 0 && maximumJump.isFinite() && maximumJump > 0)
    }
}
enum class PointerPhase { UNCALIBRATED, WAITING_RELEASE, ACQUIRING, TRACKING, CONFIRMING, PRESSED, DRAGGING, RELEASING, LOST }
enum class PointerAction { BEGIN, MOVE, END, CANCEL }
data class PointerFeedback(val phase: PointerPhase = PointerPhase.UNCALIBRATED, val position: PointerPoint? = null,
    val visible: Boolean = false, val action: PointerAction? = null, val timestampMs: Long = 0,
    val heldMs: Long = 0, val message: String? = null)
class PointerDetector(val config: PointerConfig = PointerConfig()) {
    var state = PointerFeedback(); private set
    private var area: PointerArea? = null
    private val smoothX = ExponentialPositionSmoother(config.smoothingAlpha)
    private val smoothY = ExponentialPositionSmoother(config.smoothingAlpha)
    private var lastTime: Long? = null
    private var lastTip: PointerPoint? = null
    private var stableTip: PointerPoint? = null
    private var since: Long? = null
    private var reference: PointerPoint? = null
    private var origin: PointerPoint? = null
    private var contact = false
    private var pressedAt = 0L
    private var dragging = false
    private var pinchSamples = 0
    private var aspect: Double? = null

    fun calibrate(area: PointerArea?): PointerFeedback {
        val terminal = reset().action
        this.area = area
        state = state.copy(phase = if (area == null) PointerPhase.UNCALIBRATED else PointerPhase.WAITING_RELEASE, action = terminal)
        return state
    }

    fun process(result: HandTrackingResult, nowMs: Long = result.timestampMs): PointerFeedback {
        state = state.copy(action = null)
        val time = result.timestampMs
        if (time < 0 || time > nowMs || nowMs - time > config.freshnessMs) return cancel("Tracking is not fresh")
        val previous = lastTime
        if (previous != null && time <= previous) return state
        lastTime = time
        if (previous != null && time - previous > config.maximumGapMs) return cancel("Tracking gap")
        val hand = result.pose ?: return cancel("Show your whole hand")
        if (aspect != null && aspect != result.uprightAspectRatio) {
            calibrate(null)
            return state.copy(message = "Image orientation changed. Calibrate again.").also { state = it }
        }
        aspect = result.uprightAspectRatio
        val tip = hand.landmarks[8].let { PointerPoint(it.x, it.y) }
        if (lastTip?.let { distance(it, tip, result.uprightAspectRatio) > config.maximumJump } == true) {
            lastTip = null
            return cancel("Hand moved too far. Separate your fingers and point steadily.")
        }
        lastTip = tip
        val calibration = area ?: return state.copy(phase = PointerPhase.UNCALIBRATED,
            visible = false, timestampMs = time, message = "Calibrate two corners first").also { state = it }
        val ratio = pointerPinchRatio(hand, result.uprightAspectRatio)
        if (!ratio.isFinite()) return cancel("Show a clear whole hand")
        state = state.copy(timestampMs = time, message = null, heldMs = if (contact) time - pressedAt else 0)
        when (state.phase) {
            PointerPhase.WAITING_RELEASE, PointerPhase.LOST, PointerPhase.ACQUIRING, PointerPhase.UNCALIBRATED -> {
                if (ratio < config.pinchExit || !pointerIndexExtended(hand, result.uprightAspectRatio)) {
                    since = null; stableTip = null
                    state = state.copy(phase = PointerPhase.WAITING_RELEASE, visible = false)
                } else {
                    if (stableTip == null || distance(stableTip!!, tip, result.uprightAspectRatio) > .025) {
                        since = time; stableTip = tip
                    }
                    if (time - (since ?: time) >= config.stableMs) {
                        smoothX.reset(); smoothY.reset()
                        state = state.copy(phase = PointerPhase.TRACKING, position = calibration.map(tip), visible = true)
                        since = null; stableTip = null
                    } else state = state.copy(phase = PointerPhase.ACQUIRING, visible = false)
                }
            }
            PointerPhase.TRACKING -> {
                if (ratio <= config.pinchEnter) {
                    since = time
                    pinchSamples = 1
                    state = state.copy(phase = PointerPhase.CONFIRMING)
                } else if (pointerIndexExtended(hand, result.uprightAspectRatio)) {
                    state = state.copy(position = smooth(calibration.map(tip)))
                } else return cancel("Extend your index finger to point")
            }
            PointerPhase.CONFIRMING -> {
                if (ratio >= config.pinchExit) {
                    since = null; pinchSamples = 0
                    state = state.copy(phase = PointerPhase.TRACKING)
                } else if (++pinchSamples >= config.minimumPinchSamples && time - (since ?: time) >= config.confirmationMs) {
                    contact = true; dragging = false; pressedAt = time; reference = tip; origin = state.position
                    smoothX.reset(); smoothY.reset()
                    state.position?.let { smooth(it) }
                    state = state.copy(phase = PointerPhase.PRESSED, action = PointerAction.BEGIN, heldMs = 0)
                }
            }
            PointerPhase.PRESSED, PointerPhase.DRAGGING -> {
                if (ratio >= config.pinchExit) {
                    since = time
                    state = state.copy(phase = PointerPhase.RELEASING)
                } else {
                    val anchor = checkNotNull(origin); val start = checkNotNull(reference)
                    val target = PointerPoint((anchor.x + (tip.x - start.x) / (calibration.right - calibration.left)).coerceIn(0.0, 1.0),
                        (anchor.y + (tip.y - start.y) / (calibration.bottom - calibration.top)).coerceIn(0.0, 1.0))
                    if (dragging || distance(anchor, target, 1.0) > config.dragThreshold) {
                        dragging = true
                        state = state.copy(phase = PointerPhase.DRAGGING, position = smooth(target), action = PointerAction.MOVE)
                    }
                }
            }
            PointerPhase.RELEASING -> {
                if (ratio < config.pinchExit) {
                    since = null
                    state = state.copy(phase = if (dragging) PointerPhase.DRAGGING else PointerPhase.PRESSED)
                } else if (time - (since ?: time) >= config.releaseMs) {
                    contact = false; since = null; reference = null; origin = null
                    smoothX.reset(); smoothY.reset()
                    state = state.copy(phase = PointerPhase.WAITING_RELEASE, action = PointerAction.END)
                }
            }
        }
        return state
    }

    fun cancel(message: String? = null): PointerFeedback {
        val action = if (contact) PointerAction.CANCEL else null
        contact = false; reference = null; origin = null; since = null; stableTip = null; lastTip = null; dragging = false; pinchSamples = 0
        smoothX.reset(); smoothY.reset()
        state = state.copy(phase = if (area == null) PointerPhase.UNCALIBRATED else PointerPhase.LOST,
            visible = false, action = action, heldMs = 0, message = message)
        return state
    }

    fun reset(): PointerFeedback {
        val cancelled = cancel()
        area = null; lastTime = null; aspect = null
        state = PointerFeedback(action = cancelled.action)
        return state
    }

    private fun smooth(point: PointerPoint) = PointerPoint(smoothX.add(point.x), smoothY.add(point.y))
}

internal fun distance(a: PointerPoint, b: PointerPoint, aspect: Double) = hypot((a.x - b.x) * aspect, a.y - b.y)
fun pointerPinchRatio(hand: HandPose, aspect: Double): Double {
    fun point(index: Int) = hand.landmarks[index].let { PointerPoint(it.x, it.y) }
    val palm = maxOf(distance(point(0), point(9), aspect), distance(point(5), point(17), aspect))
    return if (palm < .025) Double.NaN else distance(point(4), point(8), aspect) / palm
}

fun pointerIndexExtended(hand: HandPose, aspect: Double): Boolean {
    val a = hand.landmarks[5]; val b = hand.landmarks[6]; val c = hand.landmarks[8]
    val ax = (a.x - b.x) * aspect; val ay = a.y - b.y
    val cx = (c.x - b.x) * aspect; val cy = c.y - b.y
    val length = hypot(ax, ay) * hypot(cx, cy)
    if (length < 1e-6) return false
    return acos(((ax * cx + ay * cy) / length).coerceIn(-1.0, 1.0)) * 180 / Math.PI >= 150 &&
        hypot((a.x - c.x) * aspect, a.y - c.y) >= .06
}
