package com.fitnessapp.pulse.ui.mentalhealth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.fitnessapp.pulse.ui.components.PlaceholderScreen
import com.fitnessapp.pulse.ui.navigation.Tab

fun NavGraphBuilder.mindNavGraph(navController: NavHostController) {
    navigation(startDestination = "mind_home", route = Tab.Mind.route) {

        composable("mind_home") {
            MentalHealthScreen(
                onNavigateToHistory = { navController.navigate("check_in_history") }

            )
        }

        composable("mood_history") {
            TrendChartScreen(onBack = {navController.popBackStack() }) }
        composable("check_in_history") {
            CheckInHistoryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToTrend = { navController.navigate("mood_history") },
                onSelectCheckIn = { id -> navController.navigate("check_in_detail/$id") }
            )
        }
        composable("check_in_detail/{checkInId}") { entry ->
            val checkInId = entry.arguments?.getString("checkInId")?.toLongOrNull()
            if (checkInId != null) {
                CheckInDetailScreen(
                    checkInId = checkInId,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // Guided Reset branch
        composable("guided_reset") { PlaceholderScreen("Guided Reset", onBack = { navController.popBackStack() }) }
        composable("mind_breathing") { PlaceholderScreen("Breathing", onBack = { navController.popBackStack() }) }
        composable("timer_countdown") { PlaceholderScreen("Timer Countdown", onBack = { navController.popBackStack() }) }
        composable("session_complete") { PlaceholderScreen("Session Complete", onBack = { navController.popBackStack() }) }

        // Mood History branch
        composable("calendar_view") { PlaceholderScreen("Calendar View", onBack = { navController.popBackStack() }) }
        composable("list_view") { PlaceholderScreen("List View", onBack = { navController.popBackStack() }) }
        composable("trend_chart") { PlaceholderScreen("Trend Chart", onBack = { navController.popBackStack() }) }

        // Journal Entries branch
        composable("journal_entries") { PlaceholderScreen("Journal Entries", onBack = { navController.popBackStack() }) }
        composable("entry_list") { PlaceholderScreen("Entry List", onBack = { navController.popBackStack() }) }
        composable("entry_detail") { PlaceholderScreen("Entry Detail", onBack = { navController.popBackStack() }) }
    }
}

