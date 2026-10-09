package me.foxtails.lakehead.platform.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAlbumAggregationTest {
    private val uriFor: (Long) -> String = { "content://media/external/images/media/$it" }

    private fun row(id: Long, bucket: String, name: String, taken: Long) =
        MediaImageRow(imageId = id, bucketId = bucket, bucketName = name, dateTakenMillis = taken)

    @Test
    fun rowsGroupIntoAlbumsWithCountAndNewestCover() {
        val rows = listOf(
            row(3, "b1", "Camera", 3000),
            row(1, "b1", "Camera", 1000),
            row(2, "b2", "Screenshots", 2000),
        )

        val albums = aggregateAlbums(rows, uriFor)

        assertEquals(2, albums.size)
        val camera = albums.first { it.album.bucketId == "b1" }
        assertEquals("Camera", camera.album.name)
        assertEquals(2, camera.photoCount)
        assertEquals(uriFor(3), camera.coverContentUri)
        val shots = albums.first { it.album.bucketId == "b2" }
        assertEquals(1, shots.photoCount)
        assertEquals(uriFor(2), shots.coverContentUri)
    }

    @Test
    fun albumNameComesFromTheNewestRow() {
        val rows = listOf(
            row(2, "b1", "Renamed", 2000),
            row(1, "b1", "Camera", 1000),
        )

        val albums = aggregateAlbums(rows, uriFor)

        assertEquals(1, albums.size)
        assertEquals("Renamed", albums.single().album.name)
        assertEquals("b1", albums.single().album.bucketId)
    }

    @Test
    fun albumsSortByName() {
        val rows = listOf(
            row(1, "b1", "Zebra", 1000),
            row(2, "b2", "Apple", 2000),
        )

        val names = aggregateAlbums(rows, uriFor).map { it.album.name }

        assertEquals(listOf("Apple", "Zebra"), names)
    }

    @Test
    fun emptyRowsYieldNoAlbums() {
        assertTrue(aggregateAlbums(emptyList(), uriFor).isEmpty())
    }

    @Test
    fun photosForAlbumAreNewestFirst() {
        val rows = listOf(
            row(1, "b1", "Camera", 1000),
            row(3, "b1", "Camera", 3000),
            row(9, "b2", "Other", 9000),
            row(2, "b1", "Camera", 2000),
        )

        val photos = photosForAlbum(rows, "b1", uriFor)

        assertEquals(listOf(uriFor(3), uriFor(2), uriFor(1)), photos.map { it.contentUri })
        assertEquals(listOf(3000L, 2000L, 1000L), photos.map { it.dateTakenMillis })
    }

    @Test
    fun photosForUnknownBucketAreEmpty() {
        val rows = listOf(row(1, "b1", "Camera", 1000))

        assertTrue(photosForAlbum(rows, "missing", uriFor).isEmpty())
    }
}
