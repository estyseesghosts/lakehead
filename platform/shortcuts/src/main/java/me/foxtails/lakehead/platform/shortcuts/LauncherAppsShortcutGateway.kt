package me.foxtails.lakehead.platform.shortcuts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.LauncherShortcut
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef

/**
 * Internal platform boundary for shortcut queries and launches.
 *
 * The gateway owns fencing, caching, publishing and lifecycle; this seam
 * owns only the framework calls so unit tests can drive the gateway's
 * fence/stop semantics with a controllable fake and production keeps the
 * `LauncherApps`/`UserManager` calls in one place. Framework types never
 * leave `:platform:shortcuts`.
 */
internal interface ShortcutPlatform {
    /**
     * Returns the platform rows for [identity], or null when the profile is
     * inaccessible or the platform rejects the query (keep last-good, no
     * publish). A non-null list (possibly empty) is a successful query that
     * publishes, even when empty.
     */
    suspend fun queryRows(identity: AppIdentity): List<ShortcutQueryRow>?

    fun launch(ref: ShortcutRef): Boolean
    fun registerCallback(callback: LauncherApps.Callback)
    fun unregisterCallback(callback: LauncherApps.Callback)
    fun profileIdFor(user: UserHandle): ProfileId?
}

private class RealShortcutPlatform(appContext: Context) : ShortcutPlatform {
    private val launcherApps = requireNotNull(
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps,
    )
    private val userManager = requireNotNull(
        appContext.getSystemService(Context.USER_SERVICE) as? UserManager,
    )

    override suspend fun queryRows(identity: AppIdentity): List<ShortcutQueryRow>? {
        val user = userFor(identity.profileId) ?: return null
        return try {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(identity.component.packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC,
                )
            launcherApps.getShortcuts(query, user).orEmpty().map { info ->
                ShortcutQueryRow(
                    id = info.id,
                    shortLabel = info.shortLabel,
                    longLabel = info.longLabel,
                    rank = info.rank,
                )
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    override fun launch(ref: ShortcutRef): Boolean {
        val user = userFor(ref.profileId) ?: return false
        // startShortcut returns Unit (void) and signals failure by throwing;
        // the Boolean contract is derived from it: no throw -> true.
        return try {
            launcherApps.startShortcut(ref.packageName, ref.shortcutId, null, null, user)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    override fun registerCallback(callback: LauncherApps.Callback) {
        launcherApps.registerCallback(callback)
    }

    override fun unregisterCallback(callback: LauncherApps.Callback) {
        launcherApps.unregisterCallback(callback)
    }

    override fun profileIdFor(user: UserHandle): ProfileId? {
        if (user !in launcherApps.profiles) return null
        return try {
            val serial = userManager.getSerialNumberForUser(user)
            if (serial < 0L) null else ProfileId(serial.toString())
        } catch (_: SecurityException) {
            null
        }
    }

    private fun userFor(profileId: ProfileId): UserHandle? {
        val serial = profileId.value.toLongOrNull() ?: return null
        if (serial < 0L || serial.toString() != profileId.value) return null
        return try {
            userManager.getUserForSerialNumber(serial)?.takeIf { it in launcherApps.profiles }
        } catch (_: SecurityException) {
            null
        }
    }
}

/**
 * Profile-aware `LauncherApps` shortcut inventory behind [ShortcutGateway].
 *
 * Queries run on selection: [refresh] requeries one [AppIdentity] via
 * `LauncherApps.getShortcuts(FLAG_MATCH_MANIFEST | FLAG_MATCH_DYNAMIC)`
 * scoped to the identity's package, rank-orders the result, and republishes
 * it in [shortcuts]. Results are cached per identity in a bounded cache so
 * tap-switching between tiles never re-enters the platform. Shortcut-change
 * callbacks requery the changed package in the changed profile; package
 * change requeries every cached identity for that package; package removal
 * drops only that package's entries in the removed profile so the same
 * package in another profile keeps its snapshot and a zero-shortcut menu
 * hides honestly.
 *
 * Refresh work runs in the gateway-owned [workScope], parented to the
 * caller's scope job so process teardown still cancels it, but cancelled
 * independently by [stop] so a caller scope that stays active across
 * `onStop` (the production `lifecycleScope`) can never publish after stop.
 * Removal and stop advance the [RefreshVersionFence] so an in-flight query
 * that ignores cancellation still fails its commit instead of restoring a
 * removed entry or publishing after stop.
 *
 * No `ShortcutInfo`, drawable or user handle leaves this boundary.
 */
class LauncherAppsShortcutGateway internal constructor(
    private val platform: ShortcutPlatform,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShortcutGateway {
    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        RealShortcutPlatform(requireNotNull(context.applicationContext)),
        ioDispatcher,
    )

    private val cache = ShortcutSnapshotCache()
    private val mutableShortcuts =
        MutableStateFlow<Map<AppIdentity, List<LauncherShortcut>>>(emptyMap())
    override val shortcuts: StateFlow<Map<AppIdentity, List<LauncherShortcut>>> =
        mutableShortcuts.asStateFlow()

    private val lifecycleLock = Any()
    private var scope: CoroutineScope? = null
    private var workJob: Job? = null
    private var workScope: CoroutineScope? = null
    private var stopHandle: DisposableHandle? = null
    private var callback: ShortcutInvalidationSource? = null
    private val refreshFence = RefreshVersionFence()

    override fun shortcutsFor(identity: AppIdentity): List<LauncherShortcut> = cache.get(identity)

    override fun refresh(identity: AppIdentity) {
        synchronized(lifecycleLock) {
            val routeScope = workScope ?: return
            val version = refreshFence.next(identity)
            // Stamp and launch atomically under the lifecycle lock so stop
            // cannot interleave between them: stop either runs first (scope
            // null, no stamp, no query) or runs after (invalidateAll fails
            // the stamped commit and cancels this child of workJob).
            val job = routeScope.launch(ioDispatcher) { queryAndPublish(identity, version) }
            // Reclaim the stamp when the child never runs (cancelled before
            // start): abandon is idempotent per version, so the normal
            // tryCommit/abandon inside queryAndPublish makes this a no-op.
            job.invokeOnCompletion {
                refreshFence.abandon(identity, version)
                refreshFence.retire(identity, isCached = cache.contains(identity))
            }
        }
    }

    override suspend fun refreshAndAwait(identity: AppIdentity): Boolean {
        val deferred: Deferred<Boolean> = synchronized(lifecycleLock) {
            val routeScope = workScope ?: return false
            val owner = workJob ?: return false
            if (owner.isCancelled || owner.isCompleted) return false
            // Same atomic admission as refresh: stamp plus child creation
            // under one lock, owned by workJob so stop cancels the query.
            // A post-stop caller sees a null/cancelled owner and returns
            // false without querying.
            val version = refreshFence.next(identity)
            val created = routeScope.async(ioDispatcher) { queryAndPublish(identity, version) }
            created.invokeOnCompletion {
                refreshFence.abandon(identity, version)
                refreshFence.retire(identity, isCached = cache.contains(identity))
            }
            created
        }
        return try {
            deferred.await()
        } catch (e: CancellationException) {
            // Stop cancels the owned child while the caller stays active:
            // report failure without publishing. Caller cancellation still
            // propagates so `refreshAndAwait` stays cancellable, and the
            // admitted query is cancelled with the caller so it can never
            // publish after the caller gave up (including the coordinator's
            // timeout path, which cancels the awaiting child).
            if (currentCoroutineContext().isActive) false else {
                deferred.cancel(e)
                throw e
            }
        }
    }

    override fun launch(ref: ShortcutRef): Boolean = platform.launch(ref)

    /** Registers shortcut/package invalidation and serves query-on-select refreshes. */
    override fun start(scope: CoroutineScope) {
        synchronized(lifecycleLock) {
            if (callback != null) return
            val source = ShortcutInvalidationSource(
                profileIdFor = platform::profileIdFor,
                onShortcutsChanged = { packageName, profileId ->
                    refreshPackage(packageName, profileId)
                },
                onPackageChanged = { packageName ->
                    refreshPackage(packageName, null)
                },
                onPackageRemoved = { packageName, profileId ->
                    dropPackage(packageName, profileId)
                },
            )
            platform.registerCallback(source)
            callback = source
            this.scope = scope
            val parentJob = scope.coroutineContext[Job]
            val job = if (parentJob != null) SupervisorJob(parentJob) else SupervisorJob()
            workJob = job
            workScope = CoroutineScope(job + ioDispatcher)
            stopHandle = parentJob?.invokeOnCompletion { stop() }
        }
    }

    /**
     * Unregisters invalidation, cancels owned refresh work and fences
     * in-flight queries so post-stop completions (including blocking
     * platform calls that ignore cancellation) never publish; the last
     * published snapshot is retained. Safe to call repeatedly.
     */
    override fun stop() {
        val registered: ShortcutInvalidationSource?
        val job: Job?
        val handle: DisposableHandle?
        synchronized(lifecycleLock) {
            registered = callback
            job = workJob
            handle = stopHandle
            callback = null
            scope = null
            workJob = null
            workScope = null
            stopHandle = null
            // Fence first: an in-flight query that already passed its
            // pre-commit active check still fails tryCommit and can neither
            // restore a removed entry nor publish after stop.
            refreshFence.invalidateAll()
            try {
                registered?.let(platform::unregisterCallback)
            } catch (_: RuntimeException) {
                // Unregister races with process teardown; the snapshot stays published.
            }
        }
        handle?.dispose()
        job?.cancel()
        // Retire idle, non-cached fence state so the fence stays bounded to
        // cached plus in-flight identities. In-flight tombstones are kept
        // (outstanding > 0) until their queries finish and retire.
        refreshFence.trim(cache.cachedIdentities())
    }

    internal fun refreshPackage(packageName: String, profileId: ProfileId?) {
        val routeScope = synchronized(lifecycleLock) { workScope } ?: return
        routeScope.launch(ioDispatcher) {
            val targets = cache.identitiesForPackage(packageName)
                .filter { profileId == null || it.profileId == profileId }
            targets.forEach { identity ->
                if (!currentCoroutineContext().isActive) return@forEach
                val version = refreshFence.next(identity)
                try {
                    queryAndPublish(identity, version)
                } catch (e: CancellationException) {
                    // Stop/outer cancel: the stamp was already abandoned and
                    // retired inside queryAndPublish (idempotent with the
                    // refresh/refreshAndAwait completion handler); propagate
                    // so the batch cancels instead of continuing stale work.
                    throw e
                } catch (e: Exception) {
                    // Callback-refresh failure keeps last-good and continues
                    // with the remaining targets. The stamp was already
                    // abandoned and retired inside queryAndPublish, so
                    // repeated failures never accumulate fence state that
                    // would block retire.
                }
            }
        }
    }

    /**
     * Drops only [packageName]'s entries in [profileId] and fences that
     * package/profile so outstanding queries holding older stamps fail
     * their commit instead of restoring the removed snapshot.
     */
    internal fun dropPackage(packageName: String, profileId: ProfileId) {
        refreshFence.invalidatePackage(packageName, profileId)
        cache.removePackageForProfile(packageName, profileId)
        publish()
        // Retire idle, non-cached fence state after the removal tombstone.
        // In-flight queries for this package/profile keep their tombstone
        // (outstanding > 0) so their stale commits still fail; their
        // completion retires afterwards. Fail-closed: a retired stale stamp
        // still fails because `null != version`.
        refreshFence.trim(cache.cachedIdentities())
    }

    private suspend fun queryAndPublish(identity: AppIdentity, version: Long): Boolean {
        val rows = try {
            platform.queryRows(identity)
        } catch (e: CancellationException) {
            refreshFence.abandon(identity, version)
            refreshFence.retire(identity, isCached = cache.contains(identity))
            throw e
        } catch (e: Exception) {
            // Non-cancel platform failure (callback-refresh path included):
            // release this stamp and retire idle state via the same
            // abandon/retire completion as refresh/refreshAndAwait so
            // repeated failures never accumulate outstanding fence state
            // that would block retire. Keep last-good; the caller decides
            // whether to continue (callback batch) or propagate.
            refreshFence.abandon(identity, version)
            refreshFence.retire(identity, isCached = cache.contains(identity))
            throw e
        }
        if (rows == null) {
            refreshFence.abandon(identity, version)
            refreshFence.retire(identity, isCached = cache.contains(identity))
            return false
        }
        // Re-validate after the blocking query, before committing: a newer
        // stamp (removal/stop/newer refresh) or cancellation suppresses this
        // result so a stale query can never overwrite and publish over newer
        // state. The version check, cancellation check, cache write and
        // publish commit atomically under the fence lock.
        val callerContext = currentCoroutineContext()
        if (!callerContext.isActive) {
            refreshFence.abandon(identity, version)
            refreshFence.retire(identity, isCached = cache.contains(identity))
            return false
        }
        val mapped = mapShortcutRows(identity.component.packageName, identity.profileId, rows)
        val committed = refreshFence.tryCommit(
            identity,
            version,
            isCancelled = { !callerContext.isActive },
        ) {
            cache.put(identity, mapped)
            publish()
        }
        // Keep the fence bounded to cached plus in-flight state, including
        // identities evicted from the LRU cache by this put. In-flight
        // identities are retained by trim; a retired stale stamp still fails
        // because `null != version`.
        if (committed) {
            refreshFence.trim(cache.cachedIdentities())
        } else {
            refreshFence.retire(identity, isCached = cache.contains(identity))
            refreshFence.trim(cache.cachedIdentities())
        }
        return committed
    }

    private fun publish() {
        mutableShortcuts.value = cache.snapshot()
    }

    internal fun fenceSizeForTest(): Int = refreshFence.size()

    internal fun fenceOutstandingForTest(identity: AppIdentity): Int =
        refreshFence.outstandingCount(identity)
}
