package dev.autoscroll.nativeapp.gesture

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class GestureSafetyTest {
    private fun points(pose: RecognizedHandPose, rotation: Double = 0.0): MutableList<HandLandmark> {
        val points = MutableList(21) { 0.0 to 0.0 }
        points[0] = 0.0 to 0.12
        points[1] = -0.07 to 0.08
        points[2] = -0.11 to 0.03
        points[3] = -0.13 to -0.02
        points[4] = -0.14 to -0.07
        for ((mcp, x, y) in listOf(Triple(5, -0.065, -0.06), Triple(9, -0.02, -0.07),
            Triple(13, 0.03, -0.06), Triple(17, 0.075, -0.04))) {
            val extended = pose == RecognizedHandPose.NEUTRAL || (mcp == 5 && pose != RecognizedHandPose.UNKNOWN)
            points[mcp] = x to y
            points[mcp + 1] = x to y - 0.085
            points[mcp + 2] = if (extended) x to y - 0.16 else x + 0.05 to y - 0.085
            points[mcp + 3] = if (extended) x to y - 0.22 else x + 0.05 to y - 0.035
        }
        val angle = rotation + if (pose == RecognizedHandPose.POINTING_DOWN) PI else 0.0
        return points.mapIndexed { i, (x, y) ->
            HandLandmark(i, 0.5 + x * cos(angle) - y * sin(angle), 0.5 + x * sin(angle) + y * cos(angle), 0.0)
        }.toMutableList()
    }

    private fun sample(time: Long, pose: RecognizedHandPose) = HandTrackingResult(time, HandPose(points(pose)))

    @Test fun landmarksAreValidatedAndSnapshotted() {
        val points = points(RecognizedHandPose.POINTING_UP)
        val pose = HandPose(points)
        points[0] = points[0].copy(y = 0.0)
        assertEquals(0.62, pose.landmarks[0].y, 1e-12)
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)) {
            val invalid = points(RecognizedHandPose.POINTING_UP)
            invalid[8] = invalid[8].copy(x = bad)
            assertThrows(IllegalArgumentException::class.java) { HandPose(invalid) }
            assertEquals(RecognizedHandPose.UNKNOWN, HandPoseClassifier().classify(invalid))
        }
        assertThrows(IllegalArgumentException::class.java) { HandPose(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { HandPose(pose.landmarks, handednessConfidence = Double.NaN) }
        val depth = points(RecognizedHandPose.POINTING_UP).map { it.copy(z = -10.0) }
        assertEquals(RecognizedHandPose.POINTING_UP, HandPoseClassifier().classify(depth))
    }

    @Test fun classifierKeepsHysteresisAndRejectsSidewaysOrCollapsedGeometry() {
        val classifier = HandPoseClassifier()
        val up = RecognizedHandPose.POINTING_UP
        assertEquals(up, classifier.classify(points(up)))
        assertEquals(RecognizedHandPose.POINTING_DOWN, classifier.classify(points(RecognizedHandPose.POINTING_DOWN)))
        assertEquals(RecognizedHandPose.NEUTRAL, classifier.classify(points(RecognizedHandPose.NEUTRAL)))
        assertEquals(RecognizedHandPose.UNKNOWN, classifier.classify(points(up, 0.65)))
        assertEquals(up, classifier.classify(points(up, 0.65), up))
        assertEquals(RecognizedHandPose.UNKNOWN, classifier.classify(points(up, PI / 2), up))
        val collapsed = MutableList(21) { HandLandmark(it, 0.5, 0.5, 0.0) }
        assertEquals(RecognizedHandPose.UNKNOWN, classifier.classify(collapsed))
    }

    @Test fun jointAnglesRequireExtendedIndexAndCurledOtherFingers() {
        val classifier = HandPoseClassifier()
        val up = RecognizedHandPose.POINTING_UP
        fun joint(angle: Double, index: Boolean): List<HandLandmark> {
            val p = points(up)
            val deflection = (180 - angle) * PI / 180
            for (mcp in (if (index) 5 else 9)..(if (index) 5 else 17) step 4) {
                val pip = p[mcp + 1]
                val x = pip.x + 0.075 * sin(deflection)
                val y = pip.y - 0.075 * cos(deflection)
                p[mcp + 2] = HandLandmark(mcp + 2, x, y, 0.0)
                p[mcp + 3] = if (index) HandLandmark(mcp + 3, x + 0.06 * sin(deflection), y - 0.06 * cos(deflection), 0.0)
                    else HandLandmark(mcp + 3, x, y + 0.05, 0.0)
            }
            return p
        }
        assertEquals(up, classifier.classify(joint(165.0, true)))
        assertEquals(RecognizedHandPose.UNKNOWN, classifier.classify(joint(155.0, true)))
        assertEquals(up, classifier.classify(joint(155.0, true), up))
        assertEquals(RecognizedHandPose.UNKNOWN, classifier.classify(joint(145.0, true), up))
        assertEquals(up, classifier.classify(joint(105.0, false)))
        assertEquals(RecognizedHandPose.UNKNOWN, classifier.classify(joint(120.0, false)))
        assertEquals(up, classifier.classify(joint(120.0, false), up))
        assertEquals(RecognizedHandPose.UNKNOWN, classifier.classify(joint(130.0, false), up))
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

    @Test fun nativePoseDefaultRequiresExplicitNeutralAndNeverRepeatsHeldPose() {
        val detector = HandPoseDetector()
        val events = (0L..4000L step 100).mapNotNull { detector.process(sample(it, RecognizedHandPose.POINTING_DOWN)) }
        assertEquals(1, events.size)
        assertEquals(200L, events.single().timestampMs)
        assertEquals(HandPosePhase.LATCHED, detector.phase)
        detector.loseTracking()
        assertNull(detector.process(sample(4100, RecognizedHandPose.POINTING_UP)))
        assertEquals(HandPosePhase.LATCHED, detector.phase)
        for (ms in listOf(4200L, 4300L, 4400L)) detector.process(sample(ms, RecognizedHandPose.NEUTRAL))
        assertEquals(HandPosePhase.READY, detector.phase)
        for (ms in listOf(4500L, 4600L)) assertNull(detector.process(sample(ms, RecognizedHandPose.POINTING_UP)))
        assertEquals(RecognizedHandPose.POINTING_UP, detector.process(sample(4700, RecognizedHandPose.POINTING_UP))!!.pose)
    }

    @Test fun duplicateTimestampDiscardsCandidateAndKeepsHighWater() {
        val detector = HandPoseDetector()
        detector.process(sample(0, RecognizedHandPose.POINTING_UP))
        detector.process(sample(90, RecognizedHandPose.POINTING_UP))
        assertNull(detector.process(sample(90, RecognizedHandPose.POINTING_UP)))
        assertEquals(HandPosePhase.READY, detector.phase)
        assertNull(detector.process(sample(80, RecognizedHandPose.POINTING_UP)))
        assertEquals(HandPosePhase.READY, detector.phase)
        for (ms in listOf(180L, 270L)) assertNull(detector.process(sample(ms, RecognizedHandPose.POINTING_UP)))
        assertNotNull(detector.process(sample(360, RecognizedHandPose.POINTING_UP)))
    }

    @Test fun continuousAndFlickModesIsolateRecognitionAndConfigureResetsLatch() {
        val continuous = GestureCoordinator(mode = GestureMode.CONTINUOUS)
        for (ms in 0L..600L step 100) {
            val decision = continuous.process(sample(ms, RecognizedHandPose.POINTING_UP))
            assertNull(decision.pose)
            assertNull(decision.flick)
            assertFalse(decision.suppressesContinuous)
        }
        val flickOnly = GestureCoordinator(mode = GestureMode.FLICK)
        for (ms in 0L..600L step 100) {
            val decision = flickOnly.process(sample(ms, RecognizedHandPose.POINTING_UP))
            assertNull(decision.pose)
            assertTrue(decision.suppressesContinuous)
        }
        val hybrid = GestureCoordinator()
        for (ms in listOf(0L, 100L, 200L)) hybrid.process(sample(ms, RecognizedHandPose.POINTING_UP))
        assertEquals(HandPosePhase.LATCHED, hybrid.poseDetector.phase)
        hybrid.configure(mode = GestureMode.CONTINUOUS)
        assertEquals(HandPosePhase.READY, hybrid.poseDetector.phase)
        assertNull(hybrid.poseDetector.lastEvent)
        assertEquals(HandAction.START, hybrid.process(sample(300, RecognizedHandPose.NEUTRAL)).motion.action)
    }

    @Test fun invalidConfigsFailAtTheBoundary() {
        for (config in listOf(FlickDetectionConfig(minimumVelocity = Double.NaN),
            FlickDetectionConfig(minimumDurationMs = 0), FlickDetectionConfig(rearmVelocity = 0.2),
            FlickDetectionConfig(rearmRadius = 0.1), FlickDetectionConfig(maximumSampleGapMs = 0))) {
            assertThrows(IllegalArgumentException::class.java) { FlickDetector(config) }
        }
        for (config in listOf(HandPoseDetectionConfig(confirmationDurationMs = 0),
            HandPoseDetectionConfig(minimumObservations = 1), HandPoseDetectionConfig(minimumIndexAngle = 181.0),
            HandPoseDetectionConfig(exitCurledAngle = 151.0), HandPoseDetectionConfig(minimumVerticalAlignment = Double.NaN))) {
            assertThrows(IllegalArgumentException::class.java) { HandPoseDetector(config) }
        }
    }
}
