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
