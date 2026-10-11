package dev.autoscroll.nativeapp.practice

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
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
            scenario.onActivity { activity -> activity.setContent { MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
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

    @Test fun focusedBoardShowsEveryTargetAndStopWithoutScrolling() {
        val landscape = InstrumentationRegistry.getArguments().getString("qaLandscape") == "true"
        val stopped = AtomicInteger()
        val sample = PracticePointerSample(area = PointerArea(.1, .1, .9, .9),
            feedback = PointerFeedback(phase = PointerPhase.TRACKING))
        val fixture = mutableStateOf(PracticeState(GestureSessionState.Active, PracticeSample(handDetected = true, pointer = sample)))
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            if (landscape) {
                assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
                val deadline = SystemClock.uptimeMillis() + 5000
                while (instrumentation.targetContext.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                    SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                assertEquals(android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                    instrumentation.targetContext.resources.configuration.orientation)
                instrumentation.waitForIdleSync()
                automation.waitForIdle(500, 5000)
            }
            scenario.onActivity { activity -> activity.setContent { MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                PracticeScreen(fixture.value,
                    NativeSettings(controlMode = ControlMode.POINTER), true, false, null, null,
                    {}, {}, {
                        stopped.incrementAndGet()
                        fixture.value = PracticeState()
                    }, {})
            } } }
            assertTrue(waitFor("Open practice board", clickable = true).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            val deadline = SystemClock.uptimeMillis() + 5000
            while (described("Pointer target 1") == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
            val board = described("Local pointer practice board") ?: error("Missing focused board")
            val boardBounds = Rect().also { board.getBoundsInScreen(it) }
            val targetSize = (48 * instrumentation.targetContext.resources.displayMetrics.density).toInt()
            if (landscape) assertTrue("Landscape QA must use a wide board", boardBounds.width() > boardBounds.height())
            assertTrue("Rows must not overlap", boardBounds.height() * .2 >= targetSize)
            assertTrue("Columns must not overlap", boardBounds.width() * .3 >= targetSize)
            for (description in (1..9).map { "Pointer target $it" } + listOf("Drag object", "Drop zone")) {
                val target = described(description) ?: error("Missing $description")
                val bounds = Rect().also { target.getBoundsInScreen(it) }
                assertTrue("Clipped target $description: $bounds in $boardBounds", boardBounds.contains(bounds))
                assertEquals("Target must stay 48dp", targetSize, bounds.width())
                assertEquals("Target must stay 48dp", targetSize, bounds.height())
            }
            val stop = find(root(), "Stop practice") ?: error("Stop is not reachable")
            assertTrue(stop.isVisibleToUser)
            capture("pointer-focused-board.png")
            var button: AccessibilityNodeInfo? = stop
            while (button != null && !button.isClickable) button = button.parent
            assertTrue(checkNotNull(button).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            assertEquals(1, stopped.get())
            assertNotNull(waitFor("Practice stopped"))
            scenario.onActivity { fixture.value = PracticeState(GestureSessionState.Active,
                PracticeSample(handDetected = true, pointer = sample)) }
            assertNotNull("A new calibration must require explicit board entry", waitFor("Open practice board"))
            assertNull(find(root(), "Back to practice"))
        } } finally { if (landscape) automation.setRotation(UiAutomation.ROTATION_FREEZE_0) }
    }

    private fun waitFor(text: String, clickable: Boolean = false): AccessibilityNodeInfo {
        val until = SystemClock.uptimeMillis() + 10_000
        var forward = true
        while (SystemClock.uptimeMillis() < until) {
            val root = root()
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
        capture("pointer-practice-condition-failure.png")
        error("Missing pointer practice UI: $text")
    }
    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.lines()?.any { it.trim() == text } == true) return node
        for (i in 0 until node.childCount) find(node.getChild(i), text)?.let { return it }
        return null
    }
    private fun root(): AccessibilityNodeInfo? {
        val ui = automation
        if (Build.VERSION.SDK_INT >= 33) ui.clearCache()
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        return ui.rootInActiveWindow ?: ui.windows.firstOrNull { it.isActive || it.isFocused }?.root
    }
    private fun described(text: String, node: AccessibilityNodeInfo? = root()): AccessibilityNodeInfo? {
        if (node == null || node.contentDescription?.toString() == text) return node
        for (i in 0 until node.childCount) described(text, node.getChild(i))?.let { return it }
        return null
    }
    private fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val candidates = (0 until node.childCount).mapNotNull { scrollable(node.getChild(it)) } +
            listOfNotNull(node.takeIf { it.isScrollable })
        return candidates.maxByOrNull { candidate ->
            Rect().also { candidate.getBoundsInScreen(it) }.let { it.width().toLong() * it.height() }
        }
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
