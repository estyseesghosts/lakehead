package me.foxtails.lakehead.data.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "placements",
        foreignKeys = {
                @ForeignKey(entity = HomeEntity.class, parentColumns = "homeId",
                        childColumns = "homeId", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = AppReferenceEntity.class,
                        parentColumns = {"profileId", "packageName", "className"},
                        childColumns = {"profileId", "packageName", "className"})
        },
        indices = {@Index("homeId"), @Index({"profileId", "packageName", "className"})}
)
public class PlacementEntity {
    @PrimaryKey @NonNull public final String itemId;
    @NonNull public final String homeId;
    @NonNull public final String itemType;
    @NonNull public final String profileId;
    @NonNull public final String packageName;
    @NonNull public final String className;
    public final int column;
    public final int row;
    public final int columnSpan;
    public final int rowSpan;
    @NonNull public final String availability;

    public PlacementEntity(@NonNull String itemId, @NonNull String homeId,
            @NonNull String itemType, @NonNull String profileId, @NonNull String packageName,
            @NonNull String className, int column, int row, int columnSpan, int rowSpan,
            @NonNull String availability) {
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
    }
}
