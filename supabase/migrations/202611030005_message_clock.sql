begin;
-- Client clocks must not control conversation ordering or pagination boundaries.
create function public.hipka_stamp_message() returns trigger language plpgsql
set search_path = '' as $$
begin
  new.timestamp := (extract(epoch from clock_timestamp()) * 1000)::bigint;
  return new;
end $$;
revoke all on function public.hipka_stamp_message() from public;
create trigger hipka_message_clock before insert on public.hipka_messages
for each row execute function public.hipka_stamp_message();
commit;
