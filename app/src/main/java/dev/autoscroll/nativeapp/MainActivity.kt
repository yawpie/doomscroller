package dev.autoscroll.nativeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.autoscroll.nativeapp.accessibility.AccessibilityScrollAdapter
import dev.autoscroll.nativeapp.accessibility.GestureAccessibilityService
import dev.autoscroll.nativeapp.accessibility.SwipeConfig
import dev.autoscroll.nativeapp.presentation.HandScrollScreen
import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.scrolling.ScrollActionCoordinator
import dev.autoscroll.nativeapp.session.GestureCameraService
import dev.autoscroll.nativeapp.session.GestureSessionState
import dev.autoscroll.nativeapp.settings.NativeSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as GestureApplication
    private var permissionRevision by mutableIntStateOf(0)
    private var manualTest: Job? = null
    private var manualResult by mutableStateOf("No screen action test yet")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by app.session.state.collectAsStateWithLifecycle()
                val settings by app.settings.state.collectAsStateWithLifecycle()
                val diagnostics by app.session.diagnostics.collectAsStateWithLifecycle()
                val accessibility by GestureAccessibilityService.readiness.collectAsStateWithLifecycle()
                var consent by remember { mutableStateOf(app.settings.accessibilityConsent) }
                val cameraRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
                val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
                val cameraGranted = remember(permissionRevision) { hasCamera() }
                val overlayGranted = remember(permissionRevision) { Settings.canDrawOverlays(this) }
                HandScrollScreen(
                    state = state,
                    settings = settings,
                    diagnostics = diagnostics,
                    accessibility = accessibility,
                    consent = consent,
                    cameraGranted = cameraGranted,
                    overlayGranted = overlayGranted,
                    notificationRequired = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED,
                    manualRunning = manualTest?.isActive == true,
                    manualResult = manualResult,
                    onCameraPermission = { cameraRequest.launch(Manifest.permission.CAMERA) },
                    onOverlayPermission = {
                        stopBeforePermissionSettings()
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
                    },
                    onNotificationPermission = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onAccessibilityConsent = {
                        app.settings.accessibilityConsent = true
                        consent = true
                        stopBeforePermissionSettings()
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onStart = ::startSession,
                    onPause = ::pauseSession,
                    onStop = ::stopSession,
                    onSettingsChange = ::updateSettings,
                    onTestAction = ::testAction,
                )
            }
        }
    }

    override fun onStart() { super.onStart(); app.session.activityVisible = true }
    override fun onResume() { super.onResume(); permissionRevision++ }
    override fun onStop() { app.session.activityVisible = false; super.onStop() }
    override fun onDestroy() { manualTest?.cancel(); super.onDestroy() }

    private fun hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun startSession() {
        if (!app.session.activityVisible) return
        manualTest?.cancel()
        try {
            ContextCompat.startForegroundService(this, Intent(this, GestureCameraService::class.java).setAction(GestureCameraService.ACTION_START))
        } catch (problem: Exception) {
            app.session.machine.fail(problem.message ?: "Cannot start foreground camera")
            app.session.publish()
        }
    }
    private fun pauseSession() {
        app.session.machine.pause(); app.session.publish()
        startService(Intent(this, GestureCameraService::class.java).setAction(GestureCameraService.ACTION_PAUSE))
    }
    private fun stopSession() {
        manualTest?.cancel()
        app.session.machine.stop(); app.session.publish()
        startService(Intent(this, GestureCameraService::class.java).setAction(GestureCameraService.ACTION_STOP))
    }
    private fun stopBeforePermissionSettings() { if (app.session.machine.state != GestureSessionState.Stopped) stopSession() }
    private fun updateSettings(settings: NativeSettings) {
        if (app.session.machine.state == GestureSessionState.Active || app.session.machine.state == GestureSessionState.Starting) pauseSession()
        app.settings.update(settings)
    }
    private fun testAction(action: ScrollAction) {
        if (!app.settings.accessibilityConsent || app.session.machine.state != GestureSessionState.Stopped) return
        manualTest?.cancel()
        manualTest = lifecycleScope.launch {
            val settings = app.settings.state.value
            val adapter = AccessibilityScrollAdapter(SwipeConfig(startY = 0.5 + settings.swipeDistance / 2,
                endY = 0.5 - settings.swipeDistance / 2, durationMs = settings.swipeDurationMs))
            val coordinator = ScrollActionCoordinator(adapter, this, SystemClock::elapsedRealtime, settings.scroll)
            coordinator.configure(inverted = settings.inverted)
            manualResult = "Waiting to test $action"
            moveTaskToBack(true)
            try {
                delay(1000)
                coordinator.setSessionActive(true)
                coordinator.manual(action)
                delay(settings.swipeDurationMs + 500)
                val result = coordinator.state.value
                manualResult = "${result.lastResult ?: "No result"}: ${result.rejectionReason ?: "check the feed's response"}"
            } finally { coordinator.setSessionActive(false) }
        }
    }
}
