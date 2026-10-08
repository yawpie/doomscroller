package dev.autoscroll.nativeapp.gesture

import kotlin.math.abs

class FlickDetector(val config: FlickDetectionConfig = FlickDetectionConfig()) {
    init {
        require(listOf(config.minimumVelocity, config.minimumDisplacement, config.directionConsistency,
            config.maximumPositionJump, config.rearmVelocity, config.rearmRadius).all { it.isFinite() } &&
            config.minimumVelocity > 0 && config.minimumDisplacement > 0 && config.minimumDisplacement <= 1 &&
            config.directionConsistency > 0 && config.directionConsistency <= 1 &&
            config.maximumPositionJump > 0 && config.maximumPositionJump <= 1 &&
            config.rearmVelocity >= 0 && config.rearmVelocity < config.minimumVelocity * 0.5 &&
            config.rearmRadius > 0 && config.rearmRadius < config.minimumDisplacement &&
            config.minimumDurationMs > 0 && config.maximumDurationMs >= config.minimumDurationMs &&
            config.cooldownMs >= 0 && config.maximumSampleGapMs > 0 && config.rearmDurationMs > 0) {
            "Invalid flick detection thresholds or durations."
        }
    }

    private var previousTime: Long? = null
    private var previousX: Double? = null
    private var previousY: Double? = null
    private var candidateStart: Long? = null
    private var candidateY = 0.0
    private var path = 0.0
    private var sampleCount = 0
    private var direction: FlickDirection? = null
    private var lastFlickAt: Long? = null
    private var held = false
    private var suppressed = false
    private var stableSince: Long? = null
    private var stableX: Double? = null
    private var stableY: Double? = null

    val suppressesContinuous: Boolean get() = suppressed
    val ownsGesture: Boolean get() = held || candidateStart != null

    fun process(result: HandTrackingResult): FlickEvent? {
        suppressed = held
        val time = result.timestampMs
        val priorTime = previousTime
        if (time < 0 || (priorTime != null && time <= priorTime)) {
            hold()
            return null
        }
        val pose = result.pose
        if (pose == null) {
            loseTracking()
            return null
        }
        val x = pose.palmX
        val y = pose.palmY
        val priorX = previousX
        val priorY = previousY
        previousTime = time
        previousX = x
        previousY = y
        if (priorTime == null || priorX == null || priorY == null) {
            if (held) rearm(time, x, y, 0.0)
            suppressed = held
            return null
        }
        val gap = time - priorTime
        val dx = x - priorX
        val dy = y - priorY
        val jump = config.maximumPositionJump
        if (gap > config.maximumSampleGapMs || dx * dx + dy * dy > jump * jump) {
            hold()
            return null
        }
        val velocity = dy / (gap / 1000.0)
        if (held) {
            rearm(time, x, y, velocity)
            suppressed = held
            return null
        }
        val start = candidateStart
        if (start == null) {
            if (abs(velocity) >= config.minimumVelocity) {
                candidateStart = priorTime
                candidateY = priorY
                direction = if (dy < 0) FlickDirection.UP else FlickDirection.DOWN
                path = abs(dy)
                sampleCount = 2
                suppressed = true
            }
            return null
        }
        suppressed = true
        path += abs(dy)
        sampleCount++
        val duration = time - start
        val reversed = if (direction == FlickDirection.UP) dy > 0 else dy < 0
        if ((reversed && abs(dy) > config.rearmRadius) || abs(velocity) < config.minimumVelocity * 0.5 ||
            duration > config.maximumDurationMs) {
            hold()
            return null
        }
        val displacement = y - candidateY
        val meanVelocity = displacement / (duration / 1000.0)
        val sameDirection = if (direction == FlickDirection.UP) displacement < 0 else displacement > 0
        if (sampleCount < 3 || duration < config.minimumDurationMs ||
            abs(displacement) < config.minimumDisplacement || abs(meanVelocity) < config.minimumVelocity ||
            !sameDirection || path == 0.0 || abs(displacement) / path < config.directionConsistency) return null
        val event = FlickEvent(direction!!, time, displacement, meanVelocity, duration)
        lastFlickAt = time
        hold()
        return event
    }

    private fun hold() {
        held = true
        suppressed = true
        clearCandidate()
        clearStable()
    }

    private fun rearm(time: Long, x: Double, y: Double, velocity: Double) {
        val anchorX = stableX
        val anchorY = stableY
        val radius = config.rearmRadius
        val moved = anchorX != null && anchorY != null &&
            (x - anchorX) * (x - anchorX) + (y - anchorY) * (y - anchorY) > radius * radius
        if (abs(velocity) > config.rearmVelocity || moved) {
            clearStable()
            return
        }
        if (stableSince == null) stableSince = time
        if (stableX == null) stableX = x
        if (stableY == null) stableY = y
        val lastFlick = lastFlickAt
        val cooledDown = lastFlick == null || time - lastFlick >= config.cooldownMs
        if (cooledDown && time - stableSince!! >= config.rearmDurationMs) {
            held = false
            clearStable()
        }
    }

    private fun clearCandidate() {
        candidateStart = null
        candidateY = 0.0
        path = 0.0
        sampleCount = 0
        direction = null
    }

    private fun clearStable() {
        stableSince = null
        stableX = null
        stableY = null
    }

    fun loseTracking() {
        if (candidateStart != null) held = true
        previousTime = null
        previousX = null
        previousY = null
        clearCandidate()
        clearStable()
        suppressed = true
    }

    fun reset() {
        previousTime = null
        lastFlickAt = null
        previousX = null
        previousY = null
        held = false
        suppressed = false
        clearCandidate()
        clearStable()
    }
}
