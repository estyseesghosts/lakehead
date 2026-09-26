package me.foxtails.lakehead.data.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Persists the ordered list of pinned shortcut IDs for a single tile.
 *
 * {@code pinnedShortcutIds} is a JSON array of objects, each containing
 * {@code packageName}, {@code profileId} and {@code shortcutId}.
 * Insertion order is preserved because display order is a presentation concern.
 */
@Entity(
        tableName = "shortcut_bindings",
        foreignKeys = {
                @ForeignKey(entity = PlacementEntity.class, parentColumns = "itemId",
                        childColumns = "tileId", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("tileId")}
)
public class ShortcutBindingEntity {
    @PrimaryKey @NonNull public final String tileId;
    @NonNull public final String pinnedShortcutIds;

    public ShortcutBindingEntity(@NonNull String tileId, @NonNull String pinnedShortcutIds) {
        this.tileId = tileId;
        this.pinnedShortcutIds = pinnedShortcutIds;
    }
}