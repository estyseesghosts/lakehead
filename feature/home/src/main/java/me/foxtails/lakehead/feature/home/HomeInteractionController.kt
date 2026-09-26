package me.foxtails.lakehead.feature.home

import me.foxtails.lakehead.core.model.ItemId

/**
 * Pure interaction controller for the home screen.
 *
 * Manages the Normal ↔ Editing state without any UI, persistence, or grid mutation.
 * Tile identity is always [ItemId]; never a position or index.
 *
 * ## States
 * - [Normal]: default browsing state. Taps are inert (no launch).
 * - [Editing]: edit mode. [selectedId] tracks the currently focused tile, or null when none.
 *
 * ## Transitions
 * - Long-press on an available tile → [Editing] with that tile selected.
 * - Tap a tile while editing → [Editing] with the tapped tile selected.
 * - Tap empty space / back / navigate away → [Normal].
 *
 * All exits are idempotent: calling any exit from [Normal] returns [Normal].
 */
sealed interface HomeInteractionController {

    /** Default home browsing state. Taps never launch. */
    data object Normal : HomeInteractionController

    /**
     * Edit mode entered via long-press.
     *
     * @param selectedId the tile the user is currently focused on, or null when no tile is selected.
     */
    data class Editing(val selectedId: ItemId?) : HomeInteractionController
}

/**
 * Enter edit mode on long-press if [isAvailable] is true.
 *
 * Returns [HomeInteractionController.Editing] with [tileId] selected, or this (unchanged)
 * if the tile is unavailable. Works from both [Normal] and [Editing].
 */
fun HomeInteractionController.enterOnLongPress(
    tileId: ItemId,
    isAvailable: Boolean,
): HomeInteractionController {
    if (!isAvailable) return this
    return HomeInteractionController.Editing(selectedId = tileId)
}

/**
 * Select a tile via tap in editing mode.
 *
 * No-op in [Normal]: taps never launch from this controller.
 */
fun HomeInteractionController.selectOnTap(
    tileId: ItemId,
): HomeInteractionController {
    if (this !is HomeInteractionController.Editing) return this
    return copy(selectedId = tileId)
}

/**
 * Exit edit mode when the user taps empty space.
 *
 * [HomeInteractionController.Editing] → [HomeInteractionController.Normal].
 * No-op in [Normal] (idempotent).
 */
fun HomeInteractionController.exitOnEmptyTap(): HomeInteractionController {
    if (this !is HomeInteractionController.Editing) return this
    return HomeInteractionController.Normal
}

/**
 * Exit edit mode on back press.
 *
 * [HomeInteractionController.Editing] → [HomeInteractionController.Normal].
 * No-op in [Normal] (idempotent).
 */
fun HomeInteractionController.exitOnBack(): HomeInteractionController {
    if (this !is HomeInteractionController.Editing) return this
    return HomeInteractionController.Normal
}

/**
 * Exit edit mode on navigate-away.
 *
 * [HomeInteractionController.Editing] → [HomeInteractionController.Normal].
 * No-op in [Normal] (idempotent).
 */
fun HomeInteractionController.exitOnNavigate(): HomeInteractionController {
    if (this !is HomeInteractionController.Editing) return this
    return HomeInteractionController.Normal
}

/**
 * Deselect [tileId] if it is the currently selected tile in [HomeInteractionController.Editing].
 *
 * [HomeInteractionController.Editing] with [HomeInteractionController.Editing.selectedId]
 * matching [tileId] → [HomeInteractionController.Editing] with `selectedId = null`.
 * All other states ([Normal], [Editing] with a different or null selectedId) are
 * returned unchanged (idempotent).
 */
fun HomeInteractionController.unpinIfSelected(
    tileId: ItemId,
): HomeInteractionController {
    if (this !is HomeInteractionController.Editing) return this
    if (selectedId != tileId) return this
    return copy(selectedId = null)
}