package me.foxtails.lakehead.platform.feed

import android.os.IBinder
import com.google.android.libraries.launcherclient.ILauncherOverlay
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Binder control channel behind [FeedOverlayConnection] for one connected overlay service.
 *
 * Overlay IPC provenance: the `com.google.android.libraries.launcherclient` AIDL pair
 * (`ILauncherOverlay.aidl`, `ILauncherOverlayCallback.aidl`) vendored under
 * `platform/feed/src/main/aidl/...` is byte-identical (1031 and 200 bytes) to Lawnchair's
 * `lawnchair/aidl/com/google/android/libraries/launcherclient/` copy at commit
 * `505dbc40e6154c05158b5d0271c45f6a885a411b` (LawnchairLauncher/lawnchair, branch `15-dev`,
 * 2026-04-11) — the Launcher3-family copy of the feed overlay protocol that direct
 * providers speak. Same package declaration and same method order; no local edits. The
 * AIDL package must stay `com.google.android.libraries.launcherclient`: the generated
 * stub's interface descriptor has to match the provider's service, so renaming it would
 * break every transact.
 *
 * F1 scope, by delegation: own the proxy (via `ILauncherOverlay.Stub.asInterface`), hold
 * the single `ILauncherOverlayCallback.Stub`, link the death recipient and forward the two
 * control verbs. Window attachment (`windowAttached`, which needs an activity
 * `WindowManager.LayoutParams`) and gateway promotion stay with F2/F3; the [callback]
 * accessor below is the handoff seam for that handshake.
 *
 * Threading contract, mirroring the gateway:
 * - Binder entry points ([callback], the death recipient) never throw and never suspend:
 *   the stub only `tryEmit`s into buffer-1 flows, wrapped in a catch-all.
 * - Every transact runs under [Mutex.tryLock], so there is never a second in-flight IPC.
 *   Contention never loses the latest request: each verb caches its newest value in a
 *   volatile, the lock holder drains every pending latest value (re-reading the caches
 *   after each transact), and a post-unlock re-check loops until quiescent. A `tryLock`
 *   failure is safe via handoff: the loser returns only because the holder is still in
 *   its drain loop (holding the lock or about to re-check), so the holder delivers the
 *   loser's newest write; a value cached after the holder's final check finds a free
 *   lock and drains itself. Idempotency compares requested values against
 *   the last *delivered* values, so repeating a value that has not been delivered yet
 *   still delivers it.
 * - Every transact is wrapped in try/catch: a dead binder degrades through
 *   [onConnectionLost] exactly once (which F2 wires to the gateway's `onConnectionLost`
 *   → honest `Unavailable` + single unbind), never as an exception to the caller.
 * - Public methods additionally catch-all so [FeedOverlayConnection]'s never-throw
 *   contract holds even against unexpected errors.
 *
 * Teardown contract: [close] and loss reporting share one guard, so the deliberate-
 * teardown flag and the loss-once flag transition atomically — loss is reported at
 * most once and never after [close] has taken effect. When loss is committed before
 * [close] takes effect, [close] waits (bounded) for the in-flight listener delivery
 * to complete before returning, while the listener itself still runs outside the
 * guard; a re-entrant [close] from the delivery thread never waits.
 *
 * @param service the connected overlay service binder, from `onServiceConnected`.
 * @param onConnectionLost terminal loss signal (binder death or failed transact), invoked
 * at most once and never after [close]; must itself never suspend.
 */
internal class AidlFeedBridge(
    private val service: IBinder,
    private val onConnectionLost: () -> Unit,
) : FeedOverlayConnection {

    private val mutex = Mutex()

    /**
     * Guards the [closed]/[lossReported] transition so [close] and [notifyLossOnce] decide
     * atomically: either the loss is committed before the teardown takes effect (and is
     * still reported exactly once) or the teardown wins and no loss is reported after it.
     */
    private val tearDownGuard = Any()

    @Volatile
    private var closed = false

    @Volatile
    private var lossReported = false

    /**
     * Fires when a committed loss delivery completes (listener returned or threw).
     * Stays at 1 when no loss was ever committed; [close] only awaits it when
     * [lossReported] is true, so the no-loss path never blocks.
     */
    private val lossComplete = CountDownLatch(1)

    /** Thread currently running the loss listener, if any; skipped by re-entrant [close]. */
    @Volatile
    private var lossDeliveryThread: Thread? = null

    /** Newest visibility requested; the drain delivers it even if an older value is in flight. */
    @Volatile
    private var lastVisible: Boolean? = null

    /** Visibility most recently transacted; idempotency is measured against this. */
    @Volatile
    private var deliveredVisible: Boolean? = null

    /** Newest coerced progress requested; the drain delivers it even under contention. */
    @Volatile
    private var lastProgress: Float? = null

    /** Progress most recently transacted; idempotency is measured against this. */
    @Volatile
    private var deliveredProgress: Float? = null

    private val proxy: ILauncherOverlay? = try {
        ILauncherOverlay.Stub.asInterface(service)
    } catch (_: Exception) {
        null
    }

    private val _scrollEvents = MutableSharedFlow<Float>(extraBufferCapacity = 1)
    private val _statusEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Provider scroll updates, delivered via `tryEmit` on the binder thread. */
    internal val scrollEvents: SharedFlow<Float> = _scrollEvents.asSharedFlow()

    /** Provider status updates, delivered via `tryEmit` on the binder thread. */
    internal val statusEvents: SharedFlow<Int> = _statusEvents.asSharedFlow()

    /**
     * The single overlay callback binder for this connection.
     *
     * Held (not yet registered: registration rides on `windowAttached`, owned by F2)
     * so there is exactly one callback identity per connection.
     */
    internal val callback: ILauncherOverlayCallback = object : ILauncherOverlayCallback.Stub() {
        override fun overlayScrollChanged(progress: Float) {
            try {
                _scrollEvents.tryEmit(coerceOverlayProgress(progress))
            } catch (_: Throwable) {
                // Binder threads must never observe an exception from this callback.
            }
        }

        override fun overlayStatusChanged(status: Int) {
            try {
                _statusEvents.tryEmit(status)
            } catch (_: Throwable) {
                // Binder threads must never observe an exception from this callback.
            }
        }
    }

    private val deathRecipient = IBinder.DeathRecipient { notifyLossOnce() }

    init {
        try {
            service.linkToDeath(deathRecipient, 0)
        } catch (_: Exception) {
            // Already dead: degrade through the same single loss path.
            notifyLossOnce()
        }
    }

    override fun setVisible(visible: Boolean) {
        try {
            lastVisible = visible
            if (closed) return
            deliverPending()
        } catch (_: Throwable) {
            // Never throws to callers; transport loss is already routed to onConnectionLost.
        }
    }

    override fun setProgress(progress: Float) {
        try {
            lastProgress = coerceOverlayProgress(progress)
            if (closed) return
            deliverPending()
        } catch (_: Throwable) {
            // Never throws to callers; transport loss is already routed to onConnectionLost.
        }
    }

    override fun close() {
        try {
            val first = synchronized(tearDownGuard) {
                if (closed) {
                    false
                } else {
                    closed = true
                    true
                }
            }
            if (first) {
                try {
                    service.unlinkToDeath(deathRecipient, 0)
                } catch (_: Exception) {
                    // Best-effort: the binder may already be gone; close stays silent.
                }
            }
            // Ordering: loss committed before this teardown took effect must be
            // delivered before close returns. The listener runs outside the guard,
            // so wait (bounded) for its completion. Re-entrant close from the
            // delivery thread itself never waits; later closes observe the fired
            // latch and return immediately.
            if (lossReported && Thread.currentThread() !== lossDeliveryThread) {
                try {
                    lossComplete.await(LOSS_DELIVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (_: Exception) {
                    // Bounded wait is best-effort; close stays silent on interrupt.
                }
            }
        } catch (_: Throwable) {
            // Terminal teardown never throws.
        }
    }

    /**
     * Delivers every pending latest value under [Mutex.tryLock].
     *
     * The caller always caches first, so a `tryLock` failure is safe via handoff:
     * whoever holds the lock drains all pending values (see [drainLocked]), including
     * the loser's newest write. After unlocking, the holder re-checks and loops until
     * quiescent, so there is no bounded pass limit that can exit with pending work:
     * a value cached before the holder's final check is seen by that check, and a
     * value cached after it finds a free lock and drains itself. The loop still
     * terminates on transport loss ([lossReported]) or [close], where further
     * transacts cannot make progress — pending caches stay cached but silent.
     * Silent when [close]d.
     */
    private fun deliverPending() {
        while (true) {
            if (!mutex.tryLock()) return
            try {
                drainLocked()
            } finally {
                mutex.unlock()
            }
            if (!hasPending() || closed || lossReported) return
        }
    }

    private fun hasPending(): Boolean =
        lastVisible != deliveredVisible || lastProgress != deliveredProgress

    /**
     * Sends the newest cached value of each verb while holding the mutex.
     *
     * Caches are re-read after every transact, so a value cached while an IPC is in
     * flight is delivered on the next loop pass instead of being lost, and delivery
     * order within a verb always moves forward: a send only happens when the cache
     * differs from the last delivered value, so an older value can never be sent after
     * a newer one. A failed transact (dead binder) reports loss once. Silent when
     * [close]d or when the proxy was never usable (the latter also reports loss, since an
     * unusable proxy is a dead connection, not a deliberate teardown).
     */
    private fun drainLocked() {
        while (true) {
            val visible = lastVisible
            val needVisible = visible != null && visible != deliveredVisible
            val progress = lastProgress
            val needProgress = progress != null && progress != deliveredProgress
            if (!needVisible && !needProgress) return
            if (closed) return
            val overlay = proxy
            if (overlay == null) {
                notifyLossOnce()
                return
            }
            try {
                if (needVisible && visible != null) {
                    if (visible) {
                        overlay.openOverlay(OVERLAY_FLAGS)
                    } else {
                        overlay.closeOverlay(OVERLAY_FLAGS)
                    }
                    deliveredVisible = visible
                }
                // Re-read: a concurrent writer may have advanced the cache while the
                // visible transact above ran; always send the newest value.
                val latestProgress = lastProgress
                if (latestProgress != null && latestProgress != deliveredProgress) {
                    overlay.onScroll(latestProgress)
                    deliveredProgress = latestProgress
                }
            } catch (_: Exception) {
                notifyLossOnce()
                return
            }
        }
    }

    /**
     * Reports transport loss at most once, never after [close], and never throws —
     * including when invoked on a binder thread or when the listener itself misbehaves.
     *
     * The [closed]/[lossReported] check-and-commit runs under [tearDownGuard] together
     * with [close]'s transition, so a death callback racing a deliberate teardown cannot
     * report loss after the teardown has taken effect. The listener runs outside the
     * guard; [close] waits (bounded) for this delivery when the commit happened before
     * the teardown, giving death→close ordering (1 loss delivered before close
     * returns) and close→death silence (0 loss).
     */
    private fun notifyLossOnce() {
        try {
            val first = synchronized(tearDownGuard) {
                if (closed || lossReported) {
                    false
                } else {
                    lossReported = true
                    true
                }
            }
            if (!first) return
            lossDeliveryThread = Thread.currentThread()
            try {
                onConnectionLost()
            } catch (_: Throwable) {
                // Listener failures must not surface on binder threads.
            } finally {
                lossDeliveryThread = null
                lossComplete.countDown()
            }
        } catch (_: Throwable) {
            // Loss reporting itself never throws.
        }
    }

    companion object {
        /**
         * Flags for `openOverlay`/`closeOverlay`.
         *
         * `0` is the default behavior; F2 may tune once provider-specific open semantics
         * are device-verified.
         */
        private const val OVERLAY_FLAGS: Int = 0

        /**
         * Bounded wait in [close] for an in-flight loss delivery committed before the
         * teardown took effect. Best-effort: expiry just lets a wedged listener avoid
         * hanging teardown; the loss was still committed exactly once.
         */
        private const val LOSS_DELIVERY_TIMEOUT_MS: Long = 5_000L
    }
}
