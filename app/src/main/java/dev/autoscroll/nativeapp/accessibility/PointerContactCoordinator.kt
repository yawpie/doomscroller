package dev.autoscroll.nativeapp.accessibility

import dev.autoscroll.nativeapp.scrolling.ScrollExecutionResult

internal data class PointerDispatchSample(val point: ScreenPoint, val timestampMs: Long,
    val bounds: SwipeBounds, val rotation: Int, val targetPackage: String?)
internal enum class PointerSegmentKind { BEGIN, MOVE, END }
internal data class PointerSegment(val kind: PointerSegmentKind, val start: ScreenPoint, val end: ScreenPoint)
internal data class PointerContactState(val active: Boolean = false, val dispatching: Boolean = false,
    val ending: Boolean = false, val position: ScreenPoint? = null,
    val lastResult: ScrollExecutionResult? = null, val recoveryRequired: Boolean = false)

internal fun pointerResultOnce(complete: (ScrollExecutionResult) -> Unit): (ScrollExecutionResult) -> Unit {
    var delivered = false
    return { result -> if (!delivered) { delivered = true; complete(result) } }
}

/** Main-thread owner of one contact; platform callbacks outlive camera/session coroutines. */
internal class PointerContactCoordinator(
    private val guard: (PointerDispatchSample, ScreenPoint) -> ScrollExecutionResult?,
    private val dispatch: (PointerSegment, (ScrollExecutionResult) -> Unit) -> ScrollExecutionResult?,
    private val onState: (PointerContactState) -> Unit = {},
) {
    var state = PointerContactState()
        private set
    private var operation: PointerSegment? = null
    private var pending: PointerDispatchSample? = null
    private var terminalResult = ScrollExecutionResult.COMPLETED

    fun begin(sample: PointerDispatchSample): ScrollExecutionResult? {
        if (state.active || state.recoveryRequired) return ScrollExecutionResult.BUSY
        guard(sample, sample.point)?.let { return it }
        terminalResult = ScrollExecutionResult.COMPLETED
        // Reserve the operation before notifying a listener that may synchronously pause/stop the session.
        state = PointerContactState(active = true, position = sample.point)
        return submit(PointerSegment(PointerSegmentKind.BEGIN, sample.point, sample.point))
    }

    fun move(sample: PointerDispatchSample) {
        if (!state.active || state.ending) return
        pending = sample
        drain()
    }

    fun end(result: ScrollExecutionResult = ScrollExecutionResult.COMPLETED) {
        pending = null
        if (!state.active) return
        if (!state.ending || terminalResult == ScrollExecutionResult.COMPLETED) terminalResult = result
        update(state.copy(ending = true))
        drain()
    }

    fun acknowledgeRecovery() {
        if (!state.recoveryRequired) return
        operation = null; pending = null
        update(PointerContactState())
    }

    private fun drain() {
        if (operation != null || !state.active) return
        val position = checkNotNull(state.position)
        if (state.ending) {
            submit(PointerSegment(PointerSegmentKind.END, position, position))
            return
        }
        val sample = pending ?: return
        pending = null
        guard(sample, position)?.let { end(it); return }
        // AOSP rounds pixels; an equal-point continuation has no events and must never be submitted.
        if (sample.point != position) submit(PointerSegment(PointerSegmentKind.MOVE, position, sample.point))
    }

    private fun submit(segment: PointerSegment): ScrollExecutionResult? {
        operation = segment
        update(state.copy(dispatching = true))
        val rejection = dispatch(segment) { finish(segment, it, submitted = true) }
        if (rejection != null) finish(segment, rejection, submitted = false)
        return rejection
    }

    private fun finish(segment: PointerSegment, result: ScrollExecutionResult, submitted: Boolean) {
        if (operation !== segment) return
        operation = null
        if (result == ScrollExecutionResult.COMPLETED) {
            if (segment.kind == PointerSegmentKind.END) {
                pending = null
                update(PointerContactState(position = segment.end, lastResult = terminalResult))
            } else {
                update(state.copy(dispatching = false, position = segment.end))
                drain()
            }
        } else if (submitted && result == ScrollExecutionResult.CANCELLED ||
            !submitted && segment.kind == PointerSegmentKind.BEGIN) {
            pending = null
            update(PointerContactState(position = state.position, lastResult = result))
        } else if (segment.kind == PointerSegmentKind.END) {
            pending = null
            update(PointerContactState(position = state.position, lastResult = result, recoveryRequired = true))
        } else {
            // A rejected continuation preserves the old stroke; a missing callback may still leave it down.
            update(state.copy(dispatching = false,
                position = if (submitted) segment.end else segment.start))
            end(result)
        }
    }

    private fun update(value: PointerContactState) { state = value; onState(value) }
}

internal fun pointerPathAllowed(start: ScreenPoint, end: ScreenPoint, bounds: SwipeBounds, widget: SwipeBounds?): Boolean {
    fun contains(point: ScreenPoint) = point.x >= bounds.left && point.y >= bounds.top &&
        point.x < bounds.left + bounds.width && point.y < bounds.top + bounds.height
    if (!contains(start) || !contains(end)) return false
    if (widget == null || widget.width <= 0 || widget.height <= 0) return true
    var first = 0.0
    var last = 1.0
    fun clip(origin: Int, delta: Int, minimum: Int, maximum: Int): Boolean {
        if (delta == 0) return origin in minimum..maximum
        val a = (minimum - origin).toDouble() / delta
        val b = (maximum - origin).toDouble() / delta
        first = maxOf(first, minOf(a, b)); last = minOf(last, maxOf(a, b))
        return first <= last
    }
    val intersects = clip(start.x, end.x - start.x, widget.left, widget.left + widget.width) &&
        clip(start.y, end.y - start.y, widget.top, widget.top + widget.height)
    return !intersects
}
