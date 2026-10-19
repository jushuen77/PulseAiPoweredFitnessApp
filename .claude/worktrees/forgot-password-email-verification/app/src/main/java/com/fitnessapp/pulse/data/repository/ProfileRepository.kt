package com.fitnessapp.pulse.data.repository

import com.fitnessapp.pulse.data.remote.SupabaseInstance
import com.fitnessapp.pulse.data.remote.model.ProfileRow
import io.github.jan.supabase.postgrest.from

class ProfileRepository {

    suspend fun getProfile(userId: String): ProfileRow? {
        return SupabaseInstance.client.from("profiles")
            .select { filter { eq("user_id", userId) } }
            .decodeList<ProfileRow>()
            .firstOrNull()
    }

    suspend fun upsertProfile(userId: String, displayName: String) {
        SupabaseInstance.client.from("profiles").upsert(
            ProfileRow(userId = userId, displayName = displayName)
        )
    }
}