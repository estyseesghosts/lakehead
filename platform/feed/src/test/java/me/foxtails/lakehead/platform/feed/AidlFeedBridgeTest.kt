package me.foxtails.lakehead.platform.feed

import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.view.WindowManager
import com.google.android.libraries.launcherclient.ILauncherOverlay
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Binder-level regression tests for [AidlFeedBridge] against a fake [IBinder].
 *
 * The fake service answers `queryLocalInterface` with a recording [FakeOverlay], so
 * `ILauncherOverlay.Stub.asInterface` returns the fake directly and no real transact ever
 * runs: JVM tests pin lifecycle, loss, contention and teardown behavior, while real
 * provider handshakes stay device-verified with F2/F3.
 */
class AidlFeedBridgeTest {

    // Visibility lifecycle.

    @Test
    fun visibleTrueOpensOverlay() {
        val harness = Harness()

        harness.bridge.setVisible(true)

        assertEquals(listOf(0), harness.overlay.opens.toList())
        assertTrue(harness.overlay.closes.isEmpty())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun visibleFalseClosesOverlay() {
        val harness = Harness()

        harness.bridge.setVisible(false)

        assertEquals(listOf(0), harness.overlay.closes.toList())
        assertTrue(harness.overlay.opens.isEmpty())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun repeatedVisibleIsNoop() {
        val harness = Harness()

        harness.bridge.setVisible(true)
        harness.bridge.setVisible(true)
        harness.bridge.setVisible(false)
        harness.bridge.setVisible(false)

        assertEquals(listOf(0), harness.overlay.opens.toList())
        assertEquals(listOf(0), harness.overlay.closes.toList())
    }

    @Test
    fun visibleToggleDeliversInOrder() {
        val harness = Harness()

        harness.bridge.setVisible(true)
        harness.bridge.setVisible(false)
        harness.bridge.setVisible(true)

        assertEquals(listOf(0, 0), harness.overlay.opens.toList())
        assertEquals(listOf(0), harness.overlay.closes.toList())
    }

    @Test
    fun progressDoesNotOpenOverlay() {
        val harness = Harness()

        harness.bridge.setProgress(0.5f)

        assertTrue(harness.overlay.opens.isEmpty())
        assertTrue(harness.overlay.closes.isEmpty())
        assertEquals(listOf(0.5f), harness.overlay.scrolls.toList())
    }

    @Test
    fun visibleAfterCloseIsSilent() {
        val harness = Harness()
        harness.bridge.close()

        harness.bridge.setVisible(true)
        harness.bridge.setProgress(0.5f)

        assertTrue(harness.overlay.opens.isEmpty())
        assertTrue(harness.overlay.scrolls.isEmpty())
        assertEquals(0, harness.losses.get())
    }

    // Progress lifecycle and coercion through the bridge.

    @Test
    fun progressDelivered() {
        val harness = Harness()

        harness.bridge.setProgress(0.25f)

        assertEquals(listOf(0.25f), harness.overlay.scrolls.toList())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun nanMapsToZeroThroughBridge() {
        val harness = Harness()

        harness.bridge.setProgress(Float.NaN)

        assertEquals(listOf(0f), harness.overlay.scrolls.toList())
    }

    @Test
    fun aboveOneClampsThroughBridge() {
        val harness = Harness()

        harness.bridge.setProgress(2f)

        assertEquals(listOf(1f), harness.overlay.scrolls.toList())
    }

    @Test
    fun belowZeroClampsThroughBridge() {
        val harness = Harness()

        harness.bridge.setProgress(-0.5f)

        assertEquals(listOf(0f), harness.overlay.scrolls.toList())
    }

    @Test
    fun infinitiesClampThroughBridge() {
        val harness = Harness()

        harness.bridge.setProgress(Float.POSITIVE_INFINITY)
        harness.bridge.setProgress(Float.NEGATIVE_INFINITY)

        assertEquals(listOf(1f, 0f), harness.overlay.scrolls.toList())
    }

    @Test
    fun repeatedProgressIsNoop() {
        val harness = Harness()

        harness.bridge.setProgress(0.4f)
        harness.bridge.setProgress(0.4f)

        assertEquals(listOf(0.4f), harness.overlay.scrolls.toList())
    }

    @Test
    fun sequentialProgressDeliversInOrder() {
        val harness = Harness()

        harness.bridge.setProgress(0.1f)
        harness.bridge.setProgress(0.2f)
        harness.bridge.setProgress(0.9f)

        assertEquals(listOf(0.1f, 0.2f, 0.9f), harness.overlay.scrolls.toList())
    }

    @Test
    fun visibleDoesNotScroll() {
        val harness = Harness()

        harness.bridge.setVisible(true)
        harness.bridge.setVisible(false)

        assertTrue(harness.overlay.scrolls.isEmpty())
    }

    // Transport loss.

    @Test
    fun scrollFailureReportsLossOnce() {
        val harness = Harness()
        harness.overlay.failWith = RemoteException("dead")

        harness.bridge.setProgress(0.5f)

        assertEquals(1, harness.losses.get())
        assertTrue(harness.overlay.scrolls.isEmpty())
    }

    @Test
    fun repeatedFailuresReportLossOnce() {
        val harness = Harness()
        harness.overlay.failWith = RemoteException("dead")

        harness.bridge.setProgress(0.5f)
        harness.bridge.setProgress(0.6f)
        harness.bridge.setVisible(true)

        assertEquals(1, harness.losses.get())
    }

    @Test
    fun openFailureReportsLoss() {
        val harness = Harness()
        harness.overlay.failWith = RuntimeException("dead")

        harness.bridge.setVisible(true)

        assertEquals(1, harness.losses.get())
        assertTrue(harness.overlay.opens.isEmpty())
    }

    @Test
    fun throwingLossListenerNeverEscapes() {
        val harness = Harness(listenerThrows = RuntimeException("listener boom"))
        harness.overlay.failWith = RemoteException("dead")

        harness.bridge.setProgress(0.5f)
        harness.service.deathRecipients.single().binderDied()

        assertEquals(1, harness.losses.get())
    }

    @Test
    fun deathRecipientReportsLossOnce() {
        val harness = Harness()
        val death = harness.service.deathRecipients.single()

        death.binderDied()
        death.binderDied()

        assertEquals(1, harness.losses.get())
    }

    @Test
    fun linkToDeathFailureReportsLossOnce() {
        val harness = Harness(linkThrows = RemoteException("already dead"))

        assertEquals(1, harness.losses.get())
        // The bridge stays usable-safe: further calls never throw.
        harness.bridge.setProgress(0.5f)
        harness.bridge.close()
        assertEquals(1, harness.losses.get())
    }

    @Test
    fun nullProxyReportsLossWithoutIpc() {
        val harness = Harness(failQuery = true)

        harness.bridge.setProgress(0.5f)

        assertEquals(1, harness.losses.get())
        assertTrue(harness.overlay.scrolls.isEmpty())
        harness.bridge.close()
        assertEquals(1, harness.losses.get())
    }

    @Test
    fun failureAfterCloseReportsNoLoss() {
        val harness = Harness()
        harness.overlay.failWith = RemoteException("dead")
        harness.bridge.close()

        harness.bridge.setProgress(0.5f)
        harness.bridge.setVisible(true)

        assertEquals(0, harness.losses.get())
        assertTrue(harness.overlay.scrolls.isEmpty())
        assertTrue(harness.overlay.opens.isEmpty())
    }

    // Teardown.

    @Test
    fun closeUnlinksOnceAndIsIdempotent() {
        val harness = Harness()

        harness.bridge.close()
        harness.bridge.close()

        assertEquals(1, harness.service.unlinkCalls.get())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun closeThenDeathReportsNoLoss() {
        val harness = Harness()
        harness.bridge.close()

        harness.service.deathRecipients.single().binderDied()

        assertEquals(0, harness.losses.get())
        assertEquals(1, harness.service.unlinkCalls.get())
    }

    @Test
    fun deathThenCloseReportsLossOnce() {
        val harness = Harness()

        harness.service.deathRecipients.single().binderDied()
        harness.bridge.close()
        harness.bridge.close()

        assertEquals(1, harness.losses.get())
        assertEquals(1, harness.service.unlinkCalls.get())
    }

    @Test
    fun unlinkFailureKeepsCloseSilent() {
        val harness = Harness()
        harness.service.unlinkThrows = RuntimeException("already gone")

        harness.bridge.close()
        harness.bridge.close()

        assertEquals(1, harness.service.unlinkCalls.get())
        assertEquals(0, harness.losses.get())
    }

    // Contention: the latest request is never lost and ordering never regresses.

    @Test
    fun contentionDeliversLatestWithoutExtraCall() {
        val harness = Harness()
        harness.overlay.blockScrolls = true
        val worker = thread { harness.bridge.setProgress(0.1f) }
        assertTrue(
            "transact did not start",
            harness.overlay.enteredScroll.await(10, TimeUnit.SECONDS),
        )

        // The lock is held inside the 0.1 transact: this caches 0.9 and degrades.
        harness.bridge.setProgress(0.9f)
        harness.overlay.releaseScroll.countDown()
        worker.join(10_000)

        assertFalse("worker hung", worker.isAlive)
        assertEquals(listOf(0.1f, 0.9f), harness.overlay.scrolls.toList())
    }

    @Test
    fun concurrentWritersCoalesceToLatest() {
        val harness = Harness()
        harness.overlay.blockScrolls = true
        val worker = thread { harness.bridge.setProgress(0.1f) }
        assertTrue(
            "transact did not start",
            harness.overlay.enteredScroll.await(10, TimeUnit.SECONDS),
        )

        harness.bridge.setProgress(0.3f)
        harness.bridge.setProgress(0.5f)
        harness.bridge.setProgress(0.7f)
        harness.overlay.releaseScroll.countDown()
        worker.join(10_000)

        assertFalse("worker hung", worker.isAlive)
        // Intermediate values coalesce, but the newest write is never lost and no older
        // value is ever sent after it.
        assertEquals(listOf(0.1f, 0.7f), harness.overlay.scrolls.toList())
    }

    @Test
    fun crossVerbContentionDeliversBoth() {
        val harness = Harness()
        harness.overlay.blockScrolls = true
        val worker = thread { harness.bridge.setProgress(0.2f) }
        assertTrue(
            "transact did not start",
            harness.overlay.enteredScroll.await(10, TimeUnit.SECONDS),
        )

        harness.bridge.setVisible(true)
        harness.overlay.releaseScroll.countDown()
        worker.join(10_000)

        assertFalse("worker hung", worker.isAlive)
        assertEquals(listOf(0.2f), harness.overlay.scrolls.toList())
        assertEquals(listOf(0), harness.overlay.opens.toList())
    }

    @Test
    fun concurrentCloseAndDeathReportsAtMostOnce() {
        repeat(20) {
            val harness = Harness()
            val death = harness.service.deathRecipients.single()
            val closer = thread { repeat(50) { harness.bridge.close() } }
            val dyer = thread { repeat(50) { death.binderDied() } }
            closer.join(10_000)
            dyer.join(10_000)

            assertFalse("closer hung", closer.isAlive)
            assertFalse("dyer hung", dyer.isAlive)
            assertTrue(
                "loss reported ${harness.losses.get()} times",
                harness.losses.get() <= 1,
            )
            assertEquals(1, harness.service.unlinkCalls.get())
        }
    }

    @Test
    fun stormBeyondOldPassLimitStillDeliversLatest() {
        val harness = Harness()
        harness.overlay.blockScrolls = true
        val worker = thread { harness.bridge.setProgress(0.01f) }
        assertTrue(
            "transact did not start",
            harness.overlay.enteredScroll.await(10, TimeUnit.SECONDS),
        )

        // Initial storm while the worker holds the mutex inside the 0.01 transact:
        // these all fail tryLock and return via handoff, coalescing to 0.5f.
        for (i in 2..10) {
            harness.bridge.setProgress(i / 20f)
        }
        // Chain 9 further distinct values, each injected synchronously inside the
        // previous transact on the holder thread (tryLock handoff, never lost), so
        // each needs its own extra drain pass: entries 2..10 stage 0.55f..1.0f and
        // entry 11 delivers the newest 1.0f — 11 passes total. An 8-pass bound
        // would exit with the newest still pending and the last value short of 1.0f.
        // The hook is armed before release; entry 1 already ran, entries 2..11 see it.
        val staged = listOf(0.55f, 0.6f, 0.65f, 0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 1.0f)
        harness.overlay.scrollEntryHook = { entry ->
            val index = entry - 2
            if (index in staged.indices) {
                harness.bridge.setProgress(staged[index])
            }
        }
        harness.overlay.releaseScroll.countDown()
        worker.join(10_000)

        assertFalse("worker hung", worker.isAlive)
        val scrolls = harness.overlay.scrolls.toList()
        assertTrue("no scrolls delivered", scrolls.isNotEmpty())
        assertEquals(0.01f, scrolls.first(), 0.0f)
        assertEquals(1.0f, scrolls.last(), 0.0f)
        // Exact drain-pass accounting: 1 initial (0.01f, counted in scrollEntries too)
        // + 1 coalesced (0.5f) + 9 staged (0.55f..1.0f) = 11 onScroll entries, each
        // requiring its own drain pass inside one holder loop. An 8-pass bound would
        // exit around 0.7f with the newest still pending, so exactly 11 proves the
        // drain ran past the old bound and delivered the latest.
        assertEquals(11, harness.overlay.scrollEntries.get())
        assertEquals(11, scrolls.size)
        // Ordering never regresses: delivered values move forward only.
        for (i in 1 until scrolls.size) {
            assertTrue(
                "scroll regressed at $i: $scrolls",
                scrolls[i] >= scrolls[i - 1],
            )
        }
    }

    @Test
    fun deathBeforeCloseDeliversLossBeforeCloseReturns() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val harness = Harness(
            beforeLoss = {
                entered.countDown()
                val released = release.await(10, TimeUnit.SECONDS)
                assertTrue("listener latch timed out", released)
            },
        )
        val death = harness.service.deathRecipients.single()
        val deathThread = thread { death.binderDied() }
        assertTrue(
            "loss delivery did not start",
            entered.await(10, TimeUnit.SECONDS),
        )

        // Loss committed before close: close must wait for the in-flight delivery.
        val closeDone = CountDownLatch(1)
        val closer = thread {
            harness.bridge.close()
            closeDone.countDown()
        }
        assertFalse(
            "close returned before loss delivered",
            closeDone.await(300, TimeUnit.MILLISECONDS),
        )
        release.countDown()
        assertTrue(
            "close hung after loss delivered",
            closeDone.await(10, TimeUnit.SECONDS),
        )
        deathThread.join(10_000)
        closer.join(10_000)

        assertFalse("dyer hung", deathThread.isAlive)
        assertFalse("closer hung", closer.isAlive)
        assertEquals(1, harness.losses.get())
    }

    @Test
    fun closeBeforeConcurrentDeathDeliversNoLoss() {
        val harness = Harness()
        harness.bridge.close()

        val death = harness.service.deathRecipients.single()
        val dyer = thread { repeat(50) { death.binderDied() } }
        dyer.join(10_000)

        assertFalse("dyer hung", dyer.isAlive)
        assertEquals(0, harness.losses.get())
        assertEquals(1, harness.service.unlinkCalls.get())
    }

    @Test
    fun lossListenerReentrantCloseDoesNotDeadlock() {
        val overlay = FakeOverlay()
        val service = FakeService(overlay)
        val losses = AtomicInteger(0)
        var bridgeRef: AidlFeedBridge? = null
        val bridge = AidlFeedBridge(service.binder, onConnectionLost = {
            losses.incrementAndGet()
            // Re-entrant teardown from the delivery thread must not deadlock the
            // bounded close-wait (same-thread skip).
            bridgeRef?.close()
        })
        bridgeRef = bridge

        service.deathRecipients.single().binderDied()
        // Re-entrant close already ran inside the listener; outer close is idempotent.
        bridge.close()

        assertEquals(1, losses.get())
        assertEquals(1, service.unlinkCalls.get())
    }

    // Window handshake.
    //
    // The provider renders no pixels until the host registers its window: windowAttached
    // carries the host params plus the bridge's single callback identity, windowDetached
    // unregisters. Same threading/teardown contract as the control verbs.

    @Test
    fun windowAttachRegistersParamsCallbackAndFlags() {
        val harness = Harness()
        val params = WindowManager.LayoutParams()

        harness.bridge.windowAttached(params, 0)

        assertEquals(1, harness.overlay.attaches.size)
        val record = harness.overlay.attaches.single()
        assertTrue("attach must carry host params", record.params != null)
        assertEquals(0, record.flags)
        assertTrue("attach must register the bridge callback", record.hasCallback)
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun windowDetachUnregistersWithChangingFlag() {
        val harness = Harness()
        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        assertEquals(listOf("attached"), harness.overlay.events.toList())

        harness.bridge.windowDetached(true)

        assertEquals(listOf(true), harness.overlay.detaches.toList())
        assertEquals(listOf("attached", "detached"), harness.overlay.events.toList())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun detachWithoutAttachIsSilent() {
        val harness = Harness()

        harness.bridge.windowDetached(false)

        assertTrue(harness.overlay.attaches.isEmpty())
        assertTrue(harness.overlay.detaches.isEmpty())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun repeatedAttachReRegistersLatestFlags() {
        val harness = Harness()

        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        harness.bridge.windowAttached(WindowManager.LayoutParams(), 7)

        assertEquals(listOf(0, 7), harness.overlay.attaches.map { it.flags })
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun attachCachesCopyNotCallerReference() {
        val harness = Harness()
        val params = WindowManager.LayoutParams()

        harness.bridge.windowAttached(params, 0)

        // The bridge caches a defensive copy (no-arg plus copyFrom on device), never
        // the caller's reference, so later caller mutation cannot corrupt the pending
        // registration. Field-level copy fidelity is framework code (copyFrom) and
        // stays device-verified: stubbed unit-test copies carry no fields.
        val delivered = harness.overlay.attaches.single().params
        assertTrue(delivered != null)
        assertTrue("bridge must not transact the caller's instance", delivered !== params)
    }

    @Test
    fun windowFlushesBeforeControlVerbs() {
        val harness = Harness()

        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        harness.bridge.setVisible(true)

        assertEquals(listOf("attached", "open"), harness.overlay.events.toList())
    }

    @Test
    fun attachFailureReportsLossOnce() {
        val harness = Harness()
        harness.overlay.failWith = RemoteException("dead")

        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        harness.bridge.setProgress(0.5f)

        assertEquals(1, harness.losses.get())
        assertTrue(harness.overlay.attaches.isEmpty())
    }

    @Test
    fun detachFailureReportsLossOnce() {
        val harness = Harness()
        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        assertEquals(1, harness.overlay.attaches.size)
        harness.overlay.failWith = RemoteException("dead")

        harness.bridge.windowDetached(false)
        harness.bridge.setVisible(true)

        assertEquals(1, harness.losses.get())
        assertTrue(harness.overlay.detaches.isEmpty())
    }

    @Test
    fun closeThenWindowOpsAreSilent() {
        val harness = Harness()
        harness.bridge.close()

        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        harness.bridge.windowDetached(false)

        assertTrue(harness.overlay.attaches.isEmpty())
        assertTrue(harness.overlay.detaches.isEmpty())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun windowDetachAfterCloseIsSilent() {
        val harness = Harness()
        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        harness.bridge.close()

        harness.bridge.windowDetached(false)

        assertEquals(1, harness.overlay.attaches.size)
        assertTrue(harness.overlay.detaches.isEmpty())
        assertEquals(0, harness.losses.get())
        assertEquals(1, harness.service.unlinkCalls.get())
    }

    @Test
    fun contendedAttachDeliversViaHandoff() {
        val harness = Harness()
        harness.overlay.blockScrolls = true
        val worker = thread { harness.bridge.setProgress(0.1f) }
        assertTrue(
            "transact did not start",
            harness.overlay.enteredScroll.await(10, TimeUnit.SECONDS),
        )

        // The lock is held inside the 0.1 transact: this caches the attach and
        // degrades via handoff; the holder flushes window ops before control verbs.
        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        harness.overlay.releaseScroll.countDown()
        worker.join(10_000)

        assertFalse("worker hung", worker.isAlive)
        assertEquals(1, harness.overlay.attaches.size)
        assertEquals(listOf("scroll", "attached"), harness.overlay.events.toList())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun contendedDetachDeliversViaHandoff() {
        val harness = Harness()
        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        harness.overlay.blockScrolls = true
        val worker = thread { harness.bridge.setProgress(0.1f) }
        assertTrue(
            "transact did not start",
            harness.overlay.enteredScroll.await(10, TimeUnit.SECONDS),
        )

        // The lock is held inside the 0.1 transact: this caches the detach and
        // degrades via handoff. The in-flight scroll transact started before the
        // detach was cached, so its event precedes; the holder still delivers the
        // contended detach in the same drain pass (window ops flush before the
        // control re-check), with no loss and no extra call.
        harness.bridge.windowDetached(true)
        harness.overlay.releaseScroll.countDown()
        worker.join(10_000)

        assertFalse("worker hung", worker.isAlive)
        assertEquals(listOf("attached", "scroll", "detached"), harness.overlay.events.toList())
        assertEquals(listOf(true), harness.overlay.detaches.toList())
        assertEquals(0, harness.losses.get())
    }

    // Provider callbacks.
    //
    // The bridge's event flows are buffer-1 with no replay: values emitted while a
    // collector is subscribed are delivered, but a late subscriber starts at the tail.
    // These tests therefore subscribe first (draining the test scheduler so the
    // collector is registered) and only then invoke the binder callback — the same
    // always-subscribed shape F2 uses on device.

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun scrollCallbackEmitsCoercedProgress() = runTest {
        val harness = Harness()
        val received = mutableListOf<Float>()
        val job = launch { harness.bridge.scrollEvents.collect { received += it } }
        runCurrent()

        harness.bridge.callback.overlayScrollChanged(2f)
        runCurrent()
        job.cancel()

        assertEquals(listOf(1f), received)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun scrollCallbackMapsNanToZero() = runTest {
        val harness = Harness()
        val received = mutableListOf<Float>()
        val job = launch { harness.bridge.scrollEvents.collect { received += it } }
        runCurrent()

        harness.bridge.callback.overlayScrollChanged(Float.NaN)
        runCurrent()
        job.cancel()

        assertEquals(listOf(0f), received)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun statusCallbackEmitsStatus() = runTest {
        val harness = Harness()
        val received = mutableListOf<Int>()
        val job = launch { harness.bridge.statusEvents.collect { received += it } }
        runCurrent()

        harness.bridge.callback.overlayStatusChanged(3)
        runCurrent()
        job.cancel()

        assertEquals(listOf(3), received)
    }

    // D4 protocol trace: binder → negotiation → window/session → render with
    // first-failure attribution. A completed oneway transact is never proof of the
    // next stage: only an observed provider callback confirms a session, and only
    // an explicit content signal confirms render.

    @Test
    fun windowAttachFailureReportsSessionStageFirst() {
        val harness = Harness()
        harness.overlay.failWith = RemoteException("dead")

        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)

        // Session-stage verdict alongside the transport loss: the window was never
        // registered, so the provider holds no session.
        assertEquals(
            listOf(
                FeedDiagnosticStatus.SURFACE_SESSION_FAILED to
                    FeedDiagnosticCause.WINDOW_TRANSACT_FAILED,
            ),
            harness.protocolFailures.toList(),
        )
        assertEquals(1, harness.losses.get())
        val trace = harness.bridge.protocolTrace()
        assertFalse(trace.windowSignalSent)
        assertFalse(trace.sessionConfirmed)
        assertEquals(FeedDiagnosticStatus.SURFACE_SESSION_FAILED, trace.firstFailure?.status)
        assertEquals(FeedDiagnosticCause.WINDOW_TRANSACT_FAILED, trace.firstFailure?.cause)
    }

    @Test
    fun controlFailureBeforeCallbackReportsNegotiationStage() {
        val harness = Harness()
        harness.overlay.failWith = RemoteException("dead")

        harness.bridge.setProgress(0.5f)

        // No provider callback was ever observed: the handshake itself broke, not a
        // live session.
        assertEquals(
            listOf(
                FeedDiagnosticStatus.NEGOTIATION_FAILED to
                    FeedDiagnosticCause.CONTROL_TRANSACT_FAILED,
            ),
            harness.protocolFailures.toList(),
        )
        assertEquals(1, harness.losses.get())
        assertEquals(
            FeedDiagnosticStatus.NEGOTIATION_FAILED,
            harness.bridge.protocolTrace().firstFailure?.status,
        )
    }

    @Test
    fun controlFailureAfterCallbackReportsLossOnly() {
        val harness = Harness()
        harness.bridge.callback.overlayScrollChanged(0.5f)
        assertTrue(harness.bridge.protocolTrace().sessionConfirmed)
        harness.overlay.failWith = RemoteException("dead")

        harness.bridge.setProgress(0.75f)

        // A callback was observed, so this is transport loss on a live session
        // (the gateway's DISCONNECTED path) — never a negotiation verdict.
        assertTrue(harness.protocolFailures.isEmpty())
        assertEquals(1, harness.losses.get())
        assertTrue(harness.bridge.protocolTrace().sessionConfirmed)
    }

    @Test
    fun successfulAttachMarksSignalSentWithoutSession() {
        val harness = Harness()
        assertFalse(harness.bridge.protocolTrace().windowSignalSent)

        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)

        // The oneway transact completed, but no provider callback was observed: the
        // signal left the host, the session stays unconfirmed.
        val trace = harness.bridge.protocolTrace()
        assertTrue(trace.windowSignalSent)
        assertFalse(trace.sessionConfirmed)
        assertFalse(trace.contentConfirmed)
        assertTrue(trace.firstFailure == null)
        assertTrue(harness.protocolFailures.isEmpty())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun statusCallbackConfirmsSessionWithoutAttach() {
        val harness = Harness()

        harness.bridge.callback.overlayStatusChanged(1)

        val trace = harness.bridge.protocolTrace()
        assertTrue(trace.sessionConfirmed)
        assertFalse(trace.windowSignalSent)
        assertFalse(trace.contentConfirmed)
    }

    @Test
    fun queryHasContentTrueConfirmsRender() {
        val harness = Harness()
        harness.overlay.hasContent = true
        harness.bridge.callback.overlayScrollChanged(0.5f)

        assertEquals(true, harness.bridge.queryHasContent())

        val trace = harness.bridge.protocolTrace()
        assertTrue(trace.sessionConfirmed)
        assertTrue(trace.contentConfirmed)
        assertTrue(harness.protocolFailures.isEmpty())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun queryHasContentFalseAfterSessionReportsRenderStage() {
        val harness = Harness()
        harness.bridge.callback.overlayScrollChanged(0.5f)

        // Explicit provider no-content signal after a confirmed session: the only
        // observed render-stage failure.
        assertEquals(false, harness.bridge.queryHasContent())

        assertEquals(
            listOf(
                FeedDiagnosticStatus.RENDERING_FAILED to
                    FeedDiagnosticCause.CONTENT_ABSENT,
            ),
            harness.protocolFailures.toList(),
        )
        assertTrue(harness.bridge.protocolTrace().contentConfirmed.not())
        assertEquals(
            FeedDiagnosticStatus.RENDERING_FAILED,
            harness.bridge.protocolTrace().firstFailure?.status,
        )
    }

    @Test
    fun queryHasContentFalseWithoutSessionStaysUnconfirmed() {
        val harness = Harness()

        // No callback was ever observed: absence of a signal is unconfirmed, never
        // a render failure.
        assertEquals(false, harness.bridge.queryHasContent())

        assertTrue(harness.protocolFailures.isEmpty())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun queryHasContentFailureReturnsNullWithoutLoss() {
        val harness = Harness()
        harness.overlay.failWith = RemoteException("dead")

        // A failed poll answers nothing and kills nothing: the next control or
        // window transact owns transport-loss reporting.
        assertEquals(null, harness.bridge.queryHasContent())

        assertTrue(harness.protocolFailures.isEmpty())
        assertEquals(0, harness.losses.get())
    }

    @Test
    fun protocolFailureReportedAtMostOnce() {
        val harness = Harness()
        harness.overlay.failWith = RemoteException("dead")

        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        harness.bridge.setProgress(0.5f)
        harness.bridge.setVisible(true)

        // First failure (session stage) wins; the later collapse adds no verdict.
        assertEquals(1, harness.protocolFailures.size)
        assertEquals(
            FeedDiagnosticStatus.SURFACE_SESSION_FAILED,
            harness.protocolFailures.single().first,
        )
        assertEquals(1, harness.losses.get())
    }

    @Test
    fun protocolFailureSilentAfterClose() {
        val harness = Harness()
        harness.bridge.close()
        harness.overlay.failWith = RemoteException("dead")

        harness.bridge.windowAttached(WindowManager.LayoutParams(), 0)
        harness.bridge.setProgress(0.5f)

        assertTrue(harness.protocolFailures.isEmpty())
        assertEquals(0, harness.losses.get())
        assertTrue(harness.bridge.protocolTrace().firstFailure == null)
    }

    private class Harness(
        linkThrows: Exception? = null,
        failQuery: Boolean = false,
        listenerThrows: Exception? = null,
        beforeLoss: (() -> Unit)? = null,
    ) {
        val overlay = FakeOverlay()
        val service = FakeService(overlay, linkThrows = linkThrows, failQuery = failQuery)
        val losses = AtomicInteger(0)
        val protocolFailures = Collections.synchronizedList(
            mutableListOf<Pair<FeedDiagnosticStatus, FeedDiagnosticCause>>(),
        )
        val bridge = AidlFeedBridge(
            service.binder,
            onConnectionLost = {
                beforeLoss?.invoke()
                losses.incrementAndGet()
                listenerThrows?.let { throw it }
            },
            onProtocolFailure = { status, cause ->
                protocolFailures += status to cause
            },
        )
    }

    private class FakeOverlay : ILauncherOverlay {
        val opens = Collections.synchronizedList(mutableListOf<Int>())
        val closes = Collections.synchronizedList(mutableListOf<Int>())
        val scrolls = Collections.synchronizedList(mutableListOf<Float>())
        /** Shared call log for cross-verb ordering checks (window vs control). */
        val events = Collections.synchronizedList(mutableListOf<String>())
        /** Window registrations in call order. */
        val attaches = Collections.synchronizedList(mutableListOf<AttachRecord>())
        /** `isChangingConfigurations` values of windowDetached calls in order. */
        val detaches = Collections.synchronizedList(mutableListOf<Boolean>())

        /** Window registration record: flags, callback presence and params identity. */
        data class AttachRecord(
            val flags: Int,
            val hasCallback: Boolean,
            val params: WindowManager.LayoutParams?,
        )

        @Volatile
        var failWith: Exception? = null

        /** Explicit provider content signal for [hasOverlayContent]; false by default. */
        @Volatile
        var hasContent: Boolean = false

        @Volatile
        var blockScrolls = false
        val enteredScroll = CountDownLatch(1)
        val releaseScroll = CountDownLatch(1)
        /** Same blocking gate for windowAttached transacts (contention tests). */
        @Volatile
        var blockAttach = false
        val enteredAttach = CountDownLatch(1)
        val releaseAttach = CountDownLatch(1)
        /** Counts started onScroll transacts; lets tests prove how many drain passes ran. */
        val scrollEntries = AtomicInteger(0)
        /**
         * Runs synchronously inside each onScroll transact (holder thread) before the
         * fake records the value. Tests use it to cache the next distinct value while
         * a transact is in flight, forcing one extra drain pass per staged value.
         * Null by default so all other tests see unchanged behavior.
         */
        @Volatile
        var scrollEntryHook: ((Int) -> Unit)? = null

        lateinit var binder: IBinder

        override fun asBinder(): IBinder = binder

        override fun startScroll() = Unit

        override fun onScroll(progress: Float) {
            failWith?.let { throw it }
            val entry = scrollEntries.incrementAndGet()
            try {
                scrollEntryHook?.invoke(entry)
            } catch (_: Exception) {
                // Test hooks must never break transport.
            }
            if (blockScrolls) {
                enteredScroll.countDown()
                val released = releaseScroll.await(10, TimeUnit.SECONDS)
                if (!released) throw RemoteException("test latch timed out")
            }
            scrolls.add(progress)
            events.add("scroll")
        }

        override fun endScroll() = Unit

        override fun windowAttached(
            lp: WindowManager.LayoutParams?,
            cb: ILauncherOverlayCallback?,
            flags: Int,
        ) {
            failWith?.let { throw it }
            if (blockAttach) {
                enteredAttach.countDown()
                val released = releaseAttach.await(10, TimeUnit.SECONDS)
                if (!released) throw RemoteException("test latch timed out")
            }
            attaches.add(AttachRecord(flags, cb != null, lp))
            events.add("attached")
        }

        override fun windowDetached(isChangingConfigurations: Boolean) {
            failWith?.let { throw it }
            detaches.add(isChangingConfigurations)
            events.add("detached")
        }

        override fun closeOverlay(flags: Int) {
            failWith?.let { throw it }
            closes.add(flags)
            events.add("close")
        }

        override fun onPause() = Unit

        override fun onResume() = Unit

        override fun openOverlay(flags: Int) {
            failWith?.let { throw it }
            opens.add(flags)
            events.add("open")
        }

        override fun requestVoiceDetection(start: Boolean) = Unit

        override fun getVoiceSearchLanguage(): String = "en-US"

        override fun isVoiceDetectionRunning(): Boolean = false

        override fun hasOverlayContent(): Boolean {
            failWith?.let { throw it }
            return hasContent
        }

        override fun windowAttached2(bundle: Bundle?, cb: ILauncherOverlayCallback?) = Unit

        override fun unusedMethod() = Unit

        override fun setActivityState(flags: Int) = Unit

        override fun startSearch(data: ByteArray?, bundle: Bundle?): Boolean = false
    }

    private class FakeService(
        overlay: FakeOverlay,
        private val linkThrows: Exception? = null,
        private val failQuery: Boolean = false,
    ) {
        val deathRecipients = Collections.synchronizedList(
            mutableListOf<IBinder.DeathRecipient>(),
        )
        val unlinkCalls = AtomicInteger(0)

        @Volatile
        var unlinkThrows: Exception? = null

        val binder: IBinder = Proxy.newProxyInstance(
            IBinder::class.java.classLoader,
            arrayOf(IBinder::class.java),
        ) { _, method, args ->
            when (method.name) {
                "queryLocalInterface" -> {
                    if (failQuery) throw RemoteException("no interface")
                    overlay
                }
                "getInterfaceDescriptor" -> ILauncherOverlay.Stub.DESCRIPTOR
                "linkToDeath" -> {
                    linkThrows?.let { throw it }
                    deathRecipients.add(args!![0] as IBinder.DeathRecipient)
                    null
                }
                "unlinkToDeath" -> {
                    unlinkCalls.incrementAndGet()
                    unlinkThrows?.let { throw it }
                    true
                }
                "isBinderAlive" -> true
                "pingBinder" -> true
                "describeContents" -> 0
                "toString" -> "FakeServiceBinder"
                "hashCode" -> System.identityHashCode(overlay)
                "equals" -> false
                else -> defaultReturn(method.returnType)
            }
        } as IBinder

        init {
            overlay.binder = binder
        }

        private fun defaultReturn(type: Class<*>): Any? = when (type) {
            Void.TYPE -> null
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            else -> null
        }
    }
}
