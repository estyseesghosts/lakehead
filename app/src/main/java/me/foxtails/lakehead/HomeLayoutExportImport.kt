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
        val json = HomeLayoutCodec.encodeLayout(
            homeId, columns, placements.filterIsInstance<AppPlacement>(), bindingsByTile, albumBindingsByTile,
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

        for (item in document.items) {
            if (item.type != "APP") {
                rejected.add(RejectedItem(item.id, "Unknown item type '${item.type}'"))
                continue
            }

            val appIdentity = try {
                AppIdentity(
                    profileId = ProfileId(item.app.profileId),
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

            // Attempt placement at the requested cell first, then reflow.
            val desiredColumn = item.column
            val desiredRow = item.row
            val itemId = ItemId(item.id)
            var placed = false
            var shifted = false

            // Try the original position first.
            val tryPosition: (Int, Int) -> MutationResult? = { col, row ->
                if (col >= 0 && row >= 0 &&
                    col + span.columns <= columns &&
                    !overlaps(col, row, span.columns, span.rows, placedRects)
                ) {
                    repository.place(
                        homeId = homeId,
                        itemId = itemId,
                        appIdentity = appIdentity,
                        position = LogicalPosition(col, row),
                        span = span,
                        columns = columns,
                    )
                } else {
                    null
                }
            }

            // 1. Try requested position.
            val originalResult = tryPosition(desiredColumn, desiredRow)
            if (originalResult is MutationResult.Accepted) {
                placedRects.add(PlacedRect(desiredColumn, desiredRow, span.columns, span.rows))
                placed = true
                shifted = false
            } else {
                // 2. Reflow left within the same row.
                val maxCol = (columns - span.columns).coerceAtLeast(0)
                for (col in maxCol downTo 0) {
                    if (col == desiredColumn) continue
                    val result = tryPosition(col, desiredRow)
                    if (result is MutationResult.Accepted) {
                        placedRects.add(PlacedRect(col, desiredRow, span.columns, span.rows))
                        placed = true
                        shifted = true
                        break
                    }
                }
            }

            // 3. If still not placed, shift up against prior imported items.
            if (!placed) {
                for (row in 0 until desiredRow) {
                    for (col in 0..(columns - span.columns)) {
                        val result = tryPosition(col, row)
                        if (result is MutationResult.Accepted) {
                            placedRects.add(PlacedRect(col, row, span.columns, span.rows))
                            placed = true
                            shifted = true
                            break
                        }
                    }
                    if (placed) break
                }
            }

            if (!placed) {
                rejected.add(RejectedItem(item.id, "No legal position found"))
                continue
            }

            if (placed) itemsPlaced++
            if (shifted) itemsShifted++

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