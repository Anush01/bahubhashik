package org.anush.bahubhashik.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class Api(private val baseUrl: String = ServerConfig.baseUrl) {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
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

    suspend fun signUp(username: String, language: String, voice: String): User =
        client.post("$baseUrl/users") {
            contentType(ContentType.Application.Json)
            setBody(
                JsonObject(
                    mapOf(
                        "username" to JsonPrimitive(username),
                        "language" to JsonPrimitive(language),
                        "voice" to JsonPrimitive(voice),
                    ),
                ),
            )
        }.body()

    suspend fun everyone(): List<User> =
        client.get("$baseUrl/users").body<UsersResponse>().users

    /** Messages addressed to this person. */
    suspend fun inbox(username: String): List<Message> =
        client.get("$baseUrl/messages") {
            parameter("user", username)
        }.body<MessagesResponse>().messages

    /** One conversation, both directions. */
    suspend fun conversation(username: String, with: String): List<Message> =
        client.get("$baseUrl/messages") {
            parameter("user", username)
            parameter("with", with)
        }.body<MessagesResponse>().messages

    suspend fun message(id: String): Message =
        client.get("$baseUrl/messages/$id").body()

    /** Re-run a failed message's pipeline without re-recording it. */
    suspend fun retry(id: String) {
        client.post("$baseUrl/messages/$id/retry")
    }

    suspend fun send(
        sender: String,
        recipient: String,
        audio: ByteArray,
        filename: String,
        durationSeconds: Double,
    ): Message =
        client.post("$baseUrl/messages") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("sender", sender)
                        append("recipient", recipient)
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
