package me.foxtails.lakehead.platform.launcherapps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.UserManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.LauncherAppEntry
import java.io.ByteArrayOutputStream

/** Marker for an icon obtained by a platform activity lookup. */
interface LauncherAppIcon

/** Finds the launchable activity's icon for its complete profile-aware identity. */
fun interface LauncherAppIconLookup {
    fun findIcon(identity: AppIdentity): LauncherAppIcon?
}

/** Encodes a platform icon as a bounded PNG, or returns null when it cannot be encoded. */
fun interface LauncherAppIconPngEncoder {
    fun encode(icon: LauncherAppIcon): ByteArray?
}

/**
 * Refreshes the shared icon cache from one complete catalogue snapshot. Missing activities and
 * icons that fail lookup/encoding are omitted; replacement consequently removes stale and failed
 * identities as well as identities no longer present in [entries].
 *
 * Both lookup (including Android activity/icon access) and encoding execute on [ioDispatcher].
 */
class LauncherAppsIconSnapshotRefresher(
    private val lookup: LauncherAppIconLookup,
    private val encoder: LauncherAppIconPngEncoder,
    private val cache: AppIconSnapshotCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val refreshMutex = Mutex()

    /** Resolves icons for the supplied catalogue without modifying its entries or labels. */
    suspend fun refresh(entries: List<LauncherAppEntry>) {
        // One refresher is shared by Home and All Apps; serialise so a slower, older pass
        // cannot overwrite a newer one.
        refreshMutex.withLock { refreshLocked(entries) }
    }

    private suspend fun refreshLocked(entries: List<LauncherAppEntry>) {
        withContext(ioDispatcher) {
            val snapshots = LinkedHashMap<AppIdentity, AppIconSnapshot>()
            val visited = HashSet<AppIdentity>()
            entries.forEach { entry ->
                val identity = entry.identity
                if (!visited.add(identity)) return@forEach

                val png = try {
                    lookup.findIcon(identity)?.let(encoder::encode)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // One bad/vanished activity must not make the remaining catalogue unusable.
                    null
                }
                if (png != null && png.isNotEmpty() && png.size <= AppIconSnapshot.MAX_PNG_BYTES) {
                    snapshots[identity] = AppIconSnapshot(identity, png)
                }
            }
            cache.replaceAll(snapshots.values)
        }
    }
}

/**
 * Small Android adapter; callers invoke it through the refresher's IO-dispatched API.
 *
 * @param sourceResolver classifies raw icon drawables into the best symbol source
 *   (monochrome / adaptive foreground / legacy). Defaults to a production instance.
 */
class LauncherAppsIconActivityLookup(
    context: Context,
    private val sourceResolver: AppSymbolSourceResolver = AppSymbolSourceResolver(),
) : LauncherAppIconLookup {
    private val launcherApps: LauncherApps
    private val profileTokens: ProfileTokenAdapter

    init {
        val applicationContext = requireNotNull(context.applicationContext)
        launcherApps = requireNotNull(
            applicationContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps,
        )
        val userManager = requireNotNull(
            applicationContext.getSystemService(Context.USER_SERVICE) as? UserManager,
        )
        profileTokens = ProfileTokenAdapter(launcherApps, userManager)
    }

    override fun findIcon(identity: AppIdentity): LauncherAppIcon? {
        val user = profileTokens.userFor(identity.profileId) ?: return null
        val component = ComponentName(identity.component.packageName, identity.component.className)
        return try {
            launcherApps.getActivityList(identity.component.packageName, user)
                .firstOrNull { it.componentName == component }
                ?.getBadgedIcon(0)
                ?.let { sourceResolver.resolve(it).drawable }
                ?.let(::DrawableLauncherAppIcon)
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }
}

/**
 * Rasterizes a symbol-source drawable into a normalised, square, padded PNG.
 *
 * Normalisation (per `docs/grid.md` §7) ensures every app symbol occupies the same
 * visual box regardless of source type (monochrome / adaptive foreground / legacy):
 *
 *  1. Render the drawable at its intrinsic size.
 *  2. Find the tight bounding box of non-transparent pixels (trim transparent insets).
 *  3. Scale the content to fit inside a centred padded square ([NORMALISED_PADDING]
 *     fraction on each side) so mono/foreground/legacy produce identical visual boxes.
 *  4. Encode as PNG — raw bytes only, no tint baked in. Draw-time tinting with the
 *     theme `onSurface` colour is handled by `AppSymbol` in `core:designsystem`.
 *
 * Encoded output is rejected above [AppIconSnapshot.MAX_PNG_BYTES].
 */
class BoundedLauncherAppIconPngEncoder(
    private val sizePx: Int = DEFAULT_SIZE_PX,
) : LauncherAppIconPngEncoder {
    init {
        require(sizePx in 1..MAX_SIZE_PX) { "sizePx must be between 1 and $MAX_SIZE_PX" }
    }

    override fun encode(icon: LauncherAppIcon): ByteArray? {
        val drawable = (icon as? DrawableLauncherAppIconAccess)?.drawable ?: return null

        // 1. Render drawable at its intrinsic size to discover content bounds.
        val srcW = drawable.intrinsicWidth.coerceAtLeast(1)
        val srcH = drawable.intrinsicHeight.coerceAtLeast(1)
        val srcBitmap = Bitmap.createBitmap(srcW, srcH, Bitmap.Config.ARGB_8888)
        val oldBounds = drawable.bounds
        drawable.setBounds(0, 0, srcW, srcH)
        try {
            drawable.draw(Canvas(srcBitmap))
        } finally {
            drawable.bounds = oldBounds
        }

        // 2. Scan for the tight content bounding box (trim transparent insets).
        val pixels = IntArray(srcW * srcH)
        srcBitmap.getPixels(pixels, 0, srcW, 0, 0, srcW, srcH)
        var minX = srcW; var minY = srcH; var maxX = -1; var maxY = -1
        for (i in pixels.indices) {
            if (pixels[i] ushr 24 > 0) {
                val x = i % srcW
                val y = i / srcW
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) { srcBitmap.recycle(); return null }

        // 3. Scale trimmed content into a centred padded square.
        val contentW = (maxX - minX + 1).toFloat()
        val contentH = (maxY - minY + 1).toFloat()
        val paddedTarget = sizePx * (1f - 2f * NORMALISED_PADDING)
        val scale = minOf(paddedTarget / contentW, paddedTarget / contentH)
        val drawW = contentW * scale
        val drawH = contentH * scale
        val drawX = (sizePx - drawW) / 2f
        val drawY = (sizePx - drawH) / 2f

        val dstBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        Canvas(dstBitmap).drawBitmap(
            srcBitmap,
            Rect(minX, minY, maxX + 1, maxY + 1),
            RectF(drawX, drawY, drawX + drawW, drawY + drawH),
            paint,
        )
        srcBitmap.recycle()

        // 4. Encode normalised PNG (raw, no tint baked in).
        return try {
            ByteArrayOutputStream().use { output ->
                if (!dstBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
                output.toByteArray().takeIf { it.size <= AppIconSnapshot.MAX_PNG_BYTES }
            }
        } finally {
            dstBitmap.recycle()
        }
    }

    companion object {
        const val DEFAULT_SIZE_PX: Int = 96
        const val MAX_SIZE_PX: Int = 512

        /** Fraction of the output box reserved for padding on each side (content = 75%). */
        private const val NORMALISED_PADDING = 0.125f
    }
}

// Kept private to the platform implementation so no Drawable crosses the resolver's boundary.
private interface DrawableLauncherAppIconAccess : LauncherAppIcon {
    val drawable: Drawable
}

private class DrawableLauncherAppIcon(
    override val drawable: Drawable,
) : DrawableLauncherAppIconAccess
