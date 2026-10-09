package dev.autoscroll.nativeapp.mediapipe

/**
 * cameraFps counts frames delivered to our analyzer, not the sensor's production rate.
 * CameraX's KEEP_ONLY_LATEST drops are hidden and are not included in throttledFrames.
 * captureTimestampNs belongs to the camera clock; input/completion use elapsedRealtime.
 * inferenceMilliseconds includes preprocessing and synchronous MediaPipe inference.
 * Summaries cover at most the last 120 frames; percentiles use nearest-rank selection.
 */
data class InferenceMetrics(
    val cameraFps: Double = 0.0,
    val inferenceFps: Double = 0.0,
    val inferenceMilliseconds: Double = 0.0,
    val captureTimestampNs: Long = 0,
    val inputTimestampMs: Long = 0,
    val completionTimestampMs: Long = 0,
    val throttledFrames: Long = 0,
    val processedFrames: Long = 0,
    val staleFrames: Long = 0,
    val inferenceAverageMilliseconds: Double = 0.0,
    val inferenceP50Milliseconds: Double = 0.0,
    val inferenceP95Milliseconds: Double = 0.0,
    val inferenceSamples: Int = 0,
)

/** Owned by the inference worker; caller requests a summary at most once per second. */
internal class LatencyWindow {
    private val values = DoubleArray(120)
    private var next = 0
    private var count = 0

    data class Summary(val average: Double = 0.0, val p50: Double = 0.0,
        val p95: Double = 0.0, val samples: Int = 0)

    fun add(milliseconds: Double) {
        require(milliseconds.isFinite() && milliseconds >= 0)
        values[next] = milliseconds
        next = (next + 1) % values.size
        count = (count + 1).coerceAtMost(values.size)
    }

    fun summary(): Summary {
        if (count == 0) return Summary()
        val sorted = values.copyOf(count).sortedArray()
        return Summary(sorted.average(), sorted[(count * 50 + 99) / 100 - 1],
            sorted[(count * 95 + 99) / 100 - 1], count)
    }
}
