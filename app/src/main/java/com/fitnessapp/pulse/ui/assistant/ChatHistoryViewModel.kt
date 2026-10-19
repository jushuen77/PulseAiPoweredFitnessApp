package com.fitnessapp.pulse.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.data.remote.SupabaseInstance
import com.fitnessapp.pulse.data.remote.model.ChatMessageRow
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatSessionSummary(val sessionId: String, val title: String, val messageCount: Int, val lastMessageAt: String?)

data class ChatHistoryUiState(
    val sessions: List<ChatSessionSummary> = emptyList(),
    val isSessionsLoading: Boolean = false
)

/** Backs the past-sessions list; opening one hands its id to AiChatScreen to resume it live. */
class ChatHistoryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatHistoryUiState())
    val uiState: StateFlow<ChatHistoryUiState> = _uiState.asStateFlow()

    fun loadSessions() {
        val userId = SessionManager.userId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSessionsLoading = true) }
            val rows = runCatching {
                SupabaseInstance.client.from("chat_messages")
                    .select {
                        filter { eq("user_id", userId) }
                        order("created_at", Order.ASCENDING)
                    }
                    .decodeList<ChatMessageRow>()
            }.getOrNull()

            val sessions = rows
                ?.groupBy { it.sessionId }
                ?.map { (sessionId, messages) ->
                    val title = messages.firstOrNull { it.role == "user" }?.content
                        ?: messages.first().content
                    ChatSessionSummary(
                        sessionId = sessionId,
                        title = title,
                        messageCount = messages.size,
                        lastMessageAt = messages.last().createdAt
                    )
                }
                ?.sortedByDescending { it.lastMessageAt }
                ?: emptyList()

            _uiState.update { it.copy(sessions = sessions, isSessionsLoading = false) }
        }
    }
}
