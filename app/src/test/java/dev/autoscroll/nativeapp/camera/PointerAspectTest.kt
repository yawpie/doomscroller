package dev.autoscroll.nativeapp.camera

import dev.autoscroll.nativeapp.mediapipe.InferenceMetrics
import org.junit.Assert.*
import org.junit.Test

class PointerAspectTest {
    @Test fun deliveryRetainsUprightAspectForFreshAndExpiredFrames() {
        val result = CameraController.FrameResult(null, InferenceMetrics(inputTimestampMs = 100),
            uprightAspectRatio = .75)
        assertEquals(.75, result.resultAt(120, 300).uprightAspectRatio, 0.0)
        assertEquals(.75, result.resultAt(600, 300).uprightAspectRatio, 0.0)
        assertEquals(1.0, CameraController.FrameResult(null, InferenceMetrics()).resultAt(0, 300).uprightAspectRatio, 0.0)
    }
}
