package dev.autoscroll.nativeapp.presentation

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.MainActivity
import dev.autoscroll.nativeapp.GestureApplication
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.session.SessionDiagnostics
import dev.autoscroll.nativeapp.settings.ControlMode
import dev.autoscroll.nativeapp.settings.NativeSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PointerHomeScreenTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test fun unconfirmedReleaseExplainsPhysicalRecoveryBeforeNewStart() {
        val settings = NativeSettings(controlMode = ControlMode.POINTER, overlayEnabled = false)
        val recovered = AtomicInteger()
        val starts = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                HandScrollScreen(GestureSessionState.Error("Touch release could not be confirmed"), settings, settings, {},
                    SessionDiagnostics(), true, true, true, false, null, true, false, false, "", null,
                    {}, {}, {}, {}, { starts.incrementAndGet() }, {}, {}, {}, {}, pointerConsent = true, pointerCalibrated = true,
                    pointerRecoveryRequired = true, onPointerRecovery = { recovered.incrementAndGet() })
            } } }
            reveal("I touched the screen · clear recovery")
            assertNotNull("Recovery requires a visible manual touch acknowledgement", find("I touched the screen · clear recovery"))
            reveal("Start tracking")
            var start = find("Start tracking")
            while (start != null && !start.isClickable) start = start.parent
            assertFalse("Recovery must block Start", checkNotNull(start).isEnabled)
            reveal("I touched the screen · clear recovery")
            capture("pointer-recovery-home.png")
            var acknowledge = find("I touched the screen · clear recovery")
            while (acknowledge != null && !acknowledge.isClickable) acknowledge = acknowledge.parent
            assertTrue(checkNotNull(acknowledge).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            assertEquals(1, recovered.get())
            assertEquals("Acknowledging must never start tracking", 0, starts.get())
        }
    }

    @Test fun pointerDraftSurvivesTabsAndRecreationAndApplyDiscardAreExplicit() {
        val app = instrumentation.targetContext.applicationContext as GestureApplication
        val original = app.settings.state.value
        val baseline = original.copy(controlMode = ControlMode.SCROLL, overlayEnabled = false)
        instrumentation.runOnMainSync { app.settings.update(baseline) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                click("Settings")
                click("Pointer · tap, hold and drag")
                assertEquals(baseline, app.settings.state.value)
                click("Home")
                waitUntil { find("Control mode: Scroll") != null }
                assertNotNull(find("Control mode: Scroll"))
                if (InstrumentationRegistry.getArguments().getString("qaLandscape") == "true") {
                    assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
                    waitUntil { instrumentation.targetContext.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
                    automation.waitForIdle(500, 5000)
                }
                scenario.recreate()
                click("Settings")
                reveal("Pointer · tap, hold and drag")
                waitUntil { checked("Pointer · tap, hold and drag") }
                assertTrue(checked("Pointer · tap, hold and drag"))
                assertEquals(baseline, app.settings.state.value)
                click("Discard changes")
                reveal("Scroll")
                waitUntil { checked("Scroll") }
                assertTrue(checked("Scroll"))
                click("Pointer · tap, hold and drag")
                click("Apply changes")
                waitUntil { app.settings.state.value.controlMode == ControlMode.POINTER }
                assertEquals(ControlMode.POINTER, app.settings.state.value.controlMode)
                assertFalse(app.settings.state.value.overlayEnabled)
                click("Home")
                waitUntil { find("Control mode: Pointer") != null }
                assertNotNull(find("Control mode: Pointer"))
                assertEquals(GestureSessionState.Stopped, app.session.state.value)
                reveal("Start tracking")
                assertFalse(checkNotNull(clickable("Start tracking", enabledOnly = false)).isEnabled)
                capture("pointer-home-calibration-required.png")
            }
        } finally {
            instrumentation.runOnMainSync { app.settings.update(original) }
            if (InstrumentationRegistry.getArguments().getString("qaLandscape") == "true") automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        }
    }

    @Test fun missingCursorPermissionBlocksPointerWithFloatingWidgetOff() {
        val settings = NativeSettings(controlMode = ControlMode.POINTER, overlayEnabled = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                HandScrollScreen(GestureSessionState.Stopped, settings, settings, {}, SessionDiagnostics(),
                    true, true, true, false, null, false, false, false, "", null,
                    {}, {}, {}, {}, {}, {}, {}, {}, {}, pointerConsent = true, pointerCalibrated = true)
            } } }
            reveal("Start tracking")
            waitUntil { clickable("Start tracking", enabledOnly = false) != null }
            assertFalse(checkNotNull(clickable("Start tracking", enabledOnly = false)).isEnabled)
            reveal("Allow cursor overlay")
            assertNotNull(find("Allow cursor overlay"))
            assertNull("Cursor permission cannot be skipped in Pointer", find("Skip floating controls"))
            capture("pointer-required-overlay-widget-off.png")
        }
    }

    private fun clickable(text: String, enabledOnly: Boolean = true): AccessibilityNodeInfo? {
        var node = find(text)
        while (node != null && !node.isClickable) node = node.parent
        return node?.takeIf { !enabledOnly || it.isEnabled }
    }
    private fun checked(text: String): Boolean {
        var node = find(text)
        while (node != null && !node.isCheckable) node = node.parent
        return node?.let { it.refresh() && it.isChecked } == true
    }
    private fun click(text: String) {
        reveal(text)
        waitUntil { clickable(text)?.let { it.refresh() && it.performAction(AccessibilityNodeInfo.ACTION_CLICK) } == true }
        instrumentation.waitForIdleSync()
    }
    private fun reveal(text: String) {
        waitUntil { find("Scroll Vision") != null }
        if (find(text)?.isVisibleToUser == true) return
        // Compose omits semantics that are wholly outside this scroll viewport.
        // Start at the top before searching forward for an absent control.
        for (step in 0 until 32) {
            if (find(text)?.isVisibleToUser == true) return
            val moved = scrollable(root())
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
            automation.waitForIdle(50, 1000)
            if (!moved) break
        }
        waitUntil {
            if (find(text)?.isVisibleToUser == true) true
            else {
                scrollable(root())?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                automation.waitForIdle(50, 1000)
                false
            }
        }
    }
    private fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null || node.isScrollable) return node
        for (i in 0 until node.childCount) scrollable(node.getChild(i))?.let { return it }
        return null
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var done = condition()
        while (!done && SystemClock.uptimeMillis() < deadline) { SystemClock.sleep(50); done = condition() }
        if (!done) capture("pointer-home-condition-failure.png")
        assertTrue("Home UI condition timed out", done)
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
            "screencap -p /data/local/tmp/$name")).use { it.readBytes() }
    }

    private fun root(): AccessibilityNodeInfo? {
        val ui = automation
        if (Build.VERSION.SDK_INT >= 33) ui.clearCache()
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        return ui.rootInActiveWindow ?: ui.windows.firstOrNull { it.isActive || it.isFocused }?.root
    }
    private fun find(text: String, node: AccessibilityNodeInfo? = root()): AccessibilityNodeInfo? {
        if (node == null) return null
        val value = node.text?.toString()
        if (value?.lines()?.any { it.trim() == text } == true || value?.replace(Regex("\\s+"), " ")?.trim() == text) return node
        for (i in 0 until node.childCount) find(text, node.getChild(i))?.let { return it }
        return null
    }
}
