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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.anush.bahubhashik.audio.AudioPlayer
import org.anush.bahubhashik.audio.AudioRecorder
import org.anush.bahubhashik.audio.Downloads
import org.anush.bahubhashik.audio.downloadFilename
import org.anush.bahubhashik.audio.MAX_RECORDING_SECONDS
import org.anush.bahubhashik.audio.Recording
import org.anush.bahubhashik.data.Api
import org.anush.bahubhashik.data.ApiException
import org.anush.bahubhashik.data.Message
import org.anush.bahubhashik.data.statusLabel

@Composable
fun ConversationScreen(api: Api, me: String, other: String) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // Two kinds of bad news: one leaves us with nothing to show, the other
    // is something the person should know while still seeing their messages.
    var loadError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var refreshNow by remember { mutableStateOf(0) }

    val recorder = remember { AudioRecorder() }
    val player = remember { AudioPlayer() }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0.0) }
    var pending by remember { mutableStateOf<Recording?>(null) }
    var sending by remember { mutableStateOf(false) }
    var playingId by remember { mutableStateOf<String?>(null) }
    // Straight from the filesystem, refreshed after every save or delete.
    var savedIds by remember { mutableStateOf(Downloads.savedIds()) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            player.release()
            recorder.cancel()
        }
    }

    /**
     * A recording can't survive the app going to the background: Android's
     * MediaRecorder would carry on capturing whatever the phone can hear, and
     * iOS tears the audio session down partway through. Either way what comes
     * back isn't what the person meant to send, so throw it away and say so
     * rather than quietly handing them half a message.
     *
     * A finished-but-unsent recording is left alone — that one is already
     * complete and the person still gets to choose.
     */
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (recording) {
            recorder.cancel()
            recording = false
            elapsed = 0.0
            notice = "Recording stopped when you left the app. Nothing was saved."
        }
        player.stop()
        playingId = null
    }

    LaunchedEffect(me, other, refreshNow) {
        while (true) {
            try {
                messages = api.conversation(me, other)
                loadError = null
            } catch (e: Exception) {
                if (messages.isEmpty()) loadError = (e as? ApiException)?.message ?: "Couldn't load messages."
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Loading()
                loadError != null -> ErrorBanner(loadError!!) { refreshNow++ }
                messages.isEmpty() -> Text(
                    "No messages yet. Tap the button below to record one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(messages) { message ->
                        MessageCard(
                            message = message,
                            fromMe = message.sender == me,
                            isPlaying = playingId == message.id,
                            isSaved = message.id in savedIds,
                            isDownloading = downloadingId == message.id,
                            onSave = {
                                val url = message.translatedAudioUrl ?: return@MessageCard
                                downloadingId = message.id
                                scope.launch {
                                    try {
                                        Downloads.save(
                                            messageId = message.id,
                                            filename = downloadFilename(message.sender, message.recipient),
                                            bytes = api.download(url),
                                        )
                                        savedIds = Downloads.savedIds()
                                    } catch (e: Exception) {
                                        notice = (e as? ApiException)?.message
                                            ?: "Couldn't save that. Check your connection."
                                    }
                                    downloadingId = null
                                }
                            },
                            onShare = { Downloads.share(message.id) },
                            onRemoveDownload = {
                                Downloads.delete(message.id)
                                savedIds = Downloads.savedIds()
                            },
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

        notice?.let { NoticeBanner(it) { notice = null } }

        RecordBar(
            recording = recording,
            elapsed = elapsed,
            pending = pending,
            sending = sending,
            onStart = {
                scope.launch {
                    player.stop()
                    playingId = null
                    notice = null
                    if (recorder.start()) {
                        elapsed = 0.0
                        recording = true
                    } else {
                        notice = "Couldn't use the microphone. Check the app's permission."
                    }
                }
            },
            onStop = {
                pending = recorder.stop()
                recording = false
                if (pending == null) notice = "That recording was too short to send."
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
                        notice = (e as? ApiException)?.message ?: "Couldn't send. Check your connection."
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
    isSaved: Boolean,
    isDownloading: Boolean,
    onPlay: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRemoveDownload: () -> Unit,
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

                    if (translated != null) {
                        DownloadActions(
                            isSaved = isSaved,
                            isDownloading = isDownloading,
                            onSave = onSave,
                            onShare = onShare,
                            onRemoveDownload = onRemoveDownload,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Saving keeps a copy of the *translation* — the version the other person can
 * understand, and so the only one worth sending on to someone without the app.
 */
@Composable
private fun DownloadActions(
    isSaved: Boolean,
    isDownloading: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    when {
        isDownloading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Saving to this phone…", style = MaterialTheme.typography.bodyMedium)
        }

        isSaved -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Saved on this phone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BigButton("Share", onShare, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemoveDownload) {
                    Text("Remove", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        else -> TextButton(onClick = onSave, modifier = Modifier.padding(top = 4.dp)) {
            Text("Save to this phone", style = MaterialTheme.typography.bodyMedium)
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
