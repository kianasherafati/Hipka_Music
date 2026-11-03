# Hipka 2.0 implementation checkpoint

Updated 2026-11-03. Resume the existing workspace; never reset or clone over it.
Target: `kianasherafati/Hipka_Music` only. Original upstream is read-only.
The interrupted working tree was archived to the ignored local checkpoint
`.local-checkpoints/hipka-v2-interrupted-20261103.zip`, including its base commit
and binary diff. No prior changes were discarded.

## Implemented

- Five separate tabs and profile/avatar routing, guest access, retained mini-player.
- Supabase Auth, encrypted SDK sessions, recovery-code reset; plaintext password
  PostgREST methods removed. Session refresh remains owned by the SDK.
- Local and account playlists, track menus, revision-based cloud sync, conflict
  copies, owner isolation, public playlist publication and profile playback.
- Social profiles, photo-picker avatar uploads with bounded JPEG conversion,
  user search/follows/blocking, shared Audius tracks, participant-only messages,
  private typing/presence, keyset history pagination and foreground read handling.
- Stable Audius artist IDs, artist search, metadata-based download filter,
  deterministic Hipka Vibes and editorial mixes.
- Five additive SQL migrations, staging setup instructions, pgTAP authorization
  tests, JVM regression tests and Android navigation/Keystore instrumentation.
- English/Persian resources for navigation and new primary controls.

## Security review changes during resume

Account playlist requests capture the originating Auth JWT so an account switch
cannot attach another user's token to queued work. RLS uses server identity;
private channels authorize only their pair of participants. Public playlist
snapshot URLs are normalized and local download paths are never imported from
cloud data. Storage writes are scoped to the owner's folder. Server triggers own
message timestamps. No migrations have been deployed to a remote project.

## Verification state

Final source validation and release evidence are recorded in [VERIFICATION.md](VERIFICATION.md).
The interrupted and resumed successful build logs remain locally preserved. The
headless emulator booted and installed the APK, but Android displayed a system
process ANR. No navigation, Keystore instrumentation, audible playback or physical
phone smoke-test pass is claimed. Further emulator troubleshooting was explicitly
skipped. The attempted connected Gradle test also lacked an offline UTP dependency.
Backend integration and SQL authorization tests require an isolated Supabase stack.

## Remaining release limitations

- Supabase staging deployment and real A/B/C tests are required for Auth,
  verification/reset, revocation, RLS, Storage, realtime and multi-device sync.
- Inbox unread counts cover the newest 200 messages, not an all-time server summary.
- Artist public playlists and broad catalog pagination are not implemented.
- Some error/status messages remain English; RTL/device screenshot QA is pending.
- Account sync runs while the Playlists ViewModel is alive or on manual sync,
  not as a background WorkManager synchronization service.
- Existing pre-v3 Room destructive fallback and playback queue restoration after
  process death remain pre-existing limitations. Current Room/download schemas
  are unchanged.

## Remaining acceptance work

1. Deploy and integration-test the backend in staging using SUPABASE_SETUP.md.
2. Execute the compiled Android navigation and Keystore tests on a stable device.
3. Complete the real-device playback, downloads, RTL and accessibility checklist
   in VERIFICATION.md. These gaps do not block publishing the Android preview.
