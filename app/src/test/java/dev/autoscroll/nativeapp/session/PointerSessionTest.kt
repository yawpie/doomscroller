package dev.autoscroll.nativeapp.session

import dev.autoscroll.nativeapp.accessibility.SwipeBounds
import dev.autoscroll.nativeapp.accessibility.ScreenPoint
import dev.autoscroll.nativeapp.accessibility.pointerScreenPoint
import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

class PointerSessionTest {
    private val bounds = SwipeBounds(10, 30, 101, 201)
    @Test fun pointerEndpointsAreValidPixelsAndAlreadyMirroredCoordinatesAreNotMirroredAgain() {
        assertEquals(ScreenPoint(10, 30), pointerScreenPoint(PointerPoint(0.0, 0.0), bounds))
        assertEquals(ScreenPoint(110, 230), pointerScreenPoint(PointerPoint(1.0, 1.0), bounds))
        assertEquals(ScreenPoint(35, 130), pointerScreenPoint(PointerPoint(.25, .5), bounds))
    }
    @Test fun renderingFreshnessUsesSourceTimeWithExactDeadlineAndRejectsFutureSamples() {
        val sample = PointerFeedback(timestampMs = 100)
        assertTrue(pointerFeedbackFresh(sample, 100, 300))
        assertTrue(pointerFeedbackFresh(sample, 399, 300))
        assertFalse(pointerFeedbackFresh(sample, 400, 300))
        assertFalse(pointerFeedbackFresh(sample, 99, 300))
        assertFalse(pointerFeedbackFresh(sample, 600, 300))
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun visibleCursorExpiresAtSourceDeadlineWithoutAnyFurtherFrameOrRenderCallback() = runTest {
        var visible = true
        val expiry = schedulePointerExpiry(this, PointerFeedback(timestampMs = 100), 300,
            nowMs = { 250 + testScheduler.currentTime }, onExpired = { visible = false })
        runCurrent()
        advanceTimeBy(149); runCurrent()
        assertTrue("Arrival at250 must not restart the300ms source deadline", visible)
        advanceTimeBy(1); runCurrent()
        assertFalse(visible)
        assertTrue(expiry.isCompleted)
    }
    @Test fun recognitionOwnsExactlyOneModeAndPointerNeverEmitsScroll() {
        val sample = HandTrackingResult(100, null)
        val scroll = SessionRecognition(NativeSettings(), null, bounds)
        val pointer = SessionRecognition(NativeSettings(controlMode = ControlMode.POINTER), PointerArea(.1,.1,.9,.9), bounds)
        assertNotNull(scroll.gestures)
        assertNull(scroll.pointer)
        assertNotNull(scroll.process(sample, 100, bounds, true).scroll)
        assertNull(pointer.gestures)
        assertNotNull(pointer.pointer)
        val result = pointer.process(sample, 100, bounds, true)
        assertNull(result.scroll)
        assertNotNull(result.pointer)
    }
    @Test fun changedBoundsInvalidatesLiveCalibrationAndBlockedTargetHidesCursor() {
        val recognition = SessionRecognition(NativeSettings(controlMode = ControlMode.POINTER), PointerArea(.1,.1,.9,.9), bounds)
        assertEquals(PointerPhase.LOST, recognition.process(HandTrackingResult(100, null), 100, bounds, false).pointer!!.phase)
        assertEquals(PointerPhase.UNCALIBRATED,
            recognition.process(HandTrackingResult(200, null), 200, bounds.copy(width = 200), true).pointer!!.phase)
        assertNull(recognition.process(HandTrackingResult(300, null), 300, bounds, true).scroll)
    }
    @Test fun reversePortraitInvalidatesViewportEvenWhenSizeAndAspectStayTheSame() {
        val recognition = SessionRecognition(NativeSettings(controlMode = ControlMode.POINTER), PointerArea(.1,.1,.9,.9), bounds, 0)
        assertFalse(recognition.viewportChanged(bounds, 0))
        assertTrue(recognition.viewportChanged(bounds, 2))
        assertTrue(recognition.viewportChanged(bounds.copy(height = 300), 0))
    }
    @Test fun sessionCancelHidesFreshAcquiredPointerAndRequiresSeparatedSteadyHandAgain() {
        val recognition = SessionRecognition(NativeSettings(controlMode = ControlMode.POINTER), PointerArea(.1,.1,.9,.9), bounds)
        val landmarks = MutableList(21) { HandLandmark(it, .5, .6, 0.0) }.apply {
            this[0] = HandLandmark(0,.5,.8,0.0); this[9] = HandLandmark(9,.5,.5,0.0)
            this[5] = HandLandmark(5,.5,.65,0.0); this[6] = HandLandmark(6,.5,.5,0.0)
            this[8] = HandLandmark(8,.5,.35,0.0); this[4] = HandLandmark(4,.25,.5,0.0)
            this[17] = HandLandmark(17,.7,.65,0.0)
        }
        fun frame(time: Long) = HandTrackingResult(time, HandPose(landmarks))
        recognition.process(frame(0), 0, bounds, true)
        val acquired = recognition.process(frame(200), 200, bounds, true).pointer!!
        assertTrue(acquired.visible)
        assertFalse(recognition.cancel("Target changed")!!.visible)
        assertFalse(recognition.process(frame(250), 250, bounds, true).pointer!!.visible)
        assertTrue(recognition.process(frame(450), 450, bounds, true).pointer!!.visible)
        assertFalse(recognition.process(frame(500), 1000, bounds, true).pointer!!.visible)
    }
}

