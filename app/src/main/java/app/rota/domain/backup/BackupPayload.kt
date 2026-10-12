package app.rota.domain.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAtEpochMillis: Long,
    val habits: List<HabitBackup> = emptyList(),
    val routineItems: List<RoutineBackup> = emptyList(),
    val checkIns: List<CheckInBackup> = emptyList(),
    val snoozes: List<SnoozeBackup> = emptyList(),
)

@Serializable
data class HabitBackup(
    val id: Long,
    val name: String,
    val note: String = "",
    val category: String = "",
    val type: String,
    val target: Int = 1,
    val period: String,
    val timesPerPeriod: Int = 1,
    val intervalDays: Int = 1,
    val weekdaysMask: Int = 0,
    val reminderMinutes: Int? = null,
    val colorIndex: Int = 0,
    val archived: Boolean = false,
    val rotationIndex: Int = 0,
    val createdAtEpochDay: Long,
    val sortOrder: Int = 0,
)

@Serializable
data class RoutineBackup(
    val id: Long,
    val habitId: Long,
    val title: String,
    val sortOrder: Int,
)

@Serializable
data class CheckInBackup(
    val id: Long,
    val habitId: Long,
    val epochDay: Long,
    val status: String,
    val value: Int = 0,
    val note: String = "",
    val routineItemId: Long? = null,
    val routineLabel: String? = null,
)

@Serializable
data class SnoozeBackup(
    val id: Long,
    val habitId: Long,
    val untilEpochMillis: Long,
    val reason: String = "",
    val createdAtEpochMillis: Long,
)
