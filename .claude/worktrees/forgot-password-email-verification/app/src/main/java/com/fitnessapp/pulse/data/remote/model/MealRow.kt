package com.fitnessapp.pulse.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MealRow(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("food_name") val foodName: String,
    val calories: Int,
    @SerialName("protein_g") val proteinG: Int,
    @SerialName("carbs_g") val carbsG: Int,
    @SerialName("fat_g") val fatG: Int,
    @SerialName("logged_at") val loggedAt: String? = null,
    @SerialName("photo_path") val photoPath: String? = null
)