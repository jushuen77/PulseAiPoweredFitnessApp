package com.fitnessapp.pulse.ui.profile

import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.ui.navigation.Tab

fun NavGraphBuilder.profileNavGraph(
    navController: NavHostController,
    onLogout: () -> Unit
) {
    navigation(startDestination = "profile_home", route = Tab.Profile.route) {

        composable("profile_home") {
            val context = LocalContext.current
            ProfileScreen(
                onNavigateToEditProfile = { navController.navigate("edit_profile") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToHistory = { navController.navigate("profile_history") },
                onNavigateToQnA = { navController.navigate("qna") },
                onNavigateToTerms = { navController.navigate("terms") },
                onLogout = {
                    SessionManager.logout(context)
                    onLogout()
                }
            )
        }

        composable("edit_profile") {
            EditProfileScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable("profile_history") { ProfileHistoryHub(onBack = { navController.popBackStack() }) }
        composable("qna") { QnAScreen(onBack = { navController.popBackStack() }) }
        composable("terms") { TermsScreen(onBack = { navController.popBackStack() }) }
    }
}