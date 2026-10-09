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
)

