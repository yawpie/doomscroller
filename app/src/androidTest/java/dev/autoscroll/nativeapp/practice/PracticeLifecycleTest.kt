package dev.autoscroll.nativeapp.practice

import android.Manifest
import android.app.UiAutomation
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.GestureApplication
import dev.autoscroll.nativeapp.MainActivity
import dev.autoscroll.nativeapp.camera.TrackingConfig
import dev.autoscroll.nativeapp.mediapipe.HandLandmarkerEngine
import dev.autoscroll.nativeapp.presentation.PracticeScreen
import dev.autoscroll.nativeapp.settings.NativeSettings
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

    @Test fun fixturePreviewShowsLandmarksAndToggleOnlyHidesDrawing() {
        assumeTrue("A user-owned session is already running", app.session.machine.state == GestureSessionState.Stopped &&
            !app.session.serviceRunning && !app.session.practiceOpen)
        val source = instrumentation.context.assets.open("thumb_up.jpg").use { BitmapFactory.decodeStream(it) }
        val preview = source.copy(Bitmap.Config.ARGB_8888, false)
        val engine = HandLandmarkerEngine(instrumentation.targetContext, TrackingConfig())
        try {
            val pose = checkNotNull(engine.detect(source, 100))
            val state = PracticeState(GestureSessionState.Active, PracticeSample(handDetected = true),
                preview = preview, landmarks = pose.landmarks)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                            PracticeScreen(state, NativeSettings(), cameraGranted = true, cameraBlocked = false,
                                cameraExplanation = null, permissionError = null, onCameraPermission = {},
                                onStart = {}, onStop = {}, onExit = {})
                        }
                    }
                }
                waitUntil { described("Detected hand landmarks") != null }
                assertVisible("Hand detected")
                capturePreview("practice-tracking-on.png")
                val cameraBounds = bounds(described("Practice camera preview"))
                val feedbackBounds = bounds(find(automation.rootInActiveWindow, "Live feedback"))
                click("Show hand tracking")
                waitUntil { described("Detected hand landmarks") == null }
                capturePreview("practice-tracking-off.png")
                assertVisible("Ocean view")
                assertEquals(cameraBounds, bounds(described("Practice camera preview")))
                assertEquals(feedbackBounds, bounds(find(automation.rootInActiveWindow, "Live feedback")))
                assertVisible("Hand detected")
                click("Show hand tracking")
                waitUntil { described("Detected hand landmarks") != null }
            }
        } finally {
            engine.close()
            if (!source.isRecycled) source.recycle()
            // Compose may still hold the preview in its render thread after Activity disposal.
        }
    }

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
            waitUntil { !textExists("Waiting for a fresh camera frame") }
            val previewBounds = bounds(described("Practice camera preview"))
            val feedbackBounds = bounds(find(automation.rootInActiveWindow, "Live feedback"))
            assertTrue("Tracking overlay should start visible", checked("Show hand tracking"))
            click("Show hand tracking")
            waitUntil { !checked("Show hand tracking") }
            assertVisible("Ocean view")
            assertEquals("Camera moved with the practice content", previewBounds, bounds(described("Practice camera preview")))
            assertEquals("Live feedback moved with the practice content", feedbackBounds,
                bounds(find(automation.rootInActiveWindow, "Live feedback")))
            assertTrue("Hiding landmarks stopped recognition", textExists("No hand detected") || textExists("Hand detected"))
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
            reveal("Show hand tracking", AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            assertFalse("Recreation reset the overlay toggle", checked("Show hand tracking"))
            assertVisible("Start practice to see the camera")
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
        // Compose can replace virtual nodes after a scroll or camera recomposition.
        automation.waitForIdle(300, 5_000)
        waitUntil {
            val node = find(automation.rootInActiveWindow, text, clickable = true)
            node != null && visible(node) && node.refresh() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        instrumentation.waitForIdleSync()
    }

    private fun textExists(text: String) = find(automation.rootInActiveWindow, text) != null
    private fun capturePreview(name: String) {
        instrumentation.waitForIdleSync()
        automation.waitForIdle(500, 5_000)
        val screenshot = checkNotNull(automation.takeScreenshot())
        try {
            java.io.File(instrumentation.targetContext.cacheDir, name).outputStream().use {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { screenshot.recycle() }
    }
    private fun checked(text: String): Boolean {
        var node = find(automation.rootInActiveWindow, text)
        while (node != null && !node.isCheckable) node = node.parent
        return checkNotNull(node) { "Missing toggle $text" }.isChecked
    }
    private fun described(text: String, node: AccessibilityNodeInfo? = automation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (node == null || node.contentDescription?.toString() == text) return node
        for (index in 0 until node.childCount) described(text, node.getChild(index))?.let { return it }
        return null
    }
    private fun bounds(node: AccessibilityNodeInfo?): Rect = Rect().also {
        checkNotNull(node) { "Missing pinned panel" }.getBoundsInScreen(it)
    }
    private fun assertVisible(text: String) {
        reveal(text, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        val node = find(automation.rootInActiveWindow, text) ?: error("Missing visible sample: $text")
        assertTrue("Sample title was clipped or empty: $text", visible(node))
    }
    private fun reveal(text: String, direction: Int, clickable: Boolean = false) {
        var searchDirection = direction
        waitUntil {
            val root = automation.rootInActiveWindow
            val target = find(root, text, clickable)
            if (target != null && visible(target)) true
            else {
                scrollable(root)?.let { container ->
                    if (target != null) {
                        val viewport = bounds(container)
                        val targetBounds = bounds(target)
                        searchDirection = if (targetBounds.top < viewport.top) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                            else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    }
                    val opposite = if (searchDirection == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    if (container.actionList.none { it.id == searchDirection } && container.actionList.any { it.id == opposite })
                        searchDirection = opposite
                    scrollPractice(container, searchDirection)
                }
                instrumentation.waitForIdleSync()
                false
            }
        }
    }
    private fun scrollPractice(node: AccessibilityNodeInfo, direction: Int) {
        val area = bounds(node)
        val forward = direction == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        val startY = area.top + area.height() * if (forward) .75f else .25f
        val endY = area.top + area.height() * if (forward) .25f else .75f
        val downTime = SystemClock.uptimeMillis()
        for ((action, y) in listOf(MotionEvent.ACTION_DOWN to startY, MotionEvent.ACTION_MOVE to endY, MotionEvent.ACTION_UP to endY)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, area.exactCenterX(), y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue("Cannot scroll practice content", automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
            Thread.sleep(if (action == MotionEvent.ACTION_MOVE) 250 else 16)
        }
    }
    private fun visible(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!node.isVisibleToUser || bounds.isEmpty) return false
        var ancestor = node.parent
        while (ancestor != null) {
            if (ancestor.isScrollable && !bounds(ancestor).contains(bounds)) return false
            ancestor = ancestor.parent
        }
        return true
    }
    private fun find(node: AccessibilityNodeInfo?, text: String, clickable: Boolean = false): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.lines()?.any { it.trim() == text } == true) {
            if (!clickable) return node
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target != null && target.isEnabled) return target
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
        var satisfied = condition()
        while (!satisfied && System.nanoTime() < deadline) {
            Thread.sleep(50)
            satisfied = condition()
        }
        assertTrue("Practice condition timed out", satisfied)
    }
}
