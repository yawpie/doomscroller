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
    private var pointerStroke: GestureDescription.StrokeDescription? = null
    private var pointerSample: PointerDispatchSample? = null
    private var pointerSessionAllowed: () -> Boolean = { false }
    private var pointerWidget: () -> Rect? = { null }
    private var pointerListener: (PointerContactState) -> Unit = {}
    private var pointerTimeout: Runnable? = null
    private val pointer = PointerContactCoordinator(::validatePointer, ::dispatchPointer, ::publishPointerContact)

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
            val previous = target.state
            target.update(event.packageName?.toString())
            if (previous != target.state) pointer.end(ScrollExecutionResult.TARGET_BLOCKED)
            mutableTargetReadiness.value = target.allowed
            mutableTarget.value = target.state
        }
    }

    override fun onInterrupt() {
        suspendForInteraction(800)
    }

    internal fun suspendForInteraction(durationMs: Long) {
        interactionBlockedUntil = maxOf(interactionBlockedUntil, SystemClock.elapsedRealtime() + durationMs)
        pointer.end(ScrollExecutionResult.USER_INTERACTION)
    }
    internal fun pointerAllowed(): Boolean = connectedService === this && ::target.isInitialized && target.allowed &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked && getSystemService(PowerManager::class.java).isInteractive &&
        SystemClock.elapsedRealtime() >= interactionBlockedUntil
    // Activity insets stay authoritative: the tiny cursor's window uses a different inset policy.
    internal fun updatePointerInsets(insets: WindowInsets?) { pointerInsets = insets }

    internal suspend fun execute(request: ScrollRequest, config: SwipeConfig, overlayBounds: Rect?): ScrollExecutionResult {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (connectedService !== this) return ScrollExecutionResult.UNAVAILABLE
        if (inFlight != null || pointerDispatchBusy) return ScrollExecutionResult.BUSY
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
        // Best effort before losing the binder; there is no public gesture-cancel API after disconnect.
        pointer.end(ScrollExecutionResult.UNAVAILABLE)
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

    internal fun beginPointer(sample: PointerDispatchSample, allowed: () -> Boolean,
        widget: () -> Rect?, listener: (PointerContactState) -> Unit): ScrollExecutionResult? {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (inFlight != null || pointerDispatchBusy) return ScrollExecutionResult.BUSY
        pointerSample = sample; pointerSessionAllowed = allowed; pointerWidget = widget; pointerListener = listener
        pointerOwner = this
        val result = pointer.begin(sample)
        if (result != null && !pointer.state.active) {
            pointerOwner = null; pointerListener = {}; pointerSessionAllowed = { false }; pointerWidget = { null }
        }
        return result
    }

    internal fun movePointer(sample: PointerDispatchSample) { pointer.move(sample) }
    internal fun endPointer(result: ScrollExecutionResult = ScrollExecutionResult.COMPLETED) { pointer.end(result) }

    private fun validatePointer(sample: PointerDispatchSample, start: ScreenPoint): ScrollExecutionResult? {
        if (connectedService !== this) return ScrollExecutionResult.UNAVAILABLE
        if (inFlight != null) return ScrollExecutionResult.BUSY
        if (!pointerSessionAllowed()) return ScrollExecutionResult.REJECTED
        if (!::target.isInitialized || !target.allowed || target.state.packageName != sample.targetPackage ||
            pointerSample?.targetPackage != sample.targetPackage) return ScrollExecutionResult.TARGET_BLOCKED
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive) return ScrollExecutionResult.SCREEN_LOCKED
        val now = SystemClock.elapsedRealtime()
        if (now < interactionBlockedUntil) return ScrollExecutionResult.USER_INTERACTION
        if (sample.timestampMs < 0 || sample.timestampMs > now || now - sample.timestampMs >= 300) return ScrollExecutionResult.REJECTED
        @Suppress("DEPRECATION")
        val rotation = getSystemService(android.view.WindowManager::class.java).defaultDisplay.rotation
        if (sample.bounds != contentBounds() || sample.bounds != pointerSample?.bounds ||
            sample.rotation != rotation || sample.rotation != pointerSample?.rotation) return ScrollExecutionResult.REJECTED
        val widget = pointerWidget()?.let { SwipeBounds(it.left, it.top, it.width(), it.height()) }
        if (!pointerPathAllowed(start, sample.point, sample.bounds, widget)) return ScrollExecutionResult.TARGET_BLOCKED
        return null
    }

    private fun dispatchPointer(segment: PointerSegment, complete: (ScrollExecutionResult) -> Unit): ScrollExecutionResult? {
        if (connectedService !== this) return ScrollExecutionResult.UNAVAILABLE
        val path = Path().apply {
            moveTo(segment.start.x.toFloat(), segment.start.y.toFloat())
            if (segment.kind == PointerSegmentKind.MOVE) lineTo(segment.end.x.toFloat(), segment.end.y.toFloat())
        }
        val duration = if (segment.kind == PointerSegmentKind.MOVE) 80L else 1L
        val stroke = try {
            if (segment.kind == PointerSegmentKind.BEGIN) GestureDescription.StrokeDescription(path, 0, duration, true)
            else pointerStroke?.continueStroke(path, 0, duration, segment.kind != PointerSegmentKind.END)
                ?: return ScrollExecutionResult.REJECTED
        } catch (_: IllegalArgumentException) { return ScrollExecutionResult.REJECTED }
        lateinit var timeout: Runnable
        val finish = pointerResultOnce { result ->
            handler.removeCallbacks(timeout)
            if (pointerTimeout === timeout) pointerTimeout = null
            if (result == ScrollExecutionResult.CANCELLED)
                interactionBlockedUntil = maxOf(interactionBlockedUntil, SystemClock.elapsedRealtime() + 800)
            complete(result)
        }
        timeout = Runnable { finish(ScrollExecutionResult.UNAVAILABLE) }
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { finish(ScrollExecutionResult.COMPLETED) }
            override fun onCancelled(gestureDescription: GestureDescription?) { finish(ScrollExecutionResult.CANCELLED) }
        }
        val accepted = try {
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), callback, handler)
        } catch (_: SecurityException) { return ScrollExecutionResult.UNAVAILABLE }
        catch (_: IllegalStateException) { return ScrollExecutionResult.REJECTED }
        if (!accepted) return ScrollExecutionResult.REJECTED
        pointerStroke = stroke
        pointerTimeout = timeout
        handler.postDelayed(timeout, duration + 1500)
        return null
    }

    private fun publishPointerContact(state: PointerContactState) {
        if (pointerOwner !== this) return
        mutablePointerContact.value = state
        val listener = pointerListener
        if (!state.active && !state.recoveryRequired) {
            pointerOwner = null; pointerStroke = null; pointerSample = null
            pointerListener = {}; pointerSessionAllowed = { false }; pointerWidget = { null }
        }
        listener(state)
    }

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
        private var pointerOwner: GestureAccessibilityService? = null
        private val mutablePointerContact = MutableStateFlow(PointerContactState())
        internal val pointerContact = mutablePointerContact.asStateFlow()
        internal val pointerDispatchBusy get() = pointerContact.value.active || pointerContact.value.recoveryRequired
        internal fun acknowledgePointerRecovery() {
            check(Looper.myLooper() == Looper.getMainLooper())
            pointerOwner?.pointer?.acknowledgeRecovery()
        }
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
