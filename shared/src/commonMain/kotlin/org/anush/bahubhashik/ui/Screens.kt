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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.anush.bahubhashik.data.Api
import org.anush.bahubhashik.data.ApiException
import org.anush.bahubhashik.data.User
import org.anush.bahubhashik.data.languageName

/** Messages addressed to me, grouped by who sent them. */
@Composable
fun InboxScreen(api: Api, me: String, onOpenConversation: (String) -> Unit) {
    GroupedMessagesScreen(
        load = { api.inbox(me) },
        personOf = { it.sender },
        emptyText = "Nothing yet. Messages people send you will appear here.",
        onOpenConversation = onOpenConversation,
    )
}

/** Everything I've sent, grouped by who I sent it to. */
@Composable
fun SentScreen(api: Api, me: String, onOpenConversation: (String) -> Unit) {
    GroupedMessagesScreen(
        load = { api.sent(me) },
        // The server only returns community messages here, which always have one.
        personOf = { it.recipient.orEmpty() },
        emptyText = "You haven't sent anything yet.",
        onOpenConversation = onOpenConversation,
    )
}

/** Pick who to send to. Everyone except me. */
@Composable
fun PeoplePickerScreen(api: Api, me: String, onPick: (String) -> Unit) {
    var people by remember { mutableStateOf<List<User>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        try {
            people = api.everyone().filter { it.username != me }
            error = null
        } catch (e: Exception) {
            error = (e as? ApiException)?.message ?: "Couldn't load people."
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        when {
            loading -> Loading()
            error != null -> ErrorBanner(error!!) { reload++ }
            people.isEmpty() -> Text(
                "Nobody else has signed up yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
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
