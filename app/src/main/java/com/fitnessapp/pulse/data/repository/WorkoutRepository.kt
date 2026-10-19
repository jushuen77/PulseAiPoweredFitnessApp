package com.fitnessapp.pulse.data.repository

import com.fitnessapp.pulse.data.remote.ChatMessage
import com.fitnessapp.pulse.data.remote.ChatRequest
import com.fitnessapp.pulse.data.remote.RetrofitInstance
import com.fitnessapp.pulse.data.remote.SupabaseInstance
import com.fitnessapp.pulse.data.remote.model.WorkoutRow
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class WorkoutRepository {

    companion object {
        /** Sentinel [WorkoutRow.goal] marking a logged completed session rather than a generated plan. */
        const val COMPLETED_SESSION_GOAL = "Active Workout Session"
    }

    suspend fun getWorkoutHistory(userId: String): List<WorkoutRow> {
        return SupabaseInstance.client.from("workout_logs")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<WorkoutRow>()
    }

    suspend fun generatePlan(
        goal: String,
        level: String,
        daysPerWeek: Int,
        equipment: String,
        userId: String
    ): String {
        val systemPrompt = """
            You are a certified fitness coach. Generate a structured weekly workout plan.
            Return it as clean, numbered days with exercises, sets and reps.
            Keep it realistic and safe for the stated fitness level.
            Do not give medical advice; suggest consulting a doctor for injuries.
        """.trimIndent()

        val userPrompt =
            "Goal: $goal. Level: $level. Days available: $daysPerWeek. Equipment: $equipment."

        val request = ChatRequest(
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            )
        )

        val response = RetrofitInstance.api.getChatCompletion(request)
        val planText = response.choices.first().message.content ?: ""

        SupabaseInstance.client.from("workout_logs").insert(
            WorkoutRow(
                userId = userId,
                goal = goal,
                level = level,
                daysPerWeek = daysPerWeek,
                planText = planText
            )
        )

        return planText
    }
    suspend fun getWorkoutById(id: Long): WorkoutRow? {
        return SupabaseInstance.client.from("workout_logs")
            .select { filter { eq("id", id) } }
            .decodeList<WorkoutRow>()
            .firstOrNull()
    }

    suspend fun deleteWorkout(id: Long) {
        SupabaseInstance.client.from("workout_logs")
            .delete { filter { eq("id", id) } }
    }

    suspend fun saveCompletedWorkout(userId: String, completedSets: Int) {
        SupabaseInstance.client.from("workout_logs").insert(
            WorkoutRow(
                userId = userId,
                goal = COMPLETED_SESSION_GOAL,
                level = "-",
                daysPerWeek = 0,
                planText = "Completed $completedSets set${if (completedSets == 1) "" else "s"} during an active workout session."
            )
        )
    }
}