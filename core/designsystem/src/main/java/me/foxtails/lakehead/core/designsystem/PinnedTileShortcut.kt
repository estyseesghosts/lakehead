package me.foxtails.lakehead.core.designsystem

/**
 * One pinned shortcut as rendered inside an app tile's shortcut strip.
 *
 * Presentation only: [id] is the opaque caller identity (the shortcut's
 * stable shortcut id) carried back through click callbacks and never
 * interpreted here; [label] is the user-visible name resolved at the
 * platform boundary.  No icon is carried: `LauncherShortcut` publishes no
 * icon this slice, so the strip renders a neutral marker glyph with this
 * accessible [label] instead of a second icon system.  Per-tile distinction
 * (two tiles pinning the same package, work profiles) is preserved upstream
 * by the tile-keyed binding that produced this list; this type never merges
 * entries across tiles.
 */
data class PinnedTileShortcut(
    val id: String,
    val label: String,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(label.isNotBlank()) { "label must not be blank" }
    }
}

/**
 * Accessible name for a rendered pinned shortcut glyph: `"<label>, shortcut"`.
 *
 * The `, shortcut` suffix keeps the glyph distinct from the tile's own app
 * symbol in TalkBack when both expose similar text.  Pure, framework-free.
 */
fun pinnedShortcutDescription(label: String): String {
    require(label.isNotBlank()) { "label must not be blank" }
    return "$label, shortcut"
}

/**
 * Accessible text for hidden overflow: `"N more shortcuts"`.
 *
 * Rendered visibly as `+N` when the tile has strip capacity and is not
 * edit-selected; otherwise merged into the tile body's `stateDescription`
 * so the overflow stays announced without a visible node.  Pure,
 * framework-free.
 */
fun pinnedOverflowDescription(overflowCount: Int): String {
    require(overflowCount > 0) { "overflowCount must be positive" }
    return "$overflowCount more shortcuts"
}
