package dev.autoscroll.nativeapp.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.gesture.*
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
    @Test fun poseCooldownPersistsAndReloads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SettingsStore(context)
        val original = store.state.value
        try {
            for (delay in listOf(200L, 1600L, 3000L)) {
                store.update(original.copy(pose = original.pose.copy(cooldownMs = delay)))
                val reloaded = SettingsStore(context).state.value.pose
                assertEquals(delay, reloaded.cooldownMs)
            }
            assertThrows(IllegalArgumentException::class.java) {
                store.update(original.copy(pose = original.pose.copy(cooldownMs = 0)))
            }
        } finally {
            store.update(original)
        }
    }
    @Test fun poseActionsRoundTripAndCorruptFieldsDoNotResetOtherSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("gesture_settings", android.content.Context.MODE_PRIVATE)
        val saved = preferences.all.filterKeys { it.startsWith("poseActions.") }
        val original = SettingsStore(context).state.value
        try {
            val actions = PoseActions(true, PoseRecognitionProfile.FINGER_COMBINATIONS)
                .withBinding(PoseId.INDEX_ONLY, PoseAction.BACK)
                .withBinding(PoseId.MIDDLE_ONLY, PoseAction.NEXT_ITEM)
                .withBinding(PoseId.INDEX_MIDDLE, PoseAction.CONTINUOUS_PREVIOUS)
                .withBinding(PoseId.POINTING_LEFT, PoseAction.SCROLL_LEFT)
                .withBinding(PoseId.POINTING_RIGHT, PoseAction.SCROLL_RIGHT)
            val store = SettingsStore(context)
            store.update(original.copy(poseActions = actions))
            assertEquals(actions, SettingsStore(context).state.value.poseActions)
            store.update(original.copy(poseActions = actions.copy(enabled = false)))
            val disabled = SettingsStore(context).state.value.poseActions
            assertFalse(disabled.enabled)
            assertEquals(actions.bindings, disabled.bindings)
            assertEquals(actions.profile, disabled.profile)
            store.update(original.copy(poseActions = disabled.copy(enabled = true)))
            assertEquals(actions, SettingsStore(context).state.value.poseActions)
            preferences.edit().putString("poseActions.binding.INDEX_ONLY.action", "invalid")
                .putString("poseActions.profile", "invalid").commit()
            val reloaded = SettingsStore(context).state.value
            assertEquals(original.mode, reloaded.mode)
            assertEquals(original.pose, reloaded.pose)
            assertEquals(PoseAction.NONE, reloaded.poseActions.bindings[PoseId.INDEX_ONLY])
            assertEquals(PoseAction.NEXT_ITEM, reloaded.poseActions.bindings[PoseId.MIDDLE_ONLY])
            assertEquals(PoseRecognitionProfile.DIRECTION, reloaded.poseActions.profile)
            preferences.edit().putInt("poseActions.version", 99).commit()
            val future = SettingsStore(context)
            assertFalse(future.state.value.poseActions.enabled)
            future.update(future.state.value.copy(inverted = !future.state.value.inverted))
            assertEquals(99, preferences.getInt("poseActions.version", 0))
            assertEquals("invalid", preferences.getString("poseActions.binding.INDEX_ONLY.action", null))
        } finally {
            SettingsStore(context).update(original)
            val editor = preferences.edit()
            preferences.all.keys.filter { it.startsWith("poseActions.") }.forEach { editor.remove(it) }
            saved.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> Unit
            } }
            editor.commit()
        }
    }
}
