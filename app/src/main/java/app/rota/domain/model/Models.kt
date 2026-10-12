package app.rota.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class Period {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY,
    CUSTOM,
}

enum class HabitType {
    CHECK,
    COUNT,
    DURATION,
}

enum class CheckInStatus {
    LOG,
    DONE,
    SKIPPED,
}

data class Habit(
    val id: Long = 0,
    val name: String,
    val note: String = "",
    val category: String = "",
    val type: HabitType = HabitType.CHECK,
    val target: Int = 1,
    val period: Period = Period.DAILY,
    val timesPerPeriod: Int = 1,
    val intervalDays: Int = 1,
    val weekdaysMask: Int = 0,
    val reminderMinutes: Int? = null,
    val colorIndex: Int = 0,
    val archived: Boolean = false,
    val rotationIndex: Int = 0,
    val createdAtEpochDay: Long = LocalDate.now().toEpochDay(),
    val sortOrder: Int = 0,
) {
    val unitLabel: String
        get() = when (type) {
            HabitType.CHECK -> ""
            HabitType.COUNT -> "times"
            HabitType.DURATION -> "min"
        }

    fun allowsWeekday(date: LocalDate): Boolean {
        if (weekdaysMask == 0) return true
        val bit = weekdayBit(date.dayOfWeek)
        return weekdaysMask and bit != 0
    }

    companion object {
        fun weekdayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

        fun maskOf(days: Collection<DayOfWeek>): Int =
            days.fold(0) { acc, day -> acc or weekdayBit(day) }
    }
}

data class RoutineItem(
    val id: Long = 0,
    val habitId: Long,
    val title: String,
    val sortOrder: Int,
)

data class CheckIn(
    val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    val status: CheckInStatus,
    val value: Int = 0,
    val note: String = "",
    val routineItemId: Long? = null,
    val routineLabel: String? = null,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

data class Snooze(
    val id: Long = 0,
    val habitId: Long,
    val untilEpochMillis: Long,
    val reason: String = "",
    val createdAtEpochMillis: Long,
)

data class HabitBundle(
    val habit: Habit,
    val routine: List<RoutineItem>,
    val checkIns: List<CheckIn> = emptyList(),
    val snooze: Snooze? = null,
) {
    val currentRoutine: RoutineItem?
        get() = if (routine.isEmpty()) {
            null
        } else {
            routine[habit.rotationIndex.mod(routine.size)]
        }
}

enum class TodayStatus {
    DUE,
    LOGGED,
    DONE,
    SKIPPED,
    SNOOZED,
    REST,
}

data class TodayItem(
    val bundle: HabitBundle,
    val status: TodayStatus,
    val streak: Int,
    val periodDone: Int,
    val periodTarget: Int,
) {
    val habit: Habit get() = bundle.habit
    val routineLabel: String? get() = bundle.currentRoutine?.title
    val routineCaption: String?
        get() {
            val item = bundle.currentRoutine ?: return null
            val size = bundle.routine.size
            if (size <= 1) return item.title
            val index = habit.rotationIndex.mod(size) + 1
            return "${item.title} · $index of $size"
        }
    val todayValue: Int
        get() = bundle.checkIns.firstOrNull { it.epochDay == LocalDate.now().toEpochDay() }?.value ?: 0
    val todayNote: String
        get() = bundle.checkIns.firstOrNull { it.epochDay == LocalDate.now().toEpochDay() }?.note.orEmpty()
}
