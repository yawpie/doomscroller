package dev.autoscroll.nativeapp.gesture

import kotlin.math.min

class CustomPoseDetector(private val actions: PoseActions, private val config: HandPoseDetectionConfig) {
    private val classifier = CustomPoseClassifier(config)
    private var lastTime: Long? = null
    private var candidate: PoseId? = null
    private var candidateStart = 0L
    private var observations = 0
    private var confirmed = false
    private var lastEventAt: Long? = null
    private val latched = mutableSetOf<PoseId>()
    private var releasePose: PoseId? = null
    private var releaseStart = 0L
    private var releaseObservations = 0
    private var targetFence: PoseId? = null
    private var current: PoseId? = null
    private var lastObserved: PoseId? = null

    init {
        require(config.confirmationDurationMs > 0 && config.releaseDurationMs > 0 &&
            config.minimumObservations >= 2 && config.maximumSampleGapMs > 0 && config.cooldownMs >= 0)
    }

    var ownsGesture: Boolean = false
        private set

    fun process(result: HandTrackingResult): CustomPoseResult {
        val previous = lastTime
        val active = candidate.takeIf { previous != null && result.timestampMs > previous &&
            result.timestampMs - previous <= config.maximumSampleGapMs }
        val pose = result.pose?.let { classifier.classify(it.landmarks, result.uprightAspectRatio, actions.profile, active) }
        return processPose(result.timestampMs, pose)
    }

    internal fun processPose(time: Long, pose: PoseId?): CustomPoseResult {
        val previous = lastTime
        if (time < 0 || (previous != null && time <= previous)) {
            loseTracking()
            return CustomPoseResult(timestampMs = time)
        }
        lastTime = time
        if (previous != null && time - previous > config.maximumSampleGapMs) loseTracking()
        current = pose
        if (pose == null) {
            loseTracking()
            return CustomPoseResult(timestampMs = time)
        }
        lastObserved = pose
        if (releasePose != pose) {
            releasePose = pose
            releaseStart = time
            releaseObservations = 1
        } else if (releaseObservations < config.minimumObservations) releaseObservations++
        if (releaseObservations >= config.minimumObservations && time - releaseStart >= config.releaseDurationMs) {
            latched.removeAll { it != pose }
            if (targetFence != pose) targetFence = null
        }
        val trigger = actions.actionFor(pose).trigger
        ownsGesture = trigger != PoseTrigger.INACTIVE
        if (!ownsGesture || targetFence != null) {
            clearCandidate()
            return CustomPoseResult(pose = pose, timestampMs = time)
        }
        if (candidate != pose) {
            candidate = pose
            candidateStart = time
            observations = 1
            confirmed = false
        } else if (observations < config.minimumObservations) observations++
        val progress = min(((time - candidateStart) / config.confirmationDurationMs.toDouble()).coerceIn(0.0, 1.0),
            observations / config.minimumObservations.toDouble())
        if (observations >= config.minimumObservations && time - candidateStart >= config.confirmationDurationMs) confirmed = true
        var event: PoseId? = null
        if (confirmed && trigger == PoseTrigger.DISCRETE && pose !in latched &&
            (lastEventAt == null || time - lastEventAt!! >= config.cooldownMs)) {
            event = pose
            latched.add(pose)
            lastEventAt = time
        }
        return CustomPoseResult(pose, if (confirmed) HandPosePhase.LATCHED else HandPosePhase.CONFIRMING,
            progress, event, if (confirmed && trigger == PoseTrigger.HELD) pose else null, time)
    }

    fun targetChanged() {
        targetFence = targetFence ?: current ?: candidate ?: lastObserved
        loseTracking()
    }

    fun loseTracking() {
        clearCandidate()
        current = null
        releasePose = null
        releaseObservations = 0
        ownsGesture = false
    }

    private fun clearCandidate() {
        candidate = null
        observations = 0
        confirmed = false
    }

    fun reset() {
        loseTracking()
        lastTime = null
        lastEventAt = null
        latched.clear()
        targetFence = null
        lastObserved = null
    }
}
