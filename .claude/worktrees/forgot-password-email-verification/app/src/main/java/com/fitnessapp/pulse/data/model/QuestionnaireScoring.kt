package com.fitnessapp.pulse.data.model

data class ScoreBand(val label: String, val range: IntRange)

fun phq9Severity(total: Int): String = when (total) {
    in 0..4 -> "Minimal"
    in 5..9 -> "Mild"
    in 10..14 -> "Moderate"
    in 15..19 -> "Moderately Severe"
    else -> "Severe"
}

// DASS-21 subscale scores must be doubled to align with standard DASS severity tables
fun dassDepressionSeverity(doubledScore: Int): String = when (doubledScore) {
    in 0..9 -> "Normal"
    in 10..13 -> "Mild"
    in 14..20 -> "Moderate"
    in 21..27 -> "Severe"
    else -> "Extremely Severe"
}

fun dassAnxietySeverity(doubledScore: Int): String = when (doubledScore) {
    in 0..7 -> "Normal"
    in 8..9 -> "Mild"
    in 10..14 -> "Moderate"
    in 15..19 -> "Severe"
    else -> "Extremely Severe"
}

fun dassStressSeverity(doubledScore: Int): String = when (doubledScore) {
    in 0..14 -> "Normal"
    in 15..18 -> "Mild"
    in 19..25 -> "Moderate"
    in 26..33 -> "Severe"
    else -> "Extremely Severe"
}