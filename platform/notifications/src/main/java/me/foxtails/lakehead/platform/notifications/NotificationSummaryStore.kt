package me.foxtails.lakehead.platform.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.foxtails.lakehead.core.model.AppNotificationSummary
import me.foxtails.lakehead.core.model.NotificationSourceKey

/**
 * Owner of the in-memory notification summaries published to the launcher. The
 * listener service and the app share one process, so [shared] is the single
 * instance both use. Access is serialised; every mutation republishes.
 */
class NotificationSummaryStore {
    private val lock = Any()
    private val tracker = NotificationTracker()
    private val mutableSummaries = MutableStateFlow<Map<NotificationSourceKey, AppNotificationSummary>>(emptyMap())

    val summaries: StateFlow<Map<NotificationSourceKey, AppNotificationSummary>> = mutableSummaries.asStateFlow()

    fun replaceAll(notifications: List<TrackedNotification>) = mutate { replaceAll(notifications) }

    fun upsert(notification: TrackedNotification) = mutate { upsert(notification) }

    /** Removes by id and returns the removed notification so its photo can be released. */
    fun remove(id: String): TrackedNotification? {
        var removed: TrackedNotification? = null
        mutate { removed = remove(id) }
        return removed
    }

    fun attachPhoto(id: String, photoKey: String) = mutate { attachPhoto(id, photoKey) }

    fun clear() = mutate { clear() }

    /** Photo keys still referenced by a tracked notification. */
    fun referencedPhotoKeys(): Set<String> = synchronized(lock) { tracker.photoKeys() }

    private inline fun mutate(block: NotificationTracker.() -> Unit) {
        synchronized(lock) {
            tracker.block()
            mutableSummaries.value = tracker.summaries()
        }
    }

    companion object {
        val shared = NotificationSummaryStore()
    }
}
