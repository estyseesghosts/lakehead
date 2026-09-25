package me.foxtails.lakehead.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Fallback colour palettes for the launcher theme.
 *
 * See `docs/grid.md` section 12: colour-only contract, no typography or shapes.
 * Dynamic colour is preferred on API 31+; these defaults cover older platforms
 * and contexts where dynamic colour is unavailable.
 */
internal val LauncherFallbackDarkColors = darkColorScheme()

/**
 * Fallback light palette. See `docs/grid.md` section 12: colour-only, no
 * typography/shapes.
 */
internal val LauncherFallbackLightColors = lightColorScheme()

/**
 * Launcher theme entry point: colour-only (see `docs/grid.md` section 12).
 * Uses dynamic colour on Android S+ and falls back to the default M3
 * dark/light palettes otherwise. No typography or shape overrides.
 */
@Composable
fun LauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) LauncherFallbackDarkColors else LauncherFallbackLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
