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
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val webDav: WebDavConfig = WebDavConfig(),
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

    private val secrets: SharedPreferences by lazy {
        val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "rota_secrets",
            key,
            context,
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
        )
    }.distinctUntilChanged()

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
