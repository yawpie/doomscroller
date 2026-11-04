package dev.autoscroll.nativeapp.gesture

import java.util.Collections

enum class PoseId(val id: String) {
    POINTING_UP("POINTING_UP"), POINTING_DOWN("POINTING_DOWN"),
    POINTING_LEFT("POINTING_LEFT"), POINTING_RIGHT("POINTING_RIGHT"),
    MIDDLE_ONLY("MIDDLE_ONLY"), INDEX_MIDDLE("INDEX_MIDDLE"),
    OPEN_HAND("OPEN_HAND"), CLOSED_HAND("CLOSED_HAND"), THUMBS_UP("THUMBS_UP");

    val isPointing: Boolean get() = this == POINTING_UP || this == POINTING_DOWN || this == POINTING_LEFT || this == POINTING_RIGHT
}

enum class PoseTrigger { INACTIVE, DISCRETE, HELD }
enum class PoseAction(val id: String, val trigger: PoseTrigger) {
    NONE("NONE", PoseTrigger.INACTIVE), NEXT_ITEM("NEXT_ITEM", PoseTrigger.DISCRETE),
    PREVIOUS_ITEM("PREVIOUS_ITEM", PoseTrigger.DISCRETE), SINGLE_TAP("SINGLE_TAP", PoseTrigger.DISCRETE),
    DOUBLE_TAP("DOUBLE_TAP", PoseTrigger.DISCRETE), BACK("BACK", PoseTrigger.DISCRETE),
    CONTINUOUS_NEXT("CONTINUOUS_NEXT", PoseTrigger.HELD), CONTINUOUS_PREVIOUS("CONTINUOUS_PREVIOUS", PoseTrigger.HELD),
    SCROLL_LEFT("SCROLL_LEFT", PoseTrigger.DISCRETE), SCROLL_RIGHT("SCROLL_RIGHT", PoseTrigger.DISCRETE),
    CONTINUOUS_LEFT("CONTINUOUS_LEFT", PoseTrigger.HELD), CONTINUOUS_RIGHT("CONTINUOUS_RIGHT", PoseTrigger.HELD);

    val isHorizontal: Boolean get() = this == SCROLL_LEFT || this == SCROLL_RIGHT || this == CONTINUOUS_LEFT || this == CONTINUOUS_RIGHT
}

class PoseActions(
    val enabled: Boolean = false,
    bindings: Map<PoseId, PoseAction> = defaultBindings(),
    val autoscrollPointing: Boolean = false,
) {
    val bindings: Map<PoseId, PoseAction> = Collections.unmodifiableMap(PoseId.entries.associateWith { bindings[it] ?: defaultBindings().getValue(it) })
    fun actionFor(pose: PoseId): PoseAction {
        val action = bindings.getValue(pose)
        return if (autoscrollPointing && pose.isPointing) when (action) {
            PoseAction.NEXT_ITEM -> PoseAction.CONTINUOUS_NEXT
            PoseAction.PREVIOUS_ITEM -> PoseAction.CONTINUOUS_PREVIOUS
            PoseAction.SCROLL_LEFT -> PoseAction.CONTINUOUS_LEFT
            PoseAction.SCROLL_RIGHT -> PoseAction.CONTINUOUS_RIGHT
            else -> action
        } else action
    }
    fun copy(enabled: Boolean = this.enabled, bindings: Map<PoseId, PoseAction> = this.bindings,
        autoscrollPointing: Boolean = this.autoscrollPointing) = PoseActions(enabled, bindings, autoscrollPointing)
    fun withBinding(pose: PoseId, action: PoseAction) = copy(bindings = bindings + (pose to action))
    override fun equals(other: Any?) = other is PoseActions && enabled == other.enabled && autoscrollPointing == other.autoscrollPointing && bindings == other.bindings
    override fun hashCode() = 31 * (31 * enabled.hashCode() + autoscrollPointing.hashCode()) + bindings.hashCode()

    companion object {
        fun defaultBindings(): Map<PoseId, PoseAction> = PoseId.entries.associateWith {
            when (it) {
                PoseId.POINTING_UP -> PoseAction.PREVIOUS_ITEM
                PoseId.POINTING_DOWN -> PoseAction.NEXT_ITEM
                PoseId.POINTING_LEFT -> PoseAction.SCROLL_LEFT
                PoseId.POINTING_RIGHT -> PoseAction.SCROLL_RIGHT
                PoseId.INDEX_MIDDLE -> PoseAction.CONTINUOUS_NEXT
                PoseId.OPEN_HAND -> PoseAction.SINGLE_TAP
                PoseId.THUMBS_UP -> PoseAction.DOUBLE_TAP
                else -> PoseAction.NONE
            }
        }
    }
}
