package me.foxtails.lakehead.platform.shortcuts

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.LauncherShortcut
import me.foxtails.lakehead.core.model.ProfileId
import java.util.Collections

/**
 * Owns the immutable, per-app shortcut snapshot published by [ShortcutGateway].
 * Entries are keyed by full [AppIdentity] so the same package in two profiles
 * never shares a list. The cache is bounded: inserting beyond [maxEntries]
 * evicts the least-recently-used identity. All published maps are immutable.
 */
internal class ShortcutSnapshotCache(maxEntries: Int = 64) {
    private val max: Int = maxEntries.also { require(maxEntries > 0) { "maxEntries must be positive" } }
    private val lock = Any()

    // Access-ordered so reads refresh recency and the eldest entry evicts first.
    private val entries = object : LinkedHashMap<AppIdentity, List<LauncherShortcut>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<AppIdentity, List<LauncherShortcut>>): Boolean =
            size > max
    }

    fun put(identity: AppIdentity, shortcuts: List<LauncherShortcut>) {
        synchronized(lock) {
            entries[identity] = Collections.unmodifiableList(ArrayList(shortcuts))
        }
    }

    fun get(identity: AppIdentity): List<LauncherShortcut> = synchronized(lock) {
        entries[identity].orEmpty()
    }

    fun snapshot(): Map<AppIdentity, List<LauncherShortcut>> = synchronized(lock) {
        Collections.unmodifiableMap(LinkedHashMap(entries))
    }

    fun identitiesForPackage(packageName: String): List<AppIdentity> = synchronized(lock) {
        entries.keys.filter { it.component.packageName == packageName }
    }

    fun removePackage(packageName: String) {
        synchronized(lock) {
            entries.keys.filter { it.component.packageName == packageName }.forEach(entries::remove)
        }
    }

    /**
     * Drops only [packageName]'s entries in [profileId]; the same package in
     * another profile keeps its snapshot. Used for profile-scoped
     * `onPackageRemoved` callbacks.
     */
    fun removePackageForProfile(packageName: String, profileId: ProfileId) {
        synchronized(lock) {
            entries.keys
                .filter { it.component.packageName == packageName && it.profileId == profileId }
                .forEach(entries::remove)
        }
    }

    fun size(): Int = synchronized(lock) { entries.size }
}
