package com.fitnessapp.pulse.data.repository

import com.fitnessapp.pulse.data.remote.SupabaseInstance
import com.fitnessapp.pulse.data.remote.model.AppUserRow
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom

data class LoggedInUser(val uid: String, val username: String)

/** Result of a sign-in attempt, distinguishing an unverified account from a hard failure. */
sealed class SignInResult {
    data class Success(val user: LoggedInUser) : SignInResult()
    data class NeedsVerification(val email: String) : SignInResult()
    data class Error(val message: String) : SignInResult()
}

@Serializable
private data class EmailCodeRequest(val email: String, val purpose: String)

@Serializable
private data class VerifyCodeRequest(val email: String, val code: String)

@Serializable
private data class ResetPasswordRequest(val email: String, val code: String, val newPassword: String)

@Serializable
private data class FunctionResult(val ok: Boolean, val error: String? = null)

private val jsonContentHeaders = Headers.build { append(HttpHeaders.ContentType, "application/json") }
private val json = Json { ignoreUnknownKeys = true }

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

    suspend fun signUp(username: String, email: String, password: String): Result<LoggedInUser> = try {
        val trimmedUsername = username.trim()
        val trimmedEmail = email.trim()

        val existingUsername = SupabaseInstance.client.from("app_users")
            .select { filter { eq("username", trimmedUsername) } }
            .decodeList<AppUserRow>()
        if (existingUsername.isNotEmpty()) throw Exception("Username already taken")

        val existingEmail = SupabaseInstance.client.from("app_users")
            .select { filter { eq("email", trimmedEmail) } }
            .decodeList<AppUserRow>()
        if (existingEmail.isNotEmpty()) throw Exception("An account with that email already exists")

        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        val inserted = SupabaseInstance.client.from("app_users").insert(
            AppUserRow(
                username = trimmedUsername,
                email = trimmedEmail,
                passwordHash = hash,
                passwordSalt = salt,
                emailVerified = false
            )
        ) { select() }.decodeSingle<AppUserRow>()
        val uid = inserted.uid ?: throw Exception("Sign up succeeded but no id was returned")

        sendVerificationCode(trimmedEmail)

        Result.success(LoggedInUser(uid, trimmedUsername))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun signIn(username: String, password: String): SignInResult {
        return try {
            val trimmed = username.trim()
            val user = SupabaseInstance.client.from("app_users")
                .select { filter { eq("username", trimmed) } }
                .decodeList<AppUserRow>()
                .firstOrNull() ?: return SignInResult.Error("Invalid username or password")
            val hash = hashPassword(password, user.passwordSalt)
            if (hash != user.passwordHash) return SignInResult.Error("Invalid username or password")
            val uid = user.uid ?: return SignInResult.Error("Missing user id")
            if (!user.emailVerified) {
                val email = user.email ?: return SignInResult.Error("No email on file for this account")
                return SignInResult.NeedsVerification(email)
            }
            SignInResult.Success(LoggedInUser(uid, trimmed))
        } catch (e: Exception) {
            SignInResult.Error(e.message ?: "Sign in failed")
        }
    }

    suspend fun sendVerificationCode(email: String): Result<Unit> =
        invokeCodeFunction("send-code", EmailCodeRequest(email.trim(), "verify_email"))

    suspend fun verifyEmailCode(email: String, code: String): Result<Unit> =
        invokeCodeFunction("verify-email", VerifyCodeRequest(email.trim(), code.trim()))

    suspend fun sendPasswordResetCode(email: String): Result<Unit> =
        invokeCodeFunction("send-code", EmailCodeRequest(email.trim(), "reset_password"))

    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<Unit> =
        invokeCodeFunction("reset-password", ResetPasswordRequest(email.trim(), code.trim(), newPassword))

    private suspend inline fun <reified TBody : Any> invokeCodeFunction(function: String, body: TBody): Result<Unit> = try {
        val response = SupabaseInstance.client.functions(function, body, headers = jsonContentHeaders)
        val result = json.decodeFromString<FunctionResult>(response.bodyAsText())
        if (result.ok) Result.success(Unit) else Result.failure(Exception(result.error ?: "Request failed"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
