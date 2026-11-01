package dev.autoscroll.nativeapp.gesture

class GestureCoordinator(
    val motionDetector: ContinuousMotionDetector = ContinuousMotionDetector(),
    val flickDetector: FlickDetector = FlickDetector(),
    var poseDetector: HandPoseDetector = HandPoseDetector(),
    var mode: GestureMode = GestureMode.HYBRID,
    private val poseActions: PoseActions = PoseActions(),
) {
    private var wasSuppressed = false
    private var customDetector = if (poseActions.enabled) CustomPoseDetector(poseActions, poseDetector.config) else null
    private var customMode = mode

    fun process(result: HandTrackingResult): GestureDecision {
        if (customMode != mode) {
            customDetector?.reset()
            customMode = mode
        }
        if (result.pose == null) {
            loseTracking()
            return GestureDecision(HandMotion(HandAction.STOP), suppressesContinuous = true,
                customPose = if (mode == GestureMode.POSE || mode == GestureMode.HYBRID) customDetector?.process(result) else null)
        }
        if (mode == GestureMode.CONTINUOUS) return GestureDecision(motionDetector.process(result))
        if (mode == GestureMode.FLICK) return suppressed(result, flick = flickDetector.process(result))
        customDetector?.let { return processCustom(result, it) }
        if (poseDetector.suppressesContinuous || mode == GestureMode.POSE) {
            val pose = poseDetector.process(result)
            flickDetector.reset()
            return suppressed(result, pose = pose)
        }
        if (flickDetector.ownsGesture) return suppressed(result, flick = flickDetector.process(result))
        val pose = poseDetector.process(result)
        if (poseDetector.suppressesContinuous) {
            flickDetector.reset()
            return suppressed(result, pose = pose)
        }
        val flick = flickDetector.process(result)
        if (flickDetector.suppressesContinuous) return suppressed(result, flick = flick)
        wasSuppressed = false
        return GestureDecision(motionDetector.process(result))
    }

    private fun processCustom(result: HandTrackingResult, detector: CustomPoseDetector): GestureDecision {
        if (mode != GestureMode.POSE && flickDetector.ownsGesture) {
            val custom = detector.processPose(result.timestampMs, null)
            return suppressed(result, flick = flickDetector.process(result), customPose = custom)
        }
        val custom = detector.process(result)
        if (detector.ownsGesture || mode == GestureMode.POSE) {
            flickDetector.reset()
            return suppressed(result, customPose = custom)
        }
        val flick = flickDetector.process(result)
        if (flickDetector.suppressesContinuous) return suppressed(result, flick = flick, customPose = custom)
        wasSuppressed = false
        return GestureDecision(motionDetector.process(result), customPose = custom)
    }

    private fun suppressed(result: HandTrackingResult, pose: PoseEvent? = null, flick: FlickEvent? = null,
        customPose: CustomPoseResult? = null): GestureDecision {
        if (!wasSuppressed) motionDetector.reset()
        wasSuppressed = true
        return GestureDecision(HandMotion(HandAction.IDLE, smoothedY = result.pose!!.palmY),
            flick = flick, pose = pose, suppressesContinuous = true, customPose = customPose)
    }

    fun configure(detector: HandPoseDetector? = null, mode: GestureMode? = null) {
        reset()
        if (detector != null) {
            poseDetector = detector
            if (poseActions.enabled) customDetector = CustomPoseDetector(poseActions, detector.config)
        }
        if (mode != null) this.mode = mode
    }

    fun loseTracking() {
        motionDetector.reset()
        flickDetector.loseTracking()
        poseDetector.loseTracking()
        customDetector?.loseTracking()
        wasSuppressed = true
    }

    fun targetChanged() {
        customDetector?.targetChanged()
        motionDetector.reset()
        flickDetector.reset()
        wasSuppressed = true
    }

    fun reset() {
        motionDetector.reset()
        flickDetector.reset()
        poseDetector.reset()
        customDetector?.reset()
        wasSuppressed = false
    }
}
