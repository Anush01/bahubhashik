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
    /** Null for a composed message, which nobody receives inside the app. */
    val recipient: String? = null,
    val kind: String = "community",
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
    val isComposed: Boolean get() = kind == "composed"
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

/**
 * Every language Sarvam can speak, each in its own script — people find their
 * language faster by its look than by an English name. Ordered by English name
 * so the list doesn't favour whoever the app was first built for.
 */
val LANGUAGE_NAMES: Map<String, String> = linkedMapOf(
    "bn-IN" to "বাংলা",
    "en-IN" to "English",
    "gu-IN" to "ગુજરાતી",
    "hi-IN" to "हिन्दी",
    "kn-IN" to "ಕನ್ನಡ",
    "ml-IN" to "മലയാളം",
    "mr-IN" to "मराठी",
    "od-IN" to "ଓଡ଼ିଆ",
    "pa-IN" to "ਪੰਜਾਬੀ",
    "ta-IN" to "தமிழ்",
    "te-IN" to "తెలుగు",
)

/**
 * English names, for when the reader may not know the script: choosing a
 * language to translate *into* is often choosing one you can't read. Also
 * used in shared filenames.
 */
val LANGUAGE_ENGLISH_NAMES: Map<String, String> = mapOf(
    "bn-IN" to "Bengali",
    "en-IN" to "English",
    "gu-IN" to "Gujarati",
    "hi-IN" to "Hindi",
    "kn-IN" to "Kannada",
    "ml-IN" to "Malayalam",
    "mr-IN" to "Marathi",
    "od-IN" to "Odia",
    "pa-IN" to "Punjabi",
    "ta-IN" to "Tamil",
    "te-IN" to "Telugu",
)

fun languageName(code: String): String = LANGUAGE_NAMES[code] ?: code

fun englishLanguageName(code: String): String = LANGUAGE_ENGLISH_NAMES[code] ?: code

/** "ಕನ್ನಡ · Kannada" — native script first, English for everyone else. */
fun bilingualLanguageName(code: String): String {
    val native = languageName(code)
    val english = englishLanguageName(code)
    return if (native == english) native else "$native · $english"
}
