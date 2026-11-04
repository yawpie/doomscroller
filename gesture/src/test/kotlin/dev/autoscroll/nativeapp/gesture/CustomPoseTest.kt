package dev.autoscroll.nativeapp.gesture

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class CustomPoseTest {
    private val config = HandPoseDetectionConfig(confirmationDurationMs = 180, releaseDurationMs = 180,
        minimumObservations = 3, maximumSampleGapMs = 250, cooldownMs = 450)

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

    @Test fun pointingAndFingerCombinationsHaveOneCanonicalResultTogether() {
        val classifier = CustomPoseClassifier(config)
        fun classify(open: Set<Int>, thumb: Boolean = false) = classifier.classify(points(open, thumb), 1.0)
        assertEquals(PoseId.POINTING_UP, classify(setOf(5)))
        assertEquals(PoseId.MIDDLE_ONLY, classify(setOf(9)))
        assertEquals(PoseId.INDEX_MIDDLE, classify(setOf(5, 9)))
        assertEquals(PoseId.OPEN_HAND, classify(setOf(5, 9, 13, 17), true))
        assertEquals(PoseId.CLOSED_HAND, classify(emptySet()))
        assertEquals(PoseId.THUMBS_UP, classify(emptySet(), true))
        assertNull(classify(setOf(9, 13)))
        assertNull(classify(setOf(5, 9, 13)))
        assertNull(classify(setOf(5), thumb = true))
        for (rotation in listOf(0.0, PI / 2, PI, -PI / 2)) for (mirror in listOf(false, true)) {
            val pointing = when (rotation) { 0.0 -> PoseId.POINTING_UP; PI / 2 -> PoseId.POINTING_RIGHT;
                PI -> PoseId.POINTING_DOWN; else -> PoseId.POINTING_LEFT }
            for ((open, expected) in listOf(setOf(5) to pointing, setOf(9) to PoseId.MIDDLE_ONLY,
                setOf(5, 9) to if (rotation == 0.0) PoseId.INDEX_MIDDLE else null)) {
                assertEquals(expected, classifier.classify(points(open, rotation = rotation, mirror = mirror), 1.0))
            }
        }
    }

    @Test fun aspectMirrorSectorsAndAmbiguityAreHandled() {
        val classifier = CustomPoseClassifier(config)
        for ((angle, expected) in listOf(0.0 to PoseId.POINTING_UP, PI / 2 to PoseId.POINTING_RIGHT,
            PI to PoseId.POINTING_DOWN, -PI / 2 to PoseId.POINTING_LEFT)) {
            for (aspect in listOf(0.75, 1.0, 1.5)) for (mirror in listOf(false, true)) {
                assertEquals(expected, classifier.classify(points(setOf(5), rotation = angle, mirror = mirror, aspect = aspect), aspect))
            }
        }
        val boundary = points(setOf(5), rotation = 0.65)
        assertNull(classifier.classify(boundary, 1.0))
        assertEquals(PoseId.POINTING_UP, classifier.classify(boundary, 1.0, PoseId.POINTING_UP))
        for ((angle, active) in listOf(0.0 to PoseId.POINTING_UP, PI / 2 to PoseId.POINTING_RIGHT,
            PI to PoseId.POINTING_DOWN, -PI / 2 to PoseId.POINTING_LEFT)) {
            for (offset in listOf(-0.78, 0.78)) {
                val diagonal = points(setOf(5), rotation = angle + offset)
                assertNull(classifier.classify(diagonal, 1.0))
                assertEquals(active, classifier.classify(diagonal, 1.0, active))
            }
        }
        assertNull(classifier.classify(points(setOf(5), rotation = PI / 4), 1.0))
        val invalid = points(setOf(5)).toMutableList()
        invalid[8] = invalid[8].copy(x = 0.0)
        assertNull(classifier.classify(invalid, 1.0))
        invalid[8] = invalid[7].copy(index = 8)
        assertNull(classifier.classify(invalid, 1.0))
        val half = points(setOf(5)).toMutableList()
        half[7] = half[7].copy(x = half[7].x + 0.04, y = half[6].y - 0.04)
        assertNull(classifier.classify(half, 1.0))
    }

    @Test fun tiltedHandKeepsFingerIdentityAndPointingDirectionsAcrossTransitions() {
        fun tilted(rotation: Double, mirror: Boolean, aspect: Double): List<HandLandmark> {
            val tilt = 65 * PI / 180
            val a = (1 + cos(tilt)) / 2
            val b = (1 - cos(tilt)) / 2
            val depth = sin(tilt) / kotlin.math.sqrt(2.0)
            val source = points(setOf(5)).toMutableList()
            for (mcp in listOf(9, 13, 17)) {
                source[mcp + 2] = source[mcp + 2].copy(x = source[mcp].x, z = 0.05)
                source[mcp + 3] = source[mcp + 3].copy(x = source[mcp].x, z = 0.05)
            }
            return source.map { p ->
                val x = p.x - 0.5
                val y = p.y - 0.5
                val tx = (a * x - b * y - depth * p.z) * if (mirror) -1 else 1
                val ty = a * y - b * x - depth * p.z
                p.copy(x = 0.5 + (tx * cos(rotation) - ty * sin(rotation)) / aspect,
                    y = 0.5 + tx * sin(rotation) + ty * cos(rotation),
                    z = (depth * (x + y) + cos(tilt) * p.z) / aspect)
            }
        }
        val classifier = CustomPoseClassifier(config)
        for (aspect in listOf(0.75, 1.0, 1.5)) for (mirror in listOf(false, true)) {
            val up = tilted(0.0, mirror, aspect)
            assertEquals(PoseId.POINTING_UP, classifier.classify(up, aspect))
            val detector = CustomPoseDetector(PoseActions(enabled = true), config)
            for ((i, entry) in listOf(up to PoseId.POINTING_UP,
                tilted(-PI / 2, mirror, aspect) to PoseId.POINTING_LEFT,
                tilted(PI / 2, mirror, aspect) to PoseId.POINTING_RIGHT).withIndex()) {
                for (step in 0..2) {
                    val result = detector.process(HandTrackingResult((i * 3L + step) * 200,
                        HandPose(entry.first), uprightAspectRatio = aspect))
                    assertEquals(entry.second, result.pose)
                    if (step == 2) assertEquals(entry.second, result.event)
                }
            }
        }
    }

    @Test fun depthFoldedIndexIsRejectedWhileDepthTranslationPreservesPose() {
        val classifier = CustomPoseClassifier(config)
        assertEquals(PoseId.POINTING_UP, classifier.classify(points(setOf(5)).map { it.copy(z = -10.0) }, 1.0))
        val bent = points(setOf(5)).toMutableList()
        bent[7] = bent[7].copy(z = 0.1)
        assertNull(classifier.classify(bent, 1.0))
        assertNull(classifier.classify(bent, 1.0))
    }

    @Test fun extendedThumbAllowsNaturalIpBendAndFoldedTipBecomesClosedHand() {
        val classifier = CustomPoseClassifier(config)
        for (mirror in listOf(false, true)) for (aspect in listOf(0.75, 1.0, 1.5)) {
            fun thumb(bend: Double): List<HandLandmark> {
                val p = points(emptySet(), thumbOpen = true).toMutableList()
                p[4] = p[4].copy(x = p[3].x + 0.06 * sin(bend), y = p[3].y - 0.06 * cos(bend))
                return p.map { it.copy(x = 0.5 + (it.x - 0.5) * (if (mirror) -1 else 1) / aspect) }
            }
            assertEquals(PoseId.THUMBS_UP, classifier.classify(thumb(PI / 8), aspect))
            assertEquals(PoseId.CLOSED_HAND, classifier.classify(thumb(PI / 3), aspect))
        }
    }

    @Test fun thumbRestingAcrossPalmSupportsAllPointingDirections() {
        val classifier = CustomPoseClassifier(config)
        val p = points(setOf(5)).toMutableList()
        p[3] = p[3].copy(x = 0.43, y = 0.495)
        p[4] = p[4].copy(x = 0.485, y = 0.495)
        for ((rotation, expected) in listOf(0.0 to PoseId.POINTING_UP, PI / 2 to PoseId.POINTING_RIGHT,
            PI to PoseId.POINTING_DOWN, -PI / 2 to PoseId.POINTING_LEFT)) {
            for (mirror in listOf(false, true)) for (aspect in listOf(0.75, 1.0, 1.5)) {
                val transformed = p.map {
                    val x = (it.x - 0.5) * if (mirror) -1 else 1
                    val y = it.y - 0.5
                    it.copy(x = 0.5 + (x * cos(rotation) - y * sin(rotation)) / aspect,
                        y = 0.5 + x * sin(rotation) + y * cos(rotation))
                }
                assertEquals(expected, classifier.classify(transformed, aspect))
            }
        }
        p[4] = p[4].copy(z = -0.5)
        assertNull(classifier.classify(p, 1.0))
    }

    @Test fun discreteLatchSurvivesUnknownAndRequiresTimedValidRelease() {
        val actions = PoseActions(enabled = true).withBinding(PoseId.POINTING_UP, PoseAction.BACK)
        val detector = CustomPoseDetector(actions, config)
        fun sample(t: Long, p: PoseId?) = detector.processPose(t, p)
        assertNull(sample(0, PoseId.POINTING_UP).event)
        assertNull(sample(100, PoseId.POINTING_UP).event)
        assertEquals(PoseId.POINTING_UP, sample(200, PoseId.POINTING_UP).event)
        sample(300, null)
        for (t in 400L..1200L step 100) assertNull(sample(t, PoseId.POINTING_UP).event)
        sample(1300, PoseId.CLOSED_HAND)
        sample(1400, null)
        sample(1500, PoseId.CLOSED_HAND)
        sample(1600, PoseId.CLOSED_HAND)
        sample(1700, PoseId.CLOSED_HAND)
        sample(1800, PoseId.POINTING_UP)
        sample(1900, PoseId.POINTING_UP)
        assertEquals(PoseId.POINTING_UP, sample(2000, PoseId.POINTING_UP).event)
    }

    @Test fun holdStopsImmediatelyAndRequiresNewConfirmationAndTargetRelease() {
        val actions = PoseActions(enabled = true)
            .withBinding(PoseId.POINTING_UP, PoseAction.CONTINUOUS_NEXT)
            .withBinding(PoseId.INDEX_MIDDLE, PoseAction.CONTINUOUS_NEXT)
        val detector = CustomPoseDetector(actions, config)
        fun sample(t: Long, p: PoseId?) = detector.processPose(t, p)
        sample(0, PoseId.POINTING_UP); sample(100, PoseId.POINTING_UP)
        assertEquals(PoseId.POINTING_UP, sample(200, PoseId.POINTING_UP).held)
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
        val detector = CustomPoseDetector(PoseActions(enabled = true)
            .withBinding(PoseId.POINTING_UP, PoseAction.BACK).withBinding(PoseId.INDEX_MIDDLE, PoseAction.NONE), config)
        fun sample(t: Long, p: PoseId?) = detector.processPose(t, p)
        sample(0, PoseId.POINTING_UP); sample(100, PoseId.POINTING_UP)
        assertNull(sample(100, PoseId.POINTING_UP).event)
        sample(200, PoseId.POINTING_UP); sample(300, PoseId.POINTING_UP)
        assertNull(sample(600, PoseId.POINTING_UP).event)
        sample(700, PoseId.POINTING_UP)
        assertEquals(PoseId.POINTING_UP, sample(800, PoseId.POINTING_UP).event)
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
        val inactive = PoseActions(enabled = true)
            .withBinding(PoseId.POINTING_UP, PoseAction.BACK).withBinding(PoseId.INDEX_MIDDLE, PoseAction.NONE)
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
        val detector = CustomPoseDetector(PoseActions(enabled = true)
            .withBinding(PoseId.POINTING_UP, PoseAction.BACK), config)
        for (t in 0L..200L step 100) detector.processPose(t, PoseId.POINTING_UP)
        detector.targetChanged()
        for (t in 300L..1000L step 100) assertNull(detector.processPose(t, PoseId.POINTING_UP).event)
        for (t in 1100L..1300L step 100) detector.processPose(t, PoseId.CLOSED_HAND)
        detector.processPose(1400, PoseId.POINTING_UP)
        detector.processPose(1500, PoseId.POINTING_UP)
        assertEquals(PoseId.POINTING_UP, detector.processPose(1600, PoseId.POINTING_UP).event)
    }

    @Test fun targetFenceSurvivesLossAndRepeatedTargetChanges() {
        val detector = CustomPoseDetector(PoseActions(enabled = true)
            .withBinding(PoseId.POINTING_UP, PoseAction.CONTINUOUS_NEXT), config)
        for (t in 0L..200L step 100) detector.processPose(t, PoseId.POINTING_UP)
        detector.processPose(300, null)
        detector.targetChanged()
        detector.processPose(400, PoseId.CLOSED_HAND)
        detector.targetChanged()
        for (t in 500L..1000L step 100) assertNull(detector.processPose(t, PoseId.POINTING_UP).held)
        for (t in 1100L..1300L step 100) detector.processPose(t, PoseId.CLOSED_HAND)
        detector.processPose(1400, PoseId.POINTING_UP)
        detector.processPose(1500, PoseId.POINTING_UP)
        assertEquals(PoseId.POINTING_UP, detector.processPose(1600, PoseId.POINTING_UP).held)
    }

    @Test fun existingFlickOwnershipCannotBeStolenByPoseCandidate() {
        val actions = PoseActions(enabled = true).withBinding(PoseId.POINTING_UP, PoseAction.BACK)
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

    @Test fun twoFingerUpUsesItsOwnHoldAndDefaultPointingScrollsStayHeld() {
        val detector = CustomPoseDetector(PoseActions(enabled = true), config)
        fun sample(t: Long, open: Set<Int>) = detector.process(HandTrackingResult(t, HandPose(points(open))))
        sample(0, setOf(5)); sample(100, setOf(5))
        assertEquals(PoseId.POINTING_UP, sample(200, setOf(5)).event)
        sample(300, setOf(5, 9)); sample(400, setOf(5, 9))
        val combined = sample(500, setOf(5, 9))
        assertEquals(PoseId.INDEX_MIDDLE, combined.held)
        assertNull(combined.event)
        assertNull(sample(600, emptySet()).held)
        for (pose in PoseId.entries.filter { it.isPointing }) {
            val auto = CustomPoseDetector(PoseActions(enabled = true, autoscrollPointing = true), config)
            auto.processPose(0, pose); auto.processPose(100, pose)
            for (t in 200L..800L step 100) {
                val result = auto.processPose(t, pose)
                assertEquals(pose, result.held)
                assertNull(result.event)
            }
            assertNull(auto.processPose(900, null).held)
        }
        val diagonal = points(setOf(5, 9), rotation = 0.65)
        assertNull(CustomPoseClassifier(config).classify(diagonal, 1.0))
        assertEquals(PoseId.INDEX_MIDDLE, CustomPoseClassifier(config).classify(diagonal, 1.0, PoseId.INDEX_MIDDLE))
    }

    @Test fun editedPointingActionChoosesHoldDiscreteOrNoneWhileAutoscrollStaysOn() {
        for (action in listOf(PoseAction.SCROLL_LEFT, PoseAction.BACK, PoseAction.NONE)) {
            val detector = CustomPoseDetector(PoseActions(enabled = true, autoscrollPointing = true)
                .withBinding(PoseId.POINTING_UP, action), config)
            detector.processPose(0, PoseId.POINTING_UP)
            detector.processPose(100, PoseId.POINTING_UP)
            val confirmed = detector.processPose(200, PoseId.POINTING_UP)
            assertEquals(if (action == PoseAction.BACK) PoseId.POINTING_UP else null, confirmed.event)
            assertEquals(if (action == PoseAction.SCROLL_LEFT) PoseId.POINTING_UP else null, confirmed.held)
            assertEquals(action != PoseAction.NONE, detector.ownsGesture)
            assertNull(detector.processPose(300, PoseId.POINTING_UP).event)
        }
    }
}
