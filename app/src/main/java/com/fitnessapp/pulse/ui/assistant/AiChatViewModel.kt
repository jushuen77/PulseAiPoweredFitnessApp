package com.fitnessapp.pulse.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.data.remote.ChatMessage
import com.fitnessapp.pulse.data.remote.ChatRequest
import com.fitnessapp.pulse.data.remote.FunctionDefinition
import com.fitnessapp.pulse.data.remote.RetrofitInstance
import com.fitnessapp.pulse.data.remote.SupabaseInstance
import com.fitnessapp.pulse.data.remote.ToolDefinition
import com.fitnessapp.pulse.data.remote.model.CheckInRow
import com.fitnessapp.pulse.data.remote.model.ChatMessageRow
import com.fitnessapp.pulse.data.repository.MacroRepository
import com.fitnessapp.pulse.data.repository.ProfileRepository
import com.fitnessapp.pulse.data.repository.WorkoutRepository
import com.fitnessapp.pulse.ui.components.formatHistoryDate
import com.google.gson.Gson
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiMessage(val role: String, val text: String)

data class AiChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** A destination key the assistant just asked to navigate to; the screen consumes and clears this. */
    val pendingNavigation: String? = null,
    /** For destination == "active_workout_flow": which goal to jump straight into, if the assistant knows it. */
    val pendingGoal: String? = null
)

/** Route keys the assistant is allowed to navigate to; kept in sync with the handling in PulseNavGraph. */
private val NAV_DESTINATIONS = setOf(
    "home", "workout_graph", "active_workout_flow", "workout_history",
    "macros", "manual_entry", "meal_history",
    "mind", "check_in_history",
    "profile", "edit_profile", "settings", "profile_history", "qna", "terms"
)

private val NAVIGATE_APP_TOOL = ToolDefinition(
    function = FunctionDefinition(
        name = "navigate_app",
        description = "Navigate the user's app to a specific screen, or start an activity there. Call this " +
            "whenever the user asks to go to, open, view, or start something in the app (e.g. \"take me to my " +
            "workout plan\", \"start my workout\", \"show my meal history\", \"open settings\"). Valid " +
            "destination values: 'home' (dashboard with quick links and recent activity), 'workout_graph' " +
            "(workout planner - generate or view workout plans), 'active_workout_flow' (begin or resume an " +
            "active workout session - use this for \"start my workout\" or \"start the active workout\"), " +
            "'workout_history' (list of past generated workout plans), 'macros' (photo-based meal macro " +
            "analyzer, the Macros tab home), 'manual_entry' (describe a meal in text to estimate its macros), " +
            "'meal_history' (list of past logged meals), 'mind' (Wellness tab home / start a PHQ-9 and DASS-21 " +
            "check-in), 'check_in_history' (list of past mental health check-ins), 'profile' (profile home), " +
            "'edit_profile' (edit display name), 'settings' (app settings), 'profile_history' (a hub linking " +
            "to workout/meal/check-in history), 'qna' (frequently asked questions), 'terms' (terms and privacy). " +
            "When destination is 'active_workout_flow': if it's not already obvious which workout goal/plan the " +
            "user wants (they may have more than one), ask them which one first instead of guessing. Once you " +
            "know, also pass the 'goal' parameter set to that plan's exact name from the WORKOUT PLANS section " +
            "above, so the app jumps straight into that goal's exercise list instead of showing the goal picker.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "destination" to mapOf(
                    "type" to "string",
                    "enum" to NAV_DESTINATIONS.toList()
                ),
                "goal" to mapOf(
                    "type" to "string",
                    "description" to "Only used when destination is 'active_workout_flow'. The exact goal/plan " +
                        "name to jump directly into, matching one of the user's own goal names."
                )
            ),
            "required" to listOf("destination")
        )
    )
)

private data class NavigateArgs(val destination: String?, val goal: String? = null)

private const val BASE_SYSTEM_PROMPT =
    "You are a helpful, friendly assistant built into the Pulse fitness app. " +
        "Answer clearly and concisely. You can help with fitness, nutrition, and wellness questions, " +
        "but you're also happy to help with general everyday questions unrelated to the app.\n\n" +
        "You can also act inside the app, not just talk about it: use the navigate_app tool whenever the " +
        "user asks to go to, open, view, or start a specific screen or feature (e.g. \"start my workout\", " +
        "\"take me to my meal history\", \"open settings\"). After the tool runs, confirm briefly what you " +
        "did in the same natural, conversational tone.\n\n" +
        "Below is this user's own data pulled live from the app: every workout plan with its full " +
        "day-by-day exercise breakdown (sets, reps, and movements), every completed workout session, " +
        "every logged meal, and every mental health check-in. Use it to give specific, personalized " +
        "answers when relevant (e.g. \"what's my workout schedule\", \"what exercises are in my plan\", " +
        "\"what did I eat today\", \"how's my mood trend\"). The plans don't map to actual calendar dates, " +
        "only to their own \"Day 1\", \"Day 2\" labels, so if asked about \"today's\" workout, answer using " +
        "those day labels rather than guessing which one falls on today. " +
        "Don't say the data was 'provided' to you — just answer naturally as if you already knew it. " +
        "If something isn't covered by the data below, say you don't have that information rather than guessing."

class AiChatViewModel(
    private val workoutRepo: WorkoutRepository = WorkoutRepository(),
    private val macroRepo: MacroRepository = MacroRepository(),
    private val profileRepo: ProfileRepository = ProfileRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    /** Cached for the life of this ViewModel so every message doesn't re-fetch the user's data. */
    private var appContext: String? = null

    /** Groups this screen instance's messages into one browsable past session (see ChatHistoryScreen).
     *  Starts as a fresh conversation; [resumeSession] switches it to continue an existing one instead. */
    private var sessionId = UUID.randomUUID().toString()
    private var resumedSessionId: String? = null

    fun onInputChange(value: String) = _uiState.update { it.copy(inputText = value, errorMessage = null) }

    /** Loads a past session's messages and continues saving new ones under that same session. */
    fun resumeSession(id: String) {
        if (resumedSessionId == id) return
        resumedSessionId = id
        sessionId = id
        val userId = SessionManager.userId ?: return
        viewModelScope.launch {
            val rows = runCatching {
                SupabaseInstance.client.from("chat_messages")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("session_id", id)
                        }
                        order("created_at", Order.ASCENDING)
                    }
                    .decodeList<ChatMessageRow>()
            }.getOrNull() ?: return@launch
            _uiState.update { it.copy(messages = rows.map { row -> ChatUiMessage(role = row.role, text = row.content) }) }
        }
    }

    /** Deletes every message in the current session, then calls [onDeleted]. */
    fun deleteSession(onDeleted: () -> Unit) {
        val userId = SessionManager.userId ?: return
        val id = sessionId
        viewModelScope.launch {
            val deleted = runCatching {
                SupabaseInstance.client.from("chat_messages")
                    .delete {
                        filter {
                            eq("user_id", userId)
                            eq("session_id", id)
                        }
                    }
            }.isSuccess
            if (deleted) onDeleted()
        }
    }

    private fun saveMessage(role: String, text: String) {
        val userId = SessionManager.userId ?: return
        viewModelScope.launch {
            runCatching {
                SupabaseInstance.client.from("chat_messages")
                    .insert(ChatMessageRow(userId = userId, sessionId = sessionId, role = role, content = text))
            }
        }
    }

    /** Called by the screen once it has acted on [AiChatUiState.pendingNavigation]. */
    fun clearPendingNavigation() = _uiState.update { it.copy(pendingNavigation = null, pendingGoal = null) }

    private fun parseNavigateArgs(argumentsJson: String): NavigateArgs? =
        runCatching { Gson().fromJson(argumentsJson, NavigateArgs::class.java) }.getOrNull()

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isLoading) return

        val userMessage = ChatUiMessage(role = "user", text = text)
        _uiState.update {
            it.copy(messages = it.messages + userMessage, inputText = "", isLoading = true, errorMessage = null)
        }
        saveMessage("user", text)

        viewModelScope.launch {
            try {
                val systemPrompt = ChatUiMessage(role = "system", text = BASE_SYSTEM_PROMPT + "\n\n" + loadAppContext())
                val chatHistory = (listOf(systemPrompt) + _uiState.value.messages)
                    .map { msg -> ChatMessage(role = msg.role, content = msg.text) }
                val request = ChatRequest(messages = chatHistory, tools = listOf(NAVIGATE_APP_TOOL))
                val response = RetrofitInstance.api.getChatCompletion(request)
                val message = response.choices.first().message
                val toolCalls = message.tool_calls

                if (toolCalls.isNullOrEmpty()) {
                    val reply = message.content ?: ""
                    _uiState.update {
                        it.copy(messages = it.messages + ChatUiMessage(role = "assistant", text = reply), isLoading = false)
                    }
                    saveMessage("assistant", reply)
                } else {
                    // The model asked to navigate: execute it, tell the model what happened, then ask it
                    // for the natural-language reply that acknowledges the action.
                    var destination: String? = null
                    var goal: String? = null
                    val toolResults = toolCalls.map { call ->
                        val args = parseNavigateArgs(call.function.arguments)
                        val dest = args?.destination?.takeIf { it in NAV_DESTINATIONS }
                        if (dest != null) {
                            destination = dest
                            goal = args.goal?.takeIf { it.isNotBlank() }
                        }
                        ChatMessage(
                            role = "tool",
                            content = when {
                                dest == null -> "Unknown destination; no navigation performed."
                                goal != null -> "Navigated to \"$dest\" and jumped into goal \"$goal\"."
                                else -> "Navigated to \"$dest\"."
                            },
                            tool_call_id = call.id
                        )
                    }
                    val assistantToolMessage = ChatMessage(role = "assistant", content = null, tool_calls = toolCalls)
                    val followUpRequest = ChatRequest(
                        messages = chatHistory + assistantToolMessage + toolResults,
                        tools = listOf(NAVIGATE_APP_TOOL)
                    )
                    val followUpResponse = RetrofitInstance.api.getChatCompletion(followUpRequest)
                    val finalReply = followUpResponse.choices.first().message.content ?: "Done."
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatUiMessage(role = "assistant", text = finalReply),
                            isLoading = false,
                            pendingNavigation = destination,
                            pendingGoal = goal
                        )
                    }
                    saveMessage("assistant", finalReply)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to get a response") }
            }
        }
    }

    /** Pulls the signed-in user's workout plans, meals, and check-ins into a plain-text summary for the prompt. */
    private suspend fun loadAppContext(): String {
        appContext?.let { return it }
        val userId = SessionManager.userId
            ?: return "No signed-in user, so no app data is available."

        val built = buildString {
            runCatching { profileRepo.getProfile(userId) }.getOrNull()?.let { profile ->
                if (profile.displayName.isNotBlank()) appendLine("USER NAME: ${profile.displayName}")
            }

            runCatching { workoutRepo.getWorkoutHistory(userId) }.getOrNull()?.let { rows ->
                val plans = rows.filterNot { it.goal == WorkoutRepository.COMPLETED_SESSION_GOAL }
                if (plans.isNotEmpty()) {
                    appendLine()
                    appendLine("WORKOUT PLANS / SCHEDULE (full exercise breakdown, including every day's movements):")
                    plans.forEach { plan ->
                        appendLine("--- Goal: ${plan.goal} (${plan.level}, ${plan.daysPerWeek} days/week) ---")
                        appendLine(plan.planText)
                        appendLine()
                    }
                }
                val completed = rows.filter { it.goal == WorkoutRepository.COMPLETED_SESSION_GOAL }
                if (completed.isNotEmpty()) {
                    appendLine()
                    appendLine("ALL COMPLETED WORKOUT SESSIONS:")
                    completed.forEach { appendLine("- ${formatHistoryDate(it.createdAt)}: ${it.planText}") }
                }
            }

            runCatching { macroRepo.getMealHistory(userId) }.getOrNull()?.let { meals ->
                if (meals.isNotEmpty()) {
                    appendLine()
                    appendLine("ALL LOGGED MEALS:")
                    meals.forEach {
                        appendLine(
                            "- ${formatHistoryDate(it.loggedAt)}: ${it.foodName} " +
                                "(${it.calories} cal, ${it.proteinG}g protein, ${it.carbsG}g carbs, ${it.fatG}g fat)"
                        )
                    }
                }
            }

            runCatching {
                SupabaseInstance.client.from("check_ins")
                    .select {
                        filter { eq("user_id", userId) }
                        order("logged_at", Order.DESCENDING)
                    }
                    .decodeList<CheckInRow>()
            }.getOrNull()?.let { checkIns ->
                if (checkIns.isNotEmpty()) {
                    appendLine()
                    appendLine("ALL MENTAL HEALTH CHECK-INS:")
                    checkIns.forEach {
                        appendLine(
                            "- ${formatHistoryDate(it.loggedAt)}: PHQ-9 ${it.phq9Score} (${it.phq9Severity}), " +
                                "Depression ${it.dassDepressionScore}, Anxiety ${it.dassAnxietyScore}, Stress ${it.dassStressScore}. " +
                                "Reflection: ${it.aiReflection}"
                        )
                    }
                }
            }
        }.trim().ifBlank { "No app data recorded yet for this user." }

        appContext = built
        return built
    }
}
