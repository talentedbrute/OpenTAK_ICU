# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OpenTAK ICU is an Android video streaming app for the TAK (Team Awareness Kit) ecosystem. It streams audio/video over RTSP(S), RTMP(S), SRT, and UDP Multicast using the [RootEncoder](https://github.com/pedroSG94/RootEncoder) library, and communicates with TAK servers via Cursor-on-Target (CoT) XML messages over TCP with SSL/TLS support.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore at ../../android_keystore and ../../keystore.properties)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew testDebugUnitTest --tests "io.opentakserver.opentakicu.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Lint check
./gradlew lint
```

## Build Configuration

- **Namespace/Package:** `io.opentakserver.opentakicu`
- **Compile/Target SDK:** 34 (Android 14), **Min SDK:** 26 (Android 8.0)
- **Language:** Java (source compatibility Java 8), Kotlin Gradle scripts
- **View Binding:** enabled
- **Debug build:** appends `.debug` to applicationId
- **Signing:** release builds use an external keystore (`../../android_keystore`) with properties from `../../keystore.properties`
- **Lint:** `abortOnError = false`

## Architecture

**Single-activity, fragment-based with a foreground service:**

- `MainActivity` → hosts `Camera2Fragment` via Navigation
- `Camera2Fragment` → main UI: camera preview (OpenGL), streaming controls, status display
- `Camera2Service` → foreground service that manages streaming and recording lifecycle, independent of UI. Communicates with the fragment via `MutableLiveData` observer and broadcast intents
- `SettingsActivity` / `SettingsFragment` → preferences UI, with sub-fragments in `preferences/` (Video, Audio, Stream, ATAK)
- `OnBoardingActivity` → first-launch permissions and setup flow

**TAK/CoT System (`cot/` package):**
- CoT event model classes with Jackson XML annotations (`event`, `Detail`, `Point`, `Contact`, `Track`, `Sensor`, `__Video`, etc.)
- `TcpClient` → manages TCP connection to TAK servers with SSL/TLS, handles certificate enrollment (CSR generation via `utils/CSRGenerator`), and transmits CoT events
- `MulticastClient` → multicast UDP streaming support
- `parser/CoT` → XPath-based CoT XML parsing

**Key utilities (`utils/`):**
- `Constants` → CoT type definitions and app-wide constants
- `Preferences` (in `contants/` package) → all preference keys and defaults for streaming, video, audio, and ATAK settings
- `CSRGenerator` → RSA 2048 certificate signing request generation
- `PEMImporter` → PEM certificate import for trust store management

## Key Libraries

- **RootEncoder** (`com.github.pedroSG94.RootEncoder:library:2.5.5`) — video/audio encoding and streaming (RTSP, RTMP, SRT, UDP)
- **Jackson XML** (`jackson-dataformat-xml`) — CoT XML serialization/deserialization
- **OkHttp** — HTTP client for certificate enrollment
- **BouncyCastle** (`bcprov-jdk15on`, `bcpkix-jdk15on`) — cryptographic operations, CSR generation, certificate management
- **libsu** (`topjohnwu.libsu`) — root/system access
- **Material Components** `1.13.0-alpha08` — specifically chosen for Slider orientation support
- **Firebase** — analytics and Crashlytics

## Streaming Protocols and Codecs

- **Video:** H264, H265, AV1
- **Audio:** AAC, G711, OPUS
- **Protocols:** RTSP, RTSPS, RTMP, RTMPS, SRT, Multicast UDP
- Supports adaptive bitrate, background streaming/recording, self-signed certificates
