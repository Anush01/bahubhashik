package org.anush.bahubhashik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import org.anush.bahubhashik.audio.AudioRecorder
import org.anush.bahubhashik.audio.MAX_RECORDING_SECONDS
import org.anush.bahubhashik.audio.Recording

/**
 * Recording as seen by a screen: idle, capturing, or holding a finished take
 * that hasn't been sent yet. Shared by the conversation and Compose, which
 * record identically and differ only in what happens to the result.
 */
@Stable
class RecorderState internal constructor(private val recorder: AudioRecorder) {
    var recording by mutableStateOf(false)
        private set
    var elapsed by mutableStateOf(0.0)
        private set

    /** A finished recording waiting to be sent or thrown away. */
    var pending by mutableStateOf<Recording?>(null)
        private set

    /** False if the microphone couldn't be used. */
    suspend fun start(): Boolean {
        if (!recorder.start()) return false
        elapsed = 0.0
        recording = true
        return true
    }

    /** False if what was captured was too short to keep. */
    fun stop(): Boolean {
        pending = recorder.stop()
        recording = false
        return pending != null
    }

    fun discard() {
        pending = null
    }

    /** Throws away an in-progress take. True if there was one. */
    internal fun cancelIfRecording(): Boolean {
        if (!recording) return false
        recorder.cancel()
        recording = false
        elapsed = 0.0
        return true
    }

    internal fun release() = recorder.cancel()

    internal suspend fun runTimer() {
        // Drives the running timer, and enforces the five-minute cap by
        // stopping for you rather than throwing the recording away at the end.
        while (recording) {
            elapsed = recorder.elapsedSeconds()
            if (elapsed >= MAX_RECORDING_SECONDS) stop()
            delay(200)
        }
    }
}

/**
 * A recording can't survive the app going to the background: Android's
 * MediaRecorder would carry on capturing whatever the phone can hear, and iOS
 * tears the audio session down partway through. Either way what comes back
 * isn't what the person meant to send, so it's thrown away and
 * [onDiscardedInBackground] tells them so rather than quietly handing them
 * half a message.
 *
 * A finished-but-unsent recording is left alone — that one is already
 * complete and the person still gets to choose.
 */
@Composable
fun rememberRecorderState(onDiscardedInBackground: () -> Unit): RecorderState {
    val state = remember { RecorderState(AudioRecorder()) }

    DisposableEffect(state) {
        onDispose { state.release() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (state.cancelIfRecording()) onDiscardedInBackground()
    }
    LaunchedEffect(state.recording) {
        state.runTimer()
    }
    return state
}

const val DISCARDED_IN_BACKGROUND = "Recording stopped when you left the app. Nothing was saved."

@Composable
fun RecordBar(
    state: RecorderState,
    sending: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
    sendLabel: String = "Send",
    sendingLabel: String = "Sending…",
    sendEnabled: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val pending = state.pending
        when {
            pending != null -> {
                Text(
                    "Recorded ${formatDuration(pending.durationSeconds)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        BigButton(
                            if (sending) sendingLabel else sendLabel,
                            onSend,
                            enabled = !sending && sendEnabled,
                        )
                    }
                    TextButton(onClick = state::discard, enabled = !sending) {
                        Text("Delete", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            state.recording -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(16.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                    Text(formatDuration(state.elapsed), style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    "up to ${MAX_RECORDING_SECONDS / 60} minutes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BigButton("Stop", onStop)
            }

            else -> BigButton("Record a message", onStart)
        }
    }
}
