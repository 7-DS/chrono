# chrono

chrono is an Android SSH operations client for managing hosts, terminals, file transfers, tunnels, and server health from one mobile workspace.

## What Makes chrono Different

chrono is built as a mobile server cockpit, not just an SSH launcher. A single host profile can carry connection details, credentials, notes, tags, Wake-on-LAN settings, monitoring preferences, proxy-jump routing, terminal behavior, file-browser defaults, reconnect rules, and protocol-specific options.

## Features

### Host Profiles

- SSH, Mosh, Eternal Terminal, VNC, RDP, SMB, rclone, and local PRoot profile types.
- Per-host groups, tags, favorites, OS metadata, accent colors, notes, startup commands, start directories, environment variables, custom logos, connect timeout, compression, and reconnect policy.
- Connection presets for Linux SSH, Mosh, Eternal Terminal, desktop VNC/RDP, SMB shares, rclone remotes, and local PRoot shells.
- Duplicate-host protection using normalized protocol, host, port, and username.
- OpenSSH config import support and host share links/QR payloads that avoid exporting private credential material.

### Server Dashboard

- Home dashboard for live reachability, latency, uptime, CPU, RAM, disk, and network summaries.
- Compact top navigation reveal for long host lists.
- Server detail pages for uptime, CPU usage/load, system info, failed services, resources, filesystems, processes, systemd, network, containers, GPUs, Proxmox, battery, SMART disks, and sensors.
- Configurable detail-card ordering and hidden cards.
- Per-card metric color presets with separate network and disk display modes.
- Connection activity timeline and crash/diagnostic event retention.

### Remote Operations

- Process list with guarded runtime actions.
- Systemd service list with status/start/stop/restart style actions guarded by confirmation.
- Docker and Podman container/image inventory, stats, logs/inspect output, and guarded container actions.
- Host info collection with Linux metric parsers for filesystems, vnStat traffic history, sensors, SMART disks, NVIDIA GPUs, package updates, Proxmox resources, battery state, services, processes, and containers.
- Wake-on-LAN support with MAC, broadcast address, and SecureOn validation.

### Terminal

- Multiple terminal workspaces backed by a foreground service for active sessions.
- SSH terminal support plus Mosh UDP roaming and Eternal Terminal persistence via SSH bootstrap.
- Terminal theme catalog, custom fonts, cursor styles, bracketed paste, scrollback sizing, keep-screen-on, haptics, and left/right terminal margins.
- Configurable accessory key strip with presets for compact, navigation, TUI, shell, control, and function-key workflows.
- Snippet rendering and command helpers for common server workflows.

### Files And Transfers

- SFTP browser for SSH hosts with bookmarks, sorting, hidden-file defaults, directory navigation, text-file editing, chmod, mkdir/rename/delete, upload/download, and transfer history.
- Host-to-host transfer planning for compatible saved hosts.
- SCP transfer policy support for path-based uploads/downloads.
- SMB file browsing for password-backed SMB profiles.
- Embedded rclone profile support with rclone.conf import, encrypted config unlock flow, remote picker, root-path handling, transfer progress, and advanced rclone file actions.
- Transfer persistence for queued, running, complete, failed, and cancelled records.

### Vault, Keys, And Trust

- Vault-backed password and private-key identities stored through Android-backed secret storage.
- Duplicate credential/key label protection.
- Private-key inspection, passphrase policy, and in-app SSH key generation.
- Known-host capture, trust review, changed-key detection, and rejected-host handling.
- Public-key export/copy support without exposing private key payloads by default.
- Explicit confirmation policies for copying, exporting, or sharing secret material.

### Tunnels And Connections

- Local, remote, and dynamic SOCKS port-forward rules.
- Tunnel dashboard with active/stopped/failed state, favorites, grouping, route labels, user-facing error cleanup, and one-tap start/stop.
- ProxyJump validation for chained SSH access.
- Connection launch policy that selects the right terminal, desktop, file, or local runtime path for each host protocol.

### Backup, Share, And App Lock

- Backup export/inspect/merge flows for hosts, credential metadata, known hosts, snippets, tunnels, SFTP bookmarks, and settings.
- Encrypted backup codec for protected exports.
- Host and snippet share links plus QR payload generation.
- PIN app lock with optional biometric unlock and crash-recovery handling.

### Interface And Customization

- App theme families with light/dark/system modes.
- Per-section heading font imports for Home, Connections, Files, Vault, and Settings.
- OS/distro visual treatment for Linux, BSD, Windows, Proxmox-style hosts, and common distributions.
- Smooth Compose motion and status/IME-aware screens for mobile use.

## Requirements

- Android Studio or command-line Android SDK.
- JDK 17.
- Android SDK Platform 35 and Android Gradle Plugin 8.13.2.
- Android NDK/CMake for native terminal support.
- Git LFS for bundled native runtime libraries.

Create a local `local.properties` file with your SDK path when building outside Android Studio:

```properties
sdk.dir=/path/to/android-sdk
```

## Build

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Repository Layout

- `app/` - Android application source.
- `app/libs/` - prebuilt Java bridge libraries required by the app.
- `app/src/main/jniLibs/` - native runtime libraries bundled with the app.
- `third_party/et-transport/` - vendored Eternal Terminal transport source.
- `third_party/ssp-transport/` - vendored SSP/Mosh transport source.

## Credits

- Server and host-management features were informed by ServerBox (`lollipopkit/flutter_server_box`).
- `third_party/et-transport/` is based on Eternal Terminal and extracted from Haven's Android SSH client work.
- `third_party/ssp-transport/` is based on the Mosh SSP protocol and extracted from Haven's Android SSH client work.

## Security Notes

chrono stores credential payloads through Android-backed secret storage. Export/share flows intentionally avoid exporting private credential material unless a user explicitly requests a secret action inside the app.

## License

This repository includes third-party transport code under `third_party/` with its own license files. Review bundled native and prebuilt library provenance before redistributing production builds.
