package org.anush.bahubhashik.data

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val username: String,
    val language: String,
    val voice: String = "female",
)

@Serializable
data class UsersResponse(val users: List<User>)

/**
 * Tells the sign-in flow which screen to show next: a name it's never seen,
 * a name with a PIN, or a name from before PINs existed.
 */
@Serializable
data class Lookup(
    val exists: Boolean,
    val hasPin: Boolean,
    val username: String? = null,
    val language: String? = null,
    val voice: String? = null,
)

@Serializable
data class LanguagesResponse(val languages: List<String>, val voices: List<String>)

@Serializable
data class Message(
    val id: String,
    val sender: String,
    val recipient: String,
    val status: String,
    val sourceLang: String,
    val targetLang: String,
    val sourceText: String? = null,
    val translatedText: String? = null,
    val originalAudioUrl: String? = null,
    val translatedAudioUrl: String? = null,
    val durationSeconds: Int? = null,
    val error: String? = null,
    val createdAt: String,
) {
    /** Still moving through the pipeline — worth polling again. */
    val isProcessing: Boolean get() = status != "ready" && status != "failed"
    val isReady: Boolean get() = status == "ready"
    val hasFailed: Boolean get() = status == "failed"
}

@Serializable
data class MessagesResponse(val messages: List<Message>)

/** What the pipeline is doing, in words the person receiving it would use. */
fun statusLabel(status: String): String = when (status) {
    "uploaded" -> "Uploading…"
    "transcribing" -> "Listening…"
    "translating" -> "Translating…"
    "synthesizing" -> "Finding the words…"
    "ready" -> "Ready"
    "failed" -> "Couldn't translate"
    else -> status
}

val LANGUAGE_NAMES: Map<String, String> = mapOf(
    "mr-IN" to "मराठी",
    "kn-IN" to "ಕನ್ನಡ",
    "hi-IN" to "हिन्दी",
    "gu-IN" to "ગુજરાતી",
    "en-IN" to "English",
)

fun languageName(code: String): String = LANGUAGE_NAMES[code] ?: code
