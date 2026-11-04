package dev.autoscroll.nativeapp.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.gesture.HandPoseRearmMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    @Test fun controlModePersistsAndMissingKeyLoadsScrollKeepingOldPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("gesture_settings", android.content.Context.MODE_PRIVATE)
        val savedKey = preferences.getString("controlMode", null)
        val store = SettingsStore(context)
        val original = store.state.value
        try {
            store.update(original.copy(controlMode = ControlMode.POINTER))
            assertEquals(ControlMode.POINTER, SettingsStore(context).state.value.controlMode)
            preferences.edit().remove("controlMode").commit()
            assertEquals(original.copy(controlMode = ControlMode.SCROLL), SettingsStore(context).state.value)
        } finally {
            store.update(original)
            if (savedKey == null) preferences.edit().remove("controlMode").commit()
            else preferences.edit().putString("controlMode", savedKey).commit()
        }
    }
    @Test fun rearmDelayPersistsAndReloadsAsTimedRearm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SettingsStore(context)
        val original = store.state.value
        try {
            assertTrue(original.pose.tapGesturesEnabled)
            assertEquals(HandPoseRearmMode.TIMED, original.pose.rearmMode)
            for (delay in listOf(200L, 1600L, 3000L)) {
                store.update(original.copy(pose = original.pose.copy(cooldownMs = delay)))
                val reloaded = SettingsStore(context).state.value.pose
                assertEquals(delay, reloaded.cooldownMs)
                assertEquals(HandPoseRearmMode.TIMED, reloaded.rearmMode)
            }
            assertThrows(IllegalArgumentException::class.java) {
                store.update(original.copy(pose = original.pose.copy(cooldownMs = 0)))
            }
        } finally {
            store.update(original)
        }
    }
}
