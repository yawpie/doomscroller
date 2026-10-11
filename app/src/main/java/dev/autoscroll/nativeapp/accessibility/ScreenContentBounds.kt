package dev.autoscroll.nativeapp.accessibility

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager
import dev.autoscroll.nativeapp.gesture.PointerPoint
import kotlin.math.roundToInt

data class ScreenPoint(val x: Int, val y: Int)

internal fun pointerScreenPoint(point: PointerPoint, bounds: SwipeBounds) = ScreenPoint(
    bounds.left + (point.x.coerceIn(0.0, 1.0) * (bounds.width - 1)).roundToInt(),
    bounds.top + (point.y.coerceIn(0.0, 1.0) * (bounds.height - 1)).roundToInt())

/** Same absolute screen coordinates for cursor placement and injected gestures. */
internal fun screenContentBounds(context: Context, overlayInsets: WindowInsets? = null): SwipeBounds {
    val manager = context.getSystemService(WindowManager::class.java)
    if (Build.VERSION.SDK_INT >= 30) {
        val metrics = manager.maximumWindowMetrics
        val bounds = metrics.bounds
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return SwipeBounds(bounds.left + insets.left, bounds.top + insets.top,
            (bounds.width() - insets.left - insets.right).coerceAtLeast(1),
            (bounds.height() - insets.top - insets.bottom).coerceAtLeast(1))
    }
    @Suppress("DEPRECATION")
    val real = DisplayMetrics().also { manager.defaultDisplay.getRealMetrics(it) }
    fun dimension(name: String): Int {
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id == 0) 0 else context.resources.getDimensionPixelSize(id)
    }
    @Suppress("DEPRECATION")
    val usable = DisplayMetrics().also { manager.defaultDisplay.getMetrics(it) }
    @Suppress("DEPRECATION")
    var left = overlayInsets?.systemWindowInsetLeft ?: 0
    @Suppress("DEPRECATION")
    var top = maxOf(overlayInsets?.systemWindowInsetTop ?: 0, dimension("status_bar_height"))
    @Suppress("DEPRECATION")
    var right = overlayInsets?.systemWindowInsetRight ?: (real.widthPixels - usable.widthPixels).coerceAtLeast(0)
    @Suppress("DEPRECATION")
    var bottom = overlayInsets?.systemWindowInsetBottom ?: if (right > 0) 0 else dimension("navigation_bar_height")
    if (Build.VERSION.SDK_INT >= 28) overlayInsets?.displayCutout?.let {
        left = maxOf(left, it.safeInsetLeft); top = maxOf(top, it.safeInsetTop)
        right = maxOf(right, it.safeInsetRight); bottom = maxOf(bottom, it.safeInsetBottom)
    }
    return SwipeBounds(left, top, (real.widthPixels - left - right).coerceAtLeast(1),
        (real.heightPixels - top - bottom).coerceAtLeast(1))
}
