package dev.autoscroll.nativeapp.practice

import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.scrolling.ScrollVelocityMapper
import dev.autoscroll.nativeapp.scrolling.PoseActionResolver
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
    val customPose: PoseId? = null,
    val customPhase: HandPosePhase = HandPosePhase.READY,
    val customConfirmation: Double = 0.0,
    val selectedAction: PoseAction = PoseAction.NONE,
    val inactiveReason: String? = null,
    val holdActivations: Int = 0,
    val localDetailOpen: Boolean = false,
)

/** Recognition changes only this local sample. No screen-action executor exists here. */
internal class PracticeFeedback(private val settings: NativeSettings) {
    val pointer = if (settings.controlMode == ControlMode.POINTER) PointerPracticeFeedback() else null
    private val gestures = GestureCoordinator(ContinuousMotionDetector(settings.motion), FlickDetector(settings.flick),
        HandPoseDetector(settings.pose), settings.mode, settings.poseActions)
    private val mapper = ScrollVelocityMapper(settings.scroll).apply { configure(inverted = settings.inverted) }
    private var lastTime: Long? = null
    private var lastAspect = 1.0
    private var lastHeld: PoseId? = null
    var state = PracticeSample()
        private set

    fun process(result: HandTrackingResult, nowMs: Long = result.timestampMs): PracticeSample {
        lastAspect = result.uprightAspectRatio
        pointer?.let {
            state = state.copy(handDetected = result.handDetected, pointer = it.process(result, nowMs))
            return state
        }
        val previous = lastTime
        val customActive = settings.poseActions.enabled && (settings.mode == GestureMode.POSE || settings.mode == GestureMode.HYBRID)
        if (result.timestampMs < 0 || previous != null && result.timestampMs <= previous) {
            if (customActive) {
                gestures.loseTracking()
                lastHeld = null
                state = state.copy(customPose = null, customPhase = HandPosePhase.READY, customConfirmation = 0.0,
                    selectedAction = PoseAction.NONE, inactiveReason = "Tracking sample is out of order", velocity = 0.0)
            }
            return state
        }
        if (customActive) {
            if (nowMs < result.timestampMs || nowMs - result.timestampMs > settings.scroll.staleAfterMs) {
                gestures.loseTracking()
                lastHeld = null
                state = state.copy(handDetected = false, customPose = null, customPhase = HandPosePhase.READY,
                    customConfirmation = 0.0, selectedAction = PoseAction.NONE,
                    inactiveReason = "Tracking sample is stale", velocity = 0.0)
                return state
            }
            lastTime = result.timestampMs
            return processCustom(result, previous)
        }
        lastTime = result.timestampMs
        val decision = gestures.process(result)
        val pose = decision.pose?.pose
        val resolved = pose?.let { PoseActionResolver.legacy(it, settings.inverted) }
            ?: decision.flick?.let { PoseActionResolver.flick(it.direction, settings.inverted) }
            ?: PoseAction.NONE
        val direction = when (resolved) {
            PoseAction.NEXT_ITEM -> 1.0
            PoseAction.PREVIOUS_ITEM -> -1.0
            else -> null
        }
        val velocity = if (decision.suppressesContinuous) 0.0 else mapper.map(decision.motion)
        val seconds = if (previous == null || result.timestampMs - previous > settings.scroll.staleAfterMs) 0.0
            else (result.timestampMs - previous).coerceAtMost(50) / 1000.0
        val movement = if (direction == null) velocity * seconds / SAMPLE_HEIGHT_PX
            else direction *
                if (decision.flick != null && settings.scroll.flickAction == FlickScrollAction.FAST_SCROLL)
                    settings.scroll.flickScrollDistance / SAMPLE_HEIGHT_PX else 1.0
        val action = when {
            direction != null -> if (movement > 0) "Next item" else "Previous item"
            resolved == PoseAction.SINGLE_TAP -> "Tap video"
            resolved == PoseAction.DOUBLE_TAP -> "Double tap"
            velocity != 0.0 -> if (velocity > 0) "Moving to next item" else "Moving to previous item"
            else -> state.lastAction
        }
        state = state.copy(
            position = (state.position + movement).coerceIn(0.0, 4.0),
            videoPlaying = if (resolved == PoseAction.SINGLE_TAP) !state.videoPlaying else state.videoPlaying,
            doubleTaps = state.doubleTaps + if (resolved == PoseAction.DOUBLE_TAP) 1 else 0,
            actionCount = state.actionCount + if (decision.pose != null || decision.flick != null) 1 else 0,
            lastAction = action, handDetected = result.handDetected, pose = gestures.poseDetector.currentPose,
            phase = gestures.poseDetector.phase, confirmation = gestures.poseDetector.confirmationProgress,
            cooldownMs = gestures.poseDetector.cooldownRemainingMs, velocity = velocity,
        )
        return state
    }

    fun loseTracking(nowMs: Long) = process(HandTrackingResult(nowMs, uprightAspectRatio = lastAspect), nowMs)

    private fun processCustom(result: HandTrackingResult, previous: Long?): PracticeSample {
        val decision = gestures.process(result)
        val custom = decision.customPose
        val selected = custom?.pose?.let { PoseActionResolver.custom(it, settings) } ?: PoseAction.NONE
        val discrete = custom?.event?.let { PoseActionResolver.custom(it, settings) }
            ?: decision.flick?.let { PoseActionResolver.flick(it.direction, settings.inverted) }
            ?: PoseAction.NONE
        val held = custom?.held
        val heldVelocity = if (held == null) 0.0 else PoseActionResolver.holdVelocity(selected)
        val sameHold = held != null && held == lastHeld && previous != null &&
            result.timestampMs - previous <= settings.pose.maximumSampleGapMs
        val seconds = if (sameHold) (result.timestampMs - previous!!) / 1000.0 else 0.0
        val velocity = if (heldVelocity != 0.0) heldVelocity else if (decision.suppressesContinuous) 0.0 else mapper.map(decision.motion)
        val motionSeconds = if (previous == null || result.timestampMs - previous > settings.scroll.staleAfterMs) 0.0
            else (result.timestampMs - previous).coerceAtMost(50) / 1000.0
        val movement = when (discrete) {
            PoseAction.NEXT_ITEM, PoseAction.PREVIOUS_ITEM -> {
                val distance = if (decision.flick != null && settings.scroll.flickAction == FlickScrollAction.FAST_SCROLL)
                    settings.scroll.flickScrollDistance / SAMPLE_HEIGHT_PX else 1.0
                distance * if (discrete == PoseAction.NEXT_ITEM) 1 else -1
            }
            else -> velocity * (if (heldVelocity != 0.0) seconds else motionSeconds) / SAMPLE_HEIGHT_PX
        }
        val activated = held != null && !sameHold
        val action = when (discrete) {
            PoseAction.NEXT_ITEM -> "Next item"
            PoseAction.PREVIOUS_ITEM -> "Previous item"
            PoseAction.SINGLE_TAP -> "Tap video"
            PoseAction.DOUBLE_TAP -> "Double tap"
            PoseAction.BACK -> if (state.localDetailOpen) "Back to sample list" else "Already at sample root"
            else -> if (heldVelocity > 0) "Scrolling to next item" else if (heldVelocity < 0) "Scrolling to previous item"
                else if (velocity > 0) "Moving to next item" else if (velocity < 0) "Moving to previous item" else state.lastAction
        }
        state = state.copy(position = (state.position + movement).coerceIn(0.0, 4.0),
            videoPlaying = if (discrete == PoseAction.SINGLE_TAP) !state.videoPlaying else state.videoPlaying,
            doubleTaps = state.doubleTaps + if (discrete == PoseAction.DOUBLE_TAP) 1 else 0,
            actionCount = state.actionCount + if (discrete != PoseAction.NONE) 1 else 0,
            lastAction = action, handDetected = result.handDetected, customPose = custom?.pose,
            customPhase = custom?.phase ?: HandPosePhase.READY,
            customConfirmation = custom?.confirmationProgress ?: 0.0, selectedAction = selected,
            inactiveReason = when {
                !result.handDetected -> "No hand detected"
                custom?.pose == null -> "Pose not recognized"
                selected == PoseAction.NONE -> "No action assigned to this pose"
                else -> null
            }, velocity = velocity, holdActivations = state.holdActivations + if (activated) 1 else 0,
            localDetailOpen = state.localDetailOpen && discrete != PoseAction.BACK)
        lastHeld = held
        return state
    }

    fun openLocalDetail() { state = state.copy(localDetailOpen = true) }

    fun reset() { gestures.reset(); pointer?.reset(); lastTime = null; lastAspect = 1.0; lastHeld = null; state = PracticeSample() }

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
