package app.rota.domain

import app.rota.domain.model.Habit
import app.rota.domain.model.HabitBundle
import app.rota.domain.model.RoutineItem
import app.rota.domain.model.TodayItem
import app.rota.domain.model.TodayStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TodayItemTest {
    @Test
    fun routineCaptionIncludesIndex() {
        val item = TodayItem(
            bundle = HabitBundle(
                habit = Habit(id = 1, name = "Strength", rotationIndex = 1),
                routine = listOf(
                    RoutineItem(1, 1, "Core", 0),
                    RoutineItem(2, 1, "Legs", 1),
                    RoutineItem(3, 1, "Back", 2),
                ),
            ),
            status = TodayStatus.DUE,
            streak = 0,
            periodDone = 0,
            periodTarget = 3,
        )
        assertThat(item.routineCaption).isEqualTo("Legs · 2 of 3")
    }
}
