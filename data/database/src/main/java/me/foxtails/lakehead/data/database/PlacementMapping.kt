package me.foxtails.lakehead.data.database

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.HomeItemType
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
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

fun AppPlacement.toEntity() = PlacementEntity(
    itemId.value,
    homeId.value,
    type.name,
    appIdentity.profileId.value,
    appIdentity.component.packageName,
    appIdentity.component.className,
    position.column,
    position.row,
    span.columns,
    span.rows,
    availability.name,
)

/** Reject schema/domain boundary values unknown to this slice rather than reinterpret them. */
fun PlacementEntity.toDomain() = AppPlacement(
    homeId = HomeId(homeId),
    itemId = ItemId(itemId),
    appIdentity = AppIdentity(
        profileId = ProfileId(profileId),
        component = ComponentIdentity(packageName, className),
    ),
    position = LogicalPosition(column, row),
    span = LogicalSpan(columnSpan, rowSpan),
    availability = ItemAvailability.valueOf(availability),
).also {
    require(itemType == HomeItemType.APP.name) { "Unsupported placement type: $itemType" }
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
