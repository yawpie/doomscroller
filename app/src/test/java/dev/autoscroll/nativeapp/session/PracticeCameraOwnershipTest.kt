package dev.autoscroll.nativeapp.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PracticeCameraOwnershipTest {
    @Test fun enteringPracticeInvalidatesQueuedHomeStartEvenAfterRapidExit() {
        val store = SessionStore()
        val queued = store.requestStart()
        assertTrue(store.acceptsStart(queued))
        store.enterPractice()
        assertFalse(store.acceptsStart(queued))
        store.leavePractice()
        assertFalse("An older Home press cannot resume after practice", store.acceptsStart(queued))
        assertTrue(store.acceptsStart(store.requestStart()))
        assertFalse(store.acceptsStart(-1))
    }

    @Test fun waitsForServiceReleaseInitiationThenForItsActualWorkerTeardown() = runTest {
        val store = SessionStore()
        store.serviceRunning = true
        val oldToken = store.machine.start()!!
        store.machine.ready(oldToken)
        assertTrue(store.cameraReleased.isCompleted)
        store.enterPractice()
        assertEquals(GestureSessionState.Paused, store.state.value)
        assertFalse(store.machine.accepts(oldToken))
        var cameraCreated = false
        val waiting = launch { store.awaitPracticeCamera(); cameraCreated = true }
        runCurrent()
        assertFalse("Old completed deferred must not let practice overtake service pause", cameraCreated)
        store.cameraReleased = CompletableDeferred()
        store.practicePrepared()
        runCurrent()
        assertFalse(cameraCreated)
        store.cameraReleased.complete(Unit)
        waiting.join()
        assertTrue(cameraCreated)
    }

    @Test fun stoppedServiceStillWaitsForPreviousWorkerAndExitPreservesTeardown() = runTest {
        val store = SessionStore()
        store.cameraReleased = CompletableDeferred()
        store.enterPractice()
        var cameraCreated = false
        val waiting = launch { store.awaitPracticeCamera(); cameraCreated = true }
        runCurrent()
        assertFalse(cameraCreated)
        store.cameraReleased.complete(Unit)
        waiting.join()
        assertTrue(cameraCreated)
        val closingPracticeWorker = CompletableDeferred<Unit>()
        store.cameraReleased = closingPracticeWorker
        store.leavePractice()
        assertFalse(store.practiceOpen)
        assertSame(closingPracticeWorker, store.cameraReleased)
        assertFalse(store.cameraReleased.isCompleted)
    }

    @Test fun rapidExitAndReentryKeepUnacknowledgedServicePauseBarrier() = runTest {
        val store = SessionStore()
        store.serviceRunning = true
        store.enterPractice()
        store.leavePractice()
        store.enterPractice()
        var prepared = false
        val waiting = launch { store.awaitPracticeCamera(); prepared = true }
        runCurrent()
        assertFalse(prepared)
        // Service destruction acknowledges only after releaseSession has installed the teardown.
        store.cameraReleased = CompletableDeferred()
        store.serviceRunning = false
        store.practicePrepared()
        runCurrent()
        assertFalse(prepared)
        store.cameraReleased.complete(Unit)
        waiting.join()
        assertTrue(prepared)
    }
}
