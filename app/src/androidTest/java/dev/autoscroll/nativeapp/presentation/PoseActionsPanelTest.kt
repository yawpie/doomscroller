package dev.autoscroll.nativeapp.presentation

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.MainActivity
import dev.autoscroll.nativeapp.GestureApplication
import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.NativeSettings
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.session.SessionDiagnostics
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoseActionsPanelTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test fun fingerBindingsRemainIndependentAndResetOnlyPoseActions() {
        val state = mutableStateOf(NativeSettings(inverted = true, poseActions = PoseActions(enabled = true,
            profile = PoseRecognitionProfile.FINGER_COMBINATIONS)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { MaterialTheme {
                Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) {
                    PoseActionsPanel(state.value, { state.value = it }, null)
                }
            } } }
            reveal("Index finger only: No action")
            capture("pose-actions-editor.png")
            click("Index finger only: No action")
            capture("pose-actions-picker.png")
            click("Back")
            click("Middle finger only: No action")
            click("Scroll next once")
            click("Index and middle fingers: No action")
            click("Scroll previous while held")
            instrumentation.runOnMainSync {
                assertEquals(PoseAction.BACK, state.value.poseActions.bindings[PoseId.INDEX_ONLY])
                assertEquals(PoseAction.NEXT_ITEM, state.value.poseActions.bindings[PoseId.MIDDLE_ONLY])
                assertEquals(PoseAction.CONTINUOUS_PREVIOUS, state.value.poseActions.bindings[PoseId.INDEX_MIDDLE])
            }
            click("Index finger only: Back")
            click("Scroll left once")
            click("Middle finger only: Scroll next once")
            click("Scroll right once")
            instrumentation.runOnMainSync {
                assertEquals(PoseAction.SCROLL_LEFT, state.value.poseActions.bindings[PoseId.INDEX_ONLY])
                assertEquals(PoseAction.SCROLL_RIGHT, state.value.poseActions.bindings[PoseId.MIDDLE_ONLY])
                assertEquals(PoseAction.CONTINUOUS_PREVIOUS, state.value.poseActions.bindings[PoseId.INDEX_MIDDLE])
            }
            click("Reset pose actions")
            instrumentation.runOnMainSync {
                assertEquals(PoseActions(), state.value.poseActions)
                assertTrue(state.value.inverted)
            }
        }
    }

    @Test fun poseDraftSurvivesRecreationAndApplySavesMapping() {
        val app = instrumentation.targetContext.applicationContext as GestureApplication
        assumeTrue(app.session.machine.state == GestureSessionState.Stopped && !app.session.serviceRunning && !app.session.practiceOpen)
        val original = app.settings.state.value
        val baseline = original.copy(poseActions = PoseActions())
        instrumentation.runOnMainSync { app.settings.update(baseline) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                click("Settings")
                click("Use custom pose actions")
                click("Finger combinations")
                click("Index and middle fingers: No action")
                click("Scroll right once")
                assertEquals(baseline, app.settings.state.value)
                scenario.recreate()
                click("Index and middle fingers: Scroll right once")
                click("Cancel")
                assertEquals(baseline, app.settings.state.value)
                click("Apply changes")
                assertEquals(PoseAction.SCROLL_RIGHT, app.settings.state.value.poseActions.bindings[PoseId.INDEX_MIDDLE])
                assertEquals(PoseRecognitionProfile.FINGER_COMBINATIONS, app.settings.state.value.poseActions.profile)
                click("Gestures")
                reveal("Custom pose actions · Finger combinations")
                capture("pose-actions-saved-guide.png")
            }
        } finally {
            instrumentation.runOnMainSync { app.settings.update(original) }
        }
    }

    @Test fun dirtyDraftBlocksPracticeUntilDiscarded() {
        val saved = NativeSettings()
        val draft = mutableStateOf(saved.copy(inverted = true))
        val practice = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { MaterialTheme {
                HandScrollScreen(GestureSessionState.Stopped, saved, draft.value, { draft.value = it },
                    SessionDiagnostics(), true, true, true, false, null, true, false, false, "", null,
                    {}, {}, {}, {}, {}, {}, {}, {}, {}, onPractice = { practice.incrementAndGet() })
            } } }
            click("Gestures")
            click("Practice safely")
            assertEquals(0, practice.get())
            click("Go to Settings")
            click("Discard changes")
            click("Gestures")
            click("Practice safely")
            assertEquals(1, practice.get())
        }
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
        for (index in 0 until node.childCount) find(text, node.getChild(index))?.let { return it }
        return null
    }
    private fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null || node.isScrollable) return node
        for (index in 0 until node.childCount) scrollable(node.getChild(index))?.let { return it }
        return null
    }
    private fun reveal(text: String, click: Boolean = false) {
        fun match(): Boolean {
            var node = find(text) ?: return false
            if (!node.isVisibleToUser) return false
            if (!click) return true
            while (!node.isClickable) node = node.parent ?: return false
            return node.refresh() && node.isVisibleToUser && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        waitUntil(text) { root() != null }
        if (match()) return
        for (step in 0 until 32) {
            if (match()) return
            val moved = scrollable(root())?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
            automation.waitForIdle(50, 1000)
            if (!moved) break
        }
        waitUntil(text) {
            if (match()) true
            else {
                scrollable(root())?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                automation.waitForIdle(50, 1000)
                false
            }
        }
    }
    private fun click(text: String) {
        reveal(text, click = true)
        instrumentation.waitForIdleSync()
    }
    private fun waitUntil(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var done = condition()
        while (!done && SystemClock.uptimeMillis() < deadline) { SystemClock.sleep(50); done = condition() }
        if (!done) {
            capture("pose-actions-failure.png")
            val tree = StringBuilder()
            fun visit(node: AccessibilityNodeInfo?, depth: Int) {
                if (node == null) return
                tree.append(" ".repeat(depth)).append(node.className).append(" text=").append(node.text)
                    .append(" description=").append(node.contentDescription).append(" visible=").append(node.isVisibleToUser).append('\n')
                for (index in 0 until node.childCount) visit(node.getChild(index), depth + 1)
            }
            visit(root(), 0)
            instrumentation.targetContext.getExternalFilesDir(null)?.resolve("pose-actions-failure-tree.txt")?.writeText(tree.toString())
        }
        assertTrue("UI condition timed out: $label", done)
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
            "screencap -p /data/local/tmp/$name")).use { it.readBytes() }
    }
}
