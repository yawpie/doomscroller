package dev.autoscroll.nativeapp.session

import dev.autoscroll.nativeapp.accessibility.SwipeBounds
import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.NativeSettings
import dev.autoscroll.nativeapp.scrolling.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CustomSessionRecognitionTest {
    private val bounds = SwipeBounds(0, 0, 1080, 1920)

    private fun sample(time: Long, pointing: Boolean): HandTrackingResult {
        val points = MutableList(21) { 0.0 to 0.0 }
        points[0] = 0.0 to 0.12; points[1] = -0.11 to 0.09; points[2] = -0.11 to 0.03
        points[3] = -0.04 to 0.03; points[4] = 0.02 to 0.03
        for ((mcp, x, y) in listOf(Triple(5, -0.065, -0.06), Triple(9, -0.02, -0.07),
            Triple(13, 0.03, -0.06), Triple(17, 0.075, -0.04))) {
            val extended = mcp == 5 && pointing
            points[mcp] = x to y; points[mcp + 1] = x to y - 0.085
            points[mcp + 2] = if (extended) x to y - 0.16 else x + 0.05 to y - 0.085
            points[mcp + 3] = if (extended) x to y - 0.22 else x + 0.05 to y - 0.035
        }
        return HandTrackingResult(time, HandPose(points.mapIndexed { i, (x, y) -> HandLandmark(i, .5 + x, .5 + y, 0.0) }))
    }

    @Test fun savedMappingAndTargetChangeRequireReleaseAndFreshConfirmation() {
        val settings = NativeSettings(mode = GestureMode.POSE,
            poseActions = PoseActions(enabled = true).withBinding(PoseId.POINTING_UP, PoseAction.BACK))
        val session = SessionRecognition(settings, null, bounds)
        fun process(time: Long, pointing: Boolean) = session.process(sample(time, pointing), time, bounds, true).scroll!!.customPose!!
        process(1000, true); process(1100, true)
        assertEquals(PoseId.POINTING_UP, process(1200, true).event)
        session.targetChanged()
        for (time in 1300L..1800L step 100) assertNull(process(time, true).event)
        for (time in 1900L..2100L step 100) process(time, false)
        process(2200, true); process(2300, true)
        assertEquals(PoseId.POINTING_UP, process(2400, true).event)
    }

    @Test fun backIsOnceAcrossHoldLossAndTargetChangeUntilRelease() = runTest {
        val settings = NativeSettings(mode = GestureMode.POSE,
            poseActions = PoseActions(enabled = true).withBinding(PoseId.POINTING_UP, PoseAction.BACK))
        val requests = mutableListOf<ScrollRequest>()
        val executor = object : ScrollExecutor {
            override val isReady = true
            override suspend fun execute(request: ScrollRequest): ScrollExecutionResult {
                requests += request
                return ScrollExecutionResult.ACCEPTED
            }
        }
        var now = 1000L
        val session = SessionRecognition(settings, null, bounds)
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.configure(settings = settings); coordinator.setSessionActive(true)
        fun process(frame: HandTrackingResult, generation: Long = 1) {
            now = frame.timestampMs
            coordinator.process(session.process(frame, now, bounds, true).scroll!!, now, "target.app", generation)
        }
        for (t in 1000L..2000L step 100) { process(sample(t, true)); runCurrent() }
        process(HandTrackingResult(2100)); runCurrent()
        for (t in 2200L..2600L step 100) { process(sample(t, true)); runCurrent() }
        session.targetChanged(); coordinator.invalidate()
        for (t in 2700L..3200L step 100) { process(sample(t, true), 2); runCurrent() }
        assertEquals(1, requests.size)
        for (t in 3300L..3500L step 100) { process(sample(t, false), 2); runCurrent() }
        for (t in 3600L..3800L step 100) { process(sample(t, true), 2); runCurrent() }
        assertEquals(listOf(1L, 2L), requests.map { it.expectedTargetGeneration })
        assertTrue(requests.all { it.action == ScrollAction.BACK })
    }

    @Test fun heldScrollDropsBusySamplesAndStopsOnLossAndInvalidation() = runTest {
        val settings = NativeSettings(mode = GestureMode.POSE,
            poseActions = PoseActions(enabled = true).withBinding(PoseId.POINTING_UP, PoseAction.CONTINUOUS_NEXT))
        val requests = mutableListOf<ScrollRequest>()
        val pending = CompletableDeferred<ScrollExecutionResult>()
        val executor = object : ScrollExecutor {
            override val isReady = true
            override suspend fun execute(request: ScrollRequest): ScrollExecutionResult {
                requests += request
                return pending.await()
            }
        }
        var now = 1000L
        val session = SessionRecognition(settings, null, bounds)
        val coordinator = ScrollActionCoordinator(executor, this, { now })
        coordinator.configure(settings = settings); coordinator.setSessionActive(true)
        fun process(frame: HandTrackingResult) {
            now = frame.timestampMs
            coordinator.process(session.process(frame, now, bounds, true).scroll!!, now, "target.app", 1)
        }
        for (t in 1000L..1500L step 100) { process(sample(t, true)); runCurrent() }
        assertEquals(1, requests.size)
        process(HandTrackingResult(1600)); runCurrent()
        assertEquals(0.0, coordinator.state.value.continuousPixelsPerSecond, 0.0)
        session.targetChanged(); coordinator.invalidate(); runCurrent()
        pending.complete(ScrollExecutionResult.COMPLETED); runCurrent()
        now = 3000; runCurrent()
        assertEquals(1, requests.size)
        assertEquals(0.0, coordinator.state.value.continuousPixelsPerSecond, 0.0)
    }
}
