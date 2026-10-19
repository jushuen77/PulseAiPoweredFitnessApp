package com.fitnessapp.pulse.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutRow(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    val goal: String,
    val level: String,
    @SerialName("days_per_week") val daysPerWeek: Int,
    @SerialName("plan_text") val planText: String,
    @SerialName("created_at") val createdAt: String? = null
)