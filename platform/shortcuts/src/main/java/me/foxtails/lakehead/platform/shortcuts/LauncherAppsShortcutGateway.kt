package me.foxtails.lakehead.platform.shortcuts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.LauncherShortcut
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef

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
 * No `ShortcutInfo`, drawable or user handle leaves this boundary.
 */
class LauncherAppsShortcutGateway(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ShortcutGateway {
    private val appContext = requireNotNull(context.applicationContext)
    private val launcherApps = requireNotNull(
        appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps,
    )
    private val userManager = requireNotNull(
        appContext.getSystemService(Context.USER_SERVICE) as? UserManager,
    )

    private val cache = ShortcutSnapshotCache()
    private val mutableShortcuts =
        MutableStateFlow<Map<AppIdentity, List<LauncherShortcut>>>(emptyMap())
    override val shortcuts: StateFlow<Map<AppIdentity, List<LauncherShortcut>>> =
        mutableShortcuts.asStateFlow()

    private val lifecycleLock = Any()
    private var scope: CoroutineScope? = null
    private var callback: ShortcutInvalidationSource? = null

    override fun shortcutsFor(identity: AppIdentity): List<LauncherShortcut> = cache.get(identity)

    override fun refresh(identity: AppIdentity) {
        val routeScope = synchronized(lifecycleLock) { scope } ?: return
        routeScope.launch(ioDispatcher) { queryAndPublish(identity) }
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

    /** Registers shortcut/package invalidation and serves query-on-select refreshes. */
    override fun start(scope: CoroutineScope) {
        synchronized(lifecycleLock) {
            if (callback != null) return
            val source = ShortcutInvalidationSource(
                profileIdFor = ::profileIdFor,
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
            launcherApps.registerCallback(source)
            callback = source
            this.scope = scope
            scope.coroutineContext[Job]?.invokeOnCompletion { stop() }
        }
    }

    /** Unregisters invalidation; the last published snapshot is retained. */
    override fun stop() {
        synchronized(lifecycleLock) {
            val registered = callback
            callback = null
            scope = null
            try {
                registered?.let(launcherApps::unregisterCallback)
            } catch (_: RuntimeException) {
                // Unregister races with process teardown; the snapshot stays published.
            }
        }
    }

    private fun refreshPackage(packageName: String, profileId: ProfileId?) {
        val routeScope = synchronized(lifecycleLock) { scope } ?: return
        routeScope.launch(ioDispatcher) {
            val targets = cache.identitiesForPackage(packageName)
                .filter { profileId == null || it.profileId == profileId }
            targets.forEach(::queryAndPublish)
        }
    }

    private fun dropPackage(packageName: String, profileId: ProfileId) {
        cache.removePackageForProfile(packageName, profileId)
        publish()
    }

    private fun queryAndPublish(identity: AppIdentity) {
        val user = userFor(identity.profileId) ?: return
        val rows = try {
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
            return
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: IllegalStateException) {
            return
        }
        cache.put(
            identity,
            mapShortcutRows(identity.component.packageName, identity.profileId, rows),
        )
        publish()
    }

    private fun publish() {
        mutableShortcuts.value = cache.snapshot()
    }

    private fun profileIdFor(user: UserHandle): ProfileId? {
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
