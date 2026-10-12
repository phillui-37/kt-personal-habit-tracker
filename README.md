# Rota

Local-first habit tracker for Android. No account. Data stays on the phone unless you export it or send a copy to your own WebDAV folder.

## What it does

- **Today** — due habits only. Check off, log a count, or add minutes (chips or a simple timer). Filter by category.
- **Habits** — the full list, including archived habits, with the same category filter.
- **Rotating routines** — a habit can cycle through a list of items. Presets are edited in Settings. The current item advances when you complete the habit, not when you skip or snooze.
- **Cadence** — daily, week, month, quarter, year, or every N days. Weekday (Mon–Fri) and weekend presets, or any mix of days. “3 times this week” style targets.
- **Count and duration** — a glass-of-water habit can target 8; a run can target 30 minutes. Partial logs stay due until the target is met.
- **Snooze with a reason** — 1 hour, tonight, or tomorrow. Reasons show on the Review screen.
- **Review** — filter by range, status, cadence, habit, and category. Missed slots are counted when a period closes, not on every empty weekday.
- **Widget** — due count and a short checklist. Tap an item to open it. Tap anywhere else to open the app.
- **Settings** — theme, routine presets, reminders, and backup.
- **Backup and restore, always together** — file export/import, or the same JSON on WebDAV. Restore asks before it replaces local data.

## Run it

Open the project in Android Studio with Android Gradle Plugin 9.4 (compile SDK 37, target SDK 37).

```
./gradlew :app:assembleDebug
./gradlew :app:test
```

Install the debug APK on a device or emulator (API 26+).

## WebDAV: OkHttp + coroutines

`kotlinx.coroutines` is not an HTTP client. The real choice was:

| Approach | Verdict |
| --- | --- |
| Plain OkHttp callbacks | Works, but backup/restore sits in ViewModels that are already `suspend`. |
| **OkHttp + coroutine wrappers** | **Used.** WebDAV needs `PROPFIND`, `MKCOL`, `PUT`, `GET`. OkHttp lets those verbs be set directly and is the usual Android TLS/proxy stack. `suspendCancellableCoroutine` makes a call cancellable when you leave Settings. |
| Ktor Client | Built on coroutines, but WebDAV verbs are awkward, and the CIO engine is weaker on Android. The OkHttp Ktor engine is still OkHttp. |

Passwords are stored in EncryptedSharedPreferences. HTTP is allowed so a LAN NAS can be used. On Android 17 the app asks for local network access before it talks to a device on your Wi-Fi. Daily upload is optional WorkManager.

## Stack

Kotlin, Jetpack Compose, Material 3, Room, DataStore, Hilt, AlarmManager, WorkManager, Glance, OkHttp, kotlinx.serialization.
