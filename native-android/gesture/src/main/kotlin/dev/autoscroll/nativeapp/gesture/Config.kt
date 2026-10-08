package dev.autoscroll.nativeapp.gesture

data class MotionDetectionConfig(
    val smoothingAlpha: Double = 0.4,
    val windowSize: Int = 4,
    val minimumFrames: Int = 3,
    val movementThreshold: Double = 0.018,
    val deadZone: Double = 0.008,
    val minimumVelocity: Double = 0.025,
    val confirmationFrames: Int = 2,
    val directionCooldownMs: Long = 150,
    val maximumSampleGapMs: Long = 350,
    val maximumPositionJump: Double = 0.15,
    val stationaryVelocity: Double = 0.015,
    val calibrationDurationMs: Long = 600,
    val calibrationRadius: Double = 0.012,
) {
    init {
        require(listOf(smoothingAlpha, movementThreshold, deadZone, minimumVelocity,
            maximumPositionJump, stationaryVelocity, calibrationRadius).all { it.isFinite() })
        require(smoothingAlpha > 0 && smoothingAlpha <= 1)
        require(windowSize >= minimumFrames && minimumFrames >= 3)
        require(deadZone >= 0 && movementThreshold >= deadZone && minimumVelocity >= 0)
        require(confirmationFrames >= 1 && maximumPositionJump > 0 && maximumPositionJump <= 1)
        require(stationaryVelocity >= 0 && calibrationRadius > 0)
        require(directionCooldownMs >= 0 && maximumSampleGapMs > 0 && calibrationDurationMs >= 0)
    }
}

data class FlickDetectionConfig(
    val minimumVelocity: Double = 0.4,
    val minimumDisplacement: Double = 0.10,
    val minimumDurationMs: Long = 80,
    val maximumDurationMs: Long = 350,
    val cooldownMs: Long = 500,
    val directionConsistency: Double = 0.75,
    val maximumSampleGapMs: Long = 250,
    val maximumPositionJump: Double = 0.25,
    val rearmVelocity: Double = 0.15,
    val rearmDurationMs: Long = 150,
    val rearmRadius: Double = 0.012,
)

enum class FlickScrollAction { PAGE, FAST_SCROLL }
data class ScrollConfig(
    val pixelsPerNormalizedUnit: Double = 1800.0,
    val maximumPixelsPerSecond: Double = 1200.0,
    val staleAfterMs: Long = 350,
    val deadZone: Double = 0.025,
    val smoothingAlpha: Double = 0.3,
    val minimumSensitivity: Double = 300.0,
    val maximumSensitivity: Double = 4000.0,
    val flickAction: FlickScrollAction = FlickScrollAction.PAGE,
    val flickScrollDistance: Double = 600.0,
    val flickDurationMs: Long = 220,
) {
    init {
        require(listOf(pixelsPerNormalizedUnit, maximumPixelsPerSecond, deadZone,
            smoothingAlpha, minimumSensitivity, maximumSensitivity, flickScrollDistance).all { it.isFinite() })
        require(pixelsPerNormalizedUnit > 0 && maximumPixelsPerSecond > 0 && deadZone >= 0)
        require(smoothingAlpha > 0 && smoothingAlpha <= 1 && flickScrollDistance > 0)
        require(minimumSensitivity > 0 && minimumSensitivity <= pixelsPerNormalizedUnit &&
            maximumSensitivity >= pixelsPerNormalizedUnit)
        require(staleAfterMs > 0 && flickDurationMs > 0)
    }
}

enum class HandPoseRearmMode { OPEN_HAND, STABLE_POSE }
data class HandPoseDetectionConfig(
    val enabled: Boolean = true,
    val confirmationDurationMs: Long = 180,
    val releaseDurationMs: Long = 180,
    val cooldownMs: Long = 450,
    val rearmMode: HandPoseRearmMode = HandPoseRearmMode.OPEN_HAND,
    val stablePoseDurationMs: Long = 1000,
    val minimumObservations: Int = 3,
    val minimumIndexAngle: Double = 160.0,
    val maximumCurledAngle: Double = 110.0,
    val minimumVerticalAlignment: Double = 0.85,
    val exitIndexAngle: Double = 150.0,
    val exitCurledAngle: Double = 125.0,
    val exitVerticalAlignment: Double = 0.70,
    val minimumFingerLengthRatio: Double = 0.35,
    val minimumBoneLengthRatio: Double = 0.03,
    val maximumSampleGapMs: Long = 250,
    // Native app enables taps; false preserves the original Dart replay contract.
    val tapGesturesEnabled: Boolean = false,
)
