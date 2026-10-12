package app.rota.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Credentials
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
        val url = resolve(baseUrl, path)
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", null)
            .header("Depth", "0")
            .applyAuth(username, password)
            .header("Accept", "*/*")
            .build()
        client.await(request).use { response ->
            when (response.code) {
                in 200..299, 404, 405, 409 -> Unit
                401, 403 -> throw WebDavException(response.code, "Could not sign in to the WebDAV server.")
                else -> throw WebDavException(response.code, "WebDAV responded with ${response.code}.")
            }
        }
    }

    suspend fun put(
        baseUrl: String,
        username: String,
        password: String,
        path: String,
        bytes: ByteArray,
    ) {
        val url = resolve(baseUrl, path)
        ensureParent(baseUrl, username, password, path)
        val request = Request.Builder()
            .url(url)
            .put(bytes.toRequestBody(JSON))
            .applyAuth(username, password)
            .header("Accept", "*/*")
            .build()
        client.await(request).use { response ->
            if (!response.isSuccessful) {
                throw WebDavException(response.code, errorMessage(response, "backup"))
            }
        }
    }

    suspend fun get(
        baseUrl: String,
        username: String,
        password: String,
        path: String,
    ): ByteArray {
        val url = resolve(baseUrl, path)
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
                throw WebDavException(response.code, errorMessage(response, "restore"))
            }
            withContext(Dispatchers.IO) {
                response.body?.bytes() ?: throw WebDavException(response.code, "Empty backup file.")
            }
        }
    }

    private suspend fun ensureParent(
        baseUrl: String,
        username: String,
        password: String,
        path: String,
    ) {
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "").trim('/')
        if (parent.isEmpty()) return
        val url = resolve(baseUrl, "$parent/")
        val request = Request.Builder()
            .url(url)
            .method("MKCOL", null)
            .applyAuth(username, password)
            .build()
        client.await(request).use { response ->
            if (response.code !in listOf(201, 405, 409, 301, 200)) {
                if (response.code == 401 || response.code == 403) {
                    throw WebDavException(response.code, "Could not sign in to the WebDAV server.")
                }
            }
        }
    }

    private fun resolve(baseUrl: String, path: String): String {
        val trimmedBase = baseUrl.trim().trimEnd('/')
        val trimmedPath = path.trim().trimStart('/')
        val joined = "$trimmedBase/$trimmedPath"
        joined.toHttpUrlOrNull() ?: throw WebDavException(0, "The WebDAV URL is not valid.")
        return joined
    }

    private fun Request.Builder.applyAuth(username: String, password: String): Request.Builder {
        if (username.isBlank() && password.isBlank()) return this
        return header("Authorization", Credentials.basic(username, password))
    }

    private fun errorMessage(response: Response, action: String): String = when (response.code) {
        401, 403 -> "Could not sign in to the WebDAV server."
        404 -> "Nothing was found at that WebDAV path."
        507 -> "The WebDAV server is out of space."
        else -> "Could not $action (${response.code})."
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
                    continuation.resume(response)
                }
            })
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
