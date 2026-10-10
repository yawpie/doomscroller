package dev.autoscroll.nativeapp.accessibility

import android.app.UiAutomation
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.graphics.Path
import android.os.*
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.scrolling.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class PointerDispatchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun shortContactIsOneTapAtExplicitCoordinate() = runBlocking {
        withSurface { surface ->
            val adapter = AccessibilityScrollAdapter()
            begin(adapter, surface.start)
            instrumentation.runOnMainSync { adapter.endPointer() }
            awaitTerminal(surface)
            assertContact(surface.events(), surface.start, moving = false)
            assertEquals(0, surface.drops)
        }
    }

    @Test fun directDragDropsObjectAndReleaseDuringBusyMoveIgnoresPendingPoint() = runBlocking {
        withSurface { surface ->
            val adapter = AccessibilityScrollAdapter()
            begin(adapter, surface.start)
            assertEquals(ScrollExecutionResult.BUSY, adapter.execute(ScrollRequest(ScrollAction.SINGLE_TAP)))
            for (fraction in listOf(.25, .5)) {
                val point = interpolate(surface.start, surface.drop, fraction)
                instrumentation.runOnMainSync { adapter.movePointer(sample(point)) }
                awaitPosition(point)
            }
            instrumentation.runOnMainSync {
                adapter.movePointer(sample(surface.drop))
                adapter.movePointer(sample(surface.start))
                adapter.endPointer()
                adapter.movePointer(sample(surface.start))
            }
            awaitTerminal(surface)
            assertTrue("Raw UP arrived, but square was not dropped", surface.dropped.await(3, TimeUnit.SECONDS))
            assertEquals(1, surface.drops)
            assertContact(surface.events(), surface.drop, moving = true)
        }
    }

    @Test fun stationaryHoldTriggersLongPressWithoutContinuationThenDragDropsSameContact() = runBlocking {
        withSurface(requiresHold = true) { surface ->
            val adapter = AccessibilityScrollAdapter()
            begin(adapter, surface.start)
            repeat(20) { instrumentation.runOnMainSync { adapter.movePointer(sample(surface.start)) } }
            assertTrue("Stationary continued contact did not trigger target long press", surface.held.await(3, TimeUnit.SECONDS))
            assertEquals(listOf(MotionEvent.ACTION_DOWN), surface.events().map { it.getInt("action") })
            assertFalse(GestureAccessibilityService.pointerContact.value.dispatching)
            for (fraction in listOf(.25, .5, .75, 1.0)) {
                val point = interpolate(surface.start, surface.drop, fraction)
                instrumentation.runOnMainSync { adapter.movePointer(sample(point)) }
                awaitPosition(point)
            }
            instrumentation.runOnMainSync { adapter.endPointer() }
            awaitTerminal(surface)
            assertTrue("Hold then drag did not drop the square", surface.dropped.await(3, TimeUnit.SECONDS))
            assertContact(surface.events(), surface.drop, moving = true)
            assertEquals(1, surface.drops)
        }
    }

    @Test fun widgetCrossingAndSessionAbortTerminateAtLastSafePoint() = runBlocking {
        withSurface { surface ->
            val allowed = AtomicBoolean(true)
            var widget: Rect? = null
            val adapter = AccessibilityScrollAdapter(overlayBounds = { widget })
            begin(adapter, surface.start, allowed)
            val middle = interpolate(surface.start, surface.drop, .5)
            instrumentation.runOnMainSync {
                widget = Rect(middle.x - 20, middle.y - 20, middle.x + 20, middle.y + 20)
                adapter.movePointer(sample(surface.drop))
                allowed.set(false)
                adapter.endPointer()
            }
            awaitTerminal(surface)
            assertEquals(ScrollExecutionResult.TARGET_BLOCKED, GestureAccessibilityService.pointerContact.value.lastResult)
            assertContact(surface.events(), surface.start, moving = false)
            assertEquals(0, surface.drops)
        }
    }

    @Test fun stalePendingMoveIsRejectedBeforeSubmission() = runBlocking {
        withSurface { surface ->
            val allowed = AtomicBoolean(true)
            val adapter = AccessibilityScrollAdapter()
            begin(adapter, surface.start, allowed)
            val first = interpolate(surface.start, surface.drop, .25)
            instrumentation.runOnMainSync {
                adapter.movePointer(sample(first))
                adapter.movePointer(sample(surface.drop).copy(timestampMs = SystemClock.elapsedRealtime() - 1000))
            }
            awaitTerminal(surface)
            assertEquals(ScrollExecutionResult.REJECTED, GestureAccessibilityService.pointerContact.value.lastResult)
            assertContact(surface.events(), first, moving = true)
            assertEquals(0, surface.drops)
        }
    }

    @Test fun platformRejectsMismatchedContinuationWithCancelAndNoReplacementDown() = runBlocking {
        withSurface { surface ->
            val service = checkNotNull(GestureAccessibilityService.connectedService)
            val began = CountDownLatch(1)
            val cancelled = CountDownLatch(1)
            var accepted = false
            lateinit var stroke: GestureDescription.StrokeDescription
            instrumentation.runOnMainSync {
                stroke = GestureDescription.StrokeDescription(Path().apply {
                    moveTo(surface.start.x.toFloat(), surface.start.y.toFloat())
                }, 0, 1, true)
                accepted = service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(),
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(description: GestureDescription?) { began.countDown() }
                    }, Handler(Looper.getMainLooper()))
            }
            assertTrue(accepted)
            assertTrue("Initial platform stroke did not complete", began.await(3, TimeUnit.SECONDS))
            assertTrue("DOWN was not received by the target", surface.down.await(3, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                val mismatch = stroke.continueStroke(Path().apply {
                    moveTo(surface.start.x + 1f, surface.start.y.toFloat())
                    lineTo(surface.drop.x.toFloat(), surface.drop.y.toFloat())
                }, 0, 80, true)
                accepted = service.dispatchGesture(GestureDescription.Builder().addStroke(mismatch).build(),
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCancelled(description: GestureDescription?) { cancelled.countDown() }
                    }, Handler(Looper.getMainLooper()))
            }
            assertTrue("Client-side rejection did not exercise platform cancellation", accepted)
            assertTrue("Mismatched continuation was not cancelled", cancelled.await(3, TimeUnit.SECONDS))
            assertTrue("Target did not receive cancellation", surface.cancel.await(3, TimeUnit.SECONDS))
            val events = surface.events()
            assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), events.map { it.getInt("action") })
            assertEquals(0, surface.drops)
        }
    }

    private suspend fun begin(adapter: AccessibilityScrollAdapter, point: ScreenPoint, allowed: AtomicBoolean = AtomicBoolean(true)) {
        var rejection: ScrollExecutionResult? = null
        instrumentation.runOnMainSync { rejection = adapter.beginPointer(sample(point), allowed::get) {} }
        assertNull("Begin rejected: $rejection", rejection)
        awaitPosition(point)
    }

    private fun sample(point: ScreenPoint): PointerDispatchSample {
        check(Looper.myLooper() == Looper.getMainLooper())
        @Suppress("DEPRECATION")
        val rotation = context.getSystemService(WindowManager::class.java).defaultDisplay.rotation
        return PointerDispatchSample(point, SystemClock.elapsedRealtime(),
            checkNotNull(GestureAccessibilityService.connectedService).contentBounds(), rotation,
            GestureAccessibilityService.targetState.value.packageName)
    }

    private suspend fun awaitPosition(point: ScreenPoint) {
        withTimeout(5000) { GestureAccessibilityService.pointerContact.first { it.active && !it.dispatching && it.position == point } }
    }

    private suspend fun awaitTerminal(surface: Surface) {
        withTimeout(5000) { GestureAccessibilityService.pointerContact.first { !it.active } }
        assertFalse("Terminal could not be confirmed", GestureAccessibilityService.pointerContact.value.recoveryRequired)
        assertTrue("Target did not receive UP", surface.up.await(3, TimeUnit.SECONDS))
    }

    private fun assertContact(events: List<Bundle>, end: ScreenPoint, moving: Boolean) {
        assertEquals(MotionEvent.ACTION_DOWN, events.first().getInt("action"))
        assertEquals(MotionEvent.ACTION_UP, events.last().getInt("action"))
        assertEquals(1, events.count { it.getInt("action") == MotionEvent.ACTION_DOWN })
        assertEquals(1, events.count { it.getInt("action") == MotionEvent.ACTION_UP })
        assertEquals(0, events.count { it.getInt("action") == MotionEvent.ACTION_CANCEL })
        assertEquals(moving, events.any { it.getInt("action") == MotionEvent.ACTION_MOVE })
        assertEquals(end.x.toFloat(), events.last().getFloat("rawX"), 1f)
        assertEquals(end.y.toFloat(), events.last().getFloat("rawY"), 1f)
        assertTrue(events.all { it.getInt("pointerCount") == 1 && it.getInt("pointerId") == events.first().getInt("pointerId") &&
            it.getLong("downTime") == events.first().getLong("downTime") })
        assertTrue(events.zipWithNext().all { (a, b) -> a.getLong("eventTime") <= b.getLong("eventTime") })
    }

    private fun interpolate(start: ScreenPoint, end: ScreenPoint, fraction: Double) = ScreenPoint(
        (start.x + (end.x - start.x) * fraction).toInt(), (start.y + (end.y - start.y) * fraction).toInt())

    private suspend fun withSurface(requiresHold: Boolean = false, block: suspend (Surface) -> Unit) {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        assumeTrue("Accessibility must be preapproved; this test never changes personal permissions",
            withTimeoutOrNull(30_000) { GestureAccessibilityService.readiness.first { it } } == true)
        assertFalse("A user-owned contact is already active", GestureAccessibilityService.pointerDispatchBusy)
        val surface = Surface()
        val component = ComponentName(instrumentation.context.packageName, SwipeTestActivity::class.java.name)
        instrumentation.context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("receiver", surface.receiver).putExtra("pointer", true).putExtra("requiresHold", requiresHold))
        try {
            assertTrue("External target not ready", surface.ready.await(5, TimeUnit.SECONDS))
            withTimeout(5000) { GestureAccessibilityService.targetReadiness.first { it } }
            block(surface)
        } finally {
            instrumentation.runOnMainSync { GestureAccessibilityService.connectedService?.endPointer() }
            withTimeout(5000) { GestureAccessibilityService.pointerContact.first { !it.active } }
            instrumentation.context.startActivity(Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("finish", true))
            assertTrue("External target did not close", surface.destroyed.await(5, TimeUnit.SECONDS))
        }
    }

    private class Surface {
        val ready = CountDownLatch(1); val down = CountDownLatch(1); val up = CountDownLatch(1); val held = CountDownLatch(1)
        val dropped = CountDownLatch(1); val destroyed = CountDownLatch(1); val cancel = CountDownLatch(1)
        var start = ScreenPoint(0, 0); var drop = ScreenPoint(0, 0)
        @Volatile var drops = 0
        private val recorded = mutableListOf<Bundle>()
        fun events(): List<Bundle> = synchronized(recorded) { recorded.toList() }
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, data: Bundle?) {
                when (resultCode) {
                    SwipeTestActivity.READY -> {
                        start = ScreenPoint(data!!.getInt("objectX"), data.getInt("objectY"))
                        drop = ScreenPoint(data.getInt("dropX"), data.getInt("dropY")); ready.countDown()
                    }
                    SwipeTestActivity.POINTER_EVENT -> {
                        synchronized(recorded) { recorded += Bundle(checkNotNull(data)) }
                        if (data!!.getInt("action") == MotionEvent.ACTION_DOWN) down.countDown()
                        if (data.getInt("action") == MotionEvent.ACTION_UP) up.countDown()
                        if (data.getInt("action") == MotionEvent.ACTION_CANCEL) cancel.countDown()
                    }
                    SwipeTestActivity.LONG_PRESSED -> held.countDown()
                    SwipeTestActivity.DROPPED -> { drops = data!!.getInt("offset"); dropped.countDown() }
                    SwipeTestActivity.DESTROYED -> destroyed.countDown()
                }
            }
        }
    }
}
