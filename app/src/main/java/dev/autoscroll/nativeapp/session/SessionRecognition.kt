package dev.autoscroll.nativeapp.session

import dev.autoscroll.nativeapp.accessibility.SwipeBounds
import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.ControlMode
import dev.autoscroll.nativeapp.settings.NativeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class SessionDecision(val scroll: GestureDecision? = null, val pointer: PointerFeedback? = null)

internal fun pointerFeedbackFresh(feedback: PointerFeedback, nowMs: Long, freshnessMs: Long) =
    feedback.timestampMs >= 0 && nowMs >= feedback.timestampMs && nowMs - feedback.timestampMs < freshnessMs

internal fun schedulePointerExpiry(scope: CoroutineScope, feedback: PointerFeedback, freshnessMs: Long,
    nowMs: () -> Long, onExpired: () -> Unit) = scope.launch {
    delay((feedback.timestampMs + freshnessMs - nowMs()).coerceAtLeast(0))
    onExpired()
}

/** One recognizer per external session; modes cannot emit together. */
internal class SessionRecognition(settings: NativeSettings, area: PointerArea?, private val bounds: SwipeBounds,
    private val rotation: Int = 0) {
    val gestures = if (settings.controlMode == ControlMode.SCROLL)
        GestureCoordinator(ContinuousMotionDetector(settings.motion), FlickDetector(settings.flick),
            HandPoseDetector(settings.pose), settings.mode) else null
    val pointer = if (settings.controlMode == ControlMode.POINTER) PointerDetector().also { it.calibrate(area) } else null
    fun viewportChanged(currentBounds: SwipeBounds, currentRotation: Int) = bounds != currentBounds || rotation != currentRotation

    fun process(result: HandTrackingResult, nowMs: Long, currentBounds: SwipeBounds, eligible: Boolean): SessionDecision {
        gestures?.let { return SessionDecision(scroll = it.process(result)) }
        val detector = checkNotNull(pointer)
        val feedback = when {
            bounds != currentBounds -> detector.calibrate(null)
            !eligible -> detector.cancel("Pointer paused over this screen")
            else -> detector.process(result, nowMs)
        }
        return SessionDecision(pointer = feedback)
    }

    fun cancel(message: String? = null): PointerFeedback? {
        gestures?.reset()
        return pointer?.cancel(message)
    }
    fun reset(): PointerFeedback? { gestures?.reset(); return pointer?.reset() }
}
