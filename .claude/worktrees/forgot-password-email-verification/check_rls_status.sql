select relname as table_name, relrowsecurity as rls_enabled
from pg_class
where relname in ('workout_logs', 'meal_logs', 'check_ins', 'profiles', 'app_users');
