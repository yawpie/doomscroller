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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import dev.autoscroll.nativeapp.accessibility.AccessibilityScrollAdapter
import dev.autoscroll.nativeapp.accessibility.GestureAccessibilityService
import dev.autoscroll.nativeapp.accessibility.SwipeConfig
import dev.autoscroll.nativeapp.presentation.HandScrollScreen
import dev.autoscroll.nativeapp.presentation.settingsChangePauses
import dev.autoscroll.nativeapp.presentation.SettingsDraft
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
    private var manualRunning by mutableStateOf(false)
    private var manualResult by mutableStateOf("No screen action test yet")
    private var permissionError by mutableStateOf<String?>(null)
    private val permissionPreferences by lazy { getSharedPreferences("permission_setup", MODE_PRIVATE) }
    private val settingsDraft by lazy { ViewModelProvider(this)[SettingsDraft::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (settingsDraft.value == null) settingsDraft.value = app.settings.state.value
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme(
                primary = Color(0xFFB4D995), onPrimary = Color(0xFF213610),
                primaryContainer = Color(0xFF304F18), onPrimaryContainer = Color(0xFFD7EDBF),
            ) else lightColorScheme(
                primary = Color(0xFF386A20), onPrimary = Color.White,
                primaryContainer = Color(0xFFD7EDBF), onPrimaryContainer = Color(0xFF172B09),
            )) {
                val state by app.session.state.collectAsStateWithLifecycle()
                val settings by app.settings.state.collectAsStateWithLifecycle()
                val diagnostics by app.session.diagnostics.collectAsStateWithLifecycle()
                val accessibility by GestureAccessibilityService.readiness.collectAsStateWithLifecycle()
                var consent by remember { mutableStateOf(app.settings.accessibilityConsent) }
                val cameraRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
                val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionRevision++ }
                val cameraGranted = remember(permissionRevision) { hasCamera() }
                val cameraRequested = remember(permissionRevision) { permissionPreferences.getBoolean("cameraRequested", false) }
                val cameraBlocked = !cameraGranted && cameraRequested && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                val overlayGranted = remember(permissionRevision) { Settings.canDrawOverlays(this) }
                HandScrollScreen(
                    state = state,
                    settings = settings,
                    draft = settingsDraft.value ?: settings,
                    onDraftChange = { settingsDraft.value = it },
                    diagnostics = diagnostics,
                    accessibility = accessibility,
                    consent = consent,
                    cameraGranted = cameraGranted,
                    cameraBlocked = cameraBlocked,
                    cameraExplanation = if (!cameraGranted && cameraRequested) {
                        if (cameraBlocked) "Camera access is blocked. In App info, open Permissions → Camera and allow access, then return here."
                        else "Camera access was denied. Tracking needs the front camera; you can allow it below when ready."
                    } else null,
                    overlayGranted = overlayGranted,
                    notificationRequired = Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED,
                    manualRunning = manualRunning,
                    manualResult = manualResult,
                    permissionError = permissionError,
                    onCameraPermission = {
                        if (cameraBlocked) openPermissionSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                        else {
                            permissionPreferences.edit().putBoolean("cameraRequested", true).apply()
                            cameraRequest.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onOverlayPermission = {
                        openPermissionSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
                    },
                    onNotificationPermission = { notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onAccessibilityConsent = {
                        app.settings.accessibilityConsent = true
                        consent = true
                        openPermissionSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
    private fun openPermissionSettings(intent: Intent) {
        stopBeforePermissionSettings()
        permissionError = null
        try { startActivity(intent) }
        catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())) }
            catch (_: Exception) { permissionError = "Android could not open Settings. Open Settings → Apps → Scroll Vision to review permissions, then return here." }
        }
    }
    private fun updateSettings(settings: NativeSettings) {
        if (settings == app.settings.state.value) return
        if (settingsChangePauses(app.session.machine.state, app.settings.state.value, settings)) pauseSession()
        app.settings.update(settings)
    }
    private fun testAction(action: ScrollAction) {
        if (manualRunning || !app.settings.accessibilityConsent || !GestureAccessibilityService.readiness.value ||
            app.session.machine.state != GestureSessionState.Stopped) return
        manualTest?.cancel()
        manualRunning = true
        manualTest = lifecycleScope.launch {
            try {
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
            } finally { manualRunning = false }
        }
    }
}
