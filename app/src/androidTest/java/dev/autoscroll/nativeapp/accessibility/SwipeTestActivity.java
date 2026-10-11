package dev.autoscroll.nativeapp.accessibility;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
    public static final int POINTER_EVENT = 6;
    public static final int LONG_PRESSED = 7;
    public static final int DROPPED = 8;

    private ResultReceiver receiver;
    private ScrollView scroll;
    private int taps;
    private int singles;
    private int doubles;
    private PointerSurface pointer;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra("finish", false)) { finish(); return; }
        receiver = Build.VERSION.SDK_INT >= 33
            ? getIntent().getParcelableExtra("receiver", ResultReceiver.class)
            : getIntent().getParcelableExtra("receiver");
        if (getIntent().getBooleanExtra("pointer", false)) {
            pointer = new PointerSurface(getIntent().getBooleanExtra("requiresHold", false));
            setContentView(pointer);
            return;
        }
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
        if (hasFocus && pointer != null) pointer.post(() -> {
            int[] origin = new int[2]; pointer.getLocationOnScreen(origin);
            Bundle data = new Bundle();
            data.putInt("left", origin[0]); data.putInt("top", origin[1]);
            data.putInt("width", pointer.getWidth()); data.putInt("height", pointer.getHeight());
            data.putInt("objectX", origin[0] + pointer.startX());
            data.putInt("objectY", origin[1] + pointer.startY());
            data.putInt("dropX", origin[0] + pointer.dropX());
            data.putInt("dropY", origin[1] + pointer.dropY());
            receiver.send(READY, data);
        });
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

    private class PointerSurface extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final GestureDetector detector;
        private final boolean requiresHold;
        private boolean candidate;
        private boolean held;
        private float objectX = -1;
        private float objectY = -1;
        private int drops;
        PointerSurface(boolean requiresHold) {
            super(SwipeTestActivity.this);
            this.requiresHold = requiresHold;
            setBackgroundColor(Color.WHITE);
            detector = new GestureDetector(SwipeTestActivity.this, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent event) { return true; }
                @Override public void onLongPress(MotionEvent event) {
                    if (candidate) { held = true; report(LONG_PRESSED, 1); invalidate(); }
                }
            });
        }
        int startX() { return getWidth() / 4; }
        int startY() { return getHeight() / 3; }
        int dropX() { return getWidth() * 3 / 4; }
        int dropY() { return getHeight() * 2 / 3; }
        private float radius() { return 48 * getResources().getDisplayMetrics().density; }
        @Override protected void onDraw(Canvas canvas) {
            if (objectX < 0) { objectX = startX(); objectY = startY(); }
            paint.setColor(Color.LTGRAY);
            canvas.drawCircle(dropX(), dropY(), radius(), paint);
            paint.setColor(held ? Color.GREEN : Color.BLUE);
            canvas.drawRect(objectX - radius()/2, objectY - radius()/2, objectX + radius()/2, objectY + radius()/2, paint);
            paint.setColor(Color.BLACK); paint.setTextSize(32);
            canvas.drawText(requiresHold ? "Hold, drag, then release in grey circle" : "Drag square into grey circle", 20, 50, paint);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            Bundle data = new Bundle();
            data.putInt("action", event.getActionMasked());
            data.putFloat("rawX", event.getRawX()); data.putFloat("rawY", event.getRawY());
            data.putLong("downTime", event.getDownTime()); data.putLong("eventTime", event.getEventTime());
            data.putInt("pointerId", event.getPointerId(0)); data.putInt("pointerCount", event.getPointerCount());
            receiver.send(POINTER_EVENT, data);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                held = false;
                candidate = Math.abs(event.getX() - objectX) <= radius()/2 && Math.abs(event.getY() - objectY) <= radius()/2;
            }
            detector.onTouchEvent(event);
            if (candidate && (!requiresHold || held) && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                objectX = event.getX(); objectY = event.getY(); invalidate();
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (candidate && (!requiresHold || held) && Math.hypot(objectX - dropX(), objectY - dropY()) <= radius()) report(DROPPED, ++drops);
                candidate = false;
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                candidate = false; held = false; objectX = startX(); objectY = startY(); invalidate();
            }
            return true;
        }
    }
}
