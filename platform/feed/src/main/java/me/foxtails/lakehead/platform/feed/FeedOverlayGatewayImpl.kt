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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

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
 *   `Unavailable(NO_PROVIDER)` until the first resolution completes.
 * - Exactly one connection exists at a time; attaching the same provider reuses it, attaching a
 *   different one unbinds first.
 * - Package changes re-resolve off-main, emit the new verdict and auto-detach when the bound
 *   provider no longer resolves.
 * - `onServiceDisconnected`/`onBindingDied` clear the connection and emit `Unavailable`
 *   without ever throwing; binder callbacks use `tryEmit` because they cannot suspend.
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
     * Latest overlay progress requested by the host (0..1).
     *
     * Cached so a late bind can catch up; delivery itself is binder work that runs only while a
     * service is connected. The provider AIDL is not vendored yet, so delivery is a safe no-op
     * until C3 wires the real control channel — this cache is the forwarding handoff, not a
     * silent drop: values are retained, never reset by unbinds.
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

    private val connection = OverlayConnection()

    override fun start(scope: CoroutineScope) {
        if (!mutex.tryLock()) return
        try {
            if (this.scope != null) return
            this.scope = scope
            packageMonitor.start(::onPackageChanged)
            resolveJob = scope.launch(ioDispatcher) {
                _state.emit(resolver.resolve())
            }
        } finally {
            mutex.unlock()
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
        if (!mutex.tryLock()) return
        try {
            if (boundProvider == provider && boundPackage != null) return
            unbindLocked()
            for (packageName in provider.packageNames) {
                val accepted = try {
                    connector.bind(connection, packageName)
                } catch (_: Exception) {
                    false
                }
                if (accepted) {
                    boundProvider = provider
                    boundPackage = packageName
                    _state.tryEmit(FeedAvailability.Available(provider))
                    return
                }
            }
            _state.tryEmit(
                FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
            )
        } finally {
            mutex.unlock()
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
     * Caches [visible] first so the value survives unbinds; delivery to the service control
     * channel lands with the provider AIDL in C3. Never throws, including when unbound.
     */
    fun notifyOverlayVisible(visible: Boolean) {
        overlayVisible = visible
        deliverToOverlay()
    }

    /**
     * Forwards a host scroll progress change (0..1) toward the bound overlay.
     *
     * Same cache-then-deliver contract as [notifyOverlayVisible]; clamps to 0..1 and never
     * throws, including when unbound.
     */
    fun notifyOverlayProgress(progress: Float) {
        overlayProgress = progress.coerceIn(0f, 1f)
        deliverToOverlay()
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
        try {
            if (!mutex.tryLock()) {
                _state.tryEmit(
                    FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED),
                )
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

    private fun onPackageChanged() {
        scope?.launch(ioDispatcher) {
            val availability = try {
                resolver.resolve()
            } catch (_: Exception) {
                FeedAvailability.Unavailable(FeedAvailability.Unavailable.Reason.QUERY_FAILED)
            }
            if (!mutex.tryLock()) return@launch
            try {
                _state.emit(availability)
                val current = boundProvider
                if (current != null && availability != FeedAvailability.Available(current)) {
                    unbindLocked()
                }
            } finally {
                mutex.unlock()
            }
        }
    }

    private fun deliverToOverlay() {
        // No provider AIDL is vendored yet, so there is no transact code to invoke here; the
        // request is retained in overlayVisible/overlayProgress for the C3 control channel.
        // This method is the single forwarding point so C3 adds delivery without touching
        // callers. It must not throw when unbound.
        if (boundPackage == null) return
    }

    private fun unbindLocked() {
        if (boundProvider == null && boundPackage == null) return
        boundProvider = null
        boundPackage = null
        try {
            connector.unbind(connection)
        } catch (_: Exception) {
            // Unbind races (e.g. service already gone) are not host errors.
        }
    }

    private inner class OverlayConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            try {
                service.linkToDeath(
                    { onConnectionLost() },
                    0,
                )
            } catch (_: Exception) {
                // Death-recipient registration is best-effort; disconnect callbacks still apply.
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            onConnectionLost()
        }

        override fun onBindingDied(name: ComponentName) {
            onConnectionLost()
        }
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
