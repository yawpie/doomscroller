package dev.autoscroll.nativeapp.accessibility

import org.junit.Assert.*
import org.junit.Test

class TargetWindowPolicyTest {
    @Test fun allowedToAllowedSwitchStillChangesTargetIdentity() {
        val policy = TargetWindowPolicy("own.app")
        policy.update("first.app")
        val first = policy.state
        assertTrue(first.allowed)
        policy.update("second.app")
        assertTrue(policy.allowed)
        assertNotEquals(first, policy.state)
        assertEquals("second.app", policy.state.packageName)
        policy.update("own.app")
        assertFalse(policy.state.allowed)
        policy.update(null)
        assertFalse(policy.allowed)
    }
}
