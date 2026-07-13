<h1 align="center">
  <img src="docs/assets/chrono-heading.png" width="260" alt="chrono" />
</h1>

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
  <a href="#connection-types">Connection Types</a>
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

## Connection Types

chrono supports eight connection types. Choose the type when adding a host, then fill in the fields described below. Terminal types (SSH, Mosh, Eternal Terminal, PRoot) open a shell; VNC and RDP open a desktop viewer; SMB and rclone open the file browser.

### SSH

Encrypted terminal to a Linux/Unix server. The default and most compatible option.

- **Prerequisites:** an OpenSSH server on the host (port 22 by default); a username plus either a password or a private key.
- **Steps:**
  1. Enter the host address (IP or domain) and port (usually 22).
  2. Enter the username you log in as (e.g. `root` or your account).
  3. Add credentials: type a password, or attach a private key from the Vault.
  4. Save the host, then open it once to review and trust the SSH host-key fingerprint.
  5. Tap the host for a terminal, or open Files for SFTP.
- **Tips:** private keys are more secure than passwords — generate or import one in the Vault. If the server uses a non-standard port, set it in the port field, not the address.

### Mosh (Mobile Shell)

A roaming-friendly terminal that survives network changes and high latency. It bootstraps over SSH, then uses UDP.

- **Prerequisites:** `mosh-server` installed on the host; working SSH access (Mosh logs in over SSH first); the UDP port range reachable through the host firewall.
- **Steps:**
  1. Set the host, SSH port, and username exactly as for SSH.
  2. Add your SSH password or private key — Mosh uses it for the initial handshake.
  3. Leave *Mosh Server* as `mosh-server` unless it lives at a custom path.
  4. Set the UDP port range (default `60000:61000`) to match what is open on the host.
  5. Optionally set locale (`en_US.UTF-8`) and colors (`256`), then save and connect.
- **Tips:** open the UDP range on the server firewall or Mosh hangs after the SSH step. Ideal for spotty mobile connections — the session reconnects automatically.

### Eternal Terminal (ET)

A persistent terminal that reconnects automatically and keeps your session alive across drops. Bootstraps over SSH.

- **Prerequisites:** `etserver` installed and running on the host; working SSH access for the bootstrap; the ET port (default 2022) reachable through the firewall.
- **Steps:**
  1. Enter the host and username as for SSH.
  2. Set *SSH Port* (bootstrap, usually 22) and *ET Port* (default 2022).
  3. Add your SSH credential (password or key) for the bootstrap login.
  4. Leave *Terminal* as `xterm-256color` and *ET Command* as `etterminal` unless customized.
  5. Save, trust the host key, then connect.
- **Tips:** ET shines on unreliable links — session state is preserved server-side. Confirm `etserver` is enabled as a service so it is always listening.

### VNC (Remote Desktop)

View and control a graphical Linux/Unix desktop. Optionally tunnelled securely over SSH.

- **Prerequisites:** a VNC server on the host (e.g. TigerVNC, x11vnc), usually on port 5900 + display; a VNC password, and for tunnelling, working SSH access.
- **Steps:**
  1. Enter the host and the VNC port (5900 for display `:0`, 5901 for `:1`, etc.).
  2. Put the VNC password in the password field.
  3. Set color *Depth* (24) and target *FPS* (30) to taste.
  4. For security, enable *Tunnel over SSH* and set the SSH port so VNC never crosses the network in the clear.
  5. Use *View Only* to watch without controlling, or *Shared* to join an existing session. Save and connect.
- **Tips:** always tunnel over SSH on untrusted networks — raw VNC is unencrypted. Lower depth/FPS on slow links for a smoother picture.

### RDP (Windows Remote Desktop)

Connect to a Windows desktop or server over the Remote Desktop Protocol. Optionally tunnelled over SSH.

- **Prerequisites:** Remote Desktop enabled on the Windows host (port 3389); a Windows username and password (and domain, if applicable).
- **Steps:**
  1. Enter the host and RDP port (default 3389).
  2. Enter the Windows username and password.
  3. Set the resolution (*Width* × *Height*), color *Depth*, and *Domain* (`WORKGROUP` if none).
  4. Leave *NLA* on for Network Level Authentication unless the host requires it off.
  5. Optionally enable *Tunnel over SSH*, then save and connect.
- **Tips:** for a domain account, fill in the Domain field or use `DOMAIN\user` as the username. Match the resolution to your device screen for the crispest result.

### SMB File Share

Browse and transfer files on a Windows/Samba network share.

- **Prerequisites:** an SMB/CIFS share exposed by the host (port 445); a username and password with access to the share.
- **Steps:**
  1. Enter the host and port (445).
  2. Enter the username and password for the share.
  3. Set *Root Path* to the share, e.g. `//server/share`.
  4. Save, then open Files to browse the share and transfer files.
- **Tips:** use the `//host/share` form for Root Path, not a Windows drive letter. If access is denied, confirm the account has share + NTFS permissions.

### Cloud / rclone

Browse and transfer files on any of rclone's 70+ cloud backends (Google Drive, S3, Dropbox, etc.).

- **Prerequisites:** an existing `rclone.conf` that defines your remote (created with `rclone config` on a computer); the remote name from that config (e.g. `gdrive:`).
- **Steps:**
  1. Choose the Cloud/rclone type.
  2. Tap *Import rclone.conf* and pick your config file.
  3. Tap *Choose Remote* and select the remote from the imported config.
  4. Set *Root Path* to the starting folder, e.g. `remote:/` or `remote:backups`.
  5. Optionally expand advanced controls for Concurrency, Resume, and Checksums, then save and open Files.
- **Tips:** generate `rclone.conf` on a desktop first — token/OAuth setup is easiest there. Enable *Verify Checksums* for important transfers when the backend supports it.

### Local PRoot (on-device Linux)

Run a full Linux userland on this device with no server or network required.

- **Prerequisites:** enough free storage for the distro rootfs (typically 150–400 MB); no host, credentials, or internet needed after the rootfs is installed.
- **Steps:**
  1. Choose the Local PRoot type.
  2. Pick a distro (e.g. `alpine-3.21`) in the Distro field.
  3. Tap *Download Rootfs* to fetch it, or *Import Rootfs* to load a `.tar.gz` you already have.
  4. Leave *Mount Home* on to share chrono's home folder into the shell.
  5. Save, then tap the host to open a local Linux terminal.
- **Tips:** PRoot runs entirely on-device — good for offline scripting and learning Linux. Use *Clear Rootfs* to reclaim space or start clean.

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
