package app.rota.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rota_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val remotePath: String = "rota-backup.json",
    val autoDaily: Boolean = false,
) {
    val isConfigured: Boolean
        get() = url.isNotBlank() && remotePath.isNotBlank()
}

@Serializable
data class RoutineTemplate(
    val id: String,
    val name: String,
    val items: List<String> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(templates: List<RoutineTemplate>): String = json.encodeToString(templates)

        fun decode(raw: String?): List<RoutineTemplate> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching { json.decodeFromString<List<RoutineTemplate>>(raw) }.getOrDefault(emptyList())
        }
    }
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val webDav: WebDavConfig = WebDavConfig(),
    val routineTemplates: List<RoutineTemplate> = emptyList(),
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val themeKey = stringPreferencesKey("theme")
    private val autoBackupKey = booleanPreferencesKey("webdav_auto")
    private val urlKey = stringPreferencesKey("webdav_url")
    private val userKey = stringPreferencesKey("webdav_user")
    private val pathKey = stringPreferencesKey("webdav_path")
    private val templatesKey = stringPreferencesKey("routine_templates")

    private val secrets: SharedPreferences by lazy {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "rota_secrets",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            theme = runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            webDav = WebDavConfig(
                url = prefs[urlKey].orEmpty(),
                username = prefs[userKey].orEmpty(),
                password = secrets.getString(PASSWORD, "").orEmpty(),
                remotePath = prefs[pathKey] ?: "rota-backup.json",
                autoDaily = prefs[autoBackupKey] ?: false,
            ),
            routineTemplates = RoutineTemplate.decode(prefs[templatesKey]),
        )
    }.distinctUntilChanged()

    suspend fun setRoutineTemplates(templates: List<RoutineTemplate>) {
        context.dataStore.edit { it[templatesKey] = RoutineTemplate.encode(templates) }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setWebDav(config: WebDavConfig) {
        context.dataStore.edit {
            it[urlKey] = config.url.trim()
            it[userKey] = config.username.trim()
            it[pathKey] = config.remotePath.trim().ifBlank { "rota-backup.json" }
            it[autoBackupKey] = config.autoDaily
        }
        secrets.edit().putString(PASSWORD, config.password).apply()
    }

    companion object {
        private const val PASSWORD = "webdav_password"
    }
}
