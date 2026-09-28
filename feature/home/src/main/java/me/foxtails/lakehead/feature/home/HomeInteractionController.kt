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
 * - [Editing]: edit mode. [Editing.selectedId] tracks the currently focused tile,
 *   or null when none. [Editing.resizeArmed] tracks whether the selected tile's
 *   resize arrow has been tapped: an armed selection routes an arrow-origin drag
 *   to resize, while an unarmed selection never resizes (arrow-origin drags are
 *   inert, body drags move with the snapshotted span). The armed flag is owned
 *   by the selected item: every selection change clears it.
 *
 * ## Transitions
 * - Long-press on any tile → [Editing] with that tile selected, disarmed.
 *   Entry is independent of launchability: unavailable tiles enter exactly
 *   like launchable ones.
 * - Tap a tile while editing → [Editing] with the tapped tile selected, disarmed.
 * - Tap the selected tile's resize arrow → toggles [Editing.resizeArmed], no write.
 * - Body-drag while armed → move with the snapshotted span, disarms.
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
     * @param resizeArmed true once the selected tile's resize arrow has been tapped.
     *   Owned by [selectedId]: every transfer, long-press retarget, unpin and exit
     *   clears it, and body-drag start clears it. Transient only, never persisted.
     */
    data class Editing(
        val selectedId: ItemId?,
        val resizeArmed: Boolean = false,
    ) : HomeInteractionController
}

/**
 * Enter edit mode on long-press, independent of launchability.
 *
 * Always returns [HomeInteractionController.Editing] with [tileId] selected
 * and resize disarmed. Works from both [Normal] and [Editing]; retargeting
 * always clears a previous arm.
 */
fun HomeInteractionController.enterOnLongPress(
    tileId: ItemId,
): HomeInteractionController {
    return HomeInteractionController.Editing(selectedId = tileId)
}

/**
 * Select a tile via tap in editing mode.
 *
 * Transfers selection directly and clears any resize arm: the newly selected
 * tile starts disarmed, so an arrow-drag needs a fresh arrow-tap first.
 * No-op in [Normal]: taps never launch from this controller.
 */
fun HomeInteractionController.selectOnTap(
    tileId: ItemId,
): HomeInteractionController {
    if (this !is HomeInteractionController.Editing) return this
    return copy(selectedId = tileId, resizeArmed = false)
}

/**
 * Arm resize for the currently selected tile.
 *
 * [HomeInteractionController.Editing] with a non-null
 * [HomeInteractionController.Editing.selectedId] → copy with `resizeArmed = true`.
 * Idempotent when already armed; no-op in [Normal] or when no tile is selected.
 * Transient only: no persistence, no grid mutation, no write.
 */
fun HomeInteractionController.armResize(): HomeInteractionController {
    if (this !is HomeInteractionController.Editing) return this
    if (selectedId == null || resizeArmed) return this
    return copy(resizeArmed = true)
}

/**
 * Disarm resize, keeping the current selection.
 *
 * Armed [HomeInteractionController.Editing] → copy with `resizeArmed = false`.
 * Idempotent when already disarmed; no-op in [Normal]. Used when a body-drag
 * starts (move keeps the snapshotted span) and when resize ends.
 */
fun HomeInteractionController.disarmResize(): HomeInteractionController {
    if (this !is HomeInteractionController.Editing) return this
    if (!resizeArmed) return this
    return copy(resizeArmed = false)
}

/**
 * Toggle the resize arm for the currently selected tile.
 *
 * Backs the resize-arrow tap: below-slop arrow taps flip [resizeArmed] with
 * zero writes and no session entry. No-op in [Normal] or when no tile is
 * selected (an arrow-tap with no selection cannot arm).
 */
fun HomeInteractionController.toggleResizeArmed(): HomeInteractionController {
    if (this !is HomeInteractionController.Editing) return this
    if (selectedId == null) return this
    return copy(resizeArmed = !resizeArmed)
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
 * matching [tileId] → [HomeInteractionController.Editing] with `selectedId = null`
 * and the resize arm cleared (the arm is owned by the selection). All other
 * states ([Normal], [Editing] with a different or null selectedId) are
 * returned unchanged (idempotent).
 */
fun HomeInteractionController.unpinIfSelected(
    tileId: ItemId,
): HomeInteractionController {
    if (this !is HomeInteractionController.Editing) return this
    if (selectedId != tileId) return this
    return copy(selectedId = null, resizeArmed = false)
}