package me.foxtails.lakehead

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.NavigationControlButton
import me.foxtails.lakehead.core.designsystem.NavigationControlPill

/** Minimum bottom clearance reserved for the floating navigation pill, in [Dp]. */
val LauncherNavPillBottomClearance: Dp = 80.dp

private const val FEED_GLYPH = "F"
private const val HOME_GLYPH = "H"
private const val ALL_APPS_GLYPH = "A"

/**
 * Three-tab floating navigation pill: Feed, Home tile canvas, All Apps.
 *
 * Presentation only: [selectedPage] is caller-acquired (the launcher pager's current page)
 * and [onSelectPage] is a raw press signal; this composable holds no pager, scroll, or
 * saveable selection state. Tabs map to [LAUNCHER_PAGE_FEED], [LAUNCHER_PAGE_TILE], and
 * [LAUNCHER_PAGE_ALL_APPS] in order. [onHomeLongPress] is an optional raw long-press
 * signal carried by the middle (Home) button only (null keeps a plain click with no
 * long-click node; non-null exposes combined click + long-click); Feed and All Apps
 * expose no long-click action. The caller's [modifier] is expected to carry the
 * [androidx.compose.foundation.layout.BoxScope.align] placement; [bottomSafetyInset] only
 * lifts the pill above the system-gesture zone.
 */
@Composable
fun LauncherNavPill(
    selectedPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomSafetyInset: Dp = 0.dp,
    onHomeLongPress: (() -> Unit)? = null,
) {
    require(bottomSafetyInset.value >= 0f) {
        "bottomSafetyInset must be non-negative, was $bottomSafetyInset"
    }
    NavigationControlPill(
        modifier = modifier.padding(bottom = bottomSafetyInset),
    ) {
        NavigationControlButton(
            selected = selectedPage == LAUNCHER_PAGE_FEED,
            enabled = true,
            contentDescription = "Feed",
            onPress = { onSelectPage(LAUNCHER_PAGE_FEED) },
        ) {
            BasicText(text = FEED_GLYPH)
        }
        NavigationControlButton(
            selected = selectedPage == LAUNCHER_PAGE_TILE,
            enabled = true,
            contentDescription = "Home",
            onPress = { onSelectPage(LAUNCHER_PAGE_TILE) },
            onLongPress = onHomeLongPress,
        ) {
            BasicText(text = HOME_GLYPH)
        }
        NavigationControlButton(
            selected = selectedPage == LAUNCHER_PAGE_ALL_APPS,
            enabled = true,
            contentDescription = "All Apps",
            onPress = { onSelectPage(LAUNCHER_PAGE_ALL_APPS) },
        ) {
            BasicText(text = ALL_APPS_GLYPH)
        }
    }
}
