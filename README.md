# chrono

chrono is an Android SSH operations client for managing hosts, terminals, file transfers, tunnels, and server health from one mobile workspace.

## Features

- SSH, Mosh, Eternal Terminal, VNC, RDP, SMB, rclone, and local PRoot connection profiles.
- Host dashboard with live status, resource summaries, saved notes, tags, favorites, and trusted-host review.
- Terminal workspaces with themes, custom fonts, accessory keys, transcript tools, and reconnect behavior.
- SFTP/file-browser workflows, transfer history, rclone remotes, and SMB share support.
- Vault-backed password/private-key identities with duplicate-name protection.
- Port forwards, snippets, host share/import links, Wake-on-LAN, and server detail panels.

## Requirements

- Android Studio or command-line Android SDK.
- JDK 17.
- Android SDK Platform 35 and Android Gradle Plugin 8.13.2.
- Android NDK/CMake for native terminal support.

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
