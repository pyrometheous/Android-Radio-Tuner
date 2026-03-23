# DriveWave SDR

A premium offline-first Android FM radio tuner built for the **RTL-SDR RTL2832U / R820T2** USB-C Software Defined Radio dongle. Designed to look and feel like a high-end car head unit — not a spectrum analyzer.

---

## Features

- **Car Radio UX** — Large frequency display, signal meter, five-segment waveform visualizer, and tactile controls
- **FM Scanning** — Multi-factor confidence scoring: RF power, SNR, audio quieting, stereo pilot lock, RDS quality, raster alignment
- **RDS / RBDS Metadata** — Full offline parser for PS, RadioText, PTY, AF, PI code, PTYN (Groups 0A/0B, 2A/2B, 10A, 14)
- **Station Library** — Scan results saved to a local Room database; favorites with drag-sort
- **FM Recording** — Audio saved to `Music/DriveWaveSDR/` via MediaStore with station name and timestamp in the filename
- **PPM Calibration** — Manual dial (−200 to +200 ppm) with an auto-calibration stub
- **AM Support (Experimental)** — Depends on dongle hardware capability; disabled by default
- **Adaptive Layout** — Bottom nav on phones, navigation rail on tablets, side drawer on large tablets
- **Fully Offline** — Zero network calls. No ads. No analytics. No permissions besides USB, microphone (recording), and file access.

---

## Supported Hardware

| Device | Chip | USB VID | Status |
|---|---|---|---|
| RTL-SDR R820T2 (generic) | RTL2832U | `0x0BDA:0x2838` | Primary target |
| Nooelec NESDR Smart | RTL2832U | `0x0BDA:0x2838` | Tested |
| Other RTL2832U variants | RTL2832U | `0x0BDA:0x2832`, `0x2836`, `0x2840` | Should work |
| Rafael Micro R820T | RTL2832U | `0x0BDA:0x2831` | Untested |

Connect via a **USB-C OTG adapter** or a USB-C native dongle. The app launches automatically when the dongle is plugged in (USB device filter registered in the manifest).

### Tested Android Devices

- Samsung Galaxy Tab S9 (Android 14)
- Samsung Galaxy Note 20 Ultra (Android 13)

Minimum SDK: **Android 13 (API 33)** — required for `FOREGROUND_SERVICE_DATA_SYNC` and MediaStore scoped storage.

---

## Getting Started

### Prerequisites

- Android Studio Ladybug or newer (AGP 8.7+)
- JDK 17
- Android SDK with API 36 installed

### Build

```bash
git clone https://github.com/pyrometheous/Android-Radio-Tuner.git
cd Android-Radio-Tuner
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or download the latest artifact from the [GitHub Actions CI](../../actions/workflows/build.yml) run.

---

## Architecture

```
app/src/main/java/com/drivewave/sdr/
├── domain/          # Pure Kotlin models + repository interfaces
│   ├── model/       # Station, RdsMetadata, RadioState, Config
│   └── repository/  # StationRepository, RecordingRepository, SettingsRepository
├── data/            # Room DB, DataStore, repository implementations
├── driver/          # SdrBackend abstraction
│   ├── FakeSdrBackend          # No hardware needed — simulates 7 stations
│   ├── RtlSdrNativeBackend     # Stub for librtlsdr JNI (TODO)
│   └── ExternalDriverBackend  # Stub for rtl_tcp Android app (TODO)
├── scan/            # ScanEngine (multi-factor scoring), AutoCalibrator
├── metadata/        # RdsParser (Groups 0/2/10A/14), FakeRdsProvider
├── recording/       # RecordingManager (MediaStore)
├── service/         # RadioService (foreground, media notification)
├── di/              # Hilt modules
└── ui/
    ├── theme/       # Dark car radio palette, 4 accent themes, CompositionLocal
    ├── navigation/  # Type-safe @Serializable Dest sealed interface
    ├── adaptive/    # AdaptiveTunerLayout (bottom bar / rail / drawer)
    ├── viewmodel/   # TunerViewModel, RecordingsViewModel, SettingsViewModel
    ├── components/  # AudioWaveform, FrequencyDisplay, SignalMeter, StationRow, …
    └── screens/     # TunerScreen, StationsScreen, RecordingsScreen, SettingsScreen, DiagnosticsScreen
```

**Stack:** Kotlin 2.1 · Jetpack Compose BOM 2024.12 · Material 3 · Hilt 2.53 · Room 2.6 · DataStore 1.1 · Navigation Compose 2.8 · Media3 1.5 · WindowSizeClass

---

## SDR Backend Selection

The app tries backends in priority order:

1. **RtlSdrNativeBackend** — Direct JNI integration with `librtlsdr`. Not yet wired; returns `isAvailable = false`. See `TODO(native-backend)` comments.
2. **ExternalDriverBackend** — Connects to [rtl_tcp_andro](https://github.com/martinmarinov/rtl_tcp_andro) via socket if installed. See `TODO(external-driver)` comments.
3. **FakeSdrBackend** — Always available. Simulates real FM station behavior with synthetic IQ samples and a built-in fake RDS provider. Used during development and UI testing.

---

## Permissions

| Permission | Reason |
|---|---|
| `RECORD_AUDIO` | Capturing audio for recording feature |
| `FOREGROUND_SERVICE` | RadioService must run in foreground |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media notification category |
| `FOREGROUND_SERVICE_MICROPHONE` | Recording via microphone category |
| `POST_NOTIFICATIONS` | Android 13+ notification permission |

The app does **not** request `INTERNET` — all processing is local.

---

## AM Support

AM Medium Wave support is marked **experimental**. Not all RTL2832U dongles can tune below 24 MHz without direct sampling mode. The hardware path for direct sampling is stubbed in `RtlSdrNativeBackend` with `TODO(native-backend: directSampling)` comments.

---

## Licensing Notes

DriveWave SDR is the Android UI application. The native SDR processing library (`librtlsdr`) is licensed under **GPL v2**. When the native backend is integrated, the resulting binary distribution must comply with GPL v2 terms. The UI code in this repository does not yet link to any GPL code.

---

## CI / CD

Every push to `main` or `develop` triggers a GitHub Actions workflow:

- Assembles a **debug APK** and uploads it as a build artifact (30-day retention)
- On `main` pushes also assembles an **unsigned release APK** (90-day retention)

Download APKs from the [Actions tab](../../actions/workflows/build.yml).

---

## Contributing

1. Fork and create a feature branch from `main`
2. Run `./gradlew assembleDebug` to verify the build
3. Open a pull request — CI will run automatically
