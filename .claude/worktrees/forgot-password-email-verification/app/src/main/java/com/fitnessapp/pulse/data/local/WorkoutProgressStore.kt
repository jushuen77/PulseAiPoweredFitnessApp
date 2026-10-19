package com.fitnessapp.pulse.data.local

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
private data class DayCompletion(val dayIndex: Int, val completedDate: String)

/**
 * Persists which plan days a user has finished and on what real-world date, so a day's "done"
 * mark survives process death and the next day in the plan only becomes selectable once an
 * actual calendar day has passed since the previous one was completed.
 */
object WorkoutProgressStore {
    private const val PREFS_NAME = "workout_progress_prefs"

    private fun key(userId: String, goalId: Long) = "progress_${userId}_$goalId"
    private fun exercisesKey(userId: String, goalId: Long) = "exercises_${userId}_$goalId"

    /** Exercise names completed for [goalId], persisted permanently instead of only for the current process. */
    fun getCompletedExercises(context: Context, userId: String, goalId: Long): Set<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(exercisesKey(userId, goalId), null) ?: return emptySet()
        return try {
            Json.decodeFromString(raw)
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** Adds [names] to the permanently completed exercises for [goalId]; a no-op if [names] is empty. */
    fun markExercisesCompleted(context: Context, userId: String, goalId: Long, names: Set<String>) {
        if (names.isEmpty()) return
        val updated = getCompletedExercises(context, userId, goalId) + names
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(exercisesKey(userId, goalId), Json.encodeToString(updated))
            .apply()
    }

    private fun readAll(context: Context, userId: String, goalId: Long): List<DayCompletion> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(userId, goalId), null) ?: return emptyList()
        return try {
            Json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Completed day indices for [goalId], mapped to the calendar date each was finished on. */
    fun getCompletions(context: Context, userId: String, goalId: Long): Map<Int, LocalDate> =
        readAll(context, userId, goalId).associate { it.dayIndex to LocalDate.parse(it.completedDate) }

    /** Marks [dayIndex] of [goalId] as completed on [date]; a no-op if it was already recorded. */
    fun markDayCompleted(context: Context, userId: String, goalId: Long, dayIndex: Int, date: LocalDate = LocalDate.now()) {
        val existing = readAll(context, userId, goalId)
        if (existing.any { it.dayIndex == dayIndex }) return
        val updated = existing + DayCompletion(dayIndex, date.toString())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(key(userId, goalId), Json.encodeToString(updated))
            .apply()
    }

    /**
     * The index of the day currently open for [goalId]: the first day either not yet completed,
     * or completed on [today] itself. A day completed on an earlier date rolls the index forward,
     * so the next day only opens once a new real calendar day has actually arrived.
     */
    fun unlockedDayIndex(context: Context, userId: String, goalId: Long, today: LocalDate = LocalDate.now()): Int {
        val completions = getCompletions(context, userId, goalId)
        var index = 0
        while (true) {
            val completedDate = completions[index] ?: return index
            index = if (completedDate.isBefore(today)) index + 1 else return index
        }
    }
}
