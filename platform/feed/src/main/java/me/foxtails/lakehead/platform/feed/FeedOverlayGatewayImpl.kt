package me.foxtails.lakehead.platform.feed

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Binds one overlay [ServiceConnection] for a package name.
 *
 * String-based on purpose: the gateway passes stable package names from [FeedProviderId] and the
 * production implementation builds the `Intent` at the platform boundary, so unit tests can fake
 * binding without constructing Android framework objects.
 */
internal interface FeedServiceConnector {
    /** Returns true when the bind for [packageName] was accepted. */
    fun bind(connection: ServiceConnection, packageName: String): Boolean

    /** Releases [connection]; a no-op when it is not bound. Never throws. */
    fun unbind(connection: ServiceConnection)
}

/**
 * Watches provider installs, removals and updates.
 *
 * The production implementation registers a package-change `BroadcastReceiver`; fakes capture
 * the listener so tests can fire package changes deterministically.
 */
internal interface FeedPackageMonitor {
    /** Starts delivering package changes to [onPackageChanged]; replaces any previous listener. */
    fun start(onPackageChanged: () -> Unit)

    /** Stops delivery and drops the listener. Idempotent. */
    fun stop()
}

/**
 * Platform gateway behind [FeedOverlayGateway].
 *
 * Production construction is `FeedOverlayGatewayImpl(applicationContext, resolver,
 * ioDispatcher)`: the context is held as an application context and every `Context`,
 * `ServiceConnection`, `IBinder` and receiver type stays in this file, never leaking into
 * composables or `core:model`. The internal constructor swaps the connector and monitor for
 * fakes so JVM tests exercise the state machine without touching framework objects.
 *
 * Behavior contract:
 * - Resolution always runs off-main on [ioDispatcher]; [state] starts as
 *   `Unavailable(NO_PROVIDER)` until the first resolution completes, and [start] auto-binds
 *   the resolved provider (unbinding when nothing resolves).
 * - Exactly one connection exists at a time; attaching the same provider reuses it, attaching a
 *   different one unbinds first.
 * - Package changes re-resolve off-main, auto-bind the newly available provider
 *   (unbinding first on a switch), auto-detach when nothing resolves, and no-op when the
 *   same provider stays bound.
 * - `notifyOverlayVisible`/`notifyOverlayProgress` cache the host's latest request and
 *   forward it to the bound control channel ([FeedOverlayConnection]); a late connect
 *   re-delivers the cached values (catch-up), so requests made while unbound are not lost.
 *   The cache is retained across binds — only the connection is cleared.
 * - `onServiceDisconnected`/`onBindingDied`/binder death clear the single connection
 *   (closing the bridge, unbinding exactly once) and emit `Unavailable` without ever
 *   throwing; binder callbacks use `tryEmit` because they cannot suspend.
 * - Short critical sections are serialized with [mutex]. The public methods cannot suspend, so
 *   they use [Mutex.tryLock] rather than holding the mutex across IPC or resolution. Production
 *   callers are main-thread confined, where the lock is uncontended; contention degrades to a
 *   no-op rather than a second connection.
 */
class FeedOverlayGatewayImpl internal constructor(
    private val resolver: FeedProviderResolver,
    private val ioDispatcher: CoroutineDispatcher,
    private val connector: FeedServiceConnector,
    private val packageMonitor: FeedPackageMonitor,
) : FeedOverlayGateway {

    /**
     * Production entry point.
     *
     * @param applicationContext the host application context; retained, never an activity.
     * @param resolver pure decision owner from C1; probed off-main.
     * @param ioDispatcher dispatcher for every [FeedProviderResolver.resolve] call.
     */
    constructor(
        applicationContext: Context,
        resolver: FeedProviderResolver,
        ioDispatcher: CoroutineDispatcher,
    ) : this(
        resolver = resolver,
        ioDispatcher = ioDispatcher,
        connector = AndroidFeedConnector(applicationContext.applicationContext),
        packageMonitor = AndroidPackageMonitor(applicationContext.applicationContext),
    )

    private val mutex = Mutex()

    private val _state = MutableStateFlow<FeedAvailability>(
        FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
    )
    override val state: StateFlow<FeedAvailability> = _state

    @Volatile
    private var scope: CoroutineScope? = null
    private var resolveJob: Job? = null
    private var boundProvider: FeedProviderId? = null
    private var boundPackage: String? = null

    /**
     * Latest overlay progress requested by the host (0..1, `NaN` mapped to `0`).
     *
     * Cached so a late bind can catch up: [deliverToOverlay] re-delivers this value on every
     * connect. Retained across binds and unbinds — only the connection is cleared on
     * disconnect/binder death, never this cache.
     */
    @Volatile
    var overlayProgress: Float = 0f
        private set

    /**
     * Whether the host currently shows the overlay page.
     *
     * Same caching contract as [overlayProgress]: retained across binds, delivered only while
     * connected, never throws.
     */
    @Volatile
    var overlayVisible: Boolean = false
        private set

    /**
     * The live control channel for the single bound overlay service, null while unbound.
     *
     * Installed by [onOverlayConnected] (from `onServiceConnected` via [AidlFeedBridge], or
     * directly by JVM tests with a fake) and cleared by [unbindLocked] on every teardown path
     * — deliberate detach, package-switch rebind, disconnect and binder death — so there is
     * never a stale bridge after the connection is gone. Reads are lock-free snapshots;
     * the channel itself never throws.
     */
    @Volatile
    private var overlayBridge: FeedOverlayConnection? = null

    /**
     * Generation counter for the single bind, incremented on every teardown in
     * [unbindLocked]. Each successful bind captures the post-teardown value in its own
     * [OverlayConnection.epoch]; [onOverlayConnected] and [onBridgeLoss] accept only the
     * current generation, so a delayed callback from a previous bind is closed and dropped
     * instead of replacing the live bridge. Read lock-free (volatile); written only while
     * holding [mutex].
     */
    @Volatile
    internal var bindEpoch: Long = 0
        private set

    /**
     * Deferred transport losses observed while [mutex] was held by another path, scoped by
     * bind generation.
     *
     * Set lock-free by the contended branch of [onConnectionLost]/[onBridgeLoss] via
     * [markPendingLoss] when the loss epoch still matches [bindEpoch], even before the
     * bridge is published (a dead binder reports synchronously during construction while
     * [overlayBridge] is still null). A spurious current-epoch mark with no bind is
     * consumed without publish. Consumed under lock by [onOverlayConnected] after catch-up delivery
     * (take-check, publish, re-check) and by the background [schedulePendingLossDrain] for losses
     * that contended against non-connect holders (re-resolution, attach). Per-epoch keys
     * keep a stale generation's deferred loss from ever tearing down or overwriting its
     * replacement: [takePendingLoss] removes only the caller's own epoch.
     */
    private val pendingLossEpochs: MutableSet<Long> =
        Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    private var boundConnection: ServiceConnection? = null

    override fun start(scope: CoroutineScope) {
        if (!mutex.tryLock()) return
        try {
            if (this.scope != null) return
            this.scope = scope
            packageMonitor.start(::onPackageChanged)
        } finally {
            mutex.unlock()
        }
        // Launched after unlocking: an undispatched dispatcher runs the body eagerly on the
        // calling thread, and the body needs [mutex.tryLock] for itself. Holding the lock
        // across launch would self-contend and silently drop the first resolution.
        resolveJob = scope.launch(ioDispatcher) {
            val availability = try {
                resolver.resolve()
            } catch (_: SecurityException) {
                FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.PERMISSION_DENIED)
            } catch (_: Exception) {
                FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED)
            }
            if (!mutex.tryLock()) return@launch
            try {
                if (this@FeedOverlayGatewayImpl.scope !== scope) return@launch
                if (availability is FeedAvailability.Available) {
                    if (boundProvider == availability.provider && boundPackage != null) {
                        _state.emit(availability)
                    } else {
                        tryBindLocked(
                            provider = availability.provider,
                            fallbackReason = FeedAvailability.Unavailable.Reason.QUERY_FAILED,
                        )
                    }
                } else {
                    unbindLocked()
                    _state.emit(availability)
                }
            } finally {
                mutex.unlock()
            }
        }
    }

    override fun stop() {
        if (!mutex.tryLock()) return
        try {
            if (scope == null) return
            scope = null
            resolveJob?.cancel()
            resolveJob = null
            packageMonitor.stop()
            unbindLocked()
        } finally {
            mutex.unlock()
        }
    }

    override fun attach(provider: FeedProviderId) {
        val currentScope = scope ?: return
        currentScope.launch(ioDispatcher) {
            val availability = try {
                resolver.resolve()
            } catch (_: SecurityException) {
                FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.PERMISSION_DENIED)
            } catch (_: Exception) {
                FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED)
            }
            if (!mutex.tryLock()) return@launch
            try {
                if (this@FeedOverlayGatewayImpl.scope !== currentScope) return@launch
                if (availability is FeedAvailability.Available && availability.provider == provider) {
                    if (boundProvider == provider && boundPackage != null) {
                        _state.emit(availability)
                    } else {
                        tryBindLocked(
                            provider = provider,
                            fallbackReason = FeedAvailability.Unavailable.Reason.QUERY_FAILED,
                        )
                    }
                } else {
                    unbindLocked()
                    _state.emit(availability)
                }
            } finally {
                mutex.unlock()
            }
        }
    }

    override fun detach() {
        if (!mutex.tryLock()) return
        try {
            unbindLocked()
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Forwards a host visibility change toward the bound overlay.
     *
     * Caches [visible] first so the value survives unbinds and is re-delivered on connect;
     * then forwards both cached values to the live channel, if any. Never throws, including
     * when unbound.
     */
    override fun notifyOverlayVisible(visible: Boolean) {
        overlayVisible = visible
        deliverToOverlay()
    }

    /**
     * Forwards a host scroll progress change (0..1) toward the bound overlay.
     *
     * Same cache-then-deliver contract as [notifyOverlayVisible]; coerces via
     * [coerceOverlayProgress] (`NaN` maps to `0`) and never throws, including when unbound.
     */
    override fun notifyOverlayProgress(progress: Float) {
        overlayProgress = coerceOverlayProgress(progress)
        deliverToOverlay()
    }

    /**
     * Installs [connection] as the live control channel and catches it up.
     *
     * Production calls this from `onServiceConnected` with an [AidlFeedBridge] built around
     * the connected binder; JVM tests call it directly with a fake, so no framework object
     * crosses this seam. Re-delivers the cached [overlayVisible]/[overlayProgress] so
     * requests made while unbound are not lost, and re-emits `Available` for the bound
     * provider. A connection whose [epoch] no longer matches [bindEpoch], whose package
     * no longer matches the bound package, or that arrives after its bind is gone (stale
     * race) is closed immediately and dropped. A transport loss reported synchronously
     * from catch-up delivery while this call holds [mutex] is deferred via
     * [pendingLossEpochs] and torn down here instead of re-emitting `Available` for the
     * dead channel: the loss is consumed with a take-check before publishing, then
     * `Available` is published only for a loss-free epoch, then a second take-check
     * tears down a loss that arrived between the first check and the publish. A loss
     * that arrives after the second check (or against a non-connect holder) is drained
     * asynchronously by [schedulePendingLossDrain]. Never throws.
     */
    internal fun onOverlayConnected(
        connection: FeedOverlayConnection,
        epoch: Long = bindEpoch,
        servicePackage: String? = null,
    ) {
        try {
            if (!mutex.tryLock()) {
                try {
                    connection.close()
                } catch (_: Exception) {
                    // Dropping a channel never reports a host error.
                }
                return
            }
            try {
                if (epoch != bindEpoch) {
                    try {
                        connection.close()
                    } catch (_: Exception) {
                        // Stale teardown never throws.
                    }
                    return
                }
                if (boundProvider == null || boundPackage == null || boundConnection == null) {
                    try {
                        connection.close()
                    } catch (_: Exception) {
                        // Stale teardown never throws.
                    }
                    return
                }
                if (servicePackage != null && servicePackage != boundPackage) {
                    try {
                        connection.close()
                    } catch (_: Exception) {
                        // Stale teardown never throws.
                    }
                    return
                }
                try {
                    overlayBridge?.close()
                } catch (_: Exception) {
                    // Replacing a channel never throws.
                }
                overlayBridge = connection
                deliverToOverlay()
                if (takePendingLoss(epoch)) {
                    unbindLocked()
                    _state.tryEmit(
                        FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
                    )
                    return
                }
                val provider = boundProvider
                if (provider != null) {
                    _state.tryEmit(FeedAvailability.Available(provider))
                }
                // Loss-after-check: a contender that marked its epoch between the first
                // take and the publish above must not leave a dead bridge published as
                // Available. The holder still owns [mutex], so it tears down directly;
                // a loss arriving after this second take finds a free lock (direct
                // teardown) or a non-connect holder (background drain).
                if (takePendingLoss(epoch)) {
                    unbindLocked()
                    _state.tryEmit(
                        FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
                    )
                }
            } finally {
                mutex.unlock()
            }
        } catch (_: Exception) {
            // Binder-thread entry points must never observe an exception.
        }
    }

    /**
     * Connection-loss path shared by `onServiceDisconnected` and `onBindingDied`.
     *
     * Internal (not private) so JVM tests can drive binder death without constructing
     * `ComponentName` framework objects; both real callbacks delegate here. Clears the single
     * connection and emits `Unavailable` via non-suspending `tryEmit`, guarded so it never
     * throws on a binder thread.
     */
    internal fun onConnectionLost() {
        val epoch = bindEpoch
        try {
            if (!mutex.tryLock()) {
                // Contended: defer to the holder (connect's take-checks) or the
                // background drain. No direct emission here so a stale or
                // post-publish loss can never overwrite the owning epoch's verdict.
                if (markPendingLoss(epoch)) {
                    schedulePendingLossDrain(epoch)
                }
                return
            }
            try {
                unbindLocked()
                _state.tryEmit(
                    FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
                )
            } finally {
                mutex.unlock()
            }
        } catch (_: Exception) {
            // Binder threads must never observe an exception from this callback.
        }
    }

    /**
     * Epoch-scoped transport loss for bridges created by [OverlayConnection].
     *
     * A loss from a previous generation is dropped silently — it must not unbind or
     * overwrite the current generation's `Available`. A loss from the current generation
     * follows the same path as [onConnectionLost], including the contended
     * [pendingLossEpochs] deferral for synchronous loss during catch-up plus the
     * background [schedulePendingLossDrain] for losses against non-connect holders. The deferred
     * mark is epoch-scoped and re-validated after insertion, so a stale callback that
     * passed the first check but resumed after a replacement advances [bindEpoch]
     * self-cleans instead of marking or reporting against the replacement. Never throws.
     */
    private fun onBridgeLoss(epoch: Long) {
        try {
            if (epoch != bindEpoch) return
            if (!mutex.tryLock()) {
                if (markPendingLoss(epoch)) {
                    schedulePendingLossDrain(epoch)
                }
                return
            }
            try {
                if (epoch != bindEpoch) return
                unbindLocked()
                _state.tryEmit(
                    FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
                )
            } finally {
                mutex.unlock()
            }
        } catch (_: Exception) {
            // Binder threads must never observe an exception from this callback.
        }
    }

    private fun onPackageChanged() {
        scope?.launch(ioDispatcher) {
            val availability = try {
                resolver.resolve()
            } catch (_: SecurityException) {
                FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.PERMISSION_DENIED)
            } catch (_: Exception) {
                FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED)
            }
            if (!mutex.tryLock()) return@launch
            try {
                if (availability is FeedAvailability.Available) {
                    if (boundProvider == availability.provider && boundPackage != null) {
                        _state.emit(availability)
                    } else {
                        tryBindLocked(
                            provider = availability.provider,
                            fallbackReason = FeedAvailability.Unavailable.Reason.QUERY_FAILED,
                        )
                    }
                } else {
                    unbindLocked()
                    _state.emit(availability)
                }
            } finally {
                mutex.unlock()
            }
        }
    }

    /**
     * Binds [provider] on the calling thread; the caller must hold [mutex] via `tryLock`.
     *
     * The automatic paths ([start] resolution and [onPackageChanged]) and resolved [attach]
     * share this single bind sequence so there is exactly one connection: any previous bind
     * is released first, then each package id is tried in order. A bind rejection from the
     * connector surfaces as the supplied pre-bind verdict unless the connector throws. A thrown
     * bind is classified as `PERMISSION_DENIED` for [SecurityException], otherwise
     * `QUERY_FAILED`; a plain false result never invents a query failure.
     */
    private fun tryBindLocked(
        provider: FeedProviderId,
        fallbackReason: FeedAvailability.Unavailable.Reason,
    ) {
        unbindLocked()
        var exceptionReason: FeedAvailability.Unavailable.Reason? = null
        val freshConnection = OverlayConnection(bindEpoch)
        for (packageName in provider.packageNames) {
            val accepted = try {
                connector.bind(freshConnection, packageName)
            } catch (_: SecurityException) {
                exceptionReason = FeedAvailability.Unavailable.Reason.PERMISSION_DENIED
                false
            } catch (_: Exception) {
                if (exceptionReason == null) {
                    exceptionReason = FeedAvailability.Unavailable.Reason.QUERY_FAILED
                }
                false
            }
            if (accepted) {
                boundProvider = provider
                boundPackage = packageName
                boundConnection = freshConnection
                _state.tryEmit(FeedAvailability.Available(provider))
                return
            }
        }
        _state.tryEmit(
            FeedAvailability.Unavailable(exceptionReason ?: fallbackReason),
        )
    }

    /**
     * Forwards the cached host request to the live control channel, if any.
     *
     * This is the single forwarding point: [notifyOverlayVisible]/[notifyOverlayProgress]
     * cache first and land here, and [onOverlayConnected] lands here for catch-up. Silent
     * while unbound; never throws. The channel itself coalesces repeats, so forwarding both
     * cached values on every call is idempotent.
     */
    private fun deliverToOverlay() {
        val bridge = overlayBridge ?: return
        try {
            bridge.setVisible(overlayVisible)
            bridge.setProgress(overlayProgress)
        } catch (_: Exception) {
            // Transport loss is reported through onConnectionLost, never as an exception.
        }
    }

    /**
     * Tears down the single connection: closes and drops the live channel, then unbinds.
     *
     * The caller must hold [mutex] via `tryLock`. Closing the bridge first lets a dead
     * binder's in-flight loss delivery finish (bounded, re-entrant-safe) before the unbind;
     * a deliberate teardown closes a healthy channel silently, since [AidlFeedBridge] never
     * reports loss after close. The host request cache ([overlayVisible]/[overlayProgress])
     * is retained for the next connect. Unbind races (e.g. service already gone) are not
     * host errors.
     */
    private fun unbindLocked() {
        val bridge = overlayBridge
        overlayBridge = null
        if (bridge != null) {
            try {
                bridge.close()
            } catch (_: Exception) {
                // Channel teardown never reports a host error.
            }
        }
        val connectionToUnbind = boundConnection
        val hadBind = boundProvider != null || boundPackage != null || connectionToUnbind != null
        boundProvider = null
        boundPackage = null
        boundConnection = null
        if (hadBind || bridge != null) {
            bindEpoch += 1
        }
        // Teardown already handled any deferred loss for the torn-down generation; drop
        // stale marks so a delayed contended writer cannot resurrect them. Marks for the
        // new generation cannot exist yet (no live bridge), and a stale writer that adds
        // after this clear self-cleans in [markPendingLoss] via post-insert revalidation.
        pendingLossEpochs.clear()
        val target = connectionToUnbind ?: return
        try {
            connector.unbind(target)
        } catch (_: Exception) {
            // Unbind races (e.g. service already gone) are not host errors.
        }
    }

    /**
     * Epoch-scoped deferred-loss handoff for lock-free contended paths.
     *
     * Records the current-epoch loss even before the bridge is published (a dead
     * binder reports synchronously during `AidlFeedBridge` construction, while the
     * bind is active but [overlayBridge] is still null): the only gate is the epoch
     * match, so a contended pre-bridge loss still schedules [schedulePendingLossDrain]
     * instead of being dropped as "nothing to tear down". Spurious marks (e.g. a
     * loss observed while idle with no bind) are dropped without publishing by the
     * consumer, which re-validates the bind under lock. Stale validation is retained:
     * a callback that passed the first check but resumed after a replacement advanced
     * [bindEpoch] removes its own stale mark and reports false, so it never marks or
     * schedules against the replacement. Returns true only when the caller must
     * schedule [schedulePendingLossDrain]. Never throws.
     */
    private fun markPendingLoss(epoch: Long): Boolean {
        try {
            if (epoch != bindEpoch) return false
            pendingLossEpochs.add(epoch)
            if (epoch != bindEpoch) {
                pendingLossEpochs.remove(epoch)
                return false
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Consumes the deferred loss for [epoch]; true when the caller owns the teardown.
     * Removes only the caller's own epoch, so a stale mark never cancels the current
     * generation. Never throws.
     */
    private fun takePendingLoss(epoch: Long): Boolean {
        return try {
            pendingLossEpochs.remove(epoch)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Background consumer for deferred losses that contended against another holder
     * (connect catch-up, re-resolution, attach). Retries unbounded with a short delay
     * so the holder's critical section can finish: production holders are short, so
     * the drain eventually acquires the lock and guarantees the handoff — it never
     * abandons the epoch after a fixed window. Each attempt re-validates the epoch
     * under lock and drops stale marks without publishing; a mark for the current
     * epoch with no active bind and no live bridge (spurious idle mark, or a teardown
     * that already cleared) is consumed without publishing. Never throws.
     */
    private fun schedulePendingLossDrain(epoch: Long) {
        try {
            val drainScope = scope ?: return
            drainScope.launch(ioDispatcher) {
                try {
                    while (true) {
                        if (mutex.tryLock()) {
                            try {
                                if (epoch != bindEpoch) {
                                    pendingLossEpochs.remove(epoch)
                                    break
                                }
                                if (!pendingLossEpochs.remove(epoch)) break
                                if (boundProvider == null && boundPackage == null &&
                                    boundConnection == null && overlayBridge == null
                                ) {
                                    // Spurious current-epoch mark with nothing bound
                                    // (idle gateway): consumed, nothing to tear down.
                                    break
                                }
                                unbindLocked()
                                _state.tryEmit(
                                    FeedAvailability.Unavailable(
                                        FeedAvailability.Unavailable.Reason.QUERY_FAILED,
                                    ),
                                )
                                break
                            } finally {
                                mutex.unlock()
                            }
                        }
                        try {
                            delay(PENDING_DRAIN_RETRY_MS)
                        } catch (_: Exception) {
                            break
                        }
                    }
                } catch (_: Exception) {
                    // Drain never reports a host error.
                }
            }
        } catch (_: Exception) {
            // Scheduling never throws on binder threads.
        }
    }

    private inner class OverlayConnection(val epoch: Long) : ServiceConnection {        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            // Built outside the gateway lock: construction links the death recipient, and
            // an already-dead binder reports loss synchronously during construction — with
            // a free lock that loss path clears the stale bind first, so the install below
            // then sees no bind and drops the dead channel instead of keeping it.
            // The loss callback is epoch-scoped so a stale generation's death never tears
            // down the current generation.
            val bridge = try {
                AidlFeedBridge(service) { onBridgeLoss(epoch) }
            } catch (_: Exception) {
                onBridgeLoss(epoch)
                return
            }
            val servicePackage: String? = try {
                name.packageName
            } catch (_: Exception) {
                null
            }
            onOverlayConnected(bridge, epoch, servicePackage)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            onBridgeLoss(epoch)
        }

        override fun onBindingDied(name: ComponentName) {
            onBridgeLoss(epoch)
        }
    }

    companion object {
        /** Delay between unbounded drain retries; virtual-time friendly under `runTest`. */
        private const val PENDING_DRAIN_RETRY_MS = 1L
    }
}

/**
 * Production [FeedServiceConnector]: binds `com.android.launcher3.WINDOW_OVERLAY` in the
 * provider's package with `BIND_AUTO_CREATE`.
 */
private class AndroidFeedConnector(private val context: Context) : FeedServiceConnector {

    override fun bind(connection: ServiceConnection, packageName: String): Boolean {
        val intent = Intent(FEED_OVERLAY_ACTION).setPackage(packageName)
        return context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun unbind(connection: ServiceConnection) {
        try {
            context.unbindService(connection)
        } catch (_: IllegalArgumentException) {
            // Not bound: matches the gateway's never-throw detach contract.
        }
    }
}

/**
 * Production [FeedPackageMonitor]: re-resolves when provider packages change.
 *
 * Listens for installs, removals, updates and enable-state changes for any package; the gateway
 * re-resolves and decides whether the change matters, so the filter stays broad on purpose.
 */
private class AndroidPackageMonitor(private val context: Context) : FeedPackageMonitor {

    private var receiver: BroadcastReceiver? = null

    override fun start(onPackageChanged: () -> Unit) {
        stop()
        val registered = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                onPackageChanged()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(registered, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(registered, filter)
        }
        receiver = registered
    }

    override fun stop() {
        val registered = receiver ?: return
        receiver = null
        try {
            context.unregisterReceiver(registered)
        } catch (_: IllegalArgumentException) {
            // Already unregistered: stop stays idempotent.
        }
    }
}
