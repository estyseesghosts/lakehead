package me.foxtails.lakehead.data.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * The host-allocated {@code appWidgetId} bound to a widget placement. The provider
 * component and profile live in the placement's reference columns.
 */
@Entity(
        tableName = "widget_bindings",
        foreignKeys = {
                @ForeignKey(entity = PlacementEntity.class, parentColumns = "itemId",
                        childColumns = "itemId", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("itemId")}
)
public class WidgetBindingEntity {
    @PrimaryKey @NonNull public final String itemId;
    public final int appWidgetId;

    public WidgetBindingEntity(@NonNull String itemId, int appWidgetId) {
        this.itemId = itemId;
        this.appWidgetId = appWidgetId;
    }
}
