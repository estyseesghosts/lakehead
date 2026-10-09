package me.foxtails.lakehead

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import me.foxtails.lakehead.feature.home.HomeWidgetContent
import me.foxtails.lakehead.platform.widgets.AppWidgetHosting

/**
 * Fills the Home widget slot with the host's live view, or an "unavailable" placeholder
 * when the provider is gone. The size is reported to the provider once it settles, not on
 * every preview frame of a resize.
 */
internal fun hostedWidgetContent(hosting: AppWidgetHosting): HomeWidgetContent =
    { appWidgetId, widthDp, heightDp, onLongPress, modifier ->
        val context = LocalContext.current
        val latestLongPress = rememberUpdatedState(onLongPress)
        val view = remember(appWidgetId) {
            hosting.createView(context, appWidgetId) { latestLongPress.value() }
        }
        if (view == null) {
            Box(modifier, contentAlignment = Alignment.Center) { BasicText("Widget unavailable") }
        } else {
            AndroidView(
                factory = { view.also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = modifier,
            )
            LaunchedEffect(view, widthDp, heightDp) {
                delay(SIZE_SETTLE_MS)
                hosting.updateSize(view, widthDp, heightDp)
            }
        }
    }

private const val SIZE_SETTLE_MS = 150L
