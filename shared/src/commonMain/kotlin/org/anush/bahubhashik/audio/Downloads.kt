package org.anush.bahubhashik.audio

import org.anush.bahubhashik.data.Api
import org.anush.bahubhashik.data.Message
import org.anush.bahubhashik.data.englishLanguageName

/**
 * Saved copies of translated audio, kept inside the app's own storage.
 *
 * "Downloaded" is not tracked anywhere but the filesystem: a file either
 * exists or it doesn't. Nothing to sync, nothing to go stale, and it stays
 * honest if someone clears the app's data.
 */
expect object Downloads {
    /** Writes the bytes and returns the local path. */
    fun save(messageId: String, filename: String, bytes: ByteArray): String

    fun isSaved(messageId: String): Boolean

    /**
     * Deletes the saved copy and remembers that it was deleted on purpose, so
     * automatic saving doesn't quietly bring it back.
     */
    fun delete(messageId: String)

    /** True once someone has deleted this message's saved copy themselves. */
    fun wasRemoved(messageId: String): Boolean

    /** Everything, for sign-out. */
    fun deleteAll()

    /** Hands the saved file to the system share sheet. */
    fun share(messageId: String)

    fun savedIds(): Set<String>
}

/**
 * The name the person on the other end sees in WhatsApp or their mail client,
 * so it has to read like something a human sent. The message id keeps files
 * apart via the directory they sit in, never by being in the name.
 *
 * The extension comes from the stored file rather than being assumed: new
 * messages are MP3, but ones translated before that change are still WAV, and
 * a .mp3 name on WAV bytes is worse than either.
 */
fun downloadFilename(message: Message, sourceUrl: String): String {
    val safe = { text: String -> text.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(24) }
    // A composed message has no recipient; the language is what tells two apart.
    val about = message.recipient?.let { "to ${safe(it)}" } ?: englishLanguageName(message.targetLang)
    return "BahuBhashik ${safe(message.sender)} $about.${extensionOf(sourceUrl)}"
}

/** Fetches a ready message's translation and keeps it on the phone. */
suspend fun saveTranslation(api: Api, message: Message) {
    val url = message.translatedAudioUrl ?: return
    Downloads.save(message.id, downloadFilename(message, url), api.download(url))
}

/** Signed URLs carry a query string, so the extension sits before the '?'. */
private fun extensionOf(url: String): String {
    val candidate = url.substringBefore('?').substringAfterLast('.', "").lowercase()
    return if (candidate.length in 2..4 && candidate.all { it.isLetterOrDigit() }) candidate else "mp3"
}
