package me.foxtails.lakehead.data.database

import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.HomeItemType
import me.foxtails.lakehead.core.model.HomePlacement
import me.foxtails.lakehead.core.model.WidgetPlacement
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.core.model.TileAlbumBinding
import me.foxtails.lakehead.core.model.TileBinding
import org.json.JSONArray
import org.json.JSONObject

fun AppIdentity.toEntity() = AppReferenceEntity(
    profileId.value,
    component.packageName,
    component.className,
)

fun AppReferenceEntity.toDomain() = AppIdentity(
    profileId = ProfileId(profileId),
    component = ComponentIdentity(packageName, className),
)

/** The app or widget-provider reference a placement row points at. */
fun HomePlacement.referenceIdentity(): AppIdentity = when (this) {
    is AppPlacement -> appIdentity
    is WidgetPlacement -> provider
}

fun HomePlacement.toEntity(): PlacementEntity {
    val reference = referenceIdentity()
    return PlacementEntity(
        itemId.value,
        homeId.value,
        type.name,
        reference.profileId.value,
        reference.component.packageName,
        reference.component.className,
        position.column,
        position.row,
        span.columns,
        span.rows,
        availability.name,
    )
}

fun WidgetPlacement.toBindingEntity() = WidgetBindingEntity(itemId.value, appWidgetId)

/**
 * Reject schema/domain boundary values unknown to this slice rather than reinterpret them.
 * A widget row needs its [widgetBinding]; a missing binding is a corrupt row, not an app.
 */
fun PlacementEntity.toDomain(widgetBinding: WidgetBindingEntity? = null): HomePlacement {
    val reference = AppIdentity(
        profileId = ProfileId(profileId),
        component = ComponentIdentity(packageName, className),
    )
    return when (itemType) {
        HomeItemType.APP.name -> AppPlacement(
            homeId = HomeId(homeId),
            itemId = ItemId(itemId),
            appIdentity = reference,
            position = LogicalPosition(column, row),
            span = LogicalSpan(columnSpan, rowSpan),
            availability = ItemAvailability.valueOf(availability),
        )
        HomeItemType.WIDGET.name -> WidgetPlacement(
            homeId = HomeId(homeId),
            itemId = ItemId(itemId),
            provider = reference,
            appWidgetId = requireNotNull(widgetBinding) { "Widget placement $itemId has no binding" }
                .appWidgetId,
            position = LogicalPosition(column, row),
            span = LogicalSpan(columnSpan, rowSpan),
            availability = ItemAvailability.valueOf(availability),
        )
        else -> throw IllegalArgumentException("Unsupported placement type: $itemType")
    }
}

// --- ShortcutBinding ↔ ShortcutBindingEntity ---

fun TileBinding.toEntity() = ShortcutBindingEntity(
    tileId.value,
    JSONArray().apply {
        pinnedShortcuts.forEach { ref ->
            put(JSONObject().apply {
                put("packageName", ref.packageName)
                put("profileId", ref.profileId.value)
                put("shortcutId", ref.shortcutId)
            })
        }
    }.toString(),
)

fun ShortcutBindingEntity.toDomain() = TileBinding(
    tileId = ItemId(tileId),
    pinnedShortcuts = run {
        val array = JSONArray(pinnedShortcutIds)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ShortcutRef(
                packageName = obj.getString("packageName"),
                profileId = ProfileId(obj.getString("profileId")),
                shortcutId = obj.getString("shortcutId"),
            )
        }
    },
)

// --- TileAlbumBinding ↔ AlbumBindingEntity ---

fun TileAlbumBinding.toEntity() = AlbumBindingEntity(
    tileId.value,
    album.bucketId,
    album.name,
)

fun AlbumBindingEntity.toDomain() = TileAlbumBinding(
    tileId = ItemId(tileId),
    album = AlbumRef(bucketId = bucketId, name = albumName),
)
