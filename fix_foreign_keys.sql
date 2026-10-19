-- Run this first to see every foreign key in your project that still points at
-- Supabase's own auth.users table (the one your removed Auth module used to manage).
-- Any table listed here will fail on insert now, the same way workout_logs just did.
select conname, conrelid::regclass as table_name
from pg_constraint
where confrelid = 'auth.users'::regclass;

-- Now drop the ones we know about from the app's tables. "if exists" makes this safe
-- to run even if a name doesn't match or a constraint isn't there.
alter table workout_logs drop constraint if exists workout_logs_user_id_fkey;
alter table meal_logs drop constraint if exists meal_logs_user_id_fkey;
alter table check_ins drop constraint if exists check_ins_user_id_fkey;
alter table profiles drop constraint if exists profiles_user_id_fkey;

-- Re-run the first query above after this -- it should come back empty. If it still
-- lists a table, copy its constraint name and drop it the same way:
-- alter table <table_name> drop constraint if exists <conname>;
