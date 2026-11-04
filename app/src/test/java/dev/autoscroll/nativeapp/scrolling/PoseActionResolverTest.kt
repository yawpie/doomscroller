package dev.autoscroll.nativeapp.scrolling

import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.*
import org.junit.Assert.*
import org.junit.Test

class PoseActionResolverTest {
    @Test fun pointingAutoscrollConvertsEditedScrollBindingsAndKeepsOtherActions() {
        val saved = PoseActions(enabled = true).withBinding(PoseId.POINTING_UP, PoseAction.NEXT_ITEM)
            .withBinding(PoseId.POINTING_DOWN, PoseAction.PREVIOUS_ITEM)
            .withBinding(PoseId.POINTING_LEFT, PoseAction.SCROLL_RIGHT)
            .withBinding(PoseId.POINTING_RIGHT, PoseAction.SCROLL_LEFT)
            .withBinding(PoseId.MIDDLE_ONLY, PoseAction.SINGLE_TAP)
        assertEquals(PoseAction.CONTINUOUS_NEXT, saved.actionFor(PoseId.INDEX_MIDDLE))
        val auto = saved.copy(autoscrollPointing = true)
        for ((pose, action) in listOf(PoseId.POINTING_UP to PoseAction.CONTINUOUS_NEXT,
            PoseId.POINTING_DOWN to PoseAction.CONTINUOUS_PREVIOUS, PoseId.POINTING_LEFT to PoseAction.CONTINUOUS_RIGHT,
            PoseId.POINTING_RIGHT to PoseAction.CONTINUOUS_LEFT)) {
            for (inverted in listOf(false, true)) assertEquals(action,
                PoseActionResolver.custom(pose, NativeSettings(mode = GestureMode.POSE, inverted = inverted, poseActions = auto)))
            assertEquals(PoseTrigger.HELD, action.trigger)
        }
        assertEquals(PoseAction.SINGLE_TAP, auto.actionFor(PoseId.MIDDLE_ONLY))
        assertEquals(PoseAction.CONTINUOUS_NEXT, auto.actionFor(PoseId.INDEX_MIDDLE))
        assertEquals(saved, auto.copy(autoscrollPointing = false))
        assertEquals(PoseAction.NEXT_ITEM, auto.copy(autoscrollPointing = false).actionFor(PoseId.POINTING_UP))
        for (action in listOf(PoseAction.NONE, PoseAction.BACK, PoseAction.SINGLE_TAP, PoseAction.DOUBLE_TAP, PoseAction.CONTINUOUS_LEFT))
            assertEquals(action, auto.withBinding(PoseId.POINTING_UP, action).actionFor(PoseId.POINTING_UP))
    }

    @Test fun horizontalPointingDefaultsMatchLegacyAndRespectExplicitCustomBindings() {
        for (inverted in listOf(false, true)) {
            val settings = NativeSettings(mode = GestureMode.POSE, inverted = inverted, poseActions = PoseActions(enabled = true))
            assertEquals(PoseAction.SCROLL_LEFT, PoseActionResolver.custom(PoseId.POINTING_LEFT, settings))
            assertEquals(PoseAction.SCROLL_RIGHT, PoseActionResolver.custom(PoseId.POINTING_RIGHT, settings))
            assertEquals(PoseAction.SCROLL_LEFT, PoseActionResolver.legacy(RecognizedHandPose.POINTING_LEFT, inverted))
            assertEquals(PoseAction.SCROLL_RIGHT, PoseActionResolver.legacy(RecognizedHandPose.POINTING_RIGHT, inverted))
            val changed = settings.copy(poseActions = settings.poseActions.withBinding(PoseId.POINTING_LEFT, PoseAction.NONE)
                .withBinding(PoseId.POINTING_RIGHT, PoseAction.BACK))
            assertEquals(PoseAction.NONE, PoseActionResolver.custom(PoseId.POINTING_LEFT, changed))
            assertEquals(PoseAction.BACK, PoseActionResolver.custom(PoseId.POINTING_RIGHT, changed))
        }
    }

    @Test fun everyBindingAcceptsEveryActionIndependentlyAndAllNoneIsValid() {
        val empty = PoseActions(enabled = true, bindings = PoseId.entries.associateWith { PoseAction.NONE })
        for (pose in PoseId.entries) for (action in PoseAction.entries) {
            val changed = empty.withBinding(pose, action)
            val settings = NativeSettings(mode = GestureMode.POSE, poseActions = changed)
            assertEquals(action, PoseActionResolver.custom(pose, settings))
            assertTrue(changed.bindings.filterKeys { it != pose }.values.all { it == PoseAction.NONE })
        }
        assertTrue(empty.bindings.values.all { it == PoseAction.NONE })
        val forward = PoseActionResolver.holdVelocity(PoseAction.CONTINUOUS_NEXT)
        assertTrue(forward > 0.0)
        assertEquals(-forward, PoseActionResolver.holdVelocity(PoseAction.CONTINUOUS_PREVIOUS), 0.0)
        assertEquals(0.0, PoseActionResolver.holdVelocity(PoseAction.BACK), 0.0)
    }

    @Test fun bindingsAndSnapshotsAreIndependent() {
        val mutable = PoseActions.defaultBindings().toMutableMap()
        val original = PoseActions(true, mutable)
        mutable[PoseId.POINTING_UP] = PoseAction.BACK
        assertEquals(PoseAction.PREVIOUS_ITEM, original.actionFor(PoseId.POINTING_UP))
        val changed = original.withBinding(PoseId.POINTING_UP, PoseAction.BACK)
            .withBinding(PoseId.MIDDLE_ONLY, PoseAction.NEXT_ITEM)
            .withBinding(PoseId.INDEX_MIDDLE, PoseAction.CONTINUOUS_PREVIOUS)
        assertEquals(PoseAction.BACK, changed.actionFor(PoseId.POINTING_UP))
        assertEquals(PoseAction.NEXT_ITEM, changed.actionFor(PoseId.MIDDLE_ONLY))
        assertEquals(PoseAction.CONTINUOUS_PREVIOUS, changed.actionFor(PoseId.INDEX_MIDDLE))
        assertEquals(PoseAction.SINGLE_TAP, changed.actionFor(PoseId.OPEN_HAND))
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
