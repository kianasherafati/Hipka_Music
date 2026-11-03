-- Run with `supabase test db` against a disposable local Supabase stack.
-- These are real database authorization tests, not Android mocks. Not executed here.
begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select plan(10);

insert into auth.users(id, email, raw_user_meta_data) values
('11111111-1111-4111-8111-111111111111','hipka-test-a@example.invalid','{"username":"hipka_test_a","name":"A"}'),
('22222222-2222-4222-8222-222222222222','hipka-test-b@example.invalid','{"username":"hipka_test_b","name":"B"}'),
('33333333-3333-4333-8333-333333333333','hipka-test-c@example.invalid','{"username":"hipka_test_c","name":"C"}');

set local role authenticated;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
select lives_ok($$insert into public.hipka_messages(id,sender_id,receiver_id,text) values
('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','private test')$$,'A can message B');
select is((select count(*) from public.hipka_messages),1::bigint,'A can read own conversation');

select set_config('request.jwt.claim.sub','33333333-3333-4333-8333-333333333333',true);
select is((select count(*) from public.hipka_messages),0::bigint,'C cannot read A/B conversation');
select throws_ok($$insert into public.hipka_messages(sender_id,receiver_id,text) values
('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','spoof')$$,
'42501',null,'C cannot spoof A');

select set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
select throws_ok($$update public.hipka_messages set text='rewrite' where id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$$,
'42501',null,'Receiver cannot rewrite text');
select lives_ok($$update public.hipka_messages set status='READ' where id='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$$,'Receiver can acknowledge read');
select lives_ok($$insert into public.hipka_blocks values('22222222-2222-4222-8222-222222222222','11111111-1111-4111-8111-111111111111')$$,'B can block A');

select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
select throws_ok($$insert into public.hipka_messages(sender_id,receiver_id,text) values
('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','blocked')$$,
'42501',null,'Blocked message is rejected');
select is(public.hipka_save_playlist('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','Test','','[]',0,false),1::bigint,'Initial playlist revision is one');
select throws_ok($$select public.hipka_save_playlist('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','Overwrite','','[]',0,false)$$,
'PT409',null,'Stale playlist revision cannot overwrite newer data');
reset role;
select * from finish();
rollback;
