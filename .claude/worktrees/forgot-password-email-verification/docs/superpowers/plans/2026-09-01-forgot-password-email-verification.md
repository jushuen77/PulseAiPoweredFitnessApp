# Forgot Password & Email Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add email-verified sign-up (blocking until verified) and a forgot-password/reset-password flow to the existing custom username/password auth, delivering 6-digit codes by email via a Supabase Edge Function + Resend.

**Architecture:** Three new Supabase Edge Functions (`send-code`, `verify-email`, `reset-password`) hold the Resend API key and DB service-role access server-side; the Android app never talks to Resend or the new `auth_codes` table directly, only to these functions via the `functions-kt` client. Three new screens (VerifyEmail, ForgotPassword, ResetPassword) and their ViewModels wrap the new repository methods; `AuthRepository.signIn` gains a sealed result type so an unverified account routes to the verify screen instead of failing outright.

**Tech Stack:** Kotlin, Jetpack Compose, Supabase Kotlin SDK (`postgrest-kt`, `functions-kt`), Supabase Edge Functions (Deno/TypeScript), Resend (email API), kotlinx.serialization.

**Spec:** `docs/superpowers/specs/2026-09-01-forgot-password-email-verification-design.md`

## Global Constraints

- No email-sending API key or DB service-role key is ever placed in the Android app — only in Edge Function secrets (per spec's "Decisions locked in").
- Login identifier stays `username`; `email` is a new required sign-up field used only for verification/reset (per spec).
- Codes are 6-digit numeric, hashed with SHA-256 before storage (matching the existing password-hashing approach in `AuthRepository.kt`), 10-minute expiry.
- Deployment of the SQL migration and Edge Functions is done by the user, not by this plan's executor — this plan produces the files and documents the exact commands, but does not run `supabase functions deploy` or `supabase db push` against a live project.
- Per standing project preference, executors do not run `installDebug` or drive the UI — verification that the on-device flow works end-to-end (real email, real code) is done by the user.

---

### Task 1: SQL migration for email + auth_codes

**Files:**
- Create: `supabase_email_verification_migration.sql` (repo root, alongside the existing `supabase_auth_migration.sql`)

**Interfaces:**
- Produces: the `app_users.email` (`text`, unique, nullable), `app_users.email_verified` (`boolean not null default false`) columns, and the `auth_codes` table (`id uuid pk`, `email text`, `code_hash text`, `purpose text check in ('verify_email','reset_password')`, `expires_at timestamptz`, `used_at timestamptz`, `created_at timestamptz`) that every later task's Edge Functions and Kotlin code assume exist.

- [ ] **Step 1: Write the migration file**

```sql
-- Adds email verification and password-reset support on top of the custom
-- app_users table (see supabase_auth_migration.sql for the base table).

-- 1) Email fields on app_users. Nullable so existing test rows don't break;
-- the app requires email for every new sign-up going forward.
alter table app_users add column if not exists email text unique;
alter table app_users add column if not exists email_verified boolean not null default false;

-- 2) One-time codes for both email verification and password reset.
-- Only the Edge Functions (using the service-role key) ever touch this
-- table, so it does NOT get an anon-role RLS policy like app_users does --
-- the app never queries it directly.
create table if not exists auth_codes (
  id uuid primary key default gen_random_uuid(),
  email text not null,
  code_hash text not null,
  purpose text not null check (purpose in ('verify_email', 'reset_password')),
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

alter table auth_codes enable row level security;
-- No policies added: RLS with no policies denies all access to the anon
-- role, which is exactly what we want here.
```

- [ ] **Step 2: Verify syntax by eye (no live project to run this against)**

Re-read the file and confirm: every statement ends with `;`, the `check` constraint's allowed values (`'verify_email'`, `'reset_password'`) exactly match the `purpose` strings the Kotlin request models will send in Task 5, and column types match what the Edge Functions in Task 2 read/write. This substitutes for an automated test since there is no local Postgres instance in this repo to run migrations against — the user applies it to their own Supabase project per Task 2's README.

- [ ] **Step 3: Commit**

```bash
git add supabase_email_verification_migration.sql
git commit -m "Add SQL migration for email verification and password reset"
```

---

### Task 2: Supabase Edge Functions (send-code, verify-email, reset-password)

**Files:**
- Create: `supabase/functions/_shared/codes.ts`
- Create: `supabase/functions/send-code/index.ts`
- Create: `supabase/functions/verify-email/index.ts`
- Create: `supabase/functions/reset-password/index.ts`
- Create: `supabase/functions/README.md`

**Interfaces:**
- Consumes: the `app_users`/`auth_codes` schema from Task 1.
- Produces: three HTTP endpoints the Android app calls in Task 5 —
  `send-code` (`POST { email: string, purpose: "verify_email" | "reset_password" }` → `{ ok: boolean, error?: string }`),
  `verify-email` (`POST { email: string, code: string }` → `{ ok: boolean, error?: string }`),
  `reset-password` (`POST { email: string, code: string, newPassword: string }` → `{ ok: boolean, error?: string }`).
  All three respond with that same `{ ok, error? }` JSON shape, which `AuthRepository` in Task 5 decodes.

- [ ] **Step 1: Write the shared code-generation/hashing helper**

```ts
// supabase/functions/_shared/codes.ts

export function generateCode(): string {
  const n = crypto.getRandomValues(new Uint32Array(1))[0] % 1_000_000;
  return n.toString().padStart(6, "0");
}

export async function hashCode(code: string): Promise<string> {
  const data = new TextEncoder().encode(code);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export function jsonResponse(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
```

- [ ] **Step 2: Write `send-code`**

```ts
// supabase/functions/send-code/index.ts
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { generateCode, hashCode, jsonResponse } from "../_shared/codes.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const resendApiKey = Deno.env.get("RESEND_API_KEY")!;
const fromAddress = Deno.env.get("RESEND_FROM_ADDRESS")!;

Deno.serve(async (req) => {
  try {
    const { email, purpose } = await req.json();
    if (!email || !["verify_email", "reset_password"].includes(purpose)) {
      return jsonResponse({ ok: false, error: "Invalid request" }, 400);
    }

    const supabase = createClient(supabaseUrl, serviceRoleKey);
    const { data: user } = await supabase
      .from("app_users")
      .select("uid, email_verified")
      .eq("email", email)
      .maybeSingle();

    if (!user) {
      return jsonResponse({ ok: false, error: "No account with that email" }, 404);
    }
    if (purpose === "verify_email" && user.email_verified) {
      return jsonResponse({ ok: false, error: "Email is already verified" }, 400);
    }

    const code = generateCode();
    const codeHash = await hashCode(code);
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();

    await supabase.from("auth_codes").insert({
      email,
      code_hash: codeHash,
      purpose,
      expires_at: expiresAt,
    });

    const subject = purpose === "verify_email"
      ? "Verify your Pulse account"
      : "Reset your Pulse password";
    const text = `Your code is ${code}. It expires in 10 minutes.`;

    const emailResponse = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${resendApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ from: fromAddress, to: email, subject, text }),
    });

    if (!emailResponse.ok) {
      console.error("Resend error:", await emailResponse.text());
      return jsonResponse({ ok: false, error: "Failed to send email" }, 502);
    }

    return jsonResponse({ ok: true });
  } catch (e) {
    console.error(e);
    return jsonResponse({ ok: false, error: "Unexpected error" }, 500);
  }
});
```

- [ ] **Step 3: Write `verify-email`**

```ts
// supabase/functions/verify-email/index.ts
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { hashCode, jsonResponse } from "../_shared/codes.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

Deno.serve(async (req) => {
  try {
    const { email, code } = await req.json();
    if (!email || !code) return jsonResponse({ ok: false, error: "Invalid request" }, 400);

    const supabase = createClient(supabaseUrl, serviceRoleKey);
    const codeHash = await hashCode(code);

    const { data: row } = await supabase
      .from("auth_codes")
      .select("id, code_hash, expires_at")
      .eq("email", email)
      .eq("purpose", "verify_email")
      .is("used_at", null)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (!row || row.code_hash !== codeHash) {
      return jsonResponse({ ok: false, error: "Incorrect code" }, 400);
    }
    if (new Date(row.expires_at) < new Date()) {
      return jsonResponse({ ok: false, error: "Code has expired" }, 400);
    }

    await supabase.from("auth_codes").update({ used_at: new Date().toISOString() }).eq("id", row.id);
    await supabase.from("app_users").update({ email_verified: true }).eq("email", email);

    return jsonResponse({ ok: true });
  } catch (e) {
    console.error(e);
    return jsonResponse({ ok: false, error: "Unexpected error" }, 500);
  }
});
```

- [ ] **Step 4: Write `reset-password`**

```ts
// supabase/functions/reset-password/index.ts
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { hashCode, jsonResponse } from "../_shared/codes.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

Deno.serve(async (req) => {
  try {
    const { email, code, newPassword } = await req.json();
    if (!email || !code || !newPassword || newPassword.length < 6) {
      return jsonResponse({ ok: false, error: "Invalid request" }, 400);
    }

    const supabase = createClient(supabaseUrl, serviceRoleKey);
    const codeHash = await hashCode(code);

    const { data: row } = await supabase
      .from("auth_codes")
      .select("id, code_hash, expires_at")
      .eq("email", email)
      .eq("purpose", "reset_password")
      .is("used_at", null)
      .order("created_at", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (!row || row.code_hash !== codeHash) {
      return jsonResponse({ ok: false, error: "Incorrect code" }, 400);
    }
    if (new Date(row.expires_at) < new Date()) {
      return jsonResponse({ ok: false, error: "Code has expired" }, 400);
    }

    const salt = generateSalt();
    const passwordHash = await hashPassword(newPassword, salt);

    await supabase.from("auth_codes").update({ used_at: new Date().toISOString() }).eq("id", row.id);
    await supabase
      .from("app_users")
      .update({ password_hash: passwordHash, password_salt: salt })
      .eq("email", email);

    return jsonResponse({ ok: true });
  } catch (e) {
    console.error(e);
    return jsonResponse({ ok: false, error: "Unexpected error" }, 500);
  }
});

function generateSalt(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Must match AuthRepository.hashPassword in the Android app exactly:
// SHA-256 over (salt bytes ++ password bytes), hex-encoded.
async function hashPassword(password: string, salt: string): Promise<string> {
  const data = new TextEncoder().encode(salt + password);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}
```

- [ ] **Step 5: Write the deployment README**

```markdown
# Email verification & password reset Edge Functions

Three functions, deployed together:

- `send-code` — emails a 6-digit code for either `verify_email` or
  `reset_password`.
- `verify-email` — checks a `verify_email` code and marks the account
  verified.
- `reset-password` — checks a `reset_password` code and updates the
  account's password.

## One-time setup

1. Run `supabase_email_verification_migration.sql` (repo root) against your
   project's SQL editor, or via `supabase db push`.
2. Create a free account at https://resend.com and grab an API key. If you
   don't own a verified sending domain yet, Resend's sandbox
   `onboarding@resend.dev` sender works for testing.
3. Set the function secrets (from the project root, with the Supabase CLI
   logged in and linked to your project):

   ```bash
   supabase secrets set RESEND_API_KEY=your-resend-api-key
   supabase secrets set RESEND_FROM_ADDRESS="Pulse <onboarding@resend.dev>"
   ```

   `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are already available to
   every Edge Function automatically — you don't need to set those.

## Deploy

```bash
supabase functions deploy send-code
supabase functions deploy verify-email
supabase functions deploy reset-password
```

## Sanity check (before wiring up the app)

```bash
curl -i -X POST 'https://<project-ref>.supabase.co/functions/v1/send-code' \
  -H "Authorization: Bearer <anon-key>" \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","purpose":"verify_email"}'
```

A `{"ok":true}` response means the code was generated and Resend accepted
the email — check the inbox (or Resend's dashboard logs under "Emails") for
delivery.
```

- [ ] **Step 6: Verify by reading, not running**

There's no Deno toolchain in this repo to run these functions locally. Re-read all four TypeScript files and confirm: every function reads `req.json()` inside a `try`, every code path returns a `jsonResponse`, and `reset-password`'s `hashPassword` matches `AuthRepository.hashPassword` byte-for-byte (both are SHA-256 over salt-bytes-then-password-bytes). The user runs the actual functional check from the README's `curl` command after deploying, per this plan's Global Constraints.

- [ ] **Step 7: Commit**

```bash
git add supabase/functions
git commit -m "Add Edge Functions for email verification and password reset"
```

---

### Task 3: Email validation utility (TDD)

**Files:**
- Create: `app/src/main/java/com/fitnessapp/pulse/util/EmailValidation.kt`
- Test: `app/src/test/java/com/fitnessapp/pulse/util/EmailValidationTest.kt`

**Interfaces:**
- Produces: `fun isValidEmail(email: String): Boolean`, used by `AuthViewModel` (Task 6) and `ForgotPasswordViewModel` (Task 9).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.fitnessapp.pulse.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailValidationTest {
    @Test
    fun `valid email is accepted`() {
        assertTrue(isValidEmail("student@example.com"))
    }

    @Test
    fun `email missing at sign is rejected`() {
        assertFalse(isValidEmail("studentexample.com"))
    }

    @Test
    fun `email missing domain dot is rejected`() {
        assertFalse(isValidEmail("student@examplecom"))
    }

    @Test
    fun `blank email is rejected`() {
        assertFalse(isValidEmail("  "))
    }

    @Test
    fun `email with surrounding whitespace is trimmed and accepted`() {
        assertTrue(isValidEmail("  student@example.com  "))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.fitnessapp.pulse.util.EmailValidationTest"`
Expected: FAIL to compile — `isValidEmail` is unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.fitnessapp.pulse.util

private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.fitnessapp.pulse.util.EmailValidationTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/fitnessapp/pulse/util/EmailValidation.kt app/src/test/java/com/fitnessapp/pulse/util/EmailValidationTest.kt
git commit -m "Add email validation utility"
```

---

### Task 4: Add functions-kt dependency and install the Functions plugin

**Files:**
- Modify: `app/build.gradle.kts:80` (after the `storage-kt` line)
- Modify: `app/src/main/java/com/fitnessapp/pulse/data/remote/SupabaseInstance.kt`

**Interfaces:**
- Produces: `SupabaseInstance.client.functions` (the `Functions` plugin), used by `AuthRepository` in Task 5.

- [ ] **Step 1: Add the dependency**

In `app/build.gradle.kts`, immediately after the `storage-kt` line:

```kotlin
    implementation("io.github.jan-tennert.supabase:storage-kt")//supabase
    implementation("io.github.jan-tennert.supabase:functions-kt")//supabase
```

- [ ] **Step 2: Install the plugin**

```kotlin
package com.fitnessapp.pulse.data.remote

import com.fitnessapp.pulse.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseInstance {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Storage)
        install(Functions)
    }
}
```

- [ ] **Step 3: Sync and verify it builds**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (this only proves the new dependency resolves and the plugin installs — `AuthRepository` doesn't use it yet).

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/fitnessapp/pulse/data/remote/SupabaseInstance.kt
git commit -m "Add functions-kt dependency and install Functions plugin"
```

---

### Task 5: AppUserRow + AuthRepository — email fields, SignInResult, code-function calls

**Files:**
- Modify: `app/src/main/java/com/fitnessapp/pulse/data/remote/model/AppUserRow.kt`
- Modify: `app/src/main/java/com/fitnessapp/pulse/data/repository/AuthRepository.kt`

**Interfaces:**
- Consumes: `SupabaseInstance.client.functions` (Task 4). Note: the repository does not validate email format itself — that's UI-layer responsibility, kept in `AuthViewModel`/`ForgotPasswordViewModel` per Task 6/9.
- Produces:
  - `data class LoggedInUser(val uid: String, val username: String)` (unchanged).
  - `sealed class SignInResult { data class Success(val user: LoggedInUser); data class NeedsVerification(val email: String); data class Error(val message: String) }` — consumed by `AuthViewModel.signIn()` in Task 6.
  - `AuthRepository.signUp(username: String, email: String, password: String): Result<LoggedInUser>` — consumed by `AuthViewModel.signUp()` in Task 6.
  - `AuthRepository.signIn(username: String, password: String): SignInResult` — consumed by `AuthViewModel.signIn()` in Task 6.
  - `AuthRepository.sendVerificationCode(email: String): Result<Unit>` — consumed by `VerifyEmailViewModel` (Task 8) and internally by `signUp`.
  - `AuthRepository.verifyEmailCode(email: String, code: String): Result<Unit>` — consumed by `VerifyEmailViewModel` (Task 8).
  - `AuthRepository.sendPasswordResetCode(email: String): Result<Unit>` — consumed by `ForgotPasswordViewModel` (Task 9).
  - `AuthRepository.resetPassword(email: String, code: String, newPassword: String): Result<Unit>` — consumed by `ResetPasswordViewModel` (Task 10).

- [ ] **Step 1: Update AppUserRow**

```kotlin
package com.fitnessapp.pulse.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUserRow(
    val uid: String? = null,
    val username: String,
    val email: String? = null,
    @SerialName("password_hash") val passwordHash: String,
    @SerialName("password_salt") val passwordSalt: String,
    @SerialName("email_verified") val emailVerified: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)
```

- [ ] **Step 2: Rewrite AuthRepository**

```kotlin
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
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (No new automated test here: exercising `signUp`/`signIn`/the code-function calls for real requires a live Supabase project and deployed Edge Functions, which are out of scope for this plan per the Global Constraints — this is validated on-device by the user once Task 2's functions are deployed.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/fitnessapp/pulse/data/remote/model/AppUserRow.kt app/src/main/java/com/fitnessapp/pulse/data/repository/AuthRepository.kt
git commit -m "Add email fields, SignInResult, and verification/reset methods to AuthRepository"
```

---

### Task 6: AuthViewModel — email field, SignInResult handling, verification routing

**Files:**
- Modify: `app/src/main/java/com/fitnessapp/pulse/ui/auth/AuthViewModel.kt`

**Interfaces:**
- Consumes: `AuthRepository.signUp(username, email, password)`, `AuthRepository.signIn(username, password): SignInResult`, `isValidEmail` (Task 3).
- Produces: `AuthUiState.email: String`, `AuthUiState.verificationEmail: String?`, `AuthViewModel.onEmailChange(String)` — consumed by `SignUpScreen` (Task 7) and `LoginScreen` (Task 7).

- [ ] **Step 1: Rewrite AuthViewModel**

```kotlin
package com.fitnessapp.pulse.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.local.AuthPreferences
import com.fitnessapp.pulse.data.local.SessionManager
import com.fitnessapp.pulse.data.repository.AuthRepository
import com.fitnessapp.pulse.data.repository.SignInResult
import com.fitnessapp.pulse.util.isValidEmail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val rememberMe: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val verificationEmail: String? = null
)

class AuthViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repo: AuthRepository = AuthRepository()

    private val _uiState = MutableStateFlow(
        AuthUiState(rememberMe = AuthPreferences.getRememberMe(application))
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, errorMessage = null) }
    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    fun onRememberMeChange(value: Boolean) = _uiState.update { it.copy(rememberMe = value) }

    fun signIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repo.signIn(_uiState.value.username, _uiState.value.password)) {
                is SignInResult.Success -> {
                    AuthPreferences.setRememberMe(getApplication(), _uiState.value.rememberMe)
                    SessionManager.login(getApplication(), result.user.uid, result.user.username, _uiState.value.rememberMe)
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                is SignInResult.NeedsVerification -> {
                    _uiState.update { it.copy(isLoading = false, verificationEmail = result.email) }
                }
                is SignInResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun signUp() {
        if (_uiState.value.username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a username") }
            return
        }
        if (!isValidEmail(_uiState.value.email)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid email") }
            return
        }
        if (_uiState.value.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }
        if (_uiState.value.password != _uiState.value.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords don't match") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repo.signUp(_uiState.value.username, _uiState.value.email, _uiState.value.password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, verificationEmail = _uiState.value.email.trim()) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Sign up failed") } }
        }
    }
}
```

Note the behavior change from before this plan: `signUp()` no longer calls `SessionManager.login` or sets `isSuccess` — a new account is unverified by design (per spec, sign-up blocks on verification), so it now sets `verificationEmail` to route to the verify screen instead.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL at this point — `SignUpScreen.kt` still references the old `onSignUpSuccess: () -> Unit` callback shape and `LoginScreen.kt` doesn't yet handle `verificationEmail`. This is expected; Task 7 fixes both screens. (If you're executing tasks strictly in order, this red state is normal and resolves by the end of Task 7 — don't stop here.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/fitnessapp/pulse/ui/auth/AuthViewModel.kt
git commit -m "Add email field and SignInResult handling to AuthViewModel"
```

---

### Task 7: LoginScreen + SignUpScreen — email field, forgot-password link, verification routing

**Files:**
- Modify: `app/src/main/java/com/fitnessapp/pulse/ui/auth/LoginScreen.kt`
- Modify: `app/src/main/java/com/fitnessapp/pulse/ui/auth/SignUpScreen.kt`

**Interfaces:**
- Consumes: `AuthUiState.email`/`verificationEmail`, `AuthViewModel.onEmailChange` (Task 6).
- Produces: `LoginScreen(onLoginSuccess, onNavigateToSignUp, onNavigateToForgotPassword: () -> Unit, onNeedsVerification: (String) -> Unit, viewModel)` and `SignUpScreen(onSignUpSuccess: (email: String) -> Unit, onNavigateToLogin, viewModel)` — both consumed by `PulseNavGraph` in Task 11.

- [ ] **Step 1: Update LoginScreen**

```kotlin
package com.fitnessapp.pulse.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.BrandMark
import com.fitnessapp.pulse.ui.theme.AppBackground
import com.fitnessapp.pulse.ui.theme.Ember
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNeedsVerification: (String) -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onLoginSuccess()
    }
    LaunchedEffect(uiState.verificationEmail) {
        uiState.verificationEmail?.let(onNeedsVerification)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            .padding(top = 60.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandMark()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Welcome back", style = MaterialTheme.typography.titleLarge, color = Paper)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Log in to keep your streak going",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        PulseTextField(
            label = "USERNAME",
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            leadingIcon = Icons.Default.Person
        )
        Spacer(modifier = Modifier.height(14.dp))
        PulseTextField(
            label = "PASSWORD",
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            isPassword = true,
            leadingIcon = Icons.Default.Lock
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = uiState.rememberMe,
                onCheckedChange = viewModel::onRememberMeChange,
                colors = CheckboxDefaults.colors(checkedColor = Ember)
            )
            Text("Remember me", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onNavigateToForgotPassword) {
                Text("Forgot password?", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ErrorBanner(uiState.errorMessage)
        if (uiState.errorMessage != null) Spacer(modifier = Modifier.height(16.dp))

        AuthPrimaryButton(
            text = "Log In",
            isLoading = uiState.isLoading,
            onClick = { viewModel.signIn() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onNavigateToSignUp) {
            Text(
                "Don't have an account? Sign up",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

- [ ] **Step 2: Update SignUpScreen**

```kotlin
package com.fitnessapp.pulse.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.BrandMark
import com.fitnessapp.pulse.ui.theme.AppBackground
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper

@Composable
fun SignUpScreen(
    onSignUpSuccess: (email: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.verificationEmail) {
        uiState.verificationEmail?.let(onSignUpSuccess)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 60.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Create your account", style = MaterialTheme.typography.titleLarge, color = Paper)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Start tracking workouts, meals, and mood",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            PulseTextField(
                label = "USERNAME",
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                leadingIcon = Icons.Default.Person
            )
            Spacer(modifier = Modifier.height(14.dp))
            PulseTextField(
                label = "EMAIL",
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                leadingIcon = Icons.Default.Email
            )
            Spacer(modifier = Modifier.height(14.dp))
            PulseTextField(
                label = "PASSWORD",
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                isPassword = true,
                leadingIcon = Icons.Default.Lock
            )
            Spacer(modifier = Modifier.height(14.dp))
            PulseTextField(
                label = "CONFIRM PASSWORD",
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                isPassword = true,
                leadingIcon = Icons.Default.Lock
            )

            Spacer(modifier = Modifier.height(20.dp))
            ErrorBanner(uiState.errorMessage)
            if (uiState.errorMessage != null) Spacer(modifier = Modifier.height(16.dp))

            AuthPrimaryButton(
                text = "Sign Up",
                isLoading = uiState.isLoading,
                onClick = { viewModel.signUp() }
            )

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onNavigateToLogin) {
                Text("Have an account? Log in", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        AuthBackButton(
            onBack = onNavigateToLogin,
            modifier = Modifier.padding(top = 12.dp, start = 12.dp)
        )
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL — `PulseNavGraph.kt` still calls `LoginScreen`/`SignUpScreen` with the old parameter lists. Expected at this point; Task 11 updates the nav graph. Don't stop here if executing strictly in order.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/fitnessapp/pulse/ui/auth/LoginScreen.kt app/src/main/java/com/fitnessapp/pulse/ui/auth/SignUpScreen.kt
git commit -m "Add email field to SignUpScreen and forgot-password link to LoginScreen"
```

---

### Task 8: VerifyEmailScreen + VerifyEmailViewModel

**Files:**
- Create: `app/src/main/java/com/fitnessapp/pulse/ui/auth/VerifyEmailViewModel.kt`
- Create: `app/src/main/java/com/fitnessapp/pulse/ui/auth/VerifyEmailScreen.kt`

**Interfaces:**
- Consumes: `AuthRepository.verifyEmailCode`, `AuthRepository.sendVerificationCode` (Task 5).
- Produces: `VerifyEmailScreen(email: String, onVerified: () -> Unit, onBack: () -> Unit, viewModel)` — consumed by `PulseNavGraph` in Task 11.

- [ ] **Step 1: Write VerifyEmailViewModel**

```kotlin
package com.fitnessapp.pulse.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VerifyEmailUiState(
    val code: String = "",
    val isVerifying: Boolean = false,
    val isResending: Boolean = false,
    val errorMessage: String? = null,
    val isVerified: Boolean = false,
    val resendCooldownSeconds: Int = 0
)

class VerifyEmailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository()

    private val _uiState = MutableStateFlow(VerifyEmailUiState())
    val uiState: StateFlow<VerifyEmailUiState> = _uiState.asStateFlow()

    fun onCodeChange(value: String) = _uiState.update { it.copy(code = value, errorMessage = null) }

    fun verify(email: String) {
        if (_uiState.value.code.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter the code we emailed you") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifying = true, errorMessage = null) }
            repo.verifyEmailCode(email, _uiState.value.code)
                .onSuccess { _uiState.update { it.copy(isVerifying = false, isVerified = true) } }
                .onFailure { e -> _uiState.update { it.copy(isVerifying = false, errorMessage = e.message ?: "Verification failed") } }
        }
    }

    fun resendCode(email: String) {
        if (_uiState.value.resendCooldownSeconds > 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResending = true, errorMessage = null) }
            repo.sendVerificationCode(email)
                .onSuccess {
                    _uiState.update { it.copy(isResending = false) }
                    startResendCooldown()
                }
                .onFailure { e -> _uiState.update { it.copy(isResending = false, errorMessage = e.message ?: "Couldn't resend code") } }
        }
    }

    private fun startResendCooldown() {
        viewModelScope.launch {
            _uiState.update { it.copy(resendCooldownSeconds = 30) }
            while (_uiState.value.resendCooldownSeconds > 0) {
                delay(1000)
                _uiState.update { it.copy(resendCooldownSeconds = it.resendCooldownSeconds - 1) }
            }
        }
    }
}
```

- [ ] **Step 2: Write VerifyEmailScreen**

```kotlin
package com.fitnessapp.pulse.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.BrandMark
import com.fitnessapp.pulse.ui.theme.AppBackground
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper

@Composable
fun VerifyEmailScreen(
    email: String,
    onVerified: () -> Unit,
    onBack: () -> Unit,
    viewModel: VerifyEmailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) onVerified()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 60.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Verify your email", style = MaterialTheme.typography.titleLarge, color = Paper)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Enter the code we sent to $email, or resend it below",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            PulseTextField(
                label = "VERIFICATION CODE",
                value = uiState.code,
                onValueChange = viewModel::onCodeChange,
                leadingIcon = Icons.Default.Numbers
            )

            Spacer(modifier = Modifier.height(20.dp))
            ErrorBanner(uiState.errorMessage)
            if (uiState.errorMessage != null) Spacer(modifier = Modifier.height(16.dp))

            AuthPrimaryButton(
                text = "Verify",
                isLoading = uiState.isVerifying,
                onClick = { viewModel.verify(email) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { viewModel.resendCode(email) },
                enabled = !uiState.isResending && uiState.resendCooldownSeconds == 0
            ) {
                val label = if (uiState.resendCooldownSeconds > 0)
                    "Resend code (${uiState.resendCooldownSeconds}s)"
                else
                    "Resend code"
                Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        AuthBackButton(onBack = onBack, modifier = Modifier.padding(top = 12.dp, start = 12.dp))
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — these are new, self-contained files not referenced anywhere yet.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/fitnessapp/pulse/ui/auth/VerifyEmailViewModel.kt app/src/main/java/com/fitnessapp/pulse/ui/auth/VerifyEmailScreen.kt
git commit -m "Add VerifyEmailScreen and its view model"
```

---

### Task 9: ForgotPasswordScreen + ForgotPasswordViewModel

**Files:**
- Create: `app/src/main/java/com/fitnessapp/pulse/ui/auth/ForgotPasswordViewModel.kt`
- Create: `app/src/main/java/com/fitnessapp/pulse/ui/auth/ForgotPasswordScreen.kt`

**Interfaces:**
- Consumes: `AuthRepository.sendPasswordResetCode` (Task 5), `isValidEmail` (Task 3).
- Produces: `ForgotPasswordScreen(onCodeSent: (email: String) -> Unit, onBack: () -> Unit, viewModel)` — consumed by `PulseNavGraph` in Task 11.

- [ ] **Step 1: Write ForgotPasswordViewModel**

```kotlin
package com.fitnessapp.pulse.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.repository.AuthRepository
import com.fitnessapp.pulse.util.isValidEmail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val codeSent: Boolean = false
)

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository()

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }

    fun sendCode() {
        val email = _uiState.value.email.trim()
        if (!isValidEmail(email)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid email") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repo.sendPasswordResetCode(email)
                .onSuccess { _uiState.update { it.copy(isLoading = false, codeSent = true) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Couldn't send code") } }
        }
    }
}
```

- [ ] **Step 2: Write ForgotPasswordScreen**

```kotlin
package com.fitnessapp.pulse.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.BrandMark
import com.fitnessapp.pulse.ui.theme.AppBackground
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper

@Composable
fun ForgotPasswordScreen(
    onCodeSent: (email: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.codeSent) {
        if (uiState.codeSent) onCodeSent(uiState.email.trim())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 60.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Forgot your password?", style = MaterialTheme.typography.titleLarge, color = Paper)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Enter your email and we'll send you a reset code",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            PulseTextField(
                label = "EMAIL",
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                leadingIcon = Icons.Default.Email
            )

            Spacer(modifier = Modifier.height(20.dp))
            ErrorBanner(uiState.errorMessage)
            if (uiState.errorMessage != null) Spacer(modifier = Modifier.height(16.dp))

            AuthPrimaryButton(
                text = "Send Code",
                isLoading = uiState.isLoading,
                onClick = { viewModel.sendCode() }
            )
        }

        AuthBackButton(onBack = onBack, modifier = Modifier.padding(top = 12.dp, start = 12.dp))
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — new, self-contained files.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/fitnessapp/pulse/ui/auth/ForgotPasswordViewModel.kt app/src/main/java/com/fitnessapp/pulse/ui/auth/ForgotPasswordScreen.kt
git commit -m "Add ForgotPasswordScreen and its view model"
```

---

### Task 10: ResetPasswordScreen + ResetPasswordViewModel

**Files:**
- Create: `app/src/main/java/com/fitnessapp/pulse/ui/auth/ResetPasswordViewModel.kt`
- Create: `app/src/main/java/com/fitnessapp/pulse/ui/auth/ResetPasswordScreen.kt`

**Interfaces:**
- Consumes: `AuthRepository.resetPassword` (Task 5).
- Produces: `ResetPasswordScreen(email: String, onResetSuccess: () -> Unit, onBack: () -> Unit, viewModel)` — consumed by `PulseNavGraph` in Task 11.

- [ ] **Step 1: Write ResetPasswordViewModel**

```kotlin
package com.fitnessapp.pulse.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.pulse.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResetPasswordUiState(
    val code: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isReset: Boolean = false
)

class ResetPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository()

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun onCodeChange(value: String) = _uiState.update { it.copy(code = value, errorMessage = null) }
    fun onNewPasswordChange(value: String) = _uiState.update { it.copy(newPassword = value, errorMessage = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }

    fun resetPassword(email: String) {
        val state = _uiState.value
        if (state.code.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter the code we emailed you") }
            return
        }
        if (state.newPassword.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords don't match") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repo.resetPassword(email, state.code, state.newPassword)
                .onSuccess { _uiState.update { it.copy(isLoading = false, isReset = true) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Couldn't reset password") } }
        }
    }
}
```

- [ ] **Step 2: Write ResetPasswordScreen**

```kotlin
package com.fitnessapp.pulse.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnessapp.pulse.ui.components.BrandMark
import com.fitnessapp.pulse.ui.theme.AppBackground
import com.fitnessapp.pulse.ui.theme.Muted
import com.fitnessapp.pulse.ui.theme.Paper

@Composable
fun ResetPasswordScreen(
    email: String,
    onResetSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: ResetPasswordViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isReset) {
        if (uiState.isReset) onResetSuccess()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 60.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Reset your password", style = MaterialTheme.typography.titleLarge, color = Paper)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Enter the code we sent to $email and choose a new password",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))

            PulseTextField(
                label = "RESET CODE",
                value = uiState.code,
                onValueChange = viewModel::onCodeChange,
                leadingIcon = Icons.Default.Numbers
            )
            Spacer(modifier = Modifier.height(14.dp))
            PulseTextField(
                label = "NEW PASSWORD",
                value = uiState.newPassword,
                onValueChange = viewModel::onNewPasswordChange,
                isPassword = true,
                leadingIcon = Icons.Default.Lock
            )
            Spacer(modifier = Modifier.height(14.dp))
            PulseTextField(
                label = "CONFIRM NEW PASSWORD",
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                isPassword = true,
                leadingIcon = Icons.Default.Lock
            )

            Spacer(modifier = Modifier.height(20.dp))
            ErrorBanner(uiState.errorMessage)
            if (uiState.errorMessage != null) Spacer(modifier = Modifier.height(16.dp))

            AuthPrimaryButton(
                text = "Reset Password",
                isLoading = uiState.isLoading,
                onClick = { viewModel.resetPassword(email) }
            )
        }

        AuthBackButton(onBack = onBack, modifier = Modifier.padding(top = 12.dp, start = 12.dp))
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — new, self-contained files.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/fitnessapp/pulse/ui/auth/ResetPasswordViewModel.kt app/src/main/java/com/fitnessapp/pulse/ui/auth/ResetPasswordScreen.kt
git commit -m "Add ResetPasswordScreen and its view model"
```

---

### Task 11: Wire everything into PulseNavGraph

**Files:**
- Modify: `app/src/main/java/com/fitnessapp/pulse/ui/navigation/PulseNavGraph.kt:1-138`

**Interfaces:**
- Consumes: `LoginScreen` (Task 7), `SignUpScreen` (Task 7), `VerifyEmailScreen` (Task 8), `ForgotPasswordScreen` (Task 9), `ResetPasswordScreen` (Task 10).

- [ ] **Step 1: Add imports and new routes**

In `PulseNavGraph.kt`, add these imports next to the existing `LoginScreen`/`SignUpScreen` imports:

```kotlin
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.fitnessapp.pulse.ui.auth.ForgotPasswordScreen
import com.fitnessapp.pulse.ui.auth.ResetPasswordScreen
import com.fitnessapp.pulse.ui.auth.VerifyEmailScreen
```

Then replace the `NavHost` body (the `composable("login") { ... }` through `composable("main") { ... }` block) with:

```kotlin
    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { navController.navigate("main") { popUpTo("login") { inclusive = true } } },
                onNavigateToSignUp = { navController.navigate("signup") },
                onNavigateToForgotPassword = { navController.navigate("forgot_password") },
                onNeedsVerification = { email -> navController.navigate("verify_email/$email") }
            )
        }
        composable("signup") {
            SignUpScreen(
                onSignUpSuccess = { email -> navController.navigate("verify_email/$email") },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(
            "verify_email/{email}",
            arguments = listOf(navArgument("email") { type = NavType.StringType })
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email").orEmpty()
            VerifyEmailScreen(
                email = email,
                onVerified = {
                    navController.navigate("login") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack("login", inclusive = false) }
            )
        }
        composable("forgot_password") {
            ForgotPasswordScreen(
                onCodeSent = { email -> navController.navigate("reset_password/$email") },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "reset_password/{email}",
            arguments = listOf(navArgument("email") { type = NavType.StringType })
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email").orEmpty()
            ResetPasswordScreen(
                email = email,
                onResetSuccess = {
                    navController.navigate("login") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("main") {
            MainAppScaffold(
                windowSizeClass = windowSizeClass,
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
    }
```

- [ ] **Step 2: Full project build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. This is the point where every screen from Tasks 7-10 is actually referenced, so it's the first full-project compile check for the whole feature.

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, including the `EmailValidationTest` from Task 3 and the pre-existing `WorkoutPlanUiTest`/`ExampleUnitTest`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/fitnessapp/pulse/ui/navigation/PulseNavGraph.kt
git commit -m "Wire email verification and password reset screens into navigation"
```

---

## After implementation

The code is complete and compiles, but the feature is inert until the user (per this plan's Global Constraints):

1. Runs `supabase_email_verification_migration.sql` against their Supabase project.
2. Creates a Resend account, sets `RESEND_API_KEY`/`RESEND_FROM_ADDRESS` as function secrets, and deploys the three functions per `supabase/functions/README.md`.
3. Installs the app on a device and runs through: sign up → receive a real email → verify → log in; then log out → forgot password → receive a real email → reset → log in with the new password.

Step 3 is real-device, real-inbox testing that only the user can do.
