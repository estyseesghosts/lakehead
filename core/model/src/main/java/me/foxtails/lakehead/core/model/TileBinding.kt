package me.foxtails.lakehead.core.model

/**
 * Associates a launcher tile with zero or more pinned shortcuts.
 *
 * Identity is driven by [tileId], not by package name — two tiles may pin
 * shortcuts from the same app and remain distinct. The [pinnedShortcuts] list
 * preserves insertion order because display order is a presentation concern.
 */
data class TileBinding(
    val tileId: ItemId,
    val pinnedShortcuts: List<ShortcutRef> = emptyList(),
)