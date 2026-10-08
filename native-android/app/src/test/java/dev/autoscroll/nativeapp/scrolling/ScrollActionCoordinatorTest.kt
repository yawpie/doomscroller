package dev.autoscroll.nativeapp.scrolling

import dev.autoscroll.nativeapp.gesture.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScrollActionCoordinatorTest {
    private class FakeExecutor : ScrollExecutor {
        override var isReady = true
        val requests = mutableListOf<ScrollRequest>()
        var completion: CompletableDeferred<ScrollExecutionResult>? = null
        var ignoreCancellation = false
        override suspend fun execute(request: ScrollRequest): ScrollExecutionResult {
            requests += request
            val wait = completion ?: return ScrollExecutionResult.COMPLETED
            return if (ignoreCancellation) withContext(NonCancellable) { wait.await() } else wait.await()
        }
    }

    private fun pose(direction: RecognizedHandPose, timestamp: Long) = GestureDecision(
        motion = HandMotion(HandAction.IDLE), pose = PoseEvent(direction, timestamp), suppressesContinuous = true)

    private fun flick(direction: FlickDirection, timestamp: Long) = GestureDecision(
        motion = HandMotion(HandAction.IDLE), flick = FlickEvent(direction, timestamp, 0.2, 1.0, 200), suppressesContinuous = true)

    @Test fun openHandAndThumbMapToTapsWithoutSwipeSettingsOrDirectionInversion() = runTest {
        assertTrue(dev.autoscroll.nativeapp.settings.NativeSettings().pose.tapGesturesEnabled)
        val executor = FakeExecutor()
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.configure(inverted = true)
        coordinator.setSessionActive(true)
        coordinator.process(pose(RecognizedHandPose.NEUTRAL, now)); runCurrent()
        now += 100
        coordinator.process(pose(RecognizedHandPose.THUMBS_UP, now)); runCurrent()
        assertEquals(listOf(ScrollRequest(ScrollAction.SINGLE_TAP), ScrollRequest(ScrollAction.DOUBLE_TAP)), executor.requests)
        coordinator.process(pose(RecognizedHandPose.CLOSED_HAND, now + 1)); runCurrent()
        assertEquals(2, executor.requests.size)
    }

    @Test fun tapsHonorSessionFreshnessAndUnavailableService() = runTest {
        val executor = FakeExecutor()
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.process(pose(RecognizedHandPose.NEUTRAL, now)); runCurrent()
        coordinator.setSessionActive(true)
        coordinator.process(pose(RecognizedHandPose.THUMBS_UP, 999)); runCurrent()
        now += 500
        coordinator.process(pose(RecognizedHandPose.NEUTRAL, 1100)); runCurrent()
        coordinator.process(pose(RecognizedHandPose.NEUTRAL, 1501)); runCurrent()
        executor.isReady = false
        coordinator.process(pose(RecognizedHandPose.THUMBS_UP, now)); runCurrent()
        assertTrue(executor.requests.isEmpty())
        executor.isReady = true
        now += 100
        coordinator.process(pose(RecognizedHandPose.THUMBS_UP, now)); runCurrent()
        coordinator.process(pose(RecognizedHandPose.THUMBS_UP, now)); runCurrent()
        assertEquals(1, executor.requests.size)
    }

    @Test fun tapWhileBusyIsConsumedAndPauseCancelsScheduledTap() = runTest {
        val executor = FakeExecutor().also { it.completion = CompletableDeferred() }
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.setSessionActive(true)
        coordinator.process(pose(RecognizedHandPose.NEUTRAL, now)); runCurrent()
        now += 100
        coordinator.process(pose(RecognizedHandPose.THUMBS_UP, now)); runCurrent()
        executor.completion!!.complete(ScrollExecutionResult.COMPLETED); runCurrent()
        coordinator.process(pose(RecognizedHandPose.THUMBS_UP, now)); runCurrent()
        assertEquals(1, executor.requests.size)
        now += 100
        coordinator.process(pose(RecognizedHandPose.THUMBS_UP, now))
        coordinator.setSessionActive(false); runCurrent()
        assertEquals(1, executor.requests.size)
        assertEquals(0.0, coordinator.state.value.continuousPixelsPerSecond, 0.0)
    }

    @Test fun downwardPoseAndFlickMapForwardAndInversionAppliesExactlyOnce() = runTest {
        val executor = FakeExecutor()
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.setSessionActive(true)
        coordinator.process(pose(RecognizedHandPose.POINTING_DOWN, now)); runCurrent()
        now += 100
        coordinator.process(flick(FlickDirection.UP, now)); runCurrent()
        coordinator.configure(inverted = true)
        now += 100
        coordinator.process(pose(RecognizedHandPose.POINTING_DOWN, now)); runCurrent()
        now += 100
        coordinator.process(flick(FlickDirection.UP, now)); runCurrent()
        assertEquals(listOf(ScrollAction.FORWARD, ScrollAction.BACKWARD, ScrollAction.BACKWARD, ScrollAction.FORWARD),
            executor.requests.map { it.action })
    }

    @Test fun staleFuturePreviousSessionAndDuplicateEventsNeverDispatch() = runTest {
        val executor = FakeExecutor()
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.setSessionActive(true)
        coordinator.process(pose(RecognizedHandPose.POINTING_DOWN, 999))
        coordinator.process(pose(RecognizedHandPose.POINTING_DOWN, 1001))
        coordinator.process(pose(RecognizedHandPose.POINTING_DOWN, 1000)); runCurrent()
        coordinator.process(pose(RecognizedHandPose.POINTING_DOWN, 1000)); runCurrent()
        now = 1500
        coordinator.process(pose(RecognizedHandPose.POINTING_DOWN, 1100)); runCurrent()
        assertEquals(1, executor.requests.size)
        assertEquals("Stale gesture or previous session", coordinator.state.value.rejectionReason)
    }

    @Test fun pausedAndUnavailableRejectManualAndRecognition() = runTest {
        val executor = FakeExecutor()
        val coordinator = ScrollActionCoordinator(executor, this, { 1000L })
        coordinator.manual(ScrollAction.FORWARD)
        coordinator.process(pose(RecognizedHandPose.POINTING_DOWN, 1000)); runCurrent()
        assertTrue(executor.requests.isEmpty())
        coordinator.setSessionActive(true)
        executor.isReady = false
        coordinator.manual(ScrollAction.FORWARD); runCurrent()
        assertTrue(executor.requests.isEmpty())
        assertEquals("Accessibility service unavailable", coordinator.state.value.rejectionReason)
    }

    @Test fun busyDropsNewEventInsteadOfQueueingOrReplayingIt() = runTest {
        val executor = FakeExecutor().also { it.completion = CompletableDeferred() }
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.setSessionActive(true)
        coordinator.process(pose(RecognizedHandPose.POINTING_DOWN, now)); runCurrent()
        now++
        coordinator.process(pose(RecognizedHandPose.POINTING_UP, now)); runCurrent()
        assertEquals(1, executor.requests.size)
        assertTrue(coordinator.state.value.busy)
        executor.completion!!.complete(ScrollExecutionResult.COMPLETED); runCurrent()
        coordinator.process(pose(RecognizedHandPose.POINTING_UP, now)); runCurrent()
        assertEquals(1, executor.requests.size)
        assertFalse(coordinator.state.value.busy)
    }

    @Test fun pauseBeforeScheduledDispatchCancelsOutput() = runTest {
        val executor = FakeExecutor()
        val coordinator = ScrollActionCoordinator(executor, this, { 1000L })
        coordinator.setSessionActive(true)
        coordinator.manual(ScrollAction.FORWARD)
        coordinator.setSessionActive(false)
        runCurrent()
        assertTrue(executor.requests.isEmpty())
    }

    @Test fun completionFromPreviousGenerationCannotOverwriteNewDispatch() = runTest {
        val old = CompletableDeferred<ScrollExecutionResult>()
        val executor = FakeExecutor().also { it.completion = old; it.ignoreCancellation = true }
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.setSessionActive(true)
        coordinator.manual(ScrollAction.FORWARD); runCurrent()
        coordinator.setSessionActive(false)
        now += 100
        coordinator.setSessionActive(true)
        val current = CompletableDeferred<ScrollExecutionResult>()
        executor.completion = current
        coordinator.manual(ScrollAction.BACKWARD); runCurrent()
        old.complete(ScrollExecutionResult.CANCELLED); runCurrent()
        assertTrue(coordinator.state.value.busy)
        assertEquals(ScrollAction.BACKWARD, coordinator.state.value.lastAction)
        assertNull(coordinator.state.value.lastResult)
        current.complete(ScrollExecutionResult.COMPLETED); runCurrent()
        assertFalse(coordinator.state.value.busy)
        assertEquals(ScrollExecutionResult.COMPLETED, coordinator.state.value.lastResult)
    }

    @Test fun freshnessIsCheckedAgainWhenScheduledDispatchActuallyRuns() = runTest {
        val executor = FakeExecutor()
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.setSessionActive(true)
        coordinator.process(pose(RecognizedHandPose.POINTING_DOWN, now))
        now += 400
        runCurrent()
        assertTrue(executor.requests.isEmpty())
    }

    @Test fun motionRemainsProportionalWithSmoothingAndInversion() = runTest {
        val executor = FakeExecutor()
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now }, ScrollConfig(smoothingAlpha = 1.0))
        coordinator.setSessionActive(true)
        coordinator.process(GestureDecision(HandMotion(HandAction.MOVE_DOWN, verticalVelocity = 0.2)), now); runCurrent()
        assertEquals(ScrollAction.FORWARD, executor.requests.last().action)
        assertEquals(36.0, executor.requests.last().distancePixels!!, 0.00001)
        assertEquals(100L, executor.requests.last().durationMs)
        coordinator.configure(inverted = true)
        now += 100
        coordinator.process(GestureDecision(HandMotion(HandAction.MOVE_DOWN, verticalVelocity = 0.4)), now); runCurrent()
        assertEquals(ScrollAction.BACKWARD, executor.requests.last().action)
        assertEquals(72.0, executor.requests.last().distancePixels!!, 0.00001)
        now += 400
        coordinator.process(GestureDecision(HandMotion(HandAction.MOVE_DOWN, verticalVelocity = 0.4)), now - 400); runCurrent()
        assertEquals(2, executor.requests.size)
        assertEquals(0.0, coordinator.state.value.continuousPixelsPerSecond, 0.0)
    }

    @Test fun dispatchLatencyUsesMonotonicEventTimestampAndManualHasNoInferenceLatency() = runTest {
        val executor = FakeExecutor()
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.setSessionActive(true)
        now += 25
        coordinator.process(pose(RecognizedHandPose.POINTING_DOWN, 1000)); runCurrent()
        assertEquals(25L, coordinator.state.value.lastGestureToActionLatencyMs)
        coordinator.manual(ScrollAction.BACKWARD); runCurrent()
        assertNull(coordinator.state.value.lastGestureToActionLatencyMs)
    }
}
