package me.foxtails.lakehead.platform.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.WidgetProviderEntry

/**
 * Installed widget providers across the accessible profiles.
 *
 * Owns the single provider list. [refresh] enumerates off the main thread and
 * publishes the whole list; [start] refreshes once and again on every package
 * change reported by `LauncherApps`, so nothing enumerates during composition.
 */
class AppWidgetProviderCatalogue(context: Context) {
    private val appContext = context.applicationContext
    private val appWidgetManager = AppWidgetManager.getInstance(appContext)
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val refreshLock = Mutex()
    private val state = MutableStateFlow<List<WidgetProviderEntry>>(emptyList())
    private var callback: LauncherApps.Callback? = null

    val providers: StateFlow<List<WidgetProviderEntry>> = state.asStateFlow()

    suspend fun refresh() {
        refreshLock.withLock {
            state.value = withContext(Dispatchers.IO) { enumerate() }
        }
    }

    fun start(scope: CoroutineScope) {
        if (callback != null) return
        val invalidation = object : LauncherApps.Callback() {
            private fun invalidate() {
                scope.launch { refresh() }
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) = invalidate()
            override fun onPackageRemoved(packageName: String, user: UserHandle) = invalidate()
            override fun onPackageChanged(packageName: String, user: UserHandle) = invalidate()
            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = invalidate()

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = invalidate()
        }
        callback = invalidation
        launcherApps.registerCallback(invalidation)
        scope.launch { refresh() }
    }

    fun stop() {
        callback?.let(launcherApps::unregisterCallback)
        callback = null
    }

    private fun enumerate(): List<WidgetProviderEntry> {
        val entries = ArrayList<WidgetProviderEntry>()
        for (user in launcherApps.profiles) {
            val profileId = profileIdFor(user) ?: continue
            for (info in appWidgetManager.getInstalledProvidersForProfile(user)) {
                entries += info.toRaw(user).toEntry(profileId)
            }
        }
        return entries.sortedWith(widgetProviderOrder)
    }

    private fun profileIdFor(user: UserHandle): ProfileId? = try {
        userManager.getSerialNumberForUser(user).takeIf { it >= 0L }?.let { ProfileId(it.toString()) }
    } catch (_: SecurityException) {
        null
    }

    private fun AppWidgetProviderInfo.toRaw(user: UserHandle): RawWidgetProvider {
        val api31 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return RawWidgetProvider(
            packageName = provider.packageName,
            className = provider.className,
            label = loadLabel(appContext.packageManager),
            appLabel = appLabelFor(provider.packageName, user),
            minWidthDp = dpOf(minWidth),
            minHeightDp = dpOf(minHeight),
            minResizeWidthDp = dpOf(minResizeWidth),
            minResizeHeightDp = dpOf(minResizeHeight),
            targetCellWidth = if (api31) targetCellWidth else 0,
            targetCellHeight = if (api31) targetCellHeight else 0,
            hasConfigureActivity = configure != null,
        )
    }

    private fun appLabelFor(packageName: String, user: UserHandle): String? = try {
        launcherApps.getApplicationInfo(packageName, 0, user)
            .loadLabel(appContext.packageManager).toString()
    } catch (_: Exception) {
        null
    }

    /** Provider sizes are reported in pixels; the domain model carries dp. */
    private fun dpOf(px: Int): Int {
        val density = appContext.resources.displayMetrics.density
        return Math.round(px / density)
    }
}
