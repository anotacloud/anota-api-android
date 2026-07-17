# anota-api-android · Cliente oficial de Android/Kotlin para la API de [anota](https://anota.cloud)

**[Read me in English](README.md)** · [Referencia interactiva de la API](https://anota.cloud/developers) · [Todos los SDK](https://github.com/anotacloud/anota-api)

![CI](https://github.com/anotacloud/anota-api-android/actions/workflows/ci.yml/badge.svg)

Crea y publica formularios, edita campos y lógica condicional, lee y escribe respuestas,
y conecta webhooks — todo lo que la API REST de anota puede hacer, desde Kotlin.

Es un cliente liviano y sin dependencias construido sobre `HttpURLConnection`, así que
funciona sin cambios en Android (API 21+) y en cualquier JVM. Cada método devuelve el JSON
crudo del servidor como un `String`; analízalo con `org.json` (incluido en Android), Moshi
o kotlinx.serialization — lo que tu proyecto ya use.

> **Android:** estos métodos realizan E/S de red bloqueante, así que llámalos desde un
> hilo en segundo plano o una corrutina en `Dispatchers.IO` — nunca desde el hilo
> principal/de interfaz.

## Instalación

**Gradle vía [JitPack](https://jitpack.io):**

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

O descarga el [ZIP](https://github.com/anotacloud/anota-api-android/archive/refs/heads/main.zip) /
[Tarball](https://github.com/anotacloud/anota-api-android/archive/refs/heads/main.tar.gz)
y coloca `src/main/kotlin/cloud/anota/AnotaClient.kt` en tu proyecto — no tiene
dependencias más allá de las bibliotecas estándar de Kotlin y Java.

## Inicio rápido

```kotlin
import cloud.anota.AnotaClient

val client = AnotaClient(System.getenv("ANOTA_API_KEY"))

// Crea un formulario con un campo de texto y publícalo.
val form = client.createForm(
    title = "Opiniones de clientes",
    fields = """[{"type":"text","label":"Tu nombre","required":true}]""",
)
// (analiza `form` para leer su id — org.json / Moshi / kotlinx)
val formId = org.json.JSONObject(form).getString("id")
client.publishForm(formId)

// Lee las respuestas.
println(client.listSubmissions(formId, page = 1, pageSize = 25))
```

En Android, envuelve esas llamadas en una corrutina:

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val forms = client.listForms()
    // …actualiza la interfaz en el dispatcher principal
}
```

Un script completo de crear → agregar campo → publicar → enviar → listar está en
[`examples/EndToEnd.kt`](examples/EndToEnd.kt).

### Pasar datos estructurados

`fields`, `rules` y `answers` se pasan como cadenas JSON que construyes tú mismo (con
`org.json`, kotlinx.serialization o una cadena simple). Los cuerpos escalares simples — un
título, un estado, la URL de un webhook — se toman como valores normales de Kotlin y se
codifican por ti.

## Autenticación

Crea una clave de API en tu workspace en https://anota.cloud/api-keys y pásala al cliente.
Las claves tienen el formato `anota_sk_…` y también habilitan el conector MCP de Claude.

```kotlin
val client = AnotaClient("anota_sk_live_…")
// autoalojado o pruebas: AnotaClient(apiKey, baseUrl = "https://example.test/api/v1")
```

## Todos los métodos

Cada método es bloqueante y devuelve la respuesta JSON cruda como un `String`.

| # | Método | HTTP |
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

`fields`/`field` son JSON como `{"type":…,"label":…,"required":…,"options":…}`;
`rules`/`rule` son `{"match":"all"|"any","if":[…],"then":[…]}`; `answers` es un objeto JSON
indexado por id de campo cuyos valores son cadenas o arreglos de cadenas.

## Errores

Las respuestas que no sean 2xx lanzan `AnotaApiError` con el `status` HTTP y el mensaje del
servidor.

```kotlin
try {
    client.publishForm(formId)
} catch (e: AnotaApiError) {
    println("anota devolvió ${e.status}: ${e.message}")
}
```

Nota: una vez que un formulario ha sido publicado, sus campos existentes quedan bloqueados
(`editField` / `deleteField` devuelven 400); siempre puedes usar `addFields`.

Los fallos de red se propagan como la excepción nativa de la plataforma
(`java.io.IOException`), sin envolver.

## Licencia

MIT
