package me.foxtails.lakehead.platform.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Photo-library access for the album list: full, Android 14+ selected-photos, or none. */
enum class MediaAccess {
    FULL,
    PARTIAL,
    DENIED,
}

/**
 * Photo permission state. Permissions are only ever requested after the user
 * accepts the album prompt on a gallery tile; nothing here prompts by itself.
 */
object MediaAccessQuery {
    /**
     * The permissions to request on [sdkInt]: `READ_MEDIA_IMAGES` on API 33+,
     * `READ_EXTERNAL_STORAGE` below. `READ_MEDIA_VISUAL_USER_SELECTED` is
     * never requested directly; the system offers it alongside the images
     * permission on API 34+.
     */
    fun permissionsFor(sdkInt: Int): List<String> =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /**
     * Pure access decision from the set of granted permissions on [sdkInt].
     * On API 34+ an images grant means full access and a user-selected grant
     * alone means a partial album; below 34 only the full grant counts.
     */
    fun accessFrom(granted: Set<String>, sdkInt: Int): MediaAccess = when {
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> when {
            Manifest.permission.READ_MEDIA_IMAGES in granted -> MediaAccess.FULL
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED in granted -> MediaAccess.PARTIAL
            else -> MediaAccess.DENIED
        }
        sdkInt >= Build.VERSION_CODES.TIRAMISU ->
            if (Manifest.permission.READ_MEDIA_IMAGES in granted) MediaAccess.FULL else MediaAccess.DENIED
        else ->
            if (Manifest.permission.READ_EXTERNAL_STORAGE in granted) MediaAccess.FULL else MediaAccess.DENIED
    }

    /** The live access state; safe to call on any API level. */
    fun accessNow(context: Context): MediaAccess {
        val granted = listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        ).filter { isGranted(context, it) }.toSet()
        return accessFrom(granted, Build.VERSION.SDK_INT)
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
