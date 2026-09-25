package me.foxtails.lakehead.data.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "homes")
public class HomeEntity {
    @PrimaryKey @NonNull public final String homeId;

    public HomeEntity(@NonNull String homeId) {
        this.homeId = homeId;
    }
}
