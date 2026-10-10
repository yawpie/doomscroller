package dev.autoscroll.nativeapp.practice

import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.MainActivity
import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.presentation.PracticeScreen
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.settings.ControlMode
import dev.autoscroll.nativeapp.settings.NativeSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PointerPracticeScreenTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test fun calibrationUsesScreenButtonAndLocalBoardShowsDragFeedback() {
        val captured = AtomicInteger()
        val sample = PracticePointerSample(canCapture = true,
            feedback = PointerFeedback(phase = PointerPhase.UNCALIBRATED))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { MaterialTheme {
                PracticeScreen(PracticeState(GestureSessionState.Active, PracticeSample(handDetected = true, pointer = sample)),
                    NativeSettings(controlMode = ControlMode.POINTER), true, false, null, null,
                    {}, {}, {}, {}, onCaptureCorner = { captured.incrementAndGet() })
            } } }
            val button = waitFor("Capture upper-left corner", clickable = true)
            assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            assertEquals(1, captured.get())
            capture("pointer-calibration-screen.png")
            assertNotNull(waitFor("Pinch and move to drag"))
            assertNotNull(waitFor("Tap grid · Hold center for 600 ms"))
            assertNotNull(waitFor("Drop"))
            capture("pointer-practice-board.png")
        }
    }

    private fun waitFor(text: String, clickable: Boolean = false): AccessibilityNodeInfo {
        val until = SystemClock.uptimeMillis() + 10_000
        var forward = true
        while (SystemClock.uptimeMillis() < until) {
            val root = automation.rootInActiveWindow
            find(root, text)?.let { node ->
                var target: AccessibilityNodeInfo? = node
                if (clickable) while (target != null && !target.isClickable) target = target.parent
                if (target != null && target.isVisibleToUser && (!clickable || target.isEnabled)) return target
            }
            scrollable(root)?.let { container ->
                val direction = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                if (container.actionList.none { it.id == direction }) forward = !forward
                scroll(container, forward)
            }
            instrumentation.waitForIdleSync()
        }
        error("Missing pointer practice UI: $text")
    }
    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.lines()?.any { it.trim() == text } == true) return node
        for (i in 0 until node.childCount) find(node.getChild(i), text)?.let { return it }
        return null
    }
    private fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) scrollable(node.getChild(i))?.let { return it }
        return null
    }
    private fun scroll(node: AccessibilityNodeInfo, forward: Boolean) {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val start = bounds.top + bounds.height() * if (forward) .75f else .25f
        val end = bounds.top + bounds.height() * if (forward) .25f else .75f
        val down = SystemClock.uptimeMillis()
        for ((action, y) in listOf(MotionEvent.ACTION_DOWN to start, MotionEvent.ACTION_MOVE to end, MotionEvent.ACTION_UP to end)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue("Cannot reveal pointer practice controls", automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
            SystemClock.sleep(if (action == MotionEvent.ACTION_MOVE) 250 else 16)
        }
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        automation.waitForIdle(500, 5000)
        // Gradle uninstalls the test app; keep test-only evidence outside its deleted storage.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
            "screencap -p /data/local/tmp/$name")).use { it.readBytes() }
    }
}
