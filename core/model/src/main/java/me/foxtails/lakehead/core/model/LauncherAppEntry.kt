package me.foxtails.lakehead.core.model

/** A launchable app identity paired with its user-visible label. */
data class LauncherAppEntry(
    val identity: AppIdentity,
    val label: String,
    val sortKey: String = label,
    val displayLabel: String = label,
    val profileDisplay: ProfileDisplayKind = ProfileDisplayKind.PERSONAL,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
        require(sortKey.isNotBlank()) { "sortKey must not be blank" }
        require(displayLabel.isNotBlank()) { "displayLabel must not be blank" }
    }
}
