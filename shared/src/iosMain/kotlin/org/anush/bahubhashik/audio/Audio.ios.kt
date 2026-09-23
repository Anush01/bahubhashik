package org.anush.bahubhashik.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioQualityMedium
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.play
import platform.AVFoundation.pause
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.posix.memcpy
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class AudioRecorder {
    private var recorder: AVAudioRecorder? = null
    private var fileUrl: NSURL? = null

    actual suspend fun start(): Boolean {
        val session = AVAudioSession.sharedInstance()

        val granted = suspendCancellableCoroutine { continuation ->
            session.requestRecordPermission { allowed -> continuation.resume(allowed) }
        }
        if (!granted) return false

        return try {
            session.setCategory(AVAudioSessionCategoryPlayAndRecord, null)
            session.setActive(true, null)

            val caches = NSFileManager.defaultManager
                .URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
                .first() as NSURL
            val url = caches.URLByAppendingPathComponent(
                "bb-${NSDate().timeIntervalSince1970.toLong()}.m4a",
            ) ?: return false

            // AAC in an MP4 container: small enough to upload over mobile data
            // and accepted by Sarvam without conversion. Mono at 44.1kHz —
            // speech gains nothing from stereo.
            val settings = mapOf<Any?, Any?>(
                AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatMPEG4AAC),
                AVSampleRateKey to NSNumber(double = 44100.0),
                AVNumberOfChannelsKey to NSNumber(int = 1),
                AVEncoderAudioQualityKey to NSNumber(long = AVAudioQualityMedium),
            )

            val created = AVAudioRecorder(uRL = url, settings = settings, error = null)
            if (!created.prepareToRecord() || !created.record()) return false

            recorder = created
            fileUrl = url
            true
        } catch (_: Throwable) {
            false
        }
    }

    actual fun stop(): Recording? {
        val active = recorder ?: return null
        val url = fileUrl
        val seconds = active.currentTime

        active.stop()
        recorder = null
        fileUrl = null
        AVAudioSession.sharedInstance().setActive(false, null)

        if (url == null) return null
        val data = NSData.dataWithContentsOfURL(url) ?: return null
        val bytes = data.toByteArray()
        if (bytes.isEmpty()) return null

        NSFileManager.defaultManager.removeItemAtURL(url, null)
        return Recording(bytes, url.lastPathComponent ?: "recording.m4a", seconds)
    }

    actual fun elapsedSeconds(): Double = recorder?.currentTime ?: 0.0

    actual fun cancel() {
        recorder?.stop()
        recorder = null
        fileUrl?.let { NSFileManager.defaultManager.removeItemAtURL(it, null) }
        fileUrl = null
        AVAudioSession.sharedInstance().setActive(false, null)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class AudioPlayer {
    private var player: AVPlayer? = null
    private var endObserver: Any? = null

    /** AVPlayer streams from the URL; it does not download the file first. */
    actual fun play(url: String, onFinished: () -> Unit) {
        stop()
        val nsUrl = NSURL.URLWithString(url) ?: return

        // Playback category so audio is audible even with the ringer switch off.
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        AVAudioSession.sharedInstance().setActive(true, null)

        val item = AVPlayerItem(uRL = nsUrl)
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> onFinished() }

        player = AVPlayer(playerItem = item).also { it.play() }
    }

    actual fun stop() {
        player?.pause()
        player = null
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
    }

    actual fun release() = stop()
}

actual object Session {
    private const val KEY = "bahubhashik.username"
    private const val COMPOSE_LANGUAGE = "bahubhashik.composeLanguage"

    actual fun savedUsername(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(KEY)?.ifBlank { null }

    actual fun save(username: String) {
        NSUserDefaults.standardUserDefaults.setObject(username, KEY)
    }

    actual fun clear() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(KEY)
        NSUserDefaults.standardUserDefaults.removeObjectForKey(COMPOSE_LANGUAGE)
    }

    actual fun lastComposeLanguage(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(COMPOSE_LANGUAGE)

    actual fun saveComposeLanguage(code: String) {
        NSUserDefaults.standardUserDefaults.setObject(code, COMPOSE_LANGUAGE)
    }
}
