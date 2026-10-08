package dev.autoscroll.nativeapp.settings

import android.content.Context
import dev.autoscroll.nativeapp.camera.TrackingConfig
import dev.autoscroll.nativeapp.gesture.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NativeSettings(
    val mode: GestureMode = GestureMode.HYBRID,
    val inverted: Boolean = false,
    val overlayEnabled: Boolean = true,
    val tracking: TrackingConfig = TrackingConfig(),
    val motion: MotionDetectionConfig = MotionDetectionConfig(),
    val flick: FlickDetectionConfig = FlickDetectionConfig(),
    val pose: HandPoseDetectionConfig = HandPoseDetectionConfig(tapGesturesEnabled = true),
    val scroll: ScrollConfig = ScrollConfig(),
    val swipeDurationMs: Long = 220,
    val swipeDistance: Double = 0.5,
)

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("gesture_settings", Context.MODE_PRIVATE)
    private fun number(key: String, fallback: Double) =
        preferences.getString(key, null)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: fallback
    private val mutable = MutableStateFlow(runCatching {
        NativeSettings(
            mode = GestureMode.valueOf(preferences.getString("mode", "HYBRID")!!),
            inverted = preferences.getBoolean("inverted", false),
            overlayEnabled = preferences.getBoolean("overlay", true),
            tracking = TrackingConfig(
                inferenceFps = number("fps", 15.0).toInt(),
                maximumInferenceDimension = number("dimension", 320.0).toInt(),
            ),
            motion = MotionDetectionConfig(smoothingAlpha = number("motionAlpha", 0.4),
                movementThreshold = number("motionThreshold", 0.018)),
            flick = FlickDetectionConfig(
                minimumVelocity = number("flickVelocity", 0.4),
                minimumDisplacement = number("flickDisplacement", 0.10),
                cooldownMs = number("flickCooldown", 500.0).toLong(),
                rearmDurationMs = number("flickRearm", 150.0).toLong(),
            ),
            pose = HandPoseDetectionConfig(
                tapGesturesEnabled = true,
                confirmationDurationMs = number("poseConfirm", 180.0).toLong(),
                releaseDurationMs = number("poseRelease", 180.0).toLong(),
                cooldownMs = number("poseCooldown", 450.0).toLong(),
                minimumIndexAngle = number("indexAngle", 160.0),
                maximumCurledAngle = number("curledAngle", 110.0),
                minimumVerticalAlignment = number("alignment", 0.85),
            ),
            scroll = ScrollConfig(pixelsPerNormalizedUnit = number("sensitivity", 1800.0),
                flickDurationMs = number("swipeDuration", 220.0).toLong()),
            swipeDurationMs = number("swipeDuration", 220.0).toLong(),
            swipeDistance = number("swipeDistance", 0.5),
        ).also {
            require(it.swipeDurationMs in 100..1000 && it.swipeDistance in 0.1..0.8)
            FlickDetector(it.flick)
            HandPoseDetector(it.pose)
        }
    }.getOrDefault(NativeSettings()))
    val state = mutable.asStateFlow()
    var accessibilityConsent: Boolean
        get() = preferences.getBoolean("accessibilityConsent", false)
        set(value) { preferences.edit().putBoolean("accessibilityConsent", value).apply() }

    fun update(settings: NativeSettings) {
        require(settings.swipeDurationMs in 100..1000 && settings.swipeDistance in 0.1..0.8)
        mutable.value = settings
        preferences.edit().apply {
            putString("mode", settings.mode.name)
            putBoolean("inverted", settings.inverted)
            putBoolean("overlay", settings.overlayEnabled)
            mapOf(
                "fps" to settings.tracking.inferenceFps.toDouble(),
                "dimension" to settings.tracking.maximumInferenceDimension.toDouble(),
                "motionAlpha" to settings.motion.smoothingAlpha,
                "motionThreshold" to settings.motion.movementThreshold,
                "flickVelocity" to settings.flick.minimumVelocity,
                "flickDisplacement" to settings.flick.minimumDisplacement,
                "flickCooldown" to settings.flick.cooldownMs.toDouble(),
                "flickRearm" to settings.flick.rearmDurationMs.toDouble(),
                "poseConfirm" to settings.pose.confirmationDurationMs.toDouble(),
                "poseRelease" to settings.pose.releaseDurationMs.toDouble(),
                "poseCooldown" to settings.pose.cooldownMs.toDouble(),
                "indexAngle" to settings.pose.minimumIndexAngle,
                "curledAngle" to settings.pose.maximumCurledAngle,
                "alignment" to settings.pose.minimumVerticalAlignment,
                "sensitivity" to settings.scroll.pixelsPerNormalizedUnit,
                "swipeDuration" to settings.swipeDurationMs.toDouble(),
                "swipeDistance" to settings.swipeDistance,
            ).forEach { (key, value) -> putString(key, value.toString()) }
        }.apply()
    }
}
