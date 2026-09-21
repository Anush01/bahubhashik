package org.anush.bahubhashik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.anush.bahubhashik.audio.AudioPlayer
import org.anush.bahubhashik.audio.AudioRecorder
import org.anush.bahubhashik.audio.MAX_RECORDING_SECONDS
import org.anush.bahubhashik.audio.Recording
import org.anush.bahubhashik.data.Api
import org.anush.bahubhashik.data.Message
import org.anush.bahubhashik.data.statusLabel

@Composable
fun ConversationScreen(api: Api, me: String, other: String, onBack: () -> Unit) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshNow by remember { mutableStateOf(0) }

    val recorder = remember { AudioRecorder() }
    val player = remember { AudioPlayer() }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0.0) }
    var pending by remember { mutableStateOf<Recording?>(null) }
    var sending by remember { mutableStateOf(false) }
    var playingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            recorder.cancel()
        }
    }

    LaunchedEffect(me, other, refreshNow) {
        while (true) {
            try {
                messages = api.conversation(me, other)
                error = null
            } catch (e: Exception) {
                if (messages.isEmpty()) error = "Couldn't load messages. ${e.message ?: ""}".trim()
            }
            loading = false
            delay(if (messages.any { it.isProcessing }) 3_000 else 10_000)
        }
    }

    // Drives the running timer, and enforces the five-minute cap by stopping
    // for you rather than throwing the recording away at the end.
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = recorder.elapsedSeconds()
            if (elapsed >= MAX_RECORDING_SECONDS) {
                pending = recorder.stop()
                recording = false
            }
            delay(200)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        ScreenHeader(other, onBack = onBack)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Loading()
                error != null -> ErrorBanner(error!!) { refreshNow++ }
                messages.isEmpty() -> Text(
                    "No messages yet. Hold the button to record one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(messages) { message ->
                        MessageCard(
                            message = message,
                            fromMe = message.sender == me,
                            isPlaying = playingId == message.id,
                            onPlay = { url ->
                                if (playingId == message.id) {
                                    player.stop()
                                    playingId = null
                                } else {
                                    playingId = message.id
                                    player.play(url) { playingId = null }
                                }
                            },
                            onRetry = {
                                scope.launch {
                                    try {
                                        api.retry(message.id)
                                        refreshNow++
                                    } catch (_: Exception) {
                                        // The next poll will show whatever actually happened.
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        RecordBar(
            recording = recording,
            elapsed = elapsed,
            pending = pending,
            sending = sending,
            onStart = {
                scope.launch {
                    player.stop()
                    playingId = null
                    if (recorder.start()) {
                        elapsed = 0.0
                        recording = true
                    } else {
                        error = "Couldn't use the microphone. Check the app's permission."
                    }
                }
            },
            onStop = {
                pending = recorder.stop()
                recording = false
                if (pending == null) error = "That recording was too short to send."
            },
            onDiscard = { pending = null },
            onSend = {
                val clip = pending ?: return@RecordBar
                sending = true
                scope.launch {
                    try {
                        api.send(me, other, clip.bytes, clip.filename, clip.durationSeconds)
                        pending = null
                        refreshNow++
                    } catch (e: Exception) {
                        error = "Couldn't send. ${e.message ?: ""}".trim()
                    }
                    sending = false
                }
            },
        )
    }
}

@Composable
private fun MessageCard(
    message: Message,
    fromMe: Boolean,
    isPlaying: Boolean,
    onPlay: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (fromMe) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (fromMe) "You" else message.sender,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            when {
                message.isProcessing -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(statusLabel(message.status), style = MaterialTheme.typography.bodyMedium)
                }

                message.hasFailed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        statusLabel(message.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    message.error?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onRetry) { Text("Try again") }
                }

                else -> {
                    // What the recipient came for: the version in their language.
                    val translated = message.translatedAudioUrl
                    if (translated != null) {
                        BigButton(if (isPlaying) "Stop" else "Play", { onPlay(translated) })
                    }
                    message.translatedText?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                    message.originalAudioUrl?.let { original ->
                        TextButton(onClick = { onPlay(original) }) {
                            Text(
                                if (fromMe) "Hear what you recorded" else "Hear their own voice",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    message.sourceText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordBar(
    recording: Boolean,
    elapsed: Double,
    pending: Recording?,
    sending: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            pending != null -> {
                Text(
                    "Recorded ${formatDuration(pending.durationSeconds)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        BigButton(if (sending) "Sending…" else "Send", onSend, enabled = !sending)
                    }
                    TextButton(onClick = onDiscard, enabled = !sending) {
                        Text("Delete", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            recording -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(16.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                    Text(formatDuration(elapsed), style = MaterialTheme.typography.headlineMedium)
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
