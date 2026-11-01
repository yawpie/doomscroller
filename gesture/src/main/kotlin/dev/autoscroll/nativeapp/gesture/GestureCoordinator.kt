package dev.autoscroll.nativeapp.gesture

class GestureCoordinator(
    val motionDetector: ContinuousMotionDetector = ContinuousMotionDetector(),
    val flickDetector: FlickDetector = FlickDetector(),
    poseConfig: HandPoseDetectionConfig = HandPoseDetectionConfig(),
    var mode: GestureMode = GestureMode.HYBRID,
    poseActions: PoseActions = PoseActions(),
) {
    val poseDetector = HandPoseDetector(poseConfig, poseActions)
    private var wasSuppressed = false
    private var previousMode = mode

    fun process(result: HandTrackingResult): GestureDecision {
        if (previousMode != mode) {
            poseDetector.targetChanged()
            motionDetector.reset()
            flickDetector.reset()
            previousMode = mode
        }
        if (result.pose == null) {
            loseTracking()
            return GestureDecision(HandMotion(HandAction.STOP), suppressesContinuous = true,
                poseResult = if (mode == GestureMode.POSE || mode == GestureMode.HYBRID) poseDetector.process(result) else null)
        }
        if (mode == GestureMode.CONTINUOUS) return GestureDecision(motionDetector.process(result))
        if (mode == GestureMode.FLICK) return suppressed(result, flick = flickDetector.process(result))
        if (mode != GestureMode.POSE && flickDetector.ownsGesture) {
            val pose = poseDetector.processPose(result.timestampMs, null)
            return suppressed(result, flick = flickDetector.process(result), poseResult = pose)
        }
        val pose = poseDetector.process(result)
        if (poseDetector.ownsGesture || mode == GestureMode.POSE) {
            flickDetector.reset()
            return suppressed(result, poseResult = pose)
        }
        val flick = flickDetector.process(result)
        if (flickDetector.suppressesContinuous) return suppressed(result, flick = flick, poseResult = pose)
        wasSuppressed = false
        return GestureDecision(motionDetector.process(result), poseResult = pose)
    }

    private fun suppressed(result: HandTrackingResult, flick: FlickEvent? = null, poseResult: PoseResult? = null): GestureDecision {
        if (!wasSuppressed) motionDetector.reset()
        wasSuppressed = true
        return GestureDecision(HandMotion(HandAction.IDLE, smoothedY = result.pose!!.palmY),
            flick = flick, suppressesContinuous = true, poseResult = poseResult)
    }

    fun configure(poseConfig: HandPoseDetectionConfig? = null, mode: GestureMode? = null) {
        poseDetector.targetChanged()
        motionDetector.reset()
        flickDetector.reset()
        if (poseConfig != null && poseConfig != poseDetector.config) poseDetector.configure(poseConfig)
        if (mode != null) this.mode = mode
        previousMode = this.mode
        wasSuppressed = true
    }

    fun loseTracking() {
        motionDetector.reset()
        flickDetector.loseTracking()
        poseDetector.loseTracking()
        wasSuppressed = true
    }

    fun targetChanged() {
        poseDetector.targetChanged()
        motionDetector.reset()
        flickDetector.reset()
        wasSuppressed = true
    }

    fun reset() {
        motionDetector.reset()
        flickDetector.reset()
        poseDetector.reset()
        wasSuppressed = false
    }
}
