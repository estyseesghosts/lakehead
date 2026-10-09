package me.foxtails.lakehead.platform.notifications

import android.content.Context
import java.io.File

/**
 * Bounded on-disk cache of small PNG photos extracted from notifications, held in
 * the app cache directory (the system may clear it). Keys come from [photoKeyFor].
 * Safe to call from any background thread.
 */
class NotificationPhotoCache(context: Context, private val maxFiles: Int = MAX_FILES) {
    private val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    fun write(key: String, png: ByteArray) {
        File(directory, "$key.png").writeBytes(png)
    }

    fun read(key: String): ByteArray? {
        if (!key.all { it.isLetterOrDigit() }) return null
        return File(directory, "$key.png").takeIf { it.isFile }?.readBytes()
    }

    /** Deletes files no tracked notification references and enforces the size bound. */
    fun prune(referenced: Set<String>) {
        val cached = directory.listFiles { file -> file.extension == "png" }.orEmpty()
            .map { CachedPhoto(it.nameWithoutExtension, it.lastModified()) }
        photosToDelete(cached, referenced, maxFiles).forEach { File(directory, "$it.png").delete() }
    }

    fun clear() {
        directory.listFiles().orEmpty().forEach { it.delete() }
    }

    companion object {
        const val MAX_FILES = 64
        private const val DIRECTORY = "notification-photos"

        /** The process-wide cache the listener writes and the app reads. */
        @Volatile
        private var shared: NotificationPhotoCache? = null

        fun shared(context: Context): NotificationPhotoCache =
            shared ?: synchronized(this) {
                shared ?: NotificationPhotoCache(context.applicationContext).also { shared = it }
            }
    }
}
