# SagapMo

SagapMo is an Android app for students at Batangas State University, Alangilan Campus, to record Wi-Fi and mobile signal conditions around campus. Students can measure signal strength, test internet performance, and revisit saved readings through a map or a filterable list.

Readings stay on the device and remain available offline. Each reading includes its capture time and, when available, a location. The app supports Android 12 or later.

## Table of contents

- [Problem](#problem)
- [Objectives](#objectives)
- [Target users](#target-users)
- [Features](#features)
- [How it works](#how-it-works)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Project structure](#project-structure)
- [Privacy and data storage](#privacy-and-data-storage)
- [Known limitations](#known-limitations)
- [Developer notes](#developer-notes)

## Problem

Students rely on internet access to download learning materials, communicate with classmates, and submit academic work. Around Alangilan Campus, weak or inconsistent connections can interrupt these tasks and leave students searching for a usable connection.

Without records tied to specific locations, it is difficult to know where a connection worked well before. SagapMo lets students collect their own outdoor measurements and review the conditions recorded at each time and place.

## Objectives

Develop an Android application that lets students record Wi-Fi and cellular signal strength, measure internet performance, and review locally stored readings through a map and list.

1. Associate readings with available device coordinates or a manually selected location so students can revisit measured spots.
2. Detect the active connection type and signal strength in dBm, and measure download speed, upload speed, latency, and jitter.
3. Store readings locally so students can review them without Wi-Fi or mobile internet access.

## Target users

The app is intended for students at Batangas State University, Alangilan Campus.

| Student activity | How SagapMo helps |
| --- | --- |
| Recording a connection outdoors | Saves the active Wi-Fi or mobile signal strength with its capture time and available coordinates. |
| Testing internet performance | Measures download and upload speeds, latency, and jitter during a capture. |
| Reviewing earlier observations | Displays saved readings in a list and places readings with coordinates on a map. |
| Organizing collected readings | Supports location descriptions, manual pin placement, and deletion with temporary Undo. |

## Features

### Signal recording

- Record the active default Wi-Fi or mobile connection on demand.
- Save Wi-Fi signal in dBm or mobile signal as Android's 0–4 level, its rating, and the capture time.
- Include Android's reported 2G/3G/4G/5G label in mobile readings when available. Existing readings retain unknown network context.
- Save the serving carrier and radio type, LTE RSRP/RSRQ/RSSNR/RSSI, and 5G SS-RSRP/SS-RSRQ/SS-SINR when Android reports them. Detailed metrics appear under Advanced Information in Reading details.
- Keep a valid signal reading even when location information is unavailable.
- Retry a failed save without taking another measurement while the captured reading remains available in the current process.

### Internet speed test

- Enable **Measure Speed During Capture** in Settings to include a speed test when recording.
- Measure download and upload speeds in Mbps, plus latency and jitter in milliseconds.
- Follow live progress on the speedometer and review the saved results in Reading details.
- Download and upload each target 10 seconds on Wi-Fi or 5 seconds on mobile data, with a 150 MB payload cap per phase.

### Map and locations

- View readings with coordinates as selectable MapLibre pins.
- Open the bundled Alangilan Campus map in 2D or 3D without a connection, including on first launch.
- Open a pin to inspect its reading.
- Retain valid, recent approximate coordinates and label the location as approximate.
- Use **Set location** in Reading details to add or correct a pin manually. Manual changes preserve the reading's signal values and capture time and are labelled **Set manually**.

### Readings and organization

- Browse stored readings and filter by **All**, **Wi-Fi**, or **Mobile**.
- Add an optional location description of up to 200 characters. Changes save automatically after a short pause in typing and appear in the list.
- Swipe a row left, or use the TalkBack **Delete reading** action, then confirm deletion.
- Restore a deleted reading using **Undo** while the snackbar remains available.

### Offline access

- Review saved readings, filters, descriptions, and details without internet access.
- Keep readings across app restarts using Room.
- The campus basemap, labels, icons, saved pins, and manual location picker work offline from the first launch. No map download is needed.
- The bundled map covers Alangilan Campus and nearby streets; speed tests require internet access.

## How it works

1. Connect the phone to Wi-Fi or mobile data. SagapMo measures whichever connection Android uses as the active default network.
2. Open the app and allow location access when prompted to attach available coordinates to readings. A valid signal can still be saved without a location.
3. To include internet performance measurements, open Settings and enable **Measure Speed During Capture**.
4. Tap **Record reading** at an outdoor campus location and wait for the capture to finish.
5. Open **Readings**, or select a saved map pin, to review the measurement. Add a description or use **Set location** if needed.
6. Return later to compare your stored observations, including when the phone is offline.

To record mobile signal, turn Wi-Fi off in Android settings and wait for mobile data to become the default network. The All, Wi-Fi, and Mobile filters change which readings are displayed; they do not choose the connection being measured.

## Tech stack

| Category | Technology |
| --- | --- |
| Platform | Native Android, Android 12+ (API 31+) |
| Language | Kotlin |
| Interface | Jetpack Compose and Material 3 |
| State and asynchronous work | ViewModel, Kotlin coroutines, and Flow |
| Signal and location | Android connectivity, telephony, and location APIs |
| Local storage | Room Database with schema migrations |
| Maps | MapLibre Compose with OpenFreeMap basemaps |
| Speed tests | Custom HTTP/HTTPS engine using OkHttp and Cloudflare speed-test endpoints |
| Build tools | Gradle wrapper and Android Gradle Plugin |
| Testing | JUnit, coroutine tests, MockWebServer, Android instrumentation, and Compose UI tests |

## Getting started

### Requirements

- Android Studio and an Android SDK with Android API 37 installed. The app compiles against API 37, targets API 36, and runs on API 31 or later.
- Java 25 to run the pinned Gradle wrapper. The app's bytecode targets Java 17.
- A physical Android phone for signal measurements, or an emulator for interface development. Hardware and Android API support affect the measurements available.
- Internet access to download build dependencies and run speed tests. The campus map is included in the app.

### Open and configure the project

1. Clone or download this repository and open its root folder in Android Studio.
2. Set the Gradle JDK to Java 25.
3. Point `local.properties` at your Android SDK. On Windows, escape the drive colon, for example:

   ```properties
   sdk.dir=C\:/Users/USER/AppData/Local/Android/Sdk
   ```

4. Sync the Gradle project.

No account, API key, billing setup, or backend server is required.

### Build and install

From the repository root in PowerShell:

```powershell
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Connect a device with USB debugging enabled, or start an emulator, before installing. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Run checks

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

The second command requires a connected device or emulator. The verification record for 21 November 2026 reports five failures in the full device suite involving a migration fixture and UI assertions. The targeted campus-label tests passed in that run.

## Project structure

```text
app/
  src/main/
    AndroidManifest.xml
    kotlin/com/sagapmo/app/
      MainActivity.kt          # Android entry point
      SagapMoViewModel.kt       # Capture, saved readings, and UI state
      capture/                 # Signal and location acquisition
      map/                     # Campus map, labels, and reading pins
      reading/                 # Room entities, DAO, migrations, and validation
      speedtest/               # Speed-test engine, models, and gauge
      ui/                      # Screens, navigation, settings, and theme
    res/                       # Android resources
  src/test/                    # Unit tests
  src/androidTest/             # Device and Compose UI tests
  schemas/                     # Exported Room schemas
gradle/                        # Wrapper and dependency version catalog
design/                        # HTML/CSS/JavaScript prototype and screenshots
design-ref/                    # Supplied visual references
docs/                          # Design notes, specifications, and verification records
```

## Privacy and data storage

- Readings, coordinates, and location descriptions are stored in the app's local Room database. There are no user accounts, cloud synchronization, or shared measurement collections.
- The app requests location access to attach available coordinates. Approximate locations are identified, and readings without coordinates remain accessible in the list.
- The default campus map loads from app assets without sending map requests to external services. Speed tests exchange traffic with Cloudflare endpoints and consume data on the active connection.
- Android app backup is disabled. Clearing app data or uninstalling the app removes the saved collection. Export and recovery are not available.
- Undo is temporary and does not survive closing the app.

## Known limitations

- The project focuses on individual outdoor samples within Alangilan Campus. It does not establish continuous or campus-wide coverage, and areas without readings remain unsampled.
- Measurements reflect conditions at the time and place of capture. Device hardware, network conditions, and Android API restrictions can affect results.
- Mobile generation follows Android's carrier display policy, including 5G labels on LTE-based networks. It does not prove an active 5G radio connection or guarantee speed. Missing network context does not prevent signal recording.
- Cellular capture uses a bounded signal-strength callback with a cached-signal fallback and the existing timestamp check. LTE and 5G measurements are stored separately; unsupported, unreported, or invalid metrics remain unavailable. Carrier names identify the registered network and may differ from the SIM provider when roaming.
- Location accuracy may decrease indoors or near buildings. Approximate and manually assigned locations can affect pin placement; invalid or stale device coordinates are excluded.
- Campus landmark coordinates are estimates pending a survey.
- Saved records and the bundled campus map work offline; speed tests need internet access.
- Offline map coverage is limited to Alangilan Campus and nearby streets (longitude 121.055 to 121.095, latitude 13.765 to 13.805). It is a snapshot, updated with app releases rather than live map changes.
- Heatmaps, continuous background monitoring, cloud synchronization, shared readings, export, and user-managed map downloads are outside the current scope.
- Pending description saves continue when Reading details closes, but do not survive process termination. A failed capture save can be retried only while its captured candidate remains in the current process.

## Developer notes

The Android project is at the repository root. A fresh checkout does not need a nested `.worktrees/pilot` folder.

The `design/` browser prototype provides visual context and does not implement every native feature. Use the current Android source when older design or verification notes describe different behavior. Test readings and screenshot fixtures do not seed the production database.

Rebuild and reinstall the APK after native UI changes to see them on a device.

### Bundled campus map

`app/src/main/assets/campus-map/` includes both map styles, vector tiles, label fonts, icons, source attribution, and licenses. The snapshot adds approximately 3.5 MiB of uncompressed assets. Tiles at source zooms 12–14 retain detailed building geometry when zoomed in further.

Validate the checked-in bundle without network access:

```powershell
python tools/bundle_campus_map.py
```

To refresh the snapshot during development (requires internet), run `python tools/bundle_campus_map.py --refresh`, review the updated assets and manifest, then rebuild the app. This command does not run during app startup or normal builds.
