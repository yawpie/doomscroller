package dev.autoscroll.nativeapp.gesture

class GestureCoordinator(
    val motionDetector: ContinuousMotionDetector = ContinuousMotionDetector(),
    val flickDetector: FlickDetector = FlickDetector(),
    var poseDetector: HandPoseDetector = HandPoseDetector(),
    var mode: GestureMode = GestureMode.HYBRID,
) {
    private var wasSuppressed = false

    fun process(result: HandTrackingResult): GestureDecision {
        if (result.pose == null) {
            loseTracking()
            return GestureDecision(HandMotion(HandAction.STOP), suppressesContinuous = true)
        }
        if (mode == GestureMode.CONTINUOUS) return GestureDecision(motionDetector.process(result))
        if (mode == GestureMode.FLICK) return suppressed(result, flick = flickDetector.process(result))
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

    private fun suppressed(result: HandTrackingResult, pose: PoseEvent? = null, flick: FlickEvent? = null): GestureDecision {
        if (!wasSuppressed) motionDetector.reset()
        wasSuppressed = true
        return GestureDecision(HandMotion(HandAction.IDLE, smoothedY = result.pose!!.palmY),
            flick = flick, pose = pose, suppressesContinuous = true)
    }

    fun configure(detector: HandPoseDetector? = null, mode: GestureMode? = null) {
        reset()
        if (detector != null) poseDetector = detector
        if (mode != null) this.mode = mode
    }

    fun loseTracking() {
        motionDetector.reset()
        flickDetector.loseTracking()
        poseDetector.loseTracking()
        wasSuppressed = true
    }

    fun reset() {
        motionDetector.reset()
        flickDetector.reset()
        poseDetector.reset()
        wasSuppressed = false
    }
}
