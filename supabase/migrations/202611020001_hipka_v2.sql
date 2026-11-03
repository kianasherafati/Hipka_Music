-- Additive, isolated tables. Run on a staging Supabase project first.
begin;
create table public.hipka_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique check (username ~ '^[a-z0-9_]{3,30}$'),
  name text not null check (length(name) between 1 and 100),
  bio text not null default '' check (length(bio) <= 1000),
  avatar_url text,
  is_premium boolean not null default false,
  created_at timestamptz not null default now()
);
create table public.hipka_follows (
  follower_id uuid references public.hipka_profiles(id) on delete cascade,
  following_id uuid references public.hipka_profiles(id) on delete cascade,
  primary key (follower_id, following_id), check (follower_id <> following_id)
);
create index hipka_follows_target on public.hipka_follows(following_id);
create table public.hipka_blocks (
  blocker_id uuid references public.hipka_profiles(id) on delete cascade,
  blocked_id uuid references public.hipka_profiles(id) on delete cascade,
  primary key (blocker_id, blocked_id), check (blocker_id <> blocked_id)
);
create table public.hipka_messages (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references public.hipka_profiles(id) on delete cascade,
  receiver_id uuid not null references public.hipka_profiles(id) on delete cascade,
  text text not null default '' check (length(text) <= 10000),
  shared_song_id text check (shared_song_id ~ '^audius:[A-Za-z0-9]+$'),
  status text not null default 'SENT' check (status in ('SENT','READ')),
  timestamp bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint,
  check (sender_id <> receiver_id), check (length(trim(text)) > 0 or shared_song_id is not null)
);
create index hipka_messages_inbox on public.hipka_messages(receiver_id, timestamp desc);
create index hipka_messages_pair on public.hipka_messages(sender_id, receiver_id, timestamp desc);
create table public.hipka_playlists (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null default auth.uid() references public.hipka_profiles(id) on delete cascade,
  name text not null check (length(name) between 1 and 100),
  description text not null default '' check (length(description) <= 1000),
  artwork_url text, is_public boolean not null default false,
  revision bigint not null default 1, updated_at timestamptz not null default now()
);
create index hipka_playlists_owner on public.hipka_playlists(owner_id);
create table public.hipka_playlist_tracks (
  playlist_id uuid references public.hipka_playlists(id) on delete cascade,
  track_id text check (track_id ~ '^audius:[A-Za-z0-9]+$'),
  position integer not null check (position >= 0),
  primary key(playlist_id, track_id), unique(playlist_id, position) deferrable initially deferred
);

create function public.hipka_create_profile() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.hipka_profiles(id, username, name)
  values(new.id, lower(new.raw_user_meta_data->>'username'), new.raw_user_meta_data->>'name');
  return new;
end $$;
revoke all on function public.hipka_create_profile() from public;
create trigger hipka_auth_profile after insert on auth.users for each row execute function public.hipka_create_profile();

create function public.hipka_can_contact(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select auth.uid() in (a,b) and a <> b and not exists (
    select 1 from public.hipka_blocks where (blocker_id=a and blocked_id=b) or (blocker_id=b and blocked_id=a)
  );
$$;
revoke all on function public.hipka_can_contact(uuid,uuid) from public;
grant execute on function public.hipka_can_contact(uuid,uuid) to authenticated;

alter table public.hipka_profiles enable row level security;
alter table public.hipka_follows enable row level security;
alter table public.hipka_blocks enable row level security;
alter table public.hipka_messages enable row level security;
alter table public.hipka_playlists enable row level security;
alter table public.hipka_playlist_tracks enable row level security;

revoke all on public.hipka_profiles, public.hipka_follows, public.hipka_blocks, public.hipka_messages,
  public.hipka_playlists, public.hipka_playlist_tracks from anon, authenticated;
grant select on public.hipka_profiles to authenticated;
grant update(name, bio, avatar_url) on public.hipka_profiles to authenticated;
grant select, insert, delete on public.hipka_follows, public.hipka_blocks to authenticated;
grant select, insert on public.hipka_messages to authenticated;
grant update(status) on public.hipka_messages to authenticated;
grant select, insert, update, delete on public.hipka_playlists, public.hipka_playlist_tracks to authenticated;

create policy profiles_read on public.hipka_profiles for select to authenticated using (true);
create policy profiles_own_update on public.hipka_profiles for update to authenticated using (id=auth.uid()) with check(id=auth.uid());
create policy follows_read on public.hipka_follows for select to authenticated using (true);
create policy follows_insert on public.hipka_follows for insert to authenticated with check(follower_id=auth.uid() and public.hipka_can_contact(follower_id,following_id));
create policy follows_delete on public.hipka_follows for delete to authenticated using(follower_id=auth.uid());
create policy blocks_read on public.hipka_blocks for select to authenticated using(blocker_id=auth.uid());
create policy blocks_insert on public.hipka_blocks for insert to authenticated with check(blocker_id=auth.uid());
create policy blocks_delete on public.hipka_blocks for delete to authenticated using(blocker_id=auth.uid());
create policy messages_participants on public.hipka_messages for select to authenticated using(auth.uid() in (sender_id,receiver_id));
create policy messages_send on public.hipka_messages for insert to authenticated with check(sender_id=auth.uid() and status='SENT' and public.hipka_can_contact(sender_id,receiver_id));
create policy messages_read_receipt on public.hipka_messages for update to authenticated using(receiver_id=auth.uid()) with check(receiver_id=auth.uid() and status='READ');
create policy playlists_read on public.hipka_playlists for select to authenticated using(owner_id=auth.uid() or is_public);
create policy playlists_insert on public.hipka_playlists for insert to authenticated with check(owner_id=auth.uid());
create policy playlists_update on public.hipka_playlists for update to authenticated using(owner_id=auth.uid()) with check(owner_id=auth.uid());
create policy playlists_delete on public.hipka_playlists for delete to authenticated using(owner_id=auth.uid());
create policy tracks_read on public.hipka_playlist_tracks for select to authenticated using(exists(select 1 from public.hipka_playlists p where p.id=playlist_id and (p.owner_id=auth.uid() or p.is_public)));
create policy tracks_owner on public.hipka_playlist_tracks for all to authenticated using(exists(select 1 from public.hipka_playlists p where p.id=playlist_id and p.owner_id=auth.uid())) with check(exists(select 1 from public.hipka_playlists p where p.id=playlist_id and p.owner_id=auth.uid()));

-- Private typing/presence topics are hipka:<sorted participant uuid>:<uuid>.
create function public.hipka_realtime_allowed(topic text) returns boolean
language plpgsql stable security invoker set search_path = '' as $$
declare parts text[];
begin
  parts := string_to_array(topic, ':');
  if array_length(parts,1) <> 3 or parts[1] not in ('hipka', 'hipka-presence') then return false; end if;
  return public.hipka_can_contact(parts[2]::uuid, parts[3]::uuid);
exception when invalid_text_representation then return false;
end $$;
revoke all on function public.hipka_realtime_allowed(text) from public;
grant execute on function public.hipka_realtime_allowed(text) to authenticated;
create policy hipka_private_receive on realtime.messages for select to authenticated
  using (extension in ('broadcast','presence') and public.hipka_realtime_allowed(realtime.topic()));
create policy hipka_private_send on realtime.messages for insert to authenticated
  with check (extension in ('broadcast','presence') and public.hipka_realtime_allowed(realtime.topic()));
alter publication supabase_realtime add table public.hipka_messages;
commit;
