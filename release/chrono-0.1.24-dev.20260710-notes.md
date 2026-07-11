# chrono 0.1.24-dev.20260710

- Fixes the foreground-service startup path that could crash with `ForegroundServiceDidNotStartInTimeException`.
- Keeps active terminal sessions attached to a sticky foreground service after normal recents/task removal.
- Reworks all theme metric accents with hard-coded, visually distinct CPU, memory, disk, network, and latency colors.
- Adds explicit CPU Usage colors for user, system, nice, I/O wait, and steal segments across every theme.
- Reduces oversized CPU/chip metric heading glyphs so they align with surrounding row text and icons.
