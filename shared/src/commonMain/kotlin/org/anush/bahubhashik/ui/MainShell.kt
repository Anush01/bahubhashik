package org.anush.bahubhashik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.read
import kotlinx.coroutines.launch
import org.anush.bahubhashik.audio.Downloads
import org.anush.bahubhashik.data.Api

private object Route {
    const val HOME = "home"

    const val COMMUNITY = "community"
    const val PICKER = "picker"
    const val CONVERSATION = "conversation/{other}"

    const val COMPOSE = "compose"
    const val COMPOSE_NEW = "compose/new"
    const val COMPOSITION = "composition/{id}"

    /** Reachable from the drawer, so they show the menu button rather than back. */
    val TOP_LEVEL = setOf(HOME, COMMUNITY, COMPOSE)

    fun conversation(other: String) = "conversation/$other"
    fun composition(id: String) = "composition/$id"
}

/**
 * Everything after sign-in. A real back stack rather than a single screen
 * variable, which is what makes the Android system back button behave —
 * previously it closed the app from anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(api: Api, me: String, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var confirmSignOut by remember { mutableStateOf(false) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTopLevel = route == null || route in Route.TOP_LEVEL

    if (confirmSignOut) {
        // Signing out wipes saved recordings, which is not something to do
        // silently to files someone chose to keep.
        AlertDialog(
            // Same reason as the drawer: the default surface is lavender.
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Messages you saved to this phone will be removed. " +
                        "You can save them again after signing back in.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    Downloads.deleteAll()
                    onSignOut()
                }) {
                    Text("Sign out", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text("Stay signed in", style = MaterialTheme.typography.bodyMedium)
                }
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DrawerContents(
                me = me,
                current = route,
                onGo = { destination ->
                    scope.launch { drawer.close() }
                    nav.navigate(destination) {
                        // Hopping between sections shouldn't grow the back
                        // stack: back from any of them goes Home, and back
                        // from Home leaves the app.
                        popUpTo(Route.HOME) { inclusive = destination == Route.HOME }
                        launchSingleTop = true
                    }
                },
                onSignOut = {
                    scope.launch { drawer.close() }
                    confirmSignOut = true
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            titleFor(route, entry?.arguments?.read { getStringOrNull("other") }, me),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (isTopLevel) scope.launch { drawer.open() } else nav.popBackStack()
                            },
                        ) {
                            Text(
                                if (isTopLevel) "☰" else "‹",
                                fontSize = if (isTopLevel) 24.sp else 34.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { insets ->
            NavHost(
                navController = nav,
                startDestination = Route.HOME,
                modifier = Modifier.padding(insets),
            ) {
                composable(Route.HOME) {
                    HomeScreen(
                        onCommunity = { nav.navigate(Route.COMMUNITY) },
                        onCompose = { nav.navigate(Route.COMPOSE) },
                    )
                }

                composable(Route.COMMUNITY) {
                    CommunityScreen(
                        api = api,
                        me = me,
                        onOpenConversation = { nav.navigate(Route.conversation(it)) },
                        onNewMessage = { nav.navigate(Route.PICKER) },
                    )
                }
                composable(Route.PICKER) {
                    PeoplePickerScreen(
                        api = api,
                        me = me,
                        onPick = { person ->
                            // Replace the picker so back from a conversation
                            // returns to the list, not to choosing a person.
                            nav.navigate(Route.conversation(person)) {
                                popUpTo(Route.PICKER) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Route.CONVERSATION) { backStackEntry ->
                    val other = backStackEntry.arguments?.read { getStringOrNull("other") }.orEmpty()
                    ConversationScreen(api = api, me = me, other = other)
                }

                composable(Route.COMPOSE) {
                    ComposeHistoryScreen(
                        api = api,
                        me = me,
                        onNew = { nav.navigate(Route.COMPOSE_NEW) },
                        onOpen = { nav.navigate(Route.composition(it)) },
                    )
                }
                composable(Route.COMPOSE_NEW) {
                    NewCompositionScreen(
                        api = api,
                        me = me,
                        onCreated = { id ->
                            // Same trick as the picker: back from the result
                            // goes to the list, not to an empty recorder.
                            nav.navigate(Route.composition(id)) {
                                popUpTo(Route.COMPOSE_NEW) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Route.COMPOSITION) { backStackEntry ->
                    val id = backStackEntry.arguments?.read { getStringOrNull("id") }.orEmpty()
                    CompositionScreen(api = api, id = id)
                }
            }
        }
    }
}

private fun titleFor(route: String?, other: String?, me: String): String = when (route) {
    Route.COMMUNITY -> "Community"
    Route.PICKER -> "Send to"
    Route.CONVERSATION -> other.orEmpty().ifBlank { "Conversation" }
    Route.COMPOSE -> "Compose"
    Route.COMPOSE_NEW -> "New translation"
    Route.COMPOSITION -> "Translation"
    else -> me
}

@Composable
private fun DrawerContents(
    me: String,
    current: String?,
    onGo: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    // Without this the sheet uses Material's default lavender surface,
    // which has nothing to do with the rest of the app.
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("BahuBhashik", style = MaterialTheme.typography.titleLarge)
            Text(
                "Signed in as $me",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        DrawerRow("Home", current == Route.HOME) { onGo(Route.HOME) }
        // Hidden for now — see HomeScreen.
        // DrawerRow("Community", current == Route.COMMUNITY) { onGo(Route.COMMUNITY) }
        DrawerRow("Compose", current == Route.COMPOSE) { onGo(Route.COMPOSE) }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        DrawerRow("Sign out", selected = false, onClick = onSignOut)
    }
}

@Composable
private fun DrawerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        colors = NavigationDrawerItemDefaults.colors(),
    )
}
