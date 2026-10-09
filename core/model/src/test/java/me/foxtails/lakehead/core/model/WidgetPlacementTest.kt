package me.foxtails.lakehead.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPlacementTest {
    private val provider = AppIdentity(ProfileId("0"), ComponentIdentity("com.example", "com.example.Clock"))

    private val widget = WidgetPlacement(
        homeId = HomeId("home"),
        itemId = ItemId("w1"),
        provider = provider,
        appWidgetId = 42,
        position = LogicalPosition(0, 1),
        span = LogicalSpan(4, 2),
        availability = ItemAvailability.AVAILABLE,
    )

    @Test
    fun typeIsWidget() {
        assertEquals(HomeItemType.WIDGET, widget.type)
    }

    @Test
    fun withGeometry_changesOnlyPositionAndSpan() {
        val moved = widget.withGeometry(LogicalPosition(1, 3), LogicalSpan(3, 1))
        assertEquals(widget.copy(position = LogicalPosition(1, 3), span = LogicalSpan(3, 1)), moved)
        assertEquals(42, moved.appWidgetId)
        assertEquals(provider, moved.provider)
    }

    @Test
    fun appPlacementWithGeometry_keepsIdentity() {
        val app = AppPlacement(
            HomeId("home"), ItemId("a"), provider, LogicalPosition(0, 0), LogicalSpan(1, 1),
            ItemAvailability.AVAILABLE,
        )
        val grown = app.withGeometry(LogicalPosition(0, 0), LogicalSpan(2, 2))
        assertEquals(provider, grown.appIdentity)
        assertEquals(LogicalSpan(2, 2), grown.span)
    }
}
