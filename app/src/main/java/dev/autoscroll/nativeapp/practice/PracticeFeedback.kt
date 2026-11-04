package dev.autoscroll.nativeapp.practice

import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.scrolling.ScrollVelocityMapper
import dev.autoscroll.nativeapp.settings.NativeSettings
import dev.autoscroll.nativeapp.settings.ControlMode

internal data class PracticeSample(
    val position: Double = 2.0,
    val videoPlaying: Boolean = true,
    val doubleTaps: Int = 0,
    val actionCount: Int = 0,
    val lastAction: String = "No gesture yet",
    val handDetected: Boolean = false,
    val pose: RecognizedHandPose = RecognizedHandPose.UNKNOWN,
    val phase: HandPosePhase = HandPosePhase.READY,
    val confirmation: Double = 0.0,
    val cooldownMs: Long = 0,
    val velocity: Double = 0.0,
    val pointer: PracticePointerSample? = null,
)

/** Recognition changes only this local sample. No screen-action executor exists here. */
internal class PracticeFeedback(private val settings: NativeSettings) {
    val pointer = if (settings.controlMode == ControlMode.POINTER) PointerPracticeFeedback() else null
    private val gestures = GestureCoordinator(ContinuousMotionDetector(settings.motion), FlickDetector(settings.flick),
        HandPoseDetector(settings.pose), settings.mode)
    private val mapper = ScrollVelocityMapper(settings.scroll).apply { configure(inverted = settings.inverted) }
    private var lastTime: Long? = null
    private var lastAspect = 1.0
    var state = PracticeSample()
        private set

    fun process(result: HandTrackingResult, nowMs: Long = result.timestampMs): PracticeSample {
        lastAspect = result.uprightAspectRatio
        pointer?.let {
            state = state.copy(handDetected = result.handDetected, pointer = it.process(result, nowMs))
            return state
        }
        val previous = lastTime
        if (result.timestampMs < 0 || previous != null && result.timestampMs <= previous) return state
        lastTime = result.timestampMs
        val decision = gestures.process(result)
        val pose = decision.pose?.pose
        val direction = when (pose) {
            RecognizedHandPose.POINTING_DOWN -> true
            RecognizedHandPose.POINTING_UP -> false
            else -> decision.flick?.let { it.direction == FlickDirection.DOWN }
        }
        val velocity = if (decision.suppressesContinuous) 0.0 else mapper.map(decision.motion)
        val seconds = if (previous == null || result.timestampMs - previous > settings.scroll.staleAfterMs) 0.0
            else (result.timestampMs - previous).coerceAtMost(50) / 1000.0
        val movement = if (direction == null) velocity * seconds / SAMPLE_HEIGHT_PX
            else (if (direction != settings.inverted) 1.0 else -1.0) *
                if (decision.flick != null && settings.scroll.flickAction == FlickScrollAction.FAST_SCROLL)
                    settings.scroll.flickScrollDistance / SAMPLE_HEIGHT_PX else 1.0
        val action = when {
            direction != null -> if (movement > 0) "Next item" else "Previous item"
            pose == RecognizedHandPose.NEUTRAL -> "Tap video"
            pose == RecognizedHandPose.THUMBS_UP -> "Double tap"
            velocity != 0.0 -> if (velocity > 0) "Moving to next item" else "Moving to previous item"
            else -> state.lastAction
        }
        state = state.copy(
            position = (state.position + movement).coerceIn(0.0, 4.0),
            videoPlaying = if (pose == RecognizedHandPose.NEUTRAL) !state.videoPlaying else state.videoPlaying,
            doubleTaps = state.doubleTaps + if (pose == RecognizedHandPose.THUMBS_UP) 1 else 0,
            actionCount = state.actionCount + if (decision.pose != null || decision.flick != null) 1 else 0,
            lastAction = action, handDetected = result.handDetected, pose = gestures.poseDetector.currentPose,
            phase = gestures.poseDetector.phase, confirmation = gestures.poseDetector.confirmationProgress,
            cooldownMs = gestures.poseDetector.cooldownRemainingMs, velocity = velocity,
        )
        return state
    }

    fun loseTracking(nowMs: Long) = process(HandTrackingResult(nowMs, uprightAspectRatio = lastAspect), nowMs)

    fun reset() { gestures.reset(); pointer?.reset(); lastTime = null; lastAspect = 1.0; state = PracticeSample() }

    fun capturePointerCorner(nowMs: Long): Boolean {
        val captured = pointer?.capture(nowMs) ?: return false
        state = state.copy(pointer = pointer.state)
        return captured
    }

    fun resetPointerCalibration() {
        pointer?.reset()
        state = state.copy(pointer = pointer?.state)
    }

    // shortcut: local cards use a fixed logical page height; calibrate external feed movement on real targets.
    private companion object { const val SAMPLE_HEIGHT_PX = 600.0 }
}
