package me.foxtails.lakehead.data.database;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {HomeEntity.class, AppReferenceEntity.class, PlacementEntity.class,
                ShortcutBindingEntity.class, WidgetBindingEntity.class, AlbumBindingEntity.class},
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

    /** v3 → v4: adds tile_album_bindings (bucket id and name per gallery tile); additive only. */
    @NonNull
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `tile_album_bindings` ("
                    + "`tileId` TEXT NOT NULL, "
                    + "`bucketId` TEXT NOT NULL, "
                    + "`albumName` TEXT NOT NULL, "
                    + "PRIMARY KEY(`tileId`), "
                    + "FOREIGN KEY(`tileId`) REFERENCES `placements`(`itemId`) ON DELETE CASCADE"
                    + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tile_album_bindings_tileId`"
                    + " ON `tile_album_bindings` (`tileId`)");
        }
    };

    /**
     * v4 → v5: rebuilds placements with nullable app-reference columns (folders have no app)
     * and a nullable parentFolderId (cascade to the folder row). Every existing row is copied
     * with parentFolderId = NULL. Child tables are saved and restored around the drop so the
     * result does not depend on whether foreign-key actions are enforced during migration.
     */
    @NonNull
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TEMP TABLE `tmp_shortcut_bindings` AS SELECT * FROM `shortcut_bindings`");
            db.execSQL("CREATE TEMP TABLE `tmp_widget_bindings` AS SELECT * FROM `widget_bindings`");
            db.execSQL("CREATE TEMP TABLE `tmp_tile_album_bindings` AS SELECT * FROM `tile_album_bindings`");
            db.execSQL("CREATE TABLE `placements_new` ("
                    + "`itemId` TEXT NOT NULL, `homeId` TEXT NOT NULL, `itemType` TEXT NOT NULL, "
                    + "`profileId` TEXT, `packageName` TEXT, `className` TEXT, "
                    + "`column` INTEGER NOT NULL, `row` INTEGER NOT NULL, "
                    + "`columnSpan` INTEGER NOT NULL, `rowSpan` INTEGER NOT NULL, "
                    + "`availability` TEXT NOT NULL, `parentFolderId` TEXT, "
                    + "PRIMARY KEY(`itemId`), "
                    + "FOREIGN KEY(`homeId`) REFERENCES `homes`(`homeId`) ON UPDATE NO ACTION ON DELETE CASCADE, "
                    + "FOREIGN KEY(`profileId`, `packageName`, `className`) "
                    + "REFERENCES `app_references`(`profileId`, `packageName`, `className`) "
                    + "ON UPDATE NO ACTION ON DELETE NO ACTION, "
                    + "FOREIGN KEY(`parentFolderId`) REFERENCES `placements`(`itemId`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("INSERT INTO `placements_new` (`itemId`, `homeId`, `itemType`, `profileId`, "
                    + "`packageName`, `className`, `column`, `row`, `columnSpan`, `rowSpan`, "
                    + "`availability`, `parentFolderId`) "
                    + "SELECT `itemId`, `homeId`, `itemType`, `profileId`, `packageName`, `className`, "
                    + "`column`, `row`, `columnSpan`, `rowSpan`, `availability`, NULL FROM `placements` ORDER BY rowid");
            db.execSQL("DROP TABLE `placements`");
            db.execSQL("ALTER TABLE `placements_new` RENAME TO `placements`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_placements_homeId` ON `placements` (`homeId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_placements_profileId_packageName_className`"
                    + " ON `placements` (`profileId`, `packageName`, `className`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_placements_parentFolderId`"
                    + " ON `placements` (`parentFolderId`)");
            db.execSQL("INSERT OR IGNORE INTO `shortcut_bindings` SELECT * FROM `tmp_shortcut_bindings`");
            db.execSQL("INSERT OR IGNORE INTO `widget_bindings` SELECT * FROM `tmp_widget_bindings`");
            db.execSQL("INSERT OR IGNORE INTO `tile_album_bindings` SELECT * FROM `tmp_tile_album_bindings`");
            db.execSQL("DROP TABLE `tmp_shortcut_bindings`");
            db.execSQL("DROP TABLE `tmp_widget_bindings`");
            db.execSQL("DROP TABLE `tmp_tile_album_bindings`");
        }
    };
}
