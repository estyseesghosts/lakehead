package me.foxtails.lakehead.data.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface PlacementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertHome(HomeEntity home);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertAppReference(AppReferenceEntity appReference);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertPlacement(PlacementEntity placement);

    @Query("SELECT * FROM homes WHERE homeId = :homeId")
    HomeEntity home(String homeId);

    @Query("SELECT * FROM app_references WHERE profileId = :profileId AND packageName = :packageName AND className = :className")
    AppReferenceEntity appReference(String profileId, String packageName, String className);

    @Query("SELECT * FROM placements WHERE itemId = :itemId")
    PlacementEntity placement(String itemId);

    @Query("SELECT * FROM placements ORDER BY itemId")
    List<PlacementEntity> placements();

    /** Home occupancy: top-level rows only. Folder members are never Home occupancy. */
    @Query("SELECT * FROM placements WHERE homeId = :homeId AND parentFolderId IS NULL ORDER BY rowid")
    List<PlacementEntity> topLevelPlacements(String homeId);

    /** The interior of one folder: rows whose parent is [folderId]. */
    @Query("SELECT * FROM placements WHERE homeId = :homeId AND parentFolderId = :folderId ORDER BY rowid")
    List<PlacementEntity> folderMembers(String homeId, String folderId);

    /** Every folder member of a Home, for the closed folder tiles' previews. */
    @Query("SELECT * FROM placements WHERE homeId = :homeId AND parentFolderId IS NOT NULL ORDER BY rowid")
    List<PlacementEntity> membersForHome(String homeId);

    @Query("SELECT COUNT(*) FROM placements WHERE parentFolderId = :folderId")
    int folderMemberCount(String folderId);

    @Query("UPDATE placements SET parentFolderId = :parentFolderId, `column` = :column, `row` = :row "
            + "WHERE itemId = :itemId")
    int reparentPlacement(String itemId, String parentFolderId, int column, int row);

    /** Folders only, for the "Add to folder" chooser. */
    @Query("SELECT * FROM placements WHERE homeId = :homeId AND itemType = 'FOLDER' ORDER BY rowid")
    List<PlacementEntity> folders(String homeId);

    @Query("SELECT wb.appWidgetId FROM widget_bindings wb INNER JOIN placements p ON p.itemId = wb.itemId "
            + "WHERE p.parentFolderId = :folderId")
    List<Integer> folderMemberWidgetIds(String folderId);

    @Query("UPDATE placements SET itemId = :itemId, homeId = :homeId, itemType = :itemType, " +
            "profileId = :profileId, packageName = :packageName, className = :className, " +
            "`column` = :column, `row` = :row, columnSpan = :columnSpan, rowSpan = :rowSpan, " +
            "availability = :availability WHERE itemId = :replacingId")
    int replacePlacement(String replacingId, String itemId, String homeId, String itemType,
            String profileId, String packageName, String className, int column, int row,
            int columnSpan, int rowSpan, String availability);

    @Update
    int updatePlacement(PlacementEntity placement);

    @Query("DELETE FROM placements WHERE itemId = :itemId")
    int deletePlacement(String itemId);

    @Query("UPDATE placements SET availability = :availability WHERE itemId = :itemId")
    int updateAvailability(String itemId, String availability);

    @Query("SELECT COUNT(*) FROM homes")
    int homeCount();

    @Query("SELECT COUNT(*) FROM app_references")
    int appReferenceCount();

    @Query("SELECT COUNT(*) FROM placements")
    int placementCount();

    // --- Shortcut binding operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertShortcutBinding(ShortcutBindingEntity binding);

    @Query("SELECT * FROM shortcut_bindings WHERE tileId = :tileId")
    ShortcutBindingEntity shortcutBinding(String tileId);

    @Query("DELETE FROM shortcut_bindings WHERE tileId = :tileId")
    int deleteShortcutBinding(String tileId);

    @Query("SELECT COUNT(*) FROM shortcut_bindings")
    int shortcutBindingCount();

    // --- Widget binding operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertWidgetBinding(WidgetBindingEntity binding);

    @Query("SELECT * FROM widget_bindings WHERE itemId = :itemId")
    WidgetBindingEntity widgetBinding(String itemId);

    @Query("SELECT wb.* FROM widget_bindings wb INNER JOIN placements p ON p.itemId = wb.itemId "
            + "WHERE p.homeId = :homeId")
    List<WidgetBindingEntity> widgetBindingsForHome(String homeId);

    @Query("SELECT appWidgetId FROM widget_bindings")
    List<Integer> allAppWidgetIds();

    @Query("DELETE FROM widget_bindings WHERE itemId = :itemId")
    int deleteWidgetBinding(String itemId);

    // --- Album binding operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAlbumBinding(AlbumBindingEntity binding);

    @Query("SELECT * FROM tile_album_bindings WHERE tileId = :tileId")
    AlbumBindingEntity albumBinding(String tileId);

    @Query("DELETE FROM tile_album_bindings WHERE tileId = :tileId")
    int deleteAlbumBinding(String tileId);

    @Query("SELECT COUNT(*) FROM tile_album_bindings")
    int albumBindingCount();
}
