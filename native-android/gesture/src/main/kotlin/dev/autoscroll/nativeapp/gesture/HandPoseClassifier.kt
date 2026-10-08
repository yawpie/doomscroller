package dev.autoscroll.nativeapp.gesture

import kotlin.math.acos
import kotlin.math.PI
import kotlin.math.sqrt

class HandPoseClassifier(val config: HandPoseDetectionConfig = HandPoseDetectionConfig()) {
    init {
        require(listOf(config.minimumIndexAngle, config.maximumCurledAngle, config.minimumVerticalAlignment,
            config.exitIndexAngle, config.exitCurledAngle, config.exitVerticalAlignment,
            config.minimumFingerLengthRatio, config.minimumBoneLengthRatio).all { it.isFinite() } &&
            config.minimumIndexAngle <= 180 && config.exitIndexAngle > 0 &&
            config.exitIndexAngle <= config.minimumIndexAngle && config.maximumCurledAngle > 0 &&
            config.maximumCurledAngle <= config.exitCurledAngle && config.exitCurledAngle < config.exitIndexAngle &&
            config.minimumVerticalAlignment <= 1 && config.exitVerticalAlignment > 0 &&
            config.exitVerticalAlignment <= config.minimumVerticalAlignment && config.minimumFingerLengthRatio > 0 &&
            config.minimumBoneLengthRatio > 0 &&
            config.minimumBoneLengthRatio * 3 <= config.minimumFingerLengthRatio) {
            "Invalid hand pose geometry thresholds."
        }
    }

    fun classify(points: List<HandLandmark>, activePose: RecognizedHandPose? = null): RecognizedHandPose {
        if (!validLandmarks(points)) return RecognizedHandPose.UNKNOWN
        val palmLength = distance(points[0], points[9])
        if (palmLength <= 1e-9) return RecognizedHandPose.UNKNOWN
        for (mcp in 5..17 step 4) {
            if (!validFinger(points, mcp, palmLength)) return RecognizedHandPose.UNKNOWN
        }
        if ((activePose == RecognizedHandPose.POINTING_UP || activePose == RecognizedHandPose.POINTING_DOWN) &&
            pointing(points, activePose, exit = true)) return activePose
        if (pointing(points, RecognizedHandPose.POINTING_UP)) return RecognizedHandPose.POINTING_UP
        if (pointing(points, RecognizedHandPose.POINTING_DOWN)) return RecognizedHandPose.POINTING_DOWN
        for (mcp in 5..17 step 4) {
            if (!extended(points, mcp, config.minimumIndexAngle)) return RecognizedHandPose.UNKNOWN
        }
        return RecognizedHandPose.NEUTRAL
    }

    private fun pointing(points: List<HandLandmark>, pose: RecognizedHandPose, exit: Boolean = false): Boolean {
        if (!extended(points, 5, if (exit) config.exitIndexAngle else config.minimumIndexAngle)) return false
        val curledAngle = if (exit) config.exitCurledAngle else config.maximumCurledAngle
        for (mcp in 9..17 step 4) {
            if (angle(points[mcp], points[mcp + 1], points[mcp + 2]) > curledAngle) return false
        }
        val dx = points[8].x - points[5].x
        val dy = points[8].y - points[5].y
        val length = sqrt(dx * dx + dy * dy)
        if (length <= 1e-9) return false
        val alignment = dy / length
        val threshold = if (exit) config.exitVerticalAlignment else config.minimumVerticalAlignment
        return if (pose == RecognizedHandPose.POINTING_DOWN) alignment >= threshold else alignment <= -threshold
    }

    private fun extended(points: List<HandLandmark>, mcp: Int, threshold: Double): Boolean =
        angle(points[mcp], points[mcp + 1], points[mcp + 2]) >= threshold &&
            angle(points[mcp + 1], points[mcp + 2], points[mcp + 3]) >= threshold

    private fun validFinger(points: List<HandLandmark>, mcp: Int, palmLength: Double): Boolean {
        var length = 0.0
        for (index in mcp until mcp + 3) {
            val bone = distance(points[index], points[index + 1])
            if (bone < palmLength * config.minimumBoneLengthRatio) return false
            length += bone
        }
        return length >= palmLength * config.minimumFingerLengthRatio
    }

    private fun distance(a: HandLandmark, b: HandLandmark): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun angle(a: HandLandmark, joint: HandLandmark, b: HandLandmark): Double {
        val ax = a.x - joint.x
        val ay = a.y - joint.y
        val bx = b.x - joint.x
        val by = b.y - joint.y
        val denominator = sqrt((ax * ax + ay * ay) * (bx * bx + by * by))
        if (denominator <= 1e-18) return 0.0
        return acos(((ax * bx + ay * by) / denominator).coerceIn(-1.0, 1.0)) * 180 / PI
    }
}
