package me.foxtails.lakehead.core.model

/**
 * Launcher-side identity for a pinned shortcut.
 *
 * This represents a shortcut that the launcher has chosen to pin to a tile,
 * distinct from Android's `requestPinShortcut` API which asks the system to
 * surface a shortcut suggestion. All three fields are launcher-owned identifiers;
 * no `ShortcutInfo`, `Intent`, `Drawable` or framework metadata is stored here.
 */
data class ShortcutRef(
    val packageName: String,
    val profileId: ProfileId,
    val shortcutId: String,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(shortcutId.isNotBlank()) { "shortcutId must not be blank" }
    }
}