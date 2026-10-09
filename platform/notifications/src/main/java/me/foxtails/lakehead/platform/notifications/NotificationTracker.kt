package me.foxtails.lakehead.platform.notifications

import me.foxtails.lakehead.core.model.AppNotificationSummary
import me.foxtails.lakehead.core.model.NotificationPreviewItem
import me.foxtails.lakehead.core.model.NotificationSourceKey

/** Framework-free facts read from one status-bar notification. */
data class NotificationFacts(
    val id: String,
    val source: NotificationSourceKey,
    val postTime: Long,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val senderName: String?,
    val conversationTitle: String?,
    val title: String?,
    val text: String?,
)

/** A normalised notification the launcher tracks in memory. */
data class TrackedNotification(
    val id: String,
    val source: NotificationSourceKey,
    val postTime: Long,
    val name: String,
    val body: String,
    val photoKey: String? = null,
)

/**
 * Reduces [facts] to a [TrackedNotification], or `null` for ongoing notifications
 * and group summaries (which never count). The name is the message sender, then
 * the conversation title, then the notification title; blank strings are ignored.
 */
fun normalizeNotification(facts: NotificationFacts): TrackedNotification? {
    if (facts.isOngoing || facts.isGroupSummary) return null
    val name = listOf(facts.senderName, facts.conversationTitle, facts.title)
        .firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .orEmpty()
    return TrackedNotification(
        id = facts.id,
        source = facts.source,
        postTime = facts.postTime,
        name = name,
        body = facts.text?.trim().orEmpty(),
    )
}

/**
 * In-memory set of active notifications. Not thread-safe; [NotificationSummaryStore]
 * serialises access. Nothing here is persisted or logged.
 */
class NotificationTracker(private val maxPreviews: Int = MAX_PREVIEWS) {
    private val byId = LinkedHashMap<String, TrackedNotification>()

    fun replaceAll(notifications: List<TrackedNotification>) {
        byId.clear()
        notifications.forEach { byId[it.id] = it }
    }

    /** Inserts or replaces by id, keeping an already attached photo for the same notification. */
    fun upsert(notification: TrackedNotification) {
        val photo = notification.photoKey ?: byId[notification.id]?.photoKey
        byId[notification.id] = notification.copy(photoKey = photo)
    }

    fun remove(id: String): TrackedNotification? = byId.remove(id)

    fun attachPhoto(id: String, photoKey: String) {
        val current = byId[id] ?: return
        byId[id] = current.copy(photoKey = photoKey)
    }

    fun clear() = byId.clear()

    fun photoKeys(): Set<String> = byId.values.mapNotNullTo(HashSet()) { it.photoKey }

    /** Per-source count and newest-first previews; previews skip notifications with no text at all. */
    fun summaries(): Map<NotificationSourceKey, AppNotificationSummary> =
        byId.values.groupBy { it.source }.mapValues { (_, items) ->
            val newestFirst = items.sortedByDescending { it.postTime }
            AppNotificationSummary(
                count = items.size,
                previews = newestFirst
                    .filter { it.name.isNotEmpty() || it.body.isNotEmpty() }
                    .take(maxPreviews)
                    .map { NotificationPreviewItem(it.name, it.body, it.photoKey) },
            )
        }

    companion object {
        const val MAX_PREVIEWS = 6
    }
}
