package me.foxtails.lakehead

import me.foxtails.lakehead.core.model.AppIdentity
import me.foxtails.lakehead.core.model.AppNotificationSummary
import me.foxtails.lakehead.core.model.ComponentIdentity
import me.foxtails.lakehead.core.model.ItemAvailability
import me.foxtails.lakehead.core.model.ItemId
import me.foxtails.lakehead.core.model.LogicalPosition
import me.foxtails.lakehead.core.model.LogicalSpan
import me.foxtails.lakehead.core.model.NotificationPreviewItem
import me.foxtails.lakehead.core.model.NotificationSourceKey
import me.foxtails.lakehead.core.model.ProfileId
import me.foxtails.lakehead.feature.home.HomeRenderTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTileJoinTest {
    private val personal = ProfileId("0")
    private val work = ProfileId("10")
    private val chat = ComponentIdentity("com.example.chat", "Main")

    private fun tile(id: String, profile: ProfileId, appWidgetId: Int? = null) = HomeRenderTile(
        itemId = ItemId(id),
        appIdentity = AppIdentity(profile, chat),
        position = LogicalPosition(0, 0),
        span = LogicalSpan(2, 2),
        availability = ItemAvailability.AVAILABLE,
        label = id,
        icon = null,
        isLaunchable = true,
        appWidgetId = appWidgetId,
    )

    private fun summary(count: Int, name: String) = AppNotificationSummary(
        count,
        listOf(NotificationPreviewItem(name, "body")),
    )

    @Test
    fun workProfileCopyIsNeverMergedWithPersonal() {
        val joined = joinNotificationsToTiles(
            tiles = listOf(tile("personal", personal), tile("work", work)),
            summaries = mapOf(
                NotificationSourceKey("com.example.chat", personal) to summary(3, "Ann"),
                NotificationSourceKey("com.example.chat", work) to summary(1, "Boss"),
            ),
            photos = emptyMap(),
        )

        assertEquals(3, joined.getValue(ItemId("personal")).count)
        assertEquals("Boss", joined.getValue(ItemId("work")).previews.single().name)
    }

    @Test
    fun widgetTilesAndTilesWithoutNotificationsGetNoEntry() {
        val joined = joinNotificationsToTiles(
            tiles = listOf(tile("widget", personal, appWidgetId = 7), tile("quiet", work)),
            summaries = mapOf(NotificationSourceKey("com.example.chat", personal) to summary(2, "Ann")),
            photos = emptyMap(),
        )

        assertNull(joined[ItemId("widget")])
        assertNull(joined[ItemId("quiet")])
    }
}
