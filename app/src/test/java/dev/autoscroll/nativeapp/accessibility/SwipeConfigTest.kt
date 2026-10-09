package dev.autoscroll.nativeapp.accessibility

import dev.autoscroll.nativeapp.scrolling.ScrollAction
import dev.autoscroll.nativeapp.scrolling.ScrollRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeConfigTest {
    private val bounds = SwipeBounds(10, 40, 400, 800)

    @Test fun tapsUseContentCenterWithoutMovementOrSwipeDuration() {
        for (action in listOf(ScrollAction.SINGLE_TAP, ScrollAction.DOUBLE_TAP)) {
            val coordinates = swipeCoordinates(SwipeConfig(x = 0.2, durationMs = 1000), bounds, ScrollRequest(action))
            assertEquals(210f, coordinates.x)
            assertEquals(440f, coordinates.startY)
            assertEquals(coordinates.startY, coordinates.endY)
            assertEquals(50L, coordinates.durationMs)
            val overlay = SwipeBounds(170, 400, 90, 80)
            val clear = avoidOverlay(coordinates, bounds, overlay)!!
            assertTrue(clear.x < overlay.left || clear.x > overlay.left + overlay.width)
            assertNull(avoidOverlay(coordinates, bounds, SwipeBounds(0, 400, 450, 80)))
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { ScrollRequest(action, 10.0) }
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { ScrollRequest(action, durationMs = 1000) }
        }
    }

    @Test fun downwardNavigationUsesAnUpwardScreenSwipe() {
        val forward = swipeCoordinates(SwipeConfig(), bounds, ScrollRequest(ScrollAction.FORWARD))
        assertEquals(210f, forward.x)
        assertEquals(640f, forward.startY)
        assertEquals(240f, forward.endY)
        assertEquals(220, forward.durationMs)
        val backward = swipeCoordinates(SwipeConfig(), bounds, ScrollRequest(ScrollAction.BACKWARD))
        assertEquals(forward.startY, backward.endY)
        assertEquals(forward.endY, backward.startY)
    }

    @Test fun shortSwipePreservesPixelDistanceAndClampsToConfiguredBounds() {
        val short = swipeCoordinates(SwipeConfig(), bounds, ScrollRequest(ScrollAction.FORWARD, 24.0, 100))
        assertEquals(24f, short.startY - short.endY)
        assertEquals(100, short.durationMs)
        val clamped = swipeCoordinates(SwipeConfig(), bounds, ScrollRequest(ScrollAction.BACKWARD, 2000.0))
        assertEquals(400f, clamped.endY - clamped.startY)
    }

    @Test fun invalidCoordinatesDurationAndRequestDistanceAreRejected() {
        listOf<() -> Any>(
            { SwipeConfig(startY = Double.NaN) }, { SwipeConfig(endY = 0.8) },
            { SwipeConfig(x = 0.0) }, { SwipeConfig(durationMs = 0) },
            { ScrollRequest(ScrollAction.FORWARD, Double.POSITIVE_INFINITY) },
            { ScrollRequest(ScrollAction.FORWARD, -1.0) },
        ).forEach { build ->
            try { build(); throw AssertionError("Invalid swipe accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun overlayIntersectingSwipeGetsAClearColumnOrRejectsWithoutTouchingControls() {
        val coordinates = swipeCoordinates(SwipeConfig(), bounds, ScrollRequest(ScrollAction.FORWARD))
        val centerWidget = SwipeBounds(170, 300, 90, 150)
        val adjusted = avoidOverlay(coordinates, bounds, centerWidget)!!
        assertTrue(adjusted.x < centerWidget.left || adjusted.x > centerWidget.left + centerWidget.width)
        assertEquals(coordinates.startY, adjusted.startY)
        assertEquals(coordinates.endY, adjusted.endY)
        assertEquals(coordinates, avoidOverlay(coordinates, bounds, SwipeBounds(170, 700, 90, 80)))
        assertNull(avoidOverlay(coordinates, bounds, SwipeBounds(0, 300, 450, 150)))
    }

    @Test fun targetPolicyAllowsExternalAppWithoutInspectingItsContent() {
        val policy = TargetWindowPolicy("dev.autoscroll.nativeapp")
        assertFalse(policy.allowed)
        listOf(null, "dev.autoscroll.nativeapp", "android", "com.android.systemui", "com.android.settings",
            "com.google.android.permissioncontroller", "com.android.packageinstaller", "com.android.launcher3",
            "com.google.android.inputmethod.latin").forEach {
            policy.update(it)
            assertFalse("Unexpected target: $it", policy.allowed)
        }
        policy.update("com.google.android.youtube")
        assertTrue(policy.allowed)
        policy.update("dev.autoscroll.nativeapp.test")
        assertTrue(policy.allowed)
    }
}
