package dev.autoscroll.nativeapp.accessibility

import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.scrolling.ScrollRequest

data class SwipeConfig(
    val startY: Double = 0.75,
    val endY: Double = 0.25,
    val x: Double = 0.5,
    val durationMs: Long = 220,
) {
    init {
        require(startY.isFinite() && endY.isFinite() && x.isFinite())
        require(endY > 0 && startY < 1 && startY > endY && x > 0 && x < 1)
        require(durationMs in 1..60_000)
    }
}

internal data class SwipeBounds(val left: Int, val top: Int, val width: Int, val height: Int) {
    init { require(width > 0 && height > 0) }
}

internal data class SwipeCoordinates(val x: Float, val startY: Float, val endY: Float, val durationMs: Long,
    val endX: Float = x)

internal fun swipeCoordinates(config: SwipeConfig, bounds: SwipeBounds, request: ScrollRequest): SwipeCoordinates {
    require(request.action != ScrollAction.BACK)
    if (request.action.isTap) {
        val x = bounds.left + bounds.width * 0.5f
        val y = bounds.top + bounds.height * 0.5f
        return SwipeCoordinates(x, y, y, 50)
    }
    if (request.action == ScrollAction.LEFT || request.action == ScrollAction.RIGHT) {
        val right = bounds.left + bounds.width * config.startY
        val left = bounds.left + bounds.width * config.endY
        val distance = (request.distancePixels ?: (right - left)).coerceAtMost(right - left)
        val towardRight = request.action == ScrollAction.RIGHT
        val start = if (towardRight) right else left
        val end = start + if (towardRight) -distance else distance
        val y = (bounds.top + bounds.height * config.x).toFloat()
        return SwipeCoordinates(start.toFloat(), y, y, request.durationMs ?: config.durationMs, end.toFloat())
    }
    val lower = bounds.top + bounds.height * config.startY
    val upper = bounds.top + bounds.height * config.endY
    val distance = (request.distancePixels ?: (lower - upper)).coerceAtMost(lower - upper)
    val forward = request.action == ScrollAction.FORWARD
    val start = if (forward) lower else upper
    val end = start + if (forward) -distance else distance
    return SwipeCoordinates((bounds.left + bounds.width * config.x).toFloat(), start.toFloat(),
        end.toFloat(), request.durationMs ?: config.durationMs)
}

internal fun avoidOverlay(coordinates: SwipeCoordinates, bounds: SwipeBounds, overlay: SwipeBounds?): SwipeCoordinates? {
    if (overlay != null && coordinates.x != coordinates.endX) {
        val top = overlay.top - 12
        val bottom = overlay.top + overlay.height + 12
        if (coordinates.startY < top || coordinates.startY > bottom ||
            maxOf(coordinates.x, coordinates.endX) < overlay.left - 12 ||
            minOf(coordinates.x, coordinates.endX) > overlay.left + overlay.width + 12) return coordinates
        val clearY = listOf(0.25f, 0.75f, 0.1f, 0.9f).map { bounds.top + bounds.height * it }
            .firstOrNull { it < top || it > bottom } ?: return null
        return coordinates.copy(startY = clearY, endY = clearY)
    }
    if (overlay == null || maxOf(coordinates.startY, coordinates.endY) < overlay.top ||
        minOf(coordinates.startY, coordinates.endY) > overlay.top + overlay.height) return coordinates
    val left = overlay.left - 12
    val right = overlay.left + overlay.width + 12
    if (coordinates.x < left || coordinates.x > right) return coordinates
    val clearX = listOf(0.25f, 0.75f, 0.1f, 0.9f).map { bounds.left + bounds.width * it }
        .firstOrNull { it < left || it > right } ?: return null
    return coordinates.copy(x = clearX, endX = clearX)
}
