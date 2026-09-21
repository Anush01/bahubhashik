package org.anush.bahubhashik.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.anush.bahubhashik.data.Api
import org.anush.bahubhashik.data.Message
import org.anush.bahubhashik.data.User
import org.anush.bahubhashik.data.languageName

/**
 * Everything sent to me, newest first, grouped by who sent it. Tapping a
 * sender opens that conversation.
 */
@Composable
fun InboxScreen(
    api: Api,
    me: String,
    onOpenConversation: (String) -> Unit,
    onNewMessage: () -> Unit,
    onSignOut: () -> Unit,
) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }

    // No push notifications in v0, so the inbox polls. It backs off to every
    // ten seconds once nothing is mid-pipeline.
    LaunchedEffect(me, reloadToken) {
        while (true) {
            try {
                messages = api.inbox(me)
                error = null
            } catch (e: Exception) {
                if (messages.isEmpty()) error = "Couldn't reach the server. ${e.message ?: ""}".trim()
            }
            loading = false
            delay(if (messages.any { it.isProcessing }) 3_000 else 10_000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        ScreenHeader(
            title = me,
            action = { TextButton(onClick = onSignOut) { Text("Switch", style = MaterialTheme.typography.bodyMedium) } },
        )

        BigButton("New message", onNewMessage)

        when {
            loading -> Loading()
            error != null -> ErrorBanner(error!!) { reloadToken++ }
            messages.isEmpty() -> Text(
                "Nothing yet. Messages people send you will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp),
            )
            else -> {
                val bySender = messages.groupBy { it.sender }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    items(bySender.keys.toList()) { sender ->
                        val fromThem = bySender.getValue(sender)
                        SenderCard(
                            sender = sender,
                            count = fromThem.size,
                            pending = fromThem.count { it.isProcessing },
                            onClick = { onOpenConversation(sender) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SenderCard(sender: String, count: Int, pending: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sender, style = MaterialTheme.typography.titleLarge)
            Text(
                buildString {
                    append(if (count == 1) "1 message" else "$count messages")
                    if (pending > 0) append(" · $pending still arriving")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Pick who to send to. Everyone except me. */
@Composable
fun PeoplePickerScreen(api: Api, me: String, onPick: (String) -> Unit, onBack: () -> Unit) {
    var people by remember { mutableStateOf<List<User>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }

    LaunchedEffect(reloadToken) {
        loading = true
        try {
            people = api.everyone().filter { it.username != me }
            error = null
        } catch (e: Exception) {
            error = "Couldn't load people. ${e.message ?: ""}".trim()
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        ScreenHeader("Send to", onBack = onBack)

        when {
            loading -> Loading()
            error != null -> ErrorBanner(error!!) { reloadToken++ }
            people.isEmpty() -> Text(
                "Nobody else has signed up yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(people) { person ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(person.username) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(person.username, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "hears ${languageName(person.language)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
