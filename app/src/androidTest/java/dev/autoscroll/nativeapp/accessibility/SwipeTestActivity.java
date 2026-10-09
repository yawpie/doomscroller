package dev.autoscroll.nativeapp.accessibility;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

// Runs in the test APK's own process, which does not include the app's Kotlin runtime.
public class SwipeTestActivity extends Activity {
    public static final int READY = 0;
    public static final int SCROLLED = 1;
    public static final int TAP_UP = 2;
    public static final int SINGLE_TAPPED = 3;
    public static final int DOUBLE_TAPPED = 4;
    public static final int DESTROYED = 5;

    private ResultReceiver receiver;
    private ScrollView scroll;
    private int taps;
    private int singles;
    private int doubles;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra("finish", false)) { finish(); return; }
        receiver = Build.VERSION.SDK_INT >= 33
            ? getIntent().getParcelableExtra("receiver", ResultReceiver.class)
            : getIntent().getParcelableExtra("receiver");
        scroll = new ScrollView(this);
        if (getIntent().getBooleanExtra("taps", false)) {
            TextView surface = new TextView(this);
            surface.setText("Tap validation: 0 single / 0 double / 0 touch-ups");
            surface.setTextSize(24f);
            GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent event) { return true; }
                @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                    singles++;
                    report(SINGLE_TAPPED, 0);
                    updateLabel(surface);
                    return true;
                }
                @Override public boolean onDoubleTap(MotionEvent event) {
                    doubles++;
                    report(DOUBLE_TAPPED, 0);
                    updateLabel(surface);
                    return true;
                }
            });
            surface.setOnTouchListener((view, event) -> {
                detector.onTouchEvent(event);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    report(TAP_UP, ++taps);
                    updateLabel(surface);
                }
                return true;
            });
            setContentView(surface);
            return;
        }
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (int index = 0; index < 150; index++) {
            TextView row = new TextView(this);
            row.setText("Swipe validation row " + index);
            row.setTextSize(24f);
            row.setPadding(24, 36, 24, 36);
            rows.addView(row);
        }
        scroll.addView(rows);
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> report(SCROLLED, y));
        setContentView(scroll);
    }

    private void updateLabel(TextView surface) {
        surface.setText("Tap validation: " + singles + " single / " + doubles + " double / " + taps + " touch-ups");
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && scroll != null) report(READY, scroll.getScrollY());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("finish", false)) finish();
    }

    @Override protected void onDestroy() {
        report(DESTROYED, 0);
        super.onDestroy();
    }

    private void report(int code, int offset) {
        if (receiver == null) return;
        Bundle data = new Bundle();
        data.putInt("offset", offset);
        receiver.send(code, data);
    }
}
