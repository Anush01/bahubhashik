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
import org.anush.bahubhashik.ui.ConversationScreen
import org.anush.bahubhashik.ui.InboxScreen
import org.anush.bahubhashik.ui.PeoplePickerScreen
import org.anush.bahubhashik.ui.SignUpScreen
import org.anush.bahubhashik.ui.WakingScreen
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * How long to keep waiting for a sleeping server before offering a retry.
 * Render's free plan usually wakes in under a minute; two gives it room.
 */
private const val WAKE_BUDGET_SECONDS = 120

/** Three screens and a picker — small enough that a nav library would cost more than it saves. */
private sealed interface Screen {
    data object Inbox : Screen
    data object PeoplePicker : Screen
    data class Conversation(val with: String) : Screen
}

@Composable
fun App() {
    BahuBhashikTheme {
        val api = remember { Api() }
        var me by remember { mutableStateOf(Session.savedUsername()) }
        var screen by remember { mutableStateOf<Screen>(Screen.Inbox) }

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
            val currentUser = me
            if (!awake) {
                WakingScreen(
                    elapsedSeconds = waited,
                    budgetSeconds = WAKE_BUDGET_SECONDS,
                    gaveUp = gaveUp,
                    onRetry = { wakeAttempt++ },
                )
            } else if (currentUser == null) {
                SignUpScreen(api) { username ->
                    Session.save(username)
                    me = username
                    screen = Screen.Inbox
                }
            } else {
                when (val current = screen) {
                    Screen.Inbox -> InboxScreen(
                        api = api,
                        me = currentUser,
                        onOpenConversation = { screen = Screen.Conversation(it) },
                        onNewMessage = { screen = Screen.PeoplePicker },
                        onSignOut = {
                            Session.clear()
                            me = null
                        },
                    )

                    Screen.PeoplePicker -> PeoplePickerScreen(
                        api = api,
                        me = currentUser,
                        onPick = { screen = Screen.Conversation(it) },
                        onBack = { screen = Screen.Inbox },
                    )

                    is Screen.Conversation -> ConversationScreen(
                        api = api,
                        me = currentUser,
                        other = current.with,
                        onBack = { screen = Screen.Inbox },
                    )
                }
            }
        }
    }
}
