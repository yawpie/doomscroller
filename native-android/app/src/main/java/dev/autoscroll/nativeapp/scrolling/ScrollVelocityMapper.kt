package dev.autoscroll.nativeapp.scrolling

import dev.autoscroll.nativeapp.gesture.HandAction
import dev.autoscroll.nativeapp.gesture.HandMotion
import dev.autoscroll.nativeapp.gesture.ScrollConfig
import kotlin.math.abs

class ScrollVelocityMapper(val config: ScrollConfig) {
    var sensitivity: Double = config.pixelsPerNormalizedUnit
        private set
    var inverted: Boolean = false
        private set

    fun map(motion: HandMotion): Double {
        val velocity = motion.verticalVelocity
        if (!velocity.isFinite() || abs(velocity) <= config.deadZone ||
            motion.action != HandAction.MOVE_UP && motion.action != HandAction.MOVE_DOWN
        ) return 0.0
        val speed = (abs(velocity) * sensitivity).coerceIn(0.0, config.maximumPixelsPerSecond)
        return speed * (if (motion.action == HandAction.MOVE_UP) -1 else 1) *
            (if (inverted) -1 else 1)
    }

    fun configure(sensitivity: Double? = null, inverted: Boolean? = null) {
        sensitivity?.let {
            require(it.isFinite())
            this.sensitivity = it.coerceIn(config.minimumSensitivity, config.maximumSensitivity)
        }
        inverted?.let { this.inverted = it }
    }
}
