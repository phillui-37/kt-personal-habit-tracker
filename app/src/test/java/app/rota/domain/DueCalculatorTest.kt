package app.rota.domain

import app.rota.domain.model.CheckIn
import app.rota.domain.model.CheckInStatus
import app.rota.domain.model.Habit
import app.rota.domain.model.HabitType
import app.rota.domain.model.Period
import app.rota.domain.model.Snooze
import app.rota.domain.model.TodayStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DueCalculatorTest {
    private val monday = LocalDate.of(2026, 10, 12)

    @Test
    fun dailyIsDueUntilDone() {
        val habit = habit(period = Period.DAILY)
        assertThat(DueCalculator.isDue(habit, monday, emptyList())).isTrue()
        val done = checkIn(habit.id, monday, CheckInStatus.DONE)
        assertThat(DueCalculator.todayStatus(habit, monday, listOf(done), null, 0))
            .isEqualTo(TodayStatus.DONE)
    }

    @Test
    fun weeklyThreeTimesHidesAfterTarget() {
        val habit = habit(period = Period.WEEKLY, timesPerPeriod = 3)
        val week = listOf(
            checkIn(habit.id, monday, CheckInStatus.DONE),
            checkIn(habit.id, monday.plusDays(1), CheckInStatus.DONE),
            checkIn(habit.id, monday.plusDays(2), CheckInStatus.DONE),
        )
        assertThat(DueCalculator.isDue(habit, monday.plusDays(3), week)).isFalse()
        assertThat(DueCalculator.isDue(habit, monday.plusDays(1), week.dropLast(2))).isTrue()
    }

    @Test
    fun weekdayAndWeekendPresets() {
        val weekdays = habit(weekdaysMask = Habit.WEEKDAYS)
        val weekend = habit(weekdaysMask = Habit.WEEKEND)
        DayOfWeek.entries.forEach { day ->
            val date = monday.plusDays((day.value - DayOfWeek.MONDAY.value).toLong())
            val isWeekday = day.value <= DayOfWeek.FRIDAY.value
            assertThat(DueCalculator.isOccurrenceOpen(weekdays, date, emptyList())).isEqualTo(isWeekday)
            assertThat(DueCalculator.isOccurrenceOpen(weekend, date, emptyList())).isEqualTo(!isWeekday)
        }
    }

    @Test
    fun weekdayMaskBlocksOtherDays() {
        val habit = habit(weekdaysMask = Habit.maskOf(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        assertThat(DueCalculator.isOccurrenceOpen(habit, monday, emptyList())).isTrue()
        assertThat(DueCalculator.isOccurrenceOpen(habit, monday.plusDays(1), emptyList())).isFalse()
        assertThat(DueCalculator.isOccurrenceOpen(habit, monday.plusDays(2), emptyList())).isTrue()
    }

    @Test
    fun quarterlyOnce() {
        val october = LocalDate.of(2026, 10, 1)
        val habit = habit(
            period = Period.QUARTERLY,
            timesPerPeriod = 1,
            createdAtEpochDay = october.toEpochDay(),
        )
        assertThat(DueCalculator.isDue(habit, october, emptyList())).isTrue()
        val done = checkIn(habit.id, october, CheckInStatus.DONE)
        assertThat(DueCalculator.isDue(habit, october.plusDays(10), listOf(done))).isFalse()
    }

    @Test
    fun customEveryThreeDaysStaysDueWhenMissed() {
        val start = monday
        val habit = habit(
            period = Period.CUSTOM,
            intervalDays = 3,
            createdAtEpochDay = start.toEpochDay(),
        )
        assertThat(DueCalculator.isDue(habit, start, emptyList())).isTrue()
        assertThat(DueCalculator.isDue(habit, start.plusDays(1), emptyList())).isTrue()
        val done = checkIn(habit.id, start.plusDays(1), CheckInStatus.DONE)
        assertThat(DueCalculator.isDue(habit, start.plusDays(2), listOf(done))).isFalse()
        assertThat(DueCalculator.isDue(habit, start.plusDays(4), listOf(done))).isTrue()
    }

    @Test
    fun skipConsumesSlotWithoutBeingDue() {
        val habit = habit(period = Period.WEEKLY, timesPerPeriod = 1)
        val skipped = checkIn(habit.id, monday, CheckInStatus.SKIPPED)
        assertThat(DueCalculator.todayStatus(habit, monday, listOf(skipped), null, 0))
            .isEqualTo(TodayStatus.SKIPPED)
        assertThat(DueCalculator.isDue(habit, monday.plusDays(1), listOf(skipped))).isFalse()
    }

    @Test
    fun snoozeHidesDueHabit() {
        val habit = habit()
        val snooze = Snooze(
            habitId = habit.id,
            untilEpochMillis = 2_000,
            createdAtEpochMillis = 1_000,
        )
        assertThat(DueCalculator.todayStatus(habit, monday, emptyList(), snooze, 1_500))
            .isEqualTo(TodayStatus.SNOOZED)
        assertThat(DueCalculator.todayStatus(habit, monday, emptyList(), snooze, 2_500))
            .isEqualTo(TodayStatus.DUE)
    }

    @Test
    fun rotationWraps() {
        assertThat(DueCalculator.nextRotationIndex(2, 3)).isEqualTo(0)
        assertThat(DueCalculator.previousRotationIndex(0, 3)).isEqualTo(2)
    }

    @Test
    fun partialLogStaysDue() {
        val habit = habit(type = HabitType.COUNT, target = 8)
        val log = checkIn(habit.id, monday, CheckInStatus.LOG, value = 3)
        assertThat(DueCalculator.todayStatus(habit, monday, listOf(log), null, 0))
            .isEqualTo(TodayStatus.LOGGED)
        assertThat(DueCalculator.isDue(habit, monday, listOf(log))).isTrue()
    }

    private fun habit(
        period: Period = Period.DAILY,
        timesPerPeriod: Int = 1,
        intervalDays: Int = 1,
        weekdaysMask: Int = 0,
        type: HabitType = HabitType.CHECK,
        target: Int = 1,
        createdAtEpochDay: Long = monday.toEpochDay(),
    ) = Habit(
        id = 1,
        name = "Test",
        type = type,
        target = target,
        period = period,
        timesPerPeriod = timesPerPeriod,
        intervalDays = intervalDays,
        weekdaysMask = weekdaysMask,
        createdAtEpochDay = createdAtEpochDay,
    )

    private fun checkIn(
        habitId: Long,
        date: LocalDate,
        status: CheckInStatus,
        value: Int = 1,
    ) = CheckIn(
        habitId = habitId,
        epochDay = date.toEpochDay(),
        status = status,
        value = value,
    )
}
