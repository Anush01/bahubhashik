package org.anush.bahubhashik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import org.anush.bahubhashik.data.Api

private object Route {
    const val INBOX = "inbox"
    const val SENT = "sent"
    const val PICKER = "picker"
    const val CONVERSATION = "conversation/{other}"

    fun conversation(other: String) = "conversation/$other"
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

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTopLevel = route == null || route == Route.INBOX || route == Route.SENT

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DrawerContents(
                me = me,
                current = route,
                onGo = { destination ->
                    scope.launch { drawer.close() }
                    nav.navigate(destination) {
                        // Switching between the two lists shouldn't grow the
                        // back stack — back from either should leave the app.
                        popUpTo(Route.INBOX) { inclusive = destination == Route.INBOX }
                        launchSingleTop = true
                    }
                },
                onSignOut = {
                    scope.launch { drawer.close() }
                    onSignOut()
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
                startDestination = Route.INBOX,
                modifier = Modifier.padding(insets),
            ) {
                composable(Route.INBOX) {
                    InboxScreen(
                        api = api,
                        me = me,
                        onOpenConversation = { nav.navigate(Route.conversation(it)) },
                        onNewMessage = { nav.navigate(Route.PICKER) },
                    )
                }
                composable(Route.SENT) {
                    SentScreen(
                        api = api,
                        me = me,
                        onOpenConversation = { nav.navigate(Route.conversation(it)) },
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
            }
        }
    }
}

private fun titleFor(route: String?, other: String?, me: String): String = when (route) {
    Route.SENT -> "Messages I sent"
    Route.PICKER -> "Send to"
    Route.CONVERSATION -> other.orEmpty().ifBlank { "Conversation" }
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

        DrawerRow("Messages for me", current == Route.INBOX) { onGo(Route.INBOX) }
        DrawerRow("Messages I sent", current == Route.SENT) { onGo(Route.SENT) }

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
