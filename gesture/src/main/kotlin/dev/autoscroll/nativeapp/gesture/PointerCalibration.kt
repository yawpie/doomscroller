package dev.autoscroll.nativeapp.gesture

class PointerCalibration {
    var area: PointerArea? = null; private set
    var firstCorner: PointerPoint? = null; private set
    var canCapture = false; private set
    var message = "Start practice, point to the upper-left corner and hold steady."; private set
    private var lastTime: Long? = null
    private var stableAt: Long? = null
    private var anchor: PointerPoint? = null
    private var current: PointerPoint? = null
    private var aspect: Double? = null

    fun process(result: HandTrackingResult, nowMs: Long = result.timestampMs) {
        val t = result.timestampMs
        if (t < 0 || t > nowMs || nowMs - t > 300) { clearSamples(); return }
        if (lastTime != null && t <= lastTime!!) return
        if (lastTime?.let { t - it > 300 } == true) clearSamples()
        if (aspect != null && aspect != result.uprightAspectRatio) reset()
        aspect = result.uprightAspectRatio
        lastTime = t
        val hand = result.pose
        if (hand == null || !pointerIndexExtended(hand, result.uprightAspectRatio) ||
            pointerPinchRatio(hand, result.uprightAspectRatio).let { !it.isFinite() || it < .36 }) {
            stableAt = null; anchor = null; current = null; canCapture = false
            return
        }
        current = hand.landmarks[8].let { PointerPoint(it.x, it.y) }
        if (anchor == null || distance(anchor!!, current!!, result.uprightAspectRatio) > .025) {
            anchor = current; stableAt = t
        }
        canCapture = area == null && t - (stableAt ?: t) >= 180
    }

    fun capture(nowMs: Long): Boolean {
        if (!canCapture || lastTime?.let { nowMs < it || nowMs - it > 300 } != false) {
            canCapture = false
            message = "Show your whole hand, separate thumb and index finger, then hold your pointing finger steady."
            return false
        }
        val point = checkNotNull(current)
        if (firstCorner == null) {
            firstCorner = point
            message = "Now point to the lower-right corner and hold steady."
        } else {
            val first = checkNotNull(firstCorner)
            val captured = runCatching { PointerArea(first.x, first.y, point.x, point.y) }.getOrNull()
            if (captured == null) {
                message = "Choose a lower-right corner at least 15% farther right and lower. Try again."
                return false
            }
            area = captured
            message = "Calibrated. Point, pinch to press, move while pinched to drag, separate to release."
        }
        clearSamples()
        return true
    }

    fun reset() {
        area = null; firstCorner = null; aspect = null; clearSamples()
        message = "Point to your comfortable upper-left corner and hold steady."
    }

    private fun clearSamples() { lastTime = null; stableAt = null; anchor = null; current = null; canCapture = false }
}
