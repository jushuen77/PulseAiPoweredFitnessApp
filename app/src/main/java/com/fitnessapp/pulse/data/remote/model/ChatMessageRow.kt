package com.fitnessapp.pulse.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageRow(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("session_id") val sessionId: String,
    val role: String,
    val content: String,
    @SerialName("created_at") val createdAt: String? = null
)
