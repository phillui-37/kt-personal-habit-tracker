package app.rota.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebDavException(val status: Int, message: String) : IOException(message)

@Singleton
class WebDavClient @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun test(baseUrl: String, username: String, password: String, path: String) {
        val fileUrl = resolve(baseUrl, normalizePath(path))
        prepareParent(baseUrl, fileUrl, username, password)
    }

    suspend fun put(
        baseUrl: String,
        username: String,
        password: String,
        path: String,
        bytes: ByteArray,
    ) {
        val normalized = normalizePath(path)
        val fileUrl = resolve(baseUrl, normalized)
        prepareParent(baseUrl, fileUrl, username, password)
        val first = upload(fileUrl, bytes, username, password)
        if (first.ok) return
        if (first.code != 409 || !resourceExists(fileUrl, username, password)) {
            throw failure(first, "backup")
        }
        // Servers such as Synology and nginx answer 409 when the file is already there.
        // Stage the new copy, then move it over the old one so a failed replace keeps the previous backup.
        replaceExisting(baseUrl, normalized, fileUrl, bytes, username, password)
    }

    suspend fun get(
        baseUrl: String,
        username: String,
        password: String,
        path: String,
    ): ByteArray {
        val url = resolve(baseUrl, normalizePath(path))
        val request = Request.Builder()
            .url(url)
            .get()
            .applyAuth(username, password)
            .build()
        return client.await(request).use { response ->
            if (response.code == 404) {
                throw WebDavException(404, "No backup file was found at that path.")
            }
            if (!response.isSuccessful) {
                throw failure(Status(response.code, response.body.string()), "restore")
            }
            withContext(Dispatchers.IO) {
                response.body.bytes().also { bytes ->
                    if (bytes.isEmpty()) {
                        throw WebDavException(response.code, "Empty backup file.")
                    }
                }
            }
        }
    }

    private suspend fun prepareParent(
        baseUrl: String,
        fileUrl: String,
        username: String,
        password: String,
    ) {
        for (folder in collectionsFor(baseUrl, fileUrl)) {
            makeCollection(folder, username, password)
        }
    }

    private suspend fun makeCollection(url: String, username: String, password: String) {
        val directory = asDirectory(url)
        val parsed = directory.toHttpUrlOrNull()
            ?: throw WebDavException(0, "The WebDAV URL is not valid.")
        if (parsed.pathSegments.none { it.isNotEmpty() }) {
            val root = propfind(directory, username, password)
            if (root.code == 401 || root.code == 403) throw failure(root, "open the folder")
            if (root.ok || root.code == 405) return
            throw WebDavException(
                root.code,
                "The WebDAV URL needs a folder that already exists on the server.",
            )
        }
        val found = propfind(directory, username, password)
        when {
            found.code == 401 || found.code == 403 -> throw failure(found, "open the folder")
            found.ok && found.collection -> return
            found.ok && !found.collection -> throw WebDavException(
                409,
                "That WebDAV path is a file. Point the server URL at a folder.",
            )
            found.code == 405 -> return
        }
        val created = mkcol(directory, username, password)
        when (created.code) {
            201, 200, 204, 405 -> return
            401, 403 -> throw failure(created, "create the folder")
            409 -> {
                // nginx reports 409 when the folder is already there. Nextcloud reports 409 when the parent is missing.
                val again = propfind(directory, username, password)
                if (again.ok && again.collection) return
                if (again.code == 401 || again.code == 403) throw failure(again, "create the folder")
                throw WebDavException(
                    409,
                    "Could not create the WebDAV folder. Check that the server URL points at a folder that already exists.",
                )
            }
            else -> throw failure(created, "create the folder")
        }
    }

    private suspend fun replaceExisting(
        baseUrl: String,
        path: String,
        fileUrl: String,
        bytes: ByteArray,
        username: String,
        password: String,
    ) {
        val tempUrl = resolve(baseUrl, "$path.rota-upload")
        var staged = upload(tempUrl, bytes, username, password)
        if (staged.code == 409) {
            remove(tempUrl, username, password)
            staged = upload(tempUrl, bytes, username, password)
        }
        if (!staged.ok) throw failure(staged, "backup")
        val moved = move(tempUrl, fileUrl, username, password)
        if (moved.ok) return
        if (moved.code == 401 || moved.code == 403) {
            remove(tempUrl, username, password)
            throw failure(moved, "backup")
        }
        remove(fileUrl, username, password)
        val movedAgain = move(tempUrl, fileUrl, username, password)
        if (movedAgain.ok) return
        val replaced = upload(fileUrl, bytes, username, password)
        remove(tempUrl, username, password)
        if (!replaced.ok) throw failure(replaced, "backup")
    }

    private suspend fun resourceExists(url: String, username: String, password: String): Boolean {
        val found = propfind(url, username, password)
        if (found.code == 401 || found.code == 403) throw failure(found, "backup")
        return found.ok
    }

    private suspend fun upload(
        url: String,
        bytes: ByteArray,
        username: String,
        password: String,
    ): Status {
        val request = Request.Builder()
            .url(url)
            .put(bytes.toRequestBody(OCTET))
            .applyAuth(username, password)
            .header("X-NC-WebDAV-AutoMkcol", "1")
            .build()
        return execute(request)
    }

    private suspend fun propfind(url: String, username: String, password: String): Status {
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", ByteArray(0).toRequestBody(XML))
            .header("Depth", "0")
            .applyAuth(username, password)
            .build()
        return execute(request)
    }

    private suspend fun mkcol(url: String, username: String, password: String): Status {
        val request = Request.Builder()
            .url(url)
            .method("MKCOL", null)
            .applyAuth(username, password)
            .build()
        return execute(request)
    }

    private suspend fun move(source: String, destination: String, username: String, password: String): Status {
        val request = Request.Builder()
            .url(source)
            .method("MOVE", null)
            .header("Destination", destination)
            .header("Overwrite", "T")
            .applyAuth(username, password)
            .build()
        return execute(request)
    }

    private suspend fun remove(url: String, username: String, password: String) {
        val request = Request.Builder()
            .url(url)
            .delete()
            .applyAuth(username, password)
            .build()
        val status = execute(request)
        if (status.code == 401 || status.code == 403) throw failure(status, "backup")
    }

    private suspend fun execute(request: Request): Status =
        client.await(request).use { response ->
            Status(response.code, response.body.string())
        }

    private fun failure(status: Status, action: String): WebDavException {
        val detail = serverMessage(status.body)
        val base = when (status.code) {
            401, 403 -> "Could not sign in to the WebDAV server."
            404 -> "Nothing was found at that WebDAV path."
            507 -> "The WebDAV server is out of space."
            else -> "Could not $action (${status.code})."
        }
        return WebDavException(status.code, if (detail == null) base else "$base $detail")
    }

    private fun serverMessage(body: String): String? =
        MESSAGE.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun Request.Builder.applyAuth(username: String, password: String): Request.Builder {
        if (username.isBlank() && password.isBlank()) return this
        return header("Authorization", Credentials.basic(username, password))
    }

    private suspend fun OkHttpClient.await(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(
                        WebDavException(0, e.message ?: "Could not reach the WebDAV server."),
                    )
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    if (continuation.isCancelled) {
                        response.close()
                        return
                    }
                    continuation.resume(response)
                }
            })
        }

    private data class Status(val code: Int, val body: String) {
        val ok: Boolean get() = code in 200..299
        val collection: Boolean get() = ok && (body.isBlank() || isCollection(body))
    }

    companion object {
        private val OCTET = "application/octet-stream".toMediaType()
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val MESSAGE = Regex(
            "<(?:[\\w.-]+:)?message>([^<]+)</(?:[\\w.-]+:)?message>",
            RegexOption.IGNORE_CASE,
        )

        internal fun normalizePath(path: String): String {
            val trimmed = path.trim().trim('/')
            if (trimmed.isEmpty()) {
                throw WebDavException(0, "Add a file name in the WebDAV file path.")
            }
            return trimmed
        }

        internal fun resolve(baseUrl: String, path: String): String {
            val base = canonical(baseUrl)
            val joined = "$base/${path.trim().trimStart('/')}"
            joined.toHttpUrlOrNull() ?: throw WebDavException(0, "The WebDAV URL is not valid.")
            return joined
        }

        internal fun collectionsFor(baseUrl: String, fileUrl: String): List<String> {
            val base = canonical(baseUrl).toHttpUrlOrNull()
                ?: throw WebDavException(0, "The WebDAV URL is not valid.")
            val parent = parentOf(fileUrl)
            val baseSegments = base.segments()
            val parentSegments = parent.segments()
            if (parentSegments.size < baseSegments.size ||
                parentSegments.take(baseSegments.size) != baseSegments
            ) {
                return listOf(parent.toString().trimEnd('/'))
            }
            val folders = mutableListOf(base.toString().trimEnd('/'))
            val builder = base.newBuilder()
            for (segment in parentSegments.drop(baseSegments.size)) {
                builder.addPathSegment(segment)
                folders += builder.build().toString().trimEnd('/')
            }
            return folders
        }

        private fun parentOf(fileUrl: String): HttpUrl {
            val url = fileUrl.toHttpUrlOrNull() ?: throw WebDavException(0, "The WebDAV URL is not valid.")
            val segments = url.segments()
            if (segments.isEmpty()) throw WebDavException(0, "The WebDAV URL is not valid.")
            return url.newBuilder().removePathSegment(url.pathSize - 1).build()
        }

        private fun canonical(url: String): String {
            val parsed = url.trim().trimEnd('/').toHttpUrlOrNull()
                ?: throw WebDavException(0, "The WebDAV URL is not valid.")
            return parsed.toString().trimEnd('/')
        }

        private fun asDirectory(url: String): String {
            val parsed = url.trim().trimEnd('/').toHttpUrlOrNull()
                ?: throw WebDavException(0, "The WebDAV URL is not valid.")
            return parsed.toString().trimEnd('/') + "/"
        }

        private fun HttpUrl.segments(): List<String> = pathSegments.filter { it.isNotEmpty() }

        private fun isCollection(body: String): Boolean {
            val lower = body.lowercase()
            return lower.contains(":collection") || lower.contains("<collection")
        }
    }
}
