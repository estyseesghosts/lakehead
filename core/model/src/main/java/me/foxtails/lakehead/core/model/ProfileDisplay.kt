package me.foxtails.lakehead.core.model

/** Profile-aware presentation metadata for a launchable app. */
data class ProfileDisplay(
    val identity: AppIdentity,
    val sortKey: String,
    val displayLabel: String,
    val profileDisplay: ProfileDisplayKind,
) {
    init {
        require(sortKey.isNotBlank()) { "sortKey must not be blank" }
        require(displayLabel.isNotBlank()) { "displayLabel must not be blank" }
    }
}

enum class ProfileDisplayKind {
    PERSONAL,
    WORK,
    SHELTER,
}
