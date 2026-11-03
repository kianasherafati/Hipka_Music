# Hipka 2.0 preview backend setup

The Android integration is implemented, but no Supabase project was deployed or
multi-user integration-tested during this implementation. Use a disposable staging
project before configuring production. Audius playback and local playlists need no
Supabase account.

1. Create a Supabase project. Review and apply all SQL files in
   `supabase/migrations` in filename order. They create new `hipka_*` tables and do
   not copy or delete old university tables. Do not apply them blindly to production.
   Existing Auth users need an administrator-reviewed profile backfill; the signup
   trigger only creates profiles for new users.
2. Enable email/password Auth, configure SMTP, verification and redirect URLs. Set
   the password-reset email template to include `{{ .Token }}`: the Android recovery
   screen verifies the recovery code before changing the password. Confirmation
   emails may use the hosted confirmation URL; after verification, return to the app
   and sign in. The app does not implement an OAuth/deep-link callback.
3. Set `supabase.url` and `supabase.key` in untracked `local.properties`, or use
   `SUPABASE_URL` and `SUPABASE_KEY` environment variables. Use only the public anon
   or publishable key. Never embed a service-role key or `sb_secret_` key.
4. For GitHub APK builds, configure repository variables `SUPABASE_URL` and
   `SUPABASE_KEY` in the personal fork. Missing variables produce a guest-capable APK
   with account configuration unavailable; no production credentials are invented.
5. Confirm `hipka_messages` is in the Realtime publication. Private-channel policies
   authorize only the two UUIDs in `hipka:<id>:<id>` and
   `hipka-presence:<id>:<id>`. Review any existing broad Realtime policies: PostgreSQL
   permissive policies combine with OR, so a pre-existing permissive policy can
   undermine a narrower one. Configure private-only Realtime access for this app.
6. Test the acceptance checklist below with three disposable accounts on two devices.
   Keep the project isolated until the database policies and client behavior pass.

## Data and security model

- Profiles reference `auth.users`, have case-normalized unique usernames, and do not
  contain passwords. Clients may edit only their name, bio and avatar URL; premium
  state is not client-writable. Avatar editing supports the Android photo picker or an HTTPS URL. Picked images are bounded, resized and re-encoded as JPEG without EXIF metadata; the avatar bucket is public, with owner-only uploads and updates.
- Supabase Auth owns token refresh and restoration. Its SessionManager persists
  AES-GCM ciphertext in Android's no-backup directory using a non-exportable
  Android Keystore key. Legacy DataStore IDs never prove authentication.
- Retrofit sends the current Auth access token; Audius has a separate client and
  never receives Supabase credentials. All meaningful authorization uses RLS.
- Messages use a stable UUID and participant IDs. The receiver may update only
  `status`; neither participant can rewrite a message or spoof the sender. Only the
  participants can select a message. Blocking prevents new messages/follows and new
  private-channel joins. Presence means active in this conversation, not global
  account availability. Existing channels must reconnect after policy changes.
- Playlist writes use an ownership-checked, revision-based RPC. Direct client table
  writes are revoked. Edits/deletions persist locally before sync; conflicts preserve
  a copy instead of silently replacing a local edit. Guest playlists remain local
  and visible after login; account playlists are filtered by authenticated owner.
- Sync runs when Playlists is opened, connectivity returns while its ViewModel is
  alive, after edits, or on manual Sync. It is not a background WorkManager sync job.
- Song snapshots retain Audius IDs. Local download paths are removed before upload
  and incoming audio URLs are normalized to the provider. Existing Room schemas and
  downloaded files are unchanged.

## Required staging acceptance

- Register A and B with distinct usernames; duplicate and invalid usernames fail.
  Verify emails if enabled. Sign in, restart, expire/revoke a session, reconnect,
  reset a password by recovery code and sign out. Verify no plaintext credentials
  exist in app storage or logs. Confirm guest music still works offline.
- A searches B, visits the profile, follows B; B sees the follower. Edit name, bio,
  avatar URL, and verify an unrelated JWT cannot edit them or grant premium.
- A sends B a message and a real Audius song. B receives it, plays the shared song,
  sees typing expire and read state update. Switch apps, lose connectivity, reconnect,
  and verify presence clears and the same message UUID is not duplicated.
- With C's JWT, selecting A/B messages returns no rows. Inserting a message with
  A's sender ID under C's JWT fails. Updating message text, receiver or timestamp
  fails. Block A/B and confirm new sends/follows fail.
- Create local playlists as guest, sign in, create account playlists, restart, and
  synchronize on another device. Edit the same playlist offline on both devices;
  verify a conflict copy survives. Remove/reorder/delete and verify convergence.
- Verify that SQL migrations fail safely in a transaction if incompatible existing
  objects or publication settings exist. No live migration execution is claimed.

## Known release gaps

Avatar uploads, public playlist publication/profile listing and keyset chat history pagination are implemented but not backend-tested. Complete Persian translation of status/error copy and artist public-playlist discovery remain follow-up work. The chat inbox currently uses the
latest 200 messages, so its unread counts are bounded to that window. This is a
preview, not a claim that all 2.0 acceptance criteria are satisfied.

## Resumed implementation (2026-11-03)

Apply migrations 003 (avatar bucket and ownership), 004 (public playlist RPC), and
005 (server message timestamps) after the original two migrations. Public playlists
are visible to signed-in Hipka users; guest music still requires no account. Changing
visibility is an offline edit until sync succeeds. Photo-picker uploads require a
configured `hipka-avatars` bucket; they do not bypass Storage authorization.

Conversation history loads older messages using a timestamp/UUID keyset cursor.
The server assigns timestamps; device clocks cannot force message ordering.
The inbox remains a bounded recent-message view (200 messages), not an authoritative
all-time unread-count service. Live conversation presence is intentionally scoped to
participants currently viewing that conversation.

`supabase/tests/rls.test.sql` contains transactional pgTAP tests for participant
privacy, spoofing, receiver update restrictions, blocking and playlist revisions.
Run `supabase test db` in an isolated local/staging stack after migrations. These
SQL tests have not been run in this environment; Android unit tests are not a
substitute for them. Encrypted storage instrumentation uses synthetic tokens and a
separate test directory, not production Auth credentials.
