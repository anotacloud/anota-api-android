package cloud.anota

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnotaClientTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    // Captured from the last request the server received.
    private var lastMethod: String? = null
    private var lastPath: String? = null
    private var lastQuery: String? = null
    private var lastAuth: String? = null
    private var lastBody: String? = null

    // Canned response the server returns.
    private var responseStatus = 200
    private var responseBody = "{}"

    @BeforeTest
    fun setup() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            lastMethod = exchange.requestMethod
            lastPath = exchange.requestURI.path
            lastQuery = exchange.requestURI.rawQuery
            lastAuth = exchange.requestHeaders.getFirst("Authorization")
            lastBody = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val bytes = responseBody.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(responseStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/api/v1"
    }

    @AfterTest
    fun teardown() {
        server.stop(0)
    }

    @Test
    fun listFormsSendsGetWithBearerHeader() {
        responseBody = "[]"
        AnotaClient("anota_sk_test", baseUrl).listForms()
        assertEquals("GET", lastMethod)
        assertEquals("/api/v1/forms", lastPath)
        assertEquals("Bearer anota_sk_test", lastAuth)
    }

    @Test
    fun createSubmissionSendsJsonBody() {
        AnotaClient("k", baseUrl).createSubmission("form_1", """{"f_1":"hola"}""")
        assertEquals("POST", lastMethod)
        assertEquals("/api/v1/forms/form_1/submissions", lastPath)
        assertEquals("""{"answers":{"f_1":"hola"}}""", lastBody)
    }

    @Test
    fun nonSuccessResponseThrowsAnotaApiError() {
        responseStatus = 400
        responseBody = """{"detail":"Error: bad"}"""
        val error = assertFailsWith<AnotaApiError> {
            AnotaClient("k", baseUrl).listForms()
        }
        assertEquals(400, error.status)
        assertEquals("Error: bad", error.message)
    }

    @Test
    fun listSubmissionsBuildsPagingQuery() {
        AnotaClient("k", baseUrl).listSubmissions("form_1", 2, 10, "New")
        assertEquals("page=2&pageSize=10&status=New", lastQuery)
    }

    @Test
    fun listSubmissionsOmitsNullStatus() {
        AnotaClient("k", baseUrl).listSubmissions("form_1")
        assertEquals("page=1&pageSize=25", lastQuery)
    }
}
