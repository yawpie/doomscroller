package dev.autoscroll.nativeapp.settings

import dev.autoscroll.nativeapp.camera.TrackingConfig
import dev.autoscroll.nativeapp.gesture.*

data class NativeSettings(
    val mode: GestureMode = GestureMode.HYBRID,
    val inverted: Boolean = false,
    val overlayEnabled: Boolean = true,
    val tracking: TrackingConfig = TrackingConfig(),
    val motion: MotionDetectionConfig = MotionDetectionConfig(),
    val flick: FlickDetectionConfig = FlickDetectionConfig(),
    val pose: HandPoseDetectionConfig = HandPoseDetectionConfig(tapGesturesEnabled = true, rearmMode = HandPoseRearmMode.TIMED),
    val scroll: ScrollConfig = ScrollConfig(),
    val swipeDurationMs: Long = 220,
    val swipeDistance: Double = 0.5,
    val controlMode: ControlMode = ControlMode.SCROLL,
)

enum class ControlMode { SCROLL, POINTER }
internal fun controlModeFromPreference(value: String?) = ControlMode.entries.firstOrNull { it.name == value } ?: ControlMode.SCROLL

enum class SettingsGroup { RECOGNITION, SWIPE, CAMERA, POSES, FLICK, MOVEMENT }

fun NativeSettings.reset(group: SettingsGroup): NativeSettings {
    val defaults = NativeSettings()
    return when (group) {
        SettingsGroup.RECOGNITION -> copy(mode = defaults.mode, controlMode = defaults.controlMode)
        SettingsGroup.SWIPE -> copy(swipeDurationMs = defaults.swipeDurationMs, swipeDistance = defaults.swipeDistance,
            scroll = scroll.copy(flickDurationMs = defaults.scroll.flickDurationMs))
        SettingsGroup.CAMERA -> copy(tracking = defaults.tracking)
        SettingsGroup.POSES -> copy(pose = defaults.pose)
        SettingsGroup.FLICK -> copy(flick = defaults.flick)
        SettingsGroup.MOVEMENT -> copy(motion = defaults.motion,
            scroll = scroll.copy(pixelsPerNormalizedUnit = defaults.scroll.pixelsPerNormalizedUnit))
    }
}
