package me.foxtails.lakehead

import android.graphics.Color
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import me.foxtails.lakehead.platform.feed.FeedProviderId

/** Test tag for the hosted provider surface (present only when a provider is hosted). */
const val FEED_OVERLAY_HOST_TAG = "feed-overlay-host"

/**
 * Thin host container for one feed overlay provider (F3 :app surface).
 *
 * The provider draws its own content through the overlay service bound by the feed gateway — this
 * composable only reserves full-bleed layout and hosts a transparent [View] so the provider
 * window behind/above the feed page stays visible. It owns no grid, gesture arbitration,
 * availability or lifecycle logic: a null [provider] (nothing to host) renders the same
 * transparent [Box], and [FeedSurface] keeps the honest fallback text for that case. The
 * [FEED_OVERLAY_HOST_TAG] marks the hosted surface for device verification; progress and
 * visibility driving belongs to the `LauncherShell` feed callbacks, never to this host.
 *
 * Window handshake: [onWindowAttached] fires when the hosted view attaches (the production
 * caller registers the host window with the bound overlay through the gateway, which is
 * what lets the provider render pixels), and [onWindowDetached] fires when the hosted
 * view releases (the caller unregisters). Both default to no-ops so previews and tests
 * that do not wire a gateway keep the prior behavior. This host passes no `Activity`,
 * `LayoutParams` or service objects itself — framework types stay behind the gateway's
 * platform boundary.
 */
@Composable
internal fun FeedOverlaySurface(
    provider: FeedProviderId?,
    modifier: Modifier = Modifier,
    onWindowAttached: () -> Unit = {},
    onWindowDetached: () -> Unit = {},
) {
    if (provider == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }
    // One registration per hosted provider identity: LaunchedEffect keyed on the
    // provider fires once per host/switch, never per recomposition, so pager
    // scroll frames (which recompose this page continuously for parallax) never
    // re-register the window. Release unregisters when the hosted view leaves.
    LaunchedEffect(provider) { onWindowAttached() }
    AndroidView(
        modifier = modifier.fillMaxSize().testTag(FEED_OVERLAY_HOST_TAG),
        factory = { context ->
            View(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
            }
        },
        onRelease = { onWindowDetached() },
    )
}
