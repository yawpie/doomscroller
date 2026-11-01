package dev.autoscroll.nativeapp.gesture

import kotlin.math.acos
import kotlin.math.hypot
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

    private enum class Finger { OPEN, CLOSED, UNKNOWN }

    /** Geometry in upright, mirrored screen coordinates. Null means ambiguous or invalid. */
    fun classify(points: List<HandLandmark>, aspect: Double, profile: PoseRecognitionProfile,
        active: PoseId? = null): PoseId? {
        if (!validLandmarks(points) || !aspect.isFinite() || aspect <= 0 ||
            points.any { it.x <= 0 || it.x >= 1 || it.y <= 0 || it.y >= 1 }) return null
        fun distance(a: Int, b: Int) = hypot((points[a].x - points[b].x) * aspect, points[a].y - points[b].y)
        fun angle(a: Int, b: Int, c: Int): Double {
            val ax = (points[a].x - points[b].x) * aspect
            val ay = points[a].y - points[b].y
            val bx = (points[c].x - points[b].x) * aspect
            val by = points[c].y - points[b].y
            val denominator = hypot(ax, ay) * hypot(bx, by)
            return if (denominator <= 1e-12) 0.0 else acos(((ax * bx + ay * by) / denominator).coerceIn(-1.0, 1.0)) * 180 / PI
        }
        val palm = distance(0, 9)
        if (palm <= 1e-9) return null
        for (mcp in listOf(1, 5, 9, 13, 17)) {
            val lengths = (mcp until mcp + 3).map { distance(it, it + 1) }
            if (lengths.any { it < palm * config.minimumBoneLengthRatio } ||
                lengths.sum() < palm * config.minimumFingerLengthRatio) return null
        }
        fun recognize(exit: Boolean): PoseId? {
            val openAngle = if (exit) config.exitIndexAngle else config.minimumIndexAngle
            val closedAngle = if (exit) config.exitCurledAngle else config.maximumCurledAngle
            fun finger(mcp: Int): Finger {
                val first = angle(mcp, mcp + 1, mcp + 2)
                val second = angle(mcp + 1, mcp + 2, mcp + 3)
                return when {
                    first >= openAngle && second >= openAngle && distance(mcp + 3, 0) > distance(mcp + 1, 0) -> Finger.OPEN
                    first <= closedAngle && distance(mcp + 3, 0) < distance(mcp + 1, 0) + palm * 0.35 -> Finger.CLOSED
                    else -> Finger.UNKNOWN
                }
            }
            val fingers = listOf(5, 9, 13, 17).map(::finger)
            // Thumb bends at different joints and closes toward the palm, unlike the long fingers.
            val thumb = when {
                angle(1, 2, 3) >= openAngle && angle(2, 3, 4) >= openAngle && distance(4, 0) > distance(2, 0) -> Finger.OPEN
                angle(1, 2, 3) <= closedAngle && distance(4, 9) < palm * (if (exit) 1.15 else 1.0) -> Finger.CLOSED
                else -> Finger.UNKNOWN
            }
            if (fingers.any { it == Finger.UNKNOWN } || thumb == Finger.UNKNOWN) return null
            val allClosed = fingers.all { it == Finger.CLOSED }
            if (allClosed && thumb == Finger.OPEN) {
                val dx = (points[4].x - points[2].x) * aspect
                val dy = points[4].y - points[2].y
                if (hypot(dx, dy) > 1e-9 && -dy / hypot(dx, dy) >=
                    (if (exit) config.exitVerticalAlignment else config.minimumVerticalAlignment)) return PoseId.THUMBS_UP
            }
            if (allClosed && thumb == Finger.CLOSED) return PoseId.CLOSED_HAND
            if (fingers.all { it == Finger.OPEN } && thumb == Finger.OPEN) return PoseId.OPEN_HAND
            if (thumb != Finger.CLOSED || fingers[2] != Finger.CLOSED || fingers[3] != Finger.CLOSED) return null
            if (profile == PoseRecognitionProfile.FINGER_COMBINATIONS) return when {
                fingers[0] == Finger.OPEN && fingers[1] == Finger.OPEN -> PoseId.INDEX_MIDDLE
                fingers[0] == Finger.OPEN && fingers[1] == Finger.CLOSED -> PoseId.INDEX_ONLY
                fingers[0] == Finger.CLOSED && fingers[1] == Finger.OPEN -> PoseId.MIDDLE_ONLY
                else -> null
            }
            if (fingers[0] != Finger.OPEN || fingers[1] != Finger.CLOSED) return null
            val dx = (points[8].x - points[5].x) * aspect
            val dy = points[8].y - points[5].y
            val length = hypot(dx, dy)
            if (length <= 1e-9) return null
            val threshold = if (exit) config.exitVerticalAlignment else config.minimumVerticalAlignment
            if (exit) {
                val alignment = when (active) {
                    PoseId.POINTING_UP -> -dy / length
                    PoseId.POINTING_DOWN -> dy / length
                    PoseId.POINTING_LEFT -> -dx / length
                    PoseId.POINTING_RIGHT -> dx / length
                    else -> return null
                }
                return if (alignment >= threshold) active else null
            }
            return when {
                -dy / length >= threshold -> PoseId.POINTING_UP
                dy / length >= threshold -> PoseId.POINTING_DOWN
                -dx / length >= threshold -> PoseId.POINTING_LEFT
                dx / length >= threshold -> PoseId.POINTING_RIGHT
                else -> null
            }
        }
        if (active != null && recognize(true) == active) return active
        return recognize(false)
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
        if (config.tapGesturesEnabled && validFinger(points, 1, palmLength)) {
            if (activePose == RecognizedHandPose.THUMBS_UP && thumbsUp(points, exit = true)) return activePose
            if (thumbsUp(points)) return RecognizedHandPose.THUMBS_UP
            if (curled(points, 5, config.maximumCurledAngle)) return RecognizedHandPose.CLOSED_HAND
        }
        for (mcp in 5..17 step 4) {
            if (!extended(points, mcp, config.minimumIndexAngle)) return RecognizedHandPose.UNKNOWN
        }
        return RecognizedHandPose.NEUTRAL
    }

    private fun thumbsUp(points: List<HandLandmark>, exit: Boolean = false): Boolean {
        if (!curled(points, 5, if (exit) config.exitCurledAngle else config.maximumCurledAngle) ||
            !extended(points, 1, if (exit) config.exitIndexAngle else config.minimumIndexAngle)) return false
        val dx = points[4].x - points[2].x
        val dy = points[4].y - points[2].y
        val length = sqrt(dx * dx + dy * dy)
        return length > 1e-9 && dy / length <= -(if (exit) config.exitVerticalAlignment else config.minimumVerticalAlignment)
    }

    private fun curled(points: List<HandLandmark>, firstMcp: Int, threshold: Double): Boolean =
        (firstMcp..17 step 4).all { mcp -> angle(points[mcp], points[mcp + 1], points[mcp + 2]) <= threshold }

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
