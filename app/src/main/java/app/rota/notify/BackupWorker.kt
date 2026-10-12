package app.rota.notify

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.rota.data.backup.BackupRepository
import app.rota.data.prefs.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backup: BackupRepository,
    private val settings: SettingsStore,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val config = settings.settings.first().webDav
        if (!config.autoDaily || !config.isConfigured) return Result.success()
        return try {
            backup.backupToWebDav(config)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "rota-webdav-backup"

        fun sync(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(UNIQUE)
                return
            }
            work.enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
