package app.rota.di

import android.content.Context
import app.rota.data.backup.BackupRepository
import app.rota.data.prefs.SettingsStore
import app.rota.data.repo.HabitRepository
import app.rota.notify.ReminderScheduler
import app.rota.widget.WidgetRefresher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RotaEntryPoint {
    fun habitRepository(): HabitRepository
    fun reminderScheduler(): ReminderScheduler
    fun widgetRefresher(): WidgetRefresher
    fun backupRepository(): BackupRepository
    fun settingsStore(): SettingsStore
}

fun Context.rota(): RotaEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, RotaEntryPoint::class.java)
