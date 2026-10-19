package com.fitnessapp.pulse.ui.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.HistoryEmptyState
import com.fitnessapp.pulse.ui.components.HistoryLoadingState
import com.fitnessapp.pulse.ui.components.IconChip
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.components.formatHistoryDate
import com.fitnessapp.pulse.ui.theme.*

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    workoutId: Long,
    viewModel: WorkoutViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        viewModel.loadWorkoutDetail(workoutId)
    }

    Scaffold(
        topBar = { PulseTopBar(title = "Workout Session", onBack = onBack) }
    ) { innerPadding ->
        when {
            uiState.isDetailLoading -> HistoryLoadingState(modifier = Modifier.padding(innerPadding))
            uiState.selectedWorkout == null -> HistoryEmptyState(
                icon = Icons.Default.SearchOff,
                title = "Session not found",
                message = "This workout session may have been removed.",
                modifier = Modifier.padding(innerPadding)
            )
            else -> {
                val workout = uiState.selectedWorkout!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(icon = Icons.Default.FitnessCenter, tint = Sage, size = 52.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                workout.goal,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Paper,
                                fontWeight = FontWeight.SemiBold
                            )
                            val dateText = formatHistoryDate(workout.createdAt)
                            if (dateText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(dateText, style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBadge(
                            icon = Icons.Default.Timeline,
                            label = workout.level,
                            modifier = Modifier.weight(1f)
                        )
                        StatBadge(
                            icon = Icons.Default.CalendarMonth,
                            label = "${workout.daysPerWeek} days/week",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Plan",
                        style = MaterialTheme.typography.titleMedium,
                        color = Paper,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    WorkoutPlanSections(plan = workout.planText)
                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Ember),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Delete Session") }
                }

                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Delete this workout session?") },
                        text = { Text("This can't be undone.") },
                        confirmButton = {
                            TextButton(onClick = { viewModel.deleteWorkout(onDeleted) }) {
                                Text("Delete", color = Ember)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBadge(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Surface2,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Ember, modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Paper, fontWeight = FontWeight.Medium)
        }
    }
}
