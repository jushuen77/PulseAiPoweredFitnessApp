package com.fitnessapp.pulse.ui.mentalhealth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.data.model.QuestionnaireItem
import com.fitnessapp.pulse.data.model.dassAnxietyItems
import com.fitnessapp.pulse.data.model.dassAnxietySeverity
import com.fitnessapp.pulse.data.model.dassDepressionItems
import com.fitnessapp.pulse.data.model.dassDepressionSeverity
import com.fitnessapp.pulse.data.model.dassOptions
import com.fitnessapp.pulse.data.model.dassStressItems
import com.fitnessapp.pulse.data.model.dassStressSeverity
import com.fitnessapp.pulse.data.model.phq9Items
import com.fitnessapp.pulse.data.model.phq9Options
import com.fitnessapp.pulse.data.model.phq9Severity
import com.fitnessapp.pulse.ui.components.IconChip
import com.fitnessapp.pulse.ui.components.PulseCard
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.Gold
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper
import com.fitnessapp.pulse.ui.theme.Sage
import com.fitnessapp.pulse.ui.theme.SageOnColor

@Composable
fun MentalHealthScreen(
    onNavigateToHistory: () -> Unit,
    viewModel: MentalHealthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Every stage shares this one scroll position; without resetting it on an actual stage change
    // (e.g. tapping "Continue" while scrolled near the bottom of a long question list), the next
    // stage's content would start mid-scroll instead of at the top. But scrollState already survives
    // rotation on its own (it's rememberSaveable internally) — comparing against the last-seen stage,
    // rather than resetting on every recomposition, keeps a rotation from re-triggering this and
    // wiping out that naturally-restored scroll position.
    var lastStageName by rememberSaveable { mutableStateOf(uiState.stage.name) }
    LaunchedEffect(uiState.stage) {
        if (uiState.stage.name != lastStageName) {
            scrollState.scrollTo(0)
            lastStageName = uiState.stage.name
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Wellness Check-in",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Paper
                )
                Text(
                    "PHQ-9 & DASS-21 screening",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onNavigateToHistory) {
                Text("History", color = Sage)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PulseCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "This is a self-report screening tool, not a diagnosis. If you're in crisis, please reach out to a trusted person or a helpline right away.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Crisis interrupt — shown immediately, doesn't wait for the questionnaire to finish
        if (uiState.showCrisisResources) {
            PulseCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        "You're not alone",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ember
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "It sounds like things feel really heavy right now. Please reach out to someone you trust, or a crisis line, as soon as you can — you deserve support.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Paper
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Malaysia Befrienders KL: 03-7627 2929 · International: findahelpline.com",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sage
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        when (uiState.stage) {
            CheckInStage.INTRO -> {
                Button(
                    onClick = { viewModel.startCheckIn() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Sage,
                        contentColor = SageOnColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Begin Check-in", style = MaterialTheme.typography.bodyLarge)
                }
            }

            CheckInStage.PHQ9 -> {
                Text(
                    "Over the last 2 weeks...",
                    style = MaterialTheme.typography.titleMedium,
                    color = Paper
                )
                Spacer(modifier = Modifier.height(12.dp))
                phq9Items.forEach { item ->
                    QuestionCard(
                        item = item,
                        options = phq9Options,
                        selected = uiState.phq9Answers[item.id],
                        onSelect = { viewModel.answerPhq9(item.id, it) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Button(
                    onClick = { viewModel.proceedToDass() },
                    enabled = uiState.phq9Answers.size == phq9Items.size,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Sage,
                        contentColor = SageOnColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Continue")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.resetCheckIn() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = Ember)
                }
            }

            CheckInStage.DASS21 -> {
                Text(
                    "Over the past week...",
                    style = MaterialTheme.typography.titleMedium,
                    color = Paper
                )
                Spacer(modifier = Modifier.height(12.dp))
                (dassDepressionItems + dassAnxietyItems + dassStressItems).forEach { item ->
                    QuestionCard(
                        item = item,
                        options = dassOptions,
                        selected = uiState.dassAnswers[item.id],
                        onSelect = { viewModel.answerDass(item.id, it) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                uiState.errorMessage?.let {
                    Text(it, color = Ember, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val allAnswered = uiState.dassAnswers.size ==
                        (dassDepressionItems.size + dassAnxietyItems.size + dassStressItems.size)

                Button(
                    onClick = { viewModel.submitCheckIn() },
                    enabled = allAnswered && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Sage,
                        contentColor = SageOnColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = SageOnColor
                        )
                    } else {
                        Text("See My Results")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { viewModel.backToPhq9() }, enabled = !uiState.isLoading) {
                        Text("Back", color = Sage)
                    }
                    TextButton(onClick = { viewModel.resetCheckIn() }, enabled = !uiState.isLoading) {
                        Text("Cancel", color = Ember)
                    }
                }
            }

            CheckInStage.RESULT -> {
                PulseCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "Your Results",
                            style = MaterialTheme.typography.titleMedium,
                            color = Paper
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ResultRow(
                            "PHQ-9 (Depression)",
                            uiState.phq9Total,
                            phq9Severity(uiState.phq9Total)
                        )
                        ResultRow(
                            "DASS-21 Depression",
                            uiState.dassDepressionTotal,
                            dassDepressionSeverity(uiState.dassDepressionTotal)
                        )
                        ResultRow(
                            "DASS-21 Anxiety",
                            uiState.dassAnxietyTotal,
                            dassAnxietySeverity(uiState.dassAnxietyTotal)
                        )
                        ResultRow(
                            "DASS-21 Stress",
                            uiState.dassStressTotal,
                            dassStressSeverity(uiState.dassStressTotal)
                        )
                    }
                }

                uiState.reflection?.let { reflection ->
                    Spacer(modifier = Modifier.height(16.dp))
                    PulseCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconChip(icon = Icons.Default.SelfImprovement, tint = Sage)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Your Reflection",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Paper
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(reflection, style = MaterialTheme.typography.bodyMedium, color = Paper)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { viewModel.resetCheckIn() }) {
                    Text("Start a New Check-in", color = Sage)
                }
            }
        }
    }
}

@Composable
private fun QuestionCard(
    item: QuestionnaireItem,
    options: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    PulseCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(item.text, style = MaterialTheme.typography.bodyLarge, color = Paper)
            Spacer(modifier = Modifier.height(10.dp))
            options.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected == index, onClick = { onSelect(index) }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == index,
                        onClick = { onSelect(index) },
                        colors = RadioButtonDefaults.colors(selectedColor = Sage)
                    )
                    Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun severityColor(severity: String): Color = when (severity) {
    "Minimal", "Normal" -> Sage
    "Mild", "Moderate" -> Gold
    else -> Ember // Moderately Severe, Severe
}

@Composable
private fun ResultRow(label: String, score: Int, severity: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$score", color = Paper, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(severityColor(severity).copy(alpha = 0.14f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    severity,
                    color = severityColor(severity),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}