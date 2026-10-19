package com.fitnessapp.pulse.ui.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.Line
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper
import com.fitnessapp.pulse.ui.theme.Sage
import com.fitnessapp.pulse.ui.theme.Surface

private enum class WorkoutTab(val label: String, val icon: ImageVector) {
    PLAN("Plan", Icons.Default.EditCalendar),
    ACTIVE("Active", Icons.Default.Bolt)
}

@Composable
fun WorkoutHomeScreen(
    workoutViewModel: WorkoutViewModel,
    onNavigateToHistory: () -> Unit,
    onBeginActiveWorkout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(WorkoutTab.PLAN) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Workout",
                style = MaterialTheme.typography.headlineMedium,
                color = Paper
            )
            // Lives here, not inside either tab, since it covers history from both: generated
            // plans (Plan tab) and completed sessions (Active tab).
            TextButton(onClick = onNavigateToHistory) {
                Text("History", color = Sage)
            }
        }

        WorkoutTabSelector(
            selectedTab = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                WorkoutTab.PLAN -> WorkoutPlannerScreen(viewModel = workoutViewModel)
                WorkoutTab.ACTIVE -> ActiveWorkoutStartScreen(onBegin = onBeginActiveWorkout)
            }
        }
    }
}

/** A single unified segmented control (rather than two separate floating buttons) for Plan vs Active. */
@Composable
private fun WorkoutTabSelector(
    selectedTab: WorkoutTab,
    onSelect: (WorkoutTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(52.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Line),
        shape = MaterialTheme.shapes.large
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            WorkoutTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val background by animateColorAsState(if (selected) Ember else Color.Transparent, label = "tabBg")
                val content by animateColorAsState(if (selected) Color.White else Muted, label = "tabFg")

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(tab) }
                        .background(background, RoundedCornerShape(12.dp)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(tab.icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = content
                    )
                }
            }
        }
    }
}
