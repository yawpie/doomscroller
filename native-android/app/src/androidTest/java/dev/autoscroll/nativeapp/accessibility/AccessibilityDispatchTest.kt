package dev.autoscroll.nativeapp.accessibility

import android.content.ComponentName
import android.content.Intent
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
    @Test fun enabledServiceDispatchesBothDirectionsAndScrollSurfaceActuallyMoves() = runBlocking {
        assumeTrue("Enable Hand Scroll accessibility manually before running this integration test",
            withTimeoutOrNull(5000) { GestureAccessibilityService.readiness.first { it } } == true)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        val offsetLock = Object()
        var offset = 0
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                synchronized(offsetLock) {
                    offset = resultData?.getInt("offset") ?: 0
                    offsetLock.notifyAll()
                }
                if (resultCode == SwipeTestActivity.READY) ready.countDown()
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
