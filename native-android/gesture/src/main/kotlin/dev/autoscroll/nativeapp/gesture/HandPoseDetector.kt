package dev.autoscroll.nativeapp.gesture

import kotlin.math.min

class HandPoseDetector(val config: HandPoseDetectionConfig = HandPoseDetectionConfig()) {
    private val classifier = HandPoseClassifier(config)

    init {
        require(config.confirmationDurationMs > 0 && config.releaseDurationMs > 0 &&
            config.stablePoseDurationMs > 0 && config.cooldownMs >= 0 &&
            config.minimumObservations >= 2 && config.maximumSampleGapMs > 0) {
            "Invalid hand pose durations or observation count."
        }
        require(config.rearmMode != HandPoseRearmMode.TIMED || config.cooldownMs > 0)
        require(!config.tapGesturesEnabled || config.rearmMode == HandPoseRearmMode.TIMED)
    }

    var phase = HandPosePhase.READY
        private set
    var currentPose = RecognizedHandPose.UNKNOWN
        private set
    var lastEvent: PoseEvent? = null
        private set
    private var activePose: RecognizedHandPose? = null
    private var lastTime: Long? = null
    private var candidateStart: Long? = null
    private var observations = 0
    private var rearmStart: Long? = null
    private var rearmObservations = 0
    private var rearmPose: RecognizedHandPose? = null
    private var openHandTapped = false

    val suppressesContinuous: Boolean get() = phase != HandPosePhase.READY ||
        (config.enabled && config.tapGesturesEnabled && currentPose == RecognizedHandPose.NEUTRAL)
    val confirmationProgress: Double get() {
        val start = candidateStart
        val time = lastTime
        if (phase != HandPosePhase.CONFIRMING || start == null || time == null) return 0.0
        return min(((time - start) / config.confirmationDurationMs.toDouble()).coerceIn(0.0, 1.0),
            observations / config.minimumObservations.toDouble())
    }
    val cooldownRemainingMs: Long get() {
        val event = lastEvent
        val time = lastTime
        if (event == null || time == null) return 0
        return (config.cooldownMs - (time - event.timestampMs)).coerceAtLeast(0)
    }

    fun process(result: HandTrackingResult): PoseEvent? {
        if (!config.enabled) return null
        val time = result.timestampMs
        val previous = lastTime
        if (time < 0 || (previous != null && time <= previous)) {
            loseTracking()
            return null
        }
        lastTime = time
        if (config.rearmMode == HandPoseRearmMode.TIMED &&
            (phase == HandPosePhase.LATCHED || phase == HandPosePhase.WAITING_REARM) && cooldownRemainingMs == 0L) {
            phase = HandPosePhase.READY
            activePose = null
            discardEvidence()
        }
        val pose = result.pose
        if (pose == null) {
            loseTracking()
            return null
        }
        if (previous != null && time - previous > config.maximumSampleGapMs) discardEvidence()
        currentPose = classifier.classify(pose.landmarks, activePose)
        // Held open hand must not toggle playback again after the rearm timer expires.
        if (currentPose != RecognizedHandPose.UNKNOWN && currentPose != RecognizedHandPose.NEUTRAL) openHandTapped = false
        when (phase) {
            HandPosePhase.READY -> if (isAction(currentPose)) beginCandidate(time)
            HandPosePhase.CONFIRMING -> {
                if (currentPose != activePose) {
                    discardEvidence()
                    if (isAction(currentPose)) beginCandidate(time)
                    return null
                }
                if (observations < config.minimumObservations) observations++
                if (observations >= config.minimumObservations && time - candidateStart!! >= config.confirmationDurationMs) {
                    val event = PoseEvent(activePose!!, time)
                    lastEvent = event
                    if (event.pose == RecognizedHandPose.NEUTRAL) openHandTapped = true
                    phase = HandPosePhase.LATCHED
                    candidateStart = null
                    observations = 0
                    if (!config.tapGesturesEnabled && config.rearmMode == HandPoseRearmMode.STABLE_POSE) {
                        rearmStart = time
                        rearmObservations = 1
                        rearmPose = currentPose
                    }
                    return event
                }
            }
            HandPosePhase.LATCHED, HandPosePhase.WAITING_REARM -> updateRearm(time)
        }
        return null
    }

    private fun updateRearm(time: Long) {
        if (config.rearmMode == HandPoseRearmMode.TIMED) return
        val openHand = config.rearmMode == HandPoseRearmMode.OPEN_HAND
        val valid = if (openHand) currentPose == RecognizedHandPose.NEUTRAL else currentPose != RecognizedHandPose.UNKNOWN
        if (!valid) {
            discardEvidence()
            return
        }
        phase = HandPosePhase.WAITING_REARM
        if (rearmStart == null || rearmPose != currentPose) {
            rearmStart = time
            rearmObservations = 1
            rearmPose = currentPose
            return
        }
        if (rearmObservations < 2) rearmObservations++
        val duration = if (openHand) config.releaseDurationMs else config.stablePoseDurationMs
        if (rearmObservations >= 2 && time - rearmStart!! >= duration && time - lastEvent!!.timestampMs >= config.cooldownMs) {
            phase = HandPosePhase.READY
            activePose = null
            discardEvidence()
        }
    }

    private fun beginCandidate(time: Long) {
        phase = HandPosePhase.CONFIRMING
        activePose = currentPose
        candidateStart = time
        observations = 1
    }

    private fun isPointing(pose: RecognizedHandPose): Boolean =
        pose == RecognizedHandPose.POINTING_UP || pose == RecognizedHandPose.POINTING_DOWN

    private fun isAction(pose: RecognizedHandPose): Boolean = isPointing(pose) ||
        (config.tapGesturesEnabled && (pose == RecognizedHandPose.THUMBS_UP ||
            (pose == RecognizedHandPose.NEUTRAL && !openHandTapped)))

    private fun discardEvidence() {
        candidateStart = null
        observations = 0
        rearmStart = null
        rearmObservations = 0
        rearmPose = null
        if (phase == HandPosePhase.CONFIRMING) {
            phase = HandPosePhase.READY
            activePose = null
        } else if (phase == HandPosePhase.WAITING_REARM) phase = HandPosePhase.LATCHED
    }

    fun loseTracking() {
        currentPose = RecognizedHandPose.UNKNOWN
        discardEvidence()
    }

    fun reset() {
        phase = HandPosePhase.READY
        currentPose = RecognizedHandPose.UNKNOWN
        activePose = null
        lastTime = null
        candidateStart = null
        observations = 0
        rearmStart = null
        rearmObservations = 0
        rearmPose = null
        lastEvent = null
        openHandTapped = false
    }
}
