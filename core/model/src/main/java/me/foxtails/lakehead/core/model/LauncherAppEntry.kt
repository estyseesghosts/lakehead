package me.foxtails.lakehead.core.model

/** A launchable app identity paired with its user-visible label. */
data class LauncherAppEntry(
    val identity: AppIdentity,
    val label: String,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
    }
}
