package com.fitnessapp.pulse.ui.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.ui.assistant.AiChatScreen
import com.fitnessapp.pulse.ui.assistant.ChatHistoryScreen
import com.fitnessapp.pulse.ui.auth.LoginScreen
import com.fitnessapp.pulse.ui.auth.SignUpScreen
import com.fitnessapp.pulse.ui.home.HomeScreen
import com.fitnessapp.pulse.ui.macro.macroNavGraph
import com.fitnessapp.pulse.ui.mentalhealth.mindNavGraph
import com.fitnessapp.pulse.ui.profile.profileNavGraph
import com.fitnessapp.pulse.ui.theme.AppBackground
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.workout.workoutNavGraph

sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    object Home : Tab("home", "Home", Icons.Default.Home)
    object Workout : Tab("workout_graph", "Workout", Icons.Default.FitnessCenter)
    object Macros : Tab("macros", "Macros", Icons.Default.Restaurant)
    object Mind : Tab("mind", "Wellness", Icons.Default.SelfImprovement)
    object Profile : Tab("profile", "Profile", Icons.Default.Person)
}

/**
 * Which tab owns each AI-navigable destination (mirrors AiChatViewModel's NAV_DESTINATIONS).
 * Navigating straight to a nested sub-route (e.g. from the chat screen) skips over its tab's
 * home screen in the back stack, since Navigation Compose only pushes the graph hierarchy needed
 * to reach the target, not sibling screens — so a screen whose back button pops to a specific
 * named route (like the active workout flow's "back to workout_home") silently no-ops. Routing
 * through the owning tab first, same as a real tap-through would, keeps that route on the stack.
 */
private val DESTINATION_TABS: Map<String, Tab> = mapOf(
    "home" to Tab.Home,
    "workout_graph" to Tab.Workout,
    "active_workout_flow" to Tab.Workout,
    "workout_history" to Tab.Workout,
    "macros" to Tab.Macros,
    "manual_entry" to Tab.Macros,
    "meal_history" to Tab.Macros,
    "mind" to Tab.Mind,
    "check_in_history" to Tab.Mind,
    "profile" to Tab.Profile,
    "edit_profile" to Tab.Profile,
    "settings" to Tab.Profile,
    "profile_history" to Tab.Profile,
    "qna" to Tab.Profile,
    "terms" to Tab.Profile
)

@OptIn(ExperimentalMaterial3WindowSizeClassApi:: class)
@Composable
fun PulseNavGraph(windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    val context = LocalContext.current

    var isReady by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf("login") }

    LaunchedEffect(Unit) {
        SessionManager.restore(context)
        startDestination = if (SessionManager.userId != null) "main" else "login"
        isReady = true
    }

    if (!isReady) {
        Box(
            modifier = Modifier.fillMaxSize().background(AppBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Ember)
        }
        return
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { navController.navigate("main") { popUpTo("login") { inclusive = true } } },
                onNavigateToSignUp = { navController.navigate("signup") }
            )
        }
        composable("signup") {
            SignUpScreen(
                onSignUpSuccess = { navController.navigate("main") { popUpTo("login") { inclusive = true } } },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable("main") {
            MainAppScaffold(
                windowSizeClass = windowSizeClass,
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
private fun MainAppScaffold(
    windowSizeClass: WindowSizeClass,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val tabs = listOf(Tab.Home, Tab.Workout, Tab.Macros, Tab.Mind, Tab.Profile)
    val isWideScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    // Wrapped in movableContentOf so switching between the nav-rail (wide) and bottom-bar (compact)
    // layouts on a width-breakpoint-crossing rotation moves this same NavHost to its new position
    // instead of tearing it down and rebuilding it from scratch — which would otherwise wipe every
    // screen's remember/rememberSaveable state (scroll position included) on that specific rotation.
    val navHostContent = remember {
        movableContentOf<androidx.compose.ui.Modifier> { modifier ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = modifier
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    onNavigateToWorkout = { navController.navigateToTab(Tab.Workout) },
                    onNavigateToMacros = { navController.navigateToTab(Tab.Macros) },
                    onNavigateToWellness = { navController.navigateToTab(Tab.Mind) },
                    onNavigateToChat = { navController.navigate("ai_chat") }
                )
            }
            composable("ai_chat") {
                AiChatScreen(
                    onBack = { navController.popBackStack() },
                    onOpenHistory = { navController.navigate("chat_history") },
                    onNavigate = { destination, goal ->
                        val tab = DESTINATION_TABS[destination]
                        if (tab != null) {
                            navController.navigateToTab(tab)
                            if (destination != tab.route) {
                                val route = if (destination == "active_workout_flow" && !goal.isNullOrBlank()) {
                                    "active_workout_flow?goal=${Uri.encode(goal)}"
                                } else {
                                    destination
                                }
                                navController.navigate(route)
                            }
                        } else {
                            navController.navigate(destination)
                        }
                    }
                )
            }
            composable("chat_history") {
                ChatHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onSelectSession = { sessionId ->
                        navController.navigate("chat_session_detail/${Uri.encode(sessionId)}")
                    }
                )
            }
            composable("chat_session_detail/{sessionId}") { entry ->
                val sessionId = entry.arguments?.getString("sessionId")
                if (sessionId != null) {
                    AiChatScreen(
                        onBack = { navController.popBackStack() },
                        resumeSessionId = sessionId,
                        onSessionDeleted = { navController.popBackStack() },
                        onNavigate = { destination, goal ->
                            val tab = DESTINATION_TABS[destination]
                            if (tab != null) {
                                navController.navigateToTab(tab)
                                if (destination != tab.route) {
                                    val route = if (destination == "active_workout_flow" && !goal.isNullOrBlank()) {
                                        "active_workout_flow?goal=${Uri.encode(goal)}"
                                    } else {
                                        destination
                                    }
                                    navController.navigate(route)
                                }
                            } else {
                                navController.navigate(destination)
                            }
                        }
                    )
                }
            }
            workoutNavGraph(navController)
            macroNavGraph(navController)
            mindNavGraph(navController)
            profileNavGraph(navController, onLogout)
        }
        }
    }

    if (isWideScreen) {
        // TABLET / WIDE LAYOUT — nav rail on the side
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavigationRail {
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                tabs.forEach { tab ->
                    NavigationRailItem(
                        selected = currentRoute == tab.route,
                        onClick = { navController.navigateToTab(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
            navHostContent(Modifier.weight(1f))
        }
    } else {
        // PHONE LAYOUT — bottom bar (unchanged from before)
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.navigateToTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            navHostContent(Modifier.padding(innerPadding))
        }
    }
}

// Shared navigation logic extracted so both layouts stay in sync
private fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}