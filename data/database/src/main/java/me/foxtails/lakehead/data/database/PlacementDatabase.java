package me.foxtails.lakehead.data.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/** D1 schema only. Callers must provide explicit migrations when this version advances. */
@Database(entities = {HomeEntity.class, AppReferenceEntity.class, PlacementEntity.class},
        version = DatabaseSchema.VERSION, exportSchema = true)
public abstract class PlacementDatabase extends RoomDatabase {
    public abstract PlacementDao placementDao();
}
