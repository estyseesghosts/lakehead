package me.foxtails.lakehead.core.model

/**
 * A device photo album chosen for a tile slideshow: the `MediaStore` bucket id
 * (stable on this device; the key) and the display name (refreshed from
 * `MediaStore` on listing because renames do not change the id).
 */
data class AlbumRef(
    val bucketId: String,
    val name: String,
) {
    init {
        require(bucketId.isNotBlank()) { "bucketId must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
    }
}

/**
 * Associates a launcher tile with at most one photo album.
 *
 * Identity is driven by [tileId], not by package name — two gallery tiles may
 * show the same album and remain distinct. Absence of a row means no album is
 * bound; only bucket id and name are persisted, never URIs or bitmaps.
 */
data class TileAlbumBinding(
    val tileId: ItemId,
    val album: AlbumRef,
)
