package dev.autoscroll.nativeapp.gesture

import org.junit.Assert.*
import org.junit.Test

class GestureSafetyTest {
    @Test fun disabledDetectorDoesNotOwnOrEmit() {
        val detector = HandPoseDetector(HandPoseDetectionConfig(enabled = false))
        val result = detector.process(HandTrackingResult(100))
        assertNull(result.pose)
        assertNull(result.event)
        assertFalse(detector.ownsGesture)
    }

    @Test fun modeAndTargetChangesRetainConsumedAppearance() {
        val detector = HandPoseDetector()
        for (t in 0L..200L step 100) detector.processPose(t, PoseId.POINTING_UP)
        detector.targetChanged()
        for (t in 300L..1200L step 100) assertNull(detector.processPose(t, PoseId.POINTING_UP).event)
        for (t in 1300L..1500L step 100) detector.processPose(t, PoseId.CLOSED_HAND)
        for (t in 1600L..1700L step 100) assertNull(detector.processPose(t, PoseId.POINTING_UP).event)
        assertEquals(PoseId.POINTING_UP, detector.processPose(1800, PoseId.POINTING_UP).event)
    }

    @Test fun continuousAndFlickModesDoNotReturnPoseResults() {
        val hand = HandPose((0..20).map { HandLandmark(it, .5, .5, 0.0) })
        for (mode in listOf(GestureMode.CONTINUOUS, GestureMode.FLICK)) {
            val coordinator = GestureCoordinator(mode = mode)
            for (time in 0L..500L step 100) assertNull(coordinator.process(HandTrackingResult(time, hand)).poseResult)
        }
    }
    @Test fun landmarksAreValidatedAndSnapshotted() {
        val points = MutableList(21) { HandLandmark(it, .5, .5, 0.0) }
        val hand = HandPose(points)
        points[0] = points[0].copy(y = 0.0)
        assertEquals(.5, hand.landmarks[0].y, 0.0)
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)) {
            val invalid = hand.landmarks.toMutableList()
            invalid[8] = invalid[8].copy(x = bad)
            assertThrows(IllegalArgumentException::class.java) { HandPose(invalid) }
            assertNull(HandPoseClassifier().classify(invalid, 1.0, PoseRecognitionProfile.DIRECTION))
        }
        assertThrows(IllegalArgumentException::class.java) { HandPose(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { HandPose(hand.landmarks, handednessConfidence = Double.NaN) }
    }

    @Test fun smootherMatchesEmaAndReset() {
        val smoother = ExponentialPositionSmoother(0.4)
        assertEquals(0.5, smoother.add(0.5), 0.0)
        assertEquals(0.54, smoother.add(0.6), 1e-12)
        assertEquals(0.604, smoother.add(0.7), 1e-12)
        smoother.reset()
        assertEquals(0.2, smoother.add(0.2), 0.0)
        assertThrows(IllegalArgumentException::class.java) { ExponentialPositionSmoother(Double.NaN) }
    }

    @Test fun invalidFlickConfigsFailAtTheBoundary() {
        for (config in listOf(FlickDetectionConfig(minimumVelocity = Double.NaN),
            FlickDetectionConfig(minimumDurationMs = 0), FlickDetectionConfig(rearmVelocity = 0.2),
            FlickDetectionConfig(rearmRadius = 0.1), FlickDetectionConfig(maximumSampleGapMs = 0))) {
            assertThrows(IllegalArgumentException::class.java) { FlickDetector(config) }
        }
    }

}
