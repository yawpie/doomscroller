package dev.autoscroll.nativeapp.session

import dev.autoscroll.nativeapp.gesture.PointerArea
import dev.autoscroll.nativeapp.accessibility.SwipeBounds
import org.junit.Assert.*
import org.junit.Test

class PointerCalibrationHandoffTest {
    @Test fun enteringPracticeClearsAnyOlderPendingCalibration() {
        val store = SessionStore()
        store.preparePointerCalibration(PointerArea(.1, .1, .9, .9), SwipeBounds(0, 30, 100, 200), 0)
        store.enterPractice()
        assertNull("A fresh practice cannot leave an older calibration available to Start", store.pointerCalibration)
    }

    @Test fun onlyOneExplicitStartCanConsumeCalibrationAndBoundsChangeDiscardsIt() {
        val store = SessionStore()
        val area = PointerArea(.1, .1, .9, .9)
        val bounds = SwipeBounds(0, 30, 1080, 2200)
        store.preparePointerCalibration(area, bounds, 0)
        assertTrue(store.pointerCalibrationReady.value)
        assertEquals(area, store.takePointerCalibration(bounds, 0))
        assertNull(store.takePointerCalibration(bounds, 0))
        assertFalse(store.pointerCalibrationReady.value)
        store.preparePointerCalibration(area, bounds, 0)
        assertNull(store.takePointerCalibration(bounds.copy(height = 2100), 0))
        store.preparePointerCalibration(area, bounds, 0)
        assertNull(store.takePointerCalibration(bounds, 2))
    }

    @Test fun pauseStopSettingsAndNewProcessCannotKeepCalibration() {
        val store = SessionStore()
        store.preparePointerCalibration(PointerArea(.1, .1, .9, .9), SwipeBounds(0, 30, 100, 200), 0)
        store.invalidatePointerCalibration()
        assertNull(store.pointerCalibration)
        assertFalse(store.pointerCalibrationReady.value)
        assertNull(SessionStore().pointerCalibration)
    }
}
