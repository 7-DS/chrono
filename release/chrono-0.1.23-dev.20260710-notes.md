# chrono 0.1.23-dev.20260710

- Bumped the app version above 0.1.22 so this release installs as a newer build.
- Reworked every catalog theme with hard-coded metric accent colors for CPU, memory, disk, network, and latency.
- Removed metric accent post-processing so server cards and detail pages use the authored theme colors directly.
- Added tests that guard metric card visibility and server-detail color separation across all palettes.
- Reduced the CPU/chip glyph footprint so it aligns with surrounding row text and icons.
