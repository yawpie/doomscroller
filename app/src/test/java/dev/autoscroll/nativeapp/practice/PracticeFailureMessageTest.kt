package dev.autoscroll.nativeapp.practice

import org.junit.Assert.*
import org.junit.Test

class PracticeFailureMessageTest {
    @Test fun cameraErrorsLeadWithRecoveryBeforeSpecificDiagnostic() {
        for (code in listOf(1, 4, 7)) {
            val message = practiceFailureMessage(IllegalStateException("Camera error $code"))
            assertTrue(message.startsWith("Practice could not use the camera."))
            assertTrue(message.contains("camera permission"))
            assertTrue(message.contains("Camera access in Android's privacy controls"))
            assertTrue(message.contains("Close other camera apps"))
            assertTrue(message.contains("press Start practice again"))
            assertTrue(message.endsWith("Details: Camera error $code"))
        }
    }

    @Test fun missingOrBlankDiagnosticStillHasCompleteRecovery() {
        for (error in listOf(Exception(), Exception(" "))) {
            val message = practiceFailureMessage(error)
            assertTrue(message.contains("press Start practice again"))
            assertFalse(message.contains("Details:"))
            assertFalse(message.contains("null"))
        }
    }
}
