package app.rota

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.rota.data.prefs.SettingsStore
import app.rota.notify.ReminderScheduler
import app.rota.ui.navigation.RotaNav
import app.rota.ui.theme.RotaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openId = intent.getLongExtra(ReminderScheduler.EXTRA_HABIT_ID, -1L).takeIf { it > 0 }
        setContent {
            val settings by settingsStore.settings.collectAsStateWithLifecycle(
                initialValue = app.rota.data.prefs.AppSettings(),
            )
            RotaTheme(themeMode = settings.theme) {
                RotaNav(openHabitId = openId)
            }
        }
    }
}
