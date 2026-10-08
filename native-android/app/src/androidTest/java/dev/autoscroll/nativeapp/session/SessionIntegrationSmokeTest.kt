package dev.autoscroll.nativeapp.session

import android.Manifest
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.view.inspector.WindowInspector
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.autoscroll.nativeapp.GestureApplication
import dev.autoscroll.nativeapp.MainActivity
import dev.autoscroll.nativeapp.accessibility.GestureAccessibilityService
import dev.autoscroll.nativeapp.camera.CameraController
import dev.autoscroll.nativeapp.camera.TrackingConfig
import dev.autoscroll.nativeapp.overlay.OverlayController
import dev.autoscroll.nativeapp.settings.SettingsStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No permission grants, settings toggles, camera capture, or external gesture dispatch. */
@RunWith(AndroidJUnit4::class)
class SessionIntegrationSmokeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as GestureApplication

    @Test fun manifestLimitsPrivilegedComponentsToTheirPurpose() {
        @Suppress("DEPRECATION")
        val cameraService = context.packageManager.getServiceInfo(
            ComponentName(context, GestureCameraService::class.java), 0)
        assertFalse(cameraService.exported)
        if (Build.VERSION.SDK_INT >= 30) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA, cameraService.foregroundServiceType)
        }
        @Suppress("DEPRECATION")
        val accessibility = context.packageManager.getServiceInfo(
            ComponentName(context, GestureAccessibilityService::class.java), PackageManager.GET_META_DATA)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, accessibility.permission)
        assertTrue(accessibility.metaData.containsKey("android.accessibilityservice"))
    }

    @Test fun activityRecreationAndBackgroundingNeverStartCameraAutomatically() {
        assumeTrue("A user-owned session is already running", app.session.machine.state == GestureSessionState.Stopped)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { assertEquals(GestureSessionState.Stopped, app.session.state.value) }
            scenario.moveToState(Lifecycle.State.CREATED)
            instrumentation.runOnMainSync {
                assertFalse(app.session.activityVisible)
                assertEquals(GestureSessionState.Stopped, app.session.state.value)
            }
        }
    }

    @Test fun deniedCameraFailsWithoutStartingInferenceOrRequestingPermissions() {
        assumeFalse("Camera already granted; this test does not revoke it",
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        assumeTrue("A user-owned session is already running", app.session.machine.state == GestureSessionState.Stopped)
        val denied = CountDownLatch(1)
        var failure: Throwable? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = CameraController(activity, TrackingConfig(),
                    onResult = { fail("Denied camera emitted inference") },
                    onMetrics = { fail("Denied camera emitted metrics") },
                    onError = { failure = it; denied.countDown() })
                controller.start(activity)
            }
            assertTrue("Permission denial was not reported", denied.await(5, TimeUnit.SECONDS))
            assertTrue(failure is SecurityException)
            assertEquals(PackageManager.PERMISSION_DENIED, ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA))
        }
    }

    @Test fun preapprovedOverlayCanBeCreatedRepositionedAndRemovedIdempotently() {
        assumeTrue("Window inspection requires API29", Build.VERSION.SDK_INT >= 29)
        assumeTrue("User has not enabled overlay; no settings are changed", Settings.canDrawOverlays(context))
        assumeTrue("A user-owned session is already running", app.session.machine.state == GestureSessionState.Stopped)
        instrumentation.runOnMainSync {
            val before = WindowInspector.getGlobalWindowViews().size
            val overlay = OverlayController(context, {}, {}, {}, {}, {}, {}, {})
            try {
                overlay.show()
                overlay.show()
                overlay.update(GestureSessionState.Paused)
                overlay.reposition()
                assertEquals(before + 1, WindowInspector.getGlobalWindowViews().size)
            } finally {
                overlay.close()
                overlay.close()
            }
            assertEquals(before, WindowInspector.getGlobalWindowViews().size)
        }
    }

    @Test fun persistedSwipeDurationAlsoConfiguresDiscreteActionsAfterReload() {
        val testContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("qa_$name", mode)
        }
        try {
            val settings = SettingsStore(testContext)
            settings.update(settings.state.value.copy(swipeDurationMs = 400,
                scroll = settings.state.value.scroll.copy(flickDurationMs = 400)))
            val loaded = SettingsStore(testContext).state.value
            assertEquals(400L, loaded.swipeDurationMs)
            assertEquals(400L, loaded.scroll.flickDurationMs)
        } finally {
            context.deleteSharedPreferences("qa_gesture_settings")
        }
    }
}
