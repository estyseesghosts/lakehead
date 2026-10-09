package me.foxtails.lakehead.platform.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Granted-path device proof: with full photo access the gateway lists the
 * album holding two inserted test images, returns their photos newest first,
 * loads a thumbnail, and reports the observer firing on insert and delete.
 * Inserted rows are deleted afterwards.
 */
@RunWith(AndroidJUnit4::class)
class MediaAlbumDeviceTest {
    @get:Rule
    val grant: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.READ_MEDIA_IMAGES)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = context.contentResolver
    private val inserted = ArrayList<Uri>()

    @After
    fun cleanUp() {
        inserted.forEach { runCatching { resolver.delete(it, null, null) } }
        inserted.clear()
    }

    @Test
    fun liveAccess_reportsFullWhenGranted(): Unit = runBlocking {
        assertEquals(MediaAccess.FULL, MediaAccessQuery.accessNow(context))
    }

    @Test
    fun albums_listsTheInsertedBucketWithCountAndCover(): Unit = runBlocking {
        val first = insertTestImage("album-first.png", System.currentTimeMillis() - 60_000)
        val second = insertTestImage("album-second.png", System.currentTimeMillis())

        val gateway = MediaStoreGateway(context)
        val album = gateway.albums().first { it.album.name == BUCKET_NAME }

        assertTrue(album.photoCount >= 2)
        assertTrue(album.coverContentUri.isNotBlank())

        val photos = gateway.photos(album.album.bucketId)
        val uris = photos.map { it.contentUri }
        assertTrue(uris.contains(first.toString()))
        assertTrue(uris.contains(second.toString()))
        // Newest first: the later DATE_TAKEN sorts before the earlier one.
        assertTrue(uris.indexOf(second.toString()) < uris.indexOf(first.toString()))
    }

    @Test
    fun thumbnail_loadsADownsampledBitmap(): Unit = runBlocking {
        insertTestImage("album-thumb.png", System.currentTimeMillis())

        val gateway = MediaStoreGateway(context)
        val album = gateway.albums().first { it.album.name == BUCKET_NAME }
        val bitmap = gateway.thumbnail(album.coverContentUri, 96)

        assertNotNull(bitmap)
        assertTrue(bitmap!!.width > 0 && bitmap.height > 0)
        assertTrue(bitmap.width <= 96 && bitmap.height <= 96)
    }

    @Test
    fun changes_fireOnInsertAndDelete(): Unit = runBlocking {
        val gateway = MediaStoreGateway(context)
        gateway.start()
        try {
            val baseline = gateway.changes.value
            insertTestImage("album-observed.png", System.currentTimeMillis())
            withTimeout(10_000) {
                while (gateway.changes.value == baseline) delay(100)
            }
            val afterInsert = gateway.changes.value
            resolver.delete(inserted.removeAt(inserted.lastIndex), null, null)
            withTimeout(10_000) {
                while (gateway.changes.value == afterInsert) delay(100)
            }
        } finally {
            gateway.stop()
        }
    }

    private fun insertTestImage(displayName: String, dateTaken: Long): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$BUCKET_NAME")
            put(MediaStore.Images.Media.DATE_TAKEN, dateTaken)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { out ->
            val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
        }
        inserted += uri
        return uri
    }

    private companion object {
        const val BUCKET_NAME = "ProtoLaunchAlbumTest"
    }
}
