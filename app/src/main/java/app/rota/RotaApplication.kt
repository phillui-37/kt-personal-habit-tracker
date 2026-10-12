package app.rota

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.rota.data.repo.HabitRepository
import app.rota.notify.BackupWorker
import app.rota.notify.ReminderReconcileWorker
import app.rota.notify.createReminderChannel
import app.rota.notify.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RotaApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var settingsStore: app.rota.data.prefs.SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createReminderChannel(this)
        scope.launch {
            val bundles = habits.observeBundles(includeArchived = true).first()
            reminders.reschedule(bundles.map { it.habit })
            val auto = settingsStore.settings.first().webDav
            BackupWorker.sync(this@RotaApplication, auto.autoDaily && auto.isConfigured)
            ReminderReconcileWorker.enqueue(this@RotaApplication)
        }
    }
}
