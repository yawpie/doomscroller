package dev.autoscroll.nativeapp.practice

import android.Manifest
import android.app.UiAutomation
import android.content.pm.PackageManager
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.GestureApplication
import dev.autoscroll.nativeapp.MainActivity
import dev.autoscroll.nativeapp.session.GestureSessionState
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Camera-only local practice; never grants permissions or dispatches an external action. */
@RunWith(AndroidJUnit4::class)
class PracticeLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as GestureApplication
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test fun cameraOnlyPracticeStopsOnBackgroundAndRecreationWithoutAutoRestart() {
        requireIdleCameraPermission()
        val saved = app.settings.state.value
        val consent = app.settings.accessibilityConsent
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            click("Gestures")
            click("Practice safely")
            assertTrue(app.session.practiceOpen)
            assertVisible("Ocean view")
            click("Start practice")
            waitUntil { textExists("No hand detected") || textExists("Hand detected") || textExists("Practice needs attention") }
            assertFalse("Practice camera failed; inspect camera/privacy state", textExists("Practice needs attention"))
            assertVisible("Ocean view")
            assertEquals(GestureSessionState.Stopped, app.session.state.value)
            assertFalse("Practice created an external session service", app.session.serviceRunning)
            assertEquals(saved, app.settings.state.value)
            assertEquals(consent, app.settings.accessibilityConsent)
            scenario.moveToState(Lifecycle.State.CREATED)
            waitUntil { app.session.cameraReleased.isCompleted }
            scenario.moveToState(Lifecycle.State.RESUMED)
            reveal("Practice stopped", AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            scenario.recreate()
            waitUntil { textExists("Practice stopped") }
            assertEquals(GestureSessionState.Stopped, app.session.state.value)
            click("Back to Gestures")
            assertFalse(app.session.practiceOpen)
            assertEquals(GestureSessionState.Stopped, app.session.state.value)
        }
    }

    @Test fun immediateStopAndExitRejectCameraInitializationCallbacks() {
        requireIdleCameraPermission()
        ActivityScenario.launch(MainActivity::class.java).use {
            click("Gestures")
            click("Practice safely")
            click("Start practice")
            click("Stop practice")
            click("Back to Gestures")
            waitUntil { app.session.cameraReleased.isCompleted }
            click("Practice safely")
            waitUntil { textExists("Practice stopped") }
            assertFalse(textExists("No hand detected"))
            assertFalse(textExists("Hand detected"))
            assertEquals(GestureSessionState.Stopped, app.session.state.value)
            click("Back to Gestures")
        }
    }

    private fun requireIdleCameraPermission() {
        assumeTrue("Camera permission is user owned; this test does not grant it",
            ContextCompat.checkSelfPermission(instrumentation.targetContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        assumeTrue("A user-owned session is already running", app.session.machine.state == GestureSessionState.Stopped &&
            !app.session.serviceRunning && !app.session.practiceOpen)
    }

    private fun click(text: String) {
        reveal(text, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, clickable = true)
        val target = find(automation.rootInActiveWindow, text, clickable = true) ?: error("Missing button $text")
        assertTrue("Cannot click $text", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun textExists(text: String) = find(automation.rootInActiveWindow, text) != null
    private fun assertVisible(text: String) {
        reveal(text, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        val node = find(automation.rootInActiveWindow, text) ?: error("Missing visible sample: $text")
        assertTrue("Sample title was clipped or empty: $text", visible(node))
    }
    private fun reveal(text: String, direction: Int, clickable: Boolean = false) {
        waitUntil {
            val root = automation.rootInActiveWindow
            val target = find(root, text, clickable)
            if (target != null && visible(target)) true
            else {
                target?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                scrollable(root)?.performAction(direction)
                instrumentation.waitForIdleSync()
                false
            }
        }
    }
    private fun visible(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return node.isVisibleToUser && !bounds.isEmpty
    }
    private fun find(node: AccessibilityNodeInfo?, text: String, clickable: Boolean = false): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.lines()?.any { it.trim() == text } == true) {
            if (!clickable) return node
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target != null && target.isEnabled && visible(target)) return target
        }
        for (index in 0 until node.childCount) find(node.getChild(index), text, clickable)?.let { return it }
        return null
    }
    private fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) scrollable(node.getChild(index))?.let { return it }
        return null
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(50)
        assertTrue("Practice condition timed out", condition())
    }
}
