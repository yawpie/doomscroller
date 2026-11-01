package dev.autoscroll.nativeapp.scrolling

import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.*
import org.junit.Assert.*
import org.junit.Test

class PoseActionResolverTest {
    @Test fun profilesBindingsAndSnapshotsAreIndependent() {
        val mutable = PoseActions.defaultBindings().toMutableMap()
        val original = PoseActions(true, PoseRecognitionProfile.FINGER_COMBINATIONS, mutable)
        mutable[PoseId.INDEX_ONLY] = PoseAction.BACK
        assertEquals(PoseAction.NONE, original.actionFor(PoseId.INDEX_ONLY))
        val changed = original.withBinding(PoseId.INDEX_ONLY, PoseAction.BACK)
            .withBinding(PoseId.MIDDLE_ONLY, PoseAction.NEXT_ITEM)
            .withBinding(PoseId.INDEX_MIDDLE, PoseAction.CONTINUOUS_PREVIOUS)
        assertEquals(PoseAction.BACK, changed.actionFor(PoseId.INDEX_ONLY))
        assertEquals(PoseAction.NEXT_ITEM, changed.actionFor(PoseId.MIDDLE_ONLY))
        assertEquals(PoseAction.CONTINUOUS_PREVIOUS, changed.actionFor(PoseId.INDEX_MIDDLE))
        assertEquals(PoseAction.NONE, changed.actionFor(PoseId.POINTING_UP))
        assertEquals(PoseAction.SINGLE_TAP, changed.actionFor(PoseId.OPEN_HAND))
        assertEquals(PoseAction.SINGLE_TAP, changed.copy(profile = PoseRecognitionProfile.DIRECTION).actionFor(PoseId.OPEN_HAND))
        assertEquals(PoseTrigger.HELD, PoseAction.CONTINUOUS_PREVIOUS.trigger)
        assertEquals(PoseTrigger.INACTIVE, PoseAction.NONE.trigger)
    }

    @Test fun customDirectionsStayFinalWhileLegacyMovementAndFlickInvertOnce() {
        val settings = NativeSettings(mode = GestureMode.POSE, inverted = true,
            poseActions = PoseActions(enabled = true))
        assertEquals(PoseAction.PREVIOUS_ITEM, PoseActionResolver.custom(PoseId.POINTING_UP, settings))
        assertEquals(PoseAction.NEXT_ITEM, PoseActionResolver.legacy(RecognizedHandPose.POINTING_UP, true))
        assertEquals(PoseAction.PREVIOUS_ITEM, PoseActionResolver.flick(FlickDirection.DOWN, true))
        assertEquals(-1.0, PoseActionResolver.movement(1.0, true), 0.0)
        assertEquals(PoseAction.SINGLE_TAP, PoseActionResolver.legacy(RecognizedHandPose.NEUTRAL, true))
        assertEquals(PoseAction.NONE, PoseActionResolver.custom(PoseId.POINTING_UP, settings.copy(mode = GestureMode.FLICK)))
        assertEquals(PoseAction.NONE, PoseActionResolver.custom(PoseId.POINTING_UP, settings.copy(controlMode = ControlMode.POINTER)))
        assertEquals(PoseAction.NONE, PoseActionResolver.custom(PoseId.POINTING_UP, settings.copy(poseActions = PoseActions())))
        assertEquals(settings.copy(poseActions = PoseActions()), settings.reset(SettingsGroup.POSE_ACTIONS))
    }
}
