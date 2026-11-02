package dev.autoscroll.nativeapp.scrolling

import dev.autoscroll.nativeapp.gesture.*

/** Resolves actions only; recognition and dispatch remain with their existing owners. */
object PoseActionResolver {
    // shortcut: provisional 300 px/s; calibrate touch slop and comfort on physical targets before acceptance.
    fun holdVelocity(action: PoseAction): Double = when (action) {
        PoseAction.CONTINUOUS_NEXT -> 300.0
        PoseAction.CONTINUOUS_PREVIOUS -> -300.0
        else -> 0.0
    }

    fun flick(direction: FlickDirection, inverted: Boolean): PoseAction = direction(direction == FlickDirection.DOWN, inverted)
    fun movement(velocity: Double, inverted: Boolean): Double = if (inverted) -velocity else velocity
    private fun direction(down: Boolean, inverted: Boolean) = if (down != inverted) PoseAction.NEXT_ITEM else PoseAction.PREVIOUS_ITEM
}
