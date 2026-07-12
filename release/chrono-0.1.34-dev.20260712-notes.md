Chrono 0.1.34-dev.20260712

- Password identities no longer require a unique name — saving a password no longer fails with "identity name already exists". Passwords can share a name (host/login target) freely.
- Name uniqueness now applies to keys only: two keys still can't share a name, but a key and a password may.
