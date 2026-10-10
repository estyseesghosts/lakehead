package me.foxtails.lakehead

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.designsystem.TileNotificationPreview
import me.foxtails.lakehead.core.designsystem.TileNotifications
import me.foxtails.lakehead.core.model.AppNotificationSummary
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.NotificationSourceKey
import me.foxtails.lakehead.feature.home.HomeRenderState
import me.foxtails.lakehead.feature.home.HomeRenderTile
import me.foxtails.lakehead.platform.notifications.NotificationPhotoCache
import me.foxtails.lakehead.platform.notifications.NotificationSummaryStore

/**
 * Joins notification summaries to the app tiles they belong to. A tile matches on
 * package and profile; widget tiles and apps without notifications get no entry.
 */
internal fun joinNotificationsToTiles(
    tiles: List<HomeRenderTile>,
    summaries: Map<NotificationSourceKey, AppNotificationSummary>,
    photos: Map<String, ImageBitmap>,
): Map<ItemId, TileNotifications> {
    if (summaries.isEmpty()) return emptyMap()
    val joined = HashMap<ItemId, TileNotifications>()
    for (tile in tiles) {
        if (tile.isWidget || tile.isFolder) continue
        val identity = tile.requireAppIdentity
        val key = NotificationSourceKey(identity.component.packageName, identity.profileId)
        val summary = summaries[key]?.takeIf { it.count > 0 } ?: continue
        joined[tile.itemId] = TileNotifications(
            count = summary.count,
            previews = summary.previews.map {
                TileNotificationPreview(it.name, it.body, it.photoKey?.let(photos::get))
            },
        )
    }
    return joined
}

/** Collects the shared summaries, decodes their photos off the main thread and joins them to [homeState]. */
@Composable
internal fun rememberNotificationsByTile(
    homeState: HomeRenderState,
    extraTiles: List<HomeRenderTile> = emptyList(),
): Map<ItemId, TileNotifications> {
    val context = LocalContext.current
    val summaries by NotificationSummaryStore.shared.summaries.collectAsState()
    var photos by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    val photoKeys = remember(summaries) {
        summaries.values.flatMap { it.previews }.mapNotNull { it.photoKey }.toSet()
    }
    LaunchedEffect(photoKeys) {
        val known = photos
        photos = withContext(Dispatchers.IO) {
            val cache = NotificationPhotoCache.shared(context)
            buildMap {
                for (key in photoKeys) {
                    val bitmap = known[key] ?: cache.read(key)?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                    if (bitmap != null) put(key, bitmap)
                }
            }
        }
    }
    val tiles = (homeState as? HomeRenderState.Loaded)?.tiles.orEmpty() + extraTiles
    return remember(tiles, summaries, photos) { joinNotificationsToTiles(tiles, summaries, photos) }
}
