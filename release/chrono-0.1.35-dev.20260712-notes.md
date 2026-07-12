Chrono 0.1.35-dev.20260712

Fixes a backup export/import regression:

- Importing a backup on the same device no longer strands saved credentials.
  Backups intentionally exclude secret material, so importing used to overwrite
  the live secret reference with a placeholder — after a restart the app could
  not decrypt any password/key, so metrics, terminal, and connections all
  stopped working even though the identities still appeared in Edit Host.
  Import now keeps the existing secret and passphrase references when a
  credential with the same id is already present.
- Importing no longer spams host-key trust prompts. Host keys you already trust
  (same host/port and identical fingerprint) keep their trust across an import
  instead of being reset to Unknown.

Note: an install already broken by a prior import must re-enter its saved
passwords/keys once; the stored secret files could not be re-linked
automatically because the reference was lost on the previous import.
