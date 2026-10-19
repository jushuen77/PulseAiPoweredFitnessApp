# Forgot Password & Email Verification — Design

**Date:** 2026-09-01
**Status:** Approved, pending implementation plan

## Context

Auth in this app is a custom username/password scheme against a Supabase
`app_users` table (Supabase Auth was deliberately replaced — see
`supabase_auth_migration.sql`). There is currently no email field anywhere in
the schema and no email-sending capability at all. This design adds:

1. Email verification at sign-up (blocking — a user cannot use the app until
   verified).
2. Forgot-password / reset-password via emailed code.

Both features need the same missing piece: a way to deliver a code to a real
inbox from a mobile app that has no backend server of its own.

## Decisions locked in during brainstorming

- **Delivery mechanism:** Supabase Edge Function calls an email API. Keeps
  provider API keys and the DB service-role key out of the APK (the direct
  in-app approach used for OpenAI today is *not* repeated here since this is
  a new component, not a change to an existing shipped pattern).
- **Provider:** Resend.
- **Code format:** 6-digit numeric code, entered by hand in-app. No deep
  linking — a mobile app has no page for a link to land on, and a link
  opened on a different device wouldn't complete the flow anyway.
- **Verification gate:** Sign-up blocks on verification — user is sent
  straight to a code-entry screen and cannot reach the app until verified.
- **Login identifier:** Stays username. Email is a new required field
  collected at sign-up, used only for verification/reset — not a login
  credential.
- **Deployment:** The user deploys the SQL migration and Edge Functions
  themselves (has their own Supabase project + will create a Resend
  account). This design's deliverable includes ready-to-run SQL and Edge
  Function source, plus clear deployment instructions, but implementation
  does not execute them against the live project.

## Database changes

### `app_users` — add two columns

```sql
alter table app_users add column email text unique;
alter table app_users add column email_verified boolean not null default false;
```

Nullable `email` so existing rows (already in the table from testing) don't
break; the app enforces "email required" at sign-up going forward. Rows with
`email is null` simply can't use verification/reset until they set one —
acceptable since there's no real user base yet.

### New table — `auth_codes`

```sql
create table auth_codes (
  id uuid primary key default gen_random_uuid(),
  email text not null,
  code_hash text not null,
  purpose text not null check (purpose in ('verify_email', 'reset_password')),
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);
```

Codes are hashed (SHA-256, same approach already used for passwords) rather
than stored in plaintext. RLS stays locked down / no anon policy — only the
Edge Functions (using the service-role key) ever read or write this table,
so the Android app never queries it directly.

## Edge Functions (Deno, Supabase)

Three small, single-purpose functions:

1. **`send-code`**
   - Input: `{ email, purpose }`
   - Looks up the user by email (for `reset_password`, must exist; for
     `verify_email`, must exist and be unverified).
   - Generates a 6-digit code, hashes it, inserts into `auth_codes` with a
     10-minute expiry, sends it via the Resend API.
   - Used for: initial verification send, "resend code," and "forgot
     password" requests.

2. **`verify-email`**
   - Input: `{ email, code }`
   - Finds the newest unused, unexpired `auth_codes` row for
     `(email, 'verify_email')`, compares hash, marks `used_at`, sets
     `app_users.email_verified = true`.

3. **`reset-password`**
   - Input: `{ email, code, newPassword }`
   - Same code check against `(email, 'reset_password')`, then hashes
     `newPassword` with a fresh salt and updates `app_users.password_hash` /
     `password_salt` — no separate reset token; the code itself is the
     credential for this one call.

All three return a simple `{ ok: boolean, error?: string }` shape. Secrets
(`RESEND_API_KEY`, the service-role key) are Edge Function secrets, set via
the Supabase dashboard/CLI — never present in the Android app.

## Android app changes

### Data layer

- `AppUserRow` gains `email: String?` and `emailVerified: Boolean` (mapped
  from `email_verified`).
- `AuthRepository`:
  - `signUp(username, email, password)` — validates email locally, inserts
    the user with `email_verified = false`, then calls `send-code` with
    `purpose = verify_email`.
  - `signIn` return type becomes a sealed result: `Success(LoggedInUser)` /
    `NeedsVerification(email)` / `Error(message)`. A correct
    username/password with `email_verified = false` returns
    `NeedsVerification` instead of logging in, so the UI can route straight
    to the verify screen.
  - New methods: `sendVerificationCode(email)`, `verifyEmailCode(email, code)`,
    `sendPasswordResetCode(email)`, `resetPassword(email, code, newPassword)`
    — each a thin wrapper invoking the corresponding Edge Function.
- Add the `functions-kt` module (same `supabase-kt` BOM already pinned) to
  invoke Edge Functions, instead of hand-rolling a Retrofit client for this.

### UI layer

- `SignUpScreen` gains an email field; on success it navigates to
  `VerifyEmailScreen(email)` instead of into the app.
- `LoginScreen` gains a "Forgot password?" text button navigating to
  `ForgotPasswordScreen`. When `signIn()` yields `NeedsVerification`, it
  navigates to `VerifyEmailScreen(email)` instead of showing an error.
- **`VerifyEmailScreen`** — code entry field, "Verify" button, "Resend code"
  with a client-side cooldown (e.g. 30s) to avoid burning through Resend's
  free tier, and a way back to login.
- **`ForgotPasswordScreen`** — email entry, "Send code" button, navigates to
  `ResetPasswordScreen(email)` on success.
- **`ResetPasswordScreen`** — code + new password + confirm-password fields,
  reusing the existing password validation rules from sign-up (min length,
  match check).
- New `VerifyEmailViewModel` and `ForgotPasswordViewModel`, kept separate
  from `AuthViewModel` rather than growing it further.
- New nav routes in `PulseNavGraph`: `verify_email/{email}`,
  `forgot_password`, `reset_password/{email}`.

## Error handling

- Expired code, wrong code, already-used code → plain-language error via the
  existing `ErrorBanner` component.
- Duplicate email at sign-up → friendly "email already registered" error
  (surfaced from the `unique` constraint).
- Resend cooldown enforced client-side (does not need server enforcement for
  a class project's scale).
- No rate limiting / attempt-limiting on code guesses beyond the 10-minute
  expiry — acceptable for this project's scope; noted here in case scope
  grows later.

## Testing

- Unit tests for the new `AuthRepository` methods against mocked Edge
  Function responses (success, expired code, wrong code, duplicate email).
- Edge Function logic can be sanity-checked with direct HTTP calls (e.g.
  curl) once deployed, before wiring up the UI.
- Actual email delivery and the full on-device flow (real inbox, real code)
  can only be verified end-to-end on a real device — the user tests this
  part themselves; implementation will not run `installDebug` or drive the
  UI automatically.

## Out of scope

- Rate limiting / brute-force protection on code attempts.
- Changing the login identifier from username to email.
- Deep-link / magic-link based flows.
- Deploying the SQL migration or Edge Functions to the live Supabase
  project — the plan produces the files and deployment instructions; the
  user runs them.
