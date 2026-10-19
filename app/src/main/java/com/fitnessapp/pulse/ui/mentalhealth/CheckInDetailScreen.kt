package com.fitnessapp.pulse.ui.mentalhealth

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.data.model.QuestionnaireItem
import com.fitnessapp.pulse.data.model.dassAnxietyItems
import com.fitnessapp.pulse.data.model.dassDepressionItems
import com.fitnessapp.pulse.data.model.dassOptions
import com.fitnessapp.pulse.data.model.dassStressItems
import com.fitnessapp.pulse.data.model.phq9Items
import com.fitnessapp.pulse.data.model.phq9Options
import com.fitnessapp.pulse.ui.components.IconChip
import com.fitnessapp.pulse.ui.components.PulseCard
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.components.formatHistoryDate
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.Gold
import com.fitnessapp.pulse.ui.theme.Line
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper
import com.fitnessapp.pulse.ui.theme.Sage

@Composable
fun CheckInDetailScreen(
    checkInId: Long,
    onBack: () -> Unit,
    viewModel: MentalHealthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    val checkIn = remember(uiState.history, checkInId) { uiState.history.find { it.id == checkInId } }

    Scaffold(
        topBar = { PulseTopBar(title = "Check-in Summary", onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            when {
                uiState.isHistoryLoading && checkIn == null -> CircularProgressIndicator(color = Ember)
                checkIn == null -> Text("Check-in not found", color = Muted)
                else -> {
                    Text(
                        formatHistoryDate(checkIn.loggedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PulseCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("Your Results", style = MaterialTheme.typography.titleMedium, color = Paper)
                            Spacer(modifier = Modifier.height(12.dp))
                            ScoreRow("PHQ-9 (Depression)", checkIn.phq9Score, checkIn.phq9Severity)
                            ScoreRow("DASS-21 Depression", checkIn.dassDepressionScore, null)
                            ScoreRow("DASS-21 Anxiety", checkIn.dassAnxietyScore, null)
                            ScoreRow("DASS-21 Stress", checkIn.dassStressScore, null)
                        }
                    }

                    if (checkIn.phq9Answers != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        AnswersCard("PHQ-9 Responses", phq9Items, phq9Options, checkIn.phq9Answers)
                    }
                    if (checkIn.dassAnswers != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        AnswersCard(
                            "DASS-21 Responses",
                            dassDepressionItems + dassAnxietyItems + dassStressItems,
                            dassOptions,
                            checkIn.dassAnswers
                        )
                    }
                    if (checkIn.phq9Answers == null && checkIn.dassAnswers == null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Detailed answers weren't recorded for this check-in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }

                    if (checkIn.aiReflection.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        PulseCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconChip(icon = Icons.Default.SelfImprovement, tint = Sage)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Your Reflection", style = MaterialTheme.typography.titleMedium, color = Paper)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(checkIn.aiReflection, style = MaterialTheme.typography.bodyMedium, color = Paper)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Collapsed by default: tap the header to reveal each question's text and the option the user picked. */
@Composable
private fun AnswersCard(
    title: String,
    items: List<QuestionnaireItem>,
    options: List<String>,
    answers: Map<String, Int>
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")

    PulseCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(modifier = Modifier.clickable { expanded = !expanded }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Paper)
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = Muted,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = Line, thickness = 1.dp)
                    }
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(item.text, color = Paper, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            answers[item.id.toString()]?.let { options.getOrNull(it) } ?: "Not recorded",
                            color = Sage,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(label: String, score: Int, severity: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$score", color = Paper, style = MaterialTheme.typography.bodyMedium)
            severity?.let {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(severityColor(it).copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(it, color = severityColor(it), style = MaterialTheme.typography.labelSmall)
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
