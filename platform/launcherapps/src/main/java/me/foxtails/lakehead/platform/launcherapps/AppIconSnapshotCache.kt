package me.foxtails.lakehead.platform.launcherapps

import me.foxtails.lakehead.core.model.AppIdentity
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Owner of the current platform icon snapshots for the whole catalogue. Call [replaceAll] with a
 * complete current snapshot set; identities omitted from that set are removed. Repeated
 * identities use the last supplied snapshot and its last-occurrence position.
 *
 * There is deliberately no entry cap: entries track the catalogue, and memory is already bounded
 * per entry by the encoder size and [AppIconSnapshot.MAX_PNG_BYTES]. A numeric cap would only
 * make icons silently disappear for large catalogues.
 *
 * Methods are synchronized, so replacement, invalidation, lookup, and returned point-in-time map
 * snapshots are safe across threads. Callers must not concurrently mutate the iterable passed to
 * [replaceAll] while that call is in progress. The cache does not enumerate apps, resolve labels,
 * or decode images.
 */
class AppIconSnapshotCache {
    private val entries = LinkedHashMap<AppIdentity, AppIconSnapshot>()

    /** Replaces all entries from a complete current set, preserving deterministic input ordering. */
    @Synchronized
    fun replaceAll(snapshots: Iterable<AppIconSnapshot>) {
        entries.clear()
        for (snapshot in snapshots) {
            entries.remove(snapshot.identity)
            entries[snapshot.identity] = snapshot
        }
    }

    @Synchronized
    operator fun get(identity: AppIdentity): AppIconSnapshot? = entries[identity]

    /** Returns an immutable, detached view in deterministic insertion order. */
    @Synchronized
    fun snapshot(): Map<AppIdentity, AppIconSnapshot> =
        Collections.unmodifiableMap(LinkedHashMap(entries))

    /** Removes every cached icon. */
    @Synchronized
    fun invalidate() {
        entries.clear()
    }
}
