package dev.autoscroll.nativeapp.accessibility

import dev.autoscroll.nativeapp.scrolling.ScrollExecutionResult
import org.junit.Assert.*
import org.junit.Test

class PointerContactCoordinatorTest {
    private val bounds = SwipeBounds(0, 0, 1000, 1000)
    private fun sample(x: Int, y: Int = 200, time: Long = 1) = PointerDispatchSample(ScreenPoint(x, y), time, bounds, 0, "target")
    private class Harness {
        val sent = mutableListOf<PointerSegment>()
        val callbacks = mutableListOf<(ScrollExecutionResult) -> Unit>()
        var rejection: ScrollExecutionResult? = null
        var blocked = false
        var interactionBlocks = 0
        val contact = PointerContactCoordinator(guard = { _, _ ->
            if (blocked) ScrollExecutionResult.TARGET_BLOCKED else null
        }, dispatch = { segment, callback ->
            sent += segment
            if (rejection == null) callbacks += pointerResultOnce {
                if (it == ScrollExecutionResult.CANCELLED) interactionBlocks++
                callback(it)
            }
            rejection
        })
        fun complete(result: ScrollExecutionResult = ScrollExecutionResult.COMPLETED) { callbacks.removeAt(0)(result) }
    }

    @Test fun stationaryHoldDispatchesBeginOnceAndTerminalOnly() {
        val h = Harness()
        assertNull(h.contact.begin(sample(200)))
        h.complete()
        repeat(100) { h.contact.move(sample(200)) }
        assertEquals(1, h.sent.size)
        assertTrue(h.contact.state.active)
        h.contact.end()
        assertEquals(PointerSegmentKind.END, h.sent.last().kind)
        assertEquals(ScreenPoint(200, 200), h.sent.last().end)
        assertTrue(h.contact.state.active)
        h.complete()
        assertFalse(h.contact.state.active)
        h.contact.end()
        assertEquals(2, h.sent.size)
    }

    @Test fun directDragKeepsExactEndpointAndOneLatestPendingTarget() {
        val h = Harness()
        h.contact.begin(sample(200)); h.complete()
        h.contact.move(sample(300))
        h.contact.move(sample(400)); h.contact.move(sample(500))
        assertEquals(2, h.sent.size)
        assertEquals(ScreenPoint(200, 200), h.contact.state.position)
        h.complete()
        assertEquals(ScreenPoint(300, 200), h.sent.last().start)
        assertEquals(ScreenPoint(500, 200), h.sent.last().end)
        h.complete()
        assertEquals(ScreenPoint(500, 200), h.contact.state.position)
    }

    @Test fun releaseDuringMoveDropsPendingTargetAndUsesExecutedEndpoint() {
        val h = Harness()
        h.contact.begin(sample(200)); h.complete()
        h.contact.move(sample(300)); h.contact.move(sample(700))
        h.contact.end()
        h.contact.move(sample(900))
        assertTrue(h.contact.state.ending)
        h.complete()
        assertEquals(PointerSegmentKind.END, h.sent.last().kind)
        assertEquals(ScreenPoint(300, 200), h.sent.last().start)
        assertEquals(h.sent.last().start, h.sent.last().end)
        assertEquals(3, h.sent.size)
        assertEquals(ScrollExecutionResult.BUSY, h.contact.begin(sample(400)))
        h.complete()
        assertFalse(h.contact.state.active)
    }

    @Test fun terminalBypassesBlockedGuardEvenBeforeBeginCompletes() {
        val h = Harness()
        h.contact.begin(sample(200))
        h.blocked = true
        h.contact.end(ScrollExecutionResult.TARGET_BLOCKED)
        h.complete()
        assertEquals(PointerSegmentKind.END, h.sent.last().kind)
        h.complete()
        assertEquals(ScrollExecutionResult.TARGET_BLOCKED, h.contact.state.lastResult)
        assertFalse(h.contact.state.recoveryRequired)
    }

    @Test fun pendingMoveIsRevalidatedBeforeDispatchAndBlockedPathTerminates() {
        val h = Harness()
        h.contact.begin(sample(200)); h.complete()
        h.contact.move(sample(300)); h.contact.move(sample(500))
        h.blocked = true
        h.complete()
        assertEquals(PointerSegmentKind.END, h.sent.last().kind)
        assertEquals(ScreenPoint(300, 200), h.sent.last().end)
        assertEquals(3, h.sent.size)
    }

    @Test fun rejectedBeginDoesNotAcquireContactOrQueueRetry() {
        val h = Harness(); h.rejection = ScrollExecutionResult.REJECTED
        assertEquals(ScrollExecutionResult.REJECTED, h.contact.begin(sample(200)))
        assertFalse(h.contact.state.active)
        h.contact.move(sample(300))
        assertEquals(1, h.sent.size)
        assertFalse(h.contact.state.recoveryRequired)
    }

    @Test fun rejectedMovePreservesPreviousEndpointAndAttemptsTerminal() {
        val h = Harness()
        h.contact.begin(sample(200)); h.complete()
        h.rejection = ScrollExecutionResult.REJECTED
        h.contact.move(sample(300))
        assertEquals(listOf(PointerSegmentKind.BEGIN, PointerSegmentKind.MOVE, PointerSegmentKind.END), h.sent.map { it.kind })
        assertEquals(ScreenPoint(200, 200), h.sent.last().end)
        assertTrue(h.contact.state.recoveryRequired)
        assertEquals(ScrollExecutionResult.BUSY, h.contact.begin(sample(200)))
    }

    @Test fun acceptedCancellationReleasesOwnershipWithoutReplacementTap() {
        val h = Harness()
        h.contact.begin(sample(200)); h.complete()
        h.contact.move(sample(300)); h.complete(ScrollExecutionResult.CANCELLED)
        assertFalse(h.contact.state.active)
        assertFalse(h.contact.state.recoveryRequired)
        assertEquals(2, h.sent.size)
    }

    @Test fun missingCallbackAttemptsTerminalAndFailedTerminalRequiresAcknowledgment() {
        val h = Harness()
        h.contact.begin(sample(200))
        h.complete(ScrollExecutionResult.UNAVAILABLE)
        assertEquals(PointerSegmentKind.END, h.sent.last().kind)
        val late = h.callbacks.first()
        h.complete(ScrollExecutionResult.UNAVAILABLE)
        assertTrue(h.contact.state.recoveryRequired)
        assertFalse(h.contact.state.active)
        h.contact.acknowledgeRecovery()
        h.contact.begin(sample(700))
        late(ScrollExecutionResult.COMPLETED)
        assertEquals(ScreenPoint(700, 200), h.contact.state.position)
        assertTrue(h.contact.state.dispatching)
    }

    @Test fun blockedBeginDoesNotDispatch() {
        val h = Harness(); h.blocked = true
        assertEquals(ScrollExecutionResult.TARGET_BLOCKED, h.contact.begin(sample(200)))
        assertTrue(h.sent.isEmpty())
    }

    @Test fun lateCancellationAfterTimeoutRecoveryAndNewBeginCannotBlockNewContact() {
        val h = Harness()
        h.contact.begin(sample(200))
        val oldCallback = h.callbacks.first()
        h.complete(ScrollExecutionResult.UNAVAILABLE)
        h.complete(ScrollExecutionResult.UNAVAILABLE)
        h.contact.acknowledgeRecovery()
        h.contact.begin(sample(700))
        oldCallback(ScrollExecutionResult.CANCELLED)
        assertEquals(0, h.interactionBlocks)
        assertEquals(ScreenPoint(700, 200), h.contact.state.position)
        assertTrue(h.contact.state.dispatching)
    }

    @Test fun cancellationFromStateListenerBeforeBeginDoesNotSendAnyTouch() {
        val sent = mutableListOf<PointerSegment>()
        val callbacks = mutableListOf<(ScrollExecutionResult) -> Unit>()
        lateinit var contact: PointerContactCoordinator
        contact = PointerContactCoordinator(guard = { _, _ -> null }, dispatch = { segment, callback ->
            sent += segment; callbacks += callback; null
        }, onState = { if (it.active && !it.ending) contact.end() })
        assertEquals(ScrollExecutionResult.REJECTED, contact.begin(sample(200)))
        assertTrue(sent.isEmpty())
        assertTrue(callbacks.isEmpty())
        assertFalse(contact.state.active)
    }

    @Test fun cancellationFromStateListenerBeforeMoveEndsAtPreviousPoint() {
        val sent = mutableListOf<PointerSegment>()
        val callbacks = mutableListOf<(ScrollExecutionResult) -> Unit>()
        var cancel = false
        lateinit var contact: PointerContactCoordinator
        contact = PointerContactCoordinator(guard = { _, _ -> null }, dispatch = { segment, callback ->
            sent += segment; callbacks += callback; null
        }, onState = { if (cancel && it.active && !it.ending && it.dispatching) contact.end() })
        contact.begin(sample(200))
        callbacks.removeAt(0)(ScrollExecutionResult.COMPLETED)
        cancel = true
        contact.move(sample(700))
        assertEquals(listOf(PointerSegmentKind.BEGIN, PointerSegmentKind.END), sent.map { it.kind })
        assertEquals(ScreenPoint(200, 200), sent.last().end)
        callbacks.removeAt(0)(ScrollExecutionResult.COMPLETED)
        assertFalse(contact.state.active)
    }

    @Test fun rectangleCrossingBlocksWholePathIncludingBoundary() {
        val widget = SwipeBounds(300, 100, 100, 200)
        assertFalse(pointerPathAllowed(ScreenPoint(200, 200), ScreenPoint(500, 200), bounds, widget))
        assertFalse(pointerPathAllowed(ScreenPoint(200, 100), ScreenPoint(500, 100), bounds, widget))
        assertFalse(pointerPathAllowed(ScreenPoint(310, 200), ScreenPoint(310, 200), bounds, widget))
        assertTrue(pointerPathAllowed(ScreenPoint(200, 99), ScreenPoint(500, 99), bounds, widget))
        assertFalse(pointerPathAllowed(ScreenPoint(-1, 200), ScreenPoint(200, 200), bounds, null))
        assertFalse(pointerPathAllowed(ScreenPoint(200, 200), ScreenPoint(1000, 200), bounds, null))
        assertTrue(pointerPathAllowed(ScreenPoint(0, 0), ScreenPoint(999, 999), bounds, null))
    }
}
