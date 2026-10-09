package me.foxtails.lakehead.data.database;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {HomeEntity.class, AppReferenceEntity.class, PlacementEntity.class,
                ShortcutBindingEntity.class, WidgetBindingEntity.class},
        version = DatabaseSchema.VERSION, exportSchema = true)
public abstract class PlacementDatabase extends RoomDatabase {
    public abstract PlacementDao placementDao();

    /** v1 → v2: adds the shortcut_bindings table for pinned shortcut persistence. */
    @NonNull
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `shortcut_bindings` ("
                    + "`tileId` TEXT NOT NULL, "
                    + "`pinnedShortcutIds` TEXT NOT NULL, "
                    + "PRIMARY KEY(`tileId`), "
                    + "FOREIGN KEY(`tileId`) REFERENCES `placements`(`itemId`) ON DELETE CASCADE"
                    + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shortcut_bindings_tileId`"
                    + " ON `shortcut_bindings` (`tileId`)");
        }
    };

    /** v2 → v3: adds widget_bindings (appWidgetId per widget placement); additive only. */
    @NonNull
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `widget_bindings` ("
                    + "`itemId` TEXT NOT NULL, "
                    + "`appWidgetId` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`itemId`), "
                    + "FOREIGN KEY(`itemId`) REFERENCES `placements`(`itemId`) ON DELETE CASCADE"
                    + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_widget_bindings_itemId`"
                    + " ON `widget_bindings` (`itemId`)");
        }
    };
}
