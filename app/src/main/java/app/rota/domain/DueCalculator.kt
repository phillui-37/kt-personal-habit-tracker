package app.rota.domain

import app.rota.domain.model.CheckIn
import app.rota.domain.model.CheckInStatus
import app.rota.domain.model.Habit
import app.rota.domain.model.Period
import app.rota.domain.model.Snooze
import app.rota.domain.model.TodayStatus
import java.time.LocalDate

object DueCalculator {
    fun todayStatus(
        habit: Habit,
        date: LocalDate,
        checkIns: List<CheckIn>,
        snooze: Snooze?,
        nowMillis: Long,
    ): TodayStatus {
        if (habit.archived) return TodayStatus.REST

        val todayLog = checkIns.firstOrNull { it.epochDay == date.toEpochDay() }
        when (todayLog?.status) {
            CheckInStatus.DONE -> return TodayStatus.DONE
            CheckInStatus.SKIPPED -> return TodayStatus.SKIPPED
            CheckInStatus.LOG -> {
                if (isSnoozed(snooze, nowMillis)) return TodayStatus.SNOOZED
                return TodayStatus.LOGGED
            }
            null -> Unit
        }

        if (isSnoozed(snooze, nowMillis)) return TodayStatus.SNOOZED
        if (!isOccurrenceOpen(habit, date, checkIns)) return TodayStatus.REST
        return TodayStatus.DUE
    }

    fun isDue(
        habit: Habit,
        date: LocalDate,
        checkIns: List<CheckIn>,
        snooze: Snooze? = null,
        nowMillis: Long = 0L,
    ): Boolean {
        val status = todayStatus(habit, date, checkIns, snooze, nowMillis)
        return status == TodayStatus.DUE || status == TodayStatus.LOGGED
    }

    fun isOccurrenceOpen(
        habit: Habit,
        date: LocalDate,
        checkIns: List<CheckIn>,
    ): Boolean {
        if (habit.archived) return false
        if (!habit.allowsWeekday(date)) return false
        if (date.isBefore(LocalDate.ofEpochDay(habit.createdAtEpochDay))) return false

        return when (habit.period) {
            Period.CUSTOM -> {
                val consumed = checkIns
                    .filter { it.status == CheckInStatus.DONE || it.status == CheckInStatus.SKIPPED }
                    .maxByOrNull { it.epochDay }
                    ?.date
                !date.isBefore(PeriodWindows.nextCustomDue(habit, consumed))
            }
            Period.DAILY -> true
            Period.WEEKLY, Period.MONTHLY, Period.QUARTERLY, Period.YEARLY -> {
                val range = PeriodWindows.bounds(habit.period, date)
                val consumed = checkIns.count { checkIn ->
                    checkIn.date in range &&
                        (checkIn.status == CheckInStatus.DONE || checkIn.status == CheckInStatus.SKIPPED)
                }
                consumed < habit.timesPerPeriod.coerceAtLeast(1)
            }
        }
    }

    fun periodProgress(
        habit: Habit,
        date: LocalDate,
        checkIns: List<CheckIn>,
    ): Pair<Int, Int> {
        val target = when (habit.period) {
            Period.DAILY, Period.CUSTOM -> 1
            else -> habit.timesPerPeriod.coerceAtLeast(1)
        }
        val range = when (habit.period) {
            Period.CUSTOM -> {
                val last = checkIns
                    .filter { it.status == CheckInStatus.DONE || it.status == CheckInStatus.SKIPPED }
                    .maxByOrNull { it.epochDay }
                    ?.date
                val start = PeriodWindows.nextCustomDue(habit, last)
                DateRange(start, start)
            }
            else -> PeriodWindows.bounds(habit.period, date)
        }
        val done = checkIns.count { it.date in range && it.status == CheckInStatus.DONE }
        return done to target
    }

    fun isSnoozed(snooze: Snooze?, nowMillis: Long): Boolean =
        snooze != null && snooze.untilEpochMillis > nowMillis

    fun nextRotationIndex(current: Int, size: Int): Int =
        if (size <= 0) 0 else (current + 1).mod(size)

    fun previousRotationIndex(current: Int, size: Int): Int =
        if (size <= 0) 0 else (current - 1).mod(size)
}
