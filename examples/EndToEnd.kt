package examples

import cloud.anota.AnotaApiError
import cloud.anota.AnotaClient

/**
 * End-to-end walk-through of the anota API: create a form, add a field, publish it,
 * record a submission, then list submissions back.
 *
 * The client returns raw JSON strings — this example prints them as-is and pulls the two
 * ids it needs (the form id and its first field id) with a tiny string scan so it stays
 * dependency-free. In a real app, parse with org.json (Android), Moshi, or kotlinx.
 *
 * Run after building the library, with your key in the environment:
 *
 *   export ANOTA_API_KEY=anota_sk_...
 *   kotlinc examples/EndToEnd.kt src/main/kotlin/cloud/anota/AnotaClient.kt -include-runtime -d demo.jar
 *   java -jar demo.jar
 */
fun main() {
    val apiKey = System.getenv("ANOTA_API_KEY")
        ?: error("Set ANOTA_API_KEY to a workspace API key (https://anota.cloud/api-keys)")

    val client = AnotaClient(apiKey)

    try {
        println("1. Creating a form…")
        val form = client.createForm(
            title = "Customer feedback",
            fields = """[{"type":"text","label":"Your name","required":true}]""",
            description = "Collected from the Android SDK example",
        )
        println(form)
        val formId = firstStringValue(form, "id") ?: error("no form id in response")

        println("\n2. Adding an email field…")
        val withField = client.addFields(
            formId,
            """[{"type":"email","label":"Your email","required":true}]""",
        )
        println(withField)

        println("\n3. Publishing the form…")
        println(client.publishForm(formId))

        println("\n4. Recording a submission…")
        val nameFieldId = firstStringValue(form, "id", afterKey = "fields") ?: "f_1"
        val submission = client.createSubmission(
            formId,
            """{"$nameFieldId":"Ada Lovelace"}""",
        )
        println(submission)

        println("\n5. Listing submissions…")
        println(client.listSubmissions(formId, page = 1, pageSize = 25))
    } catch (e: AnotaApiError) {
        System.err.println("API error ${e.status}: ${e.message}")
    }
}

/** Naive helper: first string value for [key] (optionally after the first [afterKey]). */
private fun firstStringValue(json: String, key: String, afterKey: String? = null): String? {
    val start = if (afterKey == null) 0 else json.indexOf("\"$afterKey\"").let { if (it < 0) 0 else it }
    val marker = "\"$key\""
    var i = json.indexOf(marker, start)
    if (i < 0) return null
    i = json.indexOf('"', json.indexOf(':', i + marker.length) + 1)
    if (i < 0) return null
    val end = json.indexOf('"', i + 1)
    if (end < 0) return null
    return json.substring(i + 1, end)
}
