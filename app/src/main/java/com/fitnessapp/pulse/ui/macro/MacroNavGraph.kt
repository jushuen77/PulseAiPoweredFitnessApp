package com.fitnessapp.pulse.ui.macro

import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.fitnessapp.pulse.ui.components.PlaceholderScreen
import com.fitnessapp.pulse.ui.navigation.Tab

fun NavGraphBuilder.macroNavGraph(navController: NavHostController) {
    navigation(startDestination = "macro_home", route = Tab.Macros.route) {

        composable("macro_home") { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Tab.Macros.route) }
            val vm: MacroViewModel = viewModel(parentEntry)
            MacroDetectorScreen(
                viewModel = vm,
                onNavigateToHistory = { navController.navigate("meal_history") },
                onNavigateToManualEntry = { navController.navigate("manual_entry") }
            )
        }

        composable("manual_entry") {
            ManualEntryScreen(onBack = {navController.popBackStack()} ) }

        composable("meal_history") { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Tab.Macros.route) }
            val vm: MacroViewModel = viewModel(parentEntry)
            MealHistoryScreen(
                viewModel = vm,
                onBack = { navController.popBackStack()},
                onSelectMeal = { id -> navController.navigate("meal_detail/$id") }
            )
        }
        composable("meal_detail/{mealId}") { entry ->
            val mealId = entry.arguments?.getString("mealId")?.toLongOrNull()
            if (mealId != null) {
                val parentEntry = remember(entry) { navController.getBackStackEntry(Tab.Macros.route) }
                val vm: MacroViewModel = viewModel(parentEntry)
                MealDetailScreen(
                    mealId = mealId,
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() })
            }
        }

        composable("camera_scan") { PlaceholderScreen("Camera Scan", onBack = { navController.popBackStack() }) }
        composable("live_camera") { PlaceholderScreen("Live Camera", onBack = { navController.popBackStack() }) }
        composable("ai_analyzing") { PlaceholderScreen("AI Analyzing", onBack = { navController.popBackStack() }) }
        composable("scan_result") { PlaceholderScreen("Scan Result", onBack = { navController.popBackStack() }) }
    }
}