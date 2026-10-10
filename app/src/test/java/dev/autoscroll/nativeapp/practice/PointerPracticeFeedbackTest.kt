package dev.autoscroll.nativeapp.practice

import dev.autoscroll.nativeapp.gesture.*
import dev.autoscroll.nativeapp.settings.ControlMode
import dev.autoscroll.nativeapp.settings.NativeSettings
import org.junit.Assert.*
import org.junit.Test

class PointerPracticeFeedbackTest {
    private fun hand(t: Long, x: Double, y: Double, pinched: Boolean = false) = HandTrackingResult(t,
        HandPose((0..20).map { i ->
            val p = when (i) {
                0 -> x to (y + .35)
                5 -> x to (y + .24)
                6 -> x to (y + .16)
                7 -> x to (y + .08)
                8 -> x to y
                9 -> (x + .06) to (y + .18)
                17 -> (x + .16) to (y + .24)
                4 -> (x + if (pinched) .005 else .15) to y
                else -> x to (y + .24)
            }
            HandLandmark(i, p.first.coerceIn(0.0, 1.0), p.second.coerceIn(0.0, 1.0), 0.0)
        }))
    private fun practice(x: Double = .5, y: Double = .41): PointerPracticeFeedback {
        val p = PointerPracticeFeedback(PointerConfig(smoothingAlpha = 1.0))
        p.setBounds(300, 400, 48.0)
        p.detector.calibrate(PointerArea(.2, .2, .8, .8))
        for (t in 0L..300L step 60) p.process(hand(t, x, y), t)
        return p
    }
    @Test fun localTapAndHoldHitGridAndLossDoesNotCountTap() {
        val p = practice()
        for (t in 360L..480L step 60) p.process(hand(t, .5, .41, true), t)
        p.process(hand(540, .5, .41), 540)
        assertEquals(1, p.process(hand(660, .5, .41), 660).taps)
        assertEquals(0, p.state.misses)
        for (t in 720L..960L step 60) p.process(hand(t, .5, .41), t)
        for (t in 1020L..2100L step 60) p.process(hand(t, .5, .41, true), t)
        assertTrue(p.state.holdMs >= 600)
        p.process(hand(2160, .5, .41), 2160)
        assertEquals(1, p.process(hand(2280, .5, .41), 2280).holds)
        assertEquals(1, p.state.taps)
        for (t in 2340L..2580L step 60) p.process(hand(t, .5, .41), t)
        for (t in 2640L..2760L step 60) p.process(hand(t, .5, .41, true), t)
        p.process(HandTrackingResult(2820), 2820)
        assertEquals(1, p.state.taps)
        assertEquals("Touch cancelled", p.state.lastResult)
    }
    @Test fun dragMovesObjectAndDropCountsWithoutExtraTap() {
        val p = practice(.32, .698)
        for (t in 360L..480L step 60) p.process(hand(t, .32, .698, true), t)
        for ((t, x) in listOf(540L to .38, 600L to .46, 660L to .54, 720L to .62, 780L to .68))
            p.process(hand(t, x, .698, true), t)
        p.process(hand(840, .68, .698), 840)
        val dropped = p.process(hand(960, .68, .698), 960)
        assertEquals(1, dropped.drops)
        assertEquals(0, dropped.taps)
        assertEquals(.8, dropped.objectPosition.x, 1e-9)
        assertEquals("Drop hit", dropped.lastResult)
    }
    @Test fun centerHoldCrossingThresholdDuringReleaseConfirmationScoresHold() {
        val p = practice()
        for (t in 360L..1020L step 60) p.process(hand(t, .5, .41, true), t)
        assertEquals(540L, p.state.holdMs)
        p.process(hand(1050, .5, .41), 1050)
        val released = p.process(hand(1150, .5, .41), 1150)
        assertEquals(1, released.holds)
        assertEquals(0, released.taps)
        assertEquals("Hold hit", released.lastResult)
    }
    @Test fun pointerModeNeverChangesScrollSampleOrUsesScrollPoseActions() {
        val feedback = PracticeFeedback(NativeSettings(controlMode = ControlMode.POINTER))
        for (t in 0L..900L step 60) feedback.process(hand(t, .5, .4), t)
        assertEquals(2.0, feedback.state.position, 0.0)
        assertEquals(0, feedback.state.actionCount)
        assertTrue(feedback.state.videoPlaying)
        assertNotNull(feedback.pointer)
    }
    @Test fun recalibrationOrOrientationChangeCancelsLocalDragAndRestoresObject() {
        val p = practice(.32, .698)
        for (t in 360L..480L step 60) p.process(hand(t, .32, .698, true), t)
        p.process(hand(540, .40, .698, true), 540)
        val rotated = p.process(hand(600, .40, .698, true).copy(uprightAspectRatio = 2.0), 600)
        assertEquals("Touch cancelled", rotated.lastResult)
        assertEquals(PointerPoint(.2, .83), rotated.objectPosition)
        assertEquals(0, rotated.taps)
    }
    @Test fun orientationChangeAfterButtonCalibrationCancelsLocalContact() {
        val p = PointerPracticeFeedback(PointerConfig(smoothingAlpha = 1.0))
        p.setBounds(300, 400, 48.0)
        for (t in 0L..300L step 60) p.process(hand(t, .2, .2), t)
        assertTrue(p.capture(300))
        for (t in 360L..660L step 60) p.process(hand(t, .8, .8), t)
        assertTrue(p.capture(660))
        for (t in 720L..1020L step 60) p.process(hand(t, .5, .41), t)
        for (t in 1080L..1200L step 60) p.process(hand(t, .5, .41, true), t)
        assertEquals(PointerPhase.PRESSED, p.state.feedback.phase)
        val lost = p.process(hand(1260, .5, .41, true).copy(uprightAspectRatio = 2.0), 1260)
        assertEquals(PointerAction.CANCEL, lost.feedback.action)
        assertEquals("Touch cancelled", lost.lastResult)
        assertNull(lost.area)
    }
    @Test fun watchdogLossKeepsRealAspectAndCalibrationThenRequiresStableReacquire() {
        val local = PracticeFeedback(NativeSettings(controlMode = ControlMode.POINTER))
        val p = checkNotNull(local.pointer)
        for (t in 0L..300L step 60) local.process(hand(t, .2, .2).copy(uprightAspectRatio = .75), t)
        assertTrue(local.capturePointerCorner(300))
        for (t in 360L..660L step 60) local.process(hand(t, .8, .8).copy(uprightAspectRatio = .75), t)
        assertTrue(local.capturePointerCorner(660))
        val area = p.calibration.area
        for (t in 720L..1020L step 60) local.process(hand(t, .5, .41).copy(uprightAspectRatio = .75), t)
        for (t in 1080L..1200L step 60) local.process(hand(t, .5, .41, true).copy(uprightAspectRatio = .75), t)
        val lost = local.loseTracking(1260).pointer!!
        assertEquals(PointerAction.CANCEL, lost.feedback.action)
        assertEquals(area, p.calibration.area)
        for (t in 1320L..1620L step 60) local.process(hand(t, .5, .41).copy(uprightAspectRatio = .75), t)
        assertEquals(PointerPhase.TRACKING, p.state.feedback.phase)
    }
}
