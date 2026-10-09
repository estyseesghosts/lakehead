package me.foxtails.lakehead.platform.notifications

/** A cached photo file: its [key] and when it was last written. */
data class CachedPhoto(val key: String, val lastModified: Long)

/**
 * Stable, filesystem-safe key for one notification post: hex of the string hash
 * pair, so the same notification id and post time always map to the same file.
 */
fun photoKeyFor(notificationId: String, postTime: Long): String {
    val text = "$notificationId|$postTime"
    return "%08x%08x".format(text.hashCode(), text.reversed().hashCode())
}

/**
 * Keys to delete so the cache holds only photos some tracked notification still
 * references, and no more than [maxFiles] of those (oldest dropped first).
 */
fun photosToDelete(cached: List<CachedPhoto>, referenced: Set<String>, maxFiles: Int): List<String> {
    require(maxFiles >= 0) { "maxFiles must be non-negative" }
    val (kept, orphans) = cached.partition { it.key in referenced }
    val overflow = kept.sortedByDescending { it.lastModified }.drop(maxFiles)
    return (orphans + overflow).map { it.key }
}
