package com.fitnessapp.pulse.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitnessapp.pulse.data.local.WorkoutProgressStore
import com.fitnessapp.pulse.data.remote.model.WorkoutRow
import com.fitnessapp.pulse.data.repository.WorkoutRepository
import com.fitnessapp.pulse.ui.components.PulseCard
import com.fitnessapp.pulse.ui.components.PulseTopBar
import com.fitnessapp.pulse.ui.theme.*

private const val CLICK_DEBOUNCE_MS = 600L

// Ignores repeat invocations within CLICK_DEBOUNCE_MS to stop rapid double-taps firing navigate() twice.
@Composable
private fun rememberDebounced(action: () -> Unit): () -> Unit {
    var lastInvokeMs by remember { mutableStateOf(0L) }
    return {
        val now = System.currentTimeMillis()
        if (now - lastInvokeMs >= CLICK_DEBOUNCE_MS) {
            lastInvokeMs = now
            action()
        }
    }
}

@Composable
fun ActiveWorkoutStartScreen(onBegin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Ready to train?", style = MaterialTheme.typography.headlineMedium, color = Paper)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "We'll guide you through a warm-up, your working sets, and rest periods.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = rememberDebounced(onBegin),
            colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Begin Workout")
        }
    }
}

/**
 * Two-tier picker: first choose the main goal (each generated plan, boxed and categorized),
 * then choose an exercise from that goal's plan. Persisted plans come from workout_logs.
 */
@Composable
internal fun ExercisePickerScreen(
    history: List<WorkoutRow>,
    isLoading: Boolean,
    activeExerciseName: String?,
    activeGoalId: Long?,
    userId: String?,
    initialGoalName: String? = null,
    onBack: () -> Unit,
    onSelect: (goalId: Long, dayIndex: Int, dayExercises: List<ExerciseLine>, startIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val plans = remember(history) { history.filterNot { it.goal == WorkoutRepository.COMPLETED_SESSION_GOAL } }
    var selectedGoalId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Auto-selects the goal the AI assistant was told to jump into, so the picker doesn't
    // just sit on "Choose a Goal" after the user names which one they want.
    LaunchedEffect(plans, initialGoalName) {
        if (selectedGoalId == null && !initialGoalName.isNullOrBlank()) {
            val match = plans.find { it.goal.equals(initialGoalName, ignoreCase = true) }
                ?: plans.find { it.goal.contains(initialGoalName, ignoreCase = true) }
            match?.id?.let { selectedGoalId = it }
        }
    }

    val selectedPlan = remember(plans, selectedGoalId) { plans.find { it.id == selectedGoalId } }

    val title = selectedPlan?.goal?.ifBlank { "Exercises" } ?: "Choose a Goal"
    val topBarOnBack: () -> Unit = if (selectedPlan != null) {
        { selectedGoalId = null }
    } else onBack

    Scaffold(topBar = { PulseTopBar(title = title, onBack = topBarOnBack) }) { innerPadding ->
        when {
            plans.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Ember)
                } else {
                    Text(
                        "Generate a workout plan in the Plan tab first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
            selectedPlan == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                plans.forEachIndexed { index, row ->
                    if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                    val rowGoalId = row.id
                    GoalCard(
                        row = row,
                        completedExercises = if (userId != null && rowGoalId != null) {
                            WorkoutProgressStore.getCompletedExercises(context, userId, rowGoalId)
                        } else emptySet(),
                        isActive = activeGoalId != null && row.id == activeGoalId,
                        onClick = rememberDebounced { selectedGoalId = row.id }
                    )
                }
            }
            else -> {
                val content = remember(selectedPlan.planText) { parseWorkoutPlan(selectedPlan.planText) }
                val goalId = selectedPlan.id
                val goalCompletedExercises = if (userId != null && goalId != null) {
                    WorkoutProgressStore.getCompletedExercises(context, userId, goalId)
                } else emptySet()
                val dayCompletions = if (userId != null && goalId != null) {
                    WorkoutProgressStore.getCompletions(context, userId, goalId)
                } else emptyMap()
                val unlockedDayIndex = if (userId != null && goalId != null) {
                    WorkoutProgressStore.unlockedDayIndex(context, userId, goalId)
                } else 0
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    content.days.forEachIndexed { dayIndex, day ->
                        val dayExercises = remember(day) { day.categories.flatMap { it.exercises } }
                        val isDayDone = dayCompletions.containsKey(dayIndex)
                        val isCurrentDay = dayIndex == unlockedDayIndex
                        val nextExerciseIndex = if (isCurrentDay) {
                            dayExercises.indexOfFirst { it.name !in goalCompletedExercises }
                        } else -1
                        day.heading?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Sage,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isDayDone) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Day completed", tint = Sage, modifier = Modifier.size(16.dp))
                                } else if (isCurrentDay) {
                                    Text("CURRENT", color = Ember, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        dayExercises.forEachIndexed { exerciseIndex, exercise ->
                            val isActive = activeGoalId != null && selectedPlan.id == activeGoalId && exercise.name == activeExerciseName
                            PickableExerciseRow(
                                exercise = exercise,
                                isCompleted = isDayDone || exercise.name in goalCompletedExercises,
                                isActive = isActive,
                                isNextUp = !isActive && exerciseIndex == nextExerciseIndex,
                                onClick = rememberDebounced { selectedPlan.id?.let { onSelect(it, dayIndex, dayExercises, exerciseIndex) } }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/** A boxed card for one generated plan's main goal, e.g. "Thick Back" or "V-Taper Back". */
@Composable
private fun GoalCard(row: WorkoutRow, completedExercises: Set<String>, isActive: Boolean, onClick: () -> Unit) {
    val exercises = remember(row.planText) {
        parseWorkoutPlan(row.planText).days.flatMap { day -> day.categories.flatMap { it.exercises } }
    }
    val doneCount = exercises.count { it.name in completedExercises }
    val allDone = exercises.isNotEmpty() && doneCount == exercises.size

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Surface2,
        shape = RoundedCornerShape(14.dp),
        border = if (isActive) BorderStroke(2.dp, Ember) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.goal.ifBlank { "Untitled Plan" },
                    color = Paper,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (allDone) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = Sage, modifier = Modifier.size(18.dp))
                } else if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("IN PROGRESS", color = Ember, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${row.level} · ${row.daysPerWeek} day${if (row.daysPerWeek == 1) "" else "s"}/week" +
                    if (exercises.isNotEmpty()) " · $doneCount/${exercises.size} done" else "",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PickableExerciseRow(
    exercise: ExerciseLine,
    isCompleted: Boolean,
    isActive: Boolean,
    isNextUp: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Surface2,
        shape = RoundedCornerShape(10.dp),
        border = if (isActive) BorderStroke(2.dp, Ember) else if (isNextUp) BorderStroke(1.dp, Sage) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, color = Paper, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                exercise.detail?.let {
                    Text(it, color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isCompleted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = Sage, modifier = Modifier.size(18.dp))
            } else if (isActive) {
                Text("IN PROGRESS", color = Ember, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            } else if (isNextUp) {
                Text("YOU ARE HERE", color = Sage, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WarmUpScreen(viewModel: ActiveWorkoutViewModel, onNext: () -> Unit, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    // Both the Skip tap and the countdown-reaches-zero effect can independently ask to advance;
    // without this guard both fire and onNext() runs twice, pushing a duplicate screen.
    var hasAdvanced by rememberSaveable { mutableStateOf(false) }
    val advanceOnce: () -> Unit = {
        if (!hasAdvanced) {
            hasAdvanced = true
            onNext()
        }
    }

    LaunchedEffect(Unit) { viewModel.startWarmup() }

    Scaffold(topBar = { PulseTopBar(title = "Warm-up", onBack = onBack) }) { innerPadding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Warm-up", style = MaterialTheme.typography.headlineMedium, color = Paper)
        Spacer(modifier = Modifier.height(16.dp))
        val minutes = uiState.warmupSecondsLeft / 60
        val seconds = uiState.warmupSecondsLeft % 60
        Text(
            "%d:%02d".format(minutes, seconds),
            style = MaterialTheme.typography.headlineMedium,
            color = Sage
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = rememberDebounced { viewModel.skipWarmup(); advanceOnce() },
            colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Paper),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Skip") }

        LaunchedEffect(uiState.warmupSecondsLeft, uiState.isWarmupRunning) {
            if (uiState.warmupSecondsLeft == 0 && !uiState.isWarmupRunning) advanceOnce()
        }
    }
    }
}

@Composable
fun SetTrackingScreen(
    viewModel: ActiveWorkoutViewModel,
    onRest: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(topBar = { PulseTopBar(title = uiState.selectedExerciseName ?: "Log Your Sets", onBack = onBack) }) { innerPadding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(uiState.selectedExerciseName ?: "Log Your Sets", style = MaterialTheme.typography.headlineMedium, color = Paper)
        uiState.selectedExerciseDetail?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        Spacer(modifier = Modifier.height(16.dp))
        val targetSets = remember(uiState.selectedExerciseDetail) {
            parseExerciseDetail(uiState.selectedExerciseDetail).sets?.toIntOrNull()
        }
        Text(
            if (targetSets != null) "${uiState.completedSets}/$targetSets" else "${uiState.completedSets}",
            style = MaterialTheme.typography.headlineMedium,
            color = Ember
        )
        Text("sets completed", style = MaterialTheme.typography.bodyMedium, color = Muted)
        if (targetSets != null && uiState.currentExerciseIndex + 1 < uiState.dayExercises.size) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Next: ${uiState.dayExercises[uiState.currentExerciseIndex + 1].name}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = rememberDebounced { viewModel.logSet(); onRest() },
            colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Log Set & Rest") }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = rememberDebounced(onFinish),
            colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Paper),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Finish Workout") }

        LaunchedEffect(uiState.isDayComplete) {
            if (uiState.isDayComplete) onFinish()
        }
    }
    }
}

@Composable
fun RestTimerScreen(viewModel: ActiveWorkoutViewModel, onContinue: () -> Unit, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    // Leftover state from the previous rest can briefly read (0s, not running) before startRest()'s
    // reset lands, which would otherwise trip the auto-continue check below and bounce back instantly.
    var hasStarted by rememberSaveable { mutableStateOf(false) }
    // Both the Skip Rest tap and the countdown-reaches-zero effect can independently ask to continue;
    // without this guard both fire and onContinue() pops the back stack twice, overshooting past
    // set_tracking (e.g. back into warm_up, or undoing the auto-advance to the workout summary).
    var hasContinued by rememberSaveable { mutableStateOf(false) }
    val continueOnce: () -> Unit = {
        if (!hasContinued) {
            hasContinued = true
            onContinue()
        }
    }

    LaunchedEffect(Unit) { viewModel.startRest() }

    Scaffold(topBar = { PulseTopBar(title = "Rest", onBack = onBack) }) { innerPadding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Rest", style = MaterialTheme.typography.headlineMedium, color = Paper)
        Spacer(modifier = Modifier.height(16.dp))
        Text("${uiState.restSecondsLeft}s", style = MaterialTheme.typography.headlineMedium, color = Sage)
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { viewModel.adjustRestDuration(-15) }) {
                Text("-15s")
            }
            OutlinedButton(onClick = { viewModel.adjustRestDuration(15) }) {
                Text("+15s")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = rememberDebounced { viewModel.skipRest(); continueOnce() },
            colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Paper),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Skip Rest") }

        LaunchedEffect(uiState.restSecondsLeft, uiState.isRestRunning) {
            if (uiState.isRestRunning) hasStarted = true
            if (hasStarted && uiState.restSecondsLeft == 0 && !uiState.isRestRunning) continueOnce()
        }
    }
    }
}

@Composable
fun WorkoutSummaryScreen(
    viewModel: ActiveWorkoutViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onExerciseCompleted: (goalId: Long, names: Set<String>) -> Unit = { _, _ -> },
    onDayCompleted: (goalId: Long, dayIndex: Int) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.finishWorkout()
        val goalId = uiState.selectedGoalId
        if (goalId != null) {
            onExerciseCompleted(goalId, uiState.completedExerciseNames + listOfNotNull(uiState.selectedExerciseName))
        }
        if (uiState.isDayComplete) {
            val dayIndex = uiState.selectedDayIndex
            if (goalId != null && dayIndex != null) onDayCompleted(goalId, dayIndex)
        }
    }

    Scaffold(topBar = { PulseTopBar(title = "Workout Complete", onBack = onBack) }) { innerPadding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Workout Complete!", style = MaterialTheme.typography.headlineMedium, color = Paper)
        Spacer(modifier = Modifier.height(16.dp))
        PulseCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "You completed ${uiState.totalSetsLogged} sets. Nice work.",
                style = MaterialTheme.typography.bodyLarge,
                color = Paper
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = rememberDebounced(onDone),
            colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Done") }
    }
    }
}