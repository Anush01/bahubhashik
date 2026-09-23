package org.anush.bahubhashik.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.anush.bahubhashik.audio.AudioPlayer
import org.anush.bahubhashik.audio.Downloads
import org.anush.bahubhashik.audio.Session
import org.anush.bahubhashik.audio.saveTranslation
import org.anush.bahubhashik.data.Api
import org.anush.bahubhashik.data.ApiException
import org.anush.bahubhashik.data.LANGUAGE_NAMES
import org.anush.bahubhashik.data.Message
import org.anush.bahubhashik.data.bilingualLanguageName
import org.anush.bahubhashik.data.languageName
import org.anush.bahubhashik.data.statusLabel

/**
 * The whole point of a composition is getting it off the phone, so a finished
 * one is saved without being asked — unless its saved copy was deleted by
 * hand, in which case bringing it back would be overruling that person.
 *
 * Returns false if the save was attempted and failed.
 */
private suspend fun autoSave(api: Api, message: Message): Boolean {
    if (!shouldAutoSave(message)) return true
    return runCatching { saveTranslation(api, message) }.isSuccess
}

private fun shouldAutoSave(message: Message): Boolean =
    message.isReady && !Downloads.isSaved(message.id) && !Downloads.wasRemoved(message.id)

/** Everything this person has translated for themselves, newest first. */
@Composable
fun ComposeHistoryScreen(
    api: Api,
    me: String,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var savedIds by remember { mutableStateOf(Downloads.savedIds()) }

    LaunchedEffect(reload) {
        while (true) {
            try {
                messages = api.composed(me)
                error = null
            } catch (e: Exception) {
                if (messages.isEmpty()) error = (e as? ApiException)?.message ?: "Couldn't reach the server."
            }
            loading = false
            // Anything that finished while nobody was looking at it.
            messages.forEach { autoSave(api, it) }
            savedIds = Downloads.savedIds()
            delay(if (messages.any { it.isProcessing }) 3_000 else 10_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
            BigButton("New translation", onNew)
        }

        when {
            loading -> Loading()
            error != null -> ErrorBanner(error!!) { reload++ }
            messages.isEmpty() -> Text(
                "Nothing yet. Record something and it'll come back in the language you choose, " +
                    "ready to share.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    CompositionCard(
                        message = message,
                        isSaved = message.id in savedIds,
                        onClick = { onOpen(message.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompositionCard(message: Message, isSaved: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Into ${bilingualLanguageName(message.targetLang)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            message.translatedText?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                when {
                    message.isProcessing -> statusLabel(message.status)
                    message.hasFailed -> statusLabel(message.status)
                    isSaved -> "Saved on this phone"
                    else -> "Not saved on this phone"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    message.hasFailed -> MaterialTheme.colorScheme.error
                    isSaved -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (isSaved) FontWeight.SemiBold else null,
            )
        }
    }
}

/**
 * Record, then choose what language it should come out in. The sender speaks
 * their own profile language, the same as in Community — only the output is
 * chosen here, and the last choice is remembered because it's usually the
 * next one too.
 */
@Composable
fun NewCompositionScreen(api: Api, me: String, onCreated: (String) -> Unit) {
    var notice by remember { mutableStateOf<String?>(null) }
    val recorder = rememberRecorderState(onDiscardedInBackground = { notice = DISCARDED_IN_BACKGROUND })
    var target by remember { mutableStateOf(Session.lastComposeLanguage()?.takeIf { it in LANGUAGE_NAMES }) }
    var myLanguage by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(me) {
        myLanguage = runCatching { api.lookUp(me).language }.getOrNull()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                myLanguage?.let { "Speak in ${languageName(it)}." } ?: "Speak in your own language.",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Then choose the language it should come out in. You'll get a recording " +
                    "you can share on WhatsApp, email, or anywhere else.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (recorder.pending != null) {
                Text(
                    "Translate into",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                ChipRow(
                    options = LANGUAGE_NAMES.keys.toList(),
                    selected = target.orEmpty(),
                    label = ::bilingualLanguageName,
                    onSelect = { target = it },
                )
            }
        }

        notice?.let { NoticeBanner(it) { notice = null } }

        RecordBar(
            state = recorder,
            sending = sending,
            sendLabel = target?.let { "Translate into ${languageName(it)}" } ?: "Choose a language",
            sendingLabel = "Sending…",
            sendEnabled = target != null,
            onStart = {
                scope.launch {
                    notice = null
                    if (!recorder.start()) notice = MICROPHONE_UNAVAILABLE
                }
            },
            onStop = {
                if (!recorder.stop()) notice = "That recording was too short to translate."
            },
            onSend = {
                val clip = recorder.pending ?: return@RecordBar
                val language = target ?: return@RecordBar
                sending = true
                scope.launch {
                    try {
                        val created = api.compose(me, language, clip.bytes, clip.filename, clip.durationSeconds)
                        Session.saveComposeLanguage(language)
                        recorder.discard()
                        onCreated(created.id)
                    } catch (e: Exception) {
                        notice = (e as? ApiException)?.message ?: "Couldn't send. Check your connection."
                    }
                    sending = false
                }
            },
        )
    }
}

/** One composition: its progress while translating, then the result and ways to share it. */
@Composable
fun CompositionScreen(api: Api, id: String) {
    var message by remember { mutableStateOf<Message?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var refreshNow by remember { mutableStateOf(0) }
    var isSaved by remember { mutableStateOf(Downloads.isSaved(id)) }
    var saving by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { AudioPlayer() }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player.stop()
        playing = null
    }

    LaunchedEffect(id, refreshNow) {
        while (true) {
            try {
                val latest = api.message(id)
                message = latest
                loadError = null
                if (!saving && shouldAutoSave(latest)) {
                    saving = true
                    if (!autoSave(api, latest)) notice = "Couldn't save it to this phone. Tap Save to try again."
                    saving = false
                }
                isSaved = Downloads.isSaved(id)
            } catch (e: Exception) {
                if (message == null) loadError = (e as? ApiException)?.message ?: "Couldn't load this translation."
            }
            // Signed audio links last an hour, so keep refreshing slowly once ready.
            delay(if (message?.isProcessing != false) 3_000 else 60_000)
        }
    }

    val current = message
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loadError != null -> ErrorBanner(loadError!!) { refreshNow++ }
                current == null -> Loading()
                else -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Into ${bilingualLanguageName(current.targetLang)}",
                        style = MaterialTheme.typography.titleLarge,
                    )

                    when {
                        current.isProcessing -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Text(statusLabel(current.status), style = MaterialTheme.typography.bodyLarge)
                            }
                            Text(
                                "A long recording can take a minute or two. You can leave this " +
                                    "screen — it'll be waiting in your list.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        current.hasFailed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                statusLabel(current.status),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            current.error?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { api.retry(current.id) }
                                    refreshNow++
                                }
                            }) { Text("Try again") }
                        }

                        else -> {
                            current.translatedText?.let {
                                Text(it, style = MaterialTheme.typography.bodyLarge)
                            }
                            current.translatedAudioUrl?.let { url ->
                                DownloadActions(
                                    isSaved = isSaved,
                                    isDownloading = saving,
                                    onSave = {
                                        saving = true
                                        scope.launch {
                                            try {
                                                saveTranslation(api, current)
                                            } catch (e: Exception) {
                                                notice = (e as? ApiException)?.message
                                                    ?: "Couldn't save that. Check your connection."
                                            }
                                            isSaved = Downloads.isSaved(id)
                                            saving = false
                                        }
                                    },
                                    onShare = { Downloads.share(id) },
                                    onRemoveDownload = {
                                        Downloads.delete(id)
                                        isSaved = false
                                    },
                                    removeLabel = "Delete",
                                )
                                TextButton(onClick = {
                                    if (playing == url) {
                                        player.stop()
                                        playing = null
                                    } else {
                                        playing = url
                                        player.play(url) { playing = null }
                                    }
                                }) {
                                    Text(
                                        if (playing == url) "Stop" else "Play the translation",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }

                            current.originalAudioUrl?.let { original ->
                                TextButton(onClick = {
                                    if (playing == original) {
                                        player.stop()
                                        playing = null
                                    } else {
                                        playing = original
                                        player.play(original) { playing = null }
                                    }
                                }) {
                                    Text(
                                        if (playing == original) "Stop" else "Hear what you recorded",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            current.sourceText?.let {
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

        notice?.let { NoticeBanner(it) { notice = null } }
    }
}
