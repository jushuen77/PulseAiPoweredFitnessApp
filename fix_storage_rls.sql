-- Your meal-photos bucket's storage policies likely only allow the "authenticated" role
-- (Supabase Auth's JWT role), which no longer exists now that the app uses only the anon
-- key. This adds equivalent policies for the anon role, scoped ONLY to meal-photos so no
-- other bucket is affected.

create policy "anon can upload to meal-photos"
on storage.objects for insert
to anon
with check (bucket_id = 'meal-photos');

create policy "anon can read meal-photos"
on storage.objects for select
to anon
using (bucket_id = 'meal-photos');

create policy "anon can delete meal-photos"
on storage.objects for delete
to anon
using (bucket_id = 'meal-photos');

-- To see what policies already exist on storage.objects (including old ones tied to
-- "authenticated"/auth.uid() that you may want to remove once anon access works):
select policyname, roles, cmd, qual
from pg_policies
where schemaname = 'storage' and tablename = 'objects';
