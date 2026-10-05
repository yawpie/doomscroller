package dev.autoscroll.autoscroll_compvis

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private var handTracking: HandTrackingChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        handTracking = HandTrackingChannel(applicationContext, flutterEngine.dartExecutor.binaryMessenger)
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        handTracking?.close()
        handTracking = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
