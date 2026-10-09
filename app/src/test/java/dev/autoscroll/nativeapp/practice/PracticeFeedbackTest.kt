package dev.autoscroll.nativeapp.practice

import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.NativeSettings
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class PracticeFeedbackTest {
    private fun sample(time: Long, pose: RecognizedHandPose, yOffset: Double = 0.5): HandTrackingResult {
        val points = MutableList(21) { 0.0 to 0.0 }
        points[0] = 0.0 to 0.12
        points[1] = -0.11 to 0.09
        points[2] = -0.11 to 0.03
        points[3] = if (pose == RecognizedHandPose.CLOSED_HAND) -0.04 to 0.03 else -0.11 to -0.04
        points[4] = if (pose == RecognizedHandPose.CLOSED_HAND) 0.02 to 0.03 else -0.11 to -0.10
        for ((mcp, x, y) in listOf(Triple(5, -0.065, -0.06), Triple(9, -0.02, -0.07),
            Triple(13, 0.03, -0.06), Triple(17, 0.075, -0.04))) {
            val extended = pose == RecognizedHandPose.NEUTRAL ||
                (mcp == 5 && (pose == RecognizedHandPose.POINTING_UP || pose == RecognizedHandPose.POINTING_DOWN))
            points[mcp] = x to y
            points[mcp + 1] = x to y - 0.085
            points[mcp + 2] = if (extended) x to y - 0.16 else x + 0.05 to y - 0.085
            points[mcp + 3] = if (extended) x to y - 0.22 else x + 0.05 to y - 0.035
        }
        val angle = if (pose == RecognizedHandPose.POINTING_DOWN) PI else 0.0
        return HandTrackingResult(time, HandPose(points.mapIndexed { index, (x, y) ->
            HandLandmark(index, 0.5 + x * kotlin.math.cos(angle) - y * kotlin.math.sin(angle),
                yOffset + x * kotlin.math.sin(angle) + y * kotlin.math.cos(angle), 0.0)
        }))
    }

    private fun hold(feedback: PracticeFeedback, pose: RecognizedHandPose, times: LongProgression) =
        times.forEach { feedback.process(sample(it, pose)) }

    @Test fun pointingAndThumbRepeatUsingRealRecognitionAndSavedDelay() {
        for (pose in listOf(RecognizedHandPose.POINTING_DOWN, RecognizedHandPose.THUMBS_UP)) {
            val feedback = PracticeFeedback(NativeSettings(mode = GestureMode.POSE))
            hold(feedback, pose, 0L..1800L step 100)
            assertEquals(3, feedback.state.actionCount)
            if (pose == RecognizedHandPose.POINTING_DOWN) assertEquals(4.0, feedback.state.position, 0.0)
            else assertEquals(3, feedback.state.doubleTaps)
        }
        val delayed = PracticeFeedback(NativeSettings(mode = GestureMode.POSE,
            pose = NativeSettings().pose.copy(cooldownMs = 1000)))
        hold(delayed, RecognizedHandPose.THUMBS_UP, 0L..1800L step 100)
        assertEquals(2, delayed.state.doubleTaps)
    }

    @Test fun heldOpenHandTapsOnceAndTrackingLossDoesNotRearmIt() {
        val feedback = PracticeFeedback(NativeSettings(mode = GestureMode.POSE))
        hold(feedback, RecognizedHandPose.NEUTRAL, 0L..2500L step 100)
        assertFalse(feedback.state.videoPlaying)
        assertEquals(1, feedback.state.actionCount)
        feedback.process(HandTrackingResult(2600))
        hold(feedback, RecognizedHandPose.NEUTRAL, 2700L..3200L step 100)
        assertEquals(1, feedback.state.actionCount)
        feedback.process(sample(3300, RecognizedHandPose.CLOSED_HAND))
        hold(feedback, RecognizedHandPose.NEUTRAL, 3400L..3700L step 100)
        assertTrue(feedback.state.videoPlaying)
        assertEquals(2, feedback.state.actionCount)
    }

    @Test fun inversionReversesNavigationButNotTapsAndResetClearsEvidence() {
        val normal = PracticeFeedback(NativeSettings(mode = GestureMode.POSE))
        val inverted = PracticeFeedback(NativeSettings(mode = GestureMode.POSE, inverted = true))
        hold(normal, RecognizedHandPose.POINTING_DOWN, 0L..200L step 100)
        hold(inverted, RecognizedHandPose.POINTING_DOWN, 0L..200L step 100)
        assertEquals(3.0, normal.state.position, 0.0)
        assertEquals(1.0, inverted.state.position, 0.0)
        hold(inverted, RecognizedHandPose.NEUTRAL, 300L..1000L step 100)
        assertFalse(inverted.state.videoPlaying)
        inverted.reset()
        assertEquals(PracticeSample(), inverted.state)
        hold(inverted, RecognizedHandPose.NEUTRAL, 0L..200L step 100)
        assertEquals(1, inverted.state.actionCount)
    }

    @Test fun continuousModeMovesOnlyLocalFeedAndNoHandStopsMovement() {
        val feedback = PracticeFeedback(NativeSettings(mode = GestureMode.CONTINUOUS))
        val inverted = PracticeFeedback(NativeSettings(mode = GestureMode.CONTINUOUS, inverted = true))
        for (time in 0L..1000L step 100) {
            val result = sample(time, RecognizedHandPose.NEUTRAL, yOffset = 0.3 + time / 4000.0)
            feedback.process(result)
            inverted.process(result)
        }
        assertTrue(feedback.state.position > 2.0)
        assertEquals(4.0 - feedback.state.position, inverted.state.position, 0.000001)
        assertTrue(feedback.state.videoPlaying)
        assertEquals(0, feedback.state.actionCount)
        assertTrue(feedback.state.velocity > 0)
        feedback.process(HandTrackingResult(1100))
        assertEquals(0.0, feedback.state.velocity, 0.0)
        assertFalse(feedback.state.handDetected)
        val position = feedback.state.position
        feedback.process(HandTrackingResult(1200))
        assertEquals(position, feedback.state.position, 0.0)
    }

    @Test fun flickModeNavigatesWithInversionAndNeverUsesPoseTaps() {
        val normal = PracticeFeedback(NativeSettings(mode = GestureMode.FLICK))
        val inverted = PracticeFeedback(NativeSettings(mode = GestureMode.FLICK, inverted = true))
        for ((time, y) in listOf(0L to 0.3, 50L to 0.34, 100L to 0.40, 150L to 0.46, 200L to 0.52)) {
            val result = sample(time, RecognizedHandPose.NEUTRAL, yOffset = y)
            normal.process(result)
            inverted.process(result)
        }
        assertEquals(1, normal.state.actionCount)
        assertEquals(3.0, normal.state.position, 0.0)
        assertEquals(1.0, inverted.state.position, 0.0)
        assertTrue(normal.state.videoPlaying)
        assertEquals(0, normal.state.doubleTaps)
    }

    @Test fun oldOrDuplicateSamplesCannotActOnPracticeFeed() {
        val feedback = PracticeFeedback(NativeSettings(mode = GestureMode.POSE))
        hold(feedback, RecognizedHandPose.NEUTRAL, 0L..200L step 100)
        val before = feedback.state
        feedback.process(sample(200, RecognizedHandPose.THUMBS_UP))
        feedback.process(sample(100, RecognizedHandPose.POINTING_UP))
        assertEquals(before, feedback.state)
    }
}
