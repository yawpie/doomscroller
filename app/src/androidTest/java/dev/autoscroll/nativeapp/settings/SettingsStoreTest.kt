package dev.autoscroll.nativeapp.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import dev.autoscroll.nativeapp.gesture.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    @Test fun legacyProfilesMergeBindingsAndVersionTwoPreservesExplicitChoices() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("qa_combined_$name", mode)
        }
        val preferences = context.getSharedPreferences("gesture_settings", Context.MODE_PRIVATE)
        try {
            for (profile in listOf("DIRECTION", "FINGER_COMBINATIONS")) {
                preferences.edit().clear().putInt("poseActions.version", 1)
                    .putBoolean("poseActions.enabled", true).putString("poseActions.profile", profile)
                    .putString("poseActions.binding.POINTING_UP.action", "PREVIOUS_ITEM")
                    .putString("poseActions.binding.INDEX_ONLY.action", "BACK")
                    .putString("poseActions.binding.INDEX_MIDDLE.action", "NONE")
                    .putString("poseActions.binding.MIDDLE_ONLY.action", "SINGLE_TAP").commit()
                val store = SettingsStore(context)
                val migrated = store.state.value.poseActions
                val expectedUp = if (profile == "FINGER_COMBINATIONS") PoseAction.BACK else PoseAction.PREVIOUS_ITEM
                assertEquals(expectedUp, migrated.bindings[PoseId.POINTING_UP])
                assertEquals(PoseAction.CONTINUOUS_NEXT, migrated.bindings[PoseId.INDEX_MIDDLE])
                assertEquals(PoseAction.SINGLE_TAP, migrated.bindings[PoseId.MIDDLE_ONLY])
                assertFalse(migrated.autoscrollPointing)
                store.update(store.state.value.copy(poseActions = migrated.copy(autoscrollPointing = true)
                    .withBinding(PoseId.INDEX_MIDDLE, PoseAction.NONE)))
                assertEquals(2, preferences.getInt("poseActions.version", 0))
                val reloaded = SettingsStore(context).state.value.poseActions
                assertTrue(reloaded.autoscrollPointing)
                assertEquals(expectedUp, reloaded.bindings[PoseId.POINTING_UP])
                assertEquals(PoseAction.NONE, reloaded.bindings[PoseId.INDEX_MIDDLE])
                preferences.edit().putString("poseActions.binding.INDEX_ONLY.action", "DOUBLE_TAP").commit()
                assertEquals(expectedUp, SettingsStore(context).state.value.poseActions.bindings[PoseId.POINTING_UP])
            }
        } finally { target.deleteSharedPreferences("qa_combined_gesture_settings") }
    }

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
    @Test fun poseActionsRoundTripAndCorruptFieldsDoNotResetOtherSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("gesture_settings", android.content.Context.MODE_PRIVATE)
        val saved = preferences.all.filterKeys { it.startsWith("poseActions.") }
        val original = SettingsStore(context).state.value
        try {
            val actions = PoseActions(enabled = true, autoscrollPointing = true)
                .withBinding(PoseId.POINTING_UP, PoseAction.BACK)
                .withBinding(PoseId.MIDDLE_ONLY, PoseAction.NEXT_ITEM)
                .withBinding(PoseId.INDEX_MIDDLE, PoseAction.CONTINUOUS_PREVIOUS)
                .withBinding(PoseId.POINTING_LEFT, PoseAction.SCROLL_LEFT)
                .withBinding(PoseId.POINTING_RIGHT, PoseAction.SCROLL_RIGHT)
            val store = SettingsStore(context)
            store.update(original.copy(poseActions = actions))
            assertEquals(actions, SettingsStore(context).state.value.poseActions)
            preferences.edit().putString("poseActions.binding.POINTING_UP.action", "invalid")
                .putString("poseActions.autoscrollPointing", "invalid").commit()
            val reloaded = SettingsStore(context).state.value
            assertEquals(original.mode, reloaded.mode)
            assertEquals(original.pose, reloaded.pose)
            assertEquals(PoseAction.PREVIOUS_ITEM, reloaded.poseActions.bindings[PoseId.POINTING_UP])
            assertEquals(PoseAction.NEXT_ITEM, reloaded.poseActions.bindings[PoseId.MIDDLE_ONLY])
            assertFalse(reloaded.poseActions.autoscrollPointing)
            preferences.edit().putInt("poseActions.version", 99).commit()
            val future = SettingsStore(context)
            assertFalse(future.state.value.poseActions.enabled)
            future.update(future.state.value.copy(inverted = !future.state.value.inverted))
            assertEquals(99, preferences.getInt("poseActions.version", 0))
            assertEquals("invalid", preferences.getString("poseActions.binding.POINTING_UP.action", null))
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
