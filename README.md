# Hipka Music

<img src="app/src/main/ic_launcher-playstore.png" alt="Hipka Music logo" width="96" />

A Kotlin / Jetpack Compose music app with a wine-and-navy visual identity, English and Persian interfaces, and music discovery powered by **Audius**.

Originally a university team project, now being maintained as a personal portfolio project. Original contributions by **Parand Khalili, Hasti Kabirsamadi, and Kiana** remain in Git history.

## Music without hosting a catalog

The default app browses trending tracks, searches, and streams music from the [Audius public API](https://docs.audius.co/api/). No Supabase music uploads, paid subscription, or API secret is needed. Most read endpoints support unauthenticated access; quotas, availability, and catalog contents are controlled by Audius. This is an independent-artist catalog, not a promise of every commercial release.

Only public, streamable, ungated tracks are listed. Download requests recheck the provider's download permission and use its download endpoint. Streaming permission does not imply permission to download or redistribute music. Artist names and artwork are retained.

## Implemented functionality

- Trending discovery, search, artist browsing, and complete playback queues.
- Media3 background playback and media notifications; audio focus and headphone-disconnect handling.
- Shuffle, repeat, seek, playback speed, and sleep timer.
- Local favorites, recent listening, search history, and cached catalog metadata through Room.
- WorkManager downloads for provider-permitted tracks; private storage and offline playback.
- Material 3 light/dark themes, English/Persian localization, and RTL layouts.
- A local library that works without signing in.

## 2.0 preview implementation

- Five distinct tabs: Home, Search, Library, Playlists and Chats; Profile opens from the Home avatar.
- Supabase Auth email registration/login, verification messaging, recovery-code password reset and encrypted persistent sessions. Legacy password queries are removed.
- Profile editing (name, bio, photo-picker avatar upload or HTTPS URL), user search, follows, blocking, paginated conversations, shared songs, read status, private typing events and conversation presence.
- Local playlist create/edit/delete, track add/remove/reorder, playback/shuffle and text sharing. Account playlists use revision-checked cloud synchronization with preserved conflict copies and public profile publication.
- Stable Audius artist IDs and user-search endpoints, provider-metadata download filtering, track action menus and deterministic Hipka Vibes mixes. Editorial mixes are generated from real catalog tracks, with an explicit trending fallback.

**This is a preview, not a completed production 2.0 release.** Account features require deployment of the supplied Supabase migrations and configuration. No live multi-user Auth, chat, RLS or sync test is claimed. Remaining acceptance work are in [implementation progress](docs/IMPLEMENTATION_PROGRESS.md).

## Build

Requirements: JDK 17 or newer (tested setup uses Android Studio's bundled JDK), Android SDK Platform 35 and Build Tools, and network access for Gradle dependencies. Minimum device version: Android 7 / API 24.

```sh
git clone https://github.com/kianasherafati/Hipka_Music.git
cd Hipka_Music
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows use `gradlew.bat`. Open the project in Android Studio to configure the SDK, or set `ANDROID_HOME`. The default Audius app requires no `local.properties` credentials. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Official Google/Maven repositories are the defaults. On restricted networks, `HIPKA_MAVEN_MIRROR` can specify an explicitly trusted HTTPS Maven mirror. Do not commit machine-specific repository or SDK paths.

## Supabase setup

Follow [Supabase setup and staging acceptance](docs/SUPABASE_SETUP.md). The version-controlled migrations create isolated social tables, RLS policies, private Realtime authorization and a revision-checked playlist RPC. No migration has been executed against a remote database. Never put service-role or secret keys in an APK.

The default build works as a guest without Supabase configuration. For account features supply the public project URL/key through untracked local properties, environment variables, or the personal fork's Actions repository variables. Old local user IDs are ignored for authentication; existing Room music/download data is preserved.

## Architecture

```text
Compose screens → ViewModels / StateFlow → repository interfaces
                                          ├─ Audius API (catalog)
                                          ├─ Room (library/cache)
                                          ├─ MediaController → MediaSessionService / ExoPlayer
                                          └─ WorkManager → download worker / private files
```

Hilt wires dependencies. Retrofit/OkHttp and Kotlin serialization handle HTTP; the Audius client is separate from the authenticated Supabase client so backend credentials are never attached to music-provider requests. Settings and local playlists use DataStore; Auth sessions use encrypted Android Keystore-backed SDK storage.

Source folders: `presentation` (screens/navigation/state), `domain` (models/contracts), `data` (repositories/API/Room/player/downloads), `di` (dependency wiring), and `service` (background playback).

## Validation and limitations

See [verification notes](docs/VERIFICATION.md) for executed checks and remaining manual scenarios. Unit tests cover provider response parsing, gated-track filtering, stable stream URLs, malformed IDs, coroutine cancellation, playlist order/account isolation, cloud snapshot normalization, and deterministic Vibes personalization. Android navigation and encrypted-storage tests are compiled; their execution remains unverified. GitHub Actions builds, tests, and lints each push.

There are no fabricated screenshots or test badges. Device screenshots should be added only after capturing a running, verified build. Backend integration, process-death playback restoration, provider quotas, and real-device audio routing need further validation. No license grant is inferred for third-party music or original team assets.
