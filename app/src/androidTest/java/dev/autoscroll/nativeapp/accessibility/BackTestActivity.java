package dev.autoscroll.nativeapp.accessibility;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

// The fixture runs in the separate test APK process without the app's Kotlin runtime.
public class BackTestActivity extends Activity {
    public static final int READY = 0;
    public static final int BACK_TO_LIST = 1;
    public static final int ROOT_BACK = 2;
    public static final int DESTROYED = 3;
    public static final int SCROLLED = 4;
    private ResultReceiver receiver;
    private boolean detail = true;

    @Override @SuppressWarnings("deprecation") protected void onCreate(Bundle state) {
        super.onCreate(state);
        receiver = Build.VERSION.SDK_INT >= 33
            ? getIntent().getParcelableExtra("receiver", ResultReceiver.class)
            : getIntent().getParcelableExtra("receiver");
        TextView page = new TextView(this);
        page.setText("External detail");
        page.setTextSize(24);
        setContentView(page);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) report(READY, 0);
    }

    @Override @SuppressWarnings("deprecation") public void onBackPressed() {
        if (!detail) {
            report(ROOT_BACK, 0);
            return;
        }
        detail = false;
        ScrollView list = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 100; i++) {
            TextView row = new TextView(this);
            row.setText("External list item " + i);
            row.setTextSize(24);
            row.setPadding(24, 36, 24, 36);
            rows.addView(row);
        }
        list.addView(rows);
        list.setOnScrollChangeListener((view, x, y, oldX, oldY) -> report(SCROLLED, y));
        setContentView(list);
        report(BACK_TO_LIST, 0);
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
