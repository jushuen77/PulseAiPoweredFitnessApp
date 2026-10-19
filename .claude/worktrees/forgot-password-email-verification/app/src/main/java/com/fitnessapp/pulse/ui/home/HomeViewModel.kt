package com.fitnessapp.pulse.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.data.remote.SupabaseInstance
import com.fitnessapp.pulse.data.remote.model.CheckInRow
import com.fitnessapp.pulse.data.remote.model.MealRow
import com.fitnessapp.pulse.data.remote.model.WorkoutRow
import com.fitnessapp.pulse.data.repository.ProfileRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val username: String? = null,
    val displayName: String? = null,
    val latestWorkout: WorkoutRow? = null,
    val latestMeal: MealRow? = null,
    val latestCheckIn: CheckInRow? = null,
    val isLoading: Boolean = false
)

class HomeViewModel(
    private val profileRepo: ProfileRepository = ProfileRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadDashboard() {
        val userId = SessionManager.userId ?: return
        val username = SessionManager.username

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, username = username) }
            try {
                val profile = profileRepo.getProfile(userId)
                _uiState.update { it.copy(displayName = profile?.displayName?.takeIf { name -> name.isNotBlank() }) }
                val workout = SupabaseInstance.client.from("workout_logs")
                    .select {
                        filter { eq("user_id", userId) }; order(
                        "created_at",
                        Order.DESCENDING
                    ); limit(1)
                    }
                    .decodeList<WorkoutRow>()
                    .firstOrNull()

                val meal = SupabaseInstance.client.from("meal_logs")
                    .select {
                        filter { eq("user_id", userId) }; order(
                        "logged_at",
                        Order.DESCENDING
                    ); limit(1)
                    }
                    .decodeList<MealRow>()
                    .firstOrNull()

                val checkIn = SupabaseInstance.client.from("check_ins")
                    .select {
                        filter { eq("user_id", userId) }; order(
                        "logged_at",
                        Order.DESCENDING
                    ); limit(1)
                    }
                    .decodeList<CheckInRow>()
                    .firstOrNull()

                _uiState.update {
                    it.copy(
                        latestWorkout = workout,
                        latestMeal = meal,
                        latestCheckIn = checkIn,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}