package me.foxtails.lakehead.platform.shortcuts

import kotlinx.coroutines.flow.StateFlow
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.LauncherShortcut
import me.foxtails.lakehead.core.model.ShortcutRef

/**
 * Domain-facing, framework-free view of published app shortcuts across
 * accessible profiles. Labels and ranks are resolved to plain values at the
 * platform boundary; no `ShortcutInfo`, drawable or user handle is exposed.
 * Queries run on selection (per [AppIdentity]); the published [shortcuts] map
 * is refreshed through platform shortcut-change and package callbacks.
 */
interface ShortcutGateway {
    /** Observable contract for the current per-app shortcut snapshot and all subsequent updates. */
    val shortcuts: StateFlow<Map<AppIdentity, List<LauncherShortcut>>>

    /**
     * Returns a point-in-time read of [shortcuts]'s entry for [identity], or an
     * empty list when nothing has been published yet. Performs no platform
     * query; call [refresh] to requery, and observe [shortcuts] for updates.
     */
    fun shortcutsFor(identity: AppIdentity): List<LauncherShortcut> = shortcuts.value[identity].orEmpty()

    /** Requeries the platform for [identity]'s published shortcuts and republishes the result. */
    fun refresh(identity: AppIdentity)

    /**
     * Starts the shortcut named by [ref] in exactly the accessible profile
     * named by the ref. Returns false when the profile is inaccessible or the
     * platform rejects the start; never throws for launch failures.
     */
    fun launch(ref: ShortcutRef): Boolean

    /** Registers invalidation sources; safe to call repeatedly. */
    fun start(scope: kotlinx.coroutines.CoroutineScope)

    /** Unregisters invalidation sources and stops refresh work; safe to call repeatedly. */
    fun stop()
}
