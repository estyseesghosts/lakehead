package me.foxtails.lakehead

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.lakehead.core.designsystem.NavigationControlButton
import me.foxtails.lakehead.core.designsystem.NavigationControlPill
import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.navigation.LauncherDestination
import me.foxtails.lakehead.feature.allapps.AllAppsScreen
import me.foxtails.lakehead.feature.allapps.AllAppsViewState
import me.foxtails.lakehead.feature.home.HomeCanvas
import me.foxtails.lakehead.feature.home.HomeCanvasViewportStyle
import me.foxtails.lakehead.feature.home.HomeRenderState

/** Minimum bottom padding applied to All Apps content so the pill never covers the last row. */
private val MIN_PILL_CLEARANCE = 80.dp

/**
 * Minimum bottom safety padding for the navigation pill when the system gesture inset
 * is zero or very small. Prevents the pill from sitting flush with the display edge on
 * devices / orientations where [WindowInsets.systemGestures] reports no bottom zone.
 */
private val MIN_PILL_BOTTOM_PADDING = 16.dp

/** Glyph characters for each navigation destination, in [LauncherDestination] enum order. */
private val DESTINATION_GLYPHS = mapOf(
    LauncherDestination.FEED to "F",
    LauncherDestination.NOTIFICATIONS to "N",
    LauncherDestination.HOME to "H",
    LauncherDestination.ALL_APPS to "A",
)

/**
 * Top-level navigation shell that hosts Home, All Apps, and stub destinations behind a
 * bottom-aligned [NavigationControlPill].
 *
 * Destination state is [rememberSaveable] so it survives configuration changes. The pill is
 * always visible and contains one [NavigationControlButton] per [LauncherDestination] in enum
 * order; stub destinations (Feed, Notifications) are rendered with reduced alpha and show
 * honest "not available yet" content when selected. All Apps content has bottom padding to
 * keep the last row above the pill.
 */
@Composable
internal fun LauncherShell(
    homeState: HomeRenderState,
    homeIcons: Map<AppIdentity, ImageBitmap>,
    allAppsState: AllAppsViewState,
    allAppsIcons: Map<AppIdentity, ImageBitmap>,
    onLaunchApp: (AppIdentity) -> Unit,
    onAddToHome: (AppIdentity) -> Unit,
    onRequestDefaultHome: () -> Unit,
    onMoveTile: (ItemId, LogicalPosition) -> Unit = { _, _ -> },
    onResizeTile: (ItemId, LogicalSpan) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    initialDestination: LauncherDestination = LauncherDestination.HOME,
    viewportStyle: HomeCanvasViewportStyle = HomeCanvasViewportStyle(),
) {
    var destination by rememberSaveable { mutableStateOf(initialDestination) }

    // Gesture inset drives the pill's bottom safety margin so it stays above
    // Android's system-gesture exclusion zone (immersive.md §125-166).
    val density = LocalDensity.current
    val gestureBottomDp = with(density) {
        WindowInsets.systemGestures.getBottom(density).toDp()
    }
    val pillBottomPadding = maxOf(MIN_PILL_BOTTOM_PADDING, gestureBottomDp)
    val pillClearancePadding = maxOf(MIN_PILL_CLEARANCE, gestureBottomDp)

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (destination) {
            LauncherDestination.HOME -> HomeCanvas(
                state = homeState,
                iconsByIdentity = homeIcons,
                onLaunchApp = onLaunchApp,
                onRequestDefaultHome = onRequestDefaultHome,
                modifier = Modifier.fillMaxSize(),
                viewportStyle = viewportStyle,
                onMoveTile = onMoveTile,
                onResizeTile = onResizeTile,
            )

            LauncherDestination.ALL_APPS -> AllAppsScreen(
                state = allAppsState,
                icons = allAppsIcons,
                onLaunchApp = onLaunchApp,
                onAddToHome = onAddToHome,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = pillClearancePadding),
            )

            LauncherDestination.FEED -> StubContent("Feed")
            LauncherDestination.NOTIFICATIONS -> StubContent("Notifications")
        }

        NavigationPillOverlay(
            currentDestination = destination,
            onDestinationSelected = { destination = it },
            gestureBottomPadding = pillBottomPadding,
        )
    }
}

@Composable
private fun BoxScope.StubContent(label: String) {
    BasicText(
        text = "$label is not available yet",
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        style = TextStyle(color = MaterialTheme.colorScheme.onBackground),
    )
}

@Composable
private fun BoxScope.NavigationPillOverlay(
    currentDestination: LauncherDestination,
    onDestinationSelected: (LauncherDestination) -> Unit,
    gestureBottomPadding: Dp,
) {
    NavigationControlPill(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = gestureBottomPadding),
    ) {
        LauncherDestination.entries.forEach { dest ->
            NavigationControlButton(
                selected = currentDestination == dest,
                enabled = !dest.isStub,
                contentDescription = when (dest) {
                    LauncherDestination.FEED -> "Feed (not available yet)"
                    LauncherDestination.NOTIFICATIONS -> "Notifications (not available yet)"
                    LauncherDestination.HOME -> "Home"
                    LauncherDestination.ALL_APPS -> "All Apps"
                },
                onPress = { onDestinationSelected(dest) },
            ) {
                BasicText(text = DESTINATION_GLYPHS.getValue(dest))
            }
        }
    }
}