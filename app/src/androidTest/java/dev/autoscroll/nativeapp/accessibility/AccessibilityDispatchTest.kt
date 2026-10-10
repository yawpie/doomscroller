package dev.autoscroll.nativeapp.accessibility

import android.content.ComponentName
import android.content.Intent
import android.app.UiAutomation
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.scrolling.ScrollExecutionResult
import dev.autoscroll.nativeapp.scrolling.ScrollRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class AccessibilityDispatchTest {
    @Test fun enabledServiceSingleAndDoubleTapAreRecognizedByExternalSurface() = runBlocking {
        InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        // Instrumentation restarts the app; Settings may need to reconnect its enabled service.
        assumeTrue("Enable/reconnect Hand Scroll accessibility in Settings after this test starts",
            withTimeoutOrNull(30_000) { GestureAccessibilityService.readiness.first { it } } == true)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        val single = CountDownLatch(1)
        val double = CountDownLatch(1)
        val threeUps = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                when (resultCode) {
                    SwipeTestActivity.READY -> ready.countDown()
                    SwipeTestActivity.SINGLE_TAPPED -> single.countDown()
                    SwipeTestActivity.DOUBLE_TAPPED -> double.countDown()
                    SwipeTestActivity.TAP_UP -> if (resultData?.getInt("offset") == 3) threeUps.countDown()
                    SwipeTestActivity.DESTROYED -> destroyed.countDown()
                }
            }
        }
        val component = ComponentName(instrumentation.context.packageName, SwipeTestActivity::class.java.name)
        instrumentation.context.startActivity(Intent().setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("receiver", receiver).putExtra("taps", true))
        try {
            assertTrue("Test surface did not become ready", ready.await(5, TimeUnit.SECONDS))
            withTimeout(5000) { GestureAccessibilityService.targetReadiness.first { it } }
            val adapter = AccessibilityScrollAdapter()
            assertEquals(ScrollExecutionResult.COMPLETED, adapter.execute(ScrollRequest(ScrollAction.SINGLE_TAP)))
            assertTrue("External surface did not recognize a single tap", single.await(3, TimeUnit.SECONDS))
            assertEquals(ScrollExecutionResult.COMPLETED, adapter.execute(ScrollRequest(ScrollAction.DOUBLE_TAP)))
            assertTrue("External surface did not recognize a double tap", double.await(3, TimeUnit.SECONDS))
            assertTrue("Expected exactly three touch-ups", threeUps.await(3, TimeUnit.SECONDS))
        } finally {
            instrumentation.context.startActivity(Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("finish", true))
            assertTrue("Test surface did not close", destroyed.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun enabledServiceDispatchesBothDirectionsAndScrollSurfaceActuallyMoves() = runBlocking {
        InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        assumeTrue("Enable Hand Scroll accessibility manually before running this integration test",
            withTimeoutOrNull(30_000) { GestureAccessibilityService.readiness.first { it } } == true)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        val offsetLock = Object()
        var offset = 0
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                synchronized(offsetLock) {
                    offset = resultData?.getInt("offset") ?: 0
                    offsetLock.notifyAll()
                }
                if (resultCode == SwipeTestActivity.READY) ready.countDown()
                if (resultCode == SwipeTestActivity.DESTROYED) destroyed.countDown()
            }
        }
        val component = ComponentName(instrumentation.context.packageName, SwipeTestActivity::class.java.name)
        val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("receiver", receiver)
        instrumentation.context.startActivity(intent)
        try {
            assertTrue("Test surface did not become ready", ready.await(5, TimeUnit.SECONDS))
            withTimeout(5000) { GestureAccessibilityService.targetReadiness.first { it } }
            val adapter = AccessibilityScrollAdapter()
            assertEquals(ScrollExecutionResult.COMPLETED, adapter.execute(ScrollRequest(ScrollAction.FORWARD)))
            assertTrue("API completed but no forward scroll was observed", awaitOffset(offsetLock, { offset }) { it > 0 })
            val beforeBackward = synchronized(offsetLock) { offset }
            assertEquals(ScrollExecutionResult.COMPLETED, adapter.execute(ScrollRequest(ScrollAction.BACKWARD)))
            assertTrue("API completed but no backward scroll was observed",
                awaitOffset(offsetLock, { offset }) { it < beforeBackward })
        } finally {
            instrumentation.context.startActivity(Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("finish", true))
            assertTrue("Test surface did not close", destroyed.await(5, TimeUnit.SECONDS))
        }
    }

    private fun awaitOffset(lock: Object, value: () -> Int, predicate: (Int) -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 3000
        synchronized(lock) {
            while (!predicate(value())) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) return false
                lock.wait(remaining)
            }
            return true
        }
    }
}
