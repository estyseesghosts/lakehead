package me.foxtails.lakehead.platform.media

import me.foxtails.lakehead.core.model.AlbumPhoto
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.MediaAlbum

/**
 * One `MediaStore` image row. The gateway builds these from the cursor;
 * aggregation below stays pure and cursor-free.
 */
data class MediaImageRow(
    val imageId: Long,
    val bucketId: String,
    val bucketName: String,
    val dateTakenMillis: Long,
)

/**
 * Groups image rows (newest first) into albums keyed by bucket id. The display
 * name is taken from the newest row, so renames apply without changing the
 * key. Only non-empty buckets appear; albums sort by name for a stable list.
 */
fun aggregateAlbums(
    rows: List<MediaImageRow>,
    contentUriFor: (Long) -> String,
): List<MediaAlbum> {
    val byBucket = LinkedHashMap<String, MutableList<MediaImageRow>>()
    for (row in rows) {
        byBucket.getOrPut(row.bucketId) { ArrayList() } += row
    }
    return byBucket.values.mapNotNull { bucket ->
        if (bucket.isEmpty()) return@mapNotNull null
        val newest = bucket.maxByOrNull { it.dateTakenMillis } ?: return@mapNotNull null
        MediaAlbum(
            album = AlbumRef(bucketId = bucket.first().bucketId, name = newest.bucketName),
            photoCount = bucket.size,
            coverContentUri = contentUriFor(newest.imageId),
        )
    }.sortedBy { it.album.name }
}

/** The photos of one bucket, newest first. */
fun photosForAlbum(
    rows: List<MediaImageRow>,
    bucketId: String,
    contentUriFor: (Long) -> String,
): List<AlbumPhoto> =
    rows.filter { it.bucketId == bucketId }
        .sortedByDescending { it.dateTakenMillis }
        .map { AlbumPhoto(contentUri = contentUriFor(it.imageId), dateTakenMillis = it.dateTakenMillis) }
