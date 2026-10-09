package dev.autoscroll.nativeapp.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.autoscroll.nativeapp.session.GestureSessionState
import kotlin.math.abs

/** Small native view owned by the service; no Activity or Compose lifecycle. */
class OverlayController(
    private val context: Context,
    private val onActivate: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onSettings: () -> Unit,
    private val onForward: () -> Unit,
    private val onBackward: () -> Unit,
    private val onInteraction: (Boolean) -> Unit,
) : AutoCloseable {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val preferences = context.getSharedPreferences("overlay_position", Context.MODE_PRIVATE)
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = GradientDrawable().apply { setColor(Color.rgb(238, 246, 232)); cornerRadius = dp(24).toFloat() }
        elevation = dp(6).toFloat()
    }
    private val bubble = TextView(context).apply {
        textSize = 18f
        setTextColor(Color.rgb(28, 64, 20))
        gravity = Gravity.CENTER
        minWidth = dp(48); minHeight = dp(48)
        isClickable = true
        contentDescription = "Hand Scroll status. Tap to expand, drag to move."
    }
    private val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val activate = button("Start / Resume", onActivate)
    private val pause = button("Pause", onPause)
    private val forward = button("Test forward", onForward)
    private val backward = button("Test backward", onBackward)
    @Suppress("DEPRECATION")
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = preferences.getInt("x", dp(12)); y = preferences.getInt("y", dp(100))
    }
    private var attached = false
    private var expanded = false

    init {
        root.addView(bubble)
        controls.addView(activate); controls.addView(pause)
        controls.addView(button("Stop", onStop)); controls.addView(button("Settings", onSettings))
        controls.addView(forward); controls.addView(backward)
        root.addView(controls)
        controls.visibility = View.GONE
        bubble.setOnClickListener {
            expanded = !expanded
            controls.visibility = if (expanded) View.VISIBLE else View.GONE
            root.post { reposition() }
        }
        var originX = 0; var originY = 0; var downX = 0f; var downY = 0f; var dragged = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        bubble.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onInteraction(true)
                    originX = params.x; originY = params.y
                    downX = event.rawX; downY = event.rawY; dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    dragged = dragged || abs(dx) > slop || abs(dy) > slop
                    if (dragged) { params.x = originX + dx.toInt(); params.y = originY + dy.toInt(); reposition() }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    onInteraction(false)
                    if (!dragged) view.performClick()
                    preferences.edit().putInt("x", params.x).putInt("y", params.y).apply()
                    true
                }
                MotionEvent.ACTION_CANCEL -> { onInteraction(false); true }
                else -> false
            }
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun button(label: String, action: () -> Unit) = Button(context).apply {
        text = label
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> onInteraction(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onInteraction(false)
            }
            false
        }
        setOnClickListener { action() }
    }

    fun show() {
        if (attached) return
        windows.addView(root, params)
        attached = true
        root.post { reposition() }
    }

    fun visible(visible: Boolean) { root.visibility = if (visible) View.VISIBLE else View.GONE }

    fun screenBounds(): Rect? {
        if (!attached || root.visibility != View.VISIBLE) return null
        val origin = IntArray(2)
        root.getLocationOnScreen(origin)
        return Rect(origin[0], origin[1], origin[0] + root.width, origin[1] + root.height)
    }

    fun update(state: GestureSessionState) {
        bubble.text = when (state) {
            GestureSessionState.Active -> "● Active"
            GestureSessionState.Starting -> "◌ Starting"
            GestureSessionState.Paused -> "Ⅱ Paused"
            GestureSessionState.Stopped -> "■ Stopped"
            is GestureSessionState.Error -> "! Error"
        }
        bubble.contentDescription = "${bubble.text}. Tap to expand, drag to move."
        activate.isEnabled = state != GestureSessionState.Active && state != GestureSessionState.Starting
        pause.isEnabled = state == GestureSessionState.Active || state == GestureSessionState.Starting
        forward.isEnabled = state == GestureSessionState.Active
        backward.isEnabled = state == GestureSessionState.Active
        root.post { reposition() }
    }

    fun reposition() {
        if (!attached) return
        val size = if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windows.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            Pair(metrics.bounds.width() - insets.left - insets.right, metrics.bounds.height() - insets.top - insets.bottom)
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windows.defaultDisplay.getMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
        params.x = params.x.coerceIn(0, (size.first - root.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (size.second - root.height).coerceAtLeast(0))
        windows.updateViewLayout(root, params)
    }

    override fun close() {
        if (!attached) return
        attached = false
        windows.removeViewImmediate(root)
    }
}
