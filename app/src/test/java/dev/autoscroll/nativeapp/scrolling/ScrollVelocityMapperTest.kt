package dev.autoscroll.nativeapp.scrolling

import dev.autoscroll.nativeapp.gesture.HandAction
import dev.autoscroll.nativeapp.gesture.HandMotion
import dev.autoscroll.nativeapp.gesture.ScrollConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollVelocityMapperTest {
    @Test fun preservesDartDirectionDeadZoneSensitivityAndClamping() {
        val mapper = ScrollVelocityMapper(ScrollConfig())
        assertEquals(0.0, mapper.map(HandMotion(HandAction.MOVE_DOWN, verticalVelocity = 0.025)), 0.0)
        assertEquals(0.0, mapper.map(HandMotion(HandAction.MOVE_DOWN, verticalVelocity = Double.NaN)), 0.0)
        assertEquals(0.0, mapper.map(HandMotion(HandAction.IDLE, verticalVelocity = 1.0)), 0.0)
        assertEquals(360.0, mapper.map(HandMotion(HandAction.MOVE_DOWN, verticalVelocity = 0.2)), 0.0)
        assertEquals(-360.0, mapper.map(HandMotion(HandAction.MOVE_UP, verticalVelocity = -0.2)), 0.0)
        mapper.configure(sensitivity = 9000.0, inverted = true)
        assertEquals(4000.0, mapper.sensitivity, 0.0)
        assertEquals(-1200.0, mapper.map(HandMotion(HandAction.MOVE_DOWN, verticalVelocity = 1.0)), 0.0)
        mapper.configure(sensitivity = 1.0)
        assertEquals(300.0, mapper.sensitivity, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteSensitivity() {
        ScrollVelocityMapper(ScrollConfig()).configure(sensitivity = Double.NaN)
    }
}
