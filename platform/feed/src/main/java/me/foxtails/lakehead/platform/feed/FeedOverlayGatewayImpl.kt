package me.foxtails.lakehead.platform.feed

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.app.Activity
import android.net.Uri
import android.os.Process
import android.view.WindowManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Binds one overlay [ServiceConnection] for an exact resolved target.
 *
 * Target-based on purpose: the gateway passes the [FeedProviderTarget] retained from
 * `resolveDetailed` (exact package + class strings) and the production implementation
 * builds an explicit-`ComponentName` `Intent` at the platform boundary, so unit tests
 * can fake binding without constructing Android framework objects. Exactly one bind is
 * attempted per target — never a retry loop over other packages once a target is
 * selected.
 */
internal interface FeedServiceConnector {
    /** Returns true when the bind for [target] was accepted (not proof of a binder). */
    fun bind(connection: ServiceConnection, target: FeedProviderTarget): Boolean

    /** Releases [connection]; a no-op when it is not bound. Never throws. */
    fun unbind(connection: ServiceConnection)
}

/**
 * Verifies one resolved target still publishes the overlay service before binding.
 *
 * The production implementation queries the same [FEED_OVERLAY_ACTION] with the
 * target's package and requires an exact package + class match — a miss means the
 * component is gone (`NOT_RESOLVABLE`), while a denied/failed check means
 * resolvability is unknown (never reported as absence). Fakes script the verdict so
 * tests drive miss/denied/failed deterministically without touching `PackageManager`.
 */
internal interface FeedTargetPreflight {
    /** Verifies [target]; never throws — denials/failures become `UNKNOWN_*`. */
    fun verify(target: FeedProviderTarget): FeedTargetResolvability
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
 * - `notifyWindowAttached` caches a copy of the activity-derived `WindowManager`
 *   params (never the activity) and forwards them with the connection's single
 *   callback ([FeedOverlayWindowConnection]); a late connect re-delivers the cached
 *   registration before the control catch-up, so an attach made while unbound still
 *   lets the provider render. The cache is retained across binds and cleared only by
 *   [notifyWindowDetached].
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
    private val preflight: FeedTargetPreflight,
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
        preflight = AndroidFeedPreflight(applicationContext.applicationContext),
    )

    private val mutex = Mutex()

    private val _state = MutableStateFlow<FeedAvailability>(
        FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.NO_PROVIDER),
    )
    override val state: StateFlow<FeedAvailability> = _state

    private val _diagnostic = MutableStateFlow<FeedDiagnostic?>(null)
    override val diagnostic: StateFlow<FeedDiagnostic?> = _diagnostic

    private val _bindingEvidence = MutableStateFlow(FeedBindingEvidence())
    override val bindingEvidence: StateFlow<FeedBindingEvidence> = _bindingEvidence

    @Volatile
    private var scope: CoroutineScope? = null
    private var resolveJob: Job? = null
    private var boundProvider: FeedProviderId? = null
    private var boundPackage: String? = null

    /**
     * Exact target retained from `resolveDetailed` for the live (or most recent) bind
     * attempt, null while idle. Set together with [boundProvider]/[boundPackage] on
     * acceptance; cleared on teardown. The discovery verdict for the same resolution
     * is retained in [discoveryDiagnostic] (null on success) so D5 can distinguish
     * "nothing resolved" from "resolved but bind failed".
     */
    @Volatile
    private var boundTarget: FeedProviderTarget? = null

    /** Discovery verdict retained from the latest resolution; null on success. */
    @Volatile
    private var discoveryDiagnostic: FeedDiagnostic? = null

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
     * Latest window params requested by the host surface, copied from the host
     * `Activity`'s live window (never the activity itself, so no host is retained).
     *
     * Cached so a late bind can catch up: [deliverWindowToOverlay] re-delivers this
     * registration on every connect before the control catch-up, because the provider
     * renders no pixels until its window is attached. Retained across binds and
     * unbinds like [overlayVisible]/[overlayProgress] — only [notifyWindowDetached]
     * clears it, so a rebind re-attaches the same window. Null means no window is
     * currently registered by the host surface.
     */
    @Volatile
    private var windowParams: WindowManager.LayoutParams? = null

    /** Flags for the cached [windowParams] registration. */
    @Volatile
    private var windowFlags: Int = AidlFeedBridge.WINDOW_ATTACH_FLAGS

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
     * Stored lock-free by the contended branch of [onConnectionLost]/[onBridgeLoss] via
     * [markPendingLoss] when the loss epoch still matches [bindEpoch], even before the
     * bridge is published (a dead binder reports synchronously during construction while
     * [overlayBridge] is still null). A spurious current-epoch mark with no bind is
     * consumed without publish. Consumed under lock by [onOverlayConnected] after catch-up delivery
     * (take-check, publish, re-check) and by the background [schedulePendingLossDrain] for losses
     * that contended against non-connect holders (re-resolution, attach). Per-epoch keys
     * keep a stale generation's deferred loss from ever tearing down or overwriting its
     * replacement: [takePendingLoss] removes only the caller's own epoch.
     *
     * A single epoch-to-verdict map publishes the marker and its `BIND_FAILED` vs
     * `DISCONNECTED` verdict atomically: one `put` installs both and one `remove`
     * consumes both, so a concurrent consumer can never observe the marker with a
     * defaulted verdict.
     */
    private val pendingLosses: ConcurrentHashMap<Long, PendingFailure> =
        ConcurrentHashMap()

    /**
     * Epoch-scoped deferred connects observed while [mutex] was held (typically by
     * [tryBindLocked] during preflight/bind). Stored lock-free by the contended branch
     * of [onOverlayConnected] when the epoch still matches [bindEpoch]; consumed under
     * lock by [schedulePendingConnectDrain], which installs the callback after the
     * holder releases. Closed without installing when the epoch/target is stale.
     */
    private data class PendingConnect(
        val connection: FeedOverlayConnection,
        val servicePackage: String?,
        val serviceClassName: String?,
    )

    private val pendingConnects: ConcurrentHashMap<Long, PendingConnect> =
        ConcurrentHashMap()

    /**
     * Durable teardown intents for [stop]/[detach] when [mutex] is contended.
     *
     * Set lock-free before the `tryLock` attempt so a `MainActivity.onStop` on the main
     * thread that races a background bind never loses its cleanup; consumed under lock
     * by [schedulePendingTeardownDrain] after the holder releases. [pendingStop]
     * supersedes [pendingDetach].
     */
    @Volatile
    private var pendingStop: Boolean = false

    @Volatile
    private var pendingDetach: Boolean = false

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
            val resolution = resolveDetailedSafe()
            if (!mutex.tryLock()) return@launch
            try {
                if (this@FeedOverlayGatewayImpl.scope !== scope) return@launch
                applyResolutionLocked(resolution, requested = null)
            } finally {
                mutex.unlock()
            }
        }
    }

    override fun stop() {
        // Durable intent: a contended stop must serialize its cleanup after the holder
        // releases instead of returning without tearing down.
        pendingStop = true
        if (!mutex.tryLock()) {
            schedulePendingTeardownDrain()
            return
        }
        try {
            pendingStop = false
            pendingDetach = false
            if (scope == null) return
            scope = null
            resolveJob?.cancel()
            resolveJob = null
            packageMonitor.stop()
            unbindLocked()
            boundTarget = null
            discoveryDiagnostic = null
            _diagnostic.tryEmit(null)
            _bindingEvidence.tryEmit(FeedBindingEvidence())
        } finally {
            mutex.unlock()
        }
    }

    override fun attach(provider: FeedProviderId) {
        val currentScope = scope ?: return
        currentScope.launch(ioDispatcher) {
            val resolution = resolveDetailedSafe()
            if (!mutex.tryLock()) return@launch
            try {
                if (this@FeedOverlayGatewayImpl.scope !== currentScope) return@launch
                applyResolutionLocked(resolution, requested = provider)
            } finally {
                mutex.unlock()
            }
        }
    }

    override fun detach() {
        // Durable intent: serialize the detach cleanup after contention instead of
        // dropping it, so a racing bind cannot leave the monitor/binding active.
        if (!pendingStop) {
            pendingDetach = true
        }
        if (!mutex.tryLock()) {
            schedulePendingTeardownDrain()
            return
        }
        try {
            pendingDetach = false
            // Deliberate detach: teardown without reporting a failure. Clears the
            // binding evidence to idle and drops the binding diagnostic — never emits
            // NOT_RESOLVABLE/BIND_FAILED/DISCONNECTED for an explicit user/host detach.
            unbindLocked()
            boundTarget = null
            discoveryDiagnostic = null
            _diagnostic.tryEmit(null)
            _bindingEvidence.tryEmit(FeedBindingEvidence())
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Applies one [FeedProviderResolution] under [mutex].
     *
     * A resolved target whose provider matches [requested] (or any target when
     * [requested] is null) binds that exact target via [tryBindLocked] — no retry over
     * other packages once a target is selected. Anything else (unresolved, or a target
     * for a different provider than [requested]) tears down and projects the discovery
     * verdict, preserving D2's `NO_PROVIDER`/`NOT_DISCOVERABLE`/`REJECTED` mapping.
     * The caller must hold [mutex] via `tryLock`.
     */
    private fun applyResolutionLocked(
        resolution: FeedProviderResolution,
        requested: FeedProviderId?,
    ) {
        val target = resolution.target
        if (target != null && (requested == null || target.provider == requested)) {
            if (boundTarget == target && boundConnection != null) {
                _state.tryEmit(resolution.availability)
            } else {
                tryBindLocked(target)
            }
        } else {
            unbindLocked()
            boundTarget = null
            discoveryDiagnostic = resolution.diagnostic
            _diagnostic.tryEmit(resolution.diagnostic)
            _bindingEvidence.tryEmit(FeedBindingEvidence())
            _state.tryEmit(resolution.availability)
        }
    }

    /**
     * Framework-free resolution that never throws.
     *
     * `resolveDetailed` itself never throws, but the gateway stays honest if a future
     * resolver does: a platform denial becomes `REJECTED` (redacted) and any other
     * failure becomes `NOT_DISCOVERABLE` (redacted), mirroring the D2 projection.
     */
    private fun resolveDetailedSafe(): FeedProviderResolution {
        return try {
            resolver.resolveDetailed()
        } catch (_: SecurityException) {
            FeedProviderResolution(
                target = null,
                diagnostic = FeedDiagnostic(
                    status = FeedDiagnosticStatus.REJECTED,
                    cause = FeedDiagnosticCause.QUERY_DENIED,
                    evidence = FeedDiscoveryEvidence(),
                ),
            )
        } catch (_: Exception) {
            FeedProviderResolution(
                target = null,
                diagnostic = FeedDiagnostic(
                    status = FeedDiagnosticStatus.NOT_DISCOVERABLE,
                    cause = FeedDiagnosticCause.PROBE_FAILED,
                    evidence = FeedDiscoveryEvidence(),
                ),
            )
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
     * Registers the host window with the bound overlay service.
     *
     * Derives the provider's params from [activity]'s live window (copied — the
     * activity is never retained) and delegates to [notifyWindowAttachedParams], so
     * the cache-then-deliver contract holds even when unbound. A params build
     * failure keeps the previous registration and stays silent. Never throws,
     * including when unbound.
     */
    override fun notifyWindowAttached(activity: Activity) {
        val params = try {
            windowParamsFor(activity)
        } catch (_: Exception) {
            null
        } catch (_: Throwable) {
            null
        } ?: return
        notifyWindowAttachedParams(params, AidlFeedBridge.WINDOW_ATTACH_FLAGS)
    }

    /**
     * Params-copy seam for [notifyWindowAttached].
     *
     * Internal (not private) so JVM tests can drive the window handshake with a fake
     * window channel without constructing an `Activity`; the production path above
     * only adds the activity-derived copy at the platform boundary. Caches a
     * defensive copy of [params] with [flags] first, then forwards to the live
     * channel if any. Never throws, including when unbound.
     */
    internal fun notifyWindowAttachedParams(
        params: WindowManager.LayoutParams,
        flags: Int = AidlFeedBridge.WINDOW_ATTACH_FLAGS,
    ) {
        try {
            windowParams = try {
                // No public copy constructor in the compile stubs: no-arg plus copyFrom.
                WindowManager.LayoutParams().apply { copyFrom(params) }
            } catch (_: Exception) {
                params
            } catch (_: Throwable) {
                params
            }
            windowFlags = flags
        } catch (_: Throwable) {
            return
        }
        deliverWindowToOverlay()
    }

    /**
     * Unregisters the host window from the bound overlay service.
     *
     * Clears the cached registration first so a later re-attach starts clean, then
     * forwards `windowDetached` to the live channel if it holds the window. Never
     * throws, including when unbound.
     */
    override fun notifyWindowDetached(isChangingConfigurations: Boolean) {
        // Nothing registered (never attached, or already detached): silent no-op
        // without waking the channel. A live registration can only exist while
        // the cache is set — the cache is cleared here on every detach and
        // retained across unbinds otherwise — so skipping is exact, not heuristic.
        if (windowParams == null) return
        windowParams = null
        windowFlags = AidlFeedBridge.WINDOW_ATTACH_FLAGS
        val bridge = overlayBridge as? FeedOverlayWindowConnection ?: return
        try {
            bridge.windowDetached(isChangingConfigurations)
        } catch (_: Exception) {
            // Transport loss is reported through onConnectionLost, never as an exception.
        } catch (_: Throwable) {
            // Window teardown never throws.
        }
    }

    /**
     * Installs [connection] as the live control channel and catches it up.
     *
     * Production calls this from `onServiceConnected` with an [AidlFeedBridge] built around
     * the connected binder after [OverlayConnection] verified the exact package + class;
     * JVM tests call it directly with a fake, so no framework object crosses this seam.
     * Acceptance (`bindService` true) and connection (matching binder here) are correlated
     * but distinct: `connected` evidence publishes only when both hold for the same
     * target + epoch. Re-delivers the cached [overlayVisible]/[overlayProgress] so
     * requests made while unbound are not lost, and re-emits `Available` for the bound
     * provider (coarse only — never proof of content). A connection whose [epoch] no
     * longer matches [bindEpoch], whose package/class no longer matches the bound target,
     * or that arrives after its bind is gone (stale race) is closed immediately and
     * dropped without overwriting the current diagnostic. A mismatched package/class on
     * a live epoch is rejected as `BIND_FAILED`/`UNEXPECTED_COMPONENT` and never
     * installs the binder. A transport loss reported synchronously from catch-up delivery
     * while this call holds [mutex] is deferred via [pendingLosses] and torn down here
     * instead of re-emitting `Available` for the dead channel: the loss is consumed with
     * a take-check before publishing, then `Available` is published only for a loss-free
     * epoch, then a second take-check tears down a loss that arrived between the first
     * check and the publish. A loss that arrives after the second check (or against a
     * non-connect holder) is drained asynchronously by [schedulePendingLossDrain]. The
     * failure teardown retains its diagnostic; never throws.
     */
    internal fun onOverlayConnected(
        connection: FeedOverlayConnection,
        epoch: Long = bindEpoch,
        servicePackage: String? = null,
        serviceClassName: String? = null,
    ) {
        try {
            if (!mutex.tryLock()) {
                // Contended (typically tryBindLocked holding the lock across
                // preflight/bind): defer as an epoch-scoped event and install after
                // the holder releases instead of discarding the one-shot callback.
                // Closed only when the epoch/target proves stale at install time.
                if (epoch != bindEpoch) {
                    try {
                        connection.close()
                    } catch (_: Exception) {
                        // Dropping a stale channel never reports a host error.
                    }
                    return
                }
                if (markPendingConnect(epoch, connection, servicePackage, serviceClassName)) {
                    schedulePendingConnectDrain(epoch)
                } else {
                    try {
                        connection.close()
                    } catch (_: Exception) {
                        // Dropping a stale channel never reports a host error.
                    }
                }
                return
            }
            try {
                installConnectedLocked(connection, epoch, servicePackage, serviceClassName)
            } finally {
                mutex.unlock()
            }
        } catch (_: Exception) {
            // Binder-thread entry points must never observe an exception.
        }
    }

    /**
     * Installs a matching connection while holding [mutex].
     *
     * Shared by the uncontended [onOverlayConnected] path and the deferred
     * [schedulePendingConnectDrain]: validates epoch + target, rejects mismatches as
     * `BIND_FAILED`, installs the bridge with window-then-control catch-up, and honors
     * epoch-scoped deferred losses (preserving their `BIND_FAILED` vs `DISCONNECTED`
     * verdicts) across the publish. Never throws; caller must hold [mutex].
     */
    private fun installConnectedLocked(
        connection: FeedOverlayConnection,
        epoch: Long,
        servicePackage: String?,
        serviceClassName: String?,
    ) {
                if (epoch != bindEpoch) {
                    try {
                        connection.close()
                    } catch (_: Exception) {
                        // Stale teardown never throws.
                    }
                    return
                }
                val target = boundTarget
                if (target == null || boundProvider == null || boundPackage == null ||
                    boundConnection == null
                ) {
                    try {
                        connection.close()
                    } catch (_: Exception) {
                        // Stale teardown never throws.
                    }
                    return
                }
                if (servicePackage != null && servicePackage != target.component.packageName) {
                    rejectUnexpectedLocked(connection, target, epoch)
                    return
                }
                if (serviceClassName != null && serviceClassName != target.component.className) {
                    rejectUnexpectedLocked(connection, target, epoch)
                    return
                }
                try {
                    overlayBridge?.close()
                } catch (_: Exception) {
                    // Replacing a channel never throws.
                }
                overlayBridge = connection
                // Window registration precedes the control catch-up: the provider
                // renders no pixels until its window is attached, so a cached
                // attach must land before openOverlay/onScroll replay.
                deliverWindowToOverlay()
                deliverToOverlay()
                val deferred = takePendingLoss(epoch)
                if (deferred != null) {
                    val lost = boundTarget
                    unbindLocked()
                    boundTarget = null
                    val diagnostic = FeedDiagnostic(
                        status = deferred.status,
                        cause = deferred.cause,
                        provider = lost?.provider,
                        packageName = lost?.component?.packageName,
                        component = lost?.component,
                    )
                    _diagnostic.tryEmit(diagnostic)
                    _bindingEvidence.tryEmit(
                        FeedBindingEvidence(
                            target = lost,
                            attempted = true,
                            resolvability = FeedTargetResolvability.RESOLVABLE,
                            accepted = true,
                            connected = false,
                            active = false,
                            epoch = epoch,
                        ),
                    )
                    _state.tryEmit(
                        FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
                    )
                    return
                }
                val provider = boundProvider
                if (provider != null) {
                    _bindingEvidence.tryEmit(
                        FeedBindingEvidence(
                            target = target,
                            attempted = true,
                            resolvability = FeedTargetResolvability.RESOLVABLE,
                            accepted = true,
                            connected = true,
                            active = true,
                            epoch = epoch,
                        ),
                    )
                    _diagnostic.tryEmit(null)
                    _state.tryEmit(FeedAvailability.Available(provider))
                }
                // Loss-after-check: a contender that marked its epoch between the first
                // take and the publish above must not leave a dead bridge published as
                // Available. The holder still owns [mutex], so it tears down directly;
                // a loss arriving after this second take finds a free lock (direct
                // teardown) or a non-connect holder (background drain).
                val late = takePendingLoss(epoch)
                if (late != null) {
                    val lost = boundTarget
                    unbindLocked()
                    boundTarget = null
                    val diagnostic = FeedDiagnostic(
                        status = late.status,
                        cause = late.cause,
                        provider = lost?.provider,
                        packageName = lost?.component?.packageName,
                        component = lost?.component,
                    )
                    _diagnostic.tryEmit(diagnostic)
                    _bindingEvidence.tryEmit(
                        FeedBindingEvidence(
                            target = lost,
                            attempted = true,
                            resolvability = FeedTargetResolvability.RESOLVABLE,
                            accepted = true,
                            connected = false,
                            active = false,
                            epoch = epoch,
                        ),
                    )
                    _state.tryEmit(
                        FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
                    )
                }
    }

    /**
     * Rejects a live-epoch connection whose package/class mismatches the bound target.
     *
     * Closes the incoming channel without installing it, tears down the accepted bind
     * and reports `BIND_FAILED`/`UNEXPECTED_COMPONENT` with the attempted target's
     * identity (never the unexpected component's, which is untrusted). The caller must
     * hold [mutex] via `tryLock` with a live epoch already validated. Never throws.
     */
    private fun rejectUnexpectedLocked(
        connection: FeedOverlayConnection,
        target: FeedProviderTarget,
        epoch: Long,
    ) {
        try {
            connection.close()
        } catch (_: Exception) {
            // Rejection teardown never throws.
        }
        unbindLocked()
        boundTarget = null
        val diagnostic = FeedDiagnostic(
            status = FeedDiagnosticStatus.BIND_FAILED,
            cause = FeedDiagnosticCause.UNEXPECTED_COMPONENT,
            provider = target.provider,
            packageName = target.component.packageName,
            component = target.component,
        )
        _diagnostic.tryEmit(diagnostic)
        _bindingEvidence.tryEmit(
            FeedBindingEvidence(
                target = target,
                attempted = true,
                resolvability = FeedTargetResolvability.RESOLVABLE,
                accepted = true,
                connected = false,
                active = false,
                epoch = epoch,
            ),
        )
        _state.tryEmit(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
        )
    }

    /**
     * Null-binder path for `onNullBinding`.
     *
     * Internal so JVM tests can drive it without constructing `ComponentName`; the real
     * callback delegates here. A null binder for the current epoch tears down the
     * accepted bind and reports `BIND_FAILED`/`NULL_BINDING` with the attempted target's
     * identity. A stale epoch is ignored without overwriting the current diagnostic.
     * Never throws.
     */
    internal fun onNullBindingReceived(epoch: Long = bindEpoch) {
        try {
            if (epoch != bindEpoch) return
            if (!mutex.tryLock()) {
                // Contended null-binding against a connect holder is vanishingly rare
                // (the framework delivers it on the main thread); defer as a bind-stage
                // verdict so the holder drains it rather than dropping it silently.
                // Status preserved as BIND_FAILED, not DISCONNECTED.
                if (markPendingLoss(epoch, FeedDiagnosticCause.NULL_BINDING, FeedDiagnosticStatus.BIND_FAILED)) {
                    schedulePendingLossDrain(epoch)
                }
                return
            }
            try {
                if (epoch != bindEpoch) return
                val target = boundTarget ?: return
                unbindLocked()
                boundTarget = null
                val diagnostic = FeedDiagnostic(
                    status = FeedDiagnosticStatus.BIND_FAILED,
                    cause = FeedDiagnosticCause.NULL_BINDING,
                    provider = target.provider,
                    packageName = target.component.packageName,
                    component = target.component,
                )
                _diagnostic.tryEmit(diagnostic)
                _bindingEvidence.tryEmit(
                    FeedBindingEvidence(
                        target = target,
                        attempted = true,
                        resolvability = FeedTargetResolvability.RESOLVABLE,
                        accepted = true,
                        connected = false,
                        active = false,
                        epoch = epoch,
                    ),
                )
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
     * Unexpected-component path for [OverlayConnection] pre-bridge verification.
     *
     * Called when `onServiceConnected` names a package/class other than the attempted
     * target, before any [AidlFeedBridge] is built — so the unexpected binder is never
     * installed. Same verdict as [rejectUnexpectedLocked] but without an incoming
     * channel to close (there is none yet). Stale epochs are ignored. Never throws.
     */
    internal fun onUnexpectedComponent(epoch: Long) {
        try {
            if (epoch != bindEpoch) return
            if (!mutex.tryLock()) {
                if (markPendingLoss(epoch, FeedDiagnosticCause.UNEXPECTED_COMPONENT, FeedDiagnosticStatus.BIND_FAILED)) {
                    schedulePendingLossDrain(epoch)
                }
                return
            }
            try {
                if (epoch != bindEpoch) return
                val target = boundTarget ?: return
                unbindLocked()
                boundTarget = null
                val diagnostic = FeedDiagnostic(
                    status = FeedDiagnosticStatus.BIND_FAILED,
                    cause = FeedDiagnosticCause.UNEXPECTED_COMPONENT,
                    provider = target.provider,
                    packageName = target.component.packageName,
                    component = target.component,
                )
                _diagnostic.tryEmit(diagnostic)
                _bindingEvidence.tryEmit(
                    FeedBindingEvidence(
                        target = target,
                        attempted = true,
                        resolvability = FeedTargetResolvability.RESOLVABLE,
                        accepted = true,
                        connected = false,
                        active = false,
                        epoch = epoch,
                    ),
                )
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
     * Connection-loss path shared by `onServiceDisconnected` and `onBindingDied`
     * fakes without an epoch.
     *
     * Internal (not private) so JVM tests can drive binder death without constructing
     * `ComponentName` framework objects. Reports `DISCONNECTED`/`SERVICE_DISCONNECTED`
     * for the current generation and retains the failure diagnostic across the
     * teardown; a stale (post-replacement) call is ignored without overwriting the
     * current verdict. Never throws on a binder thread.
     */
    internal fun onConnectionLost() {
        handleTransportLoss(epoch = bindEpoch, cause = FeedDiagnosticCause.SERVICE_DISCONNECTED)
    }

    /**
     * `onServiceDisconnected` for the epoch-scoped [OverlayConnection] path.
     *
     * A matching connection that later disconnects reports
     * `DISCONNECTED`/`SERVICE_DISCONNECTED`. Stale epochs are dropped silently.
     */
    internal fun onServiceDisconnectedForEpoch(epoch: Long) {
        handleTransportLoss(epoch = epoch, cause = FeedDiagnosticCause.SERVICE_DISCONNECTED)
    }

    /**
     * `onBindingDied` and binder-death for the epoch-scoped [OverlayConnection] path.
     *
     * A matching connection whose binder dies reports `DISCONNECTED`/`BINDER_DIED`.
     * Stale epochs are dropped silently.
     */
    internal fun onBindingDiedForEpoch(epoch: Long) {
        handleTransportLoss(epoch = epoch, cause = FeedDiagnosticCause.BINDER_DIED)
    }

    /**
     * Epoch-scoped transport loss shared by disconnect, binding-death and binder death.
     *
     * A loss from a previous generation is dropped silently — it must not unbind or
     * overwrite the current generation's verdict. A loss from the current generation
     * tears down and reports `DISCONNECTED` with [cause], retaining the failure
     * diagnostic across the teardown. Includes the contended [pendingLosses]
     * deferral for synchronous loss during catch-up plus the background
     * [schedulePendingLossDrain] for losses against non-connect holders. The deferred
     * mark is epoch-scoped and re-validated after insertion, so a stale callback that
     * passed the first check but resumed after a replacement advances [bindEpoch]
     * self-cleans instead of marking or reporting against the replacement. The
     * deferred cause travels with the mark so the drain reports the same verdict the
     * direct path would have. Never throws.
     */
    private fun handleTransportLoss(epoch: Long, cause: FeedDiagnosticCause) {
        try {
            if (epoch != bindEpoch) return
            if (!mutex.tryLock()) {
                // Contended: defer to the holder (connect's take-checks) or the
                // background drain. No direct emission here so a stale or
                // post-publish loss can never overwrite the owning epoch's verdict.
                if (markPendingLoss(epoch, cause)) {
                    schedulePendingLossDrain(epoch)
                }
                return
            }
            try {
                if (epoch != bindEpoch) return
                val lost = boundTarget
                // Idle gateway (no bind, no bridge): a spurious current-epoch mark is
                // consumed without publishing — nothing to tear down.
                if (lost == null && overlayBridge == null && boundConnection == null) return
                unbindLocked()
                boundTarget = null
                val diagnostic = FeedDiagnostic(
                    status = FeedDiagnosticStatus.DISCONNECTED,
                    cause = cause,
                    provider = lost?.provider,
                    packageName = lost?.component?.packageName,
                    component = lost?.component,
                )
                _diagnostic.tryEmit(diagnostic)
                _bindingEvidence.tryEmit(
                    FeedBindingEvidence(
                        target = lost,
                        attempted = lost != null,
                        resolvability = if (lost != null) {
                            FeedTargetResolvability.RESOLVABLE
                        } else {
                            null
                        },
                        accepted = lost != null,
                        connected = false,
                        active = false,
                        epoch = epoch,
                    ),
                )
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
     * D4 protocol-stage failure for the epoch-scoped [OverlayConnection] path.
     *
     * Called by the [AidlFeedBridge]'s first-failure listener with the earliest broken
     * stage (`NEGOTIATION_FAILED`, `SURFACE_SESSION_FAILED` or `RENDERING_FAILED` with
     * its D4 cause): tears down the bind and reports that verdict with the attempted
     * target's identity instead of a generic disconnect. Production callers send only
     * the three D4 statuses; any status still tears down honestly, never throws, and
     * never invents a component.
     *
     * First failure wins via epoch scoping: the teardown bumps [bindEpoch], so the
     * transport loss the bridge reports alongside (or a later loss for the same dead
     * bind) arrives stale and is dropped without overwriting the stage verdict. A
     * contended call defers through the epoch-scoped [pendingLosses] verdict handoff
     * (which already carries status + cause) and the background
     * [schedulePendingLossDrain] instead of overwriting. A call with no bind and no
     * live bridge is consumed without publishing. Never throws.
     */
    internal fun onProtocolFailureForEpoch(
        epoch: Long,
        status: FeedDiagnosticStatus,
        cause: FeedDiagnosticCause,
    ) {
        try {
            if (epoch != bindEpoch) return
            if (!mutex.tryLock()) {
                if (markPendingLoss(epoch, cause, status)) {
                    schedulePendingLossDrain(epoch)
                }
                return
            }
            try {
                if (epoch != bindEpoch) return
                val lost = boundTarget
                // Idle gateway (no bind, no bridge): a spurious current-epoch mark is
                // consumed without publishing — nothing to tear down.
                if (lost == null && overlayBridge == null && boundConnection == null) return
                unbindLocked()
                boundTarget = null
                val diagnostic = FeedDiagnostic(
                    status = status,
                    cause = cause,
                    provider = lost?.provider,
                    packageName = lost?.component?.packageName,
                    component = lost?.component,
                )
                _diagnostic.tryEmit(diagnostic)
                _bindingEvidence.tryEmit(
                    FeedBindingEvidence(
                        target = lost,
                        attempted = lost != null,
                        resolvability = if (lost != null) {
                            FeedTargetResolvability.RESOLVABLE
                        } else {
                            null
                        },
                        accepted = lost != null,
                        connected = false,
                        active = false,
                        epoch = epoch,
                    ),
                )
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
     * Kept for the deferred-loss tests that drive binder death by epoch: delegates to
     * [handleTransportLoss] with `BINDER_DIED`, so a stale generation's death never
     * tears down the current generation. Never throws.
     */
    private fun onBridgeLoss(epoch: Long) {
        handleTransportLoss(epoch = epoch, cause = FeedDiagnosticCause.BINDER_DIED)
    }

    private fun onPackageChanged() {
        scope?.launch(ioDispatcher) {
            val resolution = resolveDetailedSafe()
            if (!mutex.tryLock()) return@launch
            try {
                applyResolutionLocked(resolution, requested = null)
            } finally {
                mutex.unlock()
            }
        }
    }

    /**
     * Binds the exact [target] on the calling thread; the caller must hold [mutex].
     *
     * The automatic paths ([start] resolution and [onPackageChanged]) and resolved [attach]
     * share this single bind sequence so there is exactly one connection: any previous
     * bind is released first, then exactly one preflight + one bind is attempted for the
     * retained target — never a retry loop over other packages once a target is selected.
     *
     * Steps, recorded separately in [FeedBindingEvidence] (no callback timeout, no new
     * status for the pending window):
     * - preflight with the same [FEED_OVERLAY_ACTION] and the explicit component,
     *   requiring an exact package + class match: a miss reports
     *   `NOT_RESOLVABLE`/`TARGET_NOT_RESOLVABLE`; a denied/failed check reports
     *   `NOT_RESOLVABLE`/`RESOLUTION_DENIED`/`RESOLUTION_FAILED` (resolvability unknown,
     *   detail redacted) and never attempts the bind.
     * - bind with an explicit-component intent and `BIND_AUTO_CREATE`, no fallback: a
     *   false result reports `BIND_FAILED`/`BIND_DECLINED`; a `SecurityException`
     *   reports `BIND_FAILED`/`BIND_DENIED` (never discovery `REJECTED`); any other
     *   throw reports `BIND_FAILED`/`BIND_ERROR` (detail redacted).
     * - a true result is acceptance only, not proof of a binder: the gateway publishes
     *   coarse `Available` (which never implies binder/content), records
     *   `accepted=true, connected=false` and waits for the matching binder callback —
     *   correlated by target + epoch in [OverlayConnection] — before publishing
     *   `connected`.
     */
    private fun tryBindLocked(target: FeedProviderTarget) {
        unbindLocked()
        boundTarget = null
        val attemptEpoch = bindEpoch
        _bindingEvidence.tryEmit(
            FeedBindingEvidence(
                target = target,
                attempted = true,
                resolvability = null,
                accepted = false,
                connected = false,
                active = false,
                epoch = attemptEpoch,
            ),
        )
        val resolvability = try {
            preflight.verify(target)
        } catch (_: SecurityException) {
            FeedTargetResolvability.UNKNOWN_DENIED
        } catch (_: Exception) {
            FeedTargetResolvability.UNKNOWN_FAILED
        }
        if (resolvability != FeedTargetResolvability.RESOLVABLE) {
            val cause = when (resolvability) {
                FeedTargetResolvability.NOT_RESOLVABLE -> FeedDiagnosticCause.TARGET_NOT_RESOLVABLE
                FeedTargetResolvability.UNKNOWN_DENIED -> FeedDiagnosticCause.RESOLUTION_DENIED
                else -> FeedDiagnosticCause.RESOLUTION_FAILED
            }
            val diagnostic = FeedDiagnostic(
                status = FeedDiagnosticStatus.NOT_RESOLVABLE,
                cause = cause,
                provider = target.provider,
                packageName = target.component.packageName,
                component = target.component,
            )
            discoveryDiagnostic = null
            _diagnostic.tryEmit(diagnostic)
            _bindingEvidence.tryEmit(
                FeedBindingEvidence(
                    target = target,
                    attempted = true,
                    resolvability = resolvability,
                    accepted = false,
                    connected = false,
                    active = false,
                    epoch = attemptEpoch,
                ),
            )
            _state.tryEmit(
                FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            )
            return
        }
        val freshConnection = OverlayConnection(target, attemptEpoch)
        val accepted: Boolean
        try {
            accepted = connector.bind(freshConnection, target)
        } catch (_: SecurityException) {
            reportBindThrowLocked(target, attemptEpoch, FeedDiagnosticCause.BIND_DENIED)
            return
        } catch (_: Exception) {
            reportBindThrowLocked(target, attemptEpoch, FeedDiagnosticCause.BIND_ERROR)
            return
        }
        if (!accepted) {
            val diagnostic = FeedDiagnostic(
                status = FeedDiagnosticStatus.BIND_FAILED,
                cause = FeedDiagnosticCause.BIND_DECLINED,
                provider = target.provider,
                packageName = target.component.packageName,
                component = target.component,
            )
            discoveryDiagnostic = null
            _diagnostic.tryEmit(diagnostic)
            _bindingEvidence.tryEmit(
                FeedBindingEvidence(
                    target = target,
                    attempted = true,
                    resolvability = FeedTargetResolvability.RESOLVABLE,
                    accepted = false,
                    connected = false,
                    active = false,
                    epoch = attemptEpoch,
                ),
            )
            _state.tryEmit(
                FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            )
            return
        }
        boundTarget = target
        boundProvider = target.provider
        boundPackage = target.component.packageName
        boundConnection = freshConnection
        discoveryDiagnostic = null
        _diagnostic.tryEmit(null)
        _bindingEvidence.tryEmit(
            FeedBindingEvidence(
                target = target,
                attempted = true,
                resolvability = FeedTargetResolvability.RESOLVABLE,
                accepted = true,
                connected = false,
                active = false,
                epoch = attemptEpoch,
            ),
        )
        _state.tryEmit(FeedAvailability.Available(target.provider))
    }

    /**
     * Reports a throwing bind for [target] as `BIND_FAILED` with [cause].
     *
     * The bind never took effect, so there is nothing to unbind (no double unbind):
     * only the diagnostic, evidence and coarse state are published. The original
     * failure detail is dropped — only the allow-listed [cause] travels. The caller
     * must hold [mutex]. Never throws.
     */
    private fun reportBindThrowLocked(
        target: FeedProviderTarget,
        attemptEpoch: Long,
        cause: FeedDiagnosticCause,
    ) {
        val diagnostic = FeedDiagnostic(
            status = FeedDiagnosticStatus.BIND_FAILED,
            cause = cause,
            provider = target.provider,
            packageName = target.component.packageName,
            component = target.component,
        )
        discoveryDiagnostic = null
        _diagnostic.tryEmit(diagnostic)
        _bindingEvidence.tryEmit(
            FeedBindingEvidence(
                target = target,
                attempted = true,
                resolvability = FeedTargetResolvability.RESOLVABLE,
                accepted = false,
                connected = false,
                active = false,
                epoch = attemptEpoch,
            ),
        )
        _state.tryEmit(
            FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
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
     * Forwards the cached window registration to the live window channel, if any.
     *
     * This is the single window-forwarding point: [notifyWindowAttachedParams] lands
     * here, and [onOverlayConnected] lands here before the control catch-up. Silent
     * while unbound or when the channel does not implement the window handshake
     * (older fakes); never throws. The channel re-registers the latest params, so
     * forwarding the cache on every call is idempotent.
     */
    private fun deliverWindowToOverlay() {
        val params = windowParams ?: return
        val bridge = overlayBridge as? FeedOverlayWindowConnection ?: return
        try {
            bridge.windowAttached(params, windowFlags)
        } catch (_: Exception) {
            // Transport loss is reported through onConnectionLost, never as an exception.
        } catch (_: Throwable) {
            // Window delivery never throws.
        }
    }

    /**
     * Derives the provider's overlay params from the host [activity]'s live window.
     *
     * Copies the window attributes when available (type, format, flags) so the
     * provider registers against the same window configuration the host surface is
     * attached to, and stamps the decor-view token when available so the provider
     * can place its content. Falls back to a full-bleed default when the window is
     * not ready yet; returns null only when even the fallback cannot be built.
     * Never retains [activity]. Callers treat null as "keep the previous
     * registration"; this function itself never throws.
     */
    private fun windowParamsFor(activity: Activity): WindowManager.LayoutParams? {
        return try {
            val attributes = try {
                activity.window?.attributes
            } catch (_: Exception) {
                null
            } catch (_: Throwable) {
                null
            }
            val token = try {
                activity.window?.decorView?.windowToken
            } catch (_: Exception) {
                null
            } catch (_: Throwable) {
                null
            }
            WindowManager.LayoutParams().apply {
                if (attributes != null) {
                    try {
                        copyFrom(attributes)
                    } catch (_: Exception) {
                        width = WindowManager.LayoutParams.MATCH_PARENT
                        height = WindowManager.LayoutParams.MATCH_PARENT
                    } catch (_: Throwable) {
                        width = WindowManager.LayoutParams.MATCH_PARENT
                        height = WindowManager.LayoutParams.MATCH_PARENT
                    }
                } else {
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.MATCH_PARENT
                }
                if (token != null) {
                    this.token = token
                }
            }
        } catch (_: Exception) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Tears down the single connection: closes and drops the live channel, then unbinds.
     *
     * The caller must hold [mutex] via `tryLock`. Closing the bridge first lets a dead
     * binder's in-flight loss delivery finish (bounded, re-entrant-safe) before the unbind;
     * a deliberate teardown closes a healthy channel silently, since [AidlFeedBridge] never
     * reports loss after close. The host request caches ([overlayVisible]/[overlayProgress]
     * and the window registration) are retained for the next connect — only an explicit
     * [notifyWindowDetached] clears the window cache. The failure diagnostic and binding
     * evidence are retained by the caller across a failure teardown (this function never
     * clears them); deliberate detach/stop clear them explicitly after calling here.
     * Unbind races (e.g. service already gone) are not host errors. The deferred-loss
     * marks and their causes are dropped with the torn-down generation so a delayed
     * contended writer cannot resurrect them.
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
        pendingLosses.clear()
        // Deferred connects for the torn-down generation are stale: close without
        // installing so a delayed callback cannot resurrect a dead bind.
        val staleConnects = pendingConnects.values.toList()
        pendingConnects.clear()
        for (stale in staleConnects) {
            try {
                stale.connection.close()
            } catch (_: Exception) {
                // Stale teardown never throws.
            }
        }
        val target = connectionToUnbind ?: return
        try {
            connector.unbind(target)
        } catch (_: Exception) {
            // Unbind races (e.g. service already gone) are not host errors.
        }
    }

    /** Deferred failure verdict traveling with a [pendingLosses] mark. */
    internal data class PendingFailure(
        val status: FeedDiagnosticStatus,
        val cause: FeedDiagnosticCause,
    )

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
     *
     * The marker and its verdict publish atomically in one [pendingLosses] `put`, so
     * a concurrent consumer can never observe the epoch with a defaulted
     * `DISCONNECTED`/`BINDER_DIED` verdict for a contended null-binding/mismatch.
     */
    private fun markPendingLoss(
        epoch: Long,
        cause: FeedDiagnosticCause = FeedDiagnosticCause.BINDER_DIED,
        status: FeedDiagnosticStatus = FeedDiagnosticStatus.DISCONNECTED,
    ): Boolean {
        try {
            if (epoch != bindEpoch) return false
            pendingLosses[epoch] = PendingFailure(status = status, cause = cause)
            if (epoch != bindEpoch) {
                pendingLosses.remove(epoch)
                return false
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Consumes the deferred loss for [epoch]; returns its verdict, or null when the caller
     * owns no teardown. Removes only the caller's own epoch (and its verdict), so a stale
     * mark never cancels the current generation. The single-map `remove` returns the
     * verdict stored by [markPendingLoss] — never a default. Never throws.
     */
    private fun takePendingLoss(epoch: Long): PendingFailure? {
        return try {
            pendingLosses.remove(epoch)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Epoch-scoped deferred-connect handoff for the contended [onOverlayConnected] path.
     *
     * Stores the one-shot callback when [tryBindLocked] (or another holder) owns [mutex]
     * so the install survives contention; the drain installs it after the holder
     * releases and closes it only when the epoch/target proves stale. Replaces any
     * previous pending connect for the same epoch (closing the replaced channel so no
     * bridge leaks) — only one callback per bind is expected. Stale validation mirrors
     * [markPendingLoss]: a callback that passed the first check but resumed after a
     * replacement advanced [bindEpoch] removes its own entry and reports false so the
     * caller closes it instead of scheduling against the replacement. Never throws.
     */
    private fun markPendingConnect(
        epoch: Long,
        connection: FeedOverlayConnection,
        servicePackage: String?,
        serviceClassName: String?,
    ): Boolean {
        try {
            if (epoch != bindEpoch) return false
            val previous = pendingConnects.put(
                epoch,
                PendingConnect(connection, servicePackage, serviceClassName),
            )
            if (previous != null && previous.connection !== connection) {
                try {
                    previous.connection.close()
                } catch (_: Exception) {
                    // Replaced-channel teardown never throws.
                }
            }
            if (epoch != bindEpoch) {
                pendingConnects.remove(epoch)
                return false
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Consumes the deferred connect for [epoch], or null when none is pending. Removes
     * only the caller's own epoch so a stale entry never installs over its replacement.
     * Never throws.
     */
    private fun takePendingConnect(epoch: Long): PendingConnect? {
        return try {
            pendingConnects.remove(epoch)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Background installer for a deferred connect that contended against the bind holder.
     *
     * Retries unbounded with a short delay so the holder's preflight/bind can finish;
     * each attempt re-validates under lock and closes the deferred channel without
     * installing when the epoch/target is stale. A durable [pendingStop]/[pendingDetach]
     * wins over the install: the teardown runs first and the deferred channel is closed
     * as stale. Never throws.
     */
    private fun schedulePendingConnectDrain(epoch: Long) {
        try {
            val drainScope = scope ?: CoroutineScope(ioDispatcher)
            drainScope.launch(ioDispatcher) {
                try {
                    while (true) {
                        if (mutex.tryLock()) {
                            try {
                                if (drainPendingTeardownLocked()) {
                                    val stale = takePendingConnect(epoch)
                                    if (stale != null) {
                                        try {
                                            stale.connection.close()
                                        } catch (_: Exception) {
                                            // Stale teardown never throws.
                                        }
                                    }
                                    break
                                }
                                val pending = takePendingConnect(epoch)
                                if (pending == null) break
                                installConnectedLocked(
                                    pending.connection,
                                    epoch,
                                    pending.servicePackage,
                                    pending.serviceClassName,
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

    /**
     * Runs a durable [stop]/[detach]sup intent while holding [mutex].
     *
     * Returns true when a pending teardown was consumed (the caller should stop its own
     * work — the teardown already ran). [pendingStop] supersedes [pendingDetach] and
     * clears both; detach clears only its own flag. Deliberate teardowns clear the
     * diagnostic/evidence to idle without reporting a failure. Caller must hold [mutex].
     */
    private fun drainPendingTeardownLocked(): Boolean {
        if (pendingStop) {
            pendingStop = false
            pendingDetach = false
            if (scope != null) {
                scope = null
                resolveJob?.cancel()
                resolveJob = null
                packageMonitor.stop()
                unbindLocked()
                boundTarget = null
                discoveryDiagnostic = null
                _diagnostic.tryEmit(null)
                _bindingEvidence.tryEmit(FeedBindingEvidence())
            } else {
                // Already stopped: drop any stale deferred work without publishing.
                pendingLosses.clear()
            }
            return true
        }
        if (pendingDetach) {
            pendingDetach = false
            unbindLocked()
            boundTarget = null
            discoveryDiagnostic = null
            _diagnostic.tryEmit(null)
            _bindingEvidence.tryEmit(FeedBindingEvidence())
            return true
        }
        return false
    }

    /**
     * Background consumer that serializes a contended [stop]/[detach] after the holder
     * releases [mutex]. Retries unbounded so a `MainActivity.onStop` racing a blocking
     * preflight/bind is guaranteed its cleanup instead of returning with the
     * monitor/binding still active. Never throws.
     *
     * Durable across caller-scope cancellation: the drain launches detached
     * ([NonCancellable], falling back to a fresh scope when the stored scope is
     * already cancelled) so a `lifecycleScope` cancelled in `onStop` cannot abort the
     * wait — the pending intent stays queued until the holder releases and the
     * cleanup is serialized.
     */
    private fun schedulePendingTeardownDrain() {
        try {
            val stored = scope
            val drainScope =
                if (stored != null && stored.coroutineContext[Job]?.isActive == true) {
                    stored
                } else {
                    CoroutineScope(ioDispatcher + SupervisorJob())
                }
            drainScope.launch(ioDispatcher + NonCancellable) {
                try {
                    while (true) {
                        if (mutex.tryLock()) {
                            try {
                                drainPendingTeardownLocked()
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
                    // Teardown drain never reports a host error.
                }
            }
        } catch (_: Exception) {
            // Scheduling never throws.
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
     * that already cleared) is consumed without publishing. Reports the deferred
     * verdict (`DISCONNECTED` for transport loss, `BIND_FAILED` for contended
     * null-binding/mismatch) and retains the failure diagnostic. Never throws.
     */
    private fun schedulePendingLossDrain(epoch: Long) {
        try {
            val drainScope = scope ?: CoroutineScope(ioDispatcher)
            drainScope.launch(ioDispatcher) {
                try {
                    while (true) {
                        if (mutex.tryLock()) {
                            try {
                                if (drainPendingTeardownLocked()) break
                                if (epoch != bindEpoch) {
                                    pendingLosses.remove(epoch)
                                    break
                                }
                                val verdict = takePendingLoss(epoch) ?: break
                                val cause = verdict.cause
                                val status = verdict.status
                                if (boundTarget == null && boundProvider == null &&
                                    boundPackage == null &&
                                    boundConnection == null && overlayBridge == null
                                ) {
                                    // Spurious current-epoch mark with nothing bound
                                    // (idle gateway): consumed, nothing to tear down.
                                    break
                                }
                                val lost = boundTarget
                                unbindLocked()
                                boundTarget = null
                                val diagnostic = FeedDiagnostic(
                                    status = status,
                                    cause = cause,
                                    provider = lost?.provider,
                                    packageName = lost?.component?.packageName,
                                    component = lost?.component,
                                )
                                _diagnostic.tryEmit(diagnostic)
                                _bindingEvidence.tryEmit(
                                    FeedBindingEvidence(
                                        target = lost,
                                        attempted = lost != null,
                                        resolvability = if (lost != null) {
                                            FeedTargetResolvability.RESOLVABLE
                                        } else {
                                            null
                                        },
                                        accepted = lost != null,
                                        connected = false,
                                        active = false,
                                        epoch = epoch,
                                    ),
                                )
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

    /**
     * One accepted bind's framework callback, carrying its exact target + epoch.
     *
     * Verifies the connected package + class against the attempted [target] before any
     * [AidlFeedBridge] is built: an unexpected component is rejected as `BIND_FAILED`
     * (never installs the binder) via [onUnexpectedComponent], and a null binder via
     * [onNullBindingReceived]. Acceptance (`bindService` true) and connection (matching
     * binder here) stay distinct until [onOverlayConnected] correlates both for the
     * same target + epoch. Disconnect/death delegate to the epoch-scoped
     * [onServiceDisconnectedForEpoch]/[onBindingDiedForEpoch] so a stale generation
     * never tears down its replacement. Protocol-stage failures from the bridge's
     * first-failure listener delegate to [onProtocolFailureForEpoch] with the same
     * epoch scoping, so the earliest broken stage wins over the accompanying
     * transport loss.
     */
    private inner class OverlayConnection(
        private val target: FeedProviderTarget,
        val epoch: Long,
    ) : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val servicePackage: String? = try {
                name.packageName
            } catch (_: Exception) {
                null
            }
            val serviceClass: String? = try {
                name.className
            } catch (_: Exception) {
                null
            }
            if (servicePackage != target.component.packageName ||
                serviceClass != target.component.className
            ) {
                onUnexpectedComponent(epoch)
                return
            }
            // Built outside the gateway lock: construction links the death recipient, and
            // an already-dead binder reports loss synchronously during construction — with
            // a free lock that loss path clears the stale bind first, so the install below
            // then sees no bind and drops the dead channel instead of keeping it.
            // The loss callback is epoch-scoped so a stale generation's death never tears
            // down the current generation. The protocol-failure callback carries the
            // same epoch so the earliest broken stage wins over the accompanying loss.
            val bridge = try {
                AidlFeedBridge(
                    service,
                    onConnectionLost = { onBindingDiedForEpoch(epoch) },
                    onProtocolFailure = { status, cause ->
                        onProtocolFailureForEpoch(epoch, status, cause)
                    },
                )
            } catch (_: Exception) {
                onBindingDiedForEpoch(epoch)
                return
            }
            onOverlayConnected(bridge, epoch, servicePackage, serviceClass)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            onServiceDisconnectedForEpoch(epoch)
        }

        override fun onBindingDied(name: ComponentName) {
            onBindingDiedForEpoch(epoch)
        }

        override fun onNullBinding(name: ComponentName) {
            onNullBindingReceived(epoch)
        }
    }

    companion object {
        /** Delay between unbounded drain retries; virtual-time friendly under `runTest`. */
        private const val PENDING_DRAIN_RETRY_MS = 1L
    }
}

/**
 * Production [FeedServiceConnector]: binds `com.android.launcher3.WINDOW_OVERLAY` as an
 * explicit component with `BIND_AUTO_CREATE`, no package fallback.
 *
 * The intent carries both the overlay action and the exact `ComponentName` retained
 * from resolution, so the bind addresses the discovered service instead of re-guessing
 * it. Exactly one bind per target; a false result is acceptance-denied (not proof of
 * absence) and throws classify by stage (security vs other, detail redacted).
 */
private class AndroidFeedConnector(private val context: Context) : FeedServiceConnector {

    override fun bind(connection: ServiceConnection, target: FeedProviderTarget): Boolean {
        val component = ComponentName(
            target.component.packageName,
            target.component.className,
        )
        val intent = Intent(FEED_OVERLAY_ACTION).setComponent(component)
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
 * Production [FeedTargetPreflight]: verifies the exact component still publishes the
 * overlay service.
 *
 * Queries the same [FEED_OVERLAY_ACTION] scoped to the target's package and requires
 * an exact package + class match. A miss is definitive (`NOT_RESOLVABLE`); a platform
 * denial or any other failure is unknown resolvability (`UNKNOWN_DENIED` /
 * `UNKNOWN_FAILED`), never absence — and never throws, so the gateway maps rather
 * than propagates. Holds the application context only, never an activity. Probing
 * never binds: binding stays with the gateway.
 */
private class AndroidFeedPreflight(private val context: Context) : FeedTargetPreflight {

    override fun verify(target: FeedProviderTarget): FeedTargetResolvability {
        return try {
            val packageManager = context.packageManager
            val intent = Intent(FEED_OVERLAY_ACTION)
                .setPackage(target.component.packageName)
                .setData(Uri.parse(FeedHostUri.build(context.packageName, Process.myUid())))
            val services = if (android.os.Build.VERSION.SDK_INT >= 33) {
                packageManager.queryIntentServices(
                    intent,
                    android.content.pm.PackageManager.ResolveInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentServices(intent, 0)
            }
            val match = (services ?: emptyList())
                .mapNotNull { it.serviceInfo }
                .any {
                    it.packageName == target.component.packageName &&
                        it.name == target.component.className
                }
            if (match) {
                FeedTargetResolvability.RESOLVABLE
            } else {
                FeedTargetResolvability.NOT_RESOLVABLE
            }
        } catch (_: SecurityException) {
            FeedTargetResolvability.UNKNOWN_DENIED
        } catch (_: Exception) {
            FeedTargetResolvability.UNKNOWN_FAILED
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
