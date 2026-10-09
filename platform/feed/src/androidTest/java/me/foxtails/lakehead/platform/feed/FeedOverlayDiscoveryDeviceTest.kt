package me.foxtails.lakehead.platform.feed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof for feed-overlay repair slice 1 (`docs/fixfeed.md` row 1,
 * `docs/agents/tasks/feed-overlay-repair.md`).
 *
 * Providers declare their overlay service with an `app`-scheme data filter, so only a
 * device can prove the host data URI (`FeedHostUri`) actually matches. Requires at
 * least one provider installed (verified on R5CN70E2SGV with Lawnfeed + Neo Feed).
 * Slice 2 adds per-provider bind runs: connected-proof (`bindingEvidence`), not
 * `bindService` acceptance alone, is the gate.
 */
@RunWith(AndroidJUnit4::class)
class FeedOverlayDiscoveryDeviceTest {

    @Test
    fun probeForNeoFeedReturnsOverlayService() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()

        val result = AndroidFeedServiceQuery(appContext).probe(
            NEO_FEED_PACKAGE,
            FEED_OVERLAY_ACTION,
        )

        assertEquals(
            listOf(
                FeedServiceComponent(
                    NEO_FEED_PACKAGE,
                    "com.saulhdev.feeder.manager.service.OverlayService",
                ),
            ),
            result.components,
        )
    }

    @Test
    fun resolverReturnsTargetOnDevice() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val resolver = FeedProviderResolver(AndroidFeedServiceQuery(appContext))

        val resolution = resolver.resolveDetailed()

        assertNotNull("expected a resolved target with a provider installed, got $resolution", resolution.target)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun gatewayReachesAvailableWithInstalledProvider() = runTest {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val gateway = FeedOverlayGatewayImpl(
            applicationContext = appContext,
            resolver = FeedProviderResolver(AndroidFeedServiceQuery(appContext)),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        gateway.start(this)
        advanceUntilIdle()
        val state = gateway.state.value
        gateway.stop()

        assertTrue("expected Available with a provider installed, was $state", state is FeedAvailability.Available)
    }

    @Test
    fun neoFeedAloneAcceptsAndConnects() = assertProviderConnects(
        FeedProviderId.NeoFeed,
        setOf(NEO_FEED_PACKAGE),
    )

    @Test
    fun lawnfeedAloneAcceptsAndConnects() = assertProviderConnects(
        FeedProviderId.Lawnfeed,
        setOf(LAWNFEED_PACKAGE, LAWNFEED_LEGACY_PACKAGE),
    )

    /**
     * Scopes discovery to [packages] (every other package answers confirmed-absent) so
     * exactly one provider is bound, then requires accepted + connected evidence. A
     * provider that rejects fails the test with its diagnostic and evidence attached —
     * nothing is skipped or swallowed.
     */
    private fun assertProviderConnects(provider: FeedProviderId, packages: Set<String>) = runBlocking {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val real = AndroidFeedServiceQuery(appContext)
        val scoped = FeedServiceQuery { packageName, action ->
            if (packageName in packages) {
                real.probe(packageName, action)
            } else {
                FeedServiceProbeResult.absent()
            }
        }
        val gateway = FeedOverlayGatewayImpl(
            applicationContext = appContext,
            resolver = FeedProviderResolver(scoped),
            ioDispatcher = Dispatchers.IO,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            gateway.start(scope)
            val settled = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                gateway.bindingEvidence.first { it.connected || gateway.diagnostic.value != null }
            }
            val evidence = gateway.bindingEvidence.value
            val diagnostic = gateway.diagnostic.value
            val report = "provider=$provider settled=$settled evidence=$evidence diagnostic=$diagnostic"
            assertEquals("rejected or unresolved: $report", null, diagnostic)
            assertEquals("target provider: $report", provider, evidence.target?.provider)
            assertTrue("bindService not accepted: $report", evidence.accepted)
            assertTrue("no binder connected: $report", evidence.connected)
        } finally {
            gateway.stop()
            scope.cancel()
        }
    }

    private companion object {
        const val BIND_TIMEOUT_MS = 10_000L
    }
}
