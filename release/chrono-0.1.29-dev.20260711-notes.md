chronoSSH 0.1.29-dev.20260711

- Replaced the CPU usage pill toggle with Cube, Dot, and Pill display modes. Cube is the default, and the legend marker now matches the selected mode.
- Kept server-card latency visible for every host with a compact `-- ms` placeholder until a probe has a measured latency.
- Changed uptime bars to use the SSHPeaches-style latest-known status at each half-hour bucket, so the 24h strip no longer looks sparse between checks.
- Removed the heavy full-screen surface transition that could make home, terminal, and other screen changes feel laggy.
