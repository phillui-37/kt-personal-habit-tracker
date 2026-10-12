package app.rota.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.rota.domain.model.CheckIn
import app.rota.domain.model.CheckInStatus
import app.rota.domain.model.Habit
import app.rota.domain.model.HabitBundle
import app.rota.domain.model.HabitType
import app.rota.domain.model.Period
import app.rota.domain.model.RoutineItem
import app.rota.domain.model.Snooze

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String = "",
    val category: String = "",
    val type: String,
    val target: Int = 1,
    val period: String,
    val timesPerPeriod: Int = 1,
    val intervalDays: Int = 1,
    val weekdaysMask: Int = 0,
    val reminderMinutes: Int? = null,
    val colorIndex: Int = 0,
    val archived: Boolean = false,
    val rotationIndex: Int = 0,
    val createdAtEpochDay: Long,
    val sortOrder: Int = 0,
) {
    fun toDomain() = Habit(
        id = id,
        name = name,
        note = note,
        category = category,
        type = runCatching { HabitType.valueOf(type) }.getOrDefault(HabitType.CHECK),
        target = target,
        period = runCatching { Period.valueOf(period) }.getOrDefault(Period.DAILY),
        timesPerPeriod = timesPerPeriod,
        intervalDays = intervalDays,
        weekdaysMask = weekdaysMask,
        reminderMinutes = reminderMinutes,
        colorIndex = colorIndex,
        archived = archived,
        rotationIndex = rotationIndex,
        createdAtEpochDay = createdAtEpochDay,
        sortOrder = sortOrder,
    )

    companion object {
        fun from(habit: Habit) = HabitEntity(
            id = habit.id,
            name = habit.name,
            note = habit.note,
            category = habit.category,
            type = habit.type.name,
            target = habit.target,
            period = habit.period.name,
            timesPerPeriod = habit.timesPerPeriod,
            intervalDays = habit.intervalDays,
            weekdaysMask = habit.weekdaysMask,
            reminderMinutes = habit.reminderMinutes,
            colorIndex = habit.colorIndex,
            archived = habit.archived,
            rotationIndex = habit.rotationIndex,
            createdAtEpochDay = habit.createdAtEpochDay,
            sortOrder = habit.sortOrder,
        )
    }
}

@Entity(
    tableName = "routine_items",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class RoutineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val title: String,
    val sortOrder: Int,
) {
    fun toDomain() = RoutineItem(id, habitId, title, sortOrder)

    companion object {
        fun from(item: RoutineItem) = RoutineItemEntity(item.id, item.habitId, item.title, item.sortOrder)
    }
}

@Entity(
    tableName = "check_ins",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habitId", "epochDay"], unique = true)],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    val status: String,
    val value: Int = 0,
    val note: String = "",
    val routineItemId: Long? = null,
    val routineLabel: String? = null,
) {
    fun toDomain() = CheckIn(
        id = id,
        habitId = habitId,
        epochDay = epochDay,
        status = runCatching { CheckInStatus.valueOf(status) }.getOrDefault(CheckInStatus.LOG),
        value = value,
        note = note,
        routineItemId = routineItemId,
        routineLabel = routineLabel,
    )

    companion object {
        fun from(checkIn: CheckIn) = CheckInEntity(
            id = checkIn.id,
            habitId = checkIn.habitId,
            epochDay = checkIn.epochDay,
            status = checkIn.status.name,
            value = checkIn.value,
            note = checkIn.note,
            routineItemId = checkIn.routineItemId,
            routineLabel = checkIn.routineLabel,
        )
    }
}

@Entity(
    tableName = "snoozes",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class SnoozeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val untilEpochMillis: Long,
    val reason: String = "",
    val createdAtEpochMillis: Long,
) {
    fun toDomain() = Snooze(id, habitId, untilEpochMillis, reason, createdAtEpochMillis)

    companion object {
        fun from(snooze: Snooze) = SnoozeEntity(
            snooze.id,
            snooze.habitId,
            snooze.untilEpochMillis,
            snooze.reason,
            snooze.createdAtEpochMillis,
        )
    }
}

data class HabitWithRelations(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val routine: List<RoutineItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val checkIns: List<CheckInEntity>,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val snoozes: List<SnoozeEntity>,
) {
    fun toDomain(nowMillis: Long): HabitBundle {
        val activeSnooze = snoozes
            .map { it.toDomain() }
            .filter { it.untilEpochMillis > nowMillis }
            .maxByOrNull { it.untilEpochMillis }
        return HabitBundle(
            habit = habit.toDomain(),
            routine = routine.map { it.toDomain() }.sortedBy { it.sortOrder },
            checkIns = checkIns.map { it.toDomain() },
            snooze = activeSnooze,
        )
    }
}
