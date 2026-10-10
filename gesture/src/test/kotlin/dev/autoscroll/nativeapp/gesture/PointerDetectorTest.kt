package dev.autoscroll.nativeapp.gesture

import org.junit.Assert.*
import org.junit.Test

class PointerDetectorTest {
    private val area = PointerArea(.2, .2, .8, .8)
    private fun sample(t: Long, x: Double = .5, y: Double = .5, pinch: Boolean = false,
        aspect: Double = 1.0): HandTrackingResult {
        val points = (0..20).map { HandLandmark(it, x, (y + .24).coerceAtMost(1.0), 0.0) }.toMutableList()
        points[0] = HandLandmark(0, x, (y + .35).coerceAtMost(1.0), 0.0)
        points[5] = HandLandmark(5, x, (y + .24).coerceAtMost(1.0), 0.0)
        points[6] = HandLandmark(6, x, (y + .16).coerceAtMost(1.0), 0.0)
        points[7] = HandLandmark(7, x, (y + .08).coerceAtMost(1.0), 0.0)
        points[8] = HandLandmark(8, x, y, 0.0)
        points[9] = HandLandmark(9, (x + .06).coerceAtMost(1.0), (y + .18).coerceAtMost(1.0), 0.0)
        points[17] = HandLandmark(17, (x + .16).coerceAtMost(1.0), (y + .24).coerceAtMost(1.0), 0.0)
        points[4] = HandLandmark(4, if (pinch) x + .005 else (x + .15).coerceAtMost(1.0), y, 0.0)
        return HandTrackingResult(t, HandPose(points), aspect)
    }
    private fun detector() = PointerDetector(PointerConfig(smoothingAlpha = 1.0)).apply { calibrate(area) }
    private fun arm(d: PointerDetector, x: Double = .5, y: Double = .5) {
        for (t in 0L..300L step 60) d.process(sample(t, x, y), t)
        assertEquals(PointerPhase.TRACKING, d.state.phase)
    }
    private fun press(d: PointerDetector, start: Long = 360, x: Double = .5, y: Double = .5): PointerFeedback {
        d.process(sample(start, x, y, true), start)
        d.process(sample(start + 60, x, y, true), start + 60)
        return d.process(sample(start + 120, x, y, true), start + 120)
    }

    @Test fun absoluteMappingClampsCornersAndDoesNotMirrorAgain() {
        assertEquals(PointerPoint(0.0, 0.0), area.map(PointerPoint(.2, .2)))
        assertEquals(PointerPoint(1.0, 1.0), area.map(PointerPoint(.9, .9)))
        assertEquals(.5, area.map(PointerPoint(.5, .5)).x, 1e-9)
        assertEquals(.5, area.map(PointerPoint(.5, .5)).y, 1e-9)
        assertThrows(IllegalArgumentException::class.java) { PointerArea(.5, .2, .4, .8) }
        assertThrows(IllegalArgumentException::class.java) { PointerArea(.2, .2, .21, .8) }
    }
    @Test fun startupPinchWaitsForStableSeparatedPointing() {
        val d = detector()
        for (t in 0L..600L step 60) assertNull(d.process(sample(t, pinch = true), t).action)
        assertEquals(PointerPhase.WAITING_RELEASE, d.state.phase)
        for (t in 660L..960L step 60) d.process(sample(t), t)
        assertEquals(PointerPhase.TRACKING, d.state.phase)
        assertEquals(PointerAction.BEGIN, press(d, 1020).action)
    }
    @Test fun pinchNeedsThreeObservationsAsWellAsElapsedConfirmationTime() {
        val d = detector(); arm(d)
        assertNull(d.process(sample(360, pinch = true), 360).action)
        assertNull(d.process(sample(480, pinch = true), 480).action)
        assertEquals(PointerPhase.CONFIRMING, d.state.phase)
        assertEquals(PointerAction.BEGIN, d.process(sample(540, pinch = true), 540).action)
        assertThrows(IllegalArgumentException::class.java) { PointerConfig(minimumPinchSamples = 1) }
    }
    @Test fun oneBeginThenHoldAndOneEndWithoutAdditionalTap() {
        val d = detector(); arm(d)
        assertEquals(PointerAction.BEGIN, press(d).action)
        for (t in 540L..1740L step 60) {
            val held = d.process(sample(t, pinch = true), t)
            assertNull(held.action)
            assertEquals(PointerPhase.PRESSED, held.phase)
        }
        assertNull(d.process(sample(1800), 1800).action)
        assertEquals(PointerAction.END, d.process(sample(1920), 1920).action)
        assertNull(d.process(sample(1980), 1980).action)
    }
    @Test fun pinchClosureFreezesContactThenOffsetSupportsImmediateDrag() {
        val d = detector(); arm(d)
        assertEquals(PointerAction.BEGIN, press(d, x = .44).action)
        assertEquals(.5, d.state.position!!.x, 1e-9)
        val move = d.process(sample(540, x = .50, pinch = true), 540)
        assertEquals(PointerAction.MOVE, move.action)
        assertEquals(PointerPhase.DRAGGING, move.phase)
        assertEquals(.6, move.position!!.x, 1e-9)
        val release = d.process(sample(600, x = .60), 600)
        assertEquals(.6, release.position!!.x, 1e-9)
        assertEquals(.6, d.process(sample(720, x = .60), 720).position!!.x, 1e-9)
    }
    @Test fun heldJitterDoesNotDragButHoldCanBecomeDragLater() {
        val d = detector(); arm(d); press(d)
        val jitter = d.process(sample(540, x = .502, pinch = true), 540)
        assertEquals(PointerPhase.PRESSED, jitter.phase)
        assertEquals(.5, jitter.position!!.x, 1e-9)
        for (t in 600L..1200L step 60) d.process(sample(t, pinch = true), t)
        assertEquals(PointerPhase.DRAGGING, d.process(sample(1260, x = .56, pinch = true), 1260).phase)
    }
    @Test fun lossGapFutureAndTrackingJumpCancelContact() {
        for (bad in listOf(HandTrackingResult(540), sample(900, pinch = true), sample(540, x = .9, pinch = true))) {
            val d = detector(); arm(d); press(d)
            assertEquals(PointerAction.CANCEL, d.process(bad, bad.timestampMs).action)
            assertFalse(d.state.visible)
        }
        val d = detector(); arm(d); press(d)
        assertEquals(PointerAction.CANCEL, d.process(sample(900, pinch = true), 540).action)
    }
    @Test fun duplicateAndOldSamplesCannotReplayBeginOrMove() {
        val d = detector(); arm(d); press(d)
        assertNull(d.process(sample(480, pinch = true), 480).action)
        assertNull(d.process(sample(420, pinch = true), 540).action)
        assertEquals(PointerPhase.PRESSED, d.state.phase)
        assertEquals(PointerAction.CANCEL, d.process(sample(540, pinch = true), 1000).action)
    }
    @Test fun distancesRespectUprightAspectAndPalmSize() {
        val hand = sample(0).pose!!
        assertTrue(pointerPinchRatio(hand, 2.0) > pointerPinchRatio(hand, .5))
        assertTrue(pointerIndexExtended(hand, 2.0))
        assertThrows(IllegalArgumentException::class.java) { HandTrackingResult(0, hand, Double.NaN) }
    }
    @Test fun calibrationRequiresFreshStableSeparatedSamplesAndOrderedArea() {
        val c = PointerCalibration()
        assertFalse(c.capture(0))
        for (t in 0L..300L step 60) c.process(sample(t, .25, .25), t)
        assertTrue(c.capture(300)); assertNull(c.area)
        for (t in 360L..660L step 60) c.process(sample(t, .75, .65), t)
        assertTrue(c.capture(660)); assertEquals(PointerArea(.25, .25, .75, .65), c.area)
        c.reset(); for (t in 0L..300L step 60) c.process(sample(t, pinch = true), t)
        assertFalse(c.capture(300))
        c.reset(); for (t in 0L..300L step 60) c.process(sample(t, .25, .25), t)
        assertFalse(c.capture(1000))
    }
    @Test fun orientationChangeAndResetEmitCancellationBeforeDiscardingContact() {
        val d = detector(); arm(d); press(d)
        assertEquals(PointerAction.CANCEL, d.process(sample(540, pinch = true, aspect = 2.0), 540).action)
        assertEquals(PointerPhase.UNCALIBRATED, d.state.phase)
        d.calibrate(area); arm(d); press(d)
        assertEquals(PointerAction.CANCEL, d.reset().action)
    }
}
