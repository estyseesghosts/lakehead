package me.foxtails.lakehead.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure geometry of the 2×2 preview inside a 1×1 folder tile.
 *
 * Units are whatever the caller measures in (dp or px); the rule is unit-agnostic. Cells are
 * square, read row-major (top-left, top-right, bottom-left, bottom-right), separated by [gap]
 * and inset from the tile edge by [padding].
 */
object FolderPreviewGeometry {
    const val CELL_COUNT: Int = 4

    data class Cell(val left: Float, val top: Float, val size: Float)

    /** Side of one preview cell in a square tile of side [extent]; never negative. */
    fun cellSize(extent: Float, padding: Float, gap: Float): Float =
        ((extent - 2f * padding - gap) / 2f).coerceAtLeast(0f)

    fun cells(extent: Float, padding: Float, gap: Float): List<Cell> {
        val size = cellSize(extent, padding, gap)
        val step = size + gap
        return listOf(
            Cell(padding, padding, size),
            Cell(padding + step, padding, size),
            Cell(padding, padding + step, size),
            Cell(padding + step, padding + step, size),
        )
    }
}

/** Test tag on the folder tile's single hit target. */
const val FOLDER_TILE_TAG: String = "folder-tile"

/**
 * A 1×1 folder tile: one hit target over a 2×2 grid of up to four member symbols.
 *
 * State in, events out. [previews] holds at most [FolderPreviewGeometry.CELL_COUNT] entries in
 * row-major order; a null entry is a member whose symbol is not available and draws a quiet
 * placeholder, and missing trailing entries leave empty cells. [label] is the accessible name
 * only and is never rendered. When [isInteractionEnabled] is false the tile keeps its semantics
 * but installs no click detector, so in-edit drag gestures win the slop race.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderTile(
    label: String,
    previews: List<ImageBitmap?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isInteractionEnabled: Boolean = true,
    colorRole: TileColorRole = TileColorRole.Normal,
) {
    require(label.isNotBlank()) { "label must be non-blank" }
    require(previews.size <= FolderPreviewGeometry.CELL_COUNT) { "at most 4 previews" }
    val colors = resolveTileColors(role = colorRole, isPressed = false, isSelected = isSelected)
    val dimensions = LocalLauncherDimensions.current
    BoxWithConstraints(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .fillMaxSize()
            .background(colors.container, AppTileDefaults.TileShape)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, colors.selectionBorder, AppTileDefaults.TileShape)
                } else {
                    Modifier
                },
            ),
    ) {
        val side = minOf(maxWidth, maxHeight)
        val padding = dimensions.tilePadding / 2
        val gap = dimensions.gridGap / 2
        val cells = FolderPreviewGeometry.cells(side.value, padding.value, gap.value)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(FOLDER_TILE_TAG)
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    role = Role.Button
                    selected = isSelected
                }
                .then(
                    if (isInteractionEnabled) {
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    } else {
                        Modifier
                    },
                ),
        ) {
            cells.forEachIndexed { index, cell ->
                val bitmap = previews.getOrNull(index)
                if (index < previews.size) {
                    Box(Modifier.offset(cell.left.dp, cell.top.dp).size(cell.size.dp)) {
                        if (bitmap != null) {
                            AppSymbol(bitmap = bitmap, extent = cell.size.dp)
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(colors.content.copy(alpha = 0.18f), AppTileDefaults.TileShape),
                            )
                        }
                    }
                }
            }
        }
    }
}
