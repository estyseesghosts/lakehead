package me.foxtails.lakehead.data.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "placements",
        foreignKeys = {
                @ForeignKey(entity = HomeEntity.class, parentColumns = "homeId",
                        childColumns = "homeId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = AppReferenceEntity.class,
                        parentColumns = {"profileId", "packageName", "className"},
                        childColumns = {"profileId", "packageName", "className"}),
                @ForeignKey(entity = PlacementEntity.class, parentColumns = "itemId",
                        childColumns = "parentFolderId", onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index("homeId"), @Index({"profileId", "packageName", "className"}),
                @Index("parentFolderId")}
)
public class PlacementEntity {
    @PrimaryKey @NonNull public final String itemId;
    @NonNull public final String homeId;
    @NonNull public final String itemType;
    /** App-reference columns are null only on FOLDER rows. */
    @Nullable public final String profileId;
    @Nullable public final String packageName;
    @Nullable public final String className;
    public final int column;
    public final int row;
    public final int columnSpan;
    public final int rowSpan;
    @NonNull public final String availability;
    /** The folder row this item sits in; null for top-level Home rows and for folders. */
    @Nullable public final String parentFolderId;

    @Ignore
    public PlacementEntity(@NonNull String itemId, @NonNull String homeId,
            @NonNull String itemType, @Nullable String profileId, @Nullable String packageName,
            @Nullable String className, int column, int row, int columnSpan, int rowSpan,
            @NonNull String availability) {
        this(itemId, homeId, itemType, profileId, packageName, className, column, row,
                columnSpan, rowSpan, availability, null);
    }

    public PlacementEntity(@NonNull String itemId, @NonNull String homeId,
            @NonNull String itemType, @Nullable String profileId, @Nullable String packageName,
            @Nullable String className, int column, int row, int columnSpan, int rowSpan,
            @NonNull String availability, @Nullable String parentFolderId) {
        this.itemId = itemId;
        this.homeId = homeId;
        this.itemType = itemType;
        this.profileId = profileId;
        this.packageName = packageName;
        this.className = className;
        this.column = column;
        this.row = row;
        this.columnSpan = columnSpan;
        this.rowSpan = rowSpan;
        this.availability = availability;
        this.parentFolderId = parentFolderId;
    }
}
