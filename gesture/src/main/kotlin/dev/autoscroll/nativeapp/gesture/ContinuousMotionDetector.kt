package dev.autoscroll.nativeapp.gesture

import kotlin.math.abs

interface PositionSmoother {
    fun add(value: Double): Double
    fun reset()
}

class ExponentialPositionSmoother(val alpha: Double) : PositionSmoother {
    init { require(alpha.isFinite() && alpha > 0 && alpha <= 1) }
    private var value: Double? = null
    override fun add(value: Double): Double {
        val next = this.value?.let { alpha * value + (1 - alpha) * it } ?: value
        this.value = next
        return next
    }
    override fun reset() { value = null }
}

class ContinuousMotionDetector(
    val config: MotionDetectionConfig = MotionDetectionConfig(),
    private val smoother: PositionSmoother = ExponentialPositionSmoother(config.smoothingAlpha),
) {
    private data class Sample(val timeMs: Long, val y: Double)
    private val samples = mutableListOf<Sample>()
    private var lastTime: Long? = null
    private var lastDirectionChange: Long? = null
    private var direction = HandAction.IDLE
    private var candidate = HandAction.IDLE
    private var confirmationCount = 0
    private var hadHand = false
    private var lastRawY: Double? = null
    private var neutralY: Double? = null
    private var stableAnchor: Double? = null
    private var stableSince: Long? = null

    fun process(result: HandTrackingResult): HandMotion {
        val previousTime = lastTime
        if (previousTime != null && result.timestampMs <= previousTime) return HandMotion(HandAction.IDLE)
        if (previousTime != null && result.timestampMs - previousTime > config.maximumSampleGapMs) reset()
        lastTime = result.timestampMs
        val pose = result.pose
        if (pose == null) {
            val action = if (hadHand) HandAction.STOP else HandAction.IDLE
            reset()
            lastTime = result.timestampMs
            return HandMotion(action)
        }
        val first = !hadHand
        val previousY = lastRawY
        if (previousY != null && abs(pose.palmY - previousY) > config.maximumPositionJump) {
            reset()
            return process(result)
        }
        hadHand = true
        lastRawY = pose.palmY
        if (neutralY == null) {
            val anchor = stableAnchor
            if (anchor == null || abs(pose.palmY - anchor) > config.calibrationRadius) {
                stableAnchor = pose.palmY
                stableSince = result.timestampMs
            } else if (result.timestampMs - stableSince!! >= config.calibrationDurationMs) {
                neutralY = if (samples.isEmpty()) pose.palmY else samples.sumOf { it.y } / samples.size
            }
        }
        val rawVelocity = if (previousY == null || previousTime == null) null else
            (pose.palmY - previousY) / ((result.timestampMs - previousTime) / 1000.0)
        if (rawVelocity != null && abs(rawVelocity) <= config.stationaryVelocity) {
            samples.clear()
            smoother.reset()
            direction = HandAction.IDLE
            candidate = HandAction.IDLE
            confirmationCount = 0
        }
        val y = smoother.add(pose.palmY)
        samples.add(Sample(result.timestampMs, y))
        if (samples.size > config.windowSize) samples.removeAt(0)
        if (samples.size < config.minimumFrames) return HandMotion(
            if (first) HandAction.START else HandAction.IDLE, smoothedY = y, neutralY = neutralY,
        )
        val displacement = samples.last().y - samples.first().y
        val origin = samples.first().timeMs
        var sumT = 0.0
        var sumY = 0.0
        var sumTT = 0.0
        var sumTY = 0.0
        for (sample in samples) {
            val t = (sample.timeMs - origin) / 1000.0
            sumT += t
            sumY += sample.y
            sumTT += t * t
            sumTY += t * sample.y
        }
        val n = samples.size
        val denominator = n * sumTT - sumT * sumT
        val velocity = if (denominator <= 0) 0.0 else (n * sumTY - sumT * sumY) / denominator
        val threshold = if (direction == HandAction.IDLE) config.movementThreshold else config.deadZone
        val next = if (abs(displacement) >= threshold && abs(velocity) >= config.minimumVelocity)
            if (velocity < 0) HandAction.MOVE_UP else HandAction.MOVE_DOWN else HandAction.IDLE
        if (next == HandAction.IDLE) {
            direction = HandAction.IDLE
            candidate = HandAction.IDLE
            confirmationCount = 0
        } else if (next != direction) {
            confirmationCount = if (next == candidate) confirmationCount + 1 else 1
            candidate = next
            val changed = lastDirectionChange
            val cooledDown = changed == null || result.timestampMs - changed >= config.directionCooldownMs
            direction = HandAction.IDLE
            if (confirmationCount >= config.confirmationFrames && cooledDown) {
                direction = next
                lastDirectionChange = result.timestampMs
            }
        }
        return HandMotion(direction, displacement, velocity, y, neutralY)
    }

    fun reset() {
        samples.clear()
        smoother.reset()
        lastTime = null
        lastDirectionChange = null
        direction = HandAction.IDLE
        candidate = HandAction.IDLE
        confirmationCount = 0
        hadHand = false
        lastRawY = null
        neutralY = null
        stableAnchor = null
        stableSince = null
    }
}
