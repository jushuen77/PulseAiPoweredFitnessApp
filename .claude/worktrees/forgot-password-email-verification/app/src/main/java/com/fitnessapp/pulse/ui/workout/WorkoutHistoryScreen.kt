package com.fitnessapp.pulse.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.HistoryEmptyState
import com.fitnessapp.pulse.ui.components.HistoryEntryCard
import com.fitnessapp.pulse.ui.components.HistoryLoadingState
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.components.formatHistoryDate
import com.fitnessapp.pulse.ui.theme.Sage

@Composable
fun WorkoutHistoryScreen(
    onBack: () -> Unit,
    viewModel: WorkoutViewModel = viewModel(),
    onSelectWorkout: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentViewModel by rememberUpdatedState(viewModel)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentViewModel.loadHistory()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { PulseTopBar(title = "Workout History", onBack = onBack) }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isHistoryLoading && uiState.history.isEmpty() -> HistoryLoadingState()
                uiState.history.isEmpty() -> HistoryEmptyState(
                    icon = Icons.Default.FitnessCenter,
                    title = "No workouts yet",
                    message = "Generate a plan from the planner and it'll show up here."
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.history) { workout ->
                        HistoryEntryCard(
                            icon = Icons.Default.FitnessCenter,
                            iconTint = Sage,
                            title = workout.goal,
                            subtitle = "${workout.level} · ${workout.daysPerWeek} days/week",
                            dateText = formatHistoryDate(workout.createdAt),
                            onClick = { workout.id?.let { onSelectWorkout(it) } }
                        )
                    }
                }
            }
        }
    }
}