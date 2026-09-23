package org.anush.bahubhashik.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.anush.bahubhashik.data.Api

/**
 * The two things the app does, as two big targets. Everything else is one
 * level down, so nobody has to learn the drawer to get started.
 */
@Composable
fun HomeScreen(onCommunity: () -> Unit, onCompose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeBox(
            title = "Community",
            description = "Send voice messages to people on BahuBhashik. They hear you in their language.",
            container = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.weight(1f),
            onClick = onCommunity,
        )
        HomeBox(
            title = "Compose Message",
            description = "Record something, pick a language, and share the translation on WhatsApp or anywhere else.",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.weight(1f),
            onClick = onCompose,
        )
    }
}

@Composable
private fun HomeBox(
    title: String,
    description: String,
    container: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, alignment = androidx.compose.ui.Alignment.CenterVertically),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(description, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Messages to and from other people. The two lists are tabs rather than
 * separate places, so "who wrote to me" and "who did I write to" sit together.
 */
@Composable
fun CommunityScreen(
    api: Api,
    me: String,
    onOpenConversation: (String) -> Unit,
    onNewMessage: () -> Unit,
) {
    // Saveable, so coming back from a conversation lands on the same tab.
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
            BigButton("New message", onNewMessage)
        }

        PrimaryTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            listOf("For me", "Sent").forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label, style = MaterialTheme.typography.titleMedium) },
                )
            }
        }

        when (tab) {
            0 -> InboxScreen(api = api, me = me, onOpenConversation = onOpenConversation)
            else -> SentScreen(api = api, me = me, onOpenConversation = onOpenConversation)
        }
    }
}
