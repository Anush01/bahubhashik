package org.anush.bahubhashik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shown until the backend answers. The free Render plan puts the service to
 * sleep after fifteen minutes idle, and the first request after that can take
 * the better part of a minute — long enough that without this screen the app
 * just looks broken.
 */
@Composable
fun WakingScreen(elapsedSeconds: Int, budgetSeconds: Int, gaveUp: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BahuBhashik", style = MaterialTheme.typography.headlineMedium)

        if (gaveUp) {
            Text(
                "The server isn't answering.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                "Check that you're online, then try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
            BigButton("Try again", onRetry)
        } else {
            CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
            Text(
                "Starting up…",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                "This can take up to two minutes the first time each day. " +
                    "You can leave the app open while it happens.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            LinearProgressIndicator(
                progress = { (elapsedSeconds.toFloat() / budgetSeconds).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            )
            Text(
                "${elapsedSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
