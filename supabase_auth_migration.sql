-- 1) The new table your lecturer wants: stores username + hashed password + uid.
create extension if not exists pgcrypto;

create table if not exists app_users (
  uid uuid primary key default gen_random_uuid(),
  username text unique not null,
  password_hash text not null,
  password_salt text not null,
  created_at timestamptz not null default now()
);

-- The app talks to Supabase with only the anon key (no Supabase Auth session anymore),
-- so app_users needs to be reachable by the anon role directly.
alter table app_users enable row level security;

create policy "anon can read/write app_users"
  on app_users
  for all
  to anon
  using (true)
  with check (true);

-- 2) Your other tables likely have RLS policies that check Supabase's own auth.uid().
-- That no longer exists, so those policies would silently block everything. This disables
-- RLS on them entirely -- the app already filters every query by user_id itself, but
-- disabling RLS means anyone holding your anon key could read/write ANY user's rows
-- directly against the REST API, bypassing the app. That's a real trade-off, standard for
-- a class project doing a custom-table login, but worth knowing.
alter table workout_logs disable row level security;
alter table meal_logs disable row level security;
alter table check_ins disable row level security;
alter table profiles disable row level security;

-- 3) If meal photo upload/viewing breaks after this, check Storage > Policies for the
-- "meal-photos" bucket in the dashboard -- if its policies also reference auth.uid(),
-- relax them the same way (e.g. allow the anon role for that bucket).
