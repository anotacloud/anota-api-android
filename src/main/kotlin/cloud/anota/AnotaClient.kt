package cloud.anota

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Raised for any non-2xx response from the anota API.
 *
 * @property status the HTTP status code returned by the server.
 */
class AnotaApiError(val status: Int, message: String) : Exception(message)

/**
 * A thin, dependency-free client for the [anota](https://anota.cloud) REST API.
 *
 * Built on [HttpURLConnection], so it runs unchanged on Android (API 21+) and on any
 * plain JVM. Every method performs a blocking network call and returns the server's raw
 * JSON as a [String] — parse it with `org.json` (bundled on Android), Moshi, kotlinx, or
 * whatever your project already uses. Bodies that carry structured data (`fields`,
 * `rules`, `answers`, `field`, `rule`) are passed in as JSON strings you build yourself.
 *
 * Android note: HttpURLConnection performs blocking I/O, so call these methods from a
 * background thread or a coroutine (`Dispatchers.IO`) — never from the main thread.
 *
 * @param apiKey a workspace API key (looks like `anota_sk_…`; create one at
 *   https://anota.cloud/api-keys).
 * @param baseUrl the API base URL; override only for self-hosted or testing.
 */
class AnotaClient(
    private val apiKey: String,
    baseUrl: String = "https://anota.cloud/api/v1",
) {
    init {
        require(apiKey.isNotEmpty()) {
            "apiKey is required (create one at https://anota.cloud/api-keys)"
        }
    }

    private val baseUrl: String = baseUrl.trimEnd('/')

    // ----- core -----

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        query: Map<String, String?> = emptyMap(),
    ): String {
        val queryString = query.entries
            .filter { it.value != null }
            .joinToString("&") { (k, v) -> "${enc(k)}=${enc(v!!)}" }
        val fullUrl = baseUrl + path + if (queryString.isEmpty()) "" else "?$queryString"

        val connection = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            setMethod(this, method)
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = 30_000
            readTimeout = 30_000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.readBytesCompat()?.toString(Charsets.UTF_8) ?: ""
            if (status !in 200..299) {
                throw AnotaApiError(status, extractMessage(text))
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    // ----- forms -----

    fun listForms(): String = request("GET", "/forms")

    fun createForm(title: String, fields: String, description: String? = null): String =
        request("POST", "/forms", obj("title" to str(title), "fields" to fields, "description" to description?.let { str(it) }))

    fun getForm(formId: String): String = request("GET", "/forms/$formId")

    fun addFields(formId: String, fields: String): String =
        request("POST", "/forms/$formId/fields", obj("fields" to fields))

    fun editField(formId: String, fieldId: String, field: String): String =
        request("PATCH", "/forms/$formId/fields/${enc(fieldId)}", obj("field" to field))

    fun deleteField(formId: String, fieldId: String): String =
        request("DELETE", "/forms/$formId/fields/${enc(fieldId)}")

    fun publishForm(formId: String): String = request("POST", "/forms/$formId/publish")

    fun renameForm(formId: String, title: String): String =
        request("PATCH", "/forms/$formId", obj("title" to str(title)))

    fun setPdfTemplate(formId: String, key: String): String =
        request("PUT", "/forms/$formId/pdf-template", obj("key" to str(key)))

    fun deleteForm(formId: String): String = request("DELETE", "/forms/$formId")

    fun cloneForm(formId: String): String = request("POST", "/forms/$formId/clone")

    // ----- logic rules -----

    fun addLogicRules(formId: String, rules: String): String =
        request("POST", "/forms/$formId/logic-rules", obj("rules" to rules))

    fun editLogicRule(formId: String, ruleId: String, rule: String): String =
        request("PUT", "/forms/$formId/logic-rules/${enc(ruleId)}", obj("rule" to rule))

    fun deleteLogicRule(formId: String, ruleId: String): String =
        request("DELETE", "/forms/$formId/logic-rules/${enc(ruleId)}")

    // ----- submissions -----

    fun listSubmissions(formId: String, page: Int = 1, pageSize: Int = 25, status: String? = null): String =
        request("GET", "/forms/$formId/submissions", null, mapOf("page" to page.toString(), "pageSize" to pageSize.toString(), "status" to status))

    fun getSubmission(submissionId: String): String = request("GET", "/submissions/$submissionId")

    fun createSubmission(formId: String, answers: String): String =
        request("POST", "/forms/$formId/submissions", obj("answers" to answers))

    fun setSubmissionStatus(submissionId: String, status: String): String =
        request("PATCH", "/submissions/$submissionId/status", obj("status" to str(status)))

    fun deleteSubmission(submissionId: String): String = request("DELETE", "/submissions/$submissionId")

    fun submissionStats(formId: String): String = request("GET", "/forms/$formId/stats")

    // ----- templates -----

    fun listTemplates(language: String = "es"): String =
        request("GET", "/templates", null, mapOf("language" to language))

    fun createFormFromTemplate(templateId: String): String =
        request("POST", "/forms/from-template/$templateId")

    // ----- webhooks -----

    /**
     * Lists a form's webhooks. Each row has id, url, events, enabled, secretHint and secretNote.
     * The full signing secret is never returned here: secretHint is a masked form
     * ("whsec_…" + last 4 characters, or just "whsec_…" for short secrets) that identifies
     * which secret a receiver holds, and secretNote explains the show-once rule. To replace a
     * lost secret, delete the webhook and add it again.
     */
    fun listWebhooks(formId: String): String = request("GET", "/forms/$formId/webhooks")

    /**
     * Registers a webhook URL that receives submission.created events. The response
     * (id, formId, url, secret, note) is the ONLY place the full signing secret appears:
     * store it now, it cannot be read back later (listWebhooks shows only secretHint).
     */
    fun addWebhook(formId: String, url: String): String =
        request("POST", "/forms/$formId/webhooks", obj("url" to str(url)))

    fun deleteWebhook(formId: String, webhookId: String): String =
        request("DELETE", "/forms/$formId/webhooks/$webhookId")

    // ----- helpers -----

    /** Assemble a JSON object from `name to rawJsonFragment` pairs, skipping null values. */
    private fun obj(vararg pairs: Pair<String, String?>): String =
        pairs.filter { it.second != null }
            .joinToString(",", "{", "}") { "${str(it.first)}:${it.second}" }

    /** Encode a plain string as a JSON string literal (with surrounding quotes). */
    private fun str(value: String): String {
        val sb = StringBuilder(value.length + 2).append('"')
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Pull the human-readable message out of an ASP.NET problem-details body. */
    private fun extractMessage(body: String): String =
        jsonStringValue(body, "detail") ?: jsonStringValue(body, "title") ?: body

    private fun setMethod(connection: HttpURLConnection, method: String) {
        try {
            connection.requestMethod = method
        } catch (e: java.net.ProtocolException) {
            // Legacy JVM HttpURLConnection rejects PATCH; Android's OkHttp-backed
            // implementation accepts it above. Fall back to forcing the field.
            try {
                val field = HttpURLConnection::class.java.getDeclaredField("method")
                field.isAccessible = true
                field.set(connection, method)
            } catch (_: Exception) {
                throw e
            }
        }
    }
}

/** Read all bytes from a stream (Android API 21 lacks `InputStream.readAllBytes`). */
private fun java.io.InputStream.readBytesCompat(): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (true) {
        val read = this.read(chunk)
        if (read < 0) break
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
}

/** Minimal, dependency-free extraction of a top-level JSON string value by key. */
private fun jsonStringValue(json: String, key: String): String? {
    val marker = "\"$key\""
    val keyIndex = json.indexOf(marker)
    if (keyIndex < 0) return null
    var i = json.indexOf(':', keyIndex + marker.length)
    if (i < 0) return null
    i++
    while (i < json.length && json[i].isWhitespace()) i++
    if (i >= json.length || json[i] != '"') return null
    i++
    val sb = StringBuilder()
    while (i < json.length) {
        val c = json[i]
        when {
            c == '\\' && i + 1 < json.length -> {
                when (val next = json[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    else -> sb.append(next)
                }
                i += 2
            }
            c == '"' -> return sb.toString()
            else -> {
                sb.append(c)
                i++
            }
        }
    }
    return null
}
