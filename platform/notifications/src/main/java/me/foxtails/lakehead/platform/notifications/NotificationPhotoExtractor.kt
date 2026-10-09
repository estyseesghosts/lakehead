package me.foxtails.lakehead.platform.notifications

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import java.io.ByteArrayOutputStream

/**
 * Extracts one square PNG photo from a notification: the `MessagingStyle` sender's
 * `Person` icon, then the large icon. Needs no contacts permission. Loads
 * drawables, so call it off the main thread. Returns `null` when no icon exists
 * or it cannot be loaded.
 */
internal class NotificationPhotoExtractor(
    private val context: Context,
    private val reader: StatusBarNotificationReader,
) {
    fun extract(sbn: StatusBarNotification): ByteArray? {
        val notification = sbn.notification
        val icon: Icon = reader.latestMessage(notification.extras)?.senderPerson?.icon
            ?: notification.getLargeIcon()
            ?: return null
        return runCatching { encode(icon) }.getOrNull()
    }

    private fun encode(icon: Icon): ByteArray? {
        val drawable = icon.loadDrawable(context) ?: return null
        val side = PHOTO_SIDE_PX
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Centre-crop the drawable into the square.
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: side
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: side
        val scale = maxOf(side.toFloat() / w, side.toFloat() / h)
        val scaledW = (w * scale).toInt()
        val scaledH = (h * scale).toInt()
        val left = (side - scaledW) / 2
        val top = (side - scaledH) / 2
        drawable.setBounds(left, top, left + scaledW, top + scaledH)
        drawable.draw(canvas)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    companion object {
        const val PHOTO_SIDE_PX = 96
    }
}
