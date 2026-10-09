package me.foxtails.lakehead.platform.notifications

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.service.notification.StatusBarNotification
import me.foxtails.lakehead.core.model.NotificationSourceKey
import me.foxtails.lakehead.core.model.ProfileId

/**
 * Framework boundary: reads [NotificationFacts] from a [StatusBarNotification].
 * Profile identity is the user serial number, the same token the launcher
 * catalogue uses. Content is read into memory only and never logged.
 */
internal class StatusBarNotificationReader(context: Context) {
    private val userManager = context.getSystemService(UserManager::class.java)

    fun read(sbn: StatusBarNotification): NotificationFacts? {
        val serial = userManager.getSerialNumberForUser(sbn.user)
        if (serial < 0L) return null
        val notification = sbn.notification
        val extras = notification.extras
        val latestMessage = latestMessage(extras)
        return NotificationFacts(
            id = sbn.key,
            source = NotificationSourceKey(sbn.packageName, ProfileId(serial.toString())),
            postTime = sbn.postTime,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            senderName = latestMessage?.senderPerson?.name?.toString(),
            conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = latestMessage?.text?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        )
    }

    /**
     * Newest message of a `MessagingStyle` notification, or `null` for any other style.
     * Parsing the bundles needs API 30; below that callers fall back to title and text.
     */
    @Suppress("DEPRECATION")
    internal fun latestMessage(extras: android.os.Bundle): Notification.MessagingStyle.Message? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        return Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles).lastOrNull()
    }
}
