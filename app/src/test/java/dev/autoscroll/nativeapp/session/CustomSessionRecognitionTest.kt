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

    private fun sample(time: Long, pointing: Boolean, rotation: Double = 0.0): HandTrackingResult {
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
        return HandTrackingResult(time, HandPose(points.mapIndexed { i, (x, y) -> HandLandmark(i, .5 + x * kotlin.math.cos(rotation) - y * kotlin.math.sin(rotation),
            .5 + x * kotlin.math.sin(rotation) + y * kotlin.math.cos(rotation), 0.0) }))
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

    @Test fun passivePoseDiagnosticsNeverConfirmConsumeOrRearmActions() {
        val session = SessionRecognition(NativeSettings(mode = GestureMode.POSE,
            poseActions = PoseActions(enabled = true)), null, bounds)
        for (time in 0L..800L step 100) assertEquals(PoseId.POINTING_UP, session.diagnosticPose(sample(time, true)))
        fun process(time: Long) = session.process(sample(time, true), time, bounds, true).scroll!!.customPose!!
        assertNull(process(1000).event)
        assertNull(process(1100).event)
        assertEquals(PoseId.POINTING_UP, process(1200).event)
        session.targetChanged()
        for (time in 1300L..1800L step 100) assertEquals(PoseId.CLOSED_HAND, session.diagnosticPose(sample(time, false)))
        for (time in 1900L..2400L step 100) assertNull(process(time).event)
        assertNull(session.diagnosticPose(HandTrackingResult(2500)))
        assertNull(SessionRecognition(NativeSettings(), null, bounds).diagnosticPose(sample(100, true)))
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

    @Test fun pointingAutoscrollRepeatsFullSwipesWithLegacyTimingOnEveryAxis() = runTest {
        for ((rotation, action) in listOf(0.0 to ScrollAction.BACKWARD, kotlin.math.PI to ScrollAction.FORWARD,
            -kotlin.math.PI / 2 to ScrollAction.LEFT, kotlin.math.PI / 2 to ScrollAction.RIGHT)) {
            for (toggle in listOf(false, true)) {
                val pose = when (action) {
                    ScrollAction.BACKWARD -> PoseId.POINTING_UP
                    ScrollAction.FORWARD -> PoseId.POINTING_DOWN
                    ScrollAction.LEFT -> PoseId.POINTING_LEFT
                    else -> PoseId.POINTING_RIGHT
                }
                val binding = when (action) {
                    ScrollAction.BACKWARD -> PoseAction.CONTINUOUS_PREVIOUS
                    ScrollAction.FORWARD -> PoseAction.CONTINUOUS_NEXT
                    ScrollAction.LEFT -> PoseAction.CONTINUOUS_LEFT
                    else -> PoseAction.CONTINUOUS_RIGHT
                }
                val actions = PoseActions(enabled = true, autoscrollPointing = toggle)
                val settings = NativeSettings(mode = GestureMode.POSE, inverted = true,
                    poseActions = if (toggle) actions else actions.withBinding(pose, binding))
                val requests = mutableListOf<Pair<Long, ScrollRequest>>()
                var now = 1000L
                val executor = object : ScrollExecutor {
                    override val isReady = true
                    override suspend fun execute(request: ScrollRequest): ScrollExecutionResult {
                        requests += now to request
                        return ScrollExecutionResult.COMPLETED
                    }
                }
                val session = SessionRecognition(settings, null, bounds)
                val coordinator = ScrollActionCoordinator(executor, this, { now })
                coordinator.configure(settings = settings); coordinator.setSessionActive(true)
                for (t in 1000L..2600L step 100) {
                    now = t
                    val decision = session.process(sample(t, true, rotation), t, bounds, true).scroll!!
                    assertNull(decision.customPose!!.held)
                    coordinator.process(decision, t, "target.app", 1)
                    runCurrent()
                }
                assertEquals(listOf(1200L, 1900L, 2600L), requests.map { it.first })
                assertTrue(requests.all { it.second == ScrollRequest(action,
                    expectedTargetPackage = "target.app", expectedTargetGeneration = 1) })
                session.targetChanged(); coordinator.invalidate()
                for (t in 2700L..3600L step 100) {
                    now = t
                    coordinator.process(session.process(sample(t, true, rotation), t, bounds, true).scroll!!, t)
                    runCurrent()
                }
                assertEquals(3, requests.size)
            }
        }
    }

    @Test fun repeatedPointingDropsBusyEventsAndStopsOnLossAndInvalidation() = runTest {
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
        for (t in 1000L..2100L step 100) { process(sample(t, true)); runCurrent() }
        assertEquals(1, requests.size)
        process(HandTrackingResult(2200)); runCurrent()
        assertEquals(0.0, coordinator.state.value.continuousPixelsPerSecond, 0.0)
        session.targetChanged(); coordinator.invalidate(); runCurrent()
        pending.complete(ScrollExecutionResult.COMPLETED); runCurrent()
        now = 3000; runCurrent()
        assertEquals(1, requests.size)
        assertEquals(0.0, coordinator.state.value.continuousPixelsPerSecond, 0.0)
    }
}
