package app.rota.data.backup

import app.rota.data.prefs.SettingsStore
import app.rota.data.prefs.WebDavConfig
import app.rota.data.repo.HabitRepository
import app.rota.domain.backup.BackupPayload
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val habits: HabitRepository,
    private val webDav: WebDavClient,
    private val settings: SettingsStore,
    private val json: Json,
) {
    suspend fun writeTo(stream: OutputStream) {
        val payload = habits.exportBackup()
        stream.write(encode(payload))
        stream.flush()
    }

    suspend fun restoreFrom(stream: InputStream) {
        val bytes = stream.readBytes()
        restoreFromBytes(bytes)
    }

    suspend fun backupToWebDav(config: WebDavConfig? = null) {
        val resolved = config ?: settings.settings.first().webDav
        require(resolved.isConfigured) { "Add a WebDAV URL and file path first." }
        val bytes = encode(habits.exportBackup())
        webDav.put(resolved.url, resolved.username, resolved.password, resolved.remotePath, bytes)
    }

    suspend fun restoreFromWebDav(config: WebDavConfig? = null) {
        val resolved = config ?: settings.settings.first().webDav
        require(resolved.isConfigured) { "Add a WebDAV URL and file path first." }
        val bytes = webDav.get(resolved.url, resolved.username, resolved.password, resolved.remotePath)
        restoreFromBytes(bytes)
    }

    suspend fun testWebDav(config: WebDavConfig) {
        require(config.url.isNotBlank()) { "Add a WebDAV URL first." }
        webDav.test(config.url, config.username, config.password, config.remotePath)
    }

    private suspend fun restoreFromBytes(bytes: ByteArray) {
        val payload = runCatching { json.decodeFromString(BackupPayload.serializer(), bytes.decodeToString()) }
            .getOrElse { throw IllegalArgumentException("This file is not a Rota backup.") }
        habits.restoreBackup(payload)
    }

    private fun encode(payload: BackupPayload): ByteArray =
        json.encodeToString(BackupPayload.serializer(), payload).toByteArray()
}
