package me.foxtails.lakehead

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppPlacement
import me.foxtails.lakehead.core.model.AlbumRef
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.HomeId
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.core.model.ShortcutRef
import me.foxtails.lakehead.core.model.TileAlbumBinding
import me.foxtails.lakehead.core.model.TileBinding
import me.foxtails.lakehead.core.model.FolderPlacement
import me.foxtails.lakehead.data.repository.LoadFolderResult
import me.foxtails.lakehead.data.repository.CreateFolderResult
import me.foxtails.lakehead.data.repository.TransferResult
import me.foxtails.lakehead.data.repository.FolderRepository
import me.foxtails.lakehead.data.repository.HomePlacementRepository
import me.foxtails.lakehead.data.repository.LoadResult
import me.foxtails.lakehead.data.repository.MutationResult
import org.json.JSONObject

/** Item rejected during import with its original id and the reason. */
data class RejectedItem(val id: String, val reason: String)

/** Result of an import operation. */
sealed interface ImportResult {
    /** Import succeeded (possibly partially). */
    data class Applied(
        val itemsPlaced: Int,
        val itemsShifted: Int,
        val bindingsRestored: Int,
        val rejected: List<RejectedItem>,
    ) : ImportResult

    /** The entire document was rejected (e.g. wrong version/kind). */
    data class Rejected(val reason: String) : ImportResult
}

/** Domain-facing port for home layout export and import. */
interface HomeLayoutExportImportPort {
    /** Exports the current home layout as a JSON document + suggested filename. */
    suspend fun exportLayout(): Pair<JSONObject, String>

    /** Imports a JSON document, reflowing as needed. Returns the result. */
    suspend fun importLayout(json: JSONObject): ImportResult
}

/**
 * Coordinator for home layout export/import.
 *
 * Reads placements and bindings through the [HomePlacementRepository], encodes/decodes via
 * [HomeLayoutCodec], and reflows imported items whose requested positions collide or exceed
 * the column bound.
 *
 * Album slideshow bindings (decision 7) export as the optional per-item `album`
 * field (bucket id + name only). On import a binding is restored only when its
 * bucket is in [loadDeviceAlbumBuckets]; otherwise it is dropped silently —
 * the tile is still placed, with no binding and no rejection.
 */
class HomeLayoutExportImport(
    private val homeId: HomeId,
    private val columns: Int,
    private val repository: HomePlacementRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val loadDeviceAlbumBuckets: suspend () -> Set<String> = { emptySet() },
    private val folders: FolderRepository? = null,
) : HomeLayoutExportImportPort {

    override suspend fun exportLayout(): Pair<JSONObject, String> = withContext(ioDispatcher) {
        val loadResult = repository.load(homeId, columns)
        val placements = when (loadResult) {
            is LoadResult.Loaded -> loadResult.placements
            is LoadResult.NotFound -> emptyList()
        }
        val bindingsByTile = mutableMapOf<ItemId, TileBinding>()
        val albumBindingsByTile = mutableMapOf<ItemId, TileAlbumBinding>()
        for (placement in placements) {
            bindingsByTile[placement.itemId] = repository.loadBinding(placement.itemId)
            repository.loadAlbumBinding(placement.itemId)?.let { albumBindingsByTile[placement.itemId] = it }
        }
        val members = placements.filterIsInstance<FolderPlacement>().associate { folder ->
            val loaded = folders?.loadFolder(homeId, folder.itemId, columns) as? LoadFolderResult.Loaded
            folder.itemId to loaded?.members.orEmpty().filterIsInstance<AppPlacement>()
        }
        val json = HomeLayoutCodec.encodeLayout(
            homeId, columns, placements, bindingsByTile, albumBindingsByTile, members,
        )
        val filename = "${homeId.value}-${System.currentTimeMillis() / 1000}.json"
        Pair(json, filename)
    }

    override suspend fun importLayout(json: JSONObject): ImportResult = withContext(ioDispatcher) {
        val document = try {
            HomeLayoutCodec.decodeLayout(json)
        } catch (e: HomeLayoutJsonException) {
            return@withContext ImportResult.Rejected(e.message ?: "Invalid document")
        }

        repository.ensureHome(homeId)

        var itemsPlaced = 0
        var itemsShifted = 0
        var bindingsRestored = 0
        val rejected = mutableListOf<RejectedItem>()

        // Device albums for the optional album-binding restore (decision 7):
        // queried once per import, and only when at least one item carries a
        // binding. A denied photo permission reads as no albums, so every
        // binding drops honestly.
        val deviceAlbumBuckets = if (document.items.any { it.album != null }) {
            loadDeviceAlbumBuckets()
        } else {
            emptySet()
        }

        // Track placed items for collision detection during reflow.
        // Each entry: (column, row, columns, rows)
        data class PlacedRect(val column: Int, val row: Int, val columns: Int, val rows: Int)

        val placedRects = mutableListOf<PlacedRect>()

        // Seed with existing placements so we don't collide with tiles
        // already on the home that are not part of this import.
        val existingLoad = repository.load(homeId, columns)
        if (existingLoad is LoadResult.Loaded) {
            for (p in existingLoad.placements) {
                placedRects.add(
                    PlacedRect(
                        p.position.column,
                        p.position.row,
                        p.span.columns,
                        p.span.rows,
                    )
                )
            }
        }

        fun overlaps(
            c: Int, r: Int, cs: Int, rs: Int,
            rects: List<PlacedRect>,
        ): Boolean {
            for (rect in rects) {
                if (c < rect.column + rect.columns &&
                    c + cs > rect.column &&
                    r < rect.row + rect.rows &&
                    r + rs > rect.row
                ) {
                    return true
                }
            }
            return false
        }

        class Outcome(val column: Int, val row: Int, val shifted: Boolean)

        // Places at the requested cell, else reflows left within the row, else
        // shifts up against prior items; null when no legal cell exists.
        fun placeReflowing(
            itemId: ItemId,
            appIdentity: AppIdentity,
            span: LogicalSpan,
            desiredColumn: Int,
            desiredRow: Int,
        ): Outcome? {
            fun tryPosition(col: Int, row: Int): Boolean =
                col >= 0 && row >= 0 &&
                    col + span.columns <= columns &&
                    !overlaps(col, row, span.columns, span.rows, placedRects) &&
                    repository.place(
                        homeId = homeId,
                        itemId = itemId,
                        appIdentity = appIdentity,
                        position = LogicalPosition(col, row),
                        span = span,
                        columns = columns,
                    ) is MutationResult.Accepted

            fun accept(col: Int, row: Int, shifted: Boolean): Outcome {
                placedRects.add(PlacedRect(col, row, span.columns, span.rows))
                return Outcome(col, row, shifted)
            }

            if (tryPosition(desiredColumn, desiredRow)) return accept(desiredColumn, desiredRow, false)
            val maxCol = (columns - span.columns).coerceAtLeast(0)
            for (col in maxCol downTo 0) {
                if (col == desiredColumn) continue
                if (tryPosition(col, desiredRow)) return accept(col, desiredRow, true)
            }
            for (row in 0 until desiredRow) {
                for (col in 0..(columns - span.columns)) {
                    if (tryPosition(col, row)) return accept(col, row, true)
                }
            }
            return null
        }

        // Rebuilds a folder: the first member is placed at the folder's cell and
        // converted, the rest are placed in free cells and moved in. Spans and
        // in-folder cells are not carried; members land first-free. Returns the
        // number of tiles placed (0 when the folder was rejected).
        fun importFolder(item: HomeLayoutJsonItem): Int {
            val repo = folders
            if (repo == null) {
                rejected.add(RejectedItem(item.id, "Folders are unavailable"))
                return 0
            }
            val members = item.members.mapNotNull { member ->
                try {
                    member to AppIdentity(
                        profileId = ProfileId(member.app.profileId),
                        component = ComponentIdentity(member.app.packageName, member.app.className),
                    )
                } catch (e: IllegalArgumentException) {
                    rejected.add(RejectedItem(member.id, "Invalid app reference: ${e.message}"))
                    null
                }
            }
            if (members.isEmpty()) {
                rejected.add(RejectedItem(item.id, "Folder has no importable members"))
                return 0
            }
            val one = LogicalSpan(1, 1)
            val (first, firstApp) = members.first()
            val firstAt = placeReflowing(ItemId(first.id), firstApp, one, item.column, item.row)
            if (firstAt == null) {
                rejected.add(RejectedItem(item.id, "No legal position found"))
                return 0
            }
            if (members.size == 1) return 1 // a one-member folder is just the app
            val folderId = ItemId(item.id)
            if (repo.createFolder(homeId, ItemId(first.id), folderId, columns) !is CreateFolderResult.Created) {
                rejected.add(RejectedItem(item.id, "Could not create folder"))
                return 1
            }
            var placed = 1
            for ((member, app) in members.drop(1)) {
                val at = placeReflowing(ItemId(member.id), app, one, item.column, item.row)
                if (at == null) {
                    rejected.add(RejectedItem(member.id, "No legal position found"))
                    continue
                }
                if (repo.moveIntoFolder(ItemId(member.id), folderId, columns) is TransferResult.Done) {
                    placedRects.remove(PlacedRect(at.column, at.row, 1, 1))
                    placed++
                } else {
                    rejected.add(RejectedItem(member.id, "Could not move into folder"))
                }
            }
            return placed
        }

        for (item in document.items) {
            if (item.type == "FOLDER") {
                itemsPlaced += importFolder(item)
                continue
            }
            if (item.type != "APP") {
                rejected.add(RejectedItem(item.id, "Unknown item type '${item.type}'"))
                continue
            }

            val appIdentity = try {
                AppIdentity(
                    profileId = ProfileId(item.app!!.profileId),
                    component = ComponentIdentity(item.app.packageName, item.app.className),
                )
            } catch (e: IllegalArgumentException) {
                rejected.add(RejectedItem(item.id, "Invalid app reference: ${e.message}"))
                continue
            }

            val span = try {
                LogicalSpan(item.columns, item.rows)
            } catch (e: IllegalArgumentException) {
                rejected.add(RejectedItem(item.id, "Invalid span: ${e.message}"))
                continue
            }

            val itemId = ItemId(item.id)
            val outcome = placeReflowing(itemId, appIdentity, span, item.column, item.row)
            if (outcome == null) {
                rejected.add(RejectedItem(item.id, "No legal position found"))
                continue
            }
            itemsPlaced++
            if (outcome.shifted) itemsShifted++

            // Restore pinned shortcuts.
            if (item.pinnedShortcuts.isNotEmpty()) {
                val shortcutRefs = item.pinnedShortcuts.map { ref ->
                    ShortcutRef(
                        packageName = ref.packageName,
                        profileId = ProfileId(ref.profileId),
                        shortcutId = ref.shortcutId,
                    )
                }
                repository.saveBinding(TileBinding(tileId = itemId, pinnedShortcuts = shortcutRefs))
                bindingsRestored++
            }

            // Restore the album slideshow binding only when the album is on
            // this device; otherwise the binding drops while the tile stays.
            val album = item.album
            if (album != null && album.bucketId in deviceAlbumBuckets) {
                repository.saveAlbumBinding(TileAlbumBinding(itemId, AlbumRef(album.bucketId, album.name)))
                bindingsRestored++
            }
        }

        ImportResult.Applied(
            itemsPlaced = itemsPlaced,
            itemsShifted = itemsShifted,
            bindingsRestored = bindingsRestored,
            rejected = rejected,
        )
    }
}