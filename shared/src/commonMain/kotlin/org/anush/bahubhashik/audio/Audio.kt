package org.anush.bahubhashik.audio

/** A finished recording, held in memory and ready to upload. */
data class Recording(
    val bytes: ByteArray,
    val filename: String,
    val durationSeconds: Double,
) {
    // ByteArray uses identity equality, which would make two identical
    // recordings compare unequal and break Compose's recomposition checks.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is Recording &&
                filename == other.filename &&
                durationSeconds == other.durationSeconds &&
                bytes.contentEquals(other.bytes))

    override fun hashCode(): Int =
        (bytes.contentHashCode() * 31 + filename.hashCode()) * 31 + durationSeconds.hashCode()
}

/** Product cap, mirrored from the backend. */
const val MAX_RECORDING_SECONDS = 5 * 60

expect class AudioRecorder() {
    /** Begins capture. Returns false if the mic is unavailable or permission was refused. */
    suspend fun start(): Boolean

    /** Ends capture and returns what was recorded, or null if nothing usable was captured. */
    fun stop(): Recording?

    /** Seconds captured so far, for the running timer. */
    fun elapsedSeconds(): Double

    fun cancel()
}

/**
 * Plays audio straight from a URL. Both platforms stream over HTTP rather than
 * downloading first, so a five-minute message starts playing immediately.
 */
expect class AudioPlayer() {
    fun play(url: String, onFinished: () -> Unit)
    fun stop()
    fun release()
}

/** Remembers who is signed in on this device, so the app only asks once. */
expect object Session {
    fun savedUsername(): String?
    fun save(username: String)
    fun clear()
}
