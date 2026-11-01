package dev.autoscroll.nativeapp.accessibility

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.MainActivity
import dev.autoscroll.nativeapp.gesture.PoseAction
import dev.autoscroll.nativeapp.scrolling.PoseActionResolver
import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.scrolling.ScrollExecutionResult
import dev.autoscroll.nativeapp.scrolling.ScrollRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackDispatchTest {
    @Test fun globalBackHonorsTargetAndInteractionGuardsAndNavigatesOnce() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        assumeTrue("Reconnect the preapproved accessibility service", withTimeoutOrNull(30_000) {
            GestureAccessibilityService.readiness.first { it }
        } == true)
        val ready = CountDownLatch(1)
        val list = CountDownLatch(1)
        val rootBack = CountDownLatch(1)
        val scrolled = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                when (resultCode) {
                    BackTestActivity.READY -> ready.countDown()
                    BackTestActivity.BACK_TO_LIST -> list.countDown()
                    BackTestActivity.ROOT_BACK -> rootBack.countDown()
                    BackTestActivity.SCROLLED -> if ((resultData?.getInt("offset") ?: 0) > 0) scrolled.countDown()
                    BackTestActivity.DESTROYED -> destroyed.countDown()
                }
            }
        }
        val targetPackage = instrumentation.context.packageName
        val component = ComponentName(targetPackage, BackTestActivity::class.java.name)
        instrumentation.context.startActivity(Intent().setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("receiver", receiver))
        try {
            assertTrue("External detail did not become ready", ready.await(5, TimeUnit.SECONDS))
            val target = withTimeout(5000) { GestureAccessibilityService.targetState.first { it.allowed && it.packageName == targetPackage } }
            val adapter = AccessibilityScrollAdapter()
            assertEquals(ScrollExecutionResult.TARGET_BLOCKED,
                adapter.execute(ScrollRequest(ScrollAction.BACK, expectedTargetPackage = instrumentation.targetContext.packageName)))
            assertEquals(1L, list.count)
            assertEquals(ScrollExecutionResult.TARGET_BLOCKED,
                adapter.execute(ScrollRequest(ScrollAction.BACK, expectedTargetPackage = targetPackage,
                    expectedTargetGeneration = target.generation - 1)))
            assertEquals(1L, list.count)
            withContext(Dispatchers.Main.immediate) { adapter.suspendForInteraction() }
            assertEquals(ScrollExecutionResult.USER_INTERACTION,
                adapter.execute(ScrollRequest(ScrollAction.BACK, expectedTargetPackage = targetPackage,
                    expectedTargetGeneration = target.generation)))
            delay(850)
            assertEquals(ScrollExecutionResult.ACCEPTED,
                adapter.execute(ScrollRequest(ScrollAction.BACK, expectedTargetPackage = targetPackage,
                    expectedTargetGeneration = target.generation)))
            assertTrue("Back was accepted but the external detail did not navigate to the list", list.await(5, TimeUnit.SECONDS))
            assertFalse("A second Back reached the list", rootBack.await(600, TimeUnit.MILLISECONDS))
            assertEquals(ScrollExecutionResult.TARGET_BLOCKED,
                adapter.execute(ScrollRequest(ScrollAction.BACK, expectedTargetPackage = instrumentation.targetContext.packageName)))
            assertFalse("A blocked Back reached the list", rootBack.await(300, TimeUnit.MILLISECONDS))
            assertEquals(ScrollExecutionResult.COMPLETED,
                adapter.execute(ScrollRequest(ScrollAction.FORWARD,
                    PoseActionResolver.holdVelocity(PoseAction.CONTINUOUS_NEXT) * 0.1, 100, targetPackage, target.generation)))
            assertTrue("External list did not scroll", scrolled.await(5, TimeUnit.SECONDS))
            val swipe = async { adapter.execute(ScrollRequest(ScrollAction.FORWARD, 180.0, 600, targetPackage, target.generation)) }
            delay(100)
            assertEquals(ScrollExecutionResult.BUSY,
                adapter.execute(ScrollRequest(ScrollAction.BACK, expectedTargetPackage = targetPackage,
                    expectedTargetGeneration = target.generation)))
            assertEquals(ScrollExecutionResult.COMPLETED, swipe.await())
            assertFalse("Busy Back was queued", rootBack.await(300, TimeUnit.MILLISECONDS))
        } finally {
            instrumentation.context.startActivity(Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("finish", true))
            assertTrue("External test surface did not close", destroyed.await(5, TimeUnit.SECONDS))
        }
        instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        withTimeout(5000) { GestureAccessibilityService.targetState.first {
            it.packageName == instrumentation.targetContext.packageName && !it.allowed
        } }
        assertEquals(ScrollExecutionResult.TARGET_BLOCKED, AccessibilityScrollAdapter().execute(ScrollRequest(ScrollAction.BACK)))
    }
}
