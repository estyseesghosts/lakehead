package me.foxtails.lakehead.platform.media

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.model.AlbumPhoto
import me.foxtails.lakehead.core.model.MediaAlbum

/**
 * `MediaStore` image gateway behind the album feature.
 *
 * Lists albums (name, cover, count) and the newest-first photos of one album,
 * loads downsampled thumbnails off the main thread, and raises [changes] on
 * every image-library change so callers can invalidate. With [MediaAccess.DENIED]
 * every query returns empty and thumbnails return null; the tile then renders
 * as a normal app tile. Reads the personal profile only.
 */
class MediaStoreGateway(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val queryLock = Mutex()

    /** Incremented on every observed image-library change while started. */
    private val mutableChanges = MutableStateFlow(0L)
    val changes: StateFlow<Long> = mutableChanges.asStateFlow()
    private var observer: ContentObserver? = null

    /** Starts the change observer; safe to call twice. */
    fun start() {
        if (observer != null) return
        val callback = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                mutableChanges.value++
            }
        }
        observer = callback
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, callback)
    }

    fun stop() {
        observer?.let(resolver::unregisterContentObserver)
        observer = null
    }

    /** Albums with at least one visible photo, or empty when access is denied. */
    suspend fun albums(): List<MediaAlbum> {
        if (MediaAccessQuery.accessNow(appContext) == MediaAccess.DENIED) return emptyList()
        return aggregateAlbums(queryRows(null, null), ::contentUriFor)
    }

    /** Newest-first photos of one bucket, or empty when access is denied. */
    suspend fun photos(bucketId: String, limit: Int = Int.MAX_VALUE): List<AlbumPhoto> {
        require(limit >= 0) { "limit must be non-negative" }
        if (MediaAccessQuery.accessNow(appContext) == MediaAccess.DENIED) return emptyList()
        val rows = queryRows("${MediaStore.Images.Media.BUCKET_ID} = ?", arrayOf(bucketId))
        return photosForAlbum(rows, bucketId, ::contentUriFor).take(limit)
    }

    /**
     * One downsampled thumbnail for [contentUri]. Call off the main thread;
     * null when access is denied, the photo is unreadable, or loading fails.
     */
    suspend fun thumbnail(contentUri: String, sizePx: Int): Bitmap? {
        require(sizePx > 0) { "sizePx must be positive" }
        if (MediaAccessQuery.accessNow(appContext) == MediaAccess.DENIED) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                resolver.loadThumbnail(Uri.parse(contentUri), Size(sizePx, sizePx), null)
            }.getOrNull()
        }
    }

    private suspend fun queryRows(selection: String?, args: Array<String>?): List<MediaImageRow> =
        queryLock.withLock {
            withContext(Dispatchers.IO) {
                val rows = ArrayList<MediaImageRow>()
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                )
                val sort = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC"
                } else {
                    "${MediaStore.Images.Media.DATE_TAKEN} DESC"
                }
                runCatching {
                    resolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection, selection, args, sort,
                    )?.use { cursor ->
                        val id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val bucket = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                        val name = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                        val taken = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                        while (cursor.moveToNext()) {
                            val bucketId = cursor.getString(bucket) ?: continue
                            // A null bucket name means the row carries no displayable
                            // album; it is skipped rather than shown as blank.
                            val bucketName = cursor.getString(name) ?: continue
                            rows += MediaImageRow(
                                imageId = cursor.getLong(id),
                                bucketId = bucketId,
                                bucketName = bucketName,
                                dateTakenMillis = cursor.getLong(taken),
                            )
                        }
                    }
                }
                rows
            }
        }

    private fun contentUriFor(imageId: Long): String =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId).toString()
}
