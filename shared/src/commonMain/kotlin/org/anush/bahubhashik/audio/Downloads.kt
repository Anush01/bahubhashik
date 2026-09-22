package org.anush.bahubhashik.audio

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

    fun delete(messageId: String)

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
 */
fun downloadFilename(sender: String, recipient: String): String {
    val safe = { text: String -> text.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(24) }
    return "BahuBhashik ${safe(sender)} to ${safe(recipient)}.wav"
}
