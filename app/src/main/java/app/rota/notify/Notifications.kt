package app.rota.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.rota.MainActivity
import app.rota.R
import app.rota.di.rota
import app.rota.domain.DueCalculator
import app.rota.domain.model.HabitType
import app.rota.ui.snooze.SnoozeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val notifyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun createReminderChannel(context: Context) {
    val channel = NotificationChannel(
        ReminderScheduler.CHANNEL_ID,
        context.getString(R.string.reminders_channel),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = context.getString(R.string.reminders_channel_body)
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(ReminderScheduler.EXTRA_HABIT_ID, -1L)
        if (habitId <= 0L) return
        val pending = goAsync()
        notifyScope.launch {
            try {
                val entry = context.rota()
                val bundle = entry.habitRepository().getBundle(habitId) ?: return@launch
                val due = DueCalculator.isDue(
                    habit = bundle.habit,
                    date = ReminderScheduler.today(),
                    checkIns = bundle.checkIns,
                    snooze = bundle.snooze,
                    nowMillis = System.currentTimeMillis(),
                )
                if (due) {
                    showReminder(context, bundle.habit.id, bundle.habit.name, bundle.currentRoutine?.title, bundle.habit.type)
                }
                entry.reminderScheduler().schedule(bundle.habit)
            } finally {
                pending.finish()
            }
        }
    }
}

class CompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(ReminderScheduler.EXTRA_HABIT_ID, -1L)
        if (habitId <= 0L) return
        val pending = goAsync()
        notifyScope.launch {
            try {
                val entry = context.rota()
                entry.habitRepository().toggleCheck(habitId)
                entry.widgetRefresher().refresh()
                NotificationManagerCompat.from(context).cancel(habitId.toInt())
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        notifyScope.launch {
            try {
                val entry = context.rota()
                val habits = entry.habitRepository().observeBundles().first()
                entry.reminderScheduler().reschedule(habits.map { it.habit })
            } finally {
                pending.finish()
            }
        }
    }
}

private fun showReminder(
    context: Context,
    habitId: Long,
    name: String,
    routine: String?,
    type: HabitType,
) {
    val open = PendingIntent.getActivity(
        context,
        habitId.toInt(),
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderScheduler.EXTRA_HABIT_ID, habitId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val done = PendingIntent.getBroadcast(
        context,
        (habitId + 10_000).toInt(),
        Intent(context, CompleteReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_COMPLETE
            putExtra(ReminderScheduler.EXTRA_HABIT_ID, habitId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val snooze = PendingIntent.getActivity(
        context,
        (habitId + 20_000).toInt(),
        Intent(context, SnoozeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(ReminderScheduler.EXTRA_HABIT_ID, habitId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val body = buildString {
        if (!routine.isNullOrBlank()) append(routine)
        if (type != HabitType.CHECK) {
            if (isNotEmpty()) append(" · ")
            append(
                when (type) {
                    HabitType.COUNT -> context.getString(R.string.log_count)
                    HabitType.DURATION -> context.getString(R.string.log_duration)
                    HabitType.CHECK -> ""
                },
            )
        }
    }
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(name)
        .setContentText(body.ifBlank { context.getString(R.string.reminder_body) })
        .setContentIntent(open)
        .setAutoCancel(true)
        .addAction(0, context.getString(R.string.done), done)
        .addAction(0, context.getString(R.string.snooze), snooze)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(habitId.toInt(), notification)
    } catch (_: SecurityException) {
        // The user can revoke notification permission after the check above.
    }
}
