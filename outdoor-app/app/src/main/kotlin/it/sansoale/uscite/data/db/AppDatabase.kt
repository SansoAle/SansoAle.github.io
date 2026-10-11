package it.sansoale.uscite.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TripEntity::class,
        MountainDetailEntity::class,
        HutEntity::class,
        VanDetailEntity::class,
        VanStageEntity::class,
        VanCostEntity::class,
        BikeDetailEntity::class,
        TrackEntity::class,
        PhotoEntity::class,
        TagEntity::class,
        TripTagCrossRef::class,
        CompanionEntity::class,
        TripCompanionCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "uscite.db"
    }
}
