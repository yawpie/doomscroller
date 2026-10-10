package dev.autoscroll.nativeapp.session

import dev.autoscroll.nativeapp.mediapipe.InferenceMetrics
import dev.autoscroll.nativeapp.gesture.PointerArea
import dev.autoscroll.nativeapp.gesture.PointerFeedback
import dev.autoscroll.nativeapp.accessibility.SwipeBounds
import dev.autoscroll.nativeapp.accessibility.ScreenPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CompletableDeferred

data class SessionDiagnostics(
    val metrics: InferenceMetrics = InferenceMetrics(),
    val handDetected: Boolean = false,
    val pose: String = "UNKNOWN",
    val posePhase: String = "READY",
    val lastFlick: String = "None",
    val lastPose: String = "None",
    val trackingLossCount: Long = 0,
    val lastAction: String = "None",
    val rejectionReason: String? = null,
    val gestureToActionMs: Long? = null,
    val pointer: PointerFeedback? = null,
    val cursorPosition: ScreenPoint? = null,
)

class SessionStore {
    // Explicit practice handoff, consumed by a new external Start; never persisted.
    var pointerCalibration: PointerArea? = null
        private set
    private var calibrationBounds: SwipeBounds? = null
    private var calibrationRotation = 0
    private val mutablePointerCalibrationReady = MutableStateFlow(false)
    val pointerCalibrationReady = mutablePointerCalibrationReady.asStateFlow()
    internal fun preparePointerCalibration(area: PointerArea, bounds: SwipeBounds, rotation: Int) {
        pointerCalibration = area
        calibrationBounds = bounds
        calibrationRotation = rotation
        mutablePointerCalibrationReady.value = true
    }
    internal fun takePointerCalibration(bounds: SwipeBounds, rotation: Int): PointerArea? {
        val area = pointerCalibration.takeIf { calibrationBounds == bounds && calibrationRotation == rotation }
        invalidatePointerCalibration()
        return area
    }
    internal fun checkPointerCalibrationBounds(bounds: SwipeBounds, rotation: Int) {
        if (pointerCalibration != null && (calibrationBounds != bounds || calibrationRotation != rotation)) invalidatePointerCalibration()
    }
    fun invalidatePointerCalibration() {
        pointerCalibration = null; calibrationBounds = null; mutablePointerCalibrationReady.value = false
    }
    val machine = SessionMachine()
    private val mutableState = MutableStateFlow<GestureSessionState>(machine.state)
    private val mutableDiagnostics = MutableStateFlow(SessionDiagnostics())
    val state = mutableState.asStateFlow()
    val diagnostics = mutableDiagnostics.asStateFlow()
    // The application outlives service instances: Stop -> Start must await the old worker too.
    var cameraReleased = CompletableDeferred(Unit)
    var serviceRunning = false
    var practiceOpen = false
        private set
    private var practicePreparation = CompletableDeferred(Unit)
    private var startRequest = 0L
    fun requestStart(): Long = ++startRequest
    fun acceptsStart(request: Long) = !practiceOpen && request == startRequest

    fun enterPractice() {
        invalidatePointerCalibration()
        practiceOpen = true
        startRequest++
        machine.pause()
        publish()
        if (serviceRunning && practicePreparation.isCompleted) practicePreparation = CompletableDeferred()
    }

    // A paused state can precede service teardown. Acknowledge only after releaseSession.
    fun practicePrepared() { practicePreparation.complete(Unit) }
    suspend fun awaitPracticeCamera() {
        practicePreparation.await()
        cameraReleased.await()
    }
    fun leavePractice() { practiceOpen = false }
    private val mutableVisibility = MutableStateFlow(false)
    val visibility = mutableVisibility.asStateFlow()
    var activityVisible: Boolean
        get() = mutableVisibility.value
        set(value) { mutableVisibility.value = value }
    fun publish() { mutableState.value = machine.state }
    fun diagnostics(value: SessionDiagnostics) { mutableDiagnostics.value = value }
}
