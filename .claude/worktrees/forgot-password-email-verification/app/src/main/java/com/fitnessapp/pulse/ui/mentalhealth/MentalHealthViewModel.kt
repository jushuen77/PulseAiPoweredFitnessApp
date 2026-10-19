package com.fitnessapp.pulse.ui.mentalhealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.model.dassAnxietyItems
import com.fitnessapp.pulse.data.model.dassAnxietySeverity
import com.fitnessapp.pulse.data.model.dassDepressionItems
import com.fitnessapp.pulse.data.model.dassDepressionSeverity
import com.fitnessapp.pulse.data.model.dassStressItems
import com.fitnessapp.pulse.data.model.dassStressSeverity
import com.fitnessapp.pulse.data.model.phq9Severity
import com.fitnessapp.pulse.data.remote.ChatMessage
import com.fitnessapp.pulse.data.remote.ChatRequest
import com.fitnessapp.pulse.data.remote.RetrofitInstance
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.data.remote.SupabaseInstance
import com.fitnessapp.pulse.data.remote.model.CheckInRow
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CheckInStage { INTRO, PHQ9, DASS21, RESULT }

data class MentalHealthUiState(
    val stage: CheckInStage = CheckInStage.INTRO,
    val phq9Answers: Map<Int, Int> = emptyMap(),
    val dassAnswers: Map<Int, Int> = emptyMap(),
    val showCrisisResources: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val reflection: String? = null,
    val phq9Total: Int = 0,
    val dassDepressionTotal: Int = 0,
    val dassAnxietyTotal: Int = 0,
    val dassStressTotal: Int = 0,
    val history: List<CheckInRow> = emptyList(),
    val isHistoryLoading: Boolean = false
)

class MentalHealthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MentalHealthUiState())
    val uiState: StateFlow<MentalHealthUiState> = _uiState.asStateFlow()

    fun startCheckIn() = _uiState.update { it.copy(stage = CheckInStage.PHQ9) }

    fun answerPhq9(itemId: Int, value: Int) {
        _uiState.update {
            val updated = it.phq9Answers + (itemId to value)
            val crisisTriggered = (updated[9] ?: 0) > 0
            it.copy(phq9Answers = updated, showCrisisResources = crisisTriggered)
        }
    }

    fun answerDass(itemId: Int, value: Int) {
        _uiState.update { it.copy(dassAnswers = it.dassAnswers + (itemId to value)) }
    }

    fun proceedToDass() = _uiState.update { it.copy(stage = CheckInStage.DASS21) }

    /** Returns to the PHQ-9 page without losing any answers on either page, so the user can edit them. */
    fun backToPhq9() = _uiState.update { it.copy(stage = CheckInStage.PHQ9) }

    fun submitCheckIn() {
        val userId = SessionManager.userId ?: return
        val state = _uiState.value

        val phq9Total = state.phq9Answers.values.sum()
        val depressionTotal = dassDepressionItems.sumOf { state.dassAnswers[it.id] ?: 0 } * 2
        val anxietyTotal = dassAnxietyItems.sumOf { state.dassAnswers[it.id] ?: 0 } * 2
        val stressTotal = dassStressItems.sumOf { state.dassAnswers[it.id] ?: 0 } * 2

        _uiState.update {
            it.copy(
                phq9Total = phq9Total,
                dassDepressionTotal = depressionTotal,
                dassAnxietyTotal = anxietyTotal,
                dassStressTotal = stressTotal,
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val prompt = """
                    You are a supportive wellness assistant, not a therapist or diagnostician.
                    A user completed a PHQ-9 and DASS-21 self-report screening.
                    PHQ-9 total: $phq9Total (${phq9Severity(phq9Total)})
                    DASS-21 Depression: $depressionTotal (${dassDepressionSeverity(depressionTotal)})
                    DASS-21 Anxiety: $anxietyTotal (${dassAnxietySeverity(anxietyTotal)})
                    DASS-21 Stress: $stressTotal (${dassStressSeverity(stressTotal)})
                    Write a short (3-4 sentence), warm, non-alarming reflection.
                    Do NOT diagnose. Encourage professional follow-up if scores suggest moderate or higher severity.
                    Suggest one gentle, practical next step (e.g. talking to someone, a short walk, journaling).
                """.trimIndent()

                val request = ChatRequest(
                    messages = listOf(ChatMessage(role = "system", content = prompt))
                )
                val response = RetrofitInstance.api.getChatCompletion(request)
                val reflection = response.choices.first().message.content ?: ""

                SupabaseInstance.client.from("check_ins").insert(
                    CheckInRow(
                        userId = userId,
                        phq9Score = phq9Total,
                        phq9Severity = phq9Severity(phq9Total),
                        dassDepressionScore = depressionTotal,
                        dassAnxietyScore = anxietyTotal,
                        dassStressScore = stressTotal,
                        aiReflection = reflection,
                        phq9Answers = state.phq9Answers.mapKeys { it.key.toString() },
                        dassAnswers = state.dassAnswers.mapKeys { it.key.toString() }
                    )
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        reflection = reflection,
                        stage = CheckInStage.RESULT
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to get reflection"
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
                val history = SupabaseInstance.client.from("check_ins")
                    .select {
                        filter { eq("user_id", userId) }
                        order("logged_at", Order.DESCENDING)
                    }
                    .decodeList<CheckInRow>()
                _uiState.update { it.copy(history = history, isHistoryLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isHistoryLoading = false) }
            }
        }
    }

    fun resetCheckIn() = _uiState.update { MentalHealthUiState() }
}