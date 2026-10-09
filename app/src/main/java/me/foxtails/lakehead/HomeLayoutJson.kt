package me.foxtails.lakehead

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.core.model.TileAlbumBinding
import me.foxtails.lakehead.core.model.TileBinding
import org.json.JSONArray
import org.json.JSONObject

/**
 * Typed reason for a JSON codec rejection, enabling callers to present
 * precise diagnostics without parsing exception messages.
 */
sealed interface RejectedReason {
    data object UnknownVersion : RejectedReason
    data object UnknownKind : RejectedReason
    data object UnknownItemType : RejectedReason
    data object MissingField : RejectedReason
    data object InvalidSpan : RejectedReason
    data object NoLegalPosition : RejectedReason
}

/** Exception thrown by the JSON codec when a document fails validation. */
class HomeLayoutJsonException(
    val reason: RejectedReason,
    message: String,
) : IllegalArgumentException(message)

/** Deserialized intermediate representation of one home layout item. */
data class HomeLayoutJsonItem(
    val id: String,
    val type: String,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int,
    val app: HomeLayoutJsonAppRef,
    val pinnedShortcuts: List<HomeLayoutJsonShortcutRef>,
    val album: HomeLayoutJsonAlbumRef? = null,
)

/** Profile-aware app reference inside a JSON item. */
data class HomeLayoutJsonAppRef(
    val profileId: String,
    val packageName: String,
    val className: String,
)

/** Pinned shortcut reference inside a JSON item. */
data class HomeLayoutJsonShortcutRef(
    val packageName: String,
    val profileId: String,
    val shortcutId: String,
)

/**
 * Album slideshow binding inside a JSON item (decision 7).
 *
 * The binding is an optional export field carrying only the stable bucket id
 * and the display name — never URIs or bitmaps. Absent when the tile has no
 * binding; on import it is dropped when the album is not on the device.
 */
data class HomeLayoutJsonAlbumRef(
    val bucketId: String,
    val name: String,
)

/** Top-level deserialized document. */
data class HomeLayoutJsonDocument(
    val homeId: HomeId,
    val columns: Int,
    val items: List<HomeLayoutJsonItem>,
)

/** Pure JSON codec for the home layout export/import format. Uses `org.json` only. */
object HomeLayoutCodec {
    const val HOME_LAYOUT_KIND = "proto-launch.home-layout/v1"
    const val HOME_LAYOUT_VERSION = 1

    /**
     * Encodes the current home layout into a JSON document.
     *
     * @param homeId the home identity to record in the document.
     * @param columns the column bound used for this home.
     * @param placements current placements loaded from the repository.
     * @param bindingsByTile per-tile shortcut bindings, keyed by [ItemId].
     * @param albumBindingsByTile per-tile album slideshow bindings, keyed by
     *   [ItemId]; present bindings are encoded as the optional `album` field.
     * @return a complete [JSONObject] conforming to the schema.
     */
    fun encodeLayout(
        homeId: HomeId,
        columns: Int,
        placements: List<AppPlacement>,
        bindingsByTile: Map<ItemId, TileBinding>,
        albumBindingsByTile: Map<ItemId, TileAlbumBinding> = emptyMap(),
    ): JSONObject {
        val items = JSONArray()
        for (placement in placements) {
            val binding = bindingsByTile[placement.itemId]
            val shortcuts = JSONArray()
            binding?.pinnedShortcuts?.forEach { ref ->
                shortcuts.put(JSONObject().apply {
                    put("packageName", ref.packageName)
                    put("profileId", ref.profileId.value)
                    put("shortcutId", ref.shortcutId)
                })
            }
            val albumBinding = albumBindingsByTile[placement.itemId]
            items.put(JSONObject().apply {
                put("id", placement.itemId.value)
                put("type", placement.type.name)
                put("column", placement.position.column)
                put("row", placement.position.row)
                put("columns", placement.span.columns)
                put("rows", placement.span.rows)
                put("app", JSONObject().apply {
                    put("profileId", placement.appIdentity.profileId.value)
                    put("packageName", placement.appIdentity.component.packageName)
                    put("className", placement.appIdentity.component.className)
                })
                put("pinnedShortcuts", shortcuts)
                if (albumBinding != null) {
                    put("album", JSONObject().apply {
                        put("bucketId", albumBinding.album.bucketId)
                        put("name", albumBinding.album.name)
                    })
                }
            })
        }

        return JSONObject().apply {
            put("version", HOME_LAYOUT_VERSION)
            put("kind", HOME_LAYOUT_KIND)
            put("exportedAtMillis", System.currentTimeMillis())
            put("home", JSONObject().apply {
                put("id", homeId.value)
                put("columns", columns)
                put("items", items)
            })
        }
    }

    /**
     * Decodes a JSON document into a [HomeLayoutJsonDocument].
     *
     * Validates `version`, `kind`, and every required field. Throws
     * [HomeLayoutJsonException] with a typed [RejectedReason] on failure.
     */
    fun decodeLayout(json: JSONObject): HomeLayoutJsonDocument {
        val version = json.optInt("version", -1)
        if (version != HOME_LAYOUT_VERSION) {
            throw HomeLayoutJsonException(
                RejectedReason.UnknownVersion,
                "Expected version $HOME_LAYOUT_VERSION but got $version",
            )
        }

        val kind = json.optString("kind", "")
        if (!kind.startsWith("proto-launch.home-layout/")) {
            throw HomeLayoutJsonException(
                RejectedReason.UnknownKind,
                "Expected kind starting with 'proto-launch.home-layout/' but got '$kind'",
            )
        }

        val home = requireField(json, "home") { it.optJSONObject("home") }
        val homeIdStr = requireField(home, "id") { it.optString("id", "").ifBlank { null } }
        val homeColumns = requireField(home, "columns") { v ->
            val c = v.optInt("columns", -1)
            if (c > 0) c else null
        }
        val itemsArray = requireField(home, "items") { it.optJSONArray("items") }

        val items = (0 until itemsArray.length()).map { i ->
            decodeItem(itemsArray.getJSONObject(i))
        }

        return HomeLayoutJsonDocument(
            homeId = HomeId(homeIdStr),
            columns = homeColumns,
            items = items,
        )
    }

    private fun decodeItem(obj: JSONObject): HomeLayoutJsonItem {
        val id = requireField(obj, "id") { it.optString("id", "").ifBlank { null } }
        val type = requireField(obj, "type") { it.optString("type", "").ifBlank { null } }
        // Unknown types are accepted by the codec; the import coordinator
        // rejects them per-item so a single unknown type doesn't invalidate
        // the entire document.
        val column = requireNonNegativeInt(obj, "column")
        val row = requireNonNegativeInt(obj, "row")
        val columns = requirePositiveInt(obj, "columns")
        val rows = requirePositiveInt(obj, "rows")

        val appObj = requireField(obj, "app") { it.optJSONObject("app") }
        val profileId = requireField(appObj, "profileId") {
            it.optString("profileId", "").ifBlank { null }
        }
        val packageName = requireField(appObj, "packageName") {
            it.optString("packageName", "").ifBlank { null }
        }
        val className = requireField(appObj, "className") {
            it.optString("className", "").ifBlank { null }
        }

        val shortcutsArray = obj.optJSONArray("pinnedShortcuts")
        val shortcuts = if (shortcutsArray != null) {
            (0 until shortcutsArray.length()).map { i ->
                decodeShortcutRef(shortcutsArray.getJSONObject(i))
            }
        } else {
            emptyList()
        }

        val albumObj = obj.optJSONObject("album")
        val album = if (albumObj != null) decodeAlbumRef(albumObj) else null

        return HomeLayoutJsonItem(
            id = id,
            type = type,
            column = column,
            row = row,
            columns = columns,
            rows = rows,
            app = HomeLayoutJsonAppRef(profileId, packageName, className),
            pinnedShortcuts = shortcuts,
            album = album,
        )
    }

    private fun decodeShortcutRef(obj: JSONObject): HomeLayoutJsonShortcutRef {
        val packageName = requireField(obj, "packageName") {
            it.optString("packageName", "").ifBlank { null }
        }
        val profileId = requireField(obj, "profileId") {
            it.optString("profileId", "").ifBlank { null }
        }
        val shortcutId = requireField(obj, "shortcutId") {
            it.optString("shortcutId", "").ifBlank { null }
        }
        return HomeLayoutJsonShortcutRef(packageName, profileId, shortcutId)
    }

    private fun decodeAlbumRef(obj: JSONObject): HomeLayoutJsonAlbumRef {
        val bucketId = requireField(obj, "bucketId") {
            it.optString("bucketId", "").ifBlank { null }
        }
        val name = requireField(obj, "name") {
            it.optString("name", "").ifBlank { null }
        }
        return HomeLayoutJsonAlbumRef(bucketId, name)
    }

    private fun <T> requireField(
        obj: JSONObject,
        name: String,
        extract: (JSONObject) -> T?,
    ): T {
        val value = extract(obj)
            ?: throw HomeLayoutJsonException(
                RejectedReason.MissingField,
                "Missing or blank required field '$name'",
            )
        return value
    }

    private fun requireNonNegativeInt(obj: JSONObject, name: String): Int {
        val v = obj.optInt(name, -1)
        if (v < 0) {
            throw HomeLayoutJsonException(
                RejectedReason.MissingField,
                "Missing or negative required field '$name'",
            )
        }
        return v
    }

    private fun requirePositiveInt(obj: JSONObject, name: String): Int {
        val v = obj.optInt(name, -1)
        if (v <= 0) {
            throw HomeLayoutJsonException(
                RejectedReason.InvalidSpan,
                "Non-positive required field '$name'",
            )
        }
        return v
    }
}