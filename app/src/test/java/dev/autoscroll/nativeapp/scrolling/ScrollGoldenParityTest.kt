package dev.autoscroll.nativeapp.scrolling

import dev.autoscroll.nativeapp.gesture.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Gesture module proves the landmark pipeline; this verifies its Dart-golden decisions at the action boundary. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScrollGoldenParityTest {
    @Test fun dartGoldenVelocityAndDirectionMatchNativeActionMapping() = runTest {
        val fixtureFile = File(requireNotNull(System.getProperty("parityFixtures")))
        val fixture = JSONObject(fixtureFile.readText())
        val cases = fixture.getJSONArray("cases")
        var compared = 0
        for (caseIndex in 0 until cases.length()) {
            val case = cases.getJSONObject(caseIndex)
            val configJson = case.getJSONObject("config")
            val scrollJson = configJson.getJSONObject("scroll")
            val config = ScrollConfig(
                pixelsPerNormalizedUnit = scrollJson.getDouble("pixelsPerNormalizedUnit"),
                maximumPixelsPerSecond = scrollJson.getDouble("maximumPixelsPerSecond"),
                staleAfterMs = scrollJson.getLong("staleAfterMs"), deadZone = scrollJson.getDouble("deadZone"),
                smoothingAlpha = scrollJson.getDouble("smoothingAlpha"),
                minimumSensitivity = scrollJson.getDouble("minimumSensitivity"),
                maximumSensitivity = scrollJson.getDouble("maximumSensitivity"),
                flickAction = FlickScrollAction.valueOf(scrollJson.getString("flickAction")),
                flickScrollDistance = scrollJson.getDouble("flickScrollDistance"),
                flickDurationMs = scrollJson.getLong("flickDurationMs"),
            )
            val inverted = configJson.getBoolean("inverted")
            val mapper = ScrollVelocityMapper(config).apply { configure(inverted = inverted) }
            val requests = mutableListOf<ScrollRequest>()
            val executor = object : ScrollExecutor {
                override val isReady = true
                override suspend fun execute(request: ScrollRequest): ScrollExecutionResult {
                    requests += request
                    return ScrollExecutionResult.COMPLETED
                }
            }
            var now = 0L
            val coordinator = ScrollActionCoordinator(executor, this, { now }, config)
            coordinator.configure(inverted = inverted)
            coordinator.setSessionActive(true)
            val samples = case.getJSONArray("samples")
            val expected = case.getJSONArray("expected")
            assertEquals(case.getString("caseId"), samples.length(), expected.length())
            for (sampleIndex in 0 until samples.length()) {
                val expectedSample = expected.getJSONObject(sampleIndex)
                if (expectedSample.getBoolean("rejected")) continue
                val sampleTimestamp = samples.getJSONObject(sampleIndex).getLong("timestampMs")
                now = maxOf(now, sampleTimestamp)
                val motionJson = expectedSample.getJSONObject("motion")
                val motion = HandMotion(HandAction.valueOf(motionJson.getString("action")),
                    verticalDisplacement = motionJson.getDouble("verticalDisplacement"),
                    verticalVelocity = motionJson.getDouble("verticalVelocity"))
                val flick = expectedSample.optJSONObject("flick")?.let {
                    FlickEvent(FlickDirection.valueOf(it.getString("direction")), it.getLong("timestampMs"),
                        it.getDouble("verticalDisplacement"), it.getDouble("verticalVelocity"), it.getLong("durationMs"))
                }
                val pose = expectedSample.optJSONObject("pose")?.let {
                    PoseEvent(RecognizedHandPose.valueOf(it.getString("pose")), it.getLong("timestampMs"))
                }
                val label = "${case.getString("caseId")} sample $sampleIndex"
                assertEquals(label, expectedSample.getDouble("scrollPixelsPerSecond"), mapper.map(motion),
                    fixture.getDouble("floatingPointTolerance"))
                val count = requests.size
                coordinator.process(GestureDecision(motion, flick, pose,
                    expectedSample.getBoolean("suppressesContinuous")), sampleTimestamp)
                runCurrent()
                val expectedAction = if (expectedSample.isNull("scrollAction")) null else expectedSample.getString("scrollAction")
                val actualAction = if (requests.size == count) null else requests.last().action.name
                assertEquals(label, expectedAction, actualAction)
                compared++
            }
            coordinator.setSessionActive(false)
        }
        assertEquals(39, cases.length())
        assertTrue("Expected comprehensive Dart output coverage", compared >= 400)
    }
}
