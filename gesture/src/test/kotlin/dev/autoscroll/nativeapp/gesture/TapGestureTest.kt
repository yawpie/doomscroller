package dev.autoscroll.nativeapp.gesture

import org.junit.Assert.*
import org.junit.Test

class TapGestureTest {
    @Test fun defaultsFireEveryDiscretePoseOnceUntilValidReleaseAndCooldown() {
        for (pose in listOf(PoseId.POINTING_UP, PoseId.POINTING_DOWN, PoseId.OPEN_HAND, PoseId.THUMBS_UP)) {
            val detector = HandPoseDetector()
            val events = (0L..2000L step 100).mapNotNull { detector.processPose(it, pose).event }
            assertEquals(listOf(pose), events)
            detector.loseTracking()
            for (t in 2100L..2500L step 100) assertNull(detector.processPose(t, pose).event)
            for (t in 2600L..2800L step 100) detector.processPose(t, PoseId.CLOSED_HAND)
            assertNull(detector.processPose(2900, pose).event)
            assertNull(detector.processPose(3000, pose).event)
            assertEquals(pose, detector.processPose(3100, pose).event)
        }
    }

    @Test fun cooldownAppliesAcrossDistinctActionsAfterRelease() {
        val detector = HandPoseDetector(HandPoseDetectionConfig(cooldownMs = 1000))
        for (t in 0L..200L step 100) detector.processPose(t, PoseId.POINTING_UP)
        for (t in 300L..500L step 100) detector.processPose(t, PoseId.CLOSED_HAND)
        for (t in 600L..1100L step 100) assertNull(detector.processPose(t, PoseId.THUMBS_UP).event)
        assertEquals(PoseId.THUMBS_UP, detector.processPose(1200, PoseId.THUMBS_UP).event)
    }

    @Test fun durationAndSampleValidationRejectInvalidConfiguration() {
        for (config in listOf(HandPoseDetectionConfig(confirmationDurationMs = 0),
            HandPoseDetectionConfig(releaseDurationMs = 0), HandPoseDetectionConfig(minimumObservations = 1),
            HandPoseDetectionConfig(maximumSampleGapMs = 0), HandPoseDetectionConfig(cooldownMs = -1))) {
            assertThrows(IllegalArgumentException::class.java) { HandPoseDetector(config) }
        }
    }
}
