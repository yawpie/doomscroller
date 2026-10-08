package dev.autoscroll.nativeapp.mediapipe

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dev.autoscroll.nativeapp.camera.TrackingConfig
import dev.autoscroll.nativeapp.gesture.HandLandmark
import dev.autoscroll.nativeapp.gesture.HandPose

/** Owned exclusively by the CameraController worker, including construction and close. */
internal class HandLandmarkerEngine(context: Context, config: TrackingConfig) : AutoCloseable {
    private var lastTimestampMs = -1L
    private var closed = false
    private val tracker: HandLandmarker

    init {
        val model = "models/hand_landmarker.task"
        context.assets.open(model).use { check(it.read() != -1) { "Hand Landmarker model is empty" } }
        tracker = HandLandmarker.createFromOptions(context,
            HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(model).build())
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(1)
                .setMinHandDetectionConfidence(config.detectionConfidence)
                .setMinHandPresenceConfidence(config.presenceConfidence)
                .setMinTrackingConfidence(config.trackingConfidence)
                .build())
    }

    /** MPImage owns and recycles this bitmap when inference completes. */
    fun detect(uprightBitmap: Bitmap, timestampMs: Long): HandPose? {
        check(!closed) { "Hand landmarker is closed" }
        require(timestampMs >= 0 && timestampMs > lastTimestampMs) { "Timestamps must increase" }
        val image = BitmapImageBuilder(uprightBitmap).build()
        try {
            val detection = tracker.detectForVideo(image, timestampMs)
            lastTimestampMs = timestampMs
            val points = detection.landmarks().firstOrNull() ?: return null
            require(points.size == 21) { "Expected 21 hand landmarks" }
            val landmarks = points.mapIndexed { index, point ->
                val x = point.x().toDouble()
                val y = point.y().toDouble()
                val z = point.z().toDouble()
                require(x.isFinite() && y.isFinite() && z.isFinite()) { "Non-finite landmark" }
                // Match the original selfie coordinates; keep MediaPipe handedness unchanged.
                HandLandmark(index, (1.0 - x).coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0), z)
            }
            val category = detection.handedness().firstOrNull()?.firstOrNull()
            return HandPose(landmarks, category?.categoryName(), category?.score()?.toDouble())
        } finally {
            image.close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        tracker.close()
    }
}
