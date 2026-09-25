package me.foxtails.lakehead.platform.launcherapps

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Classifies a platform icon drawable into the best available symbol source.
 *
 * Preferred order (per docs/grid.md §6):
 *  1. Monochrome layer (API 33+ adaptive, when the app supplies one)
 *  2. Adaptive foreground layer (API 26+)
 *  3. Legacy drawable fallback
 */
sealed interface AppSymbolSource {
    /** The resolved drawable to use as the symbol source. */
    val drawable: Drawable

    /** Monochrome layer from an adaptive icon (API 33+). */
    data class Monochrome(override val drawable: Drawable) : AppSymbolSource

    /** Foreground layer from an adaptive icon (API 26+). */
    data class AdaptiveForeground(override val drawable: Drawable) : AppSymbolSource

    /** Original drawable — not adaptive, or below API 26. */
    data class Legacy(override val drawable: Drawable) : AppSymbolSource
}

/**
 * Stateless resolver that maps a raw icon [Drawable] to the best [AppSymbolSource]
 * for the current device SDK level.
 *
 * Production callers use the two-parameter [resolve] which reads [Build.VERSION.SDK_INT]
 * and a production [readAdaptive] helper. Tests use the internal three-parameter seam to
 * inject a fake drawable-reader and explicit SDK level.
 */
class AppSymbolSourceResolver {

    /**
     * Resolves the best symbol source for the given [drawable] at the device's current SDK.
     */
    fun resolve(drawable: Drawable, sdkInt: Int = Build.VERSION.SDK_INT): AppSymbolSource =
        resolve(drawable, sdkInt, ::readAdaptiveLayers)

    /**
     * Testable seam: accepts an explicit [sdkInt] and a [readAdaptive] function that
     * extracts adaptive icon layers (foreground / monochrome) from a drawable.
     */
    internal fun resolve(
        drawable: Drawable,
        sdkInt: Int,
        readAdaptive: (Drawable, Int) -> AdaptiveLayers?,
    ): AppSymbolSource {
        val layers = readAdaptive(drawable, sdkInt)

        if (layers != null) {
            if (sdkInt >= 33) {
                val mono = layers.monochrome
                if (mono != null) return AppSymbolSource.Monochrome(mono)
                return AppSymbolSource.AdaptiveForeground(layers.foreground)
            }

            // SDK 26–32: adaptive foreground only
            return AppSymbolSource.AdaptiveForeground(layers.foreground)
        }

        return AppSymbolSource.Legacy(drawable)
    }
}

/**
 * Internal layer data returned by the production adaptive-icon reader.
 * Both fields may be null when the drawable is not adaptive or the layer is absent.
 */
internal data class AdaptiveLayers(
    val monochrome: Drawable?,
    val foreground: Drawable,
)

/**
 * Production adaptive-icon reader.  Returns [AdaptiveLayers] when [drawable] is an
 * [AdaptiveIconDrawable] on API 26+; returns `null` otherwise.
 *
 * Foreground is accessed via an API 26+ helper; monochrome via an API 33+ helper that
 * is only invoked when [sdkInt] >= 33.
 */
private fun readAdaptiveLayers(drawable: Drawable, sdkInt: Int): AdaptiveLayers? {
    if (sdkInt < 26 || drawable !is AdaptiveIconDrawable) return null
    return try {
        AdaptiveLayers(
            foreground = foregroundOf(drawable),
            monochrome = if (sdkInt >= 33) monochromeOf(drawable) else null,
        )
    } catch (_: Exception) {
        null
    }
}

/** Safe accessor for [AdaptiveIconDrawable.foreground] — call only when sdkInt ≥ 26. */
private fun foregroundOf(icon: AdaptiveIconDrawable): Drawable = icon.foreground

/** Safe accessor for [AdaptiveIconDrawable.getMonochrome] — call only when sdkInt ≥ 33. */
@Suppress("NewApi") // caller guards with sdkInt >= 33
private fun monochromeOf(icon: AdaptiveIconDrawable): Drawable? = icon.monochrome