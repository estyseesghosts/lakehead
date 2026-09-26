package me.foxtails.lakehead

import android.graphics.Color
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import me.foxtails.lakehead.platform.feed.FeedProviderId

/**
 * Thin host container for one feed overlay provider (C2 seam; wired in C3).
 *
 * The provider draws its own content through the overlay service bound by the feed gateway — this
 * composable only reserves layout and hosts a transparent [View] so the wallpaper behind the feed
 * page stays visible. It owns no grid, gesture arbitration or availability logic: a null
 * [provider] (nothing to host) renders the same transparent [Box], and [FeedSurface] keeps the
 * honest fallback text for that case.
 */
@Composable
internal fun FeedOverlaySurface(
    provider: FeedProviderId?,
    modifier: Modifier = Modifier,
) {
    if (provider == null) {
        Box(modifier = modifier.fillMaxSize())
        return
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            View(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
            }
        },
    )
}
