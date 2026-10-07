package me.foxtails.lakehead

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshot
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Decodes platform PNG snapshots away from composition and retains an immutable view for the
 * whole catalogue. Shared by Home and All Apps. Unbounded by design: entries track the
 * catalogue and each decoded icon is small.
 *
 * Updates are incremental: a snapshot that is the same instance as the one already decoded is
 * reused without decoding, so the published value for an unchanged icon is the same object.
 */
internal class DecodedIconCache<T : Any>(
    private val decoder: (ByteArray) -> T?,
) {
    private class Entry<T>(val source: AppIconSnapshot, val decoded: T)

    private val entries = LinkedHashMap<AppIdentity, Entry<T>>()
    private var published: Map<AppIdentity, T> = Collections.emptyMap()

    /**
     * Brings the decoded entries in line with [snapshots]: removed identities are dropped, new
     * or changed snapshots are decoded, unchanged ones are reused. An identity whose decode
     * fails keeps its last-good image, and an empty input keeps every entry, so a transient
     * failure never publishes an empty icon map. The returned map is the same instance as the
     * previous one when nothing changed.
     */
    @Synchronized
    fun replace(snapshots: Map<AppIdentity, AppIconSnapshot>): Map<AppIdentity, T> {
        if (snapshots.isEmpty()) return published

        val next = LinkedHashMap<AppIdentity, Entry<T>>(snapshots.size * 2)
        var changed = snapshots.size != entries.size
        snapshots.forEach { (identity, snapshot) ->
            val previous = entries[identity]
            if (previous != null && previous.source === snapshot) {
                next[identity] = previous
                return@forEach
            }
            val decoded = try { decoder(snapshot.pngBytes) } catch (_: Exception) { null }
            when {
                decoded != null -> {
                    next[identity] = Entry(snapshot, decoded)
                    changed = true
                }
                previous != null -> next[identity] = previous
            }
        }
        if (!changed && next.keys.toList() == entries.keys.toList()) return published

        entries.clear()
        entries.putAll(next)
        published = publish()
        return published
    }

    @Synchronized
    fun snapshot(): Map<AppIdentity, T> = published

    private fun publish(): Map<AppIdentity, T> {
        val view = LinkedHashMap<AppIdentity, T>(entries.size * 2)
        entries.forEach { (identity, entry) -> view[identity] = entry.decoded }
        return Collections.unmodifiableMap(view)
    }
}
