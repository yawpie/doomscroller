package dev.autoscroll.nativeapp.overlay

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.accessibility.ScreenPoint
import dev.autoscroll.nativeapp.accessibility.screenContentBounds
import dev.autoscroll.nativeapp.accessibility.SwipeTestActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PointerCursorTest {
    @Test fun actualTouchPassesThroughVisibleCursorToDifferentUidTarget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Overlay not preapproved; permissions are never changed", Settings.canDrawOverlays(context))
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val ready = CountDownLatch(1)
        val targetUp = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        val placed = CountDownLatch(1)
        val ups = AtomicInteger()
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                when (resultCode) {
                    SwipeTestActivity.READY -> ready.countDown()
                    SwipeTestActivity.TAP_UP -> { ups.set(resultData?.getInt("offset") ?: 0); targetUp.countDown() }
                    SwipeTestActivity.DESTROYED -> destroyed.countDown()
                }
            }
        }
        val component = ComponentName(instrumentation.context.packageName, SwipeTestActivity::class.java.name)
        instrumentation.context.startActivity(Intent().setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("receiver", receiver).putExtra("taps", true))
        var cursor: PointerCursorController? = null
        try {
            assertTrue("External target did not become ready", ready.await(5, TimeUnit.SECONDS))
            val bounds = screenContentBounds(context)
            val point = ScreenPoint(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2)
            instrumentation.runOnMainSync {
                cursor = PointerCursorController(context, onPosition = { if (it == point) placed.countDown() })
                cursor.move(point)
            }
            assertTrue("Actual cursor center was not confirmed", placed.await(3, TimeUnit.SECONDS))
            val downTime = SystemClock.uptimeMillis()
            fun touch(action: Int, time: Long) {
                val event = MotionEvent.obtain(downTime, time, action, point.x.toFloat(), point.y.toFloat(), 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue("Input injection failed", automation.injectInputEvent(event, true)) }
                finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN, downTime)
            touch(MotionEvent.ACTION_UP, downTime + 50)
            assertTrue("Different-UID target did not receive touch under cursor", targetUp.await(3, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals("Cursor must not add a second click", 1, ups.get())
        } finally {
            instrumentation.runOnMainSync { cursor?.close() }
            instrumentation.context.startActivity(Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("finish", true))
            assertTrue("External target did not close", destroyed.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun preapprovedCursorIsNonTouchableAnchoredAtActualScreenPointAndRemovedIdempotently() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Window inspection requires API29", Build.VERSION.SDK_INT >= 29)
        assumeTrue("Overlay not preapproved; permissions are never changed", Settings.canDrawOverlays(context))
        val bounds = screenContentBounds(context)
        val positions = listOf(ScreenPoint(bounds.left, bounds.top),
            ScreenPoint(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2),
            ScreenPoint(bounds.left + bounds.width - 1, bounds.top + bounds.height - 1))
        var desired = positions.first()
        var placed = CountDownLatch(1)
        lateinit var cursor: PointerCursorController
        var before = 0
        instrumentation.runOnMainSync {
            before = WindowInspector.getGlobalWindowViews().size
            cursor = PointerCursorController(context, onPosition = { if (it == desired) placed.countDown() })
        }
        try {
            for (point in positions) {
                desired = point; placed = CountDownLatch(1)
                instrumentation.runOnMainSync { cursor.move(point); cursor.move(point) }
                assertTrue("Cursor center does not match absolute screen point $point", placed.await(3, TimeUnit.SECONDS))
                instrumentation.runOnMainSync {
                    val view = WindowInspector.getGlobalWindowViews().last()
                    val params = view.layoutParams as WindowManager.LayoutParams
                    assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
                    assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
                    assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
                    assertTrue(params.alpha <= .8f)
                    assertEquals(before + 1, WindowInspector.getGlobalWindowViews().size)
                    cursor.hide()
                    assertEquals(View.GONE, view.visibility)
                }
            }
        } finally {
            instrumentation.runOnMainSync { cursor.close(); cursor.close() }
        }
        instrumentation.runOnMainSync { assertEquals(before, WindowInspector.getGlobalWindowViews().size) }
    }
}
