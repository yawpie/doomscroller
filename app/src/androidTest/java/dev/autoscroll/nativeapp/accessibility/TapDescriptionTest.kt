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
    @Test fun horizontalActionsProduceOneHorizontalStrokeInTheSelectedDirection() {
        val bounds = SwipeBounds(10, 40, 400, 800)
        for (action in listOf(ScrollAction.LEFT, ScrollAction.RIGHT)) {
            val gesture = screenGesture(swipeCoordinates(SwipeConfig(), bounds, ScrollRequest(action)), action)
            assertEquals(1, gesture.strokeCount)
            val path = PathMeasure(gesture.getStroke(0).path, false)
            assertEquals(200f, path.length, 0f)
            val start = FloatArray(2)
            val end = FloatArray(2)
            assertTrue(path.getPosTan(0f, start, null))
            assertTrue(path.getPosTan(path.length, end, null))
            assertEquals(440f, start[1], 0f)
            assertEquals(start[1], end[1], 0f)
            assertEquals(action == ScrollAction.LEFT, end[0] > start[0])
        }
    }

    @Test fun backCannotBeTurnedIntoATouchGesture() {
        assertThrows(IllegalArgumentException::class.java) {
            screenGesture(SwipeCoordinates(100f, 200f, 300f, 100), ScrollAction.BACK)
        }
    }

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
