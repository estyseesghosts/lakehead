package me.foxtails.lakehead.platform.launcherapps

import kotlinx.coroutines.flow.StateFlow
import me.foxtails.lakehead.core.model.LauncherAppEntry

/**
 * Domain-facing, framework-free view of launchable apps and their user-visible labels across
 * accessible profiles. Labels are resolved to plain strings at the platform boundary; no framework
 * types are exposed. Launching and callbacks are exercised through their dedicated contracts.
 */
interface AppCatalogue {
    /** Observable contract for the current launchable app snapshot and all subsequent updates. */
    val apps: StateFlow<List<LauncherAppEntry>>

    /**
     * Returns a point-in-time read of [apps]'s current value. This method performs no enumeration
     * and does not provide update notifications; observe [apps] for updates.
     */
    fun cachedApps(): List<LauncherAppEntry> = apps.value
}
