package com.fitnessapp.pulse.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.IconChip
import com.fitnessapp.pulse.ui.components.PulseCard
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.Gold
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper
import com.fitnessapp.pulse.ui.theme.Sage

@Composable
fun HomeScreen(
    onNavigateToWorkout: () -> Unit,
    onNavigateToMacros: () -> Unit,
    onNavigateToWellness: () -> Unit,
    onNavigateToChat: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        val friendlyName = uiState.displayName ?: uiState.username
        Text(
            friendlyName?.let { "Welcome back, $it" } ?: "Welcome to Pulse",
            style = MaterialTheme.typography.headlineMedium,
            color = Paper
        )
        uiState.username?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }

        Spacer(modifier = Modifier.height(20.dp))

        PulseCard(modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToChat)) {
            GlanceRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                iconTint = Ember,
                title = "AI Assistant",
                subtitle = "Ask me anything — fitness, nutrition, or general questions"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("At a Glance", style = MaterialTheme.typography.titleMedium, color = Paper)
        Spacer(modifier = Modifier.height(10.dp))

        PulseCard(modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToWorkout)) {
            GlanceRow(
                icon = Icons.Default.FitnessCenter,
                iconTint = Ember,
                title = "Latest Workout Plan",
                subtitle = uiState.latestWorkout?.let { "${it.goal} · ${it.level}" }
                    ?: "No plans yet — generate one to get started"
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        PulseCard(modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToMacros)) {
            GlanceRow(
                icon = Icons.Default.Restaurant,
                iconTint = Gold,
                title = "Latest Meal Logged",
                subtitle = uiState.latestMeal?.let { "${it.foodName} · ${it.calories} cal" }
                    ?: "No meals logged yet"
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        PulseCard(modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToWellness)) {
            GlanceRow(
                icon = Icons.Default.SelfImprovement,
                iconTint = Sage,
                title = "Latest Check-in",
                subtitle = uiState.latestCheckIn?.let { "PHQ-9: ${it.phq9Score} (${it.phq9Severity})" }
                    ?: "No check-ins yet"
            )
        }
    }
}

@Composable
private fun GlanceRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconChip(icon, iconTint, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Paper)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(20.dp)
        )
    }
}
