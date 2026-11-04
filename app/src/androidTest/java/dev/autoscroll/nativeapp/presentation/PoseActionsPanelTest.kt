package dev.autoscroll.nativeapp.presentation

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.SystemClock
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.MainActivity
import dev.autoscroll.nativeapp.GestureApplication
import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.NativeSettings
import dev.autoscroll.nativeapp.settings.SettingsStore
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.session.SessionDiagnostics
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoseActionsPanelTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private lateinit var originalSettings: NativeSettings

    @Before fun preserveSavedSettings() {
        originalSettings = SettingsStore(instrumentation.targetContext).state.value
    }

    @After fun restoreSavedSettings() {
        instrumentation.runOnMainSync {
            (instrumentation.targetContext.applicationContext as GestureApplication).settings.update(originalSettings)
        }
    }

    @Test fun combinedPoseToggleAndPickerWorkWithLargeTextInBothThemes() {
        for (dark in listOf(false, true)) {
            val state = mutableStateOf(NativeSettings(poseActions = PoseActions(enabled = true)))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                            Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) {
                                PoseActionsPanel(state.value, { state.value = it }, null)
                            }
                        }
                    }
                } }
                click("Autoscroll while pointing")
                assertTrue(state.value.poseActions.autoscrollPointing)
                reveal("Point up / index finger up: Scroll previous while held")
                click("Point up / index finger up: Scroll previous while held")
                click("Scroll right once")
                assertTrue(state.value.poseActions.autoscrollPointing)
                assertEquals(PoseAction.SCROLL_RIGHT, state.value.poseActions.bindings[PoseId.POINTING_UP])
                assertEquals(PoseAction.CONTINUOUS_RIGHT, state.value.poseActions.actionFor(PoseId.POINTING_UP))
                reveal("Point up / index finger up: Scroll right while held")
                capture("combined-pose-large-${if (dark) "dark" else "light"}.png")
                click("Autoscroll while pointing")
                click("Index and middle up: Scroll next while held")
                click("Scroll left while held")
                assertEquals(PoseAction.CONTINUOUS_LEFT, state.value.poseActions.bindings[PoseId.INDEX_MIDDLE])
            }
        }
    }

    @Test fun fingerBindingsRemainIndependentAndResetOnlyPoseActions() {
        val state = mutableStateOf(NativeSettings(inverted = true, poseActions = PoseActions(enabled = true)))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { MaterialTheme {
                Column(androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) {
                    PoseActionsPanel(state.value, { state.value = it }, null)
                }
            } } }
            reveal("Point up / index finger up: Scroll previous once")
            capture("pose-actions-editor.png")
            click("Point up / index finger up: Scroll previous once")
            capture("pose-actions-picker.png")
            click("Back")
            click("Middle finger only: No action")
            click("Scroll next once")
            click("Index and middle up: Scroll next while held")
            click("Scroll previous while held")
            instrumentation.runOnMainSync {
                assertEquals(PoseAction.BACK, state.value.poseActions.bindings[PoseId.POINTING_UP])
                assertEquals(PoseAction.NEXT_ITEM, state.value.poseActions.bindings[PoseId.MIDDLE_ONLY])
                assertEquals(PoseAction.CONTINUOUS_PREVIOUS, state.value.poseActions.bindings[PoseId.INDEX_MIDDLE])
            }
            click("Point up / index finger up: Back")
            click("Scroll left once")
            click("Middle finger only: Scroll next once")
            click("Scroll right once")
            instrumentation.runOnMainSync {
                assertEquals(PoseAction.SCROLL_LEFT, state.value.poseActions.bindings[PoseId.POINTING_UP])
                assertEquals(PoseAction.SCROLL_RIGHT, state.value.poseActions.bindings[PoseId.MIDDLE_ONLY])
                assertEquals(PoseAction.CONTINUOUS_PREVIOUS, state.value.poseActions.bindings[PoseId.INDEX_MIDDLE])
            }
            click("Autoscroll while pointing")
            instrumentation.runOnMainSync {
                assertTrue(state.value.poseActions.autoscrollPointing)
                assertEquals(PoseAction.CONTINUOUS_LEFT, state.value.poseActions.actionFor(PoseId.POINTING_LEFT))
            }
            reveal("Point up / index finger up: Scroll left while held")
            click("Autoscroll while pointing")
            reveal("Point up / index finger up: Scroll left once")
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
                click("Index and middle up: Scroll next while held")
                click("Scroll right once")
                click("Autoscroll while pointing")
                click("Point up / index finger up: Scroll previous while held")
                click("Scroll left once")
                assertEquals(baseline, app.settings.state.value)
                scenario.recreate()
                click("Point up / index finger up: Scroll left while held")
                click("Cancel")
                click("Index and middle up: Scroll right once")
                click("Cancel")
                assertEquals(baseline, app.settings.state.value)
                click("Apply changes")
                assertEquals(PoseAction.SCROLL_RIGHT, app.settings.state.value.poseActions.bindings[PoseId.INDEX_MIDDLE])
                assertTrue(app.settings.state.value.poseActions.autoscrollPointing)
                assertEquals(PoseAction.SCROLL_LEFT, app.settings.state.value.poseActions.bindings[PoseId.POINTING_UP])
                click("Gestures")
                reveal("Custom pose actions · Combined poses")
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
    private fun scrollForward() {
        val viewport = scrollable(root()) ?: return
        val bounds = Rect().also(viewport::getBoundsInScreen)
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        // Page-sized jumps can skip a pose label in a short landscape viewport.
        val x = bounds.centerX()
        val from = bounds.top + bounds.height() * 3 / 4
        val to = bounds.centerY()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
            "input swipe $x $from $x $to 300")).use { it.readBytes() }
        automation.waitForIdle(100, 1000)
    }
    private fun reveal(text: String) {
        waitUntil(text) { root() != null }
        if (find(text)?.isVisibleToUser == true) return
        for (step in 0 until 32) {
            if (find(text)?.isVisibleToUser == true) return
            val moved = scrollable(root())?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
            automation.waitForIdle(50, 1000)
            if (!moved) break
        }
        waitUntil(text) {
            if (find(text)?.isVisibleToUser == true) true
            else {
                scrollForward()
                false
            }
        }
    }
    private fun click(text: String) {
        reveal(text)
        waitUntil(text) {
            var node = find(text)
            if (node?.isVisibleToUser != true) {
                scrollForward()
                return@waitUntil false
            }
            while (node != null && !node.isClickable) node = node.parent
            node?.let { it.refresh() && it.performAction(AccessibilityNodeInfo.ACTION_CLICK) } == true
        }
        instrumentation.waitForIdleSync()
    }
    private fun waitUntil(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
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
