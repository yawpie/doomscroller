package dev.autoscroll.nativeapp.gesture

import java.util.Collections

data class HandLandmark(val index: Int, val x: Double, val y: Double, val z: Double)

class HandPose(
    landmarks: List<HandLandmark>,
    val handedness: String? = null,
    val handednessConfidence: Double? = null,
) {
    val landmarks: List<HandLandmark> = Collections.unmodifiableList(landmarks.toList())

    init {
        require(validLandmarks(this.landmarks)) {
            "A pose requires 21 ordered indexed finite normalized landmarks."
        }
        require(handednessConfidence == null ||
            (handednessConfidence.isFinite() && handednessConfidence in 0.0..1.0))
    }

    val palmY: Double get() = PALM_INDICES.sumOf { landmarks[it].y } / 5
    val palmX: Double get() = PALM_INDICES.sumOf { landmarks[it].x } / 5

    companion object {
        private val PALM_INDICES = intArrayOf(0, 5, 9, 13, 17)
    }
}

internal fun validLandmarks(points: List<HandLandmark>): Boolean =
    points.size == 21 && points.withIndex().all { (index, point) ->
        point.index == index && point.x.isFinite() && point.y.isFinite() && point.z.isFinite() &&
            point.x in 0.0..1.0 && point.y in 0.0..1.0
    }

data class HandTrackingResult(val timestampMs: Long, val pose: HandPose? = null) {
    val handDetected: Boolean get() = pose != null
}

enum class HandAction { IDLE, MOVE_UP, MOVE_DOWN, START, STOP }

data class HandMotion(
    val action: HandAction,
    val verticalDisplacement: Double = 0.0,
    val verticalVelocity: Double = 0.0,
    val smoothedY: Double? = null,
    val neutralY: Double? = null,
) {
    val calibrated: Boolean get() = neutralY != null
}

enum class FlickDirection { UP, DOWN }
data class FlickEvent(
    val direction: FlickDirection,
    val timestampMs: Long,
    val verticalDisplacement: Double,
    val verticalVelocity: Double,
    val durationMs: Long,
)

enum class RecognizedHandPose { POINTING_UP, POINTING_DOWN, NEUTRAL, THUMBS_UP, CLOSED_HAND, UNKNOWN }
enum class HandPosePhase { READY, CONFIRMING, LATCHED, WAITING_REARM }
data class PoseEvent(val pose: RecognizedHandPose, val timestampMs: Long)
enum class GestureMode { CONTINUOUS, FLICK, POSE, HYBRID }
data class GestureDecision(
    val motion: HandMotion,
    val flick: FlickEvent? = null,
    val pose: PoseEvent? = null,
    val suppressesContinuous: Boolean = false,
)
