package org.anush.bahubhashik.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A request the server refused, carrying its own explanation.
 *
 * Without this, Ktor happily tries to deserialize an error body into the
 * success type and the user sees a serialization stack trace instead of
 * "that PIN doesn't match".
 */
class ApiException(val status: Int, override val message: String) : Exception(message)

class Api(private val baseUrl: String = ServerConfig.baseUrl) {

    private val errorJson = Json { ignoreUnknownKeys = true }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.isSuccess()) return@validateResponse
                val body = runCatching { response.bodyAsText() }.getOrNull()
                val explanation = body
                    ?.let {
                        runCatching {
                            errorJson.parseToJsonElement(it).jsonObject["error"]?.jsonPrimitive?.content
                        }.getOrNull()
                    }
                    ?: "The server returned ${response.status.value}."
                throw ApiException(response.status.value, explanation)
            }
        }
        install(HttpTimeout) {
            // Uploading five minutes of audio over patchy mobile data is slow;
            // the pipeline itself is polled separately, so this only covers upload.
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }

    /**
     * Is the server awake? On Render's free plan it sleeps after fifteen
     * minutes idle and takes the better part of a minute to come back, so
     * this is polled behind a waiting screen rather than assumed.
     *
     * Short per-attempt timeout: we want to fail fast and retry, not hang.
     */
    suspend fun isAwake(): Boolean = try {
        client.get("$baseUrl/health") {
            timeout { requestTimeoutMillis = 10_000; connectTimeoutMillis = 10_000 }
        }.status.isSuccess()
    } catch (_: Exception) {
        false
    }

    /** Does this name exist, and has it got a PIN yet? */
    suspend fun lookUp(username: String): Lookup =
        client.get("$baseUrl/users/lookup") { parameter("username", username) }.body()

    suspend fun createAccount(username: String, language: String, voice: String, pin: String): User =
        postJson(
            "$baseUrl/users",
            "username" to username,
            "language" to language,
            "voice" to voice,
            "pin" to pin,
        )

    /** Verified server-side; the stored PIN is never sent to the app. */
    suspend fun signIn(username: String, pin: String): User =
        postJson("$baseUrl/users/login", "username" to username, "pin" to pin)

    /** First PIN for someone who signed up before PINs existed. */
    suspend fun setPin(username: String, pin: String): User =
        postJson("$baseUrl/users/pin", "username" to username, "pin" to pin)

    private suspend inline fun <reified T> postJson(url: String, vararg fields: Pair<String, String>): T =
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(JsonObject(fields.associate { (k, v) -> k to JsonPrimitive(v) }))
        }.body()

    suspend fun everyone(): List<User> =
        client.get("$baseUrl/users").body<UsersResponse>().users

    /** Messages addressed to this person. */
    suspend fun inbox(username: String): List<Message> =
        client.get("$baseUrl/messages") {
            parameter("user", username)
        }.body<MessagesResponse>().messages

    /** Messages this person has sent to everyone else. */
    suspend fun sent(username: String): List<Message> =
        client.get("$baseUrl/messages") {
            parameter("user", username)
            parameter("box", "sent")
        }.body<MessagesResponse>().messages

    /** One conversation, both directions. */
    suspend fun conversation(username: String, with: String): List<Message> =
        client.get("$baseUrl/messages") {
            parameter("user", username)
            parameter("with", with)
        }.body<MessagesResponse>().messages

    /** Messages this person translated for themselves, to share outside the app. */
    suspend fun composed(username: String): List<Message> =
        client.get("$baseUrl/messages") {
            parameter("user", username)
            parameter("box", "composed")
        }.body<MessagesResponse>().messages

    suspend fun message(id: String): Message =
        client.get("$baseUrl/messages/$id").body()

    /**
     * Fetch a signed audio URL's bytes. Points at Supabase storage rather
     * than our own API, but the same failure handling applies.
     */
    suspend fun download(url: String): ByteArray = client.get(url).body()

    /** Re-run a failed message's pipeline without re-recording it. */
    suspend fun retry(id: String) {
        client.post("$baseUrl/messages/$id/retry")
    }

    /** A community message: the output language is whatever the recipient speaks. */
    suspend fun send(
        sender: String,
        recipient: String,
        audio: ByteArray,
        filename: String,
        durationSeconds: Double,
    ): Message = upload(sender, "recipient" to recipient, audio, filename, durationSeconds)

    /** A composed message: translated into [targetLang] for the sender alone. */
    suspend fun compose(
        sender: String,
        targetLang: String,
        audio: ByteArray,
        filename: String,
        durationSeconds: Double,
    ): Message = upload(sender, "targetLang" to targetLang, audio, filename, durationSeconds)

    private suspend fun upload(
        sender: String,
        destination: Pair<String, String>,
        audio: ByteArray,
        filename: String,
        durationSeconds: Double,
    ): Message =
        client.post("$baseUrl/messages") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("sender", sender)
                        append(destination.first, destination.second)
                        append("durationSeconds", durationSeconds.toString())
                        append(
                            "audio",
                            audio,
                            Headers.build {
                                append(HttpHeaders.ContentType, "audio/m4a")
                                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                            },
                        )
                    },
                ),
            )
        }.body()
}
