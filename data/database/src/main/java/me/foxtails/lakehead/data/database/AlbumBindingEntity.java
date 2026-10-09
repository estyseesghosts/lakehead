package me.foxtails.lakehead.data.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * The photo album bound to a gallery app tile for its slideshow. Only the
 * {@code MediaStore} bucket id (the key) and the display name are persisted;
 * URIs, bitmaps and cursors are never stored.
 */
@Entity(
        tableName = "tile_album_bindings",
        foreignKeys = {
                @ForeignKey(entity = PlacementEntity.class, parentColumns = "itemId",
                        childColumns = "tileId", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("tileId")}
)
public class AlbumBindingEntity {
    @PrimaryKey @NonNull public final String tileId;
    @NonNull public final String bucketId;
    @NonNull public final String albumName;

    public AlbumBindingEntity(@NonNull String tileId, @NonNull String bucketId,
            @NonNull String albumName) {
        this.tileId = tileId;
        this.bucketId = bucketId;
        this.albumName = albumName;
    }
}
