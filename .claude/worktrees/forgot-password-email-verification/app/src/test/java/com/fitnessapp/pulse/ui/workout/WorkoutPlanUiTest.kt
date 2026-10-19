package com.fitnessapp.pulse.ui.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutPlanUiTest {

    @Test
    fun `exercise line splits when colon is attached directly to the name`() {
        val line = parseExerciseLine("Barbell Squats: 4 sets of 8-10 reps")
        assertEquals("Barbell Squats", line.name)
        assertEquals("4 sets of 8-10 reps", line.detail)
    }

    @Test
    fun `exercise line splits on a spaced dash`() {
        val line = parseExerciseLine("Walking Lunges - 3 sets of 10-12 reps")
        assertEquals("Walking Lunges", line.name)
        assertEquals("3 sets of 10-12 reps", line.detail)
    }

    @Test
    fun `exercise line without a detail stays whole`() {
        val line = parseExerciseLine("Bulgarian Split Squats")
        assertEquals("Bulgarian Split Squats", line.name)
        assertEquals(null, line.detail)
    }

    @Test
    fun `exercise detail parses inline number-first phrasing`() {
        val stats = parseExerciseDetail("4 sets of 8-10 reps")
        assertEquals("4", stats.sets)
        assertEquals("8-10", stats.reps)
        assertEquals(null, stats.note)
    }

    @Test
    fun `exercise detail parses label-first phrasing`() {
        val stats = parseExerciseDetail("Sets: 4, Reps: 6-8")
        assertEquals("4", stats.sets)
        assertEquals("6-8", stats.reps)
        assertEquals(null, stats.note)
    }

    @Test
    fun `exercise detail keeps leftover text as a note`() {
        val stats = parseExerciseDetail("3 sets of 10-12 reps (each leg)")
        assertEquals("3", stats.sets)
        assertEquals("10-12", stats.reps)
        assertEquals("(each leg)", stats.note)
    }
}
