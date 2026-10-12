package app.rota.data.backup

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class WebDavClientTest {
    private val server = MockWebServer()
    private val client = WebDavClient()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test(timeout = 20_000)
    fun uploadsIntoAnExistingFolder() = runBlocking {
        server.enqueue(collection())
        server.enqueue(response(201))

        client.put(base(), "user", "secret", "rota-backup.json", "{}".toByteArray())

        val probe = server.takeRequest()
        assertThat(probe.method).isEqualTo("PROPFIND")
        assertThat(probe.path).isEqualTo("/dav/")
        assertThat(probe.getHeader("Authorization")).isEqualTo(Credentials.basic("user", "secret"))
        val put = server.takeRequest()
        assertThat(put.method).isEqualTo("PUT")
        assertThat(put.path).isEqualTo("/dav/rota-backup.json")
        assertThat(put.body.readUtf8()).isEqualTo("{}")
        assertThat(put.getHeader("Content-Type")).isEqualTo("application/octet-stream")
        assertThat(put.getHeader("X-NC-WebDAV-AutoMkcol")).isEqualTo("1")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test(timeout = 20_000)
    fun createsMissingFoldersThenUploads() = runBlocking {
        server.enqueue(collection())
        server.enqueue(response(404))
        server.enqueue(response(201))
        server.enqueue(response(201))

        client.put(base(), "user", "secret", "Rota/rota-backup.json", "backup".toByteArray())

        assertThat(server.takeRequest().run { method to path }).isEqualTo("PROPFIND" to "/dav/")
        assertThat(server.takeRequest().run { method to path }).isEqualTo("PROPFIND" to "/dav/Rota/")
        assertThat(server.takeRequest().run { method to path }).isEqualTo("MKCOL" to "/dav/Rota/")
        val put = server.takeRequest()
        assertThat(put.method).isEqualTo("PUT")
        assertThat(put.path).isEqualTo("/dav/Rota/rota-backup.json")
    }

    @Test(timeout = 20_000)
    fun createsAMissingFolderThatIsPartOfTheServerUrl() = runBlocking {
        server.enqueue(response(404))
        server.enqueue(response(201))
        server.enqueue(response(201))

        client.put("${base()}/Rota", "user", "secret", "rota-backup.json", "backup".toByteArray())

        assertThat(server.takeRequest().run { method to path }).isEqualTo("PROPFIND" to "/dav/Rota/")
        assertThat(server.takeRequest().run { method to path }).isEqualTo("MKCOL" to "/dav/Rota/")
        assertThat(server.takeRequest().path).isEqualTo("/dav/Rota/rota-backup.json")
    }

    @Test(timeout = 20_000)
    fun replacesAnExistingFileWhenPutReturns409() = runBlocking {
        server.enqueue(collection())
        server.enqueue(response(409, "<d:error><s:message>Locked name</s:message></d:error>"))
        server.enqueue(file())
        server.enqueue(response(201))
        server.enqueue(response(204))

        client.put(base(), "user", "secret", "rota-backup.json", "new".toByteArray())

        assertThat(server.takeRequest().method).isEqualTo("PROPFIND")
        assertThat(server.takeRequest().run { method to path }).isEqualTo("PUT" to "/dav/rota-backup.json")
        assertThat(server.takeRequest().run { method to path }).isEqualTo("PROPFIND" to "/dav/rota-backup.json")
        assertThat(server.takeRequest().run { method to path })
            .isEqualTo("PUT" to "/dav/rota-backup.json.rota-upload")
        val move = server.takeRequest()
        assertThat(move.method).isEqualTo("MOVE")
        assertThat(move.path).isEqualTo("/dav/rota-backup.json.rota-upload")
        assertThat(move.getHeader("Destination")).isEqualTo("${base()}/rota-backup.json")
        assertThat(move.getHeader("Overwrite")).isEqualTo("T")
    }

    @Test(timeout = 20_000)
    fun putsAgainWhenTheServerCannotMoveOverTheOldFile() = runBlocking {
        server.enqueue(collection())
        server.enqueue(response(409))
        server.enqueue(file())
        server.enqueue(response(201))
        server.enqueue(response(405))
        server.enqueue(response(204))
        server.enqueue(response(405))
        server.enqueue(response(204))
        server.enqueue(response(204))

        client.put(base(), "user", "secret", "rota-backup.json", "new".toByteArray())

        val methods = List(server.requestCount) { server.takeRequest().method }
        assertThat(methods).containsExactly(
            "PROPFIND",
            "PUT",
            "PROPFIND",
            "PUT",
            "MOVE",
            "DELETE",
            "MOVE",
            "PUT",
            "DELETE",
        ).inOrder()
    }

    @Test(timeout = 20_000)
    fun reportsTheServerMessageWhenTheFolderCannotBeUsed() = runBlocking {
        server.enqueue(collection())
        server.enqueue(
            response(
                409,
                "<d:error><s:message>Files cannot be created in non-existent collections</s:message></d:error>",
            ),
        )
        server.enqueue(response(404))

        val error = assertThrows(WebDavException::class.java) {
            runBlocking {
                client.put(base(), "user", "secret", "rota-backup.json", "new".toByteArray())
            }
        }
        assertThat(error.status).isEqualTo(409)
        assertThat(error.message).contains("Files cannot be created in non-existent collections")
    }

    @Test(timeout = 20_000)
    fun connectionTestDoesNotTreat409AsSuccess() {
        server.enqueue(response(409))
        server.enqueue(response(409))
        server.enqueue(response(409))

        val error = assertThrows(WebDavException::class.java) {
            runBlocking { client.test(base(), "user", "secret", "rota-backup.json") }
        }
        assertThat(error.status).isEqualTo(409)
        assertThat(error.message).contains("Could not create the WebDAV folder")
    }

    @Test(timeout = 20_000)
    fun connectionTestCreatesAMissingFolder() = runBlocking {
        server.enqueue(response(404))
        server.enqueue(response(201))

        client.test(base(), "user", "secret", "rota-backup.json")

        assertThat(server.takeRequest().method).isEqualTo("PROPFIND")
        assertThat(server.takeRequest().method).isEqualTo("MKCOL")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test(timeout = 20_000)
    fun connectionTestRejectsBadCredentials() {
        server.enqueue(response(401))

        val error = assertThrows(WebDavException::class.java) {
            runBlocking { client.test(base(), "user", "wrong", "rota-backup.json") }
        }
        assertThat(error.message).contains("Could not sign in")
    }

    @Test(timeout = 20_000)
    fun refusesAServerUrlThatPointsAtAFile() {
        server.enqueue(file())

        val error = assertThrows(WebDavException::class.java) {
            runBlocking { client.put(base(), "user", "secret", "rota-backup.json", "new".toByteArray()) }
        }
        assertThat(error.message).contains("Point the server URL at a folder")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test(timeout = 20_000)
    fun treatsNginxConflictOnMkcolAsAnExistingFolder() = runBlocking {
        server.enqueue(response(404))
        server.enqueue(response(409))
        server.enqueue(collection())
        server.enqueue(response(204))

        client.put(base(), "user", "secret", "rota-backup.json", "backup".toByteArray())

        assertThat(server.takeRequest().method).isEqualTo("PROPFIND")
        assertThat(server.takeRequest().method).isEqualTo("MKCOL")
        assertThat(server.takeRequest().method).isEqualTo("PROPFIND")
        assertThat(server.takeRequest().method).isEqualTo("PUT")
    }

    private fun base(): String = server.url("/dav/").toString().trimEnd('/')

    private fun collection(): MockResponse = response(
        207,
        """
        <d:multistatus xmlns:d="DAV:">
          <d:response><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
        </d:multistatus>
        """.trimIndent(),
    )

    private fun file(): MockResponse = response(
        207,
        """
        <d:multistatus xmlns:d="DAV:">
          <d:response><d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response>
        </d:multistatus>
        """.trimIndent(),
    )

    private fun response(code: Int, body: String = ""): MockResponse =
        MockResponse().setResponseCode(code).setBody(body)
}
