package me.foxtails.lakehead

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.platform.launcherapps.AppIconSnapshot
import java.util.Collections
import java.util.LinkedHashMap

/** Decodes platform PNG snapshots away from composition and retains a bounded immutable view. */
internal class DecodedHomeIconCache<T : Any>(
    private val maxEntries: Int,
    private val decoder: (ByteArray) -> T?,
) {
    private val entries = LinkedHashMap<AppIdentity, T>()

    init { require(maxEntries > 0) }

    @Synchronized
    fun replace(snapshots: Map<AppIdentity, AppIconSnapshot>): Map<AppIdentity, T> {
        entries.clear()
        snapshots.forEach { (identity, snapshot) ->
            val decoded = try { decoder(snapshot.pngBytes) } catch (_: Exception) { null }
            if (decoded != null) entries[identity] = decoded
            while (entries.size > maxEntries) entries.remove(entries.keys.first())
        }
        return snapshot()
    }

    @Synchronized
    fun snapshot(): Map<AppIdentity, T> =
        Collections.unmodifiableMap(LinkedHashMap(entries))
}
