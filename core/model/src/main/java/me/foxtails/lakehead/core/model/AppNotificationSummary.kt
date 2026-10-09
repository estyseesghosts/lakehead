package me.foxtails.lakehead.core.model

/**
 * Which app's notifications a summary describes: package plus the profile it
 * runs in, so a work-profile copy of an app is never merged with the personal one.
 */
data class NotificationSourceKey(
    val packageName: String,
    val profileId: ProfileId,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
    }
}

/**
 * One notification shown as a preview row. [photoKey] names a platform-owned
 * cached photo (sender or large icon) and is `null` when the notification
 * carries none; the model never holds a bitmap.
 */
data class NotificationPreviewItem(
    val name: String,
    val body: String,
    val photoKey: String? = null,
)

/** Active, non-ongoing, non-summary notifications of one source: [count] and newest-first [previews]. */
data class AppNotificationSummary(
    val count: Int,
    val previews: List<NotificationPreviewItem>,
) {
    init {
        require(count >= 0) { "count must be non-negative" }
    }
}
