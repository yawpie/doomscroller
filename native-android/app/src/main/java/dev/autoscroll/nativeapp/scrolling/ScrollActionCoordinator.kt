package dev.autoscroll.nativeapp.scrolling

import dev.autoscroll.nativeapp.gesture.FlickDirection
import dev.autoscroll.nativeapp.gesture.FlickScrollAction
import dev.autoscroll.nativeapp.gesture.GestureDecision
import dev.autoscroll.nativeapp.gesture.RecognizedHandPose
import dev.autoscroll.nativeapp.gesture.ScrollConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

/** All entrypoints and the injected scope must run on the same dispatcher (Android main). */
class ScrollActionCoordinator(
    private val executor: ScrollExecutor,
    private val scope: CoroutineScope,
    private val clockMs: () -> Long,
    private var config: ScrollConfig = ScrollConfig(),
) {
    private val mutableState = MutableStateFlow(ScrollActionState())
    val state: StateFlow<ScrollActionState> = mutableState.asStateFlow()
    private var mapper = ScrollVelocityMapper(config)
    private var inverted = false
    private var generation = 0L
    private var operation: Job? = null
    private var sessionStartedAt = Long.MAX_VALUE
    private var lastDiscreteTimestamp = Long.MIN_VALUE
    private var previousMotionTimestamp: Long? = null
    private var velocity = 0.0

    fun setSessionActive(active: Boolean) {
        if (active == state.value.sessionActive) return
        resetPending()
        sessionStartedAt = if (active) clockMs() else Long.MAX_VALUE
        lastDiscreteTimestamp = Long.MIN_VALUE
        mutableState.value = state.value.copy(sessionActive = active, rejectionReason = null)
    }

    fun configure(config: ScrollConfig = this.config, inverted: Boolean = this.inverted) {
        resetPending()
        this.config = config
        this.inverted = inverted
        mapper = ScrollVelocityMapper(config).also { it.configure(inverted = inverted) }
        sessionStartedAt = if (state.value.sessionActive) clockMs() else Long.MAX_VALUE
        lastDiscreteTimestamp = Long.MIN_VALUE
    }

    /** nowMs is the monotonic timestamp of the inference sample, not its callback delivery time. */
    fun process(decision: GestureDecision, nowMs: Long = clockMs()) {
        val pose = decision.pose
        val flick = decision.flick
        if (pose != null || flick != null) {
            stopContinuous()
            val action = if (pose != null) when (pose.pose) {
                RecognizedHandPose.POINTING_DOWN -> direction(true)
                RecognizedHandPose.POINTING_UP -> direction(false)
                RecognizedHandPose.NEUTRAL -> ScrollAction.SINGLE_TAP
                RecognizedHandPose.THUMBS_UP -> ScrollAction.DOUBLE_TAP
                else -> return reject("Pose has no screen action")
            } else direction(flick!!.direction == FlickDirection.DOWN)
            val timestamp = pose?.timestampMs ?: flick!!.timestampMs
            if (timestamp > clockMs()) return reject("Gesture timestamp is in the future")
            if (timestamp <= lastDiscreteTimestamp) return reject("Duplicate or out-of-order gesture")
            lastDiscreteTimestamp = timestamp
            val distance = if (pose == null && config.flickAction == FlickScrollAction.FAST_SCROLL)
                config.flickScrollDistance else null
            val request = if (action.isTap) ScrollRequest(action) else ScrollRequest(action, distance, config.flickDurationMs)
            dispatch(request, timestamp)
            return
        }
        if (decision.suppressesContinuous) return stopContinuous()
        val target = mapper.map(decision.motion)
        if (target == 0.0) return stopContinuous()
        if (!fresh(nowMs)) {
            stopContinuous()
            return reject("Stale motion sample")
        }
        val previous = previousMotionTimestamp
        if (previous != null && nowMs <= previous) return reject("Duplicate or out-of-order motion")
        if (previous == null || nowMs - previous > config.staleAfterMs || velocity * target < 0) velocity = 0.0
        val seconds = ((nowMs - (previous ?: (nowMs - 16))).coerceIn(0, 50)) / 1000.0
        val alpha = 1 - (1 - config.smoothingAlpha).pow(seconds * 60)
        velocity += alpha * (target - velocity)
        previousMotionTimestamp = nowMs
        mutableState.value = state.value.copy(continuousPixelsPerSecond = velocity)
        // ponytail: short swipes approximate continuous pixel movement; calibrate touch slop on real targets.
        dispatch(ScrollRequest(if (velocity > 0) ScrollAction.FORWARD else ScrollAction.BACKWARD,
            abs(velocity) * CONTINUOUS_DURATION_MS / 1000.0, CONTINUOUS_DURATION_MS), nowMs)
    }

    fun manual(action: ScrollAction) {
        stopContinuous()
        dispatch(ScrollRequest(action), clockMs(), manual = true)
    }

    private fun direction(down: Boolean): ScrollAction =
        if (down != inverted) ScrollAction.FORWARD else ScrollAction.BACKWARD

    private fun fresh(timestamp: Long): Boolean {
        val current = clockMs()
        return timestamp >= sessionStartedAt && timestamp <= current && current - timestamp <= config.staleAfterMs
    }

    private fun dispatch(request: ScrollRequest, timestamp: Long, manual: Boolean = false) {
        if (!state.value.sessionActive) return reject("Session is paused or stopped")
        if (!fresh(timestamp)) return reject("Stale gesture or previous session")
        if (operation?.isActive == true) return reject("Screen action already in progress")
        if (!executor.isReady) return reject("Accessibility service unavailable")
        val token = generation
        operation = scope.launch(start = CoroutineStart.LAZY) {
            if (token != generation || !state.value.sessionActive) return@launch
            if (!fresh(timestamp)) {
                operation = null
                reject("Stale gesture before dispatch")
                return@launch
            }
            val latency = if (manual) null else (clockMs() - timestamp).coerceAtLeast(0)
            mutableState.value = state.value.copy(busy = true, lastAction = request.action,
                rejectionReason = null, lastResult = null, lastGestureToActionLatencyMs = latency)
            try {
                val result = executor.execute(request)
                if (token == generation) mutableState.value = state.value.copy(lastResult = result,
                    rejectionReason = if (result == ScrollExecutionResult.COMPLETED) null else result.name)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (token == generation) mutableState.value = state.value.copy(
                    lastResult = ScrollExecutionResult.REJECTED,
                    rejectionReason = failure.message ?: "Screen action execution failed")
            } finally {
                if (token == generation) {
                    operation = null
                    mutableState.value = state.value.copy(busy = false)
                }
            }
        }
        operation!!.start()
    }

    private fun stopContinuous() {
        velocity = 0.0
        previousMotionTimestamp = null
        mutableState.value = state.value.copy(continuousPixelsPerSecond = 0.0)
    }

    private fun resetPending() {
        generation++
        operation?.cancel()
        operation = null
        stopContinuous()
        mutableState.value = state.value.copy(busy = false)
    }

    private fun reject(reason: String) {
        mutableState.value = state.value.copy(rejectionReason = reason)
    }

    companion object { private const val CONTINUOUS_DURATION_MS = 100L }
}
