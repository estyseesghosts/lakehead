package me.foxtails.lakehead.platform.launcherapps

import me.foxtails.lakehead.core.model.AppIdentity
import java.util.Collections
import java.util.LinkedHashMap

/** What an entry was encoded from: the source's staleness stamp and the encoder revision. */
data class AppIconProvenance(val sourceStamp: Long?, val encoderVersion: Int)

/**
 * Owner of the current platform icon snapshots for the whole catalogue. It is updated
 * incrementally: [retainOnly] drops identities that left the catalogue and [put] stores or
 * replaces one entry, so an unchanged entry keeps its [AppIconSnapshot] instance. Downstream
 * decoders rely on that instance identity to skip unchanged icons.
 *
 * There is deliberately no entry cap: entries track the catalogue, and memory is already bounded
 * per entry by the encoder size and [AppIconSnapshot.MAX_PNG_BYTES]. A numeric cap would only
 * make icons silently disappear for large catalogues.
 *
 * Methods are synchronized, so updates, invalidation, lookup, and returned point-in-time map
 * snapshots are safe across threads. The cache does not enumerate apps, resolve labels, or
 * decode images.
 */
class AppIconSnapshotCache {
    private class Entry(val snapshot: AppIconSnapshot, val provenance: AppIconProvenance)

    private val entries = LinkedHashMap<AppIdentity, Entry>()

    /** Stores [snapshot] with the [provenance] it was encoded from, replacing any older entry. */
    @Synchronized
    fun put(snapshot: AppIconSnapshot, provenance: AppIconProvenance) {
        entries[snapshot.identity] = Entry(snapshot, provenance)
    }

    /** Removes every entry whose identity is not in [identities]; kept entries are untouched. */
    @Synchronized
    fun retainOnly(identities: Set<AppIdentity>) {
        entries.keys.retainAll(identities)
    }

    @Synchronized
    operator fun get(identity: AppIdentity): AppIconSnapshot? = entries[identity]?.snapshot

    /** Returns how the entry for [identity] was produced, or null when there is none. */
    @Synchronized
    fun provenanceOf(identity: AppIdentity): AppIconProvenance? = entries[identity]?.provenance

    /** Returns an immutable, detached view in deterministic insertion order. */
    @Synchronized
    fun snapshot(): Map<AppIdentity, AppIconSnapshot> {
        val copy = LinkedHashMap<AppIdentity, AppIconSnapshot>(entries.size * 2)
        entries.forEach { (identity, entry) -> copy[identity] = entry.snapshot }
        return Collections.unmodifiableMap(copy)
    }

    /** Removes every cached icon. */
    @Synchronized
    fun invalidate() {
        entries.clear()
    }
}
