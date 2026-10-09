package me.foxtails.lakehead.platform.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import me.foxtails.lakehead.core.model.AppIdentity

/**
 * The launcher's one `AppWidgetHost`: allocates, binds and deletes widget ids,
 * creates host views and reports their size. Nothing here is persisted; the
 * caller stores only the provider identity and the integer `appWidgetId`.
 */
class AppWidgetHosting(context: Context) {
    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val host = object : AppWidgetHost(appContext, HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?,
        ): AppWidgetHostView = LongPressWidgetHostView(context)
    }

    fun startListening() = host.startListening()

    fun stopListening() = host.stopListening()

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(appWidgetId: Int) = host.deleteAppWidgetId(appWidgetId)

    /** True when the system still has a provider registered for [appWidgetId]. */
    fun isProviderAvailable(appWidgetId: Int): Boolean = manager.getAppWidgetInfo(appWidgetId) != null

    /** Binds without user interaction when the system allows it; false means consent is needed. */
    fun bindIfAllowed(appWidgetId: Int, provider: AppIdentity): Boolean {
        val user = userManager.getUserForSerialNumber(provider.profileId.value.toLong()) ?: return false
        return manager.bindAppWidgetIdIfAllowed(appWidgetId, user, provider.componentName(), null)
    }

    /** The system consent prompt for a bind the launcher is not allowed to do silently. */
    fun bindConsentIntent(appWidgetId: Int, provider: AppIdentity): Intent {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.componentName())
        userManager.getUserForSerialNumber(provider.profileId.value.toLong())?.let {
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, it)
        }
        return intent
    }

    fun needsConfigure(appWidgetId: Int): Boolean =
        manager.getAppWidgetInfo(appWidgetId)?.configure != null

    /** Launches the provider's configure activity through the host; the result returns to [activity]. */
    fun startConfigure(activity: Activity, appWidgetId: Int, requestCode: Int) {
        host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, requestCode, null)
    }

    /**
     * A live host view for [appWidgetId], or null when the provider is gone. [onLongPress]
     * fires when the user holds the widget without moving past touch slop.
     */
    fun createView(context: Context, appWidgetId: Int, onLongPress: () -> Unit): View? {
        val info = manager.getAppWidgetInfo(appWidgetId) ?: return null
        val view = host.createView(context, appWidgetId, info)
        (view as? LongPressWidgetHostView)?.onLongPress = onLongPress
        return view
    }

    /** Tells the provider the size its view now has, in dp. */
    fun updateSize(view: View, widthDp: Int, heightDp: Int) {
        (view as? AppWidgetHostView)?.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
    }

    private fun AppIdentity.componentName() = ComponentName(component.packageName, component.className)

    private companion object {
        const val HOST_ID = 0x4C48 // "LH"
    }
}

/**
 * Host view that watches for a long press without taking the touch from the widget:
 * it intercepts only once the hold fires, then cancels the child.
 */
internal class LongPressWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: () -> Unit = {}
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val fire = Runnable {
        fired = true
        onLongPress()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                fired = false
                postDelayed(fire, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (Math.hypot((ev.x - downX).toDouble(), (ev.y - downY).toDouble()) > slop) {
                    removeCallbacks(fire)
                }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(fire)
        }
        return fired
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean = fired || super.onTouchEvent(ev)
}
