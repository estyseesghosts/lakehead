package me.foxtails.lakehead

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshot
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Decodes platform PNG snapshots away from composition and retains an immutable view for the
 * whole catalogue. Shared by Home and All Apps. Unbounded by design: entries track the
 * catalogue and each decoded icon is small.
 */
internal class DecodedIconCache<T : Any>(
    private val decoder: (ByteArray) -> T?,
) {
    private val entries = LinkedHashMap<AppIdentity, T>()

    /**
     * Decodes [snapshots] and replaces the internal map. If every decode attempt fails or the
     * input is empty, the previous (last-good) entries are preserved so a transient failure
     * never publishes an empty icon map.
     */
    @Synchronized
    fun replace(snapshots: Map<AppIdentity, AppIconSnapshot>): Map<AppIdentity, T> {
        val newEntries = LinkedHashMap<AppIdentity, T>()
        snapshots.forEach { (identity, snapshot) ->
            val decoded = try { decoder(snapshot.pngBytes) } catch (_: Exception) { null }
            if (decoded != null) newEntries[identity] = decoded
        }
        if (newEntries.isNotEmpty()) {
            entries.clear()
            entries.putAll(newEntries)
        }
        // When newEntries is empty the previous entries remain as last-good.
        return snapshot()
    }

    @Synchronized
    fun snapshot(): Map<AppIdentity, T> =
        Collections.unmodifiableMap(LinkedHashMap(entries))
}
