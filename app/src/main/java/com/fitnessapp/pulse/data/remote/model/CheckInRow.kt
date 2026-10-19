package com.fitnessapp.pulse.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CheckInRow(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("phq9_score") val phq9Score: Int,
    @SerialName("phq9_severity") val phq9Severity: String,
    @SerialName("dass_depression_score") val dassDepressionScore: Int,
    @SerialName("dass_anxiety_score") val dassAnxietyScore: Int,
    @SerialName("dass_stress_score") val dassStressScore: Int,
    @SerialName("ai_reflection") val aiReflection: String,
    @SerialName("logged_at") val loggedAt: String? = null,
    /** Per-question answers, keyed by [com.fitnessapp.pulse.data.model.QuestionnaireItem.id] as a string. */
    @SerialName("phq9_answers") val phq9Answers: Map<String, Int>? = null,
    @SerialName("dass_answers") val dassAnswers: Map<String, Int>? = null
)