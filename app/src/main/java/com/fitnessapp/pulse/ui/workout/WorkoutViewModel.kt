package com.fitnessapp.pulse.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.data.remote.model.WorkoutRow
import com.fitnessapp.pulse.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutUiState(
    val goal: String = "",
    val level: String = "Beginner",
    val daysPerWeek: Int = 3,
    val equipment: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val generatedPlan: String? = null,
    val history: List<WorkoutRow> = emptyList(),
    val isHistoryLoading: Boolean = false,
    val selectedWorkout: WorkoutRow? = null,
    val isDetailLoading: Boolean = false
)

class WorkoutViewModel(
    private val repo: WorkoutRepository = WorkoutRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    fun onGoalChange(value: String) = _uiState.update { it.copy(goal = value) }
    fun onLevelChange(value: String) = _uiState.update { it.copy(level = value) }
    fun onDaysChange(value: Int) = _uiState.update { it.copy(daysPerWeek = value) }
    fun onEquipmentChange(value: String) = _uiState.update { it.copy(equipment = value) }

    fun generatePlan() {
        val userId = SessionManager.userId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val plan = repo.generatePlan(
                    goal = _uiState.value.goal,
                    level = _uiState.value.level,
                    daysPerWeek = _uiState.value.daysPerWeek,
                    equipment = _uiState.value.equipment,
                    userId = userId
                )
                _uiState.update { it.copy(isLoading = false, generatedPlan = plan) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to generate plan"
                    )
                }
            }
        }
    }

    fun loadHistory() {
        val userId = SessionManager.userId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isHistoryLoading = true) }
            try {
                val history = repo.getWorkoutHistory(userId)
                _uiState.update { it.copy(history = history, isHistoryLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isHistoryLoading = false) }
            }
        }
    }

    fun loadWorkoutDetail(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true) }
            try {
                val workout = repo.getWorkoutById(id)
                _uiState.update { it.copy(selectedWorkout = workout, isDetailLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDetailLoading = false) }
            }
        }
    }

    fun deleteWorkout(onDeleted: () -> Unit) {
        val id = _uiState.value.selectedWorkout?.id ?: return
        viewModelScope.launch {
            try {
                repo.deleteWorkout(id)
                _uiState.update {
                    it.copy(
                        selectedWorkout = null,
                        history = it.history.filterNot { w -> w.id == id }
                    )
                }
                onDeleted()
            } catch (_: Exception) { }
        }
    }
}