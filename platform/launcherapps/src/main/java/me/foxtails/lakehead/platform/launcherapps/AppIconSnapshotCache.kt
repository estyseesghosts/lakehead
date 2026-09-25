package me.foxtails.lakehead.platform.launcherapps

import me.foxtails.lakehead.core.model.AppIdentity
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Bounded owner of the current platform icon snapshots. Call [replaceAll] with a complete current
 * snapshot set; identities omitted from that set are removed. If there are more snapshots than the
 * configured limit, oldest entries in the supplied order are evicted (FIFO). Repeated identities
 * use the last supplied snapshot and its last-occurrence position for eviction ordering.
 *
 * Methods are synchronized, so replacement, invalidation, lookup, and returned point-in-time map
 * snapshots are safe across threads. Callers must not concurrently mutate the iterable passed to
 * [replaceAll] while that call is in progress. The cache does not enumerate apps, resolve labels,
 * or decode images. Since each PNG value is itself size-capped, retained encoded image data is
 * bounded by [maxEntries] × [AppIconSnapshot.MAX_PNG_BYTES].
 */
class AppIconSnapshotCache(val maxEntries: Int) {
    private val entries = LinkedHashMap<AppIdentity, AppIconSnapshot>()

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    /** Replaces all entries from a complete current set, preserving deterministic input ordering. */
    @Synchronized
    fun replaceAll(snapshots: Iterable<AppIconSnapshot>) {
        entries.clear()
        for (snapshot in snapshots) {
            entries.remove(snapshot.identity)
            entries[snapshot.identity] = snapshot
        }
        while (entries.size > maxEntries) {
            val oldestIdentity = entries.keys.iterator().next()
            entries.remove(oldestIdentity)
        }
    }

    @Synchronized
    operator fun get(identity: AppIdentity): AppIconSnapshot? = entries[identity]

    /** Returns an immutable, detached view in deterministic eviction/insertion order. */
    @Synchronized
    fun snapshot(): Map<AppIdentity, AppIconSnapshot> =
        Collections.unmodifiableMap(LinkedHashMap(entries))

    /** Removes every cached icon. */
    @Synchronized
    fun invalidate() {
        entries.clear()
    }
}
