package app.rota.ui.snooze

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.rota.data.prefs.AppSettings
import app.rota.data.prefs.SettingsStore
import app.rota.data.repo.HabitRepository
import app.rota.notify.ReminderScheduler
import app.rota.ui.theme.RotaTheme
import app.rota.ui.today.SnoozeSheet
import app.rota.widget.WidgetRefresher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SnoozeActivity : ComponentActivity() {
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var widgets: WidgetRefresher
    @Inject lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val habitId = intent.getLongExtra(ReminderScheduler.EXTRA_HABIT_ID, -1L)
        if (habitId <= 0L) {
            finish()
            return
        }
        setContent {
            val appSettings by settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            var name by remember { mutableStateOf("habit") }
            LaunchedEffect(habitId) {
                name = habits.getBundle(habitId)?.habit?.name ?: "habit"
            }
            RotaTheme(appSettings.theme) {
                SnoozeSheet(
                    name = name,
                    onDismiss = { finish() },
                    onConfirm = { choice, reason ->
                        lifecycleScope.launch {
                            habits.snooze(habitId, choice.untilMillis(), reason)
                            widgets.refresh()
                            finish()
                        }
                    },
                )
            }
        }
    }
}
