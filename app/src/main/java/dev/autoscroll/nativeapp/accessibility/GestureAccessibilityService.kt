package dev.autoscroll.nativeapp.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowInsets
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import dev.autoscroll.nativeapp.scrolling.ScrollExecutionResult
import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.scrolling.ScrollRequest
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GestureAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var target: TargetWindowPolicy
    private var interactionBlockedUntil = 0L
    private var inFlight: Dispatch? = null
    private var pointerInsets: WindowInsets? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        target = TargetWindowPolicy(packageName)
        connectedService = this
        mutableTargetReadiness.value = false
        mutableTarget.value = TargetWindowState()
        mutableReadiness.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (::target.isInitialized && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            target.update(event.packageName?.toString())
            mutableTargetReadiness.value = target.allowed
            mutableTarget.value = target.state
        }
    }

    override fun onInterrupt() {
        suspendForInteraction(800)
    }

    internal fun suspendForInteraction(durationMs: Long) {
        interactionBlockedUntil = maxOf(interactionBlockedUntil, SystemClock.elapsedRealtime() + durationMs)
    }
    internal fun pointerAllowed(): Boolean = connectedService === this && ::target.isInitialized && target.allowed &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked && getSystemService(PowerManager::class.java).isInteractive &&
        SystemClock.elapsedRealtime() >= interactionBlockedUntil
    // Activity insets stay authoritative: the tiny cursor's window uses a different inset policy.
    internal fun updatePointerInsets(insets: WindowInsets?) { pointerInsets = insets }

    internal suspend fun execute(request: ScrollRequest, config: SwipeConfig, overlayBounds: Rect?): ScrollExecutionResult {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (connectedService !== this) return ScrollExecutionResult.UNAVAILABLE
        if (inFlight != null) return ScrollExecutionResult.BUSY
        if (!::target.isInitialized || !target.allowed) return ScrollExecutionResult.TARGET_BLOCKED
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive
        ) return ScrollExecutionResult.SCREEN_LOCKED
        if (SystemClock.elapsedRealtime() < interactionBlockedUntil) return ScrollExecutionResult.USER_INTERACTION
        val bounds = contentBounds()
        val overlay = overlayBounds?.takeIf { it.width() > 0 && it.height() > 0 }?.let {
            SwipeBounds(it.left, it.top, it.width(), it.height())
        }
        val coordinates = avoidOverlay(swipeCoordinates(config, bounds, request), bounds, overlay)
            ?: return ScrollExecutionResult.TARGET_BLOCKED
        val gesture = screenGesture(coordinates, request.action)
        return suspendCancellableCoroutine { continuation ->
            val dispatch = Dispatch(continuation)
            inFlight = dispatch
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    finish(dispatch, ScrollExecutionResult.COMPLETED)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    suspendForInteraction(800)
                    finish(dispatch, ScrollExecutionResult.CANCELLED)
                }
            }
            // The public API has no cancel function: pausing suppresses future output but this short stroke may finish.
            try {
                if (!dispatchGesture(gesture, callback, handler)) finish(dispatch, ScrollExecutionResult.REJECTED)
            } catch (_: SecurityException) {
                finish(dispatch, ScrollExecutionResult.UNAVAILABLE)
            } catch (_: IllegalStateException) {
                finish(dispatch, ScrollExecutionResult.REJECTED)
            }
        }
    }

    private fun finish(dispatch: Dispatch, result: ScrollExecutionResult) {
        if (inFlight !== dispatch) return
        inFlight = null
        if (dispatch.continuation.isActive) dispatch.continuation.resume(result)
    }

    private fun disconnect() {
        if (connectedService === this) {
            connectedService = null
            mutableReadiness.value = false
            mutableTargetReadiness.value = false
            mutableTarget.value = TargetWindowState()
        }
        inFlight?.let { finish(it, ScrollExecutionResult.UNAVAILABLE) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    internal fun contentBounds(): SwipeBounds = screenContentBounds(this, pointerInsets)

    private class Dispatch(val continuation: CancellableContinuation<ScrollExecutionResult>)

    companion object {
        private val mutableReadiness = MutableStateFlow(false)
        val readiness: StateFlow<Boolean> = mutableReadiness.asStateFlow()
        private val mutableTargetReadiness = MutableStateFlow(false)
        internal val targetReadiness: StateFlow<Boolean> = mutableTargetReadiness.asStateFlow()
        private val mutableTarget = MutableStateFlow(TargetWindowState())
        internal val targetState = mutableTarget.asStateFlow()
        internal var connectedService: GestureAccessibilityService? = null
            private set
    }
}

internal fun screenGesture(coordinates: SwipeCoordinates, action: ScrollAction): GestureDescription {
    val path = Path().apply {
        moveTo(coordinates.x, coordinates.startY)
        if (!action.isTap) lineTo(coordinates.x, coordinates.endY)
    }
    val builder = GestureDescription.Builder().addStroke(
        GestureDescription.StrokeDescription(path, 0, coordinates.durationMs))
    if (action == ScrollAction.DOUBLE_TAP) {
        val gap = minOf(100L, ViewConfiguration.getDoubleTapTimeout().toLong() / 2)
        builder.addStroke(GestureDescription.StrokeDescription(path, coordinates.durationMs + gap, coordinates.durationMs))
    }
    return builder.build()
}
