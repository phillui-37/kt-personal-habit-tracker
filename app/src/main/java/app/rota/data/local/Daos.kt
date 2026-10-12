package app.rota.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Transaction
    @Query("SELECT * FROM habits ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<HabitWithRelations>>

    @Transaction
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<HabitWithRelations>>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<HabitWithRelations?>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitWithRelations?

    @Transaction
    @Query("SELECT * FROM habits")
    suspend fun getAll(): List<HabitWithRelations>

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE habits SET rotationIndex = :index WHERE id = :id")
    suspend fun setRotation(id: Long, index: Int)

    @Query("DELETE FROM habits")
    suspend fun deleteAll()
}

@Dao
interface RoutineDao {
    @Insert
    suspend fun insertAll(items: List<RoutineItemEntity>)

    @Query("DELETE FROM routine_items WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: Long)

    @Query("SELECT * FROM routine_items")
    suspend fun getAll(): List<RoutineItemEntity>
}

@Dao
interface CheckInDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkIn: CheckInEntity): Long

    @Query("SELECT * FROM check_ins WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun getOnDay(habitId: Long, epochDay: Long): CheckInEntity?

    @Query("DELETE FROM check_ins WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM check_ins")
    suspend fun getAll(): List<CheckInEntity>

    @Query("DELETE FROM check_ins")
    suspend fun deleteAll()
}

@Dao
interface SnoozeDao {
    @Insert
    suspend fun insert(snooze: SnoozeEntity): Long

    @Query("SELECT * FROM snoozes WHERE habitId = :habitId ORDER BY untilEpochMillis DESC LIMIT 1")
    suspend fun latestFor(habitId: Long): SnoozeEntity?

    @Query("DELETE FROM snoozes WHERE habitId = :habitId AND untilEpochMillis > :now")
    suspend fun clearActive(habitId: Long, now: Long)

    @Query("SELECT * FROM snoozes")
    suspend fun getAll(): List<SnoozeEntity>

    @Query("DELETE FROM snoozes")
    suspend fun deleteAll()
}
