package me.foxtails.lakehead.platform.feed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Binds nothing: binding evidence is slice 2's gate.
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
}
