package dev.autoscroll.nativeapp.gesture

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import org.junit.Assert.*
import org.junit.Test

class TapGestureTest {
    private val config = HandPoseDetectionConfig(tapGesturesEnabled = true, rearmMode = HandPoseRearmMode.TIMED)

    private fun points(pose: RecognizedHandPose, rotation: Double = 0.0, mirror: Boolean = false,
        scale: Double = 1.0, xOffset: Double = 0.5, yOffset: Double = 0.5): MutableList<HandLandmark> {
        val p = MutableList(21) { 0.0 to 0.0 }
        p[0] = 0.0 to 0.12
        p[1] = -0.11 to 0.09
        p[2] = -0.11 to 0.03
        p[3] = if (pose == RecognizedHandPose.CLOSED_HAND) -0.04 to 0.03 else -0.11 to -0.04
        p[4] = if (pose == RecognizedHandPose.CLOSED_HAND) 0.02 to 0.03 else -0.11 to -0.10
        for ((mcp, x, y) in listOf(Triple(5, -0.065, -0.06), Triple(9, -0.02, -0.07),
            Triple(13, 0.03, -0.06), Triple(17, 0.075, -0.04))) {
            val extended = pose == RecognizedHandPose.NEUTRAL ||
                (mcp == 5 && (pose == RecognizedHandPose.POINTING_UP || pose == RecognizedHandPose.POINTING_DOWN))
            p[mcp] = x to y
            p[mcp + 1] = x to y - 0.085
            p[mcp + 2] = if (extended) x to y - 0.16 else x + 0.05 to y - 0.085
            p[mcp + 3] = if (extended) x to y - 0.22 else x + 0.05 to y - 0.035
        }
        val angle = rotation + if (pose == RecognizedHandPose.POINTING_DOWN) PI else 0.0
        return p.mapIndexed { i, (x, y) ->
            val mirroredX = if (mirror) -x else x
            HandLandmark(i, xOffset + scale * (mirroredX * cos(angle) - y * sin(angle)),
                yOffset + scale * (mirroredX * sin(angle) + y * cos(angle)), 0.0)
        }.toMutableList()
    }

    private fun sample(time: Long, pose: RecognizedHandPose) = HandTrackingResult(time, HandPose(points(pose)))
    private fun hold(detector: HandPoseDetector, pose: RecognizedHandPose, times: LongProgression) =
        times.mapNotNull { detector.process(sample(it, pose)) }

    @Test fun thumbsUpWorksOnBothHandsAtDifferentSizesAndPositions() {
        val classifier = HandPoseClassifier(config)
        for (mirror in listOf(false, true)) for (rotation in listOf(-0.3, 0.0, 0.3)) {
            for ((scale, x, y) in listOf(Triple(0.45, 0.25, 0.3), Triple(1.0, 0.5, 0.5), Triple(1.25, 0.65, 0.6))) {
                assertEquals(RecognizedHandPose.THUMBS_UP,
                    classifier.classify(points(RecognizedHandPose.THUMBS_UP, rotation, mirror, scale, x, y)))
            }
        }
        assertEquals(RecognizedHandPose.CLOSED_HAND, classifier.classify(points(RecognizedHandPose.CLOSED_HAND)))
        assertEquals(RecognizedHandPose.NEUTRAL, classifier.classify(points(RecognizedHandPose.NEUTRAL)))
        assertEquals(RecognizedHandPose.POINTING_UP, classifier.classify(points(RecognizedHandPose.POINTING_UP)))
    }

    @Test fun sidewaysThumbMixedFingersAndMalformedThumbNeverTriggerDoubleTap() {
        val classifier = HandPoseClassifier(config)
        assertNotEquals(RecognizedHandPose.THUMBS_UP, classifier.classify(points(RecognizedHandPose.THUMBS_UP, 1.57)))
        val mixed = points(RecognizedHandPose.THUMBS_UP)
        val open = points(RecognizedHandPose.NEUTRAL)
        for (i in 9..12) mixed[i] = open[i]
        assertEquals(RecognizedHandPose.UNKNOWN, classifier.classify(mixed))
        val collapsed = points(RecognizedHandPose.THUMBS_UP)
        for (i in 2..4) collapsed[i] = collapsed[1].copy(index = i)
        assertEquals(RecognizedHandPose.UNKNOWN, classifier.classify(collapsed))
        val invalid = points(RecognizedHandPose.THUMBS_UP)
        invalid[4] = invalid[4].copy(x = Double.NaN)
        assertEquals(RecognizedHandPose.UNKNOWN, classifier.classify(invalid))
    }

    @Test fun thumbUsesSeparateEntryAndExitThresholds() {
        val classifier = HandPoseClassifier(config)
        val thumb = RecognizedHandPose.THUMBS_UP
        assertNotEquals(thumb, classifier.classify(points(thumb, 0.65)))
        assertEquals(thumb, classifier.classify(points(thumb, 0.65), thumb))
        assertNotEquals(thumb, classifier.classify(points(thumb, 1.0), thumb))
    }

    @Test fun heldPointingAndThumbRepeatAfterTimeAndFreshConfirmationWithoutAnyReleasePose() {
        for (pose in listOf(RecognizedHandPose.POINTING_UP, RecognizedHandPose.POINTING_DOWN, RecognizedHandPose.THUMBS_UP)) {
            val events = hold(HandPoseDetector(config), pose, 0L..1800L step 100)
            assertEquals(listOf(200L, 900L, 1600L), events.map { it.timestampMs })
            assertTrue(events.all { it.pose == pose })
        }
    }

    @Test fun configuredDelayControlsRepeatIntervalAndCannotBeShortenedByOpenOrClosedHand() {
        val pointing = RecognizedHandPose.POINTING_UP
        for ((delay, expected) in listOf(300L to listOf(100L, 500L, 900L, 1300L, 1700L),
            1000L to listOf(100L, 1200L))) {
            val detector = HandPoseDetector(config.copy(cooldownMs = delay, confirmationDurationMs = 100))
            assertEquals(expected, hold(detector, pointing, 0L..1800L step 50).map { it.timestampMs })
        }
        for (release in listOf(RecognizedHandPose.NEUTRAL, RecognizedHandPose.CLOSED_HAND)) {
            val detector = HandPoseDetector(config)
            assertEquals(1, hold(detector, pointing, 0L..200L step 100).size)
            assertTrue(hold(detector, release, 300L..600L step 100).isEmpty())
            assertEquals(HandPosePhase.LATCHED, detector.phase)
            assertEquals(50L, detector.cooldownRemainingMs)
        }
    }

    @Test fun timerRearmsWithoutAHandAndAllowsDirectDirectionChangeAfterFreshConfirmation() {
        val detector = HandPoseDetector(config)
        hold(detector, RecognizedHandPose.POINTING_UP, 0L..200L step 100)
        assertNull(detector.process(HandTrackingResult(649)))
        assertEquals(HandPosePhase.LATCHED, detector.phase)
        assertEquals(1L, detector.cooldownRemainingMs)
        assertNull(detector.process(HandTrackingResult(650)))
        assertEquals(HandPosePhase.READY, detector.phase)
        assertEquals(0L, detector.cooldownRemainingMs)
        assertEquals(listOf(PoseEvent(RecognizedHandPose.POINTING_DOWN, 900)),
            hold(detector, RecognizedHandPose.POINTING_DOWN, 700L..900L step 100))
        val direct = HandPoseDetector(config)
        hold(direct, RecognizedHandPose.POINTING_UP, 0L..200L step 100)
        assertEquals(listOf(PoseEvent(RecognizedHandPose.POINTING_DOWN, 900)),
            hold(direct, RecognizedHandPose.POINTING_DOWN, 300L..900L step 100))
    }

    @Test fun openHandIsOnePauseTapPerPresentationEvenAfterDelayOrTrackingLoss() {
        val open = RecognizedHandPose.NEUTRAL
        val detector = HandPoseDetector(config)
        assertEquals(listOf(PoseEvent(open, 200)), hold(detector, open, 0L..5000L step 100))
        detector.process(HandTrackingResult(5100))
        assertTrue(hold(detector, open, 5200L..6000L step 100).isEmpty())
        assertTrue(detector.suppressesContinuous)
        detector.process(sample(6100, RecognizedHandPose.CLOSED_HAND))
        assertEquals(listOf(PoseEvent(open, 6400)), hold(detector, open, 6200L..6400L step 100))
    }

    @Test fun openHandAfterPointingOnlyTapsAndNeverRearmsBeforeTheDelay() {
        val detector = HandPoseDetector(config)
        hold(detector, RecognizedHandPose.POINTING_UP, 0L..200L step 100)
        assertEquals(listOf(PoseEvent(RecognizedHandPose.NEUTRAL, 900)),
            hold(detector, RecognizedHandPose.NEUTRAL, 300L..1500L step 100))
        assertEquals(HandPosePhase.READY, detector.phase)
        assertEquals(listOf(PoseEvent(RecognizedHandPose.THUMBS_UP, 1800)),
            hold(detector, RecognizedHandPose.THUMBS_UP, 1600L..1800L step 100))
    }

    @Test fun nativeTapConfigRejectsZeroDelayAndPoseBasedRearm() {
        assertThrows(IllegalArgumentException::class.java) { HandPoseDetector(config.copy(cooldownMs = 0)) }
        for (mode in listOf(HandPoseRearmMode.OPEN_HAND, HandPoseRearmMode.STABLE_POSE)) {
            assertThrows(IllegalArgumentException::class.java) { HandPoseDetector(config.copy(rearmMode = mode)) }
        }
    }

    @Test fun gapsDuplicateTimesAndUnknownPosesDiscardTapEvidence() {
        val detector = HandPoseDetector(config)
        detector.process(sample(0, RecognizedHandPose.THUMBS_UP))
        detector.process(sample(100, RecognizedHandPose.THUMBS_UP))
        assertNull(detector.process(sample(500, RecognizedHandPose.THUMBS_UP)))
        assertNull(detector.process(sample(500, RecognizedHandPose.THUMBS_UP)))
        assertEquals(HandPosePhase.READY, detector.phase)
        assertNull(detector.process(sample(600, RecognizedHandPose.THUMBS_UP)))
        detector.process(HandTrackingResult(700))
        assertNull(detector.process(sample(800, RecognizedHandPose.THUMBS_UP)))
        assertNull(detector.process(sample(900, RecognizedHandPose.THUMBS_UP)))
        assertNotNull(detector.process(sample(1000, RecognizedHandPose.THUMBS_UP)))
        val collapsed = MutableList(21) { HandLandmark(it, 0.5, 0.5, 0.0) }
        for (ms in 1100L..1600L step 100) detector.process(HandTrackingResult(ms, HandPose(collapsed)))
        assertEquals(listOf(PoseEvent(RecognizedHandPose.THUMBS_UP, 1900)),
            hold(detector, RecognizedHandPose.THUMBS_UP, 1700L..2000L step 100))
    }

    @Test fun poseTapsOwnHybridOutputAndCompatibilityReplayKeepsOpenHandNeutral() {
        val hybrid = GestureCoordinator(poseDetector = HandPoseDetector(config))
        val decisions = (0L..1000L step 100).map { hybrid.process(sample(it, RecognizedHandPose.NEUTRAL)) }
        assertTrue(decisions.all { it.suppressesContinuous && it.flick == null && it.motion.action == HandAction.IDLE })
        assertEquals(1, decisions.count { it.pose != null })
        val legacy = HandPoseDetector()
        assertTrue(hold(legacy, RecognizedHandPose.NEUTRAL, 0L..1000L step 100).isEmpty())
        assertEquals(RecognizedHandPose.UNKNOWN, HandPoseClassifier().classify(points(RecognizedHandPose.THUMBS_UP)))
    }
}
