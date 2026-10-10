package dev.autoscroll.nativeapp.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import dev.autoscroll.nativeapp.gesture.GestureMode
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.settings.NativeSettings
import dev.autoscroll.nativeapp.settings.ControlMode
import dev.autoscroll.nativeapp.settings.SettingsGroup
import dev.autoscroll.nativeapp.settings.reset
import org.junit.Assert.*
import org.junit.Test

class UiBehaviorTest {
    @Test fun draftSurvivesActivityOwnerRecreationWithoutSavingEdits() {
        val store = ViewModelStore()
        val factory = ViewModelProvider.NewInstanceFactory()
        val saved = NativeSettings()
        val first = ViewModelProvider(store, factory)[SettingsDraft::class.java]
        first.value = saved.copy(inverted = true)
        val recreated = ViewModelProvider(store, factory)[SettingsDraft::class.java]
        assertSame(first, recreated)
        assertTrue(recreated.value!!.inverted)
        assertFalse(saved.inverted)
        store.clear()
    }

    @Test fun requiredPermissionsBlockStartButSkippedOverlayDoesNot() {
        assertTrue(readyToStart(true, true, true, false, false))
        assertFalse(readyToStart(false, true, true, false, false))
        assertFalse(readyToStart(true, false, true, false, false))
        assertFalse(readyToStart(true, true, false, false, false))
        assertFalse(readyToStart(true, true, true, true, false))
        assertTrue(readyToStart(true, true, true, true, true))
    }

    @Test fun onlyChangedApplyPausesRunningTracking() {
        val saved = NativeSettings()
        val changed = saved.copy(inverted = true)
        for (state in listOf(GestureSessionState.Active, GestureSessionState.Starting)) {
            assertFalse(settingsChangePauses(state, saved, saved))
            assertTrue(settingsChangePauses(state, saved, changed))
        }
        for (state in listOf(GestureSessionState.Stopped, GestureSessionState.Paused, GestureSessionState.Error("camera"))) {
            assertFalse(settingsChangePauses(state, saved, changed))
        }
    }

    @Test fun pointerRequiresCursorPermissionItsOwnDisclosureAndLocalCalibrationEvenWithWidgetOff() {
        assertFalse(readyToStart(true, true, true, false, false, ControlMode.POINTER, true, true))
        assertFalse(readyToStart(true, true, true, false, true, ControlMode.POINTER, false, true))
        assertFalse(readyToStart(true, true, true, false, true, ControlMode.POINTER, true, false))
        assertTrue(readyToStart(true, true, true, false, true, ControlMode.POINTER, true, true))
        assertTrue(readyToStart(true, true, true, false, false, ControlMode.SCROLL, false, false))
    }

    @Test fun groupResetChangesOnlyThatGroupsDraftValues() {
        val saved = NativeSettings(mode = GestureMode.POSE, inverted = true, overlayEnabled = false,
            swipeDurationMs = 800, swipeDistance = 0.7)
        val draft = saved.copy(tracking = saved.tracking.copy(inferenceFps = 8),
            pose = saved.pose.copy(cooldownMs = 2000),
            flick = saved.flick.copy(cooldownMs = 1000),
            motion = saved.motion.copy(smoothingAlpha = 0.7),
            scroll = saved.scroll.copy(pixelsPerNormalizedUnit = 2800.0, flickDurationMs = 800))
        val defaults = NativeSettings()
        assertEquals(draft.copy(mode = defaults.mode), draft.reset(SettingsGroup.RECOGNITION))
        assertEquals(draft.copy(swipeDurationMs = defaults.swipeDurationMs, swipeDistance = defaults.swipeDistance,
            scroll = draft.scroll.copy(flickDurationMs = defaults.scroll.flickDurationMs)), draft.reset(SettingsGroup.SWIPE))
        assertEquals(draft.copy(tracking = defaults.tracking), draft.reset(SettingsGroup.CAMERA))
        assertEquals(draft.copy(pose = defaults.pose), draft.reset(SettingsGroup.POSES))
        assertEquals(draft.copy(flick = defaults.flick), draft.reset(SettingsGroup.FLICK))
        assertEquals(draft.copy(motion = defaults.motion,
            scroll = draft.scroll.copy(pixelsPerNormalizedUnit = defaults.scroll.pixelsPerNormalizedUnit)),
            draft.reset(SettingsGroup.MOVEMENT))
        assertEquals(800L, saved.swipeDurationMs)
    }
}
