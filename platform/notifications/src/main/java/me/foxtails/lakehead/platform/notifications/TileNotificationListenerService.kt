package me.foxtails.lakehead.platform.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Feeds [NotificationSummaryStore.shared] from the system. Active only while the
 * user has granted notification-listener access; the system unbinds it on
 * revoke, which clears every summary and cached photo. Text is recorded on the
 * callback thread; photos are extracted on a background thread and attached
 * afterwards.
 */
class TileNotificationListenerService : NotificationListenerService() {
    private val store get() = NotificationSummaryStore.shared
    private val reader by lazy(LazyThreadSafetyMode.NONE) { StatusBarNotificationReader(this) }
    private val photos by lazy(LazyThreadSafetyMode.NONE) { NotificationPhotoCache.shared(this) }
    private val extractor by lazy(LazyThreadSafetyMode.NONE) { NotificationPhotoExtractor(this, reader) }
    private var worker: ExecutorService? = null

    override fun onListenerConnected() {
        worker = Executors.newSingleThreadExecutor()
        val active = activeNotifications.orEmpty()
        store.replaceAll(active.mapNotNull(::track))
        active.forEach(::attachPhotoAsync)
    }

    override fun onListenerDisconnected() {
        worker?.shutdownNow()
        worker = null
        store.clear()
        photos.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val tracked = track(sbn)
        if (tracked == null) {
            store.remove(sbn.key)
            prunePhotosAsync()
            return
        }
        store.upsert(tracked)
        attachPhotoAsync(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        store.remove(sbn.key)
        prunePhotosAsync()
    }

    private fun track(sbn: StatusBarNotification): TrackedNotification? =
        reader.read(sbn)?.let(::normalizeNotification)

    private fun attachPhotoAsync(sbn: StatusBarNotification) {
        worker?.execute {
            val png = runCatching { extractor.extract(sbn) }.getOrNull() ?: return@execute
            val key = photoKeyFor(sbn.key, sbn.postTime)
            photos.write(key, png)
            store.attachPhoto(sbn.key, key)
            photos.prune(store.referencedPhotoKeys())
        }
    }

    private fun prunePhotosAsync() {
        worker?.execute { photos.prune(store.referencedPhotoKeys()) }
    }
}
