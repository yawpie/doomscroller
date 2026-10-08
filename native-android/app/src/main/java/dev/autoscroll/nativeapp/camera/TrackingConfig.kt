package dev.autoscroll.nativeapp.camera

data class TrackingConfig(
    val inferenceFps: Int = 15,
    val detectionConfidence: Float = .4f,
    val presenceConfidence: Float = .4f,
    val trackingConfidence: Float = .6f,
    val maximumInferenceDimension: Int = 320,
    val lostAfterMs: Long = 500,
    val analysisWidth: Int = 640,
    val analysisHeight: Int = 480,
) {
    init {
        require(inferenceFps in 1..30)
        require(listOf(detectionConfidence, presenceConfidence, trackingConfidence)
            .all { it.isFinite() && it in 0f..1f })
        require(maximumInferenceDimension in 192..1920)
        require(lostAfterMs > 0)
        require(analysisWidth in 1..1920 && analysisHeight in 1..1920)
    }

    val frameIntervalNs: Long get() = (1_000_000_000L + inferenceFps - 1) / inferenceFps
}
