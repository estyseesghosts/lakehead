package me.foxtails.lakehead.core.model

/**
 * A rank-ordered, framework-free shortcut published for one launchable app.
 *
 * [ref] is the stable launcher-owned identity (package, profile, shortcut id);
 * [label] is the user-visible short label resolved at the platform boundary;
 * [rank] preserves the application's/system's shortcut order (lower ranks first).
 * No `ShortcutInfo`, `Drawable` or icon is carried here — icon loading is a
 * later slice.
 */
data class LauncherShortcut(
    val ref: ShortcutRef,
    val label: String,
    val rank: Int,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
        require(rank >= 0) { "rank must be non-negative" }
    }
}
