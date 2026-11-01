package dev.autoscroll.nativeapp.scrolling

import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.ControlMode
import dev.autoscroll.nativeapp.settings.NativeSettings

/** Resolves actions only; recognition and dispatch remain with their existing owners. */
object PoseActionResolver {
    fun custom(pose: PoseId, settings: NativeSettings): PoseAction =
        if (settings.poseActions.enabled && settings.controlMode == ControlMode.SCROLL &&
            (settings.mode == GestureMode.POSE || settings.mode == GestureMode.HYBRID))
            settings.poseActions.actionFor(pose) else PoseAction.NONE

    fun legacy(pose: RecognizedHandPose, inverted: Boolean): PoseAction = when (pose) {
        RecognizedHandPose.POINTING_DOWN -> direction(true, inverted)
        RecognizedHandPose.POINTING_UP -> direction(false, inverted)
        RecognizedHandPose.NEUTRAL -> PoseAction.SINGLE_TAP
        RecognizedHandPose.THUMBS_UP -> PoseAction.DOUBLE_TAP
        else -> PoseAction.NONE
    }

    fun flick(direction: FlickDirection, inverted: Boolean): PoseAction = direction(direction == FlickDirection.DOWN, inverted)
    fun movement(velocity: Double, inverted: Boolean): Double = if (inverted) -velocity else velocity
    private fun direction(down: Boolean, inverted: Boolean) = if (down != inverted) PoseAction.NEXT_ITEM else PoseAction.PREVIOUS_ITEM
}
