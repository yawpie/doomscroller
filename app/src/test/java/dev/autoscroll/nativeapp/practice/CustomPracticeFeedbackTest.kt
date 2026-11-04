package dev.autoscroll.nativeapp.practice

import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.NativeSettings
import org.junit.Assert.*
import org.junit.Test

class CustomPracticeFeedbackTest {
    private fun sample(time: Long, open: Set<Int>, rotation: Double = 0.0): HandTrackingResult {
        val points = MutableList(21) { 0.0 to 0.0 }
        points[0] = 0.0 to 0.12; points[1] = -0.11 to 0.09; points[2] = -0.11 to 0.03
        points[3] = -0.04 to 0.03; points[4] = 0.02 to 0.03
        for ((mcp, x, y) in listOf(Triple(5, -0.065, -0.06), Triple(9, -0.02, -0.07),
            Triple(13, 0.03, -0.06), Triple(17, 0.075, -0.04))) {
            points[mcp] = x to y; points[mcp + 1] = x to y - 0.085
            points[mcp + 2] = if (mcp in open) x to y - 0.16 else x + 0.05 to y - 0.085
            points[mcp + 3] = if (mcp in open) x to y - 0.22 else x + 0.05 to y - 0.035
        }
        return HandTrackingResult(time, HandPose(points.mapIndexed { index, (x, y) ->
            HandLandmark(index, 0.5 + x * kotlin.math.cos(rotation) - y * kotlin.math.sin(rotation),
                0.5 + x * kotlin.math.sin(rotation) + y * kotlin.math.cos(rotation), 0.0)
        }))
    }

    private fun feedback(index: PoseAction, middle: PoseAction = PoseAction.NONE,
        both: PoseAction = PoseAction.NONE, inverted: Boolean = false): PracticeFeedback {
        val actions = PoseActions(enabled = true)
            .withBinding(PoseId.POINTING_UP, index).withBinding(PoseId.MIDDLE_ONLY, middle)
            .withBinding(PoseId.INDEX_MIDDLE, both)
        return PracticeFeedback(NativeSettings(mode = GestureMode.POSE, poseActions = actions, inverted = inverted))
    }

    @Test fun combinationUsesOnlyItsOwnActionAndBackStaysLocalAtRoot() {
        val f = feedback(PoseAction.SINGLE_TAP, PoseAction.DOUBLE_TAP, PoseAction.BACK)
        f.openLocalDetail()
        for (t in 0L..800L step 100) f.process(sample(t, setOf(5, 9)))
        assertFalse(f.state.localDetailOpen)
        assertEquals("Back to sample list", f.state.lastAction)
        assertEquals(1, f.state.actionCount)
        assertTrue(f.state.videoPlaying)
        assertEquals(0, f.state.doubleTaps)
        for (t in 900L..1100L step 100) f.process(sample(t, emptySet()))
        for (t in 1200L..1400L step 100) f.process(sample(t, setOf(5, 9)))
        assertEquals("Already at sample root", f.state.lastAction)
        assertEquals(2, f.state.actionCount)
    }

    @Test fun customDirectionIgnoresInversionAndNoneDoesNotCount() {
        val normal = feedback(PoseAction.NEXT_ITEM)
        val inverted = feedback(PoseAction.NEXT_ITEM, inverted = true)
        val none = feedback(PoseAction.NONE)
        for (t in 0L..300L step 100) {
            val frame = sample(t, setOf(5))
            normal.process(frame); inverted.process(frame); none.process(frame)
        }
        assertEquals(3.0, normal.state.position, 0.0)
        assertEquals(normal.state.position, inverted.state.position, 0.0)
        assertEquals(0, none.state.actionCount)
        assertEquals("No action assigned to this pose", none.state.inactiveReason)
    }

    @Test fun heldMotionStopsOnStaleAndGapAndRequiresFreshConfirmation() {
        val f = feedback(PoseAction.NONE, both = PoseAction.CONTINUOUS_NEXT, inverted = true)
        for (t in 0L..500L step 100) f.process(sample(t, setOf(5, 9)))
        assertEquals(1, f.state.holdActivations)
        assertEquals(0, f.state.actionCount)
        assertTrue(f.state.position > 2.0)
        val before = f.state.position
        f.process(sample(600, setOf(5, 9)), nowMs = 1000)
        assertEquals(before, f.state.position, 0.0)
        assertEquals(0.0, f.state.velocity, 0.0)
        for (t in 1100L..1200L step 100) f.process(sample(t, setOf(5, 9)))
        assertEquals(before, f.state.position, 0.0)
        f.process(sample(1300, setOf(5, 9)))
        assertEquals(2, f.state.holdActivations)
        assertEquals(before, f.state.position, 0.0)
        f.process(sample(1700, setOf(5, 9)))
        assertEquals(before, f.state.position, 0.0)
        assertEquals(0.0, f.state.velocity, 0.0)
    }

    @Test fun horizontalPosesMoveOnlyLocalPagesOncePerAppearanceAndClampAtEdges() {
        for ((binding, direction) in listOf(PoseAction.SCROLL_LEFT to -1, PoseAction.SCROLL_RIGHT to 1)) {
            val f = feedback(binding, inverted = true)
            for (t in 0L..800L step 100) f.process(sample(t, setOf(5)))
            assertEquals(2.0 + direction, f.state.horizontalPosition, 0.0)
            assertEquals(1, f.state.actionCount)
            assertEquals(2.0, f.state.position, 0.0)
            assertEquals(if (direction < 0) "Scroll left" else "Scroll right", f.state.lastAction)
            for (cycle in 1L..4L) {
                val start = cycle * 1000
                for (t in start..start + 200 step 100) f.process(sample(t, emptySet()))
                for (t in start + 300..start + 800 step 100) f.process(sample(t, setOf(5)))
            }
            assertEquals(if (direction < 0) 0.0 else 4.0, f.state.horizontalPosition, 0.0)
            assertEquals(5, f.state.actionCount)
            assertTrue(f.state.videoPlaying)
            assertEquals(0, f.state.doubleTaps)
        }
    }

    @Test fun heldDistanceUsesElapsedTimeAtDifferentSampleRates() {
        val fast = feedback(PoseAction.NONE, both = PoseAction.CONTINUOUS_NEXT)
        val slow = feedback(PoseAction.NONE, both = PoseAction.CONTINUOUS_NEXT)
        for (t in 0L..1000L step 50) fast.process(sample(t, setOf(5, 9)))
        for (t in 0L..1000L step 100) slow.process(sample(t, setOf(5, 9)))
        assertEquals(fast.state.position, slow.state.position, 0.000001)
        assertEquals(1, fast.state.holdActivations)
        assertEquals(1, slow.state.holdActivations)
    }

    @Test fun duplicateSampleStopsHoldWithoutRearmingDiscreteAction() {
        val held = feedback(PoseAction.NONE, both = PoseAction.CONTINUOUS_NEXT)
        for (t in 0L..400L step 100) held.process(sample(t, setOf(5, 9)))
        val before = held.state.position
        held.process(sample(400, setOf(5, 9)))
        assertEquals(0.0, held.state.velocity, 0.0)
        held.process(sample(500, setOf(5, 9)))
        assertEquals(before, held.state.position, 0.0)
        val discrete = feedback(PoseAction.BACK)
        for (t in 0L..400L step 100) discrete.process(sample(t, setOf(5)))
        discrete.process(sample(400, setOf(5)))
        for (t in 500L..1000L step 100) discrete.process(sample(t, setOf(5)))
        assertEquals(1, discrete.state.actionCount)
    }

    @Test fun pointingAutoscrollMovesOnlyTheChosenAxisAndStopsOnHandLoss() {
        for ((rotation, direction) in listOf(0.0 to -1, kotlin.math.PI to 1,
            -kotlin.math.PI / 2 to -1, kotlin.math.PI / 2 to 1)) {
            val horizontal = kotlin.math.abs(rotation) == kotlin.math.PI / 2
            val f = PracticeFeedback(NativeSettings(mode = GestureMode.POSE, inverted = true,
                poseActions = PoseActions(enabled = true, autoscrollPointing = true)))
            for (t in 0L..1000L step 100) f.process(sample(t, setOf(5), rotation))
            assertEquals(2.0 + if (horizontal) 0.0 else direction * 2.0, f.state.position, 0.000001)
            assertEquals(2.0 + if (horizontal) direction * 2.0 else 0.0, f.state.horizontalPosition, 0.000001)
            assertEquals(0, f.state.holdActivations)
            assertEquals(2, f.state.actionCount)
            val before = f.state
            f.process(HandTrackingResult(1100))
            assertEquals(before.position, f.state.position, 0.0)
            assertEquals(before.horizontalPosition, f.state.horizontalPosition, 0.0)
            assertEquals(0.0, f.state.velocity, 0.0)
        }
    }

    @Test fun twoFingersScrollContinuouslyWhileSinglePointKeepsItsSavedAction() {
        val settings = NativeSettings(mode = GestureMode.POSE, poseActions = PoseActions(enabled = true))
        val two = PracticeFeedback(settings)
        val single = PracticeFeedback(settings)
        for (t in 0L..800L step 100) {
            two.process(sample(t, setOf(5, 9)))
            single.process(sample(t, setOf(5)))
        }
        assertTrue(two.state.position > 2.0)
        assertEquals(1, two.state.holdActivations)
        assertEquals(0, two.state.actionCount)
        assertEquals(1.0, single.state.position, 0.0)
        assertEquals(1, single.state.actionCount)
        assertEquals(0, single.state.holdActivations)
    }
}
