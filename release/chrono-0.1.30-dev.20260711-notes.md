# Chrono 0.1.30-dev.20260711

- Kept terminal chrome visible while sessions are opening, reviewing host keys, failing, or disconnecting.
- Routed terminal close through the disconnect flow so the screen does not abruptly disappear before cleanup.
- Removed animated bottom-tab transitions and the scroll-driven compact home bar to reduce home/navigation jank.
- Changed the Android launch window to black instead of white/transparent.
- Added a Monitoring setting to show or hide server-card latency.
- Flattened backup action buttons so they use plain card styling instead of accent glow styling.
