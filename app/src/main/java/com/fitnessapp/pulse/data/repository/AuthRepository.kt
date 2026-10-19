package com.fitnessapp.pulse.data.repository

import com.fitnessapp.pulse.data.remote.SupabaseInstance
import com.fitnessapp.pulse.data.remote.model.AppUserRow
import io.github.jan.supabase.postgrest.from
import java.security.MessageDigest
import java.security.SecureRandom

data class LoggedInUser(val uid: String, val username: String)

/** Custom username/password auth against our own `app_users` table (no Supabase Auth). */
class AuthRepository {

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val hashBytes = digest.digest(password.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun signUp(username: String, password: String): Result<LoggedInUser> = try {
        val trimmed = username.trim()
        val existing = SupabaseInstance.client.from("app_users")
            .select { filter { eq("username", trimmed) } }
            .decodeList<AppUserRow>()
        if (existing.isNotEmpty()) throw Exception("Username already taken")

        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        val inserted = SupabaseInstance.client.from("app_users").insert(
            AppUserRow(username = trimmed, passwordHash = hash, passwordSalt = salt)
        ) { select() }.decodeSingle<AppUserRow>()
        val uid = inserted.uid ?: throw Exception("Sign up succeeded but no id was returned")
        Result.success(LoggedInUser(uid, trimmed))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun signIn(username: String, password: String): Result<LoggedInUser> = try {
        val trimmed = username.trim()
        val user = SupabaseInstance.client.from("app_users")
            .select { filter { eq("username", trimmed) } }
            .decodeList<AppUserRow>()
            .firstOrNull() ?: throw Exception("Invalid username or password")
        val hash = hashPassword(password, user.passwordSalt)
        if (hash != user.passwordHash) throw Exception("Invalid username or password")
        val uid = user.uid ?: throw Exception("Missing user id")
        Result.success(LoggedInUser(uid, trimmed))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

