package com.fitnessapp.pulse.ui.mentalhealth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.HistoryEmptyState
import com.fitnessapp.pulse.ui.components.HistoryEntryCard
import com.fitnessapp.pulse.ui.components.HistoryLoadingState
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.components.formatHistoryDate
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.Gold
import com.fitnessapp.pulse.ui.theme.Sage

@Composable
fun CheckInHistoryScreen(
    onBack: () -> Unit,
    viewModel: MentalHealthViewModel = viewModel(),
    onNavigateToTrend: () -> Unit,
    onSelectCheckIn: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadHistory()
    }

    Scaffold(
        topBar = {
            PulseTopBar(
                title = "Check-in History",
                onBack = onBack,
                actionLabel = "View Trend",
                onAction = onNavigateToTrend
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isHistoryLoading && uiState.history.isEmpty() -> HistoryLoadingState()
                uiState.history.isEmpty() -> HistoryEmptyState(
                    icon = Icons.Default.Psychology,
                    title = "No check-ins yet",
                    message = "Complete a check-in and it'll show up here."
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.history) { checkIn ->
                        HistoryEntryCard(
                            icon = Icons.Default.Psychology,
                            iconTint = severityColor(checkIn.phq9Severity),
                            title = "PHQ-9: ${checkIn.phq9Score} (${checkIn.phq9Severity})",
                            subtitle = "Depression ${checkIn.dassDepressionScore} · Anxiety ${checkIn.dassAnxietyScore} · Stress ${checkIn.dassStressScore}",
                            dateText = formatHistoryDate(checkIn.loggedAt),
                            onClick = checkIn.id?.let { id -> { onSelectCheckIn(id) } }
                        )
                    }
                }
            }
        }
    }
}

private fun severityColor(severity: String): Color = when {
    severity.contains("minimal", ignoreCase = true) || severity.contains("none", ignoreCase = true) -> Sage
    severity.contains("mild", ignoreCase = true) -> Gold
    else -> Ember
}
