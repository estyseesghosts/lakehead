package me.foxtails.lakehead.platform.feed

import android.content.res.Configuration
import android.view.WindowManager

/**
 * Window-handshake verbs for one bound feed overlay service.
 *
 * The Launcher3 overlay protocol renders nothing until the host registers its window:
 * the provider needs the host's [WindowManager.LayoutParams] (size, token) plus the
 * single [AidlFeedBridge.callback] identity before it draws any pixels. Control verbs
 * (`openOverlay`/`onScroll` via [FeedOverlayConnection]) only parallax or show content
 * the provider attached through this handshake, so binding without `windowAttached`
 * leaves a transparent page even when the service is connected.
 *
 * This seam owns only the per-connection window verbs so the gateway can forward the
 * handshake without leaking AIDL stubs. There are deliberately `android.view.*`
 * imports in this file: unlike [FeedOverlayConnection] (framework-free control verbs
 * for composables, domain code and JVM tests), the window handshake always carries a
 * real `LayoutParams`, so it stays behind the platform boundary — the gateway takes an
 * `Activity`, builds the params, and forwards them here. JVM tests drive the gateway's
 * params-copy seam with a fake instead of constructing framework objects.
 *
 * Every method is idempotent and never throws: repeating `windowAttached` with the
 * latest params re-registers, calling after [FeedOverlayConnection.close] is a silent
 * no-op, and a dead transport degrades to the gateway's honest `Unavailable` verdict
 * instead of an exception.
 */
internal interface FeedOverlayWindowConnection {

    /**
     * Registers the host window with the bound overlay service.
     *
     * Forwards `params` with this connection's single callback identity and [flags].
     * Repeating the call re-registers the latest params; never throws, including when
     * unbound or after close.
     */
    fun windowAttached(params: WindowManager.LayoutParams, flags: Int)

    /**
     * Supplies the host configuration carried by the next `windowAttached2` bundle.
     * Must precede [windowAttached]; the default ignores it (older fakes).
     */
    fun windowConfiguration(configuration: Configuration?) = Unit

    /**
     * Unregisters the host window from the bound overlay service.
     *
     * Forwards `windowDetached(isChangingConfigurations)`; clears any pending attach so
     * a later re-attach starts clean. Never throws, including when unbound or after
     * close.
     */
    fun windowDetached(isChangingConfigurations: Boolean)
}
