package me.foxtails.lakehead.platform.feed

import android.content.ServiceConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * State-machine tests for [FeedOverlayGatewayImpl] with a scripted [FeedServiceQuery] and a
 * recording [FeedServiceConnector].
 *
 * No Android framework object is constructed here: the gateway's internal constructor takes the
 * connector and monitor seams, and binder death is driven through
 * [FeedOverlayGatewayImpl.onConnectionLost], the same path both `onServiceDisconnected` and
 * `onBindingDied` delegate to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedOverlayGatewayTest {

    @Test
    fun startResolvesOffMainAndEmitsAvailable() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))

        harness.gateway.start(this)
        advanceUntilIdle()

        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun packageRemovalEmitsUnavailableAndAutoDetaches() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        harness.gateway.attach(FeedProviderId.Lawnfeed)
        advanceUntilIdle()
        assertEquals(1, harness.connector.bindCalls.size)

        harness.installed = emptySet()
        harness.firePackageChange()
        advanceUntilIdle()

        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
            harness.gateway.state.value,
        )
        assertEquals(1, harness.connector.unbindCalls)
    }

    @Test
    fun doubleAttachKeepsSingleConnection() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)

        harness.gateway.attach(FeedProviderId.Lawnfeed)
        harness.gateway.attach(FeedProviderId.Lawnfeed)
        advanceUntilIdle()

        assertEquals(listOf(LAWNFEED_PACKAGE), harness.connector.bindCalls)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun switchingProviderUnbindsBeforeRebinding() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE, NEO_FEED_PACKAGE))
        harness.gateway.start(this)

        harness.gateway.attach(FeedProviderId.Lawnfeed)
        harness.installed = setOf(NEO_FEED_PACKAGE)
        harness.gateway.attach(FeedProviderId.NeoFeed)
        advanceUntilIdle()

        assertEquals(listOf(LAWNFEED_PACKAGE, NEO_FEED_PACKAGE), harness.connector.bindCalls)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun stopIsIdempotentAndUnbindsOnce() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        harness.gateway.attach(FeedProviderId.Lawnfeed)

        harness.gateway.stop()
        harness.gateway.stop()
        advanceUntilIdle()

        assertEquals(1, harness.connector.unbindCalls)
        // Detach without a connection is a no-op and never throws.
        harness.gateway.detach()
    }

    @Test
    fun stopWithoutStartIsNoOp() = runTest {
        val harness = Harness(this, installed = emptySet())

        harness.gateway.stop()
        advanceUntilIdle()

        assertEquals(0, harness.connector.unbindCalls)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
            harness.gateway.state.value,
        )
    }

    @Test
    fun bindingDiedEmitsUnavailable() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        harness.gateway.attach(FeedProviderId.Lawnfeed)
        advanceUntilIdle()

        harness.gateway.onConnectionLost()
        advanceUntilIdle()

        val state = harness.gateway.state.value
        assertTrue(state is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
    }

    @Test
    fun forwardingNeverThrowsWhenUnbound() = runTest {
        val harness = Harness(this, installed = emptySet())
        harness.gateway.start(this)

        harness.gateway.notifyOverlayVisible(true)
        harness.gateway.notifyOverlayProgress(2f)
        advanceUntilIdle()

        assertEquals(true, harness.gateway.overlayVisible)
        assertEquals(1f, harness.gateway.overlayProgress, 0f)
    }

    @Test
    fun startWithProviderAutoBinds() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))

        harness.gateway.start(this)
        advanceUntilIdle()

        assertEquals(listOf(LAWNFEED_PACKAGE), harness.connector.bindCalls)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun startWithoutProviderNoBind() = runTest {
        val harness = Harness(this, installed = emptySet())

        harness.gateway.start(this)
        advanceUntilIdle()

        assertTrue(harness.connector.bindCalls.isEmpty())
        assertEquals(0, harness.connector.unbindCalls)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
            harness.gateway.state.value,
        )
    }

    @Test
    fun attachWithoutProviderEmitsNoProviderWithoutBinding() = runTest {
        val harness = Harness(this, installed = emptySet())
        harness.gateway.start(this)
        advanceUntilIdle()

        harness.gateway.attach(FeedProviderId.Lawnfeed)
        advanceUntilIdle()

        assertTrue(harness.connector.bindCalls.isEmpty())
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
            harness.gateway.state.value,
        )
    }

    @Test
    fun attachBindRejectionSurfacesQueryFailed() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE), acceptBind = false)
        harness.gateway.start(this)
        advanceUntilIdle()

        harness.gateway.attach(FeedProviderId.Lawnfeed)
        advanceUntilIdle()

        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
    }

    @Test
    fun attachBindExceptionSurfacesQueryFailed() = runTest {
        val harness = Harness(
            this,
            installed = setOf(LAWNFEED_PACKAGE),
            bindFailure = BindFailure.QUERY,
        )
        harness.gateway.start(this)
        advanceUntilIdle()

        harness.gateway.attach(FeedProviderId.Lawnfeed)
        advanceUntilIdle()

        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
    }

    @Test
    fun packageAppearAutoBinds() = runTest {
        val harness = Harness(this, installed = emptySet())
        harness.gateway.start(this)
        advanceUntilIdle()
        assertTrue(harness.connector.bindCalls.isEmpty())

        harness.installed = setOf(LAWNFEED_PACKAGE)
        harness.firePackageChange()
        advanceUntilIdle()

        assertEquals(listOf(LAWNFEED_PACKAGE), harness.connector.bindCalls)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )

        // Same provider still bound: re-resolution is a bind no-op.
        harness.firePackageChange()
        advanceUntilIdle()
        assertEquals(listOf(LAWNFEED_PACKAGE), harness.connector.bindCalls)
    }

    @Test
    fun packageSwitchRebinds() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        assertEquals(listOf(LAWNFEED_PACKAGE), harness.connector.bindCalls)

        harness.installed = setOf(NEO_FEED_PACKAGE)
        harness.firePackageChange()
        advanceUntilIdle()

        assertEquals(listOf(LAWNFEED_PACKAGE, NEO_FEED_PACKAGE), harness.connector.bindCalls)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun bindRejectionSurfacesQueryFailed() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE), acceptBind = false)

        harness.gateway.start(this)
        advanceUntilIdle()

        assertEquals(
            listOf(LAWNFEED_PACKAGE, LAWNFEED_LEGACY_PACKAGE),
            harness.connector.bindCalls,
        )
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
    }

    @Test
    fun doubleStartKeepsSingleBind() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))

        harness.gateway.start(this)
        harness.gateway.start(this)
        advanceUntilIdle()

        assertEquals(listOf(LAWNFEED_PACKAGE), harness.connector.bindCalls)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
    }

    private class Harness(
        scope: TestScope,
        var installed: Set<String>,
        acceptBind: Boolean = true,
        bindFailure: BindFailure? = null,
    ) {
        val connector = RecordingConnector(acceptBind = acceptBind, bindFailure = bindFailure)
        var packageListener: (() -> Unit)? = null
        val monitor = object : FeedPackageMonitor {
            override fun start(onPackageChanged: () -> Unit) {
                packageListener = onPackageChanged
            }

            override fun stop() {
                packageListener = null
            }
        }

        val gateway = FeedOverlayGatewayImpl(
            resolver = FeedProviderResolver { packageName, _ -> packageName in installed },
            ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
            connector = connector,
            packageMonitor = monitor,
        )

        fun firePackageChange() {
            packageListener?.invoke()
        }
    }

    private enum class BindFailure {
        QUERY,
        SECURITY,
    }

    private class RecordingConnector(
        var acceptBind: Boolean = true,
        private val bindFailure: BindFailure? = null,
    ) : FeedServiceConnector {
        val bindCalls: MutableList<String> = mutableListOf()
        var unbindCalls: Int = 0
        var lastConnection: ServiceConnection? = null

        override fun bind(connection: ServiceConnection, packageName: String): Boolean {
            bindCalls += packageName
            lastConnection = connection
            when (bindFailure) {
                BindFailure.QUERY -> throw IllegalStateException("bind failed")
                BindFailure.SECURITY -> throw SecurityException("bind denied")
                null -> Unit
            }
            return acceptBind
        }

        override fun unbind(connection: ServiceConnection) {
            unbindCalls += 1
        }
    }
}
