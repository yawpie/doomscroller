package dev.autoscroll.nativeapp.session

import org.junit.Assert.*
import org.junit.Test

class SessionMachineTest {
    @Test fun idempotentStartAndStopWithStaleCallbacks() {
        val state = SessionMachine()
        val first = state.start()!!
        assertNull(state.start())
        state.stop()
        val second = state.start()!!
        assertFalse(state.ready(first))
        assertTrue(state.ready(second))
        assertTrue(state.accepts(second))
        state.stop()
        val stopped = state.generation
        state.stop()
        assertEquals(stopped, state.generation)
        assertFalse(state.accepts(second))
    }
    @Test fun pauseDuringInitializationAndActiveAreSafe() {
        val state = SessionMachine()
        val token = state.start()!!
        state.pause()
        assertEquals(GestureSessionState.Paused, state.state)
        assertFalse(state.ready(token))
        val next = state.start()!!
        state.ready(next)
        state.pause()
        val paused = state.generation
        state.pause()
        assertEquals(paused, state.generation)
        assertFalse(state.accepts(next))
    }
    @Test fun prerequisitesAndFailureInvalidateRunningSession() {
        val state = SessionMachine()
        assertNull(state.start("Camera permission required"))
        assertTrue(state.state is GestureSessionState.Error)
        val token = state.start()!!
        state.ready(token)
        state.fail("Accessibility disconnected")
        assertFalse(state.accepts(token))
        state.stop()
        assertEquals(GestureSessionState.Stopped, state.state)
    }
}
