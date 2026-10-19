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
