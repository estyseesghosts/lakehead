package me.foxtails.lakehead.core.designsystem

import org.junit.Assert.assertNotEquals
import org.junit.Test

class LauncherThemeTest {

    @Test
    fun fallbackDarkDiffersFromLight() {
        assertNotEquals(LauncherFallbackLightColors, LauncherFallbackDarkColors)
    }

    @Test
    fun fallbackPrimaryDiffers() {
        assertNotEquals(
            LauncherFallbackLightColors.primary,
            LauncherFallbackDarkColors.primary,
        )
    }

    @Test
    fun fallbackSurfaceDiffers() {
        assertNotEquals(
            LauncherFallbackLightColors.surface,
            LauncherFallbackDarkColors.surface,
        )
    }

    @Test
    fun fallbackBackgroundDiffers() {
        assertNotEquals(
            LauncherFallbackLightColors.background,
            LauncherFallbackDarkColors.background,
        )
    }
}
