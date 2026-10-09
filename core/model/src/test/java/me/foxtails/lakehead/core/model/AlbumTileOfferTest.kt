package me.foxtails.lakehead.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTileOfferTest {

    private fun offer(
        packageName: String = "com.sec.android.gallery3d",
        isWidget: Boolean = false,
        isPersonalProfile: Boolean = true,
        hasAlbumBinding: Boolean = false,
        old: LogicalSpan = LogicalSpan(1, 1),
        new: LogicalSpan = LogicalSpan(2, 2),
    ) = shouldOfferAlbum(packageName, isWidget, isPersonalProfile, hasAlbumBinding, old, new)

    @Test
    fun galleryPackagesWithAGallery3dSegmentMatch() {
        assertTrue(isGalleryPackage("com.android.gallery3d"))
        assertTrue(isGalleryPackage("com.sec.android.gallery3d"))
        assertTrue(isGalleryPackage("com.coloros.gallery3d"))
        assertTrue(isGalleryPackage("gallery3d"))
    }

    @Test
    fun namesWithoutAWholeGallery3dSegmentDoNotMatch() {
        assertFalse(isGalleryPackage("com.google.android.apps.photos"))
        assertFalse(isGalleryPackage("com.example.mygallery3dpro"))
        assertFalse(isGalleryPackage("com.example.gallery3d2"))
        assertFalse(isGalleryPackage("com.example.Gallery3D"))
        assertFalse(isGalleryPackage("com.example.gallery"))
        assertFalse(isGalleryPackage(""))
    }

    @Test
    fun albumSpanNeedsTwoColumnsAndTwoRows() {
        assertTrue(isAlbumTileSpan(LogicalSpan(2, 2)))
        assertTrue(isAlbumTileSpan(LogicalSpan(4, 2)))
        assertTrue(isAlbumTileSpan(LogicalSpan(3, 5)))
        assertFalse(isAlbumTileSpan(LogicalSpan(1, 1)))
        assertFalse(isAlbumTileSpan(LogicalSpan(2, 1)))
        assertFalse(isAlbumTileSpan(LogicalSpan(1, 2)))
        assertFalse(isAlbumTileSpan(LogicalSpan(1, 4)))
        assertFalse(isAlbumTileSpan(LogicalSpan(4, 1)))
    }

    @Test
    fun crossingIntoTwoByTwoOffers() {
        assertTrue(offer(old = LogicalSpan(1, 1), new = LogicalSpan(2, 2)))
        assertTrue(offer(old = LogicalSpan(2, 1), new = LogicalSpan(2, 2)))
        assertTrue(offer(old = LogicalSpan(1, 2), new = LogicalSpan(3, 2)))
        assertTrue(offer(old = LogicalSpan(1, 4), new = LogicalSpan(2, 4)))
        assertTrue(offer(old = LogicalSpan(1, 1), new = LogicalSpan(4, 2)))
    }

    @Test
    fun resizeThatStaysBelowTheThresholdDoesNotOffer() {
        assertFalse(offer(old = LogicalSpan(1, 1), new = LogicalSpan(2, 1)))
        assertFalse(offer(old = LogicalSpan(1, 1), new = LogicalSpan(1, 4)))
        assertFalse(offer(old = LogicalSpan(2, 1), new = LogicalSpan(4, 1)))
    }

    @Test
    fun resizeBetweenLargeSizesOrShrinkingDoesNotOffer() {
        assertFalse(offer(old = LogicalSpan(2, 2), new = LogicalSpan(4, 2)))
        assertFalse(offer(old = LogicalSpan(4, 2), new = LogicalSpan(2, 2)))
        assertFalse(offer(old = LogicalSpan(2, 2), new = LogicalSpan(1, 1)))
        assertFalse(offer(old = LogicalSpan(2, 2), new = LogicalSpan(2, 2)))
    }

    @Test
    fun decliningIsNotRememberedSoTheNextCrossingOffersAgain() {
        assertTrue(offer(old = LogicalSpan(1, 1), new = LogicalSpan(2, 2)))
        assertFalse(offer(old = LogicalSpan(2, 2), new = LogicalSpan(1, 1)))
        assertTrue(offer(old = LogicalSpan(1, 1), new = LogicalSpan(2, 2)))
    }

    @Test
    fun alreadyBoundTilesAreNotOffered() {
        assertFalse(offer(hasAlbumBinding = true))
    }

    @Test
    fun widgetTilesAreNotOffered() {
        assertFalse(offer(isWidget = true))
    }

    @Test
    fun workProfileTilesAreNotOffered() {
        assertFalse(offer(isPersonalProfile = false))
    }

    @Test
    fun nonGalleryAppsAreNotOffered() {
        assertFalse(offer(packageName = "com.google.android.apps.photos"))
        assertFalse(offer(packageName = "com.example.notes"))
    }
}
