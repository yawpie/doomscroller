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
}
