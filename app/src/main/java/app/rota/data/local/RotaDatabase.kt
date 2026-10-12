package app.rota.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HabitEntity::class,
        RoutineItemEntity::class,
        CheckInEntity::class,
        SnoozeEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class RotaDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun routineDao(): RoutineDao
    abstract fun checkInDao(): CheckInDao
    abstract fun snoozeDao(): SnoozeDao
}
