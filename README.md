# anota-api-android · Official Android/Kotlin client for the [anota](https://anota.cloud) API

**[Léeme en español](README.es.md)** · [Interactive API reference](https://anota.cloud/developers) · [All SDKs](https://github.com/anotacloud/anota-api)

![CI](https://github.com/anotacloud/anota-api-android/actions/workflows/ci.yml/badge.svg)

Create and publish forms, edit fields and conditional logic, read and write
submissions, and wire webhooks — everything the anota REST API can do, from Kotlin.

It's a thin, dependency-free client built on `HttpURLConnection`, so it runs unchanged on
Android (API 21+) and on any plain JVM. Every method returns the server's raw JSON as a
`String`; parse it with `org.json` (bundled on Android), Moshi, or kotlinx.serialization —
whatever your project already uses.

> **Android:** these methods perform blocking network I/O, so call them from a background
> thread or a coroutine on `Dispatchers.IO` — never from the main/UI thread.

## Install

**Gradle via [JitPack](https://jitpack.io):**

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.anotacloud:anota-api-android:v1.0.0")
}
```

Or download the [ZIP](https://github.com/anotacloud/anota-api-android/archive/refs/heads/main.zip) /
[Tarball](https://github.com/anotacloud/anota-api-android/archive/refs/heads/main.tar.gz)
and drop `src/main/kotlin/cloud/anota/AnotaClient.kt` into your project — it has no
dependencies beyond the Kotlin and Java standard libraries.

## Quickstart

```kotlin
import cloud.anota.AnotaClient

val client = AnotaClient(System.getenv("ANOTA_API_KEY"))

// Create a form with one text field, then publish it.
val form = client.createForm(
    title = "Customer feedback",
    fields = """[{"type":"text","label":"Your name","required":true}]""",
)
// (parse `form` to read its id — org.json / Moshi / kotlinx)
val formId = org.json.JSONObject(form).getString("id")
client.publishForm(formId)

// Read submissions back.
println(client.listSubmissions(formId, page = 1, pageSize = 25))
```

On Android, wrap those calls in a coroutine:

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val forms = client.listForms()
    // …update UI on the main dispatcher
}
```

A complete create → add field → publish → submit → list script lives in
[`examples/EndToEnd.kt`](examples/EndToEnd.kt).

### Passing structured data

`fields`, `rules`, and `answers` are passed as JSON strings you build yourself (with
`org.json`, kotlinx.serialization, or a plain string). Simple scalar bodies — a title, a
status, a webhook URL — are taken as ordinary Kotlin values and encoded for you.

## Authentication

Create an API key in your workspace at https://anota.cloud/api-keys and pass it to the
client. Keys look like `anota_sk_…` and also power the Claude MCP connector.

```kotlin
val client = AnotaClient("anota_sk_live_…")
// self-hosted or testing: AnotaClient(apiKey, baseUrl = "https://example.test/api/v1")
```

## All methods

Every method blocks and returns the raw JSON response as a `String`.

| # | Method | HTTP |
|---|---|---|
| 1 | `listForms()` | `GET /forms` |
| 2 | `createForm(title, fields, description? = null)` | `POST /forms` |
| 3 | `getForm(formId)` | `GET /forms/{formId}` |
| 4 | `addFields(formId, fields)` | `POST /forms/{formId}/fields` |
| 5 | `editField(formId, fieldId, field)` | `PATCH /forms/{formId}/fields/{fieldId}` |
| 6 | `deleteField(formId, fieldId)` | `DELETE /forms/{formId}/fields/{fieldId}` |
| 7 | `publishForm(formId)` | `POST /forms/{formId}/publish` |
| 8 | `renameForm(formId, title)` | `PATCH /forms/{formId}` |
| 9 | `setPdfTemplate(formId, key)` | `PUT /forms/{formId}/pdf-template` |
| 10 | `deleteForm(formId)` | `DELETE /forms/{formId}` |
| 11 | `cloneForm(formId)` | `POST /forms/{formId}/clone` |
| 12 | `addLogicRules(formId, rules)` | `POST /forms/{formId}/logic-rules` |
| 13 | `editLogicRule(formId, ruleId, rule)` | `PUT /forms/{formId}/logic-rules/{ruleId}` |
| 14 | `deleteLogicRule(formId, ruleId)` | `DELETE /forms/{formId}/logic-rules/{ruleId}` |
| 15 | `listSubmissions(formId, page = 1, pageSize = 25, status? = null)` | `GET /forms/{formId}/submissions` |
| 16 | `getSubmission(submissionId)` | `GET /submissions/{submissionId}` |
| 17 | `createSubmission(formId, answers)` | `POST /forms/{formId}/submissions` |
| 18 | `setSubmissionStatus(submissionId, status)` | `PATCH /submissions/{submissionId}/status` |
| 19 | `deleteSubmission(submissionId)` | `DELETE /submissions/{submissionId}` |
| 20 | `submissionStats(formId)` | `GET /forms/{formId}/stats` |
| 21 | `listTemplates(language = "es")` | `GET /templates` |
| 22 | `createFormFromTemplate(templateId)` | `POST /forms/from-template/{templateId}` |
| 23 | `listWebhooks(formId)` | `GET /forms/{formId}/webhooks` |
| 24 | `addWebhook(formId, url)` | `POST /forms/{formId}/webhooks` |
| 25 | `deleteWebhook(formId, webhookId)` | `DELETE /forms/{formId}/webhooks/{webhookId}` |

`fields`/`field` are JSON like `{"type":…,"label":…,"required":…,"options":…}`;
`rules`/`rule` are `{"match":"all"|"any","if":[…],"then":[…]}`; `answers` is a JSON object
keyed by field id whose values are strings or string arrays.

## Errors

Non-2xx responses raise `AnotaApiError` with the HTTP `status` and the server's message.

```kotlin
try {
    client.publishForm(formId)
} catch (e: AnotaApiError) {
    println("anota returned ${e.status}: ${e.message}")
}
```

Note: once a form has been published, its existing fields are locked (`editField` /
`deleteField` return 400); you can always `addFields`.

Network failures surface as the platform's native `java.io.IOException`, not wrapped.

## License

MIT
