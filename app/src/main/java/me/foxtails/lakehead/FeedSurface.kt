package me.foxtails.lakehead

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Test tag for the feed page surface. */
const val FEED_SURFACE_TAG = "feed-surface"

/**
 * Host-boundary seam for the future Feed destination.
 *
 * Placeholder only: renders an honest "Feed unavailable" message with a transparent
 * background so the host wallpaper (owned by the system) shows through. The real feed
 * implementation will attach behind this seam without changing the pager shell.
 */
@Composable
internal fun FeedSurface(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(FEED_SURFACE_TAG),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = "Feed unavailable")
    }
}
