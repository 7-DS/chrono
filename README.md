<h1 align="center" aria-label="chrono">𝖈𝖍𝖗𝖔𝖓𝖔</h1>

<p align="center">
  <img src="docs/assets/chronossh.png" width="120" alt="chronoSSH logo" />
</p>

<p align="center">
  Android server management for SSH operations, terminal work, file transfers, tunnels, and live host monitoring.
</p>

<p align="center">
  <a href="https://github.com/7-DS/chrono/releases/latest"><img src="https://img.shields.io/github/v/release/7-DS/chrono?style=flat-square&label=release&sort=date" alt="Latest release" /></a>
  <a href="https://github.com/7-DS/chrono/releases"><img src="https://img.shields.io/github/downloads/7-DS/chrono/total?style=flat-square&label=downloads" alt="Release downloads" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+" />
  <img src="https://img.shields.io/badge/Jetpack-Compose-4285f4?style=flat-square" alt="Jetpack Compose" />
</p>

<p align="center">
  <a href="https://github.com/7-DS/chrono/releases/latest">Download</a>
  &bull;
  <a href="#features">Features</a>
  &bull;
  <a href="#build-from-source">Build</a>
  &bull;
  <a href="#credits">Credits</a>
</p>

chrono is designed as a mobile server cockpit rather than a simple SSH launcher. A single host profile can include credentials, notes, tags, Wake-on-LAN, monitoring preferences, proxy-jump routing, terminal behavior, file-browser defaults, reconnect rules, and protocol-specific settings.

## At a Glance

| Area | Included |
| --- | --- |
| Connections | SSH, Mosh, Eternal Terminal, VNC, RDP, SMB, rclone, and local PRoot profiles |
| Monitoring | Reachability, latency, uptime, CPU, RAM, disk, network, process, systemd, containers, GPU, SMART, sensors, battery, and Proxmox-oriented views |
| Terminal | Workspaces, themes, fonts, cursor styles, bracketed paste, accessory keys, scrollback, haptics, and foreground-session handling |
| Files | SFTP, SCP policy paths, SMB shares, rclone remotes, transfer history, and rclone.conf import |
| Security | Android-backed vault storage, known-host review, SSH key generation, duplicate identity guards, PIN lock, and optional biometric unlock |
| Operations | Local, remote, and dynamic SOCKS tunnels, guarded runtime actions, Wake-on-LAN, backups, share links, and QR flows |

## Download

| Channel | Package |
| --- | --- |
| [GitHub Releases](https://github.com/7-DS/chrono/releases/latest) | APK builds published for review and testing |

## Features

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
- Duplicate key and credential label prevention.
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

## Build From Source

### Requirements

| Tool | Version |
| --- | --- |
| JDK | 17 |
| Android SDK Platform | 35 |
| Android Gradle Plugin | 8.13.2 |
| Android NDK/CMake | Required for native terminal support |
| Git LFS | Required for bundled native runtime libraries |

When building outside Android Studio, create `local.properties` with your SDK path:

```properties
sdk.dir=/path/to/android-sdk
```

Build and test:

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Repository Layout

| Path | Purpose |
| --- | --- |
| `app/` | Android application source |
| `app/libs/` | Prebuilt Java bridge libraries required by the app |
| `app/src/main/jniLibs/` | Native runtime libraries bundled with the app |
| `third_party/et-transport/` | Vendored Eternal Terminal transport source |
| `third_party/ssp-transport/` | Vendored SSP/Mosh transport source |

## Security Notes

chrono stores credential payloads through Android-backed secret storage. Export and share flows intentionally avoid exporting private credential material unless the user explicitly requests a secret action inside the app.

## Credits

- Server and host-management features were informed by [ServerBox](https://github.com/lollipopkit/flutter_server_box).
- UI direction was inspired in part by [SwiftServer](https://swiftserver.app/) for iOS and macOS.
- `third_party/et-transport/` is based on [Eternal Terminal](https://github.com/MisterTea/EternalTerminal) and extracted from [Haven](https://github.com/GlassHaven/Haven)'s Android SSH client work.
- `third_party/ssp-transport/` is based on the [Mosh](https://github.com/mobile-shell/mosh) SSP protocol and extracted from [Haven](https://github.com/GlassHaven/Haven)'s Android SSH client work.

## License

This repository includes third-party transport code under `third_party/` with its own license files. Review bundled native and prebuilt library provenance before redistributing production builds.
