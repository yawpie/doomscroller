package dev.autoscroll.nativeapp.scrolling

import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.NativeSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CustomScrollActionCoordinatorTest {
    private class Executor : ScrollExecutor {
        override var isReady = true
        val requests = mutableListOf<ScrollRequest>()
        var result = ScrollExecutionResult.COMPLETED
        var pending: CompletableDeferred<ScrollExecutionResult>? = null
        override suspend fun execute(request: ScrollRequest): ScrollExecutionResult {
            requests += request
            return pending?.await() ?: result
        }
    }

    private fun settings(action: PoseAction) = NativeSettings(mode = GestureMode.POSE, inverted = true,
        poseActions = PoseActions(enabled = true).withBinding(PoseId.POINTING_UP, action))

    private fun decision(time: Long, event: Boolean = false, held: Boolean = false) = GestureDecision(
        HandMotion(HandAction.IDLE), suppressesContinuous = true,
        customPose = CustomPoseResult(pose = PoseId.POINTING_UP, event = if (event) PoseId.POINTING_UP else null,
            held = if (held) PoseId.POINTING_UP else null, timestampMs = time))

    @Test fun mappedBackAcceptedAndBusyIsConsumedWithoutRetry() = runTest {
        val executor = Executor().apply { pending = CompletableDeferred() }
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.configure(settings = settings(PoseAction.BACK))
        coordinator.setSessionActive(true)
        coordinator.process(decision(now, event = true), now, "target.app", 7); runCurrent()
        now += 100
        coordinator.process(decision(now, event = true), now, "target.app"); runCurrent()
        executor.pending!!.complete(ScrollExecutionResult.ACCEPTED); runCurrent()
        now += 100
        coordinator.process(decision(now), now, "target.app"); runCurrent()
        assertEquals(listOf(ScrollRequest(ScrollAction.BACK, expectedTargetPackage = "target.app", expectedTargetGeneration = 7)), executor.requests)
        assertEquals(ScrollExecutionResult.ACCEPTED, coordinator.state.value.lastResult)
        assertNull(coordinator.state.value.rejectionReason)
    }

    @Test fun holdHasFinalDirectionAndStopsOnInvalidStaleOrDuplicateSamples() = runTest {
        val executor = Executor()
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.configure(inverted = true, settings = settings(PoseAction.CONTINUOUS_NEXT))
        coordinator.setSessionActive(true)
        coordinator.process(decision(now, held = true), now, "target.app"); runCurrent()
        assertEquals(ScrollRequest(ScrollAction.FORWARD, 30.0, 100, "target.app"), executor.requests.single())
        assertEquals(300.0, coordinator.state.value.continuousPixelsPerSecond, 0.0)
        coordinator.process(decision(now, held = true), now, "target.app"); runCurrent()
        assertEquals(0.0, coordinator.state.value.continuousPixelsPerSecond, 0.0)
        now += 500
        coordinator.process(decision(now - 400, held = true), now - 400, "target.app"); runCurrent()
        assertEquals(1, executor.requests.size)
        coordinator.process(decision(now), now, "target.app"); runCurrent()
        assertEquals(0.0, coordinator.state.value.continuousPixelsPerSecond, 0.0)
        coordinator.invalidate()
        coordinator.setSessionActive(false)
        now += 100
        coordinator.process(decision(now, held = true), now, "other.app"); runCurrent()
        assertEquals(1, executor.requests.size)
    }

    @Test fun customDiscreteDirectionsIgnoreReverseAndGenerationDropsQueuedWork() = runTest {
        val executor = Executor()
        var now = 1000L
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.configure(inverted = true, settings = settings(PoseAction.NEXT_ITEM))
        coordinator.setSessionActive(true)
        coordinator.process(decision(now, event = true), now, "target.app"); runCurrent()
        assertEquals(ScrollAction.FORWARD, executor.requests.single().action)
        now += 100
        coordinator.process(decision(now, event = true), now, "target.app")
        coordinator.invalidate(); runCurrent()
        assertEquals(1, executor.requests.size)
    }
}
