package me.foxtails.lakehead.platform.feed

import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
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
 * control verbs. Window attachment (`windowAttached`, which carries an activity-derived
 * `WindowManager.LayoutParams` plus the single [callback] identity) is also owned here
 * behind [FeedOverlayWindowConnection]: without it the provider renders no pixels even
 * while bound, so the gateway forwards the host's window handshake through this bridge
 * and the [callback] accessor below is the identity that handshake registers. The
 * gateway builds the params from the host `Activity` at the platform boundary; this
 * bridge only forwards the copy it is given.
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
 * D4 protocol trace: this bridge owns the [FeedProtocolTrace] for its connection —
 * binder (construction + death link) → negotiation (control transacts) →
 * window/session (`windowAttached` signal vs observed provider callbacks) → render
 * (explicit `hasOverlayContent` signal). A transact failure is attributed to the
 * earliest stage it proves broken and reported once via [onProtocolFailure] alongside
 * the transport-loss signal:
 * - `windowAttached` transact failure → `SURFACE_SESSION_FAILED`/`WINDOW_TRANSACT_FAILED`;
 * - control transact failure before any provider callback → `NEGOTIATION_FAILED`/
 *   `CONTROL_TRANSACT_FAILED` (the handshake itself broke, not a live session);
 * - control transact failure after a callback → transport loss only (the gateway's
 *   `DISCONNECTED` path owns live-session loss);
 * - explicit `hasOverlayContent() == false` after a confirmed session (observed only
 *   via [queryHasContent], never by timeout) → `RENDERING_FAILED`/`CONTENT_ABSENT`.
 * A completed `windowAttached` transact is oneway with no acknowledgement, so it
 * marks "signal sent" only — session proof requires an observed provider callback,
 * and the host surface tag is never render proof. Unobserved stages stay
 * unconfirmed, never invented.
 *
 * @param service the connected overlay service binder, from `onServiceConnected`.
 * @param onConnectionLost terminal loss signal (binder death or failed transact), invoked
 * at most once and never after [close]; must itself never suspend.
 * @param onProtocolFailure first protocol-stage failure, invoked at most once and never
 * after [close]; carries the earliest broken stage so the gateway can diagnose it
 * instead of a generic disconnect. Must itself never suspend; defaults to a no-op so
 * callers that only track transport loss keep their behavior.
 * @param onTraceChanged invoked with the new [FeedProtocolTrace] snapshot after every
 * stage transition that actually changes it (idempotent re-confirmations are silent);
 * may run on a binder thread, must never suspend, and its failures are swallowed.
 */
internal class AidlFeedBridge(
    private val service: IBinder,
    private val onConnectionLost: () -> Unit,
    private val onProtocolFailure: (FeedDiagnosticStatus, FeedDiagnosticCause) -> Unit = { _, _ -> },
    private val onTraceChanged: (FeedProtocolTrace) -> Unit = {},
    private val apiVersion: Int = 1,
) : FeedOverlayConnection, FeedOverlayWindowConnection {

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

    /**
     * D4 protocol trace snapshot for this connection.
     *
     * Immutable value behind a volatile: every stage transition publishes a new snapshot,
     * so readers ([protocolTrace]) always see a consistent trace without locking.
     */
    @Volatile
    private var trace = FeedProtocolTrace()

    /** Serializes trace read-modify-writes in [publishTrace]. */
    private val traceLock = Any()

    /** Whether [onProtocolFailure] has fired; first protocol failure wins, like loss. */
    @Volatile
    private var protocolFailureReported = false

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

    /**
     * Newest window params requested; the drain registers them even if an older attach
     * is in flight. Stored as a defensive copy so the caller cannot mutate the
     * pending registration; null means no attach is pending.
     */
    @Volatile
    private var lastWindowParams: WindowManager.LayoutParams? = null

    /** Host configuration sent in the `windowAttached2` bundle; null omits the key. */
    @Volatile
    private var lastWindowConfiguration: Configuration? = null

    /** Flags for the pending [lastWindowParams] registration. */
    @Volatile
    private var lastWindowFlags: Int = WINDOW_ATTACH_FLAGS

    /**
     * Whether the provider currently holds our window registration.
     *
     * Set only by the drain after a successful `windowAttached` transact, cleared only
     * after a successful `windowDetached` transact or by [close] (teardown drops the
     * registration without IPC: the subsequent unbind ends the provider side).
     */
    @Volatile
    private var windowAttachedLive: Boolean = false

    /**
     * Pending `windowDetached` request with its `isChangingConfigurations` argument.
     * Null means no detach is pending. A detach for a window that was never registered
     * ([windowAttachedLive] false) is dropped without IPC; otherwise detach wins over
     * a co-pending attach (the attach is dropped, the provider never held the window).
     */
    @Volatile
    private var pendingWindowDetachChanging: Boolean? = null

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
                // An observed provider callback is the only session proof: the
                // oneway windowAttached transact below can never confirm it.
                publishTrace { it.withSessionConfirmed() }
                _scrollEvents.tryEmit(coerceOverlayProgress(progress))
            } catch (_: Throwable) {
                // Binder threads must never observe an exception from this callback.
            }
        }

        override fun overlayStatusChanged(status: Int) {
            try {
                publishTrace { it.withSessionConfirmed() }
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

    /**
     * Registers the host window with the bound overlay service.
     *
     * Caches a defensive copy of [params] with [flags] first, so a `tryLock` failure
     * is safe via handoff exactly like the control verbs: whoever holds the lock
     * flushes window ops before control verbs in [drainLocked], and the post-unlock
     * re-check ([hasPending] covers window state) loops until quiescent. Repeating
     * the call replaces the pending registration; a co-pending detach (see
     * [windowDetached]) drops the attach without IPC when the provider never held
     * the window. Silent when [close]d.
     */
    override fun windowAttached(params: WindowManager.LayoutParams, flags: Int) {
        try {
            lastWindowParams = copyWindowParams(params)
            lastWindowFlags = flags
            if (closed) return
            deliverPending()
        } catch (_: Throwable) {
            // Never throws to callers; transport loss is already routed to onConnectionLost.
        }
    }

    /**
     * Unregisters the host window from the bound overlay service.
     *
     * Caches [isChangingConfigurations] first with the same handoff contract as
     * [windowAttached]. A detach for a window that was never registered is dropped
     * without IPC; otherwise detach wins over a co-pending attach. Clears the live
     * flag on teardown ([close]) without IPC: the gateway's unbind ends the
     * provider side. Silent when [close]d.
     */
    override fun windowConfiguration(configuration: Configuration?) {
        lastWindowConfiguration = configuration
    }

    override fun windowDetached(isChangingConfigurations: Boolean) {
        try {
            pendingWindowDetachChanging = isChangingConfigurations
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
                // Teardown drops the window registration without IPC: the gateway
                // unbinds next, which ends the provider side. Clearing here keeps a
                // post-close attach from ever transacting (deliverPending is gated
                // on [closed], and the drain below also bails when closed).
                lastWindowParams = null
                pendingWindowDetachChanging = null
                windowAttachedLive = false
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
     * Current D4 protocol-trace snapshot for this connection.
     *
     * A consistent immutable value: `windowSignalSent` proves only that the oneway
     * attach left the host, `sessionConfirmed` proves an observed provider callback,
     * `contentConfirmed` proves an explicit provider content signal, and `firstFailure`
     * names the earliest broken stage (null while unfailed). Never throws.
     */
    fun protocolTrace(): FeedProtocolTrace =
        try {
            trace
        } catch (_: Throwable) {
            FeedProtocolTrace()
        }

    /**
     * Best-effort poll of the provider's explicit content signal.
     *
     * Returns `true` when the provider reports it holds overlay content (and marks
     * the trace content-confirmed), `false` when it explicitly reports no content
     * after a confirmed session — the only observed render-stage failure, reported
     * once as `RENDERING_FAILED`/`CONTENT_ABSENT` — and null when the query itself
     * cannot answer (no proxy, torn down, or failed transact). A `false` without a
     * confirmed session, and any failure of this poll, stays unconfirmed: absence of
     * a signal is never a failure, and this poll never reports transport loss (the
     * next control or window transact owns that).
     *
     * Blocking binder call: invoke off the main thread (device and D5 tooling only).
     * Never throws.
     */
    fun queryHasContent(): Boolean? {
        return try {
            val overlay = proxy ?: return null
            if (closed) return null
            val hasContent = overlay.hasOverlayContent()
            if (hasContent) {
                publishTrace { it.withContentConfirmed() }
            } else if (trace.sessionConfirmed) {
                notifyProtocolFailureOnce(
                    FeedDiagnosticStatus.RENDERING_FAILED,
                    FeedDiagnosticCause.CONTENT_ABSENT,
                )
            }
            hasContent
        } catch (_: Exception) {
            null
        } catch (_: Throwable) {
            null
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
        lastVisible != deliveredVisible || lastProgress != deliveredProgress ||
            lastWindowParams != null || pendingWindowDetachChanging != null

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
     *
     * Window ops flush first on every pass (see [drainWindowLocked]): the provider
     * needs the registration before openOverlay/onScroll can show or move pixels.
     */
    private fun drainLocked() {
        while (true) {
            // Window ops flush first: the provider needs the registration before
            // openOverlay/onScroll can show or move any pixels.
            if (!drainWindowLocked()) return
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
                // First-failure attribution: a control transact that fails before any
                // provider callback broke the negotiation itself
                // (NEGOTIATION_FAILED); after a callback the same failure is
                // transport loss on a live session, owned by the gateway's
                // DISCONNECTED path, so no protocol failure is reported.
                if (!trace.sessionConfirmed) {
                    notifyProtocolFailureOnce(
                        FeedDiagnosticStatus.NEGOTIATION_FAILED,
                        FeedDiagnosticCause.CONTROL_TRANSACT_FAILED,
                    )
                }
                notifyLossOnce()
                return
            }
        }
    }

    /**
     * Flushes pending window ops while holding the mutex; true when the caller should
     * continue to the control verbs, false when the drain must stop (closed or dead
     * transport).
     *
     * Ordering: a co-pending detach wins over an undelivered attach (the provider
     * never held the window, so both collapse to at most one `windowDetached` when
     * live, or nothing when never registered). Otherwise the pending attach registers
     * first. A failed transact reports loss once and stops the drain. Silent when
     * [close]d. The caller must hold the mutex via `tryLock`.
     */
    private fun drainWindowLocked(): Boolean {
        while (true) {
            if (closed) return false
            val overlay = proxy
            if (overlay == null) {
                // An unusable proxy is a dead connection — but only when there is
                // actual window work pending. A null proxy with nothing to register
                // must not convert an otherwise healthy channel into a loss.
                if (lastWindowParams == null && pendingWindowDetachChanging == null) return true
                notifyLossOnce()
                return false
            }
            val detachChanging = pendingWindowDetachChanging
            if (detachChanging != null) {
                if (windowAttachedLive) {
                    try {
                        overlay.windowDetached(detachChanging)
                    } catch (_: Exception) {
                        // The window was live, so this is transport loss on an
                        // established session (the gateway's DISCONNECTED path),
                        // never a session-stage failure.
                        notifyLossOnce()
                        return false
                    }
                    windowAttachedLive = false
                }
                // Either way the detach is consumed, and a co-pending attach for a
                // window the provider never held is dropped with it.
                pendingWindowDetachChanging = null
                lastWindowParams = null
                continue
            }
            val params = lastWindowParams
            if (params != null) {
                try {
                    if (apiVersion < WINDOW_ATTACHED2_MIN_API) {
                        overlay.windowAttached(params, callback, lastWindowFlags)
                    } else {
                        overlay.windowAttached2(
                            windowBundle(params, lastWindowFlags, lastWindowConfiguration),
                            callback,
                        )
                    }
                } catch (_: Exception) {
                    // The host window was never registered, so the provider holds
                    // no session: this is the first failure at the session stage,
                    // reported alongside the transport loss below.
                    notifyProtocolFailureOnce(
                        FeedDiagnosticStatus.SURFACE_SESSION_FAILED,
                        FeedDiagnosticCause.WINDOW_TRANSACT_FAILED,
                    )
                    notifyLossOnce()
                    return false
                }
                try {
                    sendActivityStateLocked(overlay)
                } catch (_: Exception) {
                    notifyLossOnce()
                    return false
                }
                // Oneway with no acknowledgement: the signal left the host, but only
                // an observed provider callback (see [callback]) proves a session.
                publishTrace { it.withWindowSignalSent() }
                windowAttachedLive = true
                lastWindowParams = null
                continue
            }
            return true
        }
    }

    /**
     * Tells the provider the host activity state right after attach, as the reference
     * client's `exchangeConfig` does: `setActivityState` from API 4, else
     * `onResume`/`onPause`. The default state is started and resumed.
     */
    private fun sendActivityStateLocked(overlay: ILauncherOverlay) {
        if (apiVersion >= ACTIVITY_STATE_MIN_API) {
            overlay.setActivityState(ACTIVITY_STARTED or ACTIVITY_RESUMED)
        } else {
            overlay.onResume()
        }
    }

    /**
     * Defensive copy of window params for the pending registration cache.
     *
     * Built with the public no-arg constructor plus [WindowManager.LayoutParams.copyFrom]
     * (there is no public copy constructor in the compile stubs). Falls back to the
     * caller's reference when the copy itself throws (best-effort: the transact still
     * runs, and teardown clears the reference). Never throws.
     */
    private fun windowBundle(
        params: WindowManager.LayoutParams,
        flags: Int,
        configuration: Configuration?,
    ): Bundle = Bundle().apply {
        putParcelable(BUNDLE_LAYOUT_PARAMS, params)
        if (configuration != null) putParcelable(BUNDLE_CONFIGURATION, configuration)
        putInt(BUNDLE_CLIENT_OPTIONS, flags)
    }

    private fun copyWindowParams(params: WindowManager.LayoutParams): WindowManager.LayoutParams =
        try {
            WindowManager.LayoutParams().apply { copyFrom(params) }
        } catch (_: Exception) {
            params
        } catch (_: Throwable) {
            params
        }

    /**
     * Applies one monotonic trace [update], and reports the new snapshot to
     * [onTraceChanged] only when it differs from the previous one.
     *
     * The read-modify-write is synchronized so concurrent binder-thread callbacks
     * cannot lose a confirmation; the listener runs outside the lock and never throws.
     */
    private fun publishTrace(update: (FeedProtocolTrace) -> FeedProtocolTrace) {
        val next = synchronized(traceLock) {
            val updated = update(trace)
            if (updated == trace) return
            trace = updated
            updated
        }
        try {
            onTraceChanged(next)
        } catch (_: Throwable) {
            // Listener failures must not surface on binder threads.
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

    /**
     * Reports the first protocol-stage failure at most once, never after [close],
     * and never throws — including when invoked on a binder thread or when the
     * listener itself misbehaves.
     *
     * Shares [tearDownGuard] with [close] and [notifyLossOnce] so the at-most-once
     * decision is atomic against teardown: a transact failure racing a deliberate
     * teardown reports nothing after the teardown has taken effect. Records the
     * verdict in [trace] first so [protocolTrace] agrees with the listener even if
     * the listener itself throws. The listener runs outside the guard.
     */
    private fun notifyProtocolFailureOnce(
        status: FeedDiagnosticStatus,
        cause: FeedDiagnosticCause,
    ) {
        try {
            val first = synchronized(tearDownGuard) {
                if (closed || protocolFailureReported) {
                    false
                } else {
                    protocolFailureReported = true
                    true
                }
            }
            if (!first) return
            publishTrace { it.withFirstFailure(FeedDiagnostic(status = status, cause = cause)) }
            try {
                onProtocolFailure(status, cause)
            } catch (_: Throwable) {
                // Listener failures must not surface on binder threads.
            }
        } catch (_: Throwable) {
            // Failure reporting itself never throws.
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
         * Flags for `windowAttached`.
         *
         * Bit 0 is "overlay enabled" (reference `LauncherClient.setEnableFeed`); with 0 the
         * provider is asked for no feed and never answers.
         */
        internal const val WINDOW_ATTACH_FLAGS: Int = 1

        /** Service API version (`service.api.version`) from which `windowAttached2` is used. */
        internal const val WINDOW_ATTACHED2_MIN_API: Int = 3

        /** Service API version from which `setActivityState` replaces `onResume`/`onPause`. */
        internal const val ACTIVITY_STATE_MIN_API: Int = 4
        internal const val ACTIVITY_STARTED: Int = 1
        internal const val ACTIVITY_RESUMED: Int = 2

        private const val BUNDLE_LAYOUT_PARAMS = "layout_params"
        private const val BUNDLE_CONFIGURATION = "configuration"
        private const val BUNDLE_CLIENT_OPTIONS = "client_options"

        /**
         * Bounded wait in [close] for an in-flight loss delivery committed before the
         * teardown took effect. Best-effort: expiry just lets a wedged listener avoid
         * hanging teardown; the loss was still committed exactly once.
         */
        private const val LOSS_DELIVERY_TIMEOUT_MS: Long = 5_000L
    }
}
