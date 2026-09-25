package me.foxtails.lakehead.data.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/** Persisted identity only; an app need not currently be installed to remain referenced. */
@Entity(tableName = "app_references", primaryKeys = {"profileId", "packageName", "className"})
public class AppReferenceEntity {
    @NonNull public final String profileId;
    @NonNull public final String packageName;
    @NonNull public final String className;

    public AppReferenceEntity(@NonNull String profileId, @NonNull String packageName,
            @NonNull String className) {
        this.profileId = profileId;
        this.packageName = packageName;
        this.className = className;
    }
}
