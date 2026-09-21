package org.anush.bahubhashik.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * The shared module has no Context of its own. MainActivity sets this once at
 * startup, before any screen can reach the recorder.
 */
object AndroidContext {
    lateinit var application: Context

    val isInitialised: Boolean get() = ::application.isInitialized
}

actual class AudioRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0

    actual suspend fun start(): Boolean {
        if (!AndroidContext.isInitialised) return false
        val context = AndroidContext.application

        // MainActivity asks for this at launch; if it was refused there's
        // nothing useful to do here but report it.
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        return try {
            val file = File.createTempFile("bb-", ".m4a", context.cacheDir)

            @Suppress("DEPRECATION")
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            created.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                // AAC in MP4: small over mobile data, accepted by Sarvam as-is.
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = created
            outputFile = file
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (_: Exception) {
            cleanUp()
            false
        }
    }

    actual fun stop(): Recording? {
        val active = recorder ?: return null
        val file = outputFile
        val seconds = elapsedSeconds()

        try {
            active.stop()
        } catch (_: RuntimeException) {
            // stop() throws when almost nothing was captured; the file is unusable.
            cleanUp()
            return null
        } finally {
            active.release()
            recorder = null
        }

        if (file == null || !file.exists()) return null
        val bytes = file.readBytes()
        file.delete()
        outputFile = null

        return if (bytes.isEmpty()) null else Recording(bytes, file.name, seconds)
    }

    actual fun elapsedSeconds(): Double =
        if (startedAt == 0L) 0.0 else (SystemClock.elapsedRealtime() - startedAt) / 1000.0

    actual fun cancel() {
        try {
            recorder?.stop()
        } catch (_: RuntimeException) {
            // Nothing captured; discarding anyway.
        }
        cleanUp()
    }

    private fun cleanUp() {
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
        startedAt = 0
    }
}

actual class AudioPlayer {
    private var player: MediaPlayer? = null

    /** prepareAsync streams over HTTP rather than downloading the whole file first. */
    actual fun play(url: String, onFinished: () -> Unit) {
        stop()
        player = MediaPlayer().apply {
            setOnCompletionListener { onFinished() }
            setOnErrorListener { _, _, _ -> onFinished(); true }
            try {
                setDataSource(url)
                setOnPreparedListener { it.start() }
                prepareAsync()
            } catch (_: Exception) {
                onFinished()
            }
        }
    }

    actual fun stop() {
        player?.run {
            try {
                if (isPlaying) stop()
            } catch (_: IllegalStateException) {
                // Already stopped or never started.
            }
            release()
        }
        player = null
    }

    actual fun release() = stop()
}

actual object Session {
    private const val PREFS = "bahubhashik"
    private const val KEY = "username"

    private fun prefs() = AndroidContext.application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    actual fun savedUsername(): String? =
        if (!AndroidContext.isInitialised) null else prefs().getString(KEY, null)?.ifBlank { null }

    actual fun save(username: String) {
        prefs().edit().putString(KEY, username).apply()
    }

    actual fun clear() {
        prefs().edit().remove(KEY).apply()
    }
}
