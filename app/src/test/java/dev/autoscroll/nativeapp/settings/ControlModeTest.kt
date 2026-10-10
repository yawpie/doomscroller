package dev.autoscroll.nativeapp.settings

import org.junit.Assert.*
import org.junit.Test

class ControlModeTest {
    @Test fun missingOrUnknownPreferenceUsesScrollWithoutResettingSettings() {
        assertEquals(ControlMode.SCROLL, controlModeFromPreference(null))
        assertEquals(ControlMode.SCROLL, controlModeFromPreference("future"))
        assertEquals(ControlMode.POINTER, controlModeFromPreference("POINTER"))
        assertEquals(ControlMode.SCROLL, NativeSettings().controlMode)
        val pointer = NativeSettings(controlMode = ControlMode.POINTER, inverted = true)
        assertEquals(ControlMode.POINTER, pointer.reset(SettingsGroup.CAMERA).controlMode)
        assertEquals(pointer.copy(controlMode = ControlMode.SCROLL), pointer.reset(SettingsGroup.RECOGNITION))
    }
}
