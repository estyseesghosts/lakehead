package me.foxtails.lakehead.platform.feed

import android.content.ServiceConnection
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * State-machine tests for [FeedOverlayGatewayImpl] with a scripted [FeedServiceQuery], a
 * recording [FeedServiceConnector] that captures the exact [FeedProviderTarget], a scripted
 * [FeedTargetPreflight] and a fake [FeedOverlayConnection].
 *
 * No Android framework object is constructed here: the gateway's internal constructor takes the
 * connector, monitor and preflight seams, binder death is driven through
 * [FeedOverlayGatewayImpl.onConnectionLost] (the same path `onServiceDisconnected` delegates
 * to) and epoch-scoped death through `onBindingDiedForEpoch`/`onServiceDisconnectedForEpoch`
 * (the same paths `onBindingDied`/`onServiceDisconnected` delegate to), null binding through
 * [FeedOverlayGatewayImpl.onNullBindingReceived], unexpected components through
 * [FeedOverlayGatewayImpl.onUnexpectedComponent] and direct `onOverlayConnected` package/class
 * mismatches (the same verdicts the production `OverlayConnection` reports before any
 * [AidlFeedBridge] is built), and connects are driven through
 * [FeedOverlayGatewayImpl.onOverlayConnected] with a fake channel (the same path the
 * production `onServiceConnected` installs its [AidlFeedBridge] through after exact
 * package + class verification).
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

        // Exactly one bind for the exact resolved target — no retry over the legacy
        // package once a target is selected.
        assertEquals(
            listOf(LAWNFEED_PACKAGE),
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

    @Test
    fun lateBindCatchesUpCachedVisibleAndProgress() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()

        // Host requests arrive before any channel is connected: cached, nothing delivered.
        harness.gateway.notifyOverlayVisible(true)
        harness.gateway.notifyOverlayProgress(0.75f)
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)

        assertEquals(listOf(true), channel.visibles)
        assertEquals(listOf(0.75f), channel.progresses)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun liveChannelClampsProgressAndMapsNanToZero() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)

        harness.gateway.notifyOverlayProgress(2f)
        harness.gateway.notifyOverlayProgress(Float.NaN)
        harness.gateway.notifyOverlayProgress(-0.5f)

        // Leading 0f is the connect-time catch-up of the default cache.
        assertEquals(listOf(0f, 1f, 0f, 0f), channel.progresses)
        assertEquals(0f, harness.gateway.overlayProgress, 0f)
    }

    @Test
    fun connectionDeathClosesChannelAndUnbindsOnce() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        harness.gateway.notifyOverlayVisible(true)

        harness.gateway.onConnectionLost()
        advanceUntilIdle()

        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, channel.closeCalls)

        // Death is terminal for this connection: a second loss reports no second unbind,
        // and later host requests reach no channel.
        val delivered = channel.visibles.size
        harness.gateway.onConnectionLost()
        harness.gateway.notifyOverlayVisible(false)
        advanceUntilIdle()

        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, channel.closeCalls)
        assertEquals(delivered, channel.visibles.size)
    }

    @Test
    fun packageSwitchClosesOldChannelAndCatchesUpNew() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        harness.gateway.notifyOverlayVisible(true)
        harness.gateway.notifyOverlayProgress(0.25f)
        val oldChannel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(oldChannel)

        harness.installed = setOf(NEO_FEED_PACKAGE)
        harness.firePackageChange()
        advanceUntilIdle()

        assertEquals(listOf(LAWNFEED_PACKAGE, NEO_FEED_PACKAGE), harness.connector.bindCalls)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, oldChannel.closeCalls)

        // The retained host cache is re-delivered to the replacement channel.
        val newChannel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(newChannel)

        assertEquals(listOf(true), newChannel.visibles)
        assertEquals(listOf(0.25f), newChannel.progresses)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun staleConnectAfterUnbindIsClosedAndDropped() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        harness.gateway.detach()

        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)

        // No bind survives the detach, so the stale channel is closed, never installed.
        assertEquals(1, channel.closeCalls)
        harness.gateway.notifyOverlayVisible(true)
        assertTrue(channel.visibles.isEmpty())
    }

    @Test
    fun lossDuringCatchUpTearsDownWithoutLaterDelivery() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()

        harness.gateway.notifyOverlayVisible(true)
        harness.gateway.notifyOverlayProgress(0.5f)
        val lossy = LossyOverlayConnection(harness.gateway)
        harness.gateway.onOverlayConnected(lossy)

        // Synchronous loss during catch-up still tears down: Unavailable, one unbind,
        // dead channel closed exactly once.
        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, lossy.closeCalls)

        // No later delivery reaches the dead channel.
        val visibles = lossy.visibles.size
        val progresses = lossy.progresses.size
        harness.gateway.notifyOverlayVisible(false)
        harness.gateway.notifyOverlayProgress(0.9f)
        advanceUntilIdle()

        assertEquals(visibles, lossy.visibles.size)
        assertEquals(progresses, lossy.progresses.size)
        assertEquals(1, lossy.closeCalls)
        assertEquals(1, harness.connector.unbindCalls)
        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
    }

    @Test
    fun staleConnectAfterReplacementBindIsDropped() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val staleEpoch = harness.gateway.bindEpoch

        harness.installed = setOf(NEO_FEED_PACKAGE)
        harness.firePackageChange()
        advanceUntilIdle()

        assertEquals(listOf(LAWNFEED_PACKAGE, NEO_FEED_PACKAGE), harness.connector.bindCalls)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )

        // Delayed callback from the previous generation (old provider/package) is closed
        // and dropped: no second unbind, no delivery, current availability untouched.
        val staleChannel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(
            staleChannel,
            staleEpoch,
            LAWNFEED_PACKAGE,
        )

        assertEquals(1, staleChannel.closeCalls)
        assertTrue(staleChannel.visibles.isEmpty())
        assertTrue(staleChannel.progresses.isEmpty())
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )

        // The current generation still connects and catches up (default false cache,
        // then the live notification).
        val freshChannel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(freshChannel)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )
        harness.gateway.notifyOverlayVisible(true)
        assertEquals(listOf(false, true), freshChannel.visibles)
    }

    @Test
    fun pendingLossForCurrentEpochSuppressesAvailable() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val epoch = harness.gateway.bindEpoch

        // Simulate a loss-after-check handoff already pending for this epoch: the
        // connect must consume it and tear down instead of publishing Available.
        pendingLosses(harness)[epoch] = FeedOverlayGatewayImpl.PendingFailure(
            status = FeedDiagnosticStatus.DISCONNECTED,
            cause = FeedDiagnosticCause.BINDER_DIED,
        )
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        advanceUntilIdle()

        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, channel.closeCalls)
    }

    @Test
    fun stalePendingLossDoesNotBlockFreshPublish() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val epoch = harness.gateway.bindEpoch

        // A stale generation's deferred mark must never suppress the replacement's
        // Available: the take is scoped to the connecting epoch.
        pendingLosses(harness)[epoch - 1] = FeedOverlayGatewayImpl.PendingFailure(
            status = FeedDiagnosticStatus.DISCONNECTED,
            cause = FeedDiagnosticCause.BINDER_DIED,
        )
        pendingLosses(harness)[epoch + 10_000L] = FeedOverlayGatewayImpl.PendingFailure(
            status = FeedDiagnosticStatus.DISCONNECTED,
            cause = FeedDiagnosticCause.BINDER_DIED,
        )
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        advanceUntilIdle()

        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
        assertEquals(0, harness.connector.unbindCalls)
        assertEquals(0, channel.closeCalls)
    }

    @Test
    fun contendedLossVsNonConnectHolderIsDrained() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )

        // Simulate a loss contending against a non-connect holder (e.g. re-resolution
        // holding the mutex): hold the lock, report loss, verify no immediate overwrite,
        // then release and let the background drain tear down.
        val mutex = gatewayMutex(harness)
        assertTrue(mutex.tryLock())
        try {
            harness.gateway.onConnectionLost()
            // Deferred, not yet torn down: still Available until the drain runs.
            assertEquals(
                FeedAvailability.Available(FeedProviderId.Lawnfeed),
                harness.gateway.state.value,
            )
        } finally {
            mutex.unlock()
        }
        advanceUntilIdle()

        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, channel.closeCalls)

        // Dead bridge stays cleared: later host requests reach no channel.
        val delivered = channel.visibles.size
        harness.gateway.notifyOverlayVisible(true)
        advanceUntilIdle()
        assertEquals(delivered, channel.visibles.size)
        assertEquals(1, harness.connector.unbindCalls)
    }

    @Test
    fun staleEpochContendedLossIsDroppedVsReplacement() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val staleEpoch = harness.gateway.bindEpoch

        harness.installed = setOf(NEO_FEED_PACKAGE)
        harness.firePackageChange()
        advanceUntilIdle()
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )
        val freshChannel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(freshChannel)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )
        val unbindsBefore = harness.connector.unbindCalls

        // Stale callback invoked after the replacement completed: dropped at the
        // first epoch check, never reaching the mark/revalidate path. This pins
        // first-check staleness only — it does not interleave a mark across the
        // replacement (the mark/revalidate window needs a pause between the epoch
        // check and the set insert, which this sequential invocation cannot hit).
        val mutex = gatewayMutex(harness)
        assertTrue(mutex.tryLock())
        try {
            invokeBridgeLoss(harness, staleEpoch)
            // No immediate overwrite of the replacement's Available.
            assertEquals(
                FeedAvailability.Available(FeedProviderId.NeoFeed),
                harness.gateway.state.value,
            )
        } finally {
            mutex.unlock()
        }
        advanceUntilIdle()

        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )
        assertEquals(unbindsBefore, harness.connector.unbindCalls)
        assertEquals(0, freshChannel.closeCalls)
        harness.gateway.notifyOverlayVisible(true)
        assertEquals(listOf(false, true), freshChannel.visibles)
    }

    @Test
    fun preBridgeContendedLossIsDrainedNotDropped() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        // Bind is active (Available from tryBindLocked) but no bridge published yet:
        // the dead-binder window where AidlFeedBridge reports loss during
        // construction, before onOverlayConnected installs the channel.
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )

        val mutex = gatewayMutex(harness)
        assertTrue(mutex.tryLock())
        try {
            // Contended pre-bridge loss: with the lock held the loss cannot tear
            // down directly and must defer via the pending handoff.
            harness.gateway.onConnectionLost()
            assertEquals(
                FeedAvailability.Available(FeedProviderId.Lawnfeed),
                harness.gateway.state.value,
            )
        } finally {
            mutex.unlock()
        }
        advanceUntilIdle()

        // The deferred pre-bridge mark is drained: dead bind torn down, not left
        // Available with no consumer.
        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
    }

    @Test
    fun longContendedLossStillDrainedAfterOldBoundedWindow() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )

        // Hold the lock past the old 10 x 1ms bounded window while the drain backs
        // off on virtual time: an unbounded handoff must still tear down once the
        // holder releases, where a bounded drain would have abandoned the epoch.
        val mutex = gatewayMutex(harness)
        assertTrue(mutex.tryLock())
        try {
            harness.gateway.onConnectionLost()
            advanceTimeBy(50)
            // Still deferred while the holder owns the lock.
            assertEquals(
                FeedAvailability.Available(FeedProviderId.Lawnfeed),
                harness.gateway.state.value,
            )
        } finally {
            mutex.unlock()
        }
        advanceUntilIdle()

        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, channel.closeCalls)
    }

    @Test
    fun asyncLossDuringCatchUpIsTornDown() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()

        harness.gateway.notifyOverlayVisible(true)
        harness.gateway.notifyOverlayProgress(0.5f)
        // Async complement to the synchronous re-entry test: loss arrives on a
        // background thread while the connect still holds its lock (contended
        // handoff), not via same-thread re-entry from the transact. The loss lands
        // during catch-up delivery, i.e. before the connect's first take-check, so
        // this pins the loss-before-first-take window only — not the narrower
        // publish-then-loss window between the first take and the second take
        // (covered by the double take-check in onOverlayConnected, not by timing
        // here).
        val async = AsyncLossyOverlayConnection(harness.gateway)
        harness.gateway.onOverlayConnected(async)
        assertTrue(async.lossDelivered(5, TimeUnit.SECONDS))
        advanceUntilIdle()

        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, async.closeCalls)

        val visibles = async.visibles.size
        val progresses = async.progresses.size
        harness.gateway.notifyOverlayVisible(false)
        harness.gateway.notifyOverlayProgress(0.9f)
        advanceUntilIdle()

        assertEquals(visibles, async.visibles.size)
        assertEquals(progresses, async.progresses.size)
        assertEquals(1, harness.connector.unbindCalls)
        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
    }

    private class Harness(
        scope: TestScope,
        var installed: Set<String>,
        acceptBind: Boolean = true,
        bindFailure: BindFailure? = null,
        preflightVerdict: FeedTargetResolvability = FeedTargetResolvability.RESOLVABLE,
        preflightFailure: PreflightFailure? = null,
    ) {
        val connector = RecordingConnector(acceptBind = acceptBind, bindFailure = bindFailure)
        val preflight = ScriptedPreflight(default = preflightVerdict, failure = preflightFailure)
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
            resolver = FeedProviderResolver { packageName, _ ->
                if (packageName in installed) {
                    FeedServiceProbeResult.present(
                        FeedServiceComponent(packageName, "$packageName.FeedService"),
                    )
                } else {
                    FeedServiceProbeResult.absent()
                }
            },
            ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
            connector = connector,
            packageMonitor = monitor,
            preflight = preflight,
        )

        fun firePackageChange() {
            packageListener?.invoke()
        }

        fun lawnfeedTarget(): FeedProviderTarget = FeedProviderTarget(
            FeedProviderId.Lawnfeed,
            FeedServiceComponent(LAWNFEED_PACKAGE, "$LAWNFEED_PACKAGE.FeedService"),
        )

        fun legacyTarget(): FeedProviderTarget = FeedProviderTarget(
            FeedProviderId.Lawnfeed,
            FeedServiceComponent(LAWNFEED_LEGACY_PACKAGE, "$LAWNFEED_LEGACY_PACKAGE.FeedService"),
        )

        fun neoTarget(): FeedProviderTarget = FeedProviderTarget(
            FeedProviderId.NeoFeed,
            FeedServiceComponent(NEO_FEED_PACKAGE, "$NEO_FEED_PACKAGE.FeedService"),
        )
    }

    private enum class BindFailure {
        QUERY,
        SECURITY,
    }

    private enum class PreflightFailure {
        QUERY,
        SECURITY,
    }

    /**
     * Scripted [FeedTargetPreflight] that records every verified target.
     *
     * [failure] throws instead of answering (with redacted-detail-hostile messages, so
     * tests can prove the gateway drops the detail); otherwise answers [default].
     */
    private class ScriptedPreflight(
        var default: FeedTargetResolvability = FeedTargetResolvability.RESOLVABLE,
        var failure: PreflightFailure? = null,
    ) : FeedTargetPreflight {
        val verifiedTargets: MutableList<FeedProviderTarget> = mutableListOf()

        override fun verify(target: FeedProviderTarget): FeedTargetResolvability {
            verifiedTargets += target
            when (failure) {
                PreflightFailure.QUERY -> throw IllegalStateException("preflight blowup 7x9q")
                PreflightFailure.SECURITY -> throw SecurityException("preflight denied s3cr3t")
                null -> Unit
            }
            return default
        }
    }

    private class RecordingConnector(
        var acceptBind: Boolean = true,
        private val bindFailure: BindFailure? = null,
    ) : FeedServiceConnector {
        /** Exact targets attempted, in order — proves exact-component forwarding. */
        val bindTargets: MutableList<FeedProviderTarget> = mutableListOf()

        /** Package names attempted, in order — coarse view of [bindTargets]. */
        val bindCalls: MutableList<String> = mutableListOf()
        var unbindCalls: Int = 0
        var lastConnection: ServiceConnection? = null

        override fun bind(connection: ServiceConnection, target: FeedProviderTarget): Boolean {
            bindTargets += target
            bindCalls += target.component.packageName
            lastConnection = connection
            when (bindFailure) {
                BindFailure.QUERY -> throw IllegalStateException("bind failed 8y2w")
                BindFailure.SECURITY -> throw SecurityException("bind denied z1x9k")
                null -> Unit
            }
            return acceptBind
        }

        override fun unbind(connection: ServiceConnection) {
            unbindCalls += 1
        }
    }

    /**
     * Framework-free stand-in for [AidlFeedBridge]: records every verb the gateway forwards
     * so catch-up, clamping and teardown tests pin delivery without touching `IBinder`.
     */
    private class FakeOverlayConnection : FeedOverlayConnection {
        val visibles: MutableList<Boolean> = mutableListOf()
        val progresses: MutableList<Float> = mutableListOf()
        var closeCalls: Int = 0

        override fun setVisible(visible: Boolean) {
            visibles += visible
        }

        override fun setProgress(progress: Float) {
            progresses += progress
        }

        override fun close() {
            closeCalls += 1
        }
    }

    /**
     * Reports transport loss synchronously from catch-up delivery, modeling an
     * [AidlFeedBridge] whose first transact fails while [FeedOverlayGatewayImpl] still
     * holds its lock: each verb re-enters [FeedOverlayGatewayImpl.onConnectionLost] on the
     * same thread, contending the mutex the connect path holds.
     */
    private class LossyOverlayConnection(
        private val gateway: FeedOverlayGatewayImpl,
    ) : FeedOverlayConnection {
        val visibles: MutableList<Boolean> = mutableListOf()
        val progresses: MutableList<Float> = mutableListOf()
        var closeCalls: Int = 0

        override fun setVisible(visible: Boolean) {
            gateway.onConnectionLost()
        }

        override fun setProgress(progress: Float) {
            gateway.onConnectionLost()
        }

        override fun close() {
            closeCalls += 1
        }
    }

    /**
     * Async complement to [LossyOverlayConnection]: the first transact fails on a
     * background thread while the connect still holds its lock, so the loss contends
     * the mutex from another thread (loss-before-first-take window) instead of re-entering
     * synchronously. `setProgress` holds the connect thread briefly so the background
     * mark reliably lands while the lock is held (contended handoff). The narrower
     * publish-then-loss window between the two take-checks is covered by the double
     * take-check in onOverlayConnected, not timing-pinned here.
     */
    private class AsyncLossyOverlayConnection(
        private val gateway: FeedOverlayGatewayImpl,
    ) : FeedOverlayConnection {
        val visibles: MutableList<Boolean> = mutableListOf()
        val progresses: MutableList<Float> = mutableListOf()
        var closeCalls: Int = 0
        private val lossDone = CountDownLatch(1)

        override fun setVisible(visible: Boolean) {
            Thread {
                try {
                    gateway.onConnectionLost()
                } finally {
                    lossDone.countDown()
                }
            }.start()
        }

        override fun setProgress(progress: Float) {
            try {
                // Hold the connect's lock long enough for the background loss above to
                // contend it (async handoff); short enough to keep the unit test fast.
                Thread.sleep(150)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        override fun close() {
            closeCalls += 1
        }

        fun lossDelivered(timeout: Long, unit: TimeUnit): Boolean {
            return try {
                lossDone.await(timeout, unit)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }
    }

    private fun gatewayMutex(harness: Harness): Mutex {
        val field = FeedOverlayGatewayImpl::class.java.getDeclaredField("mutex")
        field.isAccessible = true
        return field.get(harness.gateway) as Mutex
    }

    @Suppress("UNCHECKED_CAST")
    private fun pendingLosses(
        harness: Harness,
    ): ConcurrentHashMap<Long, FeedOverlayGatewayImpl.PendingFailure> {
        val field = FeedOverlayGatewayImpl::class.java.getDeclaredField("pendingLosses")
        field.isAccessible = true
        return field.get(harness.gateway) as ConcurrentHashMap<Long, FeedOverlayGatewayImpl.PendingFailure>
    }

    private fun invokeBridgeLoss(harness: Harness, epoch: Long) {
        val method = FeedOverlayGatewayImpl::class.java.getDeclaredMethod(
            "onBridgeLoss",
            Long::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(harness.gateway, epoch)
    }

    // D3 binding/binder lifecycle: exact-target preflight + bind + binder correlation
    // with binding evidence and safe-cause diagnostics. No framework objects are
    // constructed: mismatches ride the gateway's package/class string seam (the same
    // verdicts OverlayConnection reports before building any bridge), and the
    // ComponentName/IBinder extraction inside OverlayConnection stays device-verified.

    @Test
    fun bindForwardsExactCurrentComponent() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()

        val expected = harness.lawnfeedTarget()
        assertEquals(listOf(expected), harness.connector.bindTargets)
        assertEquals(listOf(expected), harness.preflight.verifiedTargets)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
        // Accepted, not connected: bind true is acceptance, never proof of a binder.
        val evidence = harness.gateway.bindingEvidence.value
        assertEquals(expected, evidence.target)
        assertTrue(evidence.attempted)
        assertEquals(FeedTargetResolvability.RESOLVABLE, evidence.resolvability)
        assertTrue(evidence.accepted)
        assertTrue(!evidence.connected)
        assertTrue(!evidence.active)
        assertEquals(null, harness.gateway.diagnostic.value)
    }

    @Test
    fun bindForwardsExactLegacyComponentWithoutRetryingCurrent() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_LEGACY_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()

        // Resolver walks current (absent) then legacy (present); the bind addresses the
        // exact legacy component once — never a retry over other packages.
        val expected = harness.legacyTarget()
        assertEquals(listOf(expected), harness.connector.bindTargets)
        assertEquals(listOf(expected), harness.preflight.verifiedTargets)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
        assertEquals(null, harness.gateway.diagnostic.value)
    }

    @Test
    fun preflightMissReportsNotResolvableWithoutBind() = runTest {
        val harness = Harness(
            this,
            installed = setOf(LAWNFEED_PACKAGE),
            preflightVerdict = FeedTargetResolvability.NOT_RESOLVABLE,
        )
        harness.gateway.start(this)
        advanceUntilIdle()

        assertTrue(harness.connector.bindTargets.isEmpty())
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.NOT_RESOLVABLE, diagnostic?.status)
        assertEquals(FeedDiagnosticStage.RESOLUTION, diagnostic?.stage)
        assertEquals(FeedDiagnosticCause.TARGET_NOT_RESOLVABLE, diagnostic?.cause)
        assertEquals(FeedProviderId.Lawnfeed, diagnostic?.provider)
        assertEquals(LAWNFEED_PACKAGE, diagnostic?.packageName)
        assertEquals(harness.lawnfeedTarget().component, diagnostic?.component)
        val evidence = harness.gateway.bindingEvidence.value
        assertEquals(harness.lawnfeedTarget(), evidence.target)
        assertTrue(evidence.attempted)
        assertEquals(FeedTargetResolvability.NOT_RESOLVABLE, evidence.resolvability)
        assertTrue(!evidence.accepted)
        assertTrue(!evidence.connected)
        assertEquals(0, harness.connector.unbindCalls)
    }

    @Test
    fun preflightDeniedReportsNotResolvableWithRedactedCause() = runTest {
        val harness = Harness(
            this,
            installed = setOf(LAWNFEED_PACKAGE),
            preflightFailure = PreflightFailure.SECURITY,
        )
        harness.gateway.start(this)
        advanceUntilIdle()

        assertTrue(harness.connector.bindTargets.isEmpty())
        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.NOT_RESOLVABLE, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.RESOLUTION_DENIED, diagnostic?.cause)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        assertTrue(!diagnostic.toString().contains("s3cr3t"))
    }

    @Test
    fun preflightFailedReportsNotResolvableWithRedactedCause() = runTest {
        val harness = Harness(
            this,
            installed = setOf(LAWNFEED_PACKAGE),
            preflightFailure = PreflightFailure.QUERY,
        )
        harness.gateway.start(this)
        advanceUntilIdle()

        assertTrue(harness.connector.bindTargets.isEmpty())
        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.NOT_RESOLVABLE, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.RESOLUTION_FAILED, diagnostic?.cause)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        assertTrue(!diagnostic.toString().contains("7x9q"))
    }

    @Test
    fun bindFalseReportsBindFailedWithoutUnbind() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE), acceptBind = false)
        harness.gateway.start(this)
        advanceUntilIdle()

        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.BIND_FAILED, diagnostic?.status)
        assertEquals(FeedDiagnosticStage.BIND, diagnostic?.stage)
        assertEquals(FeedDiagnosticCause.BIND_DECLINED, diagnostic?.cause)
        assertEquals(harness.lawnfeedTarget().component, diagnostic?.component)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        val evidence = harness.gateway.bindingEvidence.value
        assertTrue(evidence.attempted)
        assertTrue(!evidence.accepted)
        assertTrue(!evidence.connected)
        // The bind never took effect: nothing to unbind, never a double unbind.
        assertEquals(0, harness.connector.unbindCalls)
    }

    @Test
    fun bindSecurityExceptionReportsBindFailedNeverRejected() = runTest {
        val harness = Harness(
            this,
            installed = setOf(LAWNFEED_PACKAGE),
            bindFailure = BindFailure.SECURITY,
        )
        harness.gateway.start(this)
        advanceUntilIdle()

        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.BIND_FAILED, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.BIND_DENIED, diagnostic?.cause)
        // Bind-stage denial is BIND_FAILED/QUERY_FAILED — never discovery REJECTED or
        // PERMISSION_DENIED.
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        assertTrue(!diagnostic.toString().contains("z1x9k"))
        assertEquals(0, harness.connector.unbindCalls)
    }

    @Test
    fun bindExceptionReportsBindFailedWithRedactedCause() = runTest {
        val harness = Harness(
            this,
            installed = setOf(LAWNFEED_PACKAGE),
            bindFailure = BindFailure.QUERY,
        )
        harness.gateway.start(this)
        advanceUntilIdle()

        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.BIND_FAILED, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.BIND_ERROR, diagnostic?.cause)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        assertTrue(!diagnostic.toString().contains("8y2w"))
        assertEquals(0, harness.connector.unbindCalls)
    }

    @Test
    fun unexpectedPackageViaConnectIsRejectedWithoutInstalling() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val epoch = harness.gateway.bindEpoch

        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(
            channel,
            epoch,
            NEO_FEED_PACKAGE,
            "$NEO_FEED_PACKAGE.FeedService",
        )
        advanceUntilIdle()

        // Rejected as BIND_FAILED: the unexpected binder is closed, never installed.
        assertEquals(1, channel.closeCalls)
        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.BIND_FAILED, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.UNEXPECTED_COMPONENT, diagnostic?.cause)
        assertEquals(harness.lawnfeedTarget().component, diagnostic?.component)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        assertEquals(1, harness.connector.unbindCalls)
        val evidence = harness.gateway.bindingEvidence.value
        assertTrue(!evidence.connected)
        assertTrue(!evidence.active)
        // Never installed: later host requests reach no channel.
        harness.gateway.notifyOverlayVisible(true)
        assertTrue(channel.visibles.isEmpty())
    }

    @Test
    fun samePackageWrongClassViaConnectIsRejectedWithoutInstalling() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val epoch = harness.gateway.bindEpoch

        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(
            channel,
            epoch,
            LAWNFEED_PACKAGE,
            "$LAWNFEED_PACKAGE.WrongService",
        )
        advanceUntilIdle()

        assertEquals(1, channel.closeCalls)
        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.BIND_FAILED, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.UNEXPECTED_COMPONENT, diagnostic?.cause)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        assertEquals(1, harness.connector.unbindCalls)
        harness.gateway.notifyOverlayVisible(true)
        assertTrue(channel.visibles.isEmpty())
    }

    @Test
    fun preBridgeUnexpectedComponentIsRejectedWithoutBinder() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val epoch = harness.gateway.bindEpoch

        // Same verdict OverlayConnection reports before building any bridge.
        harness.gateway.onUnexpectedComponent(epoch)
        advanceUntilIdle()

        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.BIND_FAILED, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.UNEXPECTED_COMPONENT, diagnostic?.cause)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        assertEquals(1, harness.connector.unbindCalls)
    }

    @Test
    fun acceptedNotConnectedStaysPendingWithoutFailure() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()

        // Bind accepted, no binder callback yet: coarse Available (which never implies
        // a binder), no failure diagnostic, no timeout, no new status.
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
        assertEquals(null, harness.gateway.diagnostic.value)
        val evidence = harness.gateway.bindingEvidence.value
        assertTrue(evidence.accepted)
        assertTrue(!evidence.connected)
        assertTrue(!evidence.active)
        assertEquals(0, harness.connector.unbindCalls)
    }

    @Test
    fun matchingConnectPublishesConnected() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()

        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        harness.gateway.notifyOverlayVisible(true)

        assertEquals(listOf(false, true), channel.visibles)
        val evidence = harness.gateway.bindingEvidence.value
        assertTrue(evidence.accepted)
        assertTrue(evidence.connected)
        assertTrue(evidence.active)
        assertEquals(null, harness.gateway.diagnostic.value)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun nullBindingReportsBindFailed() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val epoch = harness.gateway.bindEpoch

        harness.gateway.onNullBindingReceived(epoch)
        advanceUntilIdle()

        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.BIND_FAILED, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.NULL_BINDING, diagnostic?.cause)
        assertEquals(harness.lawnfeedTarget().component, diagnostic?.component)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        assertEquals(1, harness.connector.unbindCalls)
        val evidence = harness.gateway.bindingEvidence.value
        assertTrue(!evidence.connected)
        assertTrue(!evidence.active)
    }

    @Test
    fun disconnectAfterConnectReportsDisconnectedAndRetainsDiagnostic() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )

        harness.gateway.onConnectionLost()
        advanceUntilIdle()

        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.DISCONNECTED, diagnostic?.status)
        assertEquals(FeedDiagnosticStage.CONNECTION, diagnostic?.stage)
        assertEquals(FeedDiagnosticCause.SERVICE_DISCONNECTED, diagnostic?.cause)
        assertEquals(harness.lawnfeedTarget().component, diagnostic?.component)
        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
        // Failure teardown retains the diagnostic: a second loss adds no second unbind
        // and never overwrites the verdict.
        harness.gateway.onConnectionLost()
        advanceUntilIdle()
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(diagnostic, harness.gateway.diagnostic.value)
    }

    @Test
    fun bindingDiedAfterConnectReportsBinderDied() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        val epoch = harness.gateway.bindEpoch

        harness.gateway.onBindingDiedForEpoch(epoch)
        advanceUntilIdle()

        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.DISCONNECTED, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.BINDER_DIED, diagnostic?.cause)
        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, channel.closeCalls)
    }

    @Test
    fun serviceDisconnectedAfterConnectReportsDisconnected() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        val epoch = harness.gateway.bindEpoch

        harness.gateway.onServiceDisconnectedForEpoch(epoch)
        advanceUntilIdle()

        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.DISCONNECTED, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.SERVICE_DISCONNECTED, diagnostic?.cause)
        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
    }

    @Test
    fun staleUnexpectedAfterReplaceIsIgnoredWithoutOverwrite() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val staleEpoch = harness.gateway.bindEpoch

        harness.installed = setOf(NEO_FEED_PACKAGE)
        harness.firePackageChange()
        advanceUntilIdle()
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )
        val unbindsBefore = harness.connector.unbindCalls

        // Stale generation's unexpected-component report: ignored, never overwrites the
        // replacement's pending (accepted, no failure) verdict.
        harness.gateway.onUnexpectedComponent(staleEpoch)
        harness.gateway.onNullBindingReceived(staleEpoch)
        advanceUntilIdle()

        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )
        assertEquals(null, harness.gateway.diagnostic.value)
        assertEquals(unbindsBefore, harness.connector.unbindCalls)
        assertEquals(harness.neoTarget(), harness.gateway.bindingEvidence.value.target)
    }

    @Test
    fun deliberateDetachReportsNoFailureAndClearsEvidence() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)

        harness.gateway.detach()
        advanceUntilIdle()

        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(null, harness.gateway.diagnostic.value)
        val evidence = harness.gateway.bindingEvidence.value
        assertEquals(null, evidence.target)
        assertTrue(!evidence.attempted)
        assertTrue(!evidence.accepted)
        assertTrue(!evidence.connected)
        // Second detach: no double unbind, still no failure.
        harness.gateway.detach()
        advanceUntilIdle()
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(null, harness.gateway.diagnostic.value)
    }

    @Test
    fun d3DiagnosticsNeverCarryExceptionDetail() = runTest {
        val denied = Harness(
            this,
            installed = setOf(LAWNFEED_PACKAGE),
            bindFailure = BindFailure.SECURITY,
        )
        denied.gateway.start(this)
        advanceUntilIdle()
        assertTrue(!denied.gateway.diagnostic.toString().contains("z1x9k"))

        val failed = Harness(
            this,
            installed = setOf(LAWNFEED_PACKAGE),
            bindFailure = BindFailure.QUERY,
        )
        failed.gateway.start(this)
        advanceUntilIdle()
        assertTrue(!failed.gateway.diagnostic.toString().contains("8y2w"))
    }

    // Window handshake (P1): the host surface registers its window through the
    // gateway so the bound provider renders pixels. Params copies cache like the
    // control verbs (catch-up across binds), the activity is never retained, and
    // channels without the window handshake are no-ops.

    @Test
    fun attachBeforeConnectCatchesUpBeforeControlVerbs() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        harness.gateway.notifyOverlayVisible(true)
        harness.gateway.notifyWindowAttachedParams(windowParams(width = 1080, height = 2400))

        val channel = FakeWindowConnection()
        harness.gateway.onOverlayConnected(channel)

        // Window registration precedes the control catch-up: the provider needs
        // the window before open/scroll replay can show pixels.
        assertEquals(listOf("attached", "visible", "progress"), channel.events)
        assertEquals(listOf(true), channel.visibles)
        assertEquals(1, channel.attaches.size)
        // Flags ride the JVM-safe primitive path (params field copies are framework
        // code and stay device-verified: stubbed unit-test copies carry no fields).
        assertEquals(0, channel.attaches.single().flags)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun liveAttachDeliversImmediately() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeWindowConnection()
        harness.gateway.onOverlayConnected(channel)
        assertTrue(channel.attaches.isEmpty())

        val params = windowParams(width = 720, height = 1600)
        harness.gateway.notifyWindowAttachedParams(params)

        assertEquals(1, channel.attaches.size)
        assertEquals(0, channel.attaches.single().flags)
        // The gateway caches its own copy, never the caller's instance.
        assertTrue(channel.attaches.single().source !== params)
    }

    @Test
    fun repeatedAttachReRegistersLatest() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeWindowConnection()
        harness.gateway.onOverlayConnected(channel)

        harness.gateway.notifyWindowAttachedParams(windowParams(width = 100, height = 100))
        harness.gateway.notifyWindowAttachedParams(windowParams(width = 200, height = 200), 5)

        assertEquals(2, channel.attaches.size)
        assertEquals(listOf(0, 5), channel.attaches.map { it.flags })
    }

    @Test
    fun detachClearsCacheSoNextConnectStaysDetached() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        harness.gateway.notifyWindowAttachedParams(windowParams(width = 1080, height = 2400))
        val first = FakeWindowConnection()
        harness.gateway.onOverlayConnected(first)
        assertEquals(1, first.attaches.size)

        harness.gateway.notifyWindowDetached(false)
        assertEquals(listOf(false), first.detaches)

        // Rebind after an explicit detach must not resurrect the registration.
        harness.installed = setOf(NEO_FEED_PACKAGE)
        harness.firePackageChange()
        advanceUntilIdle()
        val second = FakeWindowConnection()
        harness.gateway.onOverlayConnected(second)

        assertTrue(second.attaches.isEmpty())
        assertTrue(second.detaches.isEmpty())
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun rebindRetainsWindowCache() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        harness.gateway.notifyWindowAttachedParams(windowParams(width = 1080, height = 2400))
        val first = FakeWindowConnection()
        harness.gateway.onOverlayConnected(first)
        assertEquals(1, first.attaches.size)

        // Package switch tears down the old channel but keeps the host's window
        // registration: the replacement re-attaches on connect.
        harness.installed = setOf(NEO_FEED_PACKAGE)
        harness.firePackageChange()
        advanceUntilIdle()
        assertEquals(1, first.closeCalls)

        val second = FakeWindowConnection()
        harness.gateway.onOverlayConnected(second)

        assertEquals(1, second.attaches.size)
        assertEquals(0, second.attaches.single().flags)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.NeoFeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun detachWithoutRegistrationIsSilent() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()

        // No cache, no channel: silent no-op, never throws.
        harness.gateway.notifyWindowDetached(false)

        // Window channel connected but nothing registered: no IPC.
        val channel = FakeWindowConnection()
        harness.gateway.onOverlayConnected(channel)
        harness.gateway.notifyWindowDetached(true)

        assertTrue(channel.attaches.isEmpty())
        assertTrue(channel.detaches.isEmpty())
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun nonWindowChannelIsNoop() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()

        // Channels without the window handshake (older fakes, control-only) make
        // attach/detach silent no-ops instead of crashes.
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        harness.gateway.notifyWindowAttachedParams(windowParams(width = 1080, height = 2400))
        harness.gateway.notifyWindowDetached(false)

        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
    }

    @Test
    fun attachFailureOnConnectTearsDown() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        harness.gateway.notifyWindowAttachedParams(windowParams(width = 1080, height = 2400))

        // Transport loss during the window catch-up transact tears down like any
        // other catch-up loss: honest Unavailable, one unbind, dead channel closed.
        val lossy = LossyWindowConnection(harness.gateway)
        harness.gateway.onOverlayConnected(lossy)

        assertTrue(harness.gateway.state.value is FeedAvailability.Unavailable)
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, lossy.closeCalls)
    }

    @Test
    fun contendedMatchingConnectIsDeferredAndInstalledAfterRelease() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val epoch = harness.gateway.bindEpoch

        // Simulate tryBindLocked holding the lock across preflight/bind while the
        // one-shot onServiceConnected arrives on another thread.
        val mutex = gatewayMutex(harness)
        assertTrue(mutex.tryLock())
        try {
            val channel = FakeOverlayConnection()
            harness.gateway.onOverlayConnected(
                channel,
                epoch,
                LAWNFEED_PACKAGE,
                "$LAWNFEED_PACKAGE.FeedService",
            )
            // Deferred, not discarded: never closed while contended, still pending.
            assertEquals(0, channel.closeCalls)
            assertTrue(channel.visibles.isEmpty())
            assertEquals(
                FeedAvailability.Available(FeedProviderId.Lawnfeed),
                harness.gateway.state.value,
            )
            // Hold the reference for post-release assertions.
            contendedChannel = channel
        } finally {
            mutex.unlock()
        }
        advanceUntilIdle()

        val channel = contendedChannel as FakeOverlayConnection
        assertEquals(0, channel.closeCalls)
        val evidence = harness.gateway.bindingEvidence.value
        assertTrue(evidence.accepted)
        assertTrue(evidence.connected)
        assertTrue(evidence.active)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )
        // Catch-up delivered after the holder released.
        assertEquals(listOf(false), channel.visibles)
    }

    private var contendedChannel: FeedOverlayConnection? = null

    @Test
    fun contendedNullBindingReportsBindFailed() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val epoch = harness.gateway.bindEpoch

        val mutex = gatewayMutex(harness)
        assertTrue(mutex.tryLock())
        try {
            harness.gateway.onNullBindingReceived(epoch)
            // Deferred, not yet torn down.
            assertEquals(
                FeedAvailability.Available(FeedProviderId.Lawnfeed),
                harness.gateway.state.value,
            )
            assertEquals(0, harness.connector.unbindCalls)
        } finally {
            mutex.unlock()
        }
        advanceUntilIdle()

        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.BIND_FAILED, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.NULL_BINDING, diagnostic?.cause)
        assertEquals(harness.lawnfeedTarget().component, diagnostic?.component)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        assertEquals(1, harness.connector.unbindCalls)
    }

    @Test
    fun contendedUnexpectedComponentReportsBindFailed() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val epoch = harness.gateway.bindEpoch

        val mutex = gatewayMutex(harness)
        assertTrue(mutex.tryLock())
        try {
            harness.gateway.onUnexpectedComponent(epoch)
            assertEquals(
                FeedAvailability.Available(FeedProviderId.Lawnfeed),
                harness.gateway.state.value,
            )
            assertEquals(0, harness.connector.unbindCalls)
        } finally {
            mutex.unlock()
        }
        advanceUntilIdle()

        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.BIND_FAILED, diagnostic?.status)
        assertEquals(FeedDiagnosticCause.UNEXPECTED_COMPONENT, diagnostic?.cause)
        assertEquals(harness.lawnfeedTarget().component, diagnostic?.component)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        assertEquals(1, harness.connector.unbindCalls)
    }

    @Test
    fun contendedStopSerializesTeardownAfterRelease() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )

        // Simulate a background bind holding the lock (blocking preflight/bind) while
        // MainActivity.onStop races on the main thread.
        val mutex = gatewayMutex(harness)
        assertTrue(mutex.tryLock())
        try {
            harness.gateway.stop()
            // Durable intent: nothing torn down yet, but nothing lost either.
            assertEquals(0, harness.connector.unbindCalls)
            assertEquals(0, channel.closeCalls)
        } finally {
            mutex.unlock()
        }
        advanceUntilIdle()

        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, channel.closeCalls)
        assertEquals(null, harness.gateway.diagnostic.value)
        val evidence = harness.gateway.bindingEvidence.value
        assertEquals(null, evidence.target)
        assertTrue(!evidence.attempted)
        // Package monitor stopped with the gateway.
        harness.firePackageChange()
        advanceUntilIdle()
        assertEquals(1, harness.connector.bindCalls.size)
    }

    @Test
    fun contendedDetachSerializesCleanupAfterRelease() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)

        val mutex = gatewayMutex(harness)
        assertTrue(mutex.tryLock())
        try {
            harness.gateway.detach()
            assertEquals(0, harness.connector.unbindCalls)
        } finally {
            mutex.unlock()
        }
        advanceUntilIdle()

        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, channel.closeCalls)
        assertEquals(null, harness.gateway.diagnostic.value)
        assertEquals(null, harness.gateway.bindingEvidence.value.target)
    }

    @Test
    fun contendedStopSurvivesCallerScopeCancel() = runTest {
        // Caller-owned scope mirrors MainActivity.lifecycleScope: the contended stop
        // schedules its teardown drain there, and onStop cancels the scope while the
        // drain still waits on the holder.
        val callerScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(callerScope)
        advanceUntilIdle()
        val channel = FakeOverlayConnection()
        harness.gateway.onOverlayConnected(channel)
        assertEquals(
            FeedAvailability.Available(FeedProviderId.Lawnfeed),
            harness.gateway.state.value,
        )

        val mutex = gatewayMutex(harness)
        assertTrue(mutex.tryLock())
        try {
            harness.gateway.stop()
            assertEquals(0, harness.connector.unbindCalls)
            // onStop cancels the caller scope while the drain waits for the holder.
            callerScope.cancel()
        } finally {
            mutex.unlock()
        }
        advanceUntilIdle()

        // Durable teardown: the pending intent is consumed and the binding/monitor
        // are gone even though the drain's original scope was cancelled mid-wait.
        assertEquals(1, harness.connector.unbindCalls)
        assertEquals(1, channel.closeCalls)
        assertEquals(null, harness.gateway.diagnostic.value)
        val evidence = harness.gateway.bindingEvidence.value
        assertEquals(null, evidence.target)
        assertTrue(!evidence.attempted)
        // Package monitor stopped with the gateway.
        harness.firePackageChange()
        advanceUntilIdle()
        assertEquals(1, harness.connector.bindCalls.size)
    }

    @Test
    fun contendedNullAndMismatchPreserveBindFailed() = runTest {
        val harness = Harness(this, installed = setOf(LAWNFEED_PACKAGE))
        harness.gateway.start(this)
        advanceUntilIdle()
        val epoch = harness.gateway.bindEpoch

        // Overlapping contended bind-stage verdicts: both defer via the single
        // atomic epoch-to-verdict handoff while a holder owns the lock.
        val mutex = gatewayMutex(harness)
        assertTrue(mutex.tryLock())
        try {
            harness.gateway.onNullBindingReceived(epoch)
            harness.gateway.onUnexpectedComponent(epoch)
            // Serialized proof of atomicity: the marker already carries its full
            // verdict before the holder releases — never a bare epoch that a
            // concurrent consumer could drain as DISCONNECTED/BINDER_DIED.
            val pending = pendingLosses(harness)[epoch]
            assertEquals(FeedDiagnosticStatus.BIND_FAILED, pending?.status)
            assertTrue(
                pending?.cause == FeedDiagnosticCause.NULL_BINDING ||
                    pending?.cause == FeedDiagnosticCause.UNEXPECTED_COMPONENT,
            )
            assertEquals(
                FeedAvailability.Available(FeedProviderId.Lawnfeed),
                harness.gateway.state.value,
            )
        } finally {
            mutex.unlock()
        }
        advanceUntilIdle()

        // The drain reports the stored BIND_FAILED verdict — never the
        // DISCONNECTED/BINDER_DIED defaults of a torn marker.
        val diagnostic = harness.gateway.diagnostic.value
        assertEquals(FeedDiagnosticStatus.BIND_FAILED, diagnostic?.status)
        assertTrue(
            diagnostic?.cause == FeedDiagnosticCause.NULL_BINDING ||
                diagnostic?.cause == FeedDiagnosticCause.UNEXPECTED_COMPONENT,
        )
        assertEquals(harness.lawnfeedTarget().component, diagnostic?.component)
        assertEquals(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            harness.gateway.state.value,
        )
        assertEquals(1, harness.connector.unbindCalls)
    }

    private fun windowParams(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams().apply {
            this.width = width
            this.height = height
        }

    /** Window registration record for [FakeWindowConnection]. */
    private data class WindowAttach(
        val flags: Int,
        val source: WindowManager.LayoutParams?,
    )

    /**
     * Window-capable stand-in for [AidlFeedBridge]: records control verbs and window
     * ops in one [events] log so catch-up ordering (attach before visible/progress)
     * is pinned without touching `IBinder`.
     */
    private class FakeWindowConnection : FeedOverlayConnection, FeedOverlayWindowConnection {
        val events: MutableList<String> = mutableListOf()
        val visibles: MutableList<Boolean> = mutableListOf()
        val progresses: MutableList<Float> = mutableListOf()
        val attaches: MutableList<WindowAttach> = mutableListOf()
        val detaches: MutableList<Boolean> = mutableListOf()
        var closeCalls: Int = 0

        override fun setVisible(visible: Boolean) {
            visibles += visible
            events += "visible"
        }

        override fun setProgress(progress: Float) {
            progresses += progress
            events += "progress"
        }

        override fun windowAttached(params: WindowManager.LayoutParams, flags: Int) {
            attaches += WindowAttach(flags, params)
            events += "attached"
        }

        override fun windowDetached(isChangingConfigurations: Boolean) {
            detaches += isChangingConfigurations
            events += "detached"
        }

        override fun close() {
            closeCalls += 1
        }
    }

    /**
     * Reports transport loss synchronously from the window catch-up transact,
     * modeling an [AidlFeedBridge] whose first `windowAttached` fails while
     * [FeedOverlayGatewayImpl] still holds its lock.
     */
    private class LossyWindowConnection(
        private val gateway: FeedOverlayGatewayImpl,
    ) : FeedOverlayConnection, FeedOverlayWindowConnection {
        var closeCalls: Int = 0

        override fun setVisible(visible: Boolean) = Unit

        override fun setProgress(progress: Float) = Unit

        override fun windowAttached(params: WindowManager.LayoutParams, flags: Int) {
            gateway.onConnectionLost()
        }

        override fun windowDetached(isChangingConfigurations: Boolean) = Unit

        override fun close() {
            closeCalls += 1
        }
    }
}
