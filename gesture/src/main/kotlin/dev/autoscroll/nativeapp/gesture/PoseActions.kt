package dev.autoscroll.nativeapp.gesture

import java.util.Collections

enum class PoseRecognitionProfile(val id: String) { DIRECTION("DIRECTION"), FINGER_COMBINATIONS("FINGER_COMBINATIONS") }

enum class PoseId(val id: String, val profile: PoseRecognitionProfile? = null) {
    POINTING_UP("POINTING_UP", PoseRecognitionProfile.DIRECTION),
    POINTING_DOWN("POINTING_DOWN", PoseRecognitionProfile.DIRECTION),
    POINTING_LEFT("POINTING_LEFT", PoseRecognitionProfile.DIRECTION),
    POINTING_RIGHT("POINTING_RIGHT", PoseRecognitionProfile.DIRECTION),
    INDEX_ONLY("INDEX_ONLY", PoseRecognitionProfile.FINGER_COMBINATIONS),
    MIDDLE_ONLY("MIDDLE_ONLY", PoseRecognitionProfile.FINGER_COMBINATIONS),
    INDEX_MIDDLE("INDEX_MIDDLE", PoseRecognitionProfile.FINGER_COMBINATIONS),
    OPEN_HAND("OPEN_HAND"), CLOSED_HAND("CLOSED_HAND"), THUMBS_UP("THUMBS_UP")
}

enum class PoseTrigger { INACTIVE, DISCRETE, HELD }
enum class PoseAction(val id: String, val trigger: PoseTrigger) {
    NONE("NONE", PoseTrigger.INACTIVE), NEXT_ITEM("NEXT_ITEM", PoseTrigger.DISCRETE),
    PREVIOUS_ITEM("PREVIOUS_ITEM", PoseTrigger.DISCRETE), SINGLE_TAP("SINGLE_TAP", PoseTrigger.DISCRETE),
    DOUBLE_TAP("DOUBLE_TAP", PoseTrigger.DISCRETE), BACK("BACK", PoseTrigger.DISCRETE),
    CONTINUOUS_NEXT("CONTINUOUS_NEXT", PoseTrigger.HELD), CONTINUOUS_PREVIOUS("CONTINUOUS_PREVIOUS", PoseTrigger.HELD),
    SCROLL_LEFT("SCROLL_LEFT", PoseTrigger.DISCRETE), SCROLL_RIGHT("SCROLL_RIGHT", PoseTrigger.DISCRETE)
}

class PoseActions(
    val enabled: Boolean = false,
    val profile: PoseRecognitionProfile = PoseRecognitionProfile.DIRECTION,
    bindings: Map<PoseId, PoseAction> = defaultBindings(),
) {
    val bindings: Map<PoseId, PoseAction> = Collections.unmodifiableMap(PoseId.entries.associateWith { bindings[it] ?: defaultBindings().getValue(it) })
    fun actionFor(pose: PoseId): PoseAction = if (pose.profile == null || pose.profile == profile) bindings.getValue(pose) else PoseAction.NONE
    fun copy(enabled: Boolean = this.enabled, profile: PoseRecognitionProfile = this.profile, bindings: Map<PoseId, PoseAction> = this.bindings) = PoseActions(enabled, profile, bindings)
    fun withBinding(pose: PoseId, action: PoseAction) = copy(bindings = bindings + (pose to action))
    override fun equals(other: Any?) = other is PoseActions && enabled == other.enabled && profile == other.profile && bindings == other.bindings
    override fun hashCode() = 31 * (31 * enabled.hashCode() + profile.hashCode()) + bindings.hashCode()

    companion object {
        fun defaultBindings(): Map<PoseId, PoseAction> = PoseId.entries.associateWith {
            when (it) {
                PoseId.POINTING_UP -> PoseAction.PREVIOUS_ITEM
                PoseId.POINTING_DOWN -> PoseAction.NEXT_ITEM
                PoseId.POINTING_LEFT -> PoseAction.SCROLL_LEFT
                PoseId.POINTING_RIGHT -> PoseAction.SCROLL_RIGHT
                PoseId.OPEN_HAND -> PoseAction.SINGLE_TAP
                PoseId.THUMBS_UP -> PoseAction.DOUBLE_TAP
                else -> PoseAction.NONE
            }
        }
    }
}
