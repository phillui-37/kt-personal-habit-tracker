package app.rota.data.repo

import androidx.room.withTransaction
import app.rota.data.local.CheckInDao
import app.rota.data.local.CheckInEntity
import app.rota.data.local.HabitDao
import app.rota.data.local.HabitEntity
import app.rota.data.local.HabitWithRelations
import app.rota.data.local.RotaDatabase
import app.rota.data.local.RoutineDao
import app.rota.data.local.RoutineItemEntity
import app.rota.data.local.SnoozeDao
import app.rota.data.local.SnoozeEntity
import app.rota.domain.DueCalculator
import app.rota.domain.Progress
import app.rota.domain.StreakCalculator
import app.rota.domain.backup.BackupPayload
import app.rota.domain.backup.CheckInBackup
import app.rota.domain.backup.HabitBackup
import app.rota.domain.backup.RoutineBackup
import app.rota.domain.backup.SnoozeBackup
import app.rota.domain.model.CheckIn
import app.rota.domain.model.CheckInStatus
import app.rota.domain.model.Habit
import app.rota.domain.model.HabitBundle
import app.rota.domain.model.HabitType
import app.rota.domain.model.TodayItem
import app.rota.domain.model.TodayStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor(
    private val db: RotaDatabase,
    private val habitDao: HabitDao,
    private val routineDao: RoutineDao,
    private val checkInDao: CheckInDao,
    private val snoozeDao: SnoozeDao,
) {
    fun observeBundles(includeArchived: Boolean = false): Flow<List<HabitBundle>> {
        val source = if (includeArchived) habitDao.observeAll() else habitDao.observeActive()
        return source.map { rows -> rows.map { it.toBundle() } }
    }

    fun observeBundle(id: Long): Flow<HabitBundle?> =
        habitDao.observeById(id).map { it?.toBundle() }

    fun observeToday(date: LocalDate = LocalDate.now()): Flow<List<TodayItem>> =
        observeBundles().map { bundles ->
            bundles.map { bundle -> toTodayItem(bundle, date) }
                .filter { it.status != TodayStatus.REST }
                .sortedWith(
                    compareBy(
                        { it.status.ordinal },
                        { it.habit.sortOrder },
                        { it.habit.name },
                    ),
                )
        }

    suspend fun getBundle(id: Long): HabitBundle? = habitDao.getById(id)?.toBundle()

    suspend fun saveHabit(habit: Habit, routineTitles: List<String>): Long {
        return db.withTransaction {
            val created = if (habit.id == 0L) LocalDate.now().toEpochDay() else habit.createdAtEpochDay
            val entity = HabitEntity.from(
                habit.copy(
                    createdAtEpochDay = created,
                    target = when (habit.type) {
                        HabitType.CHECK -> 1
                        else -> habit.target.coerceAtLeast(1)
                    },
                ),
            )
            val id = if (habit.id == 0L) {
                habitDao.insert(entity.copy(id = 0))
            } else {
                habitDao.update(entity)
                habit.id
            }
            routineDao.deleteForHabit(id)
            val items = routineTitles.map { it.trim() }.filter { it.isNotEmpty() }
            if (items.isNotEmpty()) {
                routineDao.insertAll(
                    items.mapIndexed { index, title ->
                        RoutineItemEntity(habitId = id, title = title, sortOrder = index)
                    },
                )
            }
            id
        }
    }

    suspend fun archive(id: Long, archived: Boolean) {
        val current = habitDao.getById(id)?.habit ?: return
        habitDao.update(current.copy(archived = archived))
    }

    suspend fun deleteHabit(id: Long) {
        habitDao.delete(id)
    }

    suspend fun logValue(habitId: Long, newValue: Int, date: LocalDate = LocalDate.now()) {
        mutateProgress(habitId, date) { habit, existing, routineId, routineLabel ->
            Progress.setValue(habit, existing, newValue, date.toEpochDay(), routineId, routineLabel)
        }
    }

    suspend fun addValue(habitId: Long, delta: Int, date: LocalDate = LocalDate.now()) {
        val bundle = getBundle(habitId) ?: return
        val existing = bundle.checkIns.firstOrNull { it.epochDay == date.toEpochDay() }
        logValue(habitId, (existing?.value ?: 0) + delta, date)
    }

    suspend fun toggleCheck(habitId: Long, date: LocalDate = LocalDate.now()) {
        val bundle = getBundle(habitId) ?: return
        val existing = bundle.checkIns.firstOrNull { it.epochDay == date.toEpochDay() }
        val complete = existing?.status != CheckInStatus.DONE
        logValue(habitId, if (complete) bundle.habit.target else 0, date)
    }

    suspend fun skip(habitId: Long, note: String = "", date: LocalDate = LocalDate.now()) {
        mutateProgress(habitId, date) { habit, existing, routineId, routineLabel ->
            Progress.skip(habit, existing, date.toEpochDay(), routineId, routineLabel, note)
        }
        snoozeDao.clearActive(habitId, System.currentTimeMillis())
    }

    suspend fun snooze(habitId: Long, untilEpochMillis: Long, reason: String) {
        snoozeDao.insert(
            SnoozeEntity(
                habitId = habitId,
                untilEpochMillis = untilEpochMillis,
                reason = reason.trim(),
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearSnooze(habitId: Long) {
        snoozeDao.clearActive(habitId, System.currentTimeMillis())
    }

    suspend fun exportBackup(): BackupPayload {
        val habits = habitDao.getAll()
        return BackupPayload(
            version = 1,
            exportedAtEpochMillis = System.currentTimeMillis(),
            habits = habits.map { row ->
                val h = row.habit
                HabitBackup(
                    id = h.id,
                    name = h.name,
                    note = h.note,
                    type = h.type,
                    target = h.target,
                    period = h.period,
                    timesPerPeriod = h.timesPerPeriod,
                    intervalDays = h.intervalDays,
                    weekdaysMask = h.weekdaysMask,
                    reminderMinutes = h.reminderMinutes,
                    colorIndex = h.colorIndex,
                    archived = h.archived,
                    rotationIndex = h.rotationIndex,
                    createdAtEpochDay = h.createdAtEpochDay,
                    sortOrder = h.sortOrder,
                )
            },
            routineItems = habits.flatMap { row ->
                row.routine.map { RoutineBackup(it.id, it.habitId, it.title, it.sortOrder) }
            },
            checkIns = habits.flatMap { row ->
                row.checkIns.map {
                    CheckInBackup(
                        id = it.id,
                        habitId = it.habitId,
                        epochDay = it.epochDay,
                        status = it.status,
                        value = it.value,
                        note = it.note,
                        routineItemId = it.routineItemId,
                        routineLabel = it.routineLabel,
                    )
                }
            },
            snoozes = habits.flatMap { row ->
                row.snoozes.map {
                    SnoozeBackup(
                        id = it.id,
                        habitId = it.habitId,
                        untilEpochMillis = it.untilEpochMillis,
                        reason = it.reason,
                        createdAtEpochMillis = it.createdAtEpochMillis,
                    )
                }
            },
        )
    }

    suspend fun restoreBackup(payload: BackupPayload) {
        require(payload.version == 1) { "Unsupported backup version ${payload.version}" }
        db.withTransaction {
            habitDao.deleteAll()
            payload.habits.forEach { habit ->
                habitDao.insert(
                    HabitEntity(
                        id = habit.id,
                        name = habit.name,
                        note = habit.note,
                        type = habit.type,
                        target = habit.target,
                        period = habit.period,
                        timesPerPeriod = habit.timesPerPeriod,
                        intervalDays = habit.intervalDays,
                        weekdaysMask = habit.weekdaysMask,
                        reminderMinutes = habit.reminderMinutes,
                        colorIndex = habit.colorIndex,
                        archived = habit.archived,
                        rotationIndex = habit.rotationIndex,
                        createdAtEpochDay = habit.createdAtEpochDay,
                        sortOrder = habit.sortOrder,
                    ),
                )
            }
            if (payload.routineItems.isNotEmpty()) {
                routineDao.insertAll(
                    payload.routineItems.map {
                        RoutineItemEntity(it.id, it.habitId, it.title, it.sortOrder)
                    },
                )
            }
            payload.checkIns.forEach {
                checkInDao.upsert(
                    CheckInEntity(
                        id = it.id,
                        habitId = it.habitId,
                        epochDay = it.epochDay,
                        status = it.status,
                        value = it.value,
                        note = it.note,
                        routineItemId = it.routineItemId,
                        routineLabel = it.routineLabel,
                    ),
                )
            }
            payload.snoozes.forEach {
                snoozeDao.insert(
                    SnoozeEntity(
                        id = it.id,
                        habitId = it.habitId,
                        untilEpochMillis = it.untilEpochMillis,
                        reason = it.reason,
                        createdAtEpochMillis = it.createdAtEpochMillis,
                    ),
                )
            }
        }
    }

    private suspend fun mutateProgress(
        habitId: Long,
        date: LocalDate,
        mutate: (Habit, CheckIn?, Long?, String?) -> app.rota.domain.ProgressChange,
    ) {
        val bundle = getBundle(habitId) ?: return
        val existing = bundle.checkIns.firstOrNull { it.epochDay == date.toEpochDay() }
        val routine = bundle.currentRoutine
        val change = mutate(bundle.habit, existing, routine?.id, routine?.title)
        if (change.delete && change.checkIn.id != 0L) {
            checkInDao.delete(change.checkIn.id)
        } else if (!change.delete) {
            checkInDao.upsert(CheckInEntity.from(change.checkIn))
        }
        if (change.rotationDelta != 0 && bundle.routine.isNotEmpty()) {
            val next = if (change.rotationDelta > 0) {
                DueCalculator.nextRotationIndex(bundle.habit.rotationIndex, bundle.routine.size)
            } else {
                DueCalculator.previousRotationIndex(bundle.habit.rotationIndex, bundle.routine.size)
            }
            habitDao.setRotation(habitId, next)
        }
        snoozeDao.clearActive(habitId, System.currentTimeMillis())
    }

    private fun HabitWithRelations.toBundle(): HabitBundle = toDomain(System.currentTimeMillis())

    private fun toTodayItem(bundle: HabitBundle, date: LocalDate): TodayItem {
        val status = DueCalculator.todayStatus(
            habit = bundle.habit,
            date = date,
            checkIns = bundle.checkIns,
            snooze = bundle.snooze,
            nowMillis = System.currentTimeMillis(),
        )
        val (done, target) = DueCalculator.periodProgress(bundle.habit, date, bundle.checkIns)
        return TodayItem(
            bundle = bundle,
            status = status,
            streak = StreakCalculator.currentStreak(bundle.habit, bundle.checkIns, date),
            periodDone = done,
            periodTarget = target,
        )
    }
}
