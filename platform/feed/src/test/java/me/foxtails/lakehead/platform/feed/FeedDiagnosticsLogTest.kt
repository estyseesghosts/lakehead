package me.foxtails.lakehead.platform.feed

import android.app.Activity
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the diagnostics log lines, the setprop gate spellings and the transition logging. */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedDiagnosticsLogTest {

    private val neoTarget = FeedProviderTarget(
        FeedProviderId.NeoFeed,
        FeedServiceComponent(NEO_FEED_PACKAGE, "com.saulhdev.feeder.manager.service.OverlayService"),
    )

    @Test
    fun stateLinesNameProviderOrReason() {
        assertEquals(
            "state Available provider=NeoFeed",
            formatFeedState(FeedAvailability.Available(FeedProviderId.NeoFeed)),
        )
        assertEquals(
            "state Unavailable reason=NO_PROVIDER",
            formatFeedState(
                FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
            ),
        )
    }

    @Test
    fun diagnosticLineCarriesStatusStageCauseAndSubject() {
        assertEquals("diagnostic none", formatFeedDiagnostic(null))
        assertEquals(
            "diagnostic status=BIND_FAILED stage=BIND cause=NULL_BINDING " +
                "provider=Lawnfeed package=$LAWNFEED_PACKAGE",
            formatFeedDiagnostic(
                FeedDiagnostic(
                    status = FeedDiagnosticStatus.BIND_FAILED,
                    cause = FeedDiagnosticCause.NULL_BINDING,
                    provider = FeedProviderId.Lawnfeed,
                    packageName = LAWNFEED_PACKAGE,
                ),
            ),
        )
        assertEquals(
            "diagnostic status=NO_PROVIDER stage=DISCOVERY cause=ALL_ABSENT provider=- package=-",
            formatFeedDiagnostic(
                FeedDiagnostic(FeedDiagnosticStatus.NO_PROVIDER, FeedDiagnosticCause.ALL_ABSENT),
            ),
        )
    }

    @Test
    fun bindingLineSeparatesAcceptedFromConnected() {
        assertEquals(
            "binding provider=- attempted=false resolvability=- accepted=false " +
                "connected=false active=false epoch=0",
            formatFeedBinding(FeedBindingEvidence()),
        )
        assertEquals(
            "binding provider=NeoFeed attempted=true resolvability=RESOLVABLE accepted=true " +
                "connected=false active=false epoch=3",
            formatFeedBinding(
                FeedBindingEvidence(
                    target = neoTarget,
                    attempted = true,
                    resolvability = FeedTargetResolvability.RESOLVABLE,
                    accepted = true,
                    epoch = 3,
                ),
            ),
        )
    }

    @Test
    fun traceLineReportsUnobservedStagesAsFalse() {
        assertEquals(
            "trace windowSignalSent=false sessionConfirmed=false contentConfirmed=false " +
                "firstFailure=none",
            formatFeedTrace(FeedProtocolTrace()),
        )
        assertEquals(
            "trace windowSignalSent=true sessionConfirmed=false contentConfirmed=false " +
                "firstFailure=SURFACE_SESSION_FAILED/WINDOW_TRANSACT_FAILED",
            formatFeedTrace(
                FeedProtocolTrace(windowSignalSent = true).withFirstFailure(
                    FeedDiagnostic(
                        FeedDiagnosticStatus.SURFACE_SESSION_FAILED,
                        FeedDiagnosticCause.WINDOW_TRANSACT_FAILED,
                    ),
                ),
            ),
        )
    }

    @Test
    fun propertyGateAcceptsOnlyOneAndTrue() {
        assertTrue(isFeedDiagnosticsEnabled("1"))
        assertTrue(isFeedDiagnosticsEnabled("true"))
        assertTrue(isFeedDiagnosticsEnabled("TRUE"))
        assertFalse(isFeedDiagnosticsEnabled(null))
        assertFalse(isFeedDiagnosticsEnabled(""))
        assertFalse(isFeedDiagnosticsEnabled("0"))
        assertFalse(isFeedDiagnosticsEnabled("yes"))
    }

    @Test
    fun transitionsOfEveryStreamAreLoggedInOrderWithoutRepeats() {
        val gateway = FakeGateway()
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        gateway.logDiagnosticTransitions(scope) { lines += it }

        assertEquals(
            listOf(
                "state Unavailable reason=NO_PROVIDER",
                "diagnostic none",
                "binding provider=- attempted=false resolvability=- accepted=false " +
                    "connected=false active=false epoch=0",
                "trace windowSignalSent=false sessionConfirmed=false contentConfirmed=false " +
                    "firstFailure=none",
            ),
            lines.toList(),
        )
        lines.clear()

        gateway.evidence.value = FeedBindingEvidence(
            target = neoTarget,
            attempted = true,
            resolvability = FeedTargetResolvability.RESOLVABLE,
            accepted = true,
            connected = true,
            active = true,
            epoch = 1,
        )
        gateway.evidence.value = gateway.evidence.value.copy()
        gateway.trace.value = FeedProtocolTrace(windowSignalSent = true)
        gateway.availability.value = FeedAvailability.Available(FeedProviderId.NeoFeed)

        assertEquals(
            listOf(
                "binding provider=NeoFeed attempted=true resolvability=RESOLVABLE accepted=true " +
                    "connected=true active=true epoch=1",
                "trace windowSignalSent=true sessionConfirmed=false contentConfirmed=false " +
                    "firstFailure=none",
                "state Available provider=NeoFeed",
            ),
            lines.toList(),
        )
        scope.cancel()
    }

    @Test
    fun throwingSinkNeverStopsLaterTransitions() {
        val gateway = FakeGateway()
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        var fail = true
        gateway.logDiagnosticTransitions(scope) { line ->
            if (fail) throw IllegalStateException("sink")
            lines += line
        }
        fail = false

        gateway.availability.value = FeedAvailability.Available(FeedProviderId.Lawnfeed)

        assertEquals(listOf("state Available provider=Lawnfeed"), lines.toList())
        scope.cancel()
    }

    private class FakeGateway : FeedOverlayGateway {
        val availability = MutableStateFlow<FeedAvailability>(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
        )
        val diagnosticFlow = MutableStateFlow<FeedDiagnostic?>(null)
        val evidence = MutableStateFlow(FeedBindingEvidence())
        val trace = MutableStateFlow(FeedProtocolTrace())

        override val state: StateFlow<FeedAvailability> = availability
        override val diagnostic: StateFlow<FeedDiagnostic?> = diagnosticFlow
        override val bindingEvidence: StateFlow<FeedBindingEvidence> = evidence
        override val protocolTrace: StateFlow<FeedProtocolTrace> = trace

        override fun start(scope: CoroutineScope) = Unit
        override fun stop() = Unit
        override fun attach(provider: FeedProviderId) = Unit
        override fun detach() = Unit
        override fun notifyOverlayVisible(visible: Boolean) = Unit
        override fun notifyOverlayProgress(progress: Float) = Unit
        override fun notifyWindowAttached(activity: Activity) = Unit
        override fun notifyWindowDetached(isChangingConfigurations: Boolean) = Unit
    }
}
