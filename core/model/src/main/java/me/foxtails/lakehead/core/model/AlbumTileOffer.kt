package me.foxtails.lakehead.core.model

/**
 * True when [packageName] has a dot-separated segment equal to `gallery3d`
 * (`com.android.gallery3d`, `com.sec.android.gallery3d`, `com.coloros.gallery3d`).
 * Substring matches such as `com.example.mygallery3dpro` do not count.
 */
fun isGalleryPackage(packageName: String): Boolean =
    packageName.split('.').any { it == "gallery3d" }

/** `true` when [span] is at least 2 columns and 2 rows, the size at which an album slideshow shows. */
fun isAlbumTileSpan(span: LogicalSpan): Boolean = span.columns >= 2 && span.rows >= 2

/**
 * Whether an accepted resize should offer an album slideshow.
 *
 * The offer is made only when a gallery app tile in the personal profile, with no album bound,
 * grows from below [isAlbumTileSpan] to at or above it. Resizing between two large sizes does not
 * re-offer, and declining is not remembered, so the next crossing offers again. Widget tiles and
 * work-profile tiles never get the offer.
 */
fun shouldOfferAlbum(
    packageName: String,
    isWidget: Boolean,
    isPersonalProfile: Boolean,
    hasAlbumBinding: Boolean,
    oldSpan: LogicalSpan,
    newSpan: LogicalSpan,
): Boolean =
    !isWidget &&
        isPersonalProfile &&
        !hasAlbumBinding &&
        isGalleryPackage(packageName) &&
        !isAlbumTileSpan(oldSpan) &&
        isAlbumTileSpan(newSpan)
