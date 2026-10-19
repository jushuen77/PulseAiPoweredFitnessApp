package com.fitnessapp.pulse.ui.workout

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.data.local.WorkoutProgressStore
import com.fitnessapp.pulse.ui.components.PlaceholderScreen
import com.fitnessapp.pulse.ui.navigation.Tab

/** Optional "goal" query arg lets the AI assistant jump straight into a named goal's exercise list. */
private const val ACTIVE_WORKOUT_FLOW_ROUTE = "active_workout_flow?goal={goal}"

fun NavGraphBuilder.workoutNavGraph(navController: NavHostController) {
    navigation(startDestination = "workout_home", route = Tab.Workout.route) {

        composable("workout_home") { entry ->
            val parentEntry = remember(entry) { navController.getBackStackEntry(Tab.Workout.route) }
            val workoutVm: WorkoutViewModel = viewModel(parentEntry)
            WorkoutHomeScreen(
                workoutViewModel = workoutVm,
                onNavigateToHistory = { navController.navigate("workout_history") },
                onBeginActiveWorkout = { navController.navigate("active_workout_flow") }
            )
        }

        navigation(
            startDestination = "exercise_picker",
            route = ACTIVE_WORKOUT_FLOW_ROUTE,
            arguments = listOf(navArgument("goal") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) {
            // Once the workout has actually begun (warm-up onward), back (and "Done") always return
            // straight to the exercise picker on the goal the workout was started from — popping to
            // the existing "exercise_picker" entry (rather than navigating to a fresh one) leaves its
            // selectedGoalId untouched, whether that was picked by hand or auto-selected for the AI
            // assistant's "start my workout" flow. Popping only as far as "workout_home" instead would
            // land on whatever tab (Plan vs Active) that entry's local state happened to default to,
            // which is wrong for a flow the AI started without ever visiting workout_home by hand.
            val returnToGoal: () -> Unit = {
                navController.popBackStack("exercise_picker", inclusive = false)
            }
            composable("exercise_picker") { entry ->
                val workoutParentEntry = remember(entry) { navController.getBackStackEntry(Tab.Workout.route) }
                val workoutVm: WorkoutViewModel = viewModel(workoutParentEntry)
                val activeParentEntry = remember(entry) { navController.getBackStackEntry(ACTIVE_WORKOUT_FLOW_ROUTE) }
                val activeVm: ActiveWorkoutViewModel = viewModel(activeParentEntry)
                val workoutUiState by workoutVm.uiState.collectAsState()
                val activeUiState by activeVm.uiState.collectAsState()
                LaunchedEffect(Unit) { workoutVm.loadHistory() }
                ExercisePickerScreen(
                    history = workoutUiState.history,
                    isLoading = workoutUiState.isHistoryLoading,
                    activeExerciseName = activeUiState.selectedExerciseName,
                    activeGoalId = activeUiState.selectedGoalId,
                    userId = SessionManager.userId,
                    initialGoalName = activeParentEntry.arguments?.getString("goal"),
                    onSelect = { goalId, dayIndex, dayExercises, startIndex ->
                        activeVm.selectExercise(goalId, dayIndex, dayExercises, startIndex)
                        navController.navigate("warm_up")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("warm_up") { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(ACTIVE_WORKOUT_FLOW_ROUTE) }
                val vm: ActiveWorkoutViewModel = viewModel(parentEntry)
                WarmUpScreen(
                    vm,
                    onNext = { navController.navigate("set_tracking") },
                    onBack = returnToGoal
                )
            }
            composable("set_tracking") { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(ACTIVE_WORKOUT_FLOW_ROUTE) }
                val vm: ActiveWorkoutViewModel = viewModel(parentEntry)
                SetTrackingScreen(
                    vm,
                    onRest = { navController.navigate("rest_timer") },
                    onFinish = { navController.navigate("workout_summary") },
                    onBack = returnToGoal
                )
            }
            composable("rest_timer") { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(ACTIVE_WORKOUT_FLOW_ROUTE) }
                val vm: ActiveWorkoutViewModel = viewModel(parentEntry)
                RestTimerScreen(
                    vm,
                    onContinue = { navController.popBackStack() },
                    onBack = returnToGoal
                )
            }
            composable("workout_summary") { entry ->
                val parentEntry = remember(entry) { navController.getBackStackEntry(ACTIVE_WORKOUT_FLOW_ROUTE) }
                val vm: ActiveWorkoutViewModel = viewModel(parentEntry)
                val context = LocalContext.current
                WorkoutSummaryScreen(
                    vm,
                    onDone = returnToGoal,
                    onBack = returnToGoal,
                    onExerciseCompleted = { goalId, names ->
                        val userId = SessionManager.userId
                        if (userId != null) WorkoutProgressStore.markExercisesCompleted(context, userId, goalId, names)
                    },
                    onDayCompleted = { goalId, dayIndex ->
                        val userId = SessionManager.userId
                        if (userId != null) WorkoutProgressStore.markDayCompleted(context, userId, goalId, dayIndex)
                    }
                )
            }
        }

        composable("exercise_detail") { PlaceholderScreen("Exercise Detail", onBack = { navController.popBackStack() }) }
        composable("exercise_instructions") { PlaceholderScreen("Instructions", onBack = { navController.popBackStack() }) }
        composable("exercise_demo_video") { PlaceholderScreen("Demo Video", onBack = { navController.popBackStack() }) }
        composable("add_to_plan") { PlaceholderScreen("Add to Plan", onBack = { navController.popBackStack() }) }

        composable("workout_history") {
            WorkoutHistoryScreen(
                onBack = { navController.popBackStack() },
                onSelectWorkout = { id -> navController.navigate("session_detail/$id") }
            )
        }
        composable("session_detail/{workoutId}") { entry ->
            val workoutId = entry.arguments?.getString("workoutId")?.toLongOrNull()
            if (workoutId != null)
                SessionDetailScreen(
                    workoutId = workoutId,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() }
                )
            }
        composable("session_list") { PlaceholderScreen("Session List", onBack = { navController.popBackStack() }) }
        composable("stats_streak") { PlaceholderScreen("Stats & Streak", onBack = { navController.popBackStack() }) }
    }
}