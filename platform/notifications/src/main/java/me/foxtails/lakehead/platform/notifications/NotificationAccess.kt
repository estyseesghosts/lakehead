package me.foxtails.lakehead.platform.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Notification-listener access state and the system screen that changes it. */
object NotificationAccess {
    fun isGranted(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.isNotificationListenerAccessGranted(
            ComponentName(context, TileNotificationListenerService::class.java),
        )
    }

    /** The system listener-access settings; access is only ever granted there, never pre-granted. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
