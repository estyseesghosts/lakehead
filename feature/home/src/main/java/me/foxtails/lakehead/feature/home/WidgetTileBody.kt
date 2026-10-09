package me.foxtails.lakehead.feature.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag

/**
 * Slot `:app` fills with the hosted widget view. The feature never sees a View: it
 * passes the widget id, the tile's current size in dp, a long-press callback the
 * widget view raises while Home is idle, and the modifier that fills the tile.
 */
typealias HomeWidgetContent = @Composable (
    appWidgetId: Int,
    widthDp: Int,
    heightDp: Int,
    onLongPress: () -> Unit,
    modifier: Modifier,
) -> Unit

/** Empty slot used until a host supplies widgets. */
val NoHomeWidgetContent: HomeWidgetContent = { _, _, _, _, _ -> }

/**
 * Body of a widget tile. In normal mode the widget owns its touches; while editing a
 * transparent layer over it takes taps for selection and blocks the widget, so the
 * grid's unified move/resize handler and corner controls work as for app tiles.
 */
@Composable
internal fun WidgetTileBody(
    tile: HomeRenderTile,
    isEditing: Boolean,
    widgetContent: HomeWidgetContent,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appWidgetId = requireNotNull(tile.appWidgetId) { "not a widget tile" }
    BoxWithConstraints(modifier = modifier.fillMaxSize().testTag(HOME_WIDGET_TILE_TAG)) {
        val density = LocalDensity.current
        val widthDp = with(density) { constraints.maxWidth.toDp().value.toInt() }
        val heightDp = with(density) { constraints.maxHeight.toDp().value.toInt() }
        widgetContent(appWidgetId, widthDp, heightDp, onLongPress, Modifier.fillMaxSize())
        if (isEditing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(tile.itemId) { detectTapGestures(onTap = { onSelect() }) },
            )
        }
    }
}

/** Test tag on a widget tile body. */
internal const val HOME_WIDGET_TILE_TAG: String = "home-widget-tile"
