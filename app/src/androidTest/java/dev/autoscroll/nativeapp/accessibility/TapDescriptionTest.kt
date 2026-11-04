package dev.autoscroll.nativeapp.accessibility

import android.graphics.PathMeasure
import android.graphics.RectF
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.scrolling.ScrollRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TapDescriptionTest {
    @Test fun doubleTapIsTwoStationarySequentialTouchesWithinAndroidTimeout() {
        val coordinates = swipeCoordinates(SwipeConfig(), SwipeBounds(0, 40, 1080, 1800), ScrollRequest(ScrollAction.DOUBLE_TAP))
        val gesture = screenGesture(coordinates, ScrollAction.DOUBLE_TAP)
        assertEquals(2, gesture.strokeCount)
        val first = gesture.getStroke(0)
        val second = gesture.getStroke(1)
        val gap = second.startTime - first.startTime - first.duration
        assertTrue(gap >= 40 && gap < ViewConfiguration.getDoubleTapTimeout())
        for (i in 0 until gesture.strokeCount) {
            val stroke = gesture.getStroke(i)
            assertEquals(0f, PathMeasure(stroke.path, false).length, 0f)
            assertTrue(stroke.duration in 1 until ViewConfiguration.getLongPressTimeout().toLong())
            val bounds = RectF().also { stroke.path.computeBounds(it, true) }
            assertEquals(540f, bounds.left, 0f)
            assertEquals(940f, bounds.top, 0f)
        }
    }

    @Test fun singleTapIsOneStationaryTouchAndSwipesStillHaveLength() {
        val bounds = SwipeBounds(0, 40, 1080, 1800)
        val tap = screenGesture(swipeCoordinates(SwipeConfig(), bounds, ScrollRequest(ScrollAction.SINGLE_TAP)), ScrollAction.SINGLE_TAP)
        assertEquals(1, tap.strokeCount)
        assertEquals(0f, PathMeasure(tap.getStroke(0).path, false).length, 0f)
        val swipe = screenGesture(swipeCoordinates(SwipeConfig(), bounds, ScrollRequest(ScrollAction.FORWARD)), ScrollAction.FORWARD)
        assertEquals(1, swipe.strokeCount)
        assertEquals(900f, PathMeasure(swipe.getStroke(0).path, false).length, 0f)
    }
}
