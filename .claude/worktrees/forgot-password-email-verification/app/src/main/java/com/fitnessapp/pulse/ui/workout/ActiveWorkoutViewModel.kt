package com.fitnessapp.pulse.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.data.repository.WorkoutRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActiveWorkoutUiState(
    val warmupSecondsLeft: Int = 300,
    val isWarmupRunning: Boolean = false,
    val completedSets: Int = 0,
    val restDurationSeconds: Int = 60,
    val restSecondsLeft: Int = 60,
    val isRestRunning: Boolean = false,
    val hasSavedSession: Boolean = false,
    val selectedExerciseName: String? = null,
    val selectedExerciseDetail: String? = null,
    /** The goal/plan (WorkoutRow.id) the active exercise was picked from, so "in progress" only marks that one goal. */
    val selectedGoalId: Long? = null,
    /** Index into the plan's day sections the active exercise was picked from, for date-based day-completion tracking. */
    val selectedDayIndex: Int? = null,
    /** The full ordered exercise list for the day the current exercise was picked from. */
    val dayExercises: List<ExerciseLine> = emptyList(),
    val currentExerciseIndex: Int = 0,
    /** Exercises that reached their target set count during this session, across the whole day's flow. */
    val completedExerciseNames: Set<String> = emptySet(),
    /** True once the last exercise in dayExercises has hit its target set count, so the screen can move on automatically. */
    val isDayComplete: Boolean = false
)

private const val MIN_REST_SECONDS = 15
private const val MAX_REST_SECONDS = 300

class ActiveWorkoutViewModel(
    private val repo: WorkoutRepository = WorkoutRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(ActiveWorkoutUiState())
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    fun startWarmup() {
        if (_uiState.value.isWarmupRunning) return
        _uiState.update { it.copy(isWarmupRunning = true) }
        viewModelScope.launch {
            while (_uiState.value.warmupSecondsLeft > 0 && _uiState.value.isWarmupRunning) {
                delay(1000)
                _uiState.update { it.copy(warmupSecondsLeft = (it.warmupSecondsLeft - 1).coerceAtLeast(0)) }
            }
            _uiState.update { it.copy(isWarmupRunning = false) }
        }
    }

    fun skipWarmup() = _uiState.update { it.copy(warmupSecondsLeft = 0, isWarmupRunning = false) }

    internal fun selectExercise(goalId: Long, dayIndex: Int, dayExercises: List<ExerciseLine>, startIndex: Int) {
        val exercise = dayExercises.getOrNull(startIndex) ?: return
        _uiState.update {
            it.copy(
                dayExercises = dayExercises,
                currentExerciseIndex = startIndex,
                selectedExerciseName = exercise.name,
                selectedExerciseDetail = exercise.detail,
                selectedGoalId = goalId,
                selectedDayIndex = dayIndex,
                completedSets = 0,
                isDayComplete = false,
                // A fresh pick starts a new round within this same active-workout flow (e.g. after
                // "Done" returns to the goal's exercise list), so it should be saved to history on
                // its own completion too, not skipped because a prior round already saved once.
                hasSavedSession = false
            )
        }
    }

    /**
     * Logs a set. Once the current exercise's target set count (parsed from its plan detail, e.g.
     * "4 sets x 8 reps") is reached, it's marked completed and the flow advances to the next
     * exercise in the day automatically. If that was the last exercise in the day, isDayComplete
     * flips true so the screen can move on to the workout summary without a manual "Finish" tap;
     * with no target, it just keeps counting since there's nothing to compare against.
     */
    fun logSet() {
        _uiState.update { current ->
            val newCompleted = current.completedSets + 1
            val targetSets = parseExerciseDetail(current.selectedExerciseDetail).sets?.toIntOrNull()
            if (targetSets == null || newCompleted < targetSets) {
                return@update current.copy(completedSets = newCompleted)
            }

            val completedNames = current.selectedExerciseName?.let { current.completedExerciseNames + it }
                ?: current.completedExerciseNames
            val nextIndex = current.currentExerciseIndex + 1
            val next = current.dayExercises.getOrNull(nextIndex)
            if (next == null) {
                current.copy(completedSets = newCompleted, completedExerciseNames = completedNames, isDayComplete = true)
            } else {
                current.copy(
                    completedSets = 0,
                    currentExerciseIndex = nextIndex,
                    selectedExerciseName = next.name,
                    selectedExerciseDetail = next.detail,
                    completedExerciseNames = completedNames
                )
            }
        }
    }

    fun startRest() {
        if (_uiState.value.isRestRunning) return
        _uiState.update { it.copy(restSecondsLeft = it.restDurationSeconds, isRestRunning = true) }
        viewModelScope.launch {
            while (_uiState.value.restSecondsLeft > 0 && _uiState.value.isRestRunning) {
                delay(1000)
                _uiState.update { it.copy(restSecondsLeft = (it.restSecondsLeft - 1).coerceAtLeast(0)) }
            }
            _uiState.update { it.copy(isRestRunning = false) }
        }
    }

    fun skipRest() = _uiState.update { it.copy(restSecondsLeft = 0, isRestRunning = false) }

    /** Adjusts both the running countdown and the default used for the next rest, clamped to a sane range. */
    fun adjustRestDuration(deltaSeconds: Int) {
        _uiState.update {
            val newDuration = (it.restDurationSeconds + deltaSeconds).coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS)
            val newRemaining = if (it.isRestRunning) {
                (it.restSecondsLeft + deltaSeconds).coerceIn(0, MAX_REST_SECONDS)
            } else {
                it.restSecondsLeft
            }
            it.copy(restDurationSeconds = newDuration, restSecondsLeft = newRemaining)
        }
    }

    /** Persists the completed session to workout history exactly once per active-workout flow. */
    fun finishWorkout() {
        if (_uiState.value.hasSavedSession) return
        val userId = SessionManager.userId ?: return
        _uiState.update { it.copy(hasSavedSession = true) }
        viewModelScope.launch {
            try {
                repo.saveCompletedWorkout(userId, _uiState.value.completedSets)
            } catch (_: Exception) {
                _uiState.update { it.copy(hasSavedSession = false) }
            }
        }
    }
}