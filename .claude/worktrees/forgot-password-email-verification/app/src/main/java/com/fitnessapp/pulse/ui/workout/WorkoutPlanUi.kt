package com.fitnessapp.pulse.ui.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnessapp.pulse.ui.components.IconChip
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.EmberDim
import com.fitnessapp.pulse.ui.theme.Ink
import com.fitnessapp.pulse.ui.theme.Line
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper
import com.fitnessapp.pulse.ui.theme.Sage
import com.fitnessapp.pulse.ui.theme.Surface2

internal data class WorkoutPlanContent(val planTitle: String?, val overview: String?, val days: List<PlanSection>)
internal data class PlanSection(val heading: String?, val categories: List<ExerciseCategory>)
internal data class ExerciseCategory(val heading: String?, val exercises: List<ExerciseLine>)
data class ExerciseLine(val name: String, val detail: String?)
internal data class ExerciseStats(val sets: String?, val reps: String?, val note: String?)

private val dayHeadingRegex = Regex("^day\\s*\\d+", RegexOption.IGNORE_CASE)
private val dividerRegex = Regex("^[-=*]{2,}$")
private val bulletPrefixRegex = Regex("^([-*#]+|\\d+[.)])\\s*")
private val categoryNameRegex = Regex(
    "^(warm[\\s-]?ups?|cool[\\s-]?downs?|exercises?|main\\s+(workout|sets?)|workout|cardio|stretch(ing)?|finisher|core|abs|circuit|superset)$",
    RegexOption.IGNORE_CASE
)
/** A line like "Sets: 4" or "Reps: 8-10" that belongs to the exercise named on the line above it. */
private val continuationRegex = Regex(
    "^(sets?|reps?|rest|tempo|weight|duration|distance|time)\\s*:",
    RegexOption.IGNORE_CASE
)
/** A category heading like "Notes" whose lines are independent tips, not exercise attributes to merge. */
private val freeformSectionRegex = Regex("^(notes?|tips?|guidelines?)$", RegexOption.IGNORE_CASE)

/** A short standalone line like "Weekly Workout Plan" — a title, not a sentence of the overview. */
private fun looksLikeTitleLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.any { it in ".,:;" }) return false
    val words = trimmed.split(Regex("\\s+"))
    if (words.size !in 1..5) return false
    return words.last().trimEnd('!').equals("plan", ignoreCase = true)
}

/** Splits the AI's freeform plan text into an optional title/overview and "Day N" sections. */
internal fun parseWorkoutPlan(plan: String): WorkoutPlanContent {
    val introLines = mutableListOf<String>()
    val days = mutableListOf<PlanSection>()
    var dayHeading: String? = null
    var dayLines = mutableListOf<String>()
    var sawDay = false

    fun flushDay() {
        dayHeading?.let { days += PlanSection(it, groupCategories(dayLines)) }
    }

    plan.lines().forEach { raw ->
        val trimmedRaw = raw.trim()
        if (trimmedRaw.isEmpty() || dividerRegex.matches(trimmedRaw)) return@forEach
        val line = trimmedRaw.replace("**", "").replace(bulletPrefixRegex, "").trim()
        if (line.isEmpty()) return@forEach
        if (dayHeadingRegex.containsMatchIn(line)) {
            flushDay()
            sawDay = true
            dayHeading = line.trimEnd(':')
            dayLines = mutableListOf()
        } else if (!sawDay) {
            introLines += line
        } else {
            dayLines += line
        }
    }
    flushDay()

    val titleIndex = introLines.indexOfLast(::looksLikeTitleLine)
    val planTitle = titleIndex.takeIf { it >= 0 }?.let { introLines[it] }
    val overview = introLines.filterIndexed { i, _ -> i != titleIndex }.joinToString(" ").ifBlank { null }
    return WorkoutPlanContent(planTitle = planTitle, overview = overview, days = days)
}

/** Groups a day's raw lines into categories (e.g. Warm-up, Exercises), each holding its own exercises. */
private fun groupCategories(lines: List<String>): List<ExerciseCategory> {
    val categories = mutableListOf<ExerciseCategory>()
    var heading: String? = null
    var exercises = mutableListOf<ExerciseLine>()

    fun flush() {
        if (heading != null || exercises.isNotEmpty()) categories += ExerciseCategory(heading, exercises)
    }

    lines.forEach { line ->
        val strippedForHeading = line.trimEnd(':').trim()
        val looksLikeCategory = (line.trim().endsWith(':') && line.none { it.isDigit() }) ||
            categoryNameRegex.matches(strippedForHeading)
        val isFreeformSection = heading?.let { freeformSectionRegex.matches(it.trim()) } == true
        when {
            looksLikeCategory -> {
                flush()
                heading = strippedForHeading
                exercises = mutableListOf()
            }
            !isFreeformSection && exercises.isNotEmpty() && continuationRegex.containsMatchIn(line) -> {
                val previous = exercises.removeAt(exercises.lastIndex)
                val mergedDetail = listOfNotNull(previous.detail, line.trim()).joinToString(", ")
                exercises += previous.copy(detail = mergedDetail)
            }
            else -> exercises += parseExerciseLine(line)
        }
    }
    flush()
    return categories
}

/** Splits an exercise line like "Bench Press: 4 sets x 8 reps" into a name and a trailing detail. */
internal fun parseExerciseLine(raw: String): ExerciseLine {
    val separator = Regex("\\s*[-–—:]\\s+")
    val parts = raw.split(separator, limit = 2)
    return if (parts.size == 2 && parts[1].isNotBlank() && parts[1].any { it.isDigit() }) {
        ExerciseLine(name = parts[0].trim(), detail = parts[1].trim())
    } else {
        ExerciseLine(name = raw.trim(), detail = null)
    }
}

private val inlineSetsAndReps = Regex(
    "(\\d+)\\s*sets?\\s*(?:x|of|×)?\\s*(\\d+(?:-\\d+)?)\\s*reps?",
    RegexOption.IGNORE_CASE
)
private val inlineShorthand = Regex("(\\d+)\\s*[x×]\\s*(\\d+(?:-\\d+)?)", RegexOption.IGNORE_CASE)
private val labelSets = Regex("sets?\\s*:\\s*(\\d+)", RegexOption.IGNORE_CASE)
private val labelReps = Regex("reps?\\s*:\\s*(\\d+(?:-\\d+)?)", RegexOption.IGNORE_CASE)

/** Pulls sets/reps figures out of a detail string, leaving any remainder as a free-text note. */
internal fun parseExerciseDetail(detail: String?): ExerciseStats {
    if (detail == null) return ExerciseStats(null, null, null)

    val inlineMatch = inlineSetsAndReps.find(detail) ?: inlineShorthand.find(detail)
    if (inlineMatch != null) {
        val (sets, reps) = inlineMatch.destructured
        val note = detail.replaceRange(inlineMatch.range, "")
            .trim()
            .trim(',', '-', '–', '—', '.')
            .trim()
        return ExerciseStats(sets = sets, reps = reps, note = note.ifBlank { null })
    }

    // Label-first phrasing, e.g. "Sets: 4, Reps: 6-8" (the AI wrote the word before the number).
    val setsMatch = labelSets.find(detail)
    val repsMatch = labelReps.find(detail)
    if (setsMatch != null || repsMatch != null) {
        var note: String = detail
        listOfNotNull(setsMatch?.range, repsMatch?.range)
            .sortedByDescending { it.first }
            .forEach { note = note.removeRange(it) }
        note = note.trim().trim(',', '-', '–', '—', '.').trim()
        return ExerciseStats(
            sets = setsMatch?.groupValues?.get(1),
            reps = repsMatch?.groupValues?.get(1),
            note = note.ifBlank { null }
        )
    }

    return ExerciseStats(null, null, detail)
}

/**
 * Renders a freeform AI workout plan: an unnumbered overview field (if the AI wrote one),
 * followed by unnumbered day/category sections whose exercises are individually numbered.
 */
@Composable
internal fun WorkoutPlanSections(plan: String, modifier: Modifier = Modifier) {
    val content = remember(plan) { parseWorkoutPlan(plan) }
    Column(modifier = modifier) {
        if (content.planTitle != null || content.overview != null) {
            PlanHeaderFields(title = content.planTitle, overview = content.overview)
            Spacer(modifier = Modifier.height(14.dp))
        }
        content.days.forEachIndexed { dayIndex, day ->
            if (dayIndex > 0) Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Surface2,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    day.heading?.let { heading ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconChip(icon = Icons.Default.FitnessCenter, tint = Sage, size = 30.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                heading,
                                style = MaterialTheme.typography.titleSmall,
                                color = Paper,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = Line, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    day.categories.forEachIndexed { categoryIndex, category ->
                        if (categoryIndex > 0) Spacer(modifier = Modifier.height(14.dp))
                        category.heading?.let { heading ->
                            Text(
                                heading.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = Sage,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        category.exercises.forEachIndexed { exerciseIndex, exercise ->
                            if (exerciseIndex > 0) Spacer(modifier = Modifier.height(8.dp))
                            ExerciseRow(index = exerciseIndex + 1, exercise = exercise)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The AI's plan title and overview blurb rendered as two separate fields — the title as its
 * own heading, the overview as a distinct highlighted card — so they don't run together.
 */
@Composable
private fun PlanHeaderFields(title: String?, overview: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        title?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = Paper,
                fontWeight = FontWeight.Bold
            )
            if (overview != null) Spacer(modifier = Modifier.height(10.dp))
        }
        overview?.let { text ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = EmberDim,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "OVERVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ember,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = Paper)
                }
            }
        }
    }
}

/**
 * A single exercise: its name on the first line (numbered within its category). Its sets/reps
 * are plain, unnumbered text that drops down underneath once the name row is tapped.
 */
@Composable
private fun ExerciseRow(index: Int, exercise: ExerciseLine) {
    var expanded by rememberSaveable(exercise.name, exercise.detail) { mutableStateOf(false) }
    val stats = remember(exercise.detail) { parseExerciseDetail(exercise.detail) }
    val hasDetail = exercise.detail != null
    val detailLine = remember(stats) {
        val setsReps = listOfNotNull(stats.sets?.let { "$it sets" }, stats.reps?.let { "$it reps" })
        buildString {
            append(setsReps.joinToString(" × "))
            stats.note?.let { note ->
                if (isNotEmpty()) append(" · ")
                append(note)
            }
        }
    }
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = Ink,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.then(
                if (hasDetail) Modifier.clickable { expanded = !expanded } else Modifier
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$index. ${exercise.name}",
                    color = Paper,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (hasDetail) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse details" else "Expand details",
                        tint = Muted,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevronRotation)
                    )
                }
            }
            if (expanded && detailLine.isNotBlank()) {
                Text(
                    detailLine,
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                )
            }
        }
    }
}
