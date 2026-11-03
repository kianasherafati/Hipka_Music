begin;
alter table public.hipka_playlists add column snapshot jsonb not null default '[]'::jsonb;
alter table public.hipka_playlists add column deleted boolean not null default false;
-- All writes use CAS, so REST clients cannot bypass revision checks.
revoke insert, update, delete on public.hipka_playlists, public.hipka_playlist_tracks from authenticated;
create function public.hipka_save_playlist(p_id uuid, p_name text, p_description text,
  p_snapshot jsonb, p_revision bigint, p_deleted boolean) returns bigint
language plpgsql security definer set search_path = '' as $$
declare current_revision bigint; current_owner uuid; next_revision bigint;
begin
  if auth.uid() is null then raise insufficient_privilege; end if;
  if jsonb_typeof(p_snapshot) <> 'array' or jsonb_array_length(p_snapshot) > 1000 then
    raise exception 'Invalid playlist snapshot';
  end if;
  if exists (select 1 from jsonb_array_elements(p_snapshot) item
    where coalesce(item->>'id','') !~ '^audius:[A-Za-z0-9]+$') then raise exception 'Invalid provider ID'; end if;
  -- Serialize even the initial insert for a supplied UUID.
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_id::text, 0));
  select revision, owner_id into current_revision, current_owner from public.hipka_playlists where id=p_id for update;
  if found then
    if current_owner <> auth.uid() then raise insufficient_privilege; end if;
    if current_revision <> p_revision then raise sqlstate 'PT409' using message='Playlist changed on another device'; end if;
    next_revision := current_revision + 1;
    update public.hipka_playlists set name=p_name, description=p_description, snapshot=p_snapshot,
      revision=next_revision, deleted=p_deleted, updated_at=now() where id=p_id;
  else
    if p_revision <> 0 then raise sqlstate 'PT409' using message='Playlist revision no longer exists'; end if;
    next_revision := 1;
    insert into public.hipka_playlists(id,owner_id,name,description,snapshot,deleted,revision)
      values(p_id,auth.uid(),p_name,p_description,p_snapshot,p_deleted,next_revision);
  end if;
  delete from public.hipka_playlist_tracks where playlist_id=p_id;
  insert into public.hipka_playlist_tracks(playlist_id,track_id,position)
    select p_id,item->>'id',(ordinality-1)::integer from jsonb_array_elements(p_snapshot) with ordinality as entries(item,ordinality);
  return next_revision;
end $$;
revoke all on function public.hipka_save_playlist(uuid,text,text,jsonb,bigint,boolean) from public;
grant execute on function public.hipka_save_playlist(uuid,text,text,jsonb,bigint,boolean) to authenticated;
commit;
