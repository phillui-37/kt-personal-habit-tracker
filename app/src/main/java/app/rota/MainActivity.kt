package app.rota

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rota.data.prefs.SettingsStore
import app.rota.notify.ReminderScheduler
import app.rota.ui.navigation.HabitOpen
import app.rota.ui.navigation.RotaNav
import app.rota.ui.theme.RotaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore
    private var openHabit by mutableStateOf<HabitOpen?>(null)
    private var openSerial = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) publishOpen(intent)
        setContent {
            val settings by settingsStore.settings.collectAsStateWithLifecycle(
                initialValue = app.rota.data.prefs.AppSettings(),
            )
            val request = openHabit
            RotaTheme(themeMode = settings.theme) {
                RotaNav(openHabit = request)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishOpen(intent)
    }

    private fun publishOpen(intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_HABIT_ID, -1L)
        openHabit = if (id > 0) HabitOpen(id, ++openSerial) else null
    }
}
