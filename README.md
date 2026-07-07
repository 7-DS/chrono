# chrono

chrono is an Android server-management client for SSH operations, terminal work, file transfers, tunnels, and live host monitoring.

It is designed as a mobile server cockpit rather than a simple SSH launcher: one host profile can include credentials, notes, tags, Wake-on-LAN, monitoring preferences, proxy-jump routing, terminal behavior, file-browser defaults, reconnect rules, and protocol-specific settings.

## Highlights

- Multi-protocol profiles: SSH, Mosh, Eternal Terminal, VNC, RDP, SMB, rclone, and local PRoot.
- Live server dashboard with reachability, latency, uptime, CPU, RAM, disk, network, process, systemd, container, GPU, SMART, sensor, battery, and Proxmox-oriented views.
- Terminal workspaces with themes, fonts, cursor styles, bracketed paste, custom accessory keys, scrollback, haptics, and foreground-session handling.
- File workflows across SFTP, SCP policy paths, SMB shares, and rclone remotes, including transfer history and rclone.conf import.
- Vault-backed password/private-key identities, known-host trust review, SSH key generation, public-key export, and duplicate identity guards.
- Port-forward management for local, remote, and dynamic SOCKS tunnels.
- Backup, share-link, QR, app-lock, and crash/diagnostic flows built into the app.

## Feature Overview

### Host Management

- Host groups, tags, favorites, OS metadata, accent colors, custom logos, notes, startup commands, start directories, environment variables, connect timeout, compression, and reconnect policy.
- Presets for Linux SSH, Mosh, Eternal Terminal, desktop VNC/RDP, SMB shares, rclone remotes, and local PRoot shells.
- Duplicate-host prevention based on normalized protocol, host, port, and username.
- OpenSSH config import support.
- Host share links and QR payloads that avoid exporting private credential material.

### Monitoring And Operations

- Home dashboard for live host status, top-level resource summaries, and long-list navigation.
- Server detail cards for uptime, CPU usage/load, system info, failed services, resources, filesystems, processes, systemd, network, containers, GPUs, Proxmox, battery, SMART disks, and sensors.
- Configurable metric-card order, hidden cards, ring color presets, disk display modes, and network total/rate display modes.
- Guarded runtime actions for processes, systemd services, and Docker/Podman containers.
- Linux metric collection for filesystems, vnStat traffic history, sensors, SMART disks, NVIDIA GPUs, package updates, Proxmox resources, battery state, services, processes, and containers.
- Wake-on-LAN support with MAC, broadcast, and SecureOn validation.

### Terminal

- SSH terminal sessions plus Mosh UDP roaming and Eternal Terminal persistence through SSH bootstrap.
- Multiple workspaces backed by an Android foreground service for active connections.
- Terminal theme and font catalogs, custom cursor style, bracketed paste, scrollback sizing, keep-screen-on, haptics, and terminal margins.
- Accessory key presets for compact, navigation, TUI, shell, control, and function-key workflows.
- Snippet rendering and command helpers for common server tasks.

### Files And Transfers

- SFTP browser with bookmarks, sorting, hidden-file defaults, directory navigation, text editing, chmod, mkdir, rename, delete, upload, download, and transfer history.
- Host-to-host transfer planning for compatible saved hosts.
- SCP path policy support for direct upload and download flows.
- SMB file browsing for password-backed SMB profiles.
- Embedded rclone profile support with rclone.conf import, encrypted config unlock, remote picker, root-path handling, transfer progress, and advanced rclone actions.
- Transfer state persistence for queued, running, complete, failed, and cancelled records.

### Vault And Trust

- Android-backed secret storage for password and private-key identities.
- Duplicate key/credential label prevention.
- Private-key inspection, passphrase handling, and in-app SSH key generation.
- Known-host capture, trust review, changed-key detection, and rejected-host handling.
- Public-key export/copy support without exposing private key payloads by default.
- Explicit confirmation before copying, exporting, or sharing secret material.

### Tunnels, Backup, And Security

- Local, remote, and dynamic SOCKS port-forward rules.
- Tunnel dashboard with active/stopped/failed state, favorites, grouping, route labels, cleaned-up user-facing errors, and one-tap start/stop.
- ProxyJump validation for chained SSH access.
- Backup export, inspect, and merge flows for hosts, credential metadata, known hosts, snippets, tunnels, SFTP bookmarks, and settings.
- Encrypted backup codec for protected exports.
- PIN app lock with optional biometric unlock and crash-recovery handling.

### Customization

- App theme families with light, dark, and system modes.
- Per-section heading font imports for Home, Connections, Files, Vault, and Settings.
- OS and distribution visual treatment for Linux, BSD, Windows, Proxmox-style hosts, and common distributions.
- Compose-based mobile UI with status-bar and keyboard-aware screens.

## Requirements

- Android Studio or command-line Android SDK.
- JDK 17.
- Android SDK Platform 35.
- Android Gradle Plugin 8.13.2.
- Android NDK/CMake for native terminal support.
- Git LFS for bundled native runtime libraries.

When building outside Android Studio, create `local.properties` with your SDK path:

```properties
sdk.dir=/path/to/android-sdk
```

## Build

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Releases

Debug builds are published from GitHub Releases when available:

https://github.com/7-DS/chrono/releases

## Repository Layout

```text
app/                         Android application source
app/libs/                    Prebuilt Java bridge libraries required by the app
app/src/main/jniLibs/        Native runtime libraries bundled with the app
third_party/et-transport/    Vendored Eternal Terminal transport source
third_party/ssp-transport/   Vendored SSP/Mosh transport source
```

## Security Notes

chrono stores credential payloads through Android-backed secret storage. Export and share flows intentionally avoid exporting private credential material unless the user explicitly requests a secret action inside the app.

## Credits

- Server and host-management features were informed by [ServerBox](https://github.com/lollipopkit/flutter_server_box).
- UI direction was inspired in part by [SwiftServer](https://swiftserver.app/) for iOS and macOS.
- `third_party/et-transport/` is based on [Eternal Terminal](https://github.com/MisterTea/EternalTerminal) and extracted from [Haven](https://github.com/GlassOnTin/Haven)'s Android SSH client work.
- `third_party/ssp-transport/` is based on the [Mosh](https://github.com/mobile-shell/mosh) SSP protocol and extracted from [Haven](https://github.com/GlassOnTin/Haven)'s Android SSH client work.

## License

This repository includes third-party transport code under `third_party/` with its own license files. Review bundled native and prebuilt library provenance before redistributing production builds.
