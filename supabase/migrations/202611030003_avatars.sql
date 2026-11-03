begin;
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('hipka-avatars','hipka-avatars',true,2097152,array['image/jpeg']);
create policy hipka_avatar_insert on storage.objects for insert to authenticated
with check(bucket_id='hipka-avatars' and (storage.foldername(name))[1]=auth.uid()::text);
create policy hipka_avatar_update on storage.objects for update to authenticated
using(bucket_id='hipka-avatars' and (storage.foldername(name))[1]=auth.uid()::text)
with check(bucket_id='hipka-avatars' and (storage.foldername(name))[1]=auth.uid()::text);
create policy hipka_avatar_owner_read on storage.objects for select to authenticated
using(bucket_id='hipka-avatars' and (storage.foldername(name))[1]=auth.uid()::text);
create policy hipka_avatar_delete on storage.objects for delete to authenticated
using(bucket_id='hipka-avatars' and (storage.foldername(name))[1]=auth.uid()::text);
commit;
