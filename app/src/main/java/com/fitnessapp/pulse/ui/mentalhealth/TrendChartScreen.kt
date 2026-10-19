package com.fitnessapp.pulse.ui.mentalhealth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.PulseCard
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.components.formatHistoryDate
import com.fitnessapp.pulse.ui.theme.*

@Composable
fun TrendChartScreen(
    onBack: () -> Unit,
    viewModel: MentalHealthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    Scaffold(
        topBar = { PulseTopBar(title = "Trend Chart", onBack = onBack) }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("Mood Trend", style = MaterialTheme.typography.headlineMedium, color = Paper)
            Text("PHQ-9 score over time", style = MaterialTheme.typography.bodyMedium, color = Muted)
            Spacer(modifier = Modifier.height(20.dp))

            if (uiState.history.isEmpty()) {
                Text("No check-ins yet — complete one to see your trend.", color = Muted)
            } else {
                val entries = uiState.history.reversed() // oldest to newest
                val maxScore = 27f // PHQ-9 max possible
                val yLabels = listOf(27, 18, 9, 0)

                PulseCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row {
                            Column(
                                modifier = Modifier.height(180.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                yLabels.forEach {
                                    Text("$it", style = MaterialTheme.typography.labelSmall, color = Muted)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Canvas(modifier = Modifier.weight(1f).height(180.dp)) {
                                // Gridlines shared with the y-axis labels, plus the axes themselves.
                                yLabels.forEach { value ->
                                    val y = size.height - (value / maxScore) * size.height
                                    drawLine(
                                        color = Line,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1f
                                    )
                                }
                                drawLine(color = Line, start = Offset(0f, 0f), end = Offset(0f, size.height), strokeWidth = 2f)
                                drawLine(
                                    color = Line,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 2f
                                )

                                val stepX = if (entries.size > 1) size.width / (entries.size - 1) else size.width
                                val points = entries.mapIndexed { index, entry ->
                                    Offset(
                                        x = index * stepX,
                                        y = size.height - (entry.phq9Score / maxScore) * size.height
                                    )
                                }
                                for (i in 0 until points.size - 1) {
                                    drawLine(color = Sage, start = points[i], end = points[i + 1], strokeWidth = 4f)
                                }
                                points.forEach { point -> drawCircle(color = Ember, radius = 6f, center = point) }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.width(24.dp))
                            Text(
                                formatHistoryDate(entries.first().loggedAt).substringBefore(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Muted
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (entries.size > 1) {
                                Text(
                                    formatHistoryDate(entries.last().loggedAt).substringBefore(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Muted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(Sage, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PHQ-9 score (0-27)", style = MaterialTheme.typography.labelSmall, color = Muted)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Lower scores indicate fewer reported symptoms. This is a self-report trend, not a diagnosis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
        }
    }
}