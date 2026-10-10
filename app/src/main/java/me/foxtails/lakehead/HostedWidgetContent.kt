package me.foxtails.lakehead

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import me.foxtails.lakehead.core.designsystem.AppTileDefaults
import me.foxtails.lakehead.feature.home.HomeWidgetContent
import me.foxtails.lakehead.platform.widgets.AppWidgetHosting
import kotlin.math.max

/**
 * Fills the Home widget slot with the host's live view, or an "unavailable" placeholder
 * when the provider is gone. The size is reported to the provider once it settles, not on
 * every preview frame of a resize.
 *
 * Behind the widget the tile takes the colour of the widget's own surface when that surface is
 * smaller than the tile (see [WidgetBackgroundSampler]), so a widget that does not reach the
 * tile edges still reads as filling it. The widget is re-sampled, debounced, whenever it redraws.
 */
@OptIn(FlowPreview::class)
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
            var fill by remember(appWidgetId) { mutableStateOf<Color?>(null) }
            Box(modifier.background(fill ?: Color.Transparent, AppTileDefaults.TileShape)) {
                AndroidView(
                    factory = { view.also { (it.parent as? ViewGroup)?.removeView(it) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            LaunchedEffect(view, widthDp, heightDp) {
                delay(SIZE_SETTLE_MS)
                hosting.updateSize(view, widthDp, heightDp)
            }
            val redraws = remember(view) {
                MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            }
            DisposableEffect(view) {
                val listener = ViewTreeObserver.OnDrawListener { redraws.tryEmit(Unit) }
                view.viewTreeObserver.addOnDrawListener(listener)
                onDispose {
                    if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener)
                }
            }
            LaunchedEffect(view, widthDp, heightDp) {
                delay(SIZE_SETTLE_MS + SAMPLE_AFTER_SETTLE_MS)
                redraws.tryEmit(Unit)
            }
            LaunchedEffect(view) {
                redraws.debounce(SAMPLE_DEBOUNCE_MS).collect {
                    val sampled = sampleBackground(view)
                    fill = sampled?.let { Color(it) }
                }
            }
        }
    }

/**
 * Renders [view] at a small size and asks [WidgetBackgroundSampler] for a fill colour. The draw
 * must run on the main thread; the pixel analysis does not.
 */
internal suspend fun sampleBackground(view: View): Int? {
    if (view.width <= 0 || view.height <= 0) return null
    val scale = minOf(1f, SAMPLE_MAX_EDGE_PX.toFloat() / max(view.width, view.height))
    val w = max(1, (view.width * scale).toInt())
    val h = max(1, (view.height * scale).toInt())
    val bitmap = try {
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            canvas.scale(scale, scale)
            view.draw(canvas)
        }
    } catch (_: RuntimeException) {
        return null
    }
    return withContext(Dispatchers.Default) {
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        bitmap.recycle()
        WidgetBackgroundSampler.sample(pixels, w, h)
    }
}

private const val SIZE_SETTLE_MS = 150L
private const val SAMPLE_AFTER_SETTLE_MS = 400L
private const val SAMPLE_DEBOUNCE_MS = 500L
private const val SAMPLE_MAX_EDGE_PX = 96
