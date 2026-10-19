package com.fitnessapp.pulse.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.auth.PulseTextField
import com.fitnessapp.pulse.ui.components.PulseCard
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper
import com.fitnessapp.pulse.ui.theme.Surface2
import kotlin.math.roundToInt

@Composable
fun WorkoutPlannerScreen(
    viewModel: WorkoutViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp)
    ) {
        Text(
            "Workout Planner",
            style = MaterialTheme.typography.titleLarge,
            color = Paper
        )
        Text(
            "Tell us your goal and we'll build your week",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )

        Spacer(modifier = Modifier.height(24.dp))

        PulseCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                PulseTextField(
                    label = "GOAL",
                    value = uiState.goal,
                    onValueChange = viewModel::onGoalChange
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text("FITNESS LEVEL", style = MaterialTheme.typography.labelSmall, color = Muted)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Beginner", "Intermediate", "Advanced").forEach { level ->
                        LevelChip(
                            label = level,
                            selected = uiState.level == level,
                            onClick = { viewModel.onLevelChange(level) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "DAYS PER WEEK: ${uiState.daysPerWeek}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )
                Slider(
                    value = uiState.daysPerWeek.toFloat(),
                    onValueChange = { viewModel.onDaysChange(it.roundToInt()) },
                    valueRange = 1f..7f,
                    steps = 5,
                    colors = SliderDefaults.colors(thumbColor = Ember, activeTrackColor = Ember)
                )

                Spacer(modifier = Modifier.height(8.dp))

                PulseTextField(
                    label = "EQUIPMENT (e.g. bodyweight only, dumbbells, full gym)",
                    value = uiState.equipment,
                    onValueChange = viewModel::onEquipmentChange
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        uiState.errorMessage?.let {
            Text(it, color = Ember, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = { viewModel.generatePlan() },
            enabled = !uiState.isLoading && uiState.goal.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Ember,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                Text("Generate Plan", style = MaterialTheme.typography.bodyLarge)
            }
        }

        uiState.generatedPlan?.let { plan ->
            Spacer(modifier = Modifier.height(20.dp))
            PulseCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Your Plan", style = MaterialTheme.typography.titleMedium, color = Paper)
                    Text(
                        "AI-generated — review before starting",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    WorkoutPlanSections(plan = plan)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LevelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Ember else Surface2
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White else Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}



