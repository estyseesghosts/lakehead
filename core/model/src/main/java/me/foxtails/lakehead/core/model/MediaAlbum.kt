package me.foxtails.lakehead.core.model

/**
 * One device photo album as shown in the album picker and slideshow.
 *
 * [album] is the stable key (bucket id) plus the display name; [photoCount]
 * is the number of photos currently visible to the launcher, and
 * [coverContentUri] addresses the newest photo. Only non-empty albums are
 * listed, so [photoCount] is always positive and the cover always exists.
 */
data class MediaAlbum(
    val album: AlbumRef,
    val photoCount: Int,
    val coverContentUri: String,
) {
    init {
        require(photoCount > 0) { "photoCount must be positive" }
        require(coverContentUri.isNotBlank()) { "coverContentUri must not be blank" }
    }
}

/** One photo in an album, newest first. [contentUri] is the only photo handle carried. */
data class AlbumPhoto(
    val contentUri: String,
    val dateTakenMillis: Long,
) {
    init {
        require(contentUri.isNotBlank()) { "contentUri must not be blank" }
    }
}
