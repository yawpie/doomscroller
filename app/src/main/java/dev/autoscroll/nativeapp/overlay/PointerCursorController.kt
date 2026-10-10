package dev.autoscroll.nativeapp.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import dev.autoscroll.nativeapp.accessibility.ScreenPoint
import dev.autoscroll.nativeapp.gesture.PointerPhase

/** A small, service-owned cursor. Its center is the future touch coordinate. */
internal class PointerCursorController(context: Context, private val onPosition: (ScreenPoint) -> Unit) : AutoCloseable {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val size = ((24 * context.resources.displayMetrics.density).toInt() / 2 * 2).coerceAtLeast(2)
    private var phase = PointerPhase.TRACKING
    private val root = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 3f; style = Paint.Style.STROKE }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val center = width / 2f
            paint.color = Color.BLACK; paint.strokeWidth = 5f
            canvas.drawCircle(center, center, center - 4, paint)
            paint.color = Color.WHITE; paint.strokeWidth = 2f
            canvas.drawCircle(center, center, center - 4, paint)
            canvas.drawLine(center - 6, center, center + 6, center, paint)
            canvas.drawLine(center, center - 6, center, center + 6, paint)
            if (phase == PointerPhase.CONFIRMING || phase == PointerPhase.PRESSED ||
                phase == PointerPhase.DRAGGING || phase == PointerPhase.RELEASING) {
                paint.style = Paint.Style.FILL
                paint.color = when (phase) {
                    PointerPhase.CONFIRMING -> Color.YELLOW
                    PointerPhase.DRAGGING -> Color.CYAN
                    PointerPhase.RELEASING -> Color.MAGENTA
                    else -> Color.GREEN
                }
                canvas.drawCircle(center, center, center / 3, paint)
                paint.style = Paint.Style.STROKE
            }
        }
    }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    @Suppress("DEPRECATION")
    private val params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        alpha = .6f
        if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    private var attached = false
    init {
        root.viewTreeObserver.addOnPreDrawListener {
            if (attached && root.visibility == View.VISIBLE) {
                val origin = IntArray(2)
                root.getLocationOnScreen(origin)
                onPosition(ScreenPoint(origin[0] + size / 2, origin[1] + size / 2))
            }
            true
        }
    }
    fun move(point: ScreenPoint, phase: PointerPhase = PointerPhase.TRACKING) {
        check(Looper.myLooper() == Looper.getMainLooper())
        this.phase = phase; root.invalidate()
        params.x = point.x - size / 2; params.y = point.y - size / 2
        root.visibility = View.VISIBLE
        if (!attached) { windows.addView(root, params); attached = true }
        else windows.updateViewLayout(root, params)
    }
    fun hide() { root.visibility = View.GONE }
    override fun close() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!attached) return
        attached = false; windows.removeViewImmediate(root)
    }
}
