package app.rota.data.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
    private val passwordKey = stringPreferencesKey("webdav_password")
    private val cipher = KeystoreSecret()
    private val secretsMutex = Mutex()
    private val legacyMigrated = AtomicBoolean(false)

    val settings: Flow<AppSettings> = flow {
        migrateLegacyPassword()
        emitAll(
            context.dataStore.data.map { prefs ->
                AppSettings(
                    theme = runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.SYSTEM.name) }
                        .getOrDefault(ThemeMode.SYSTEM),
                    webDav = WebDavConfig(
                        url = prefs[urlKey].orEmpty(),
                        username = prefs[userKey].orEmpty(),
                        password = cipher.decrypt(prefs[passwordKey].orEmpty()),
                        remotePath = prefs[pathKey] ?: "rota-backup.json",
                        autoDaily = prefs[autoBackupKey] ?: false,
                    ),
                )
            },
        )
    }.distinctUntilChanged()

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setWebDav(config: WebDavConfig) {
        secretsMutex.withLock {
            context.dataStore.edit {
                it[urlKey] = config.url.trim()
                it[userKey] = config.username.trim()
                it[pathKey] = config.remotePath.trim().ifBlank { "rota-backup.json" }
                it[autoBackupKey] = config.autoDaily
                it[passwordKey] = cipher.encrypt(config.password)
            }
            if (legacyFile().exists()) context.deleteSharedPreferences(LEGACY_SECRETS)
            legacyMigrated.set(true)
        }
    }

    private suspend fun migrateLegacyPassword() {
        if (legacyMigrated.get()) return
        secretsMutex.withLock {
            if (legacyMigrated.get()) return
            val prefs = context.dataStore.data.first()
            if (!prefs.contains(passwordKey)) {
                when (val legacy = readLegacyPassword()) {
                    LegacyPassword.Failed -> return@withLock
                    LegacyPassword.Absent -> context.dataStore.edit { it[passwordKey] = "" }
                    is LegacyPassword.Found -> {
                        context.dataStore.edit { it[passwordKey] = cipher.encrypt(legacy.password) }
                        context.deleteSharedPreferences(LEGACY_SECRETS)
                    }
                }
            } else if (legacyFile().exists()) {
                context.deleteSharedPreferences(LEGACY_SECRETS)
            }
            legacyMigrated.set(true)
        }
    }

    @Suppress("DEPRECATION")
    private fun readLegacyPassword(): LegacyPassword {
        if (!legacyFile().exists()) return LegacyPassword.Absent
        return try {
            val key = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                LEGACY_SECRETS,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            LegacyPassword.Found(prefs.getString(LEGACY_PASSWORD, "").orEmpty())
        } catch (_: GeneralSecurityException) {
            LegacyPassword.Failed
        } catch (_: java.io.IOException) {
            LegacyPassword.Failed
        }
    }

    private fun legacyFile(): File =
        File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_SECRETS.xml")

    private class KeystoreSecret {
        private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        private val lock = Any()

        fun encrypt(plain: String): String = synchronized(lock) {
            if (plain.isEmpty()) return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val payload = ByteArray(iv.size + encrypted.size)
            iv.copyInto(payload)
            encrypted.copyInto(payload, destinationOffset = iv.size)
            Base64.encodeToString(payload, Base64.NO_WRAP)
        }

        fun decrypt(payload: String): String = synchronized(lock) {
            if (payload.isEmpty()) return ""
            try {
                val bytes = Base64.decode(payload, Base64.NO_WRAP)
                if (bytes.size <= IV_SIZE) return ""
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    GCMParameterSpec(128, bytes.copyOfRange(0, IV_SIZE)),
                )
                String(cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)), Charsets.UTF_8)
            } catch (_: GeneralSecurityException) {
                ""
            } catch (_: IllegalArgumentException) {
                ""
            }
        }

        private fun secretKey(): SecretKey {
            val existing = keyStore.getKey(ALIAS, null) as? SecretKey
            if (existing != null) return existing
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            return generator.generateKey()
        }

        private companion object {
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val ALIAS = "rota_webdav_password"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val IV_SIZE = 12
        }
    }

    private sealed interface LegacyPassword {
        data class Found(val password: String) : LegacyPassword
        data object Absent : LegacyPassword
        data object Failed : LegacyPassword
    }

    private companion object {
        const val LEGACY_SECRETS = "rota_secrets"
        const val LEGACY_PASSWORD = "webdav_password"
    }
}
