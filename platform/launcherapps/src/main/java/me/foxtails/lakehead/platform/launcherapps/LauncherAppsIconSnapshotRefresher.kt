package me.foxtails.lakehead.platform.launcherapps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.UserManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
    /** Resolves icons for the supplied catalogue without modifying its entries or labels. */
    suspend fun refresh(entries: List<LauncherAppEntry>) {
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

/** Small Android adapter; callers invoke it through the refresher's IO-dispatched API. */
class LauncherAppsIconActivityLookup(context: Context) : LauncherAppIconLookup {
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

/** Rasterizes to a fixed-size PNG; encoded output is rejected above the snapshot's byte bound. */
class BoundedLauncherAppIconPngEncoder(
    private val sizePx: Int = DEFAULT_SIZE_PX,
) : LauncherAppIconPngEncoder {
    init {
        require(sizePx in 1..MAX_SIZE_PX) { "sizePx must be between 1 and $MAX_SIZE_PX" }
    }

    override fun encode(icon: LauncherAppIcon): ByteArray? {
        val drawable = (icon as? DrawableLauncherAppIconAccess)?.drawable ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            val oldBounds = drawable.bounds
            drawable.setBounds(0, 0, sizePx, sizePx)
            try {
                drawable.draw(canvas)
            } finally {
                drawable.bounds = oldBounds
            }
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
                output.toByteArray().takeIf { it.size <= AppIconSnapshot.MAX_PNG_BYTES }
            }
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        const val DEFAULT_SIZE_PX: Int = 96
        const val MAX_SIZE_PX: Int = 512
    }
}

// Kept private to the platform implementation so no Drawable crosses the resolver's boundary.
private interface DrawableLauncherAppIconAccess : LauncherAppIcon {
    val drawable: Drawable
}

private class DrawableLauncherAppIcon(
    override val drawable: Drawable,
) : DrawableLauncherAppIconAccess
