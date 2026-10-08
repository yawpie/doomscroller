package dev.autoscroll.nativeapp.scrolling

enum class ScrollAction { FORWARD, BACKWARD }

data class ScrollRequest(
    val action: ScrollAction,
    val distancePixels: Double? = null,
    val durationMs: Long? = null,
) {
    init {
        require(distancePixels == null || distancePixels.isFinite() && distancePixels > 0)
        require(durationMs == null || durationMs in 1..60_000)
    }
}

enum class ScrollExecutionResult {
    COMPLETED, CANCELLED, REJECTED, UNAVAILABLE, BUSY,
    TARGET_BLOCKED, SCREEN_LOCKED, USER_INTERACTION,
}

interface ScrollExecutor {
    val isReady: Boolean
    suspend fun execute(request: ScrollRequest): ScrollExecutionResult
}

data class ScrollActionState(
    val sessionActive: Boolean = false,
    val busy: Boolean = false,
    val lastAction: ScrollAction? = null,
    val lastResult: ScrollExecutionResult? = null,
    val rejectionReason: String? = null,
    val lastGestureToActionLatencyMs: Long? = null,
    val continuousPixelsPerSecond: Double = 0.0,
)
