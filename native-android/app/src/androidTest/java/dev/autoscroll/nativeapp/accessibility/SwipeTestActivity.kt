package dev.autoscroll.nativeapp.accessibility

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A test-APK window reports its own scroll position; production accessibility never reads it. */
class SwipeTestActivity : Activity() {
    private var receiver: ResultReceiver? = null
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiver = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("receiver", ResultReceiver::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra("receiver")
        scroll = ScrollView(this)
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        repeat(150) { index ->
            rows.addView(TextView(this).apply {
                text = "Swipe validation row $index"
                textSize = 24f
                setPadding(24, 36, 24, 36)
            })
        }
        scroll.addView(rows)
        scroll.setOnScrollChangeListener { _, _, y, _, _ -> report(SCROLLED, y) }
        setContentView(scroll)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) report(READY, scroll.scrollY)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("finish", false)) finish()
    }

    private fun report(code: Int, offset: Int) {
        receiver?.send(code, Bundle().apply { putInt("offset", offset) })
    }

    companion object { const val READY = 0; const val SCROLLED = 1 }
}
