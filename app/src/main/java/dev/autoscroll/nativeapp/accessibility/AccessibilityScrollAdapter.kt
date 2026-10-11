package dev.autoscroll.nativeapp.accessibility

import android.graphics.Rect
import dev.autoscroll.nativeapp.scrolling.ScrollExecutionResult
import dev.autoscroll.nativeapp.scrolling.ScrollExecutor
import dev.autoscroll.nativeapp.scrolling.ScrollRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccessibilityScrollAdapter(
    private var config: SwipeConfig = SwipeConfig(),
    private val overlayBounds: () -> Rect? = { null },
) : ScrollExecutor {
    private var pointerOwner: GestureAccessibilityService? = null
    override val isReady: Boolean get() = GestureAccessibilityService.readiness.value

    fun configure(config: SwipeConfig) { this.config = config }

    /** Call on overlay touch-down so a gesture cannot race with its user's controls. */
    fun suspendForInteraction(durationMs: Long = 800) {
        require(durationMs >= 0)
        GestureAccessibilityService.connectedService?.suspendForInteraction(durationMs)
    }

    override suspend fun execute(request: ScrollRequest): ScrollExecutionResult = withContext(Dispatchers.Main.immediate) {
        val service = GestureAccessibilityService.connectedService ?: return@withContext ScrollExecutionResult.UNAVAILABLE
        service.execute(request, config, overlayBounds())
    }

    internal fun beginPointer(sample: PointerDispatchSample, allowed: () -> Boolean,
        listener: (PointerContactState) -> Unit): ScrollExecutionResult? {
        val service = GestureAccessibilityService.connectedService ?: return ScrollExecutionResult.UNAVAILABLE
        pointerOwner = service
        val result = service.beginPointer(sample, allowed, overlayBounds) { state ->
            if (!state.active) pointerOwner = null
            listener(state)
        }
        if (result != null) pointerOwner = null
        return result
    }

    internal fun movePointer(sample: PointerDispatchSample) { pointerOwner?.movePointer(sample) }
    internal fun endPointer(result: ScrollExecutionResult = ScrollExecutionResult.COMPLETED) { pointerOwner?.endPointer(result) }
}
