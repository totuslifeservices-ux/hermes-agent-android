package com.nousresearch.hermes.agent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nousresearch.hermes.agent.HermesApplication
import com.nousresearch.hermes.agent.ui.chat.ChatScreen
import com.nousresearch.hermes.agent.ui.chat.ChatViewModel
import com.nousresearch.hermes.agent.ui.sessions.SessionListScreen
import com.nousresearch.hermes.agent.ui.sessions.SessionListViewModel
import com.nousresearch.hermes.agent.ui.settings.SettingsScreen
import com.nousresearch.hermes.agent.ui.settings.SettingsViewModel
import com.nousresearch.hermes.agent.ui.tools.ToolDashboardScreen
import com.nousresearch.hermes.agent.ui.tools.ToolDashboardViewModel

// ── Route definitions ───────────────────────────────────────────────

object Routes {
    const val CHAT = "chat"
    const val CHAT_NEW = "chat/new"
    const val CHAT_SESSION = "chat/{sessionId}"
    const val SESSIONS = "sessions"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"

    fun chatSession(sessionId: String) = "chat/$sessionId"
}

/**
 * Bottom navigation tab items.
 */
private data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(
        route = Routes.CHAT_NEW,
        label = "Chat",
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.Chat,
    ),
    BottomNavItem(
        route = Routes.SESSIONS,
        label = "Sessions",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
    ),
    BottomNavItem(
        route = Routes.TOOLS,
        label = "Tools",
        selectedIcon = Icons.Filled.Extension,
        unselectedIcon = Icons.Outlined.Extension,
    ),
    BottomNavItem(
        route = Routes.SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
)

/**
 * HermesNavHost — Root navigation container with bottom navigation bar.
 *
 * Routes:
 * - /chat/new — New chat session
 * - /chat/{sessionId} — Existing chat session
 * - /sessions — Session history
 * - /tools — Tool dashboard
 * - /settings — Settings page
 *
 * Bottom bar is hidden during splash screen.
 *
 * @param showBottomBar Whether to show the bottom navigation bar
 * @param onNavigateToChat Callback when navigating to the chat tab
 */
@Composable
fun HermesNavHost(
    showBottomBar: Boolean = true,
    onNavigateToChat: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val app = HermesApplication.instance
    val orchestrator = requireNotNull(app.orchestrator) {
        "AgentOrchestrator not initialized. Call HermesApplication.initAgentOrchestrator() first."
    }

    // Create shared viewmodels
    val chatViewModel = remember { ChatViewModel(orchestrator) }
    val settingsViewModel = remember { SettingsViewModel(app, orchestrator) }
    val sessionListViewModel = remember { SessionListViewModel(orchestrator.sessionStore) }
    val toolDashboardViewModel = remember { ToolDashboardViewModel(com.nousresearch.hermes.agent.core.tools.ToolRegistry()) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                HermesBottomNavBar(navController = navController)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CHAT_NEW,
            modifier = Modifier.padding(innerPadding),
        ) {
            // ── Chat ────────────────────────────────────────────────
            composable(route = Routes.CHAT_NEW) {
                chatViewModel.startNewSession()
                ChatScreen(
                    viewModel = chatViewModel,
                    onNewSession = {
                        chatViewModel.startNewSession()
                        navController.navigate(Routes.CHAT_NEW) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(
                route = Routes.CHAT_SESSION,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId")
                if (sessionId != null) {
                    chatViewModel.loadSession(sessionId)
                }
                ChatScreen(
                    viewModel = chatViewModel,
                    onNewSession = {
                        chatViewModel.startNewSession()
                        navController.navigate(Routes.CHAT_NEW) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }

            // ── Sessions ────────────────────────────────────────────
            composable(route = Routes.SESSIONS) {
                sessionListViewModel.refresh()
                SessionListScreen(
                    viewModel = sessionListViewModel,
                    onSessionSelected = { sessionId ->
                        navController.navigate(Routes.chatSession(sessionId)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                )
            }

            // ── Tools ───────────────────────────────────────────────
            composable(route = Routes.TOOLS) {
                ToolDashboardScreen(viewModel = toolDashboardViewModel)
            }

            // ── Settings ────────────────────────────────────────────
            composable(route = Routes.SETTINGS) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}

/**
 * Bottom navigation bar with 4 tabs.
 */
@Composable
private fun HermesBottomNavBar(
    navController: NavHostController,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.6f,
                    ),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.6f,
                    ),
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ),
            )
        }
    }
}
