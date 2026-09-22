package org.anush.bahubhashik.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.anush.bahubhashik.data.ApiException
import org.anush.bahubhashik.audio.Downloads
import org.anush.bahubhashik.data.Message

/**
 * Both message lists are the same shape — messages grouped by the other
 * person, newest first — so they share one implementation. Only which side
 * of the message names that person differs.
 */
@Composable
fun GroupedMessagesScreen(
    load: suspend () -> List<Message>,
    personOf: (Message) -> String,
    emptyText: String,
    onOpenConversation: (String) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    // Recomputed on every poll so a download made inside a conversation is
    // reflected here when you come back.
    var savedIds by remember { mutableStateOf(Downloads.savedIds()) }

    // No push notifications in v0, so this polls — every three seconds while
    // something is mid-pipeline, backing off to ten when nothing is.
    LaunchedEffect(reload) {
        while (true) {
            try {
                messages = load()
                error = null
            } catch (e: Exception) {
                if (messages.isEmpty()) error = (e as? ApiException)?.message ?: "Couldn't reach the server."
            }
            savedIds = Downloads.savedIds()
            loading = false
            delay(if (messages.any { it.isProcessing }) 3_000 else 10_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        header?.invoke()

        when {
            loading -> Loading()
            error != null -> ErrorBanner(error!!) { reload++ }
            messages.isEmpty() -> Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
            else -> {
                val byPerson = messages.groupBy(personOf)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                ) {
                    items(byPerson.keys.toList()) { person ->
                        val theirs = byPerson.getValue(person)
                        PersonCard(
                            person = person,
                            count = theirs.size,
                            pending = theirs.count { it.isProcessing },
                            failed = theirs.count { it.hasFailed },
                            saved = theirs.count { it.id in savedIds },
                            onClick = { onOpenConversation(person) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: String,
    count: Int,
    pending: Int,
    failed: Int,
    saved: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(person, style = MaterialTheme.typography.titleLarge)
            Text(
                buildString {
                    append(if (count == 1) "1 message" else "$count messages")
                    if (pending > 0) append(" · $pending still arriving")
                    if (failed > 0) append(" · $failed didn't work")
                    if (saved > 0) append(" · $saved saved")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed > 0) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
    }
}
