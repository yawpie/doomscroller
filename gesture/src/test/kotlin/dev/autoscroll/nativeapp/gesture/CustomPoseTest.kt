package dev.autoscroll.nativeapp.gesture

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class CustomPoseTest {
    private val config = HandPoseDetectionConfig(confirmationDurationMs = 180, releaseDurationMs = 180,
        minimumObservations = 3, maximumSampleGapMs = 250, cooldownMs = 450)
    private val direction = PoseRecognitionProfile.DIRECTION
    private val combinations = PoseRecognitionProfile.FINGER_COMBINATIONS

    private fun points(open: Set<Int>, thumbOpen: Boolean = false, rotation: Double = 0.0,
        mirror: Boolean = false, aspect: Double = 1.0): List<HandLandmark> {
        val p = MutableList(21) { 0.0 to 0.0 }
        p[0] = 0.0 to 0.12
        p[1] = -0.11 to 0.09
        p[2] = -0.11 to 0.03
        p[3] = if (thumbOpen) -0.11 to -0.04 else -0.04 to 0.03
        p[4] = if (thumbOpen) -0.11 to -0.10 else 0.02 to 0.03
        for ((mcp, x, y) in listOf(Triple(5, -0.065, -0.06), Triple(9, -0.02, -0.07),
            Triple(13, 0.03, -0.06), Triple(17, 0.075, -0.04))) {
            p[mcp] = x to y
            p[mcp + 1] = x to y - 0.085
            p[mcp + 2] = if (mcp in open) x to y - 0.16 else x + 0.05 to y - 0.085
            p[mcp + 3] = if (mcp in open) x to y - 0.22 else x + 0.05 to y - 0.035
        }
        return p.mapIndexed { i, (x, y) ->
            val mx = if (mirror) -x else x
            HandLandmark(i, 0.5 + (mx * cos(rotation) - y * sin(rotation)) / aspect,
                0.5 + mx * sin(rotation) + y * cos(rotation), 0.0)
        }
    }

    @Test fun exactFingerStatesAndProfilesHaveOneCanonicalResult() {
        val classifier = HandPoseClassifier(config)
        fun classify(open: Set<Int>, thumb: Boolean = false, profile: PoseRecognitionProfile = combinations) =
            classifier.classify(points(open, thumb), 1.0, profile)
        assertEquals(PoseId.INDEX_ONLY, classify(setOf(5)))
        assertEquals(PoseId.MIDDLE_ONLY, classify(setOf(9)))
        assertEquals(PoseId.INDEX_MIDDLE, classify(setOf(5, 9)))
        assertEquals(PoseId.OPEN_HAND, classify(setOf(5, 9, 13, 17), true))
        assertEquals(PoseId.CLOSED_HAND, classify(emptySet()))
        assertEquals(PoseId.THUMBS_UP, classify(emptySet(), true))
        assertNull(classify(setOf(9, 13)))
        assertNull(classify(setOf(5, 9, 13)))
        assertNull(classify(setOf(5, 9), profile = direction))
        assertEquals(PoseId.POINTING_UP, classify(setOf(5), profile = direction))
        assertNull(classify(setOf(5), thumb = true))
        for (rotation in listOf(0.0, PI / 2, PI, -PI / 2)) for (mirror in listOf(false, true)) {
            for ((open, expected) in listOf(setOf(5) to PoseId.INDEX_ONLY, setOf(9) to PoseId.MIDDLE_ONLY,
                setOf(5, 9) to PoseId.INDEX_MIDDLE)) {
                assertEquals(expected, classifier.classify(points(open, rotation = rotation, mirror = mirror), 1.0, combinations))
            }
        }
    }

    @Test fun aspectMirrorSectorsAndAmbiguityAreHandled() {
        val classifier = HandPoseClassifier(config)
        for ((angle, expected) in listOf(0.0 to PoseId.POINTING_UP, PI / 2 to PoseId.POINTING_RIGHT,
            PI to PoseId.POINTING_DOWN, -PI / 2 to PoseId.POINTING_LEFT)) {
            for (aspect in listOf(0.75, 1.0, 1.5)) for (mirror in listOf(false, true)) {
                assertEquals(expected, classifier.classify(points(setOf(5), rotation = angle, mirror = mirror, aspect = aspect), aspect, direction))
            }
        }
        val boundary = points(setOf(5), rotation = 0.65)
        assertNull(classifier.classify(boundary, 1.0, direction))
        assertEquals(PoseId.POINTING_UP, classifier.classify(boundary, 1.0, direction, PoseId.POINTING_UP))
        for ((angle, active) in listOf(0.0 to PoseId.POINTING_UP, PI / 2 to PoseId.POINTING_RIGHT,
            PI to PoseId.POINTING_DOWN, -PI / 2 to PoseId.POINTING_LEFT)) {
            for (offset in listOf(-0.78, 0.78)) {
                val diagonal = points(setOf(5), rotation = angle + offset)
                assertNull(classifier.classify(diagonal, 1.0, direction))
                assertEquals(active, classifier.classify(diagonal, 1.0, direction, active))
            }
        }
        assertNull(classifier.classify(points(setOf(5), rotation = PI / 4), 1.0, direction))
        val invalid = points(setOf(5)).toMutableList()
        invalid[8] = invalid[8].copy(x = 0.0)
        assertNull(classifier.classify(invalid, 1.0, direction))
        invalid[8] = invalid[7].copy(index = 8)
        assertNull(classifier.classify(invalid, 1.0, direction))
        val half = points(setOf(5)).toMutableList()
        half[7] = half[7].copy(x = half[7].x + 0.04, y = half[6].y - 0.04)
        assertNull(classifier.classify(half, 1.0, combinations))
    }

    @Test fun canonicalClassifierRejectsInvalidGeometryThresholds() {
        for (invalid in listOf(config.copy(minimumIndexAngle = Double.NaN),
            config.copy(exitCurledAngle = config.exitIndexAngle),
            config.copy(exitVerticalAlignment = config.minimumVerticalAlignment + 0.01),
            config.copy(minimumBoneLengthRatio = config.minimumFingerLengthRatio))) {
            assertThrows(IllegalArgumentException::class.java) { HandPoseClassifier(invalid) }
        }
    }

    @Test fun discreteLatchSurvivesUnknownAndRequiresTimedValidRelease() {
        val actions = PoseActions(enabled = true, profile = combinations).withBinding(PoseId.INDEX_ONLY, PoseAction.BACK)
        val detector = CustomPoseDetector(actions, config)
        fun sample(t: Long, p: PoseId?) = detector.processPose(t, p)
        assertNull(sample(0, PoseId.INDEX_ONLY).event)
        assertNull(sample(100, PoseId.INDEX_ONLY).event)
        assertEquals(PoseId.INDEX_ONLY, sample(200, PoseId.INDEX_ONLY).event)
        sample(300, null)
        for (t in 400L..1200L step 100) assertNull(sample(t, PoseId.INDEX_ONLY).event)
        sample(1300, PoseId.CLOSED_HAND)
        sample(1400, null)
        sample(1500, PoseId.CLOSED_HAND)
        sample(1600, PoseId.CLOSED_HAND)
        sample(1700, PoseId.CLOSED_HAND)
        sample(1800, PoseId.INDEX_ONLY)
        sample(1900, PoseId.INDEX_ONLY)
        assertEquals(PoseId.INDEX_ONLY, sample(2000, PoseId.INDEX_ONLY).event)
    }

    @Test fun holdStopsImmediatelyAndRequiresNewConfirmationAndTargetRelease() {
        val actions = PoseActions(enabled = true, profile = combinations)
            .withBinding(PoseId.INDEX_ONLY, PoseAction.CONTINUOUS_NEXT)
            .withBinding(PoseId.INDEX_MIDDLE, PoseAction.CONTINUOUS_NEXT)
        val detector = CustomPoseDetector(actions, config)
        fun sample(t: Long, p: PoseId?) = detector.processPose(t, p)
        sample(0, PoseId.INDEX_ONLY); sample(100, PoseId.INDEX_ONLY)
        assertEquals(PoseId.INDEX_ONLY, sample(200, PoseId.INDEX_ONLY).held)
        assertNull(sample(300, PoseId.INDEX_MIDDLE).held)
        sample(400, PoseId.INDEX_MIDDLE)
        assertEquals(PoseId.INDEX_MIDDLE, sample(500, PoseId.INDEX_MIDDLE).held)
        assertNull(sample(600, null).held)
        sample(700, PoseId.INDEX_MIDDLE); sample(800, PoseId.INDEX_MIDDLE)
        assertEquals(PoseId.INDEX_MIDDLE, sample(900, PoseId.INDEX_MIDDLE).held)
        detector.targetChanged()
        for (t in 1000L..1500L step 100) assertNull(sample(t, PoseId.INDEX_MIDDLE).held)
        for (t in 1600L..1800L step 100) sample(t, PoseId.CLOSED_HAND)
        sample(1900, PoseId.INDEX_MIDDLE); sample(2000, PoseId.INDEX_MIDDLE)
        assertEquals(PoseId.INDEX_MIDDLE, sample(2100, PoseId.INDEX_MIDDLE).held)
    }

    @Test fun timestampAndGapDiscardEvidenceAndNoneNeverOwns() {
        val detector = CustomPoseDetector(PoseActions(enabled = true, profile = combinations)
            .withBinding(PoseId.INDEX_ONLY, PoseAction.BACK), config)
        fun sample(t: Long, p: PoseId?) = detector.processPose(t, p)
        sample(0, PoseId.INDEX_ONLY); sample(100, PoseId.INDEX_ONLY)
        assertNull(sample(100, PoseId.INDEX_ONLY).event)
        sample(200, PoseId.INDEX_ONLY); sample(300, PoseId.INDEX_ONLY)
        assertNull(sample(600, PoseId.INDEX_ONLY).event)
        sample(700, PoseId.INDEX_ONLY)
        assertEquals(PoseId.INDEX_ONLY, sample(800, PoseId.INDEX_ONLY).event)
        sample(900, PoseId.INDEX_MIDDLE)
        assertFalse(detector.ownsGesture)
    }

    @Test fun gapDoesNotReuseExitOnlyGeometry() {
        val detector = CustomPoseDetector(PoseActions(enabled = true), config)
        assertEquals(PoseId.POINTING_UP,
            detector.process(HandTrackingResult(0, HandPose(points(setOf(5))))).pose)
        assertNull(detector.process(HandTrackingResult(400, HandPose(points(setOf(5), rotation = 0.65)))).pose)
    }

    @Test fun hybridNoneAllowsMotionAndActivePoseOwnsWithoutFallback() {
        val inactive = PoseActions(enabled = true, profile = combinations)
            .withBinding(PoseId.INDEX_ONLY, PoseAction.BACK)
        val coordinator = GestureCoordinator(mode = GestureMode.HYBRID, poseActions = inactive)
        val combined = HandPose(points(setOf(5, 9)))
        for (t in 0L..500L step 100) {
            val result = coordinator.process(HandTrackingResult(t, combined))
            assertEquals(PoseId.INDEX_MIDDLE, result.customPose?.pose)
            assertNull(result.customPose?.event)
            assertFalse(result.suppressesContinuous)
        }
        val index = HandPose(points(setOf(5)))
        assertTrue(coordinator.process(HandTrackingResult(600, index)).suppressesContinuous)
    }

    @Test fun targetChangeCannotRearmAConsumedDiscretePose() {
        val detector = CustomPoseDetector(PoseActions(enabled = true, profile = combinations)
            .withBinding(PoseId.INDEX_ONLY, PoseAction.BACK), config)
        for (t in 0L..200L step 100) detector.processPose(t, PoseId.INDEX_ONLY)
        detector.targetChanged()
        for (t in 300L..1000L step 100) assertNull(detector.processPose(t, PoseId.INDEX_ONLY).event)
        for (t in 1100L..1300L step 100) detector.processPose(t, PoseId.CLOSED_HAND)
        detector.processPose(1400, PoseId.INDEX_ONLY)
        detector.processPose(1500, PoseId.INDEX_ONLY)
        assertEquals(PoseId.INDEX_ONLY, detector.processPose(1600, PoseId.INDEX_ONLY).event)
    }

    @Test fun targetFenceSurvivesLossAndRepeatedTargetChanges() {
        val detector = CustomPoseDetector(PoseActions(enabled = true, profile = combinations)
            .withBinding(PoseId.INDEX_ONLY, PoseAction.CONTINUOUS_NEXT), config)
        for (t in 0L..200L step 100) detector.processPose(t, PoseId.INDEX_ONLY)
        detector.processPose(300, null)
        detector.targetChanged()
        detector.processPose(400, PoseId.CLOSED_HAND)
        detector.targetChanged()
        for (t in 500L..1000L step 100) assertNull(detector.processPose(t, PoseId.INDEX_ONLY).held)
        for (t in 1100L..1300L step 100) detector.processPose(t, PoseId.CLOSED_HAND)
        detector.processPose(1400, PoseId.INDEX_ONLY)
        detector.processPose(1500, PoseId.INDEX_ONLY)
        assertEquals(PoseId.INDEX_ONLY, detector.processPose(1600, PoseId.INDEX_ONLY).held)
    }

    @Test fun existingFlickOwnershipCannotBeStolenByPoseCandidate() {
        val actions = PoseActions(enabled = true, profile = combinations).withBinding(PoseId.INDEX_ONLY, PoseAction.BACK)
        val coordinator = GestureCoordinator(mode = GestureMode.HYBRID, poseActions = actions)
        val base = points(setOf(9))
        fun shifted(t: Long, y: Double, p: List<HandLandmark> = base) = HandTrackingResult(t,
            HandPose(p.map { it.copy(y = it.y + y) }))
        coordinator.process(shifted(0, 0.0))
        coordinator.process(shifted(100, 0.08))
        assertTrue(coordinator.flickDetector.ownsGesture)
        val decision = coordinator.process(shifted(200, 0.16, points(setOf(5))))
        assertNull(decision.customPose?.pose)
        assertNull(decision.customPose?.event)
    }
}
