package app.rota.domain

import app.rota.domain.model.CheckIn
import app.rota.domain.model.CheckInStatus
import app.rota.domain.model.Habit
import app.rota.domain.model.Period
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 10, 12)

    @Test
    fun dailyStreakCountsConsecutiveDones() {
        val habit = Habit(id = 1, name = "Read", createdAtEpochDay = today.minusDays(10).toEpochDay())
        val logs = (0..2).map { offset ->
            CheckIn(
                habitId = 1,
                epochDay = today.minusDays(offset.toLong()).toEpochDay(),
                status = CheckInStatus.DONE,
                value = 1,
            )
        }
        assertThat(StreakCalculator.currentStreak(habit, logs, today)).isEqualTo(3)
    }

    @Test
    fun skipBreaksDailyStreak() {
        val habit = Habit(id = 1, name = "Read", createdAtEpochDay = today.minusDays(10).toEpochDay())
        val logs = listOf(
            CheckIn(habitId = 1, epochDay = today.toEpochDay(), status = CheckInStatus.DONE, value = 1),
            CheckIn(habitId = 1, epochDay = today.minusDays(1).toEpochDay(), status = CheckInStatus.SKIPPED, value = 0),
            CheckIn(habitId = 1, epochDay = today.minusDays(2).toEpochDay(), status = CheckInStatus.DONE, value = 1),
        )
        assertThat(StreakCalculator.currentStreak(habit, logs, today)).isEqualTo(1)
    }

    @Test
    fun weeklyStreakCountsClosedWeeks() {
        val habit = Habit(
            id = 1,
            name = "Gym",
            period = Period.WEEKLY,
            timesPerPeriod = 3,
            createdAtEpochDay = today.minusDays(21).toEpochDay(),
        )
        val lastWeek = today.minusWeeks(1).with(java.time.DayOfWeek.MONDAY)
        val logs = (0..2).map {
            CheckIn(
                habitId = 1,
                epochDay = lastWeek.plusDays(it.toLong()).toEpochDay(),
                status = CheckInStatus.DONE,
                value = 1,
            )
        }
        assertThat(StreakCalculator.currentStreak(habit, logs, today)).isEqualTo(1)
    }
}
