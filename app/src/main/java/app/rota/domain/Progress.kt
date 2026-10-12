package app.rota.domain

import app.rota.domain.model.CheckIn
import app.rota.domain.model.CheckInStatus
import app.rota.domain.model.Habit
import app.rota.domain.model.HabitType

data class ProgressChange(
    val checkIn: CheckIn,
    val rotationDelta: Int,
    val delete: Boolean = false,
)

object Progress {
    fun setValue(
        habit: Habit,
        existing: CheckIn?,
        newValue: Int,
        epochDay: Long,
        routineItemId: Long?,
        routineLabel: String?,
        note: String = existing?.note.orEmpty(),
    ): ProgressChange {
        val value = newValue.coerceAtLeast(0)
        val wasDone = existing?.status == CheckInStatus.DONE
        val status = when (habit.type) {
            HabitType.CHECK -> if (value > 0) CheckInStatus.DONE else CheckInStatus.LOG
            HabitType.COUNT, HabitType.DURATION -> when {
                value >= habit.target.coerceAtLeast(1) -> CheckInStatus.DONE
                value > 0 -> CheckInStatus.LOG
                else -> CheckInStatus.LOG
            }
        }
        val storedValue = if (habit.type == HabitType.CHECK) {
            if (status == CheckInStatus.DONE) 1 else 0
        } else {
            value
        }
        val checkIn = CheckIn(
            id = existing?.id ?: 0,
            habitId = habit.id,
            epochDay = epochDay,
            status = status,
            value = storedValue,
            note = note,
            routineItemId = routineItemId ?: existing?.routineItemId,
            routineLabel = routineLabel ?: existing?.routineLabel,
        )
        val nowDone = status == CheckInStatus.DONE
        val rotationDelta = when {
            nowDone && !wasDone -> 1
            !nowDone && wasDone -> -1
            else -> 0
        }
        return ProgressChange(
            checkIn = checkIn,
            rotationDelta = rotationDelta,
            delete = storedValue == 0 && status != CheckInStatus.SKIPPED && note.isBlank(),
        )
    }

    fun skip(
        habit: Habit,
        existing: CheckIn?,
        epochDay: Long,
        routineItemId: Long?,
        routineLabel: String?,
        note: String = "",
    ): ProgressChange {
        val wasDone = existing?.status == CheckInStatus.DONE
        return ProgressChange(
            checkIn = CheckIn(
                id = existing?.id ?: 0,
                habitId = habit.id,
                epochDay = epochDay,
                status = CheckInStatus.SKIPPED,
                value = 0,
                note = note,
                routineItemId = routineItemId,
                routineLabel = routineLabel,
            ),
            rotationDelta = if (wasDone) -1 else 0,
        )
    }
}
