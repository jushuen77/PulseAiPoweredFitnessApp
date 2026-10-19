-- Persists AI Assistant chat history per user (see ai assistant "add chat history" change).
create table if not exists chat_messages (
  id bigint generated always as identity primary key,
  user_id uuid not null,
  role text not null,
  content text not null,
  created_at timestamptz not null default now()
);

create index if not exists chat_messages_user_id_created_at_idx
  on chat_messages (user_id, created_at);

-- Same custom-table-login trade-off as the other data tables (see supabase_auth_migration.sql):
-- the app has no Supabase Auth session, so RLS is disabled and the app filters by user_id itself.
alter table chat_messages disable row level security;
