package me.foxtails.lakehead.platform.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the D4 [FeedProtocolTrace] contract: unconfirmed by default, monotonic
 * confirmations, set-once first failure with its stage derived from the status.
 */
class FeedProtocolTraceTest {

    @Test
    fun defaultsAreUnconfirmedWithoutFailure() {
        val trace = FeedProtocolTrace()

        assertFalse(trace.windowSignalSent)
        assertFalse(trace.sessionConfirmed)
        assertFalse(trace.contentConfirmed)
        assertNull(trace.firstFailure)
    }

    @Test
    fun windowSignalSentIsMonotonic() {
        val trace = FeedProtocolTrace().withWindowSignalSent()

        assertTrue(trace.windowSignalSent)
        assertFalse(trace.sessionConfirmed)
        assertFalse(trace.contentConfirmed)
    }

    @Test
    fun sessionConfirmedWithoutWindowSignalIsRepresentable() {
        // A provider callback observed without a recorded attach (e.g. a rebind
        // racing the trace read) still counts as a session: confirmation never
        // requires the earlier stage to have been recorded first.
        val trace = FeedProtocolTrace().withSessionConfirmed()

        assertFalse(trace.windowSignalSent)
        assertTrue(trace.sessionConfirmed)
    }

    @Test
    fun contentConfirmedWithoutSessionIsRepresentable() {
        val trace = FeedProtocolTrace().withContentConfirmed()

        assertTrue(trace.contentConfirmed)
        assertFalse(trace.sessionConfirmed)
    }

    @Test
    fun confirmationsAreIdempotent() {
        val trace = FeedProtocolTrace()
            .withWindowSignalSent()
            .withSessionConfirmed()
            .withContentConfirmed()

        assertEquals(trace, trace.withWindowSignalSent().withSessionConfirmed().withContentConfirmed())
    }

    @Test
    fun firstFailureIsSetOnce() {
        val negotiation = FeedDiagnostic(
            status = FeedDiagnosticStatus.NEGOTIATION_FAILED,
            cause = FeedDiagnosticCause.CONTROL_TRANSACT_FAILED,
        )
        val disconnect = FeedDiagnostic(
            status = FeedDiagnosticStatus.DISCONNECTED,
            cause = FeedDiagnosticCause.BINDER_DIED,
        )

        val trace = FeedProtocolTrace().withFirstFailure(negotiation)

        // The later transport collapse must not overwrite which stage failed first.
        assertEquals(negotiation, trace.withFirstFailure(disconnect).firstFailure)
    }

    @Test
    fun firstFailureStageDerivesFromStatus() {
        val session = FeedDiagnostic(
            status = FeedDiagnosticStatus.SURFACE_SESSION_FAILED,
            cause = FeedDiagnosticCause.WINDOW_TRANSACT_FAILED,
        )
        val render = FeedDiagnostic(
            status = FeedDiagnosticStatus.RENDERING_FAILED,
            cause = FeedDiagnosticCause.CONTENT_ABSENT,
        )

        assertEquals(
            FeedDiagnosticStage.SURFACE_SESSION,
            FeedProtocolTrace().withFirstFailure(session).firstFailure?.stage,
        )
        assertEquals(
            FeedDiagnosticStage.RENDERING,
            FeedProtocolTrace().withFirstFailure(render).firstFailure?.stage,
        )
    }
}
