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

    @Query("SELECT * FROM placements WHERE homeId = :homeId ORDER BY rowid")
    List<PlacementEntity> placementsForHome(String homeId);

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
}
