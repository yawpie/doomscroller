package dev.autoscroll.nativeapp.accessibility

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.scrolling.*
import dev.autoscroll.nativeapp.session.SessionRecognition
import dev.autoscroll.nativeapp.settings.NativeSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DefaultPoseDispatchTest {
    @Test fun heldDefaultPointingDownScrollsExternalSurfaceOnce() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        assumeTrue("Reconnect the preapproved accessibility service", withTimeoutOrNull(30_000) {
            GestureAccessibilityService.readiness.first { it }
        } == true)
        val ready = CountDownLatch(1)
        val scrolled = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                when (resultCode) {
                    SwipeTestActivity.READY -> ready.countDown()
                    SwipeTestActivity.SCROLLED -> if ((resultData?.getInt("offset") ?: 0) > 0) scrolled.countDown()
                    SwipeTestActivity.DESTROYED -> destroyed.countDown()
                }
            }
        }
        val targetPackage = instrumentation.context.packageName
        val component = ComponentName(targetPackage, SwipeTestActivity::class.java.name)
        instrumentation.context.startActivity(Intent().setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("receiver", receiver))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            assertTrue("External surface did not become ready", ready.await(5, TimeUnit.SECONDS))
            val target = withTimeout(5000) { GestureAccessibilityService.targetState.first { it.allowed && it.packageName == targetPackage } }
            val adapter = AccessibilityScrollAdapter()
            val requests = mutableListOf<ScrollRequest>()
            val executor = object : ScrollExecutor {
                override val isReady get() = adapter.isReady
                override suspend fun execute(request: ScrollRequest): ScrollExecutionResult {
                    requests += request
                    return adapter.execute(request)
                }
            }
            val settings = NativeSettings(mode = GestureMode.POSE)
            val bounds = requireNotNull(GestureAccessibilityService.connectedService).contentBounds()
            val session = SessionRecognition(settings, null, bounds)
            val coordinator = ScrollActionCoordinator(executor, scope, SystemClock::elapsedRealtime)
            withContext(Dispatchers.Main.immediate) {
                coordinator.configure(settings = settings)
                coordinator.setSessionActive(true)
            }
            repeat(12) {
                val now = SystemClock.elapsedRealtime()
                val frame = pointingDown(now)
                withContext(Dispatchers.Main.immediate) {
                    val decision = session.process(frame, now, bounds, true).scroll!!
                    coordinator.process(decision, now, targetPackage, target.generation)
                }
                delay(100)
            }
            assertTrue("Preset action did not move the external surface", scrolled.await(5, TimeUnit.SECONDS))
            withContext(Dispatchers.Main.immediate) {
                assertEquals(1, requests.size)
                assertEquals(ScrollAction.FORWARD, requests.single().action)
                assertEquals(targetPackage, requests.single().expectedTargetPackage)
                assertEquals(target.generation, requests.single().expectedTargetGeneration)
            }
        } finally {
            scope.cancel()
            instrumentation.context.startActivity(Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("finish", true))
            assertTrue("External test surface did not close", destroyed.await(5, TimeUnit.SECONDS))
        }
    }

    private fun pointingDown(time: Long): HandTrackingResult {
        val p = MutableList(21) { 0.0 to 0.0 }
        p[0] = 0.0 to 0.12; p[1] = -0.11 to 0.09; p[2] = -0.11 to 0.03
        p[3] = -0.04 to 0.03; p[4] = 0.02 to 0.03
        for ((mcp, x, y) in listOf(Triple(5, -0.065, -0.06), Triple(9, -0.02, -0.07),
            Triple(13, 0.03, -0.06), Triple(17, 0.075, -0.04))) {
            val open = mcp == 5
            p[mcp] = x to y; p[mcp + 1] = x to y - 0.085
            p[mcp + 2] = if (open) x to y - 0.16 else x + 0.05 to y - 0.085
            p[mcp + 3] = if (open) x to y - 0.22 else x + 0.05 to y - 0.035
        }
        return HandTrackingResult(time, HandPose(p.mapIndexed { i, (x, y) -> HandLandmark(i, 0.5 - x, 0.5 - y, 0.0) }))
    }
}
