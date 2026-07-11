# chrono 0.1.25-dev.20260711

- Reworks the uptime page to use collected up/down samples instead of deriving uptime bars from host uptime text.
- Adds a global uptime background-monitoring toggle in Settings and on the Uptime page.
- Adds per-host uptime monitoring toggles on the Uptime page.
- Adds a dedicated foreground service for background uptime checks that starts foreground immediately.
- Records TCP reachability checks into persisted uptime history so uptime charts survive app restarts.
- Keeps uptime checks credential-free; SSH credentials are not required just to verify host reachability.
