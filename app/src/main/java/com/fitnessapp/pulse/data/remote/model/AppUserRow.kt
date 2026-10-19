package com.fitnessapp.pulse.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUserRow(
    val uid: String? = null,
    val username: String,
    @SerialName("password_hash") val passwordHash: String,
    @SerialName("password_salt") val passwordSalt: String,
    @SerialName("created_at") val createdAt: String? = null
)
