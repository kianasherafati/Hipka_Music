# Security boundaries

The default Audius/local-library mode has no account credentials. Music HTTP clients do not carry Supabase headers. Cleartext HTTP and application backup are disabled. Download filenames are hashed, written to temporary private files, checked for completeness, and renamed only after successful transfer. Downloads are bounded to 250 MiB.

The legacy password-query implementation and password DTO field have been removed. Supabase Auth now owns sign-in, sign-up, token refresh and sign-out. Encrypted SDK sessions are stored in the no-backup directory using Android Keystore AES-GCM. A local user ID is never accepted as authentication. Passwords/recovery codes are not saved in instance state.

Before a public authenticated release, deploy and test the additive migrations on staging with three users, inspect any existing permissive RLS/Realtime policies, and validate session restoration, revocation and blocking on real devices. Existing university identities and any old plaintext password data require a separate administrator-controlled migration; this client does not alter those tables. Review historical deployments and rotate previously exposed privileged keys where applicable. See [backend setup](SUPABASE_SETUP.md).

Do not embed service-role JWTs, `sb_secret_` keys, or database passwords. Public client keys are extractable from APKs and require RLS; see [Supabase API security](https://supabase.com/docs/guides/api/securing-your-api).

No production credentials or remote database mutations were used. SQL migrations are version-controlled but have not been deployed or integration-tested.

Ktor is aligned to 2.3.13, the patched 2.x version for [CVE-2024-49580](https://github.com/advisories/GHSA-8qv4-773j-c979). The affected HttpCache plugin was not explicitly installed by this app; this update removes the affected dependency version. This is a targeted advisory review, not a claim that every transitive dependency is vulnerability-free.
