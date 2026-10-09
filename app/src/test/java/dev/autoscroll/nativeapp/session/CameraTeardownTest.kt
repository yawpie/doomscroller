package dev.autoscroll.nativeapp.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CameraTeardownTest {
    @Test fun nextServiceWaitsForOldWorkerAndChecksItsGeneration() = runTest {
        val store = SessionStore()
        store.cameraReleased = CompletableDeferred()
        val token = store.machine.start()!!
        var initialized = false
        val pending = launch {
            store.cameraReleased.await()
            if (store.machine.generation == token && store.machine.state == GestureSessionState.Starting)
                initialized = true
        }
        runCurrent()
        assertFalse(initialized)
        store.machine.stop()
        store.cameraReleased.complete(Unit)
        pending.join()
        assertFalse(initialized)
        val next = store.machine.start()!!
        store.cameraReleased.await()
        assertTrue(store.machine.ready(next))
    }
}
