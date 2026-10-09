package dev.autoscroll.nativeapp.gesture

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GoldenParityTest(private val id: String, private val fixture: JSONObject) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): Collection<Array<Any>> {
            val file = File(requireNotNull(System.getProperty("parityFixtures")))
            val cases = JSONObject(file.readText()).getJSONArray("cases")
            require(cases.length() >= 24)
            return (0 until cases.length()).map {
                val fixture = cases.getJSONObject(it)
                arrayOf(fixture.getString("caseId"), fixture)
            }
        }
    }

    @Test fun existingDartOutputMatchesKotlin() {
        val config = fixture.getJSONObject("config")
        val motion = ContinuousMotionDetector(readMotionConfig(config.getJSONObject("motion")))
        val flick = FlickDetector(readFlickConfig(config.getJSONObject("flick")))
        var poseConfig = config.getJSONObject("pose")
        val coordinator = GestureCoordinator(motion, flick, HandPoseDetector(readPoseConfig(poseConfig)),
            GestureMode.valueOf(config.getString("mode")))
        val samples = fixture.getJSONArray("samples")
        val expected = fixture.getJSONArray("expected")
        assertEquals(samples.length(), expected.length())
        for (index in 0 until samples.length()) {
            val sample = samples.getJSONObject(index)
            val change = sample.optJSONObject("configure")
            if (change != null) {
                poseConfig = JSONObject(poseConfig.toString())
                change.keySet().forEach { poseConfig.put(it, change.get(it)) }
                coordinator.configure(HandPoseDetector(readPoseConfig(poseConfig)),
                    GestureMode.valueOf(change.getString("mode")))
            }
            val points = sample.optJSONArray("landmarks")?.let(::readLandmarks)
            val wanted = expected.getJSONObject(index)
            val hand: HandPose? = try { points?.let { HandPose(it) } } catch (_: IllegalArgumentException) {
                assertTrue("$id sample $index must reject invalid landmarks", wanted.getBoolean("rejected"))
                assertEquals(wanted.getString("classification"), HandPoseClassifier(readPoseConfig(poseConfig)).classify(points!!).name)
                continue
            }
            assertFalse("$id sample $index must accept valid landmarks", wanted.getBoolean("rejected"))
            val decision = coordinator.process(HandTrackingResult(sample.getLong("timestampMs"), hand))
            val actual = json(
                "rejected" to false,
                "motion" to json("action" to decision.motion.action.name,
                    "verticalDisplacement" to decision.motion.verticalDisplacement,
                    "verticalVelocity" to decision.motion.verticalVelocity,
                    "smoothedY" to decision.motion.smoothedY, "neutralY" to decision.motion.neutralY),
                "flick" to decision.flick?.let { json("direction" to it.direction.name, "timestampMs" to it.timestampMs,
                    "verticalDisplacement" to it.verticalDisplacement, "verticalVelocity" to it.verticalVelocity,
                    "durationMs" to it.durationMs) },
                "pose" to decision.pose?.let { json("pose" to it.pose.name, "timestampMs" to it.timestampMs) },
                "posePhase" to coordinator.poseDetector.phase.name,
                "currentPose" to coordinator.poseDetector.currentPose.name,
                "confirmationProgress" to coordinator.poseDetector.confirmationProgress,
                "cooldownRemainingMs" to coordinator.poseDetector.cooldownRemainingMs,
                "flickOwnsGesture" to flick.ownsGesture,
                "flickSuppressesContinuous" to flick.suppressesContinuous,
                "suppressesContinuous" to decision.suppressesContinuous,
            )
            for (key in actual.keySet()) compare("$id sample $index $key", wanted.get(key), actual.get(key))
            val actionable = listOf(decision.pose != null, decision.flick != null,
                decision.motion.action == HandAction.MOVE_UP || decision.motion.action == HandAction.MOVE_DOWN).count { it }
            assertTrue("$id sample $index emits at most one action", actionable <= 1)
        }
    }
}

private fun json(vararg entries: Pair<String, Any?>): JSONObject = JSONObject().apply {
    entries.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
}

private fun compare(path: String, wanted: Any, actual: Any) {
    if (wanted is JSONObject && actual is JSONObject) {
        assertEquals("$path keys", wanted.keySet(), actual.keySet())
        wanted.keySet().forEach { compare("$path.$it", wanted.get(it), actual.get(it)) }
    } else if (wanted is Number && actual is Number) {
        if (wanted is Float || wanted is Double || actual is Float || actual is Double)
            assertEquals(path, wanted.toDouble(), actual.toDouble(), 1e-9)
        else assertEquals(path, wanted.toLong(), actual.toLong())
    } else assertEquals(path, wanted, actual)
}

private fun readLandmarks(points: JSONArray): List<HandLandmark> = (0 until points.length()).map {
    val p = points.getJSONObject(it)
    HandLandmark(p.getInt("index"), p.getDouble("x"), p.getDouble("y"), p.getDouble("z"))
}

private fun readMotionConfig(c: JSONObject) = MotionDetectionConfig(
    smoothingAlpha = c.getDouble("smoothingAlpha"), windowSize = c.getInt("windowSize"),
    minimumFrames = c.getInt("minimumFrames"), movementThreshold = c.getDouble("movementThreshold"),
    deadZone = c.getDouble("deadZone"), minimumVelocity = c.getDouble("minimumVelocity"),
    confirmationFrames = c.getInt("confirmationFrames"), directionCooldownMs = c.getLong("directionCooldownMs"),
    maximumSampleGapMs = c.getLong("maximumSampleGapMs"), maximumPositionJump = c.getDouble("maximumPositionJump"),
    stationaryVelocity = c.getDouble("stationaryVelocity"), calibrationDurationMs = c.getLong("calibrationDurationMs"),
    calibrationRadius = c.getDouble("calibrationRadius"),
)

private fun readFlickConfig(c: JSONObject) = FlickDetectionConfig(
    minimumVelocity = c.getDouble("minimumVelocity"), minimumDisplacement = c.getDouble("minimumDisplacement"),
    minimumDurationMs = c.getLong("minimumDurationMs"), maximumDurationMs = c.getLong("maximumDurationMs"),
    cooldownMs = c.getLong("cooldownMs"), directionConsistency = c.getDouble("directionConsistency"),
    maximumSampleGapMs = c.getLong("maximumSampleGapMs"), maximumPositionJump = c.getDouble("maximumPositionJump"),
    rearmVelocity = c.getDouble("rearmVelocity"), rearmDurationMs = c.getLong("rearmDurationMs"),
    rearmRadius = c.getDouble("rearmRadius"),
)

private fun readPoseConfig(c: JSONObject) = HandPoseDetectionConfig(
    enabled = c.getBoolean("enabled"), confirmationDurationMs = c.getLong("confirmationDurationMs"),
    releaseDurationMs = c.getLong("releaseDurationMs"), cooldownMs = c.getLong("cooldownMs"),
    rearmMode = HandPoseRearmMode.valueOf(c.getString("rearmMode")), stablePoseDurationMs = c.getLong("stablePoseDurationMs"),
    minimumObservations = c.getInt("minimumObservations"), minimumIndexAngle = c.getDouble("minimumIndexAngle"),
    maximumCurledAngle = c.getDouble("maximumCurledAngle"), minimumVerticalAlignment = c.getDouble("minimumVerticalAlignment"),
    exitIndexAngle = c.getDouble("exitIndexAngle"), exitCurledAngle = c.getDouble("exitCurledAngle"),
    exitVerticalAlignment = c.getDouble("exitVerticalAlignment"), minimumFingerLengthRatio = c.getDouble("minimumFingerLengthRatio"),
    minimumBoneLengthRatio = c.getDouble("minimumBoneLengthRatio"), maximumSampleGapMs = c.getLong("maximumSampleGapMs"),
)
