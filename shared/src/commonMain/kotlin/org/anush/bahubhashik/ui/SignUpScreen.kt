package org.anush.bahubhashik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.anush.bahubhashik.data.Api
import org.anush.bahubhashik.data.LANGUAGE_NAMES
import org.anush.bahubhashik.data.languageName

/**
 * The only setup step. Declaring your own language here is what lets everyone
 * else skip choosing one when they message you — the backend derives the
 * target language from whoever is receiving.
 */
@Composable
fun SignUpScreen(api: Api, onSignedIn: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("mr-IN") }
    var voice by remember { mutableStateOf("female") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("BahuBhashik", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Send voice messages to people who don't speak your language.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40); error = null },
            label = { Text("Your name", style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("I speak", style = MaterialTheme.typography.titleMedium)
            Text(
                "Messages sent to you arrive in this language.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(
                options = LANGUAGE_NAMES.keys.toList(),
                selected = language,
                label = { languageName(it) },
                onSelect = { language = it },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("My voice", style = MaterialTheme.typography.titleMedium)
            Text(
                "How your messages sound to the person receiving them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(
                options = listOf("female", "male"),
                selected = voice,
                label = { if (it == "female") "Woman" else "Man" },
                onSelect = { voice = it },
            )
        }

        error?.let { ErrorBanner(it) }

        BigButton(
            text = if (busy) "Just a moment…" else "Start",
            enabled = name.isNotBlank() && !busy,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val user = api.signUp(name.trim(), language, voice)
                        onSignedIn(user.username)
                    } catch (e: Exception) {
                        error = "Couldn't reach the server. ${e.message ?: ""}".trim()
                        busy = false
                    }
                }
            },
        )

        Text(
            "Typing a name that already exists signs you in as that person.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    label: (String) -> String,
    onSelect: (String) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.bodyLarge) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}
