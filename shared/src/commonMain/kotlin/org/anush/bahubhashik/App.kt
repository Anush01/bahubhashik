package org.anush.bahubhashik

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import org.anush.bahubhashik.audio.Session
import org.anush.bahubhashik.data.Api
import org.anush.bahubhashik.ui.BahuBhashikTheme
import org.anush.bahubhashik.ui.MainShell
import org.anush.bahubhashik.ui.SignInFlow
import org.anush.bahubhashik.ui.WakingScreen
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * How long to keep waiting for a sleeping server before offering a retry.
 * Render's free plan usually wakes in under a minute; two gives it room.
 */
private const val WAKE_BUDGET_SECONDS = 120

@Composable
fun App() {
    BahuBhashikTheme {
        val api = remember { Api() }
        var me by remember { mutableStateOf(Session.savedUsername()) }

        // Nothing in the app works without the backend, and on the free plan
        // it may be asleep. Hold everything behind a health check rather than
        // letting people record a message into a void.
        var awake by remember { mutableStateOf(false) }
        var waited by remember { mutableStateOf(0) }
        var gaveUp by remember { mutableStateOf(false) }
        var wakeAttempt by remember { mutableStateOf(0) }

        LaunchedEffect(wakeAttempt) {
            awake = false
            gaveUp = false
            waited = 0
            val startedAt = TimeSource.Monotonic.markNow()

            while (startedAt.elapsedNow() < WAKE_BUDGET_SECONDS.seconds) {
                if (api.isAwake()) {
                    awake = true
                    return@LaunchedEffect
                }
                waited = startedAt.elapsedNow().inWholeSeconds.toInt()
                delay(2_000)
            }
            gaveUp = true
        }

        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            val signedInAs = me
            when {
                !awake -> WakingScreen(
                    elapsedSeconds = waited,
                    budgetSeconds = WAKE_BUDGET_SECONDS,
                    gaveUp = gaveUp,
                    onRetry = { wakeAttempt++ },
                )

                signedInAs == null -> SignInFlow(api) { username ->
                    Session.save(username)
                    me = username
                }

                else -> MainShell(api = api, me = signedInAs) {
                    Session.clear()
                    me = null
                }
            }
        }
    }
}
