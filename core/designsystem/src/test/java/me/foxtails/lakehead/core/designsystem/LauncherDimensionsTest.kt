package me.foxtails.lakehead.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherDimensionsTest {

    @Test
    fun defaultsAreEightEightEightForty() {
        assertEquals(8.dp, DefaultLauncherDimensions.gridGap)
        assertEquals(8.dp, DefaultLauncherDimensions.horizontalInset)
        assertEquals(8.dp, DefaultLauncherDimensions.tilePadding)
        assertEquals(40.dp, DefaultLauncherDimensions.symbolSize)
        assertEquals(40.dp, DefaultLauncherDimensions.unitIconSize)
    }

    @Test
    fun unitIconSizeEqualsSymbolSize() {
        assertEquals(DefaultLauncherDimensions.symbolSize, DefaultLauncherDimensions.unitIconSize)
    }

    @Test
    fun edgeEqualsGap() {
        assertEquals(DefaultLauncherDimensions.gridGap, DefaultLauncherDimensions.horizontalInset)
    }

    @Test
    fun copyPreservesUnchangedFields() {
        val custom = DefaultLauncherDimensions.copy(symbolSize = 48.dp)

        assertEquals(DefaultLauncherDimensions.gridGap, custom.gridGap)
        assertEquals(DefaultLauncherDimensions.horizontalInset, custom.horizontalInset)
        assertEquals(DefaultLauncherDimensions.tilePadding, custom.tilePadding)
        assertEquals(48.dp, custom.symbolSize)
        assertEquals(DefaultLauncherDimensions.unitIconSize, custom.unitIconSize)
    }
}
