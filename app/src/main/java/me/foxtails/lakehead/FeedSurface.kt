package me.foxtails.lakehead

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.foxtails.lakehead.platform.feed.FeedAvailability

/** Test tag for the feed page surface. */
const val FEED_SURFACE_TAG = "feed-surface"

/**
 * Host-boundary seam for the Feed destination.
 *
 * When [availability] is [FeedAvailability.Available] the page hosts the real provider surface
 * via [FeedOverlaySurface]; otherwise it renders an honest "Feed unavailable" message. The
 * background stays transparent in both cases so the host wallpaper (owned by the system) shows
 * through. This seam owns no grid, gesture arbitration or lifecycle logic.
 *
 * Window handshake: [onWindowAttached]/[onWindowDetached] pass through to the hosted
 * [FeedOverlaySurface] (production registers/unregisters the host window with the bound
 * overlay through the gateway). Both default to no-ops so previews and tests that do not
 * wire a gateway keep the prior behavior.
 */
@Composable
internal fun FeedSurface(
    availability: FeedAvailability,
    modifier: Modifier = Modifier,
    onWindowAttached: () -> Unit = {},
    onWindowDetached: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(FEED_SURFACE_TAG),
        contentAlignment = Alignment.Center,
    ) {
        when (availability) {
            is FeedAvailability.Available -> FeedOverlaySurface(
                provider = availability.provider,
                onWindowAttached = onWindowAttached,
                onWindowDetached = onWindowDetached,
            )
            is FeedAvailability.Unavailable -> BasicText(
                text = "Feed unavailable",
                style = LocalTextStyle.current.merge(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
