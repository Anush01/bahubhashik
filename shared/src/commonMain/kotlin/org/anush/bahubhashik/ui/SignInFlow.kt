package org.anush.bahubhashik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.anush.bahubhashik.data.Api
import org.anush.bahubhashik.data.ApiException
import org.anush.bahubhashik.data.LANGUAGE_NAMES
import org.anush.bahubhashik.data.languageName

/** Where the sign-in flow currently is. */
private sealed interface Step {
    data object Name : Step
    data class NewPerson(val username: String) : Step
    data class Password(val username: String) : Step
    data class FirstPin(val username: String) : Step
}

/**
 * Name first, then one of three things: a name we've never seen signs up, a
 * name with a PIN is asked for it, and a name from before PINs existed is
 * asked to choose one. That last branch is why `pin` is nullable — nobody who
 * already had an account gets locked out by this change.
 */
@Composable
fun SignInFlow(api: Api, setPinFor: String? = null, onSignedIn: (String) -> Unit) {
    // A remembered session for someone who has no PIN yet lands here: they
    // were signed up before PINs existed, so ask before letting them back in.
    var step by remember(setPinFor) {
        mutableStateOf<Step>(if (setPinFor != null) Step.FirstPin(setPinFor) else Step.Name)
    }

    when (val current = step) {
        Step.Name -> NameStep(
            api = api,
            onNewPerson = { step = Step.NewPerson(it) },
            onHasPin = { step = Step.Password(it) },
            onNeedsPin = { step = Step.FirstPin(it) },
        )

        is Step.NewPerson -> NewPersonStep(
            api = api,
            username = current.username,
            onBack = { step = Step.Name },
            onCreated = onSignedIn,
        )

        is Step.Password -> PasswordStep(
            api = api,
            username = current.username,
            onBack = { step = Step.Name },
            onSignedIn = onSignedIn,
        )

        is Step.FirstPin -> ChoosePinStep(
            // Name in the title: a resumed session lands straight here, and
            // "Choose a PIN" alone doesn't say whose.
            title = current.username,
            subtitle = "Choose a PIN. You'll need it each time you sign in.",
            onBack = { step = Step.Name },
            onChosen = { pin, fail ->
                runCatching { api.setPin(current.username, pin) }
                    .onSuccess { onSignedIn(current.username) }
                    .onFailure { fail((it as? ApiException)?.message ?: "Couldn't save your PIN.") }
            },
        )
    }
}

@Composable
private fun NameStep(
    api: Api,
    onNewPerson: (String) -> Unit,
    onHasPin: (String) -> Unit,
    onNeedsPin: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AuthScaffold(
        title = "BahuBhashik",
        subtitle = "Send voice messages to people who don't speak your language.",
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40); error = null },
            label = { Text("Your name", style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { ErrorBanner(it) }

        BigButton(
            text = if (busy) "Just a moment…" else "Continue",
            enabled = name.isNotBlank() && !busy,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    val username = name.trim()
                    try {
                        val found = api.lookUp(username)
                        when {
                            !found.exists -> onNewPerson(username)
                            found.hasPin -> onHasPin(username)
                            else -> onNeedsPin(username)
                        }
                    } catch (e: Exception) {
                        error = (e as? ApiException)?.message ?: "Couldn't reach the server."
                    }
                    busy = false
                }
            },
        )
    }
}

@Composable
private fun NewPersonStep(api: Api, username: String, onBack: () -> Unit, onCreated: (String) -> Unit) {
    var language by remember { mutableStateOf("mr-IN") }
    var voice by remember { mutableStateOf("female") }
    var choosingPin by remember { mutableStateOf(false) }

    if (choosingPin) {
        ChoosePinStep(
            title = username,
            subtitle = "Choose a PIN. You'll need it each time you sign in.",
            onBack = { choosingPin = false },
            onChosen = { pin, fail ->
                runCatching { api.createAccount(username, language, voice, pin) }
                    .onSuccess { onCreated(username) }
                    .onFailure { fail((it as? ApiException)?.message ?: "Couldn't create your account.") }
            },
        )
        return
    }

    AuthScaffold(title = "Hello, $username", subtitle = null, onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("I speak", style = MaterialTheme.typography.titleMedium)
            Text(
                "Messages sent to you arrive in this language.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(LANGUAGE_NAMES.keys.toList(), language, { languageName(it) }) { language = it }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("My voice", style = MaterialTheme.typography.titleMedium)
            Text(
                "How your messages sound to the person receiving them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(listOf("female", "male"), voice, { if (it == "female") "Woman" else "Man" }) { voice = it }
        }

        BigButton("Next", { choosingPin = true })
    }
}

@Composable
private fun PasswordStep(api: Api, username: String, onBack: () -> Unit, onSignedIn: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Submit as soon as the fourth digit lands — no "done" button to hunt for.
    LaunchedEffect(pin) {
        if (pin.length < PIN_LENGTH || busy) return@LaunchedEffect
        busy = true
        error = null
        try {
            api.signIn(username, pin)
            onSignedIn(username)
        } catch (e: Exception) {
            error = if (e is ApiException && e.status == 401) {
                "That PIN doesn't match. Try again."
            } else {
                (e as? ApiException)?.message ?: "Couldn't sign in. Check your connection."
            }
            pin = ""
            busy = false
        }
    }

    AuthScaffold(title = username, subtitle = "Enter your PIN", onBack = onBack, subtitleMinHeight = 64.dp) {
        PinPad(pin = pin, onPinChange = { pin = it }, enabled = !busy)
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Enter a PIN twice. There's no recovery if it's forgotten, so a typo on the
 * way in would be expensive — confirming costs one extra screen.
 */
@Composable
private fun ChoosePinStep(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onChosen: suspend (pin: String, fail: (String) -> Unit) -> Unit,
) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pin) {
        if (pin.length < PIN_LENGTH || busy) return@LaunchedEffect
        val confirming = first
        if (confirming == null) {
            first = pin
            pin = ""
            error = null
        } else if (confirming != pin) {
            first = null
            pin = ""
            error = "Those didn't match. Let's start again."
        } else {
            busy = true
            error = null
            onChosen(pin) { message ->
                error = message
                first = null
                pin = ""
                busy = false
            }
        }
    }

    AuthScaffold(
        title = title,
        subtitle = if (first == null) subtitle else "Enter the same four digits again",
        onBack = onBack,
        subtitleMinHeight = 64.dp,
    ) {
        PinPad(pin = pin, onPinChange = { pin = it }, enabled = !busy)
        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)? = null,
    /**
     * Minimum height reserved for the subtitle. The PIN screens change their
     * subtitle between steps, and without this the keypad slides up and down
     * under the user's finger mid-entry. A minimum rather than a fixed height
     * so longer text can still grow instead of being clipped.
     */
    subtitleMinHeight: Dp = Dp.Unspecified,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
                Text("‹ Back", style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (subtitleMinHeight != Dp.Unspecified) {
                            Modifier.heightIn(min = subtitleMinHeight)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        content()
    }
}
