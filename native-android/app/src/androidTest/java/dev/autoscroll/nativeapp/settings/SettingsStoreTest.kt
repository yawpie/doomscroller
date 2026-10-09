package dev.autoscroll.nativeapp.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.gesture.HandPoseRearmMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
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
