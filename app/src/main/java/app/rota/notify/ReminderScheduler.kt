package app.rota.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.rota.domain.model.Habit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarms: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun reschedule(habits: List<Habit>) {
        habits.forEach { schedule(it) }
    }

    fun schedule(habit: Habit) {
        val intent = pending(habit.id)
        alarms.cancel(intent)
        val minutes = habit.reminderMinutes ?: return
        if (habit.archived) return
        val triggerAt = nextTriggerMillis(minutes)
        if (canExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    fun cancel(habitId: Long) {
        alarms.cancel(pending(habitId))
    }

    fun canExact(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarms.canScheduleExactAlarms()
    }

    private fun nextTriggerMillis(minutesFromMidnight: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        val hour = (minutesFromMidnight / 60).coerceIn(0, 23)
        val minute = (minutesFromMidnight % 60).coerceIn(0, 59)
        var at = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
        if (!at.isAfter(now)) {
            at = at.plusDays(1)
        }
        return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun pending(habitId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_HABIT_ID, habitId)
        }
        return PendingIntent.getBroadcast(
            context,
            habitId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_REMIND = "app.rota.REMIND"
        const val ACTION_COMPLETE = "app.rota.COMPLETE"
        const val EXTRA_HABIT_ID = "habit_id"
        const val CHANNEL_ID = "rota_reminders"

        fun minutesOf(hour: Int, minute: Int): Int = hour * 60 + minute

        fun clockLabel(minutes: Int): String {
            val time = LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))
            val hour = if (time.hour % 12 == 0) 12 else time.hour % 12
            val amPm = if (time.hour < 12) "AM" else "PM"
            return "$hour:${time.minute.toString().padStart(2, '0')} $amPm"
        }

        fun today(): LocalDate = LocalDate.now()
    }
}
