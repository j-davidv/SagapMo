# SagapMo? Pilot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android app that captures real Wi-Fi and Mobile signal readings, saves them locally, and displays them through a campus map and an offline readings list.

**Architecture:** One Android application module owns a Room database and an activity-scoped ViewModel. Small Android signal/location adapters feed a common capture flow, and both destinations observe the same saved readings and filter. Map loading operates independently of capture and database access.

**Tech Stack:** Kotlin, Jetpack Compose with Material 3, coroutines, Room/KSP, MapLibre Compose with its Android OpenGL runtime, OpenFreeMap Liberty, JUnit, AndroidX Test, and Compose UI tests.

**Spec:** [Approved pilot design](../specs/2026-11-17-sagapmo-pilot-design.md). Read it before executing any task. The user approved the spec on 2026-11-17 and requested a plan before giving implementation direction.

## Global Constraints

- Android 12 / API 31 minimum.
- Use MapLibre and a basemap that requires no billing account.
- Store individual readings locally on the device. The pilot has no accounts, server, or shared measurements.
- Capture one reading for each completed, user-initiated recording operation.
- Preserve valid measurements when a usable location is unavailable. Explain the missing pin.
- Leave areas without readings unsampled.
- Wi-Fi Good is at least -65 dBm, Fair is at least -75 dBm and below -65 dBm, and Poor is a valid RSSI below -75 dBm. Valid RSSI is strictly between -127 and 0 dBm.
- Mobile Good / Fair / Poor is Android level 3-4 / 2 / 1. Mobile level 0 is unavailable for this pilot. Maximum cellular measurement age is 60 seconds, inclusive.
- Location request deadline is 10 seconds after permission handling. Maximum location age is 30 seconds, inclusive, at signal acquisition. Maximum reported horizontal accuracy is 30 metres, inclusive.
- Initial camera is latitude 13.78421, longitude 121.07428, zoom about 17.
- Basemap is `https://tiles.openfreemap.org/styles/liberty`. Preserve OpenFreeMap, OpenMapTiles, and OpenStreetMap attribution.
- Expose map retry/readings actions after 15 seconds without a usable visible basemap. Late success clears the failure feedback.
- Offline access covers stored readings, filters, and details. Downloadable map packs belong to deferred scope.

## Review Focus

1. Wall-clock changes must not change signal/location freshness. Task 1 tests elapsed-time boundaries, and Task 2 tests location age.
2. Permission dialogs and rotation must preserve one capture, while backgrounding before saving cancels acquisition. Task 4 tests those transitions.
3. Save retry and an existing primary key must preserve the original reading. Task 3 tests identical retries and rejects a conflicting payload.
4. Recording while a filter hides the captured connection must still save it and allow opening it. Task 5 tests the normal UI entry point.
5. Map callbacks arriving after retry or leaving the screen must not replace the current attempt's state. Task 7 tests attempt identity and late recovery.

---

## Starting point and implementation choices

The repository currently contains the approved design document. There is no Android application or Gradle wrapper yet. Local inspection found Java 25.0.2, Android Studio's bundled Java, SDK platform directories `android-34`, `android-35`, `android-36`, and `android-37.0`, and `adb.exe` at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. Confirm the intended physical device when execution begins.

Use application ID and namespace `com.sagapmo.app`, one `app` module, `minSdk = 31`, `compileSdk = 37`, and `targetSdk = 36`. Compiling against 37 accommodates the selected map library's published build baseline. Target 36 preserves its documented Android target baseline while keeping API-31-compatible runtime calls. This is a pilot build for installation through Android Studio/ADB.

Pin these versions in `gradle/libs.versions.toml`. This selection follows the map library's release source and official Android documentation. It remains subject to the first build's dependency-resolution check.

| Component | Pin |
|---|---|
| Gradle wrapper | 9.7.1 |
| Android Gradle plugin | 9.3.3 |
| Kotlin and Compose compiler plugin | 2.4.20 |
| KSP | 2.3.14 |
| Compose BOM | 2026.09.00 |
| Activity Compose | 1.13.0 |
| Lifecycle ViewModel/Runtime Compose | 2.11.0 |
| Coroutines Android and test | 1.11.0 |
| Room runtime/compiler/testing | 2.8.5 |
| MapLibre Compose and OpenGL Android runtime | 0.20.0 |

Use AGP's built-in Kotlin support. Upgrade its KGP buildscript dependency to the selected Kotlin version using the documented mechanism, and apply the matching Compose compiler plugin and KSP. Resolve test runner/JUnit pins from stable AndroidX Test artifacts at bootstrap and record exact versions in the catalog. Include Compose UI testing through the same BOM. Compile Java/Kotlin bytecode to JVM 17. Use the local Java 25 installation to run Gradle and confirm the Gradle/AGP combination in Task 1. [MapLibre release dependencies](https://raw.githubusercontent.com/maplibre/maplibre-compose/v0.20.0/gradle/libs.versions.toml) · [Upstream wrapper](https://raw.githubusercontent.com/maplibre/maplibre-compose/v0.20.0/gradle/wrapper/gradle-wrapper.properties) · [AGP 9.3 compatibility and fixes](https://developer.android.com/build/releases/agp-9-3-0-release-notes) · [KGP upgrade mechanism](https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-kotlin-gradle-plugin-upgrade)

Room uses KSP, and the Compose compiler follows Kotlin's version. Use the base map artifact plus exactly one Android runtime, `org.maplibre.compose:maplibre-compose-runtime-opengl-android:0.20.0`, as documented. A single Android module can consume the Android variant of the published Compose library. The OpenGL choice favors broad pilot-device support. [Room dependencies](https://developer.android.com/jetpack/androidx/releases/room) · [Compose BOM](https://developer.android.com/develop/ui/compose/bom) · [MapLibre installation](https://maplibre.org/maplibre-compose/getting-started/) · [KSP releases](https://github.com/google/ksp/releases)

Keep direct dependency construction in `SagapMoApplication` and the Activity's ViewModel factory. Use two small hardware-source interfaces to make capture behavior testable. Call Room's DAO directly. Use standard Material 3 components, the system font, system theme, text rating labels, and 48 dp minimum touch targets.

## File structure

Every path below is planned, except the existing spec. Production Kotlin files live under `app/src/main/kotlin/com/sagapmo/app/`. Unit tests mirror that package under `app/src/test/kotlin/`, and device/UI tests under `app/src/androidTest/kotlin/`.

| Files | Responsibility |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `.gitignore` | Reproducible single-module Android build |
| `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/xml/data_extraction_rules.xml` | App configuration, labels, runtime permissions, backup exclusions |
| `SagapMoApplication.kt`, `MainActivity.kt` | Database/hardware construction, permission launcher, activity lifecycle, Compose entry point |
| `reading/Reading.kt`, `reading/ReadingRules.kt` | Reading/entity and enums, validation, ratings, location attachment |
| `capture/CaptureModels.kt`, `capture/SignalReader.kt`, `capture/LocationReader.kt` | Capture snapshots/results and Android hardware adapters |
| `reading/ReadingDao.kt`, `reading/ReadingDatabase.kt`, `app/schemas/com.sagapmo.app.reading.ReadingDatabase/1.json` | Room queries, atomic save/retry behavior, schema version 1 |
| `SagapMoViewModel.kt` | Shared filter, capture state, permission continuation, save/read retry and selected reading |
| `ui/SagapMoApp.kt`, `ui/ReadingsScreen.kt`, `ui/ReadingDetails.kt` | Navigation, capture actions, list, common detail sheet |
| `map/CampusMapScreen.kt`, `map/MapLoadState.kt` | Map camera, pins, attribution, bounded loading/error/retry state |
| Unit tests `reading/ReadingRulesTest.kt`, `capture/LocationRulesTest.kt`, `SagapMoViewModelTest.kt`, `map/MapLoadStateTest.kt` | Boundary, lifecycle, concurrency, and asynchronous error checks |
| Device tests `reading/ReadingDaoTest.kt`, `ui/SagapMoAppTest.kt`, `map/CampusMapScreenTest.kt` | Persistence, UI actions, map integration and resource failures |
| `docs/superpowers/verification/2026-11-17-sagapmo-pilot.md` | Actual device observations and acceptance results, populated during execution |

## Shared types

Define these once in Tasks 1 and 2. Later tasks consume these names.

- `ConnectionType { WIFI, MOBILE }`, `SignalUnit { DBM, ANDROID_LEVEL }`, `Rating { GOOD, FAIR, POOR }`.
- `NoLocationReason` uses the seven exact names in the spec's location table.
- `CaptureError { NO_CONNECTION, UNSUPPORTED_CONNECTION, VPN_ACTIVE, CONNECTION_CHANGED, SIGNAL_UNAVAILABLE, SIGNAL_INVALID, SIGNAL_STALE, SIGNAL_PERMISSION_DENIED }`.
- `SignalSample(connectionType: ConnectionType, rawSignalValue: Int, signalUnit: SignalUnit, rating: Rating, signalAgeMillisAtCapture: Long?, capturedAtEpochMillis: Long, capturedElapsedMillis: Long)`.
- `SignalResult.Available(sample: SignalSample)` and `SignalResult.Failed(error: CaptureError)`.
- `ConnectionSnapshot(networkHandle: Long, connectionType: ConnectionType, dataSubscriptionId: Int?)`. Only supported unambiguous transports produce this type. Wi-Fi has a null subscription ID.
- `ConnectionResult.Available(snapshot: ConnectionSnapshot)` and `ConnectionResult.Failed(error: CaptureError)`.
- `LocationAccess(precise: Boolean, coarse: Boolean, servicesEnabled: Boolean)`.
- `LocationFix(latitude: Double, longitude: Double, accuracyMetres: Float?, measuredElapsedMillis: Long)`.
- `LocationResult.Candidate(fix: LocationFix)` and `LocationResult.Missing(reason: NoLocationReason)`.

`Reading` uses the spec's exact field names and enums, with `id: String`, timestamps/ages as `Long`, coordinates as `Double?`, and `accuracyMetres: Float?`. It contains every listed database field and omits the transient `capturedElapsedMillis`. Room stores enum names as strings using its built-in enum converters.

All commands below run from the repository root in PowerShell. Bootstrap must make `./gradlew.bat` available before running test commands. JUnit's `assertThrows` examples assume JUnit 4.13.2 or a compatible assertion API. Test blocks show named assertions to implement in the named tests, using local fixtures/fakes where referenced.

## Task 1. Bootstrap the app and prove signal acquisition

**Files**

Create the build/configuration files listed above, `SagapMoApplication.kt`, `MainActivity.kt`, `reading/Reading.kt`, `reading/ReadingRules.kt`, `capture/CaptureModels.kt`, `capture/SignalReader.kt`, and unit test `reading/ReadingRulesTest.kt`. Create the verification document with a blank results table whose statuses say pending.

**Interfaces**

- `fun rateWifi(rssi: Int): Rating?` and `fun rateMobile(level: Int, measuredElapsedMillis: Long, nowElapsedMillis: Long): Rating?` in `ReadingRules.kt` return null when unavailable/invalid/stale.
- `fun connectionChange(before: ConnectionSnapshot, after: ConnectionResult): CaptureError?` returns a change error when the network, type, or Mobile subscription differs or disappears.
- `interface SignalReader { fun currentConnection(): ConnectionResult; suspend fun read(): SignalResult }` and `class AndroidSignalReader(context: Context) : SignalReader` in `SignalReader.kt`.

- [ ] Bootstrap the smallest Android application with the pinned build tools, the full planned dependency set, JUnit/coroutine test support, and a launcher Activity. Create the root/app Gradle files, wrapper, manifest, strings, theme, and `.gitignore` listed in the file structure. Task 3 creates the extraction rules and exported Room schema. Add `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `ACCESS_COARSE_LOCATION`, and `ACCESS_FINE_LOCATION`. Mark Wi-Fi and telephony hardware optional in the manifest so unsupported hardware can show a capture error. Configure Room schema export for Task 3. Ignore local SDK paths, build output, IDE-local files, and verification screenshots. Run `./gradlew.bat :app:assembleDebug` and resolve bootstrap failures before adding behavior. Expected result is `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] Write `ReadingRulesTest` with these assertions and named cases for changed/disappeared default connections and invalid subscriptions.

```kotlin
@Test fun wifiBoundaries() {
    assertEquals(Rating.GOOD, rateWifi(-65))
    assertEquals(Rating.FAIR, rateWifi(-66))
    assertEquals(Rating.FAIR, rateWifi(-75))
    assertEquals(Rating.POOR, rateWifi(-76))
    listOf(Int.MIN_VALUE, -128, -127, 0, 1).forEach { assertNull(rateWifi(it)) }
}

@Test fun mobileBoundariesUseElapsedTime() {
    assertEquals(Rating.POOR, rateMobile(1, 40_000, 100_000))
    assertEquals(Rating.FAIR, rateMobile(2, 40_000, 100_000))
    assertEquals(Rating.GOOD, rateMobile(3, 40_000, 100_000))
    assertEquals(Rating.GOOD, rateMobile(4, 40_000, 100_000))
    assertNull(rateMobile(0, 40_000, 100_000))
    assertNull(rateMobile(5, 40_000, 100_000))
    assertNull(rateMobile(3, 39_999, 100_000))
    assertNull(rateMobile(3, 100_001, 100_000))
    assertNull(rateMobile(3, -1, 100_000))
}
```

- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests 'com.sagapmo.app.reading.ReadingRulesTest'`. Expected result is failure because production rules are absent.

- [ ] Implement the types and rule signatures. `AndroidSignalReader` obtains the active default network/capabilities per read. Reject VPN first, then missing/unsupported/ambiguous transports. Fetch Wi-Fi RSSI from that network's `WifiInfo`. For Mobile, reject an invalid active-data subscription before creating its `TelephonyManager`, then read `signalStrength`, `level`, and `timestampMillis`. Recheck connection identity around acquisition. Use elapsed realtime for age and wall clock only for `capturedAtEpochMillis`. Perform binder work away from the UI thread. Map expected platform failures to `SignalResult.Failed`, and propagate coroutine cancellation.

- [ ] Wire a temporary usable **Record reading** action in `MainActivity` that displays connection, rating, raw value and signal age/error for the just-acquired snapshot. Guard rapid repeated taps. This becomes the final capture entry point in Task 4. Show **Captured** at this stage, reserving **Reading saved** for a confirmed Room insert.

- [ ] Rerun the rule test and `./gradlew.bat :app:assembleDebug :app:lintDebug`. Expected result is passing tests and successful tasks. If published dependencies raise a compile/metadata mismatch, align the affected pinned build/dependency version and record the change and reason in this plan before continuing.

- [ ] Verify milestone 1 on a physical phone. Run `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices`, select the intended device, install through Android Studio or `./gradlew.bat :app:installDebug`, and launch the app. Record campus Wi-Fi and Mobile snapshots, observed ages, reconnect/movement behavior, unavailable/null cases, level-0 handling, a VPN attempt, and a connection switch. Check dual SIM when available. Record phone model/Android version and observed results in the verification document. If the intended phone is unavailable, continue checks possible locally and leave this milestone pending. The pilot's completion condition still requires hardware evidence.

- [ ] Commit the working signal-capture slice and its evidence with `feat: capture active Wi-Fi and mobile signal`.

## Task 2. Add bounded foreground location and final location validation

**Files**

Create `capture/LocationReader.kt` and unit test `capture/LocationRulesTest.kt`. Modify `capture/CaptureModels.kt`, `reading/ReadingRules.kt`, and the verification document.

**Interfaces**

- `interface LocationReader { fun currentAccess(): LocationAccess; suspend fun locate(): LocationResult }` and `class AndroidLocationReader(context: Context) : LocationReader` in `LocationReader.kt`.
- `fun locationReason(fix: LocationFix, access: LocationAccess, atElapsedMillis: Long): NoLocationReason?` returns null for a pin-eligible fix.
- `fun assembleReading(id: String, sample: SignalSample, location: LocationResult, access: LocationAccess): Reading` rechecks location eligibility at `sample.capturedElapsedMillis` and populates all location columns or the missing reason.

- [ ] Write `LocationRulesTest` with these assertions and named cases for out-of-range longitude, missing/negative/infinite accuracy, invalid coordinates/timestamps, and permission precedence.

```kotlin
@Test fun exactLocationLimitsQualify() {
    val access = LocationAccess(precise = true, coarse = true, servicesEnabled = true)
    val fix = LocationFix(13.78421, 121.07428, 30f, 70_000)
    assertNull(locationReason(fix, access, 100_000))
    assertEquals(NoLocationReason.LOCATION_STALE, locationReason(fix, access, 100_001))
    assertEquals(NoLocationReason.LOCATION_INACCURATE,
        locationReason(fix.copy(accuracyMetres = 30.01f), access, 100_000))
    assertEquals(NoLocationReason.LOCATION_UNAVAILABLE,
        locationReason(fix.copy(latitude = Double.NaN), access, 100_000))
    assertEquals(NoLocationReason.APPROXIMATE_ONLY,
        locationReason(fix, access.copy(precise = false), 100_000))
    assertEquals(NoLocationReason.PERMISSION_DENIED,
        locationReason(fix, access.copy(precise = false, coarse = false), 100_000))
}
```

- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests 'com.sagapmo.app.capture.LocationRulesTest'`. Expected result is failure for the missing location rules.

- [ ] Implement `locationReason` and `assembleReading`. Use the spec's reason precedence. Treat non-finite coordinates, negative/non-finite accuracy, and future/negative fix timestamps as invalid. Missing accuracy and finite accuracy above 30 metres are inaccurate. A permission downgrade outranks an earlier candidate. Preserve sample time, raw signal, and rating when dropping a pin. Wi-Fi age remains null.

- [ ] Implement `AndroidLocationReader.locate()`. Return immediately for denied, approximate-only, or disabled location access. Inspect last-known locations for enabled GPS/network providers, then request temporary foreground updates from available enabled providers when needed. Keep the best candidate, accept the first qualifying fix, and enforce a single 10-second deadline for the whole operation. Prefer a valid recent/accurate candidate, then a recent inaccurate one, then a stale otherwise-valid one for reporting. Retry quality through subsequent updates while time remains. At expiry return a retained candidate for final classification, a provider-failure reason, or timeout when no candidate arrived. Remove every listener on completion, failure, timeout, or cancellation. Use `elapsedRealtimeNanos / 1_000_000` for API-31-compatible fix time.

- [ ] Rerun `LocationRulesTest`. Expected result is passing boundary and final-attachment checks, including `assembleReading` saving a complete signal with every location column null and one reason.

- [ ] Record provider cancellation and physical permission/accuracy scenarios as pending for Task 4, where the permission launcher and capture entry point connect this adapter. Commit with `feat: validate bounded foreground location fixes`.

## Task 3. Persist readings with consistent retries

**Files**

Create `reading/ReadingDao.kt`, `reading/ReadingDatabase.kt`, `app/src/main/res/xml/data_extraction_rules.xml`, device test `reading/ReadingDaoTest.kt`, and schema `app/schemas/com.sagapmo.app.reading.ReadingDatabase/1.json`. Modify `reading/Reading.kt`, `reading/ReadingRules.kt`, `SagapMoApplication.kt`, and the manifest.

**Interfaces**

- `fun validateReading(reading: Reading): Unit` in `ReadingRules.kt` throws `IllegalArgumentException` for invalid row invariants.
- `ReadingDao.observeReadings(type: ConnectionType?): Flow<List<Reading>>`, `suspend fun getById(id: String): Reading?`, and `suspend fun save(reading: Reading): Reading`.
- DAO implementation methods `suspend fun insertIfAbsent(reading: Reading): Long` with `OnConflictStrategy.IGNORE`, plus a transaction around `save` validation/insert/existing-row comparison.
- `ReadingDatabase : RoomDatabase`, schema version 1, exposes `fun readings(): ReadingDao`. Database filename is `sagapmo.db`.

- [ ] Write a file-backed `ReadingDaoTest`. Use a dedicated test database filename and clean only that test database. Include these transactional assertions with a valid local `sampleReading(id)` fixture.

```kotlin
@Test fun saveRetryIsIdempotentAndConflictPreservesOriginal() = runBlocking {
    val original = sampleReading("same-operation")
    assertEquals(original, dao.save(original))
    assertEquals(original, dao.save(original))
    assertEquals(1, dao.observeReadings(null).first().size)
    var conflict: Throwable? = null
    try { dao.save(original.copy(rawSignalValue = -70, rating = Rating.FAIR)) }
    catch (error: IllegalArgumentException) { conflict = error }
    assertNotNull(conflict)
    assertEquals(original, dao.getById(original.id))
}
```

Add `reopenKeepsLocatedAndUnlocatedReadings`, `filtersAndTimestampTiesAreDeterministic`, `invalidRowsAreRejected`, and `concurrentIdenticalSavesCreateOneRow`. Close and reopen the file-backed database in the persistence test. Assert enum string storage, Mobile age, Wi-Fi null age, and historical ratings survive.

- [ ] Run `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sagapmo.app.reading.ReadingDaoTest`. Expected result is failure because the DAO/database behavior is absent.

- [ ] Implement the Room entity, database, and DAO. Query `ORDER BY capturedAtEpochMillis DESC, id DESC` and filter by nullable enum parameter. `save` validates, inserts if absent, and returns the persisted reading. Existing identical payloads succeed, and a different payload for the same ID fails without overwriting it. Validate type/unit/rating consistency, stored age limits, and complete location-or-reason invariants using capture-time data. Historical reads use the stored rating. Export schema version 1.

- [ ] Construct the single database in `SagapMoApplication` with app-private storage. Set `allowBackup=false` and API-31 data extraction rules excluding the database domain from cloud backup and device transfer. Verify the merged manifest points at those rules. Keep database-read errors available to the ViewModel. Preserve the database on errors and on schema-upgrade failures.

- [ ] Rerun `ReadingDaoTest` and `./gradlew.bat :app:assembleDebug :app:lintDebug`. Expected result is passing persistence/concurrency tests, generated schema, and successful tasks. Physical app-level restart verification follows in Task 4. Commit with `feat: persist local signal readings with Room`.

## Task 4. Connect capture, permissions, lifecycle, and save retries

**Files**

Create `SagapMoViewModel.kt` and unit test `SagapMoViewModelTest.kt`. Modify `MainActivity.kt`, `SagapMoApplication.kt`, and the verification document.

**Interfaces**

- `SagapMoViewModel(signalReader: SignalReader, locationReader: LocationReader, dao: ReadingDao)` uses constructor-injected sources and a fake DAO for coroutine tests.
- `CapturePhase { IDLE, AWAITING_PERMISSION, LOCATING, ACQUIRING_SIGNAL, SAVING, SAVE_FAILED, SAVED, FAILED, INTERRUPTED }`.
- Expose `uiState: StateFlow<AppUiState>`. `AppUiState` carries `capturePhase`, `captureError`, `pendingSave: Reading?`, `savedReading: Reading?`, `filter: ConnectionType?`, `destination: Destination`, `selectedReading: Reading?`, `readingsState: ReadingsState`, and `focusedReadingId: String?`. `Destination { MAP, READINGS }`; `ReadingsState` is `Loading`, `Loaded(readings: List<Reading>)`, or `Failed`.
- ViewModel actions `record(needsPermissionPrompt: Boolean)`, `onPermissionResult()`, `retrySave()`, `retryReadings()`, `onAppBackgrounded()`, `setFilter(type: ConnectionType?)`, `setDestination(destination: Destination)`, `openReading(reading: Reading)`, `closeDetails()`, and `showOnMap(reading: Reading)`.

- [ ] Write coroutine tests using simple fake hardware sources, a fake DAO, and `Dispatchers.setMain`/`runTest`. Name and assert `doubleTapCreatesOneOperation`, `permissionContinuationRunsOnce`, `backgroundDuringPermissionDoesNotCancelDialog`, `backgroundDuringLocationCancelsAndRemovesRequest`, `backgroundDuringSaveDoesNotStartAnotherInsert`, `storageFailureRetainsIdAndCandidate`, `retrySaveDoesNotRecapture`, `signalFailureDoesNotInsert`, `eachMissingLocationReasonStillSavesValidSignal`, and `readFailureCanBeRetried`. Use deferred gates to hold location and insert calls while asserting state.

```kotlin
// Assertions in retrySaveDoesNotRecapture, after the fake DAO fails once.
assertEquals(CapturePhase.SAVE_FAILED, viewModel.uiState.value.capturePhase)
val candidate = requireNotNull(viewModel.uiState.value.pendingSave)
viewModel.retrySave()
advanceUntilIdle()
assertEquals(1, fakeSignal.readCalls)
assertEquals(candidate.id, fakeDao.saved.single().id)
assertEquals(CapturePhase.SAVED, viewModel.uiState.value.capturePhase)
```

- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests 'com.sagapmo.app.SagapMoViewModelTest'`. Expected result is failure for the missing capture coordinator.

- [ ] Implement the ViewModel using one main-thread-owned acquisition job and one retained save candidate. Guard all entry points, including retries and permission callbacks. Perform the spec's early connection check, permission pause, location wait, signal read, final location revalidation, and atomic save in order. Generate a UUID once per operation. Reuse it for save retries. Permit connection changes during the location wait, and reject changes reported during signal acquisition. Success follows DAO confirmation. Preserve `CancellationException` as cancellation, and convert database failures to recoverable state. UI selection/filter changes leave acquisition and inserts consistent.

- [ ] Wire an activity-scoped ViewModel factory and a `RequestMultiplePermissions` launcher. Launch only after the ViewModel enters AWAITING_PERMISSION, recording in the operation that its request has been launched so recomposition/rotation cannot launch it twice. Request coarse/fine together and continue with actual granted access. Persist whether location permission has been requested in Activity preferences so denial does not create a prompt loop on restart. Put the app-settings action in the relevant explanation, refreshing access on return. Route Activity `onStop` to acquisition cancellation while excluding configuration recreation and the permission-dialog phase. Preserve the same ViewModel across rotation. Acquisition cancellation removes location resources, and saving already underway can finish. After process recreation, show committed rows and begin in idle state. There is no success claim for an unconfirmed save.

- [ ] Rerun the ViewModel tests and the unit suite. Expected result is passing state, retry, and cancellation assertions. Run the app with precise, denied, one-time, approximate, revoked, disabled, timed-out, stale, and inaccurate location scenarios. Confirm valid signal values save without pins. Rotate during capture, switch destinations while saving, and background during location acquisition. Force-stop, reopen, reboot, and reopen again to verify saved records.

- [ ] Complete milestone 2 results in the verification document, leaving any device-only case lacking evidence pending. Commit with `feat: coordinate permission-aware capture and save retries`.

## Task 5. Build the shared readings list and details

**Files**

Create `ui/SagapMoApp.kt`, `ui/ReadingsScreen.kt`, `ui/ReadingDetails.kt`, and device/UI test `ui/SagapMoAppTest.kt`. Modify `MainActivity.kt`, `SagapMoViewModel.kt`, and `app/src/main/res/values/strings.xml`.

**Interfaces**

- `@Composable fun SagapMoApp(viewModel: SagapMoViewModel)` owns the two destinations and the shared detail sheet.
- `@Composable fun ReadingsScreen(state: AppUiState, onRecord: () -> Unit, onFilter: (ConnectionType?) -> Unit, onOpen: (Reading) -> Unit, onRetry: () -> Unit)`.
- `@Composable fun ReadingDetails(reading: Reading, onDismiss: () -> Unit, onShowOnMap: (Reading) -> Unit)`.
- Task 4 actions supply selection/navigation. `openReading` adjusts a hiding filter to All so **Show saved reading** can reveal its row.

- [ ] Write UI tests for empty database, no matches for each filter, newest-first order, save progress, missing-location explanations, localized full date/time in details, **Retry save**, and read-error retry. Include the hidden-filter regression and exact-coordinate duplicate rows.

```kotlin
// In recordsMobileWhileWifiFilterIsSelected, using injected fake hardware.
composeRule.onNodeWithText("Wi-Fi", useUnmergedTree = true).performClick()
composeRule.onNodeWithText("Record reading").performClick()
composeRule.onNodeWithText("Show saved reading").performClick()
composeRule.onNodeWithText("Level 3/4").assertIsDisplayed()
assertEquals(ConnectionType.MOBILE, fakeDao.saved.single().connectionType)
```

- [ ] Run `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sagapmo.app.ui.SagapMoAppTest`. Expected result is failure for missing screens/semantics.

- [ ] Implement a Material 3 scaffold with **Map** and **Readings** destinations, shared All/Wi-Fi/Mobile filters, and **Record reading**. Both destinations remain navigable during capture. Display all stored fields required by the spec in list/details. Show **Level N/4** for Mobile and **N dBm** for Wi-Fi. Details include accuracy/location age and signal age or **Wi-Fi measurement age unavailable**. Show **Show on map** only for usable coordinates. Until Task 6 connects Map, keep its surface visibly labeled as pending implementation in development builds.

- [ ] Observe one filtered Room stream in the shared ViewModel, sharing its committed rows with both destinations. Catch read failures and recreate collection on **Retry**. Preserve selected reading details when navigation requires them. Implement **Show saved reading** and **Show on map** through the shared actions, and dismiss the detail sheet with Android Back. Format UTC timestamps in the phone's locale/time zone. Use text with rating colors and meaningful TalkBack labels for controls/status, with flexible layout for large text.

- [ ] Rerun the UI test class and unit suite. Verify system font at 200%, TalkBack traversal and announcements, and 48 dp targets. Expected result is all rows/actions accessible and usable, including unlocated readings and exact-coordinate duplicates. Commit with `feat: add readings filters and shared reading details`.

## Task 6. Render the campus map from saved readings

**Files**

Create `map/CampusMapScreen.kt` and device/UI test `map/CampusMapScreenTest.kt`. Modify `ui/SagapMoApp.kt`, `SagapMoApplication.kt`, and the verification document.

**Interfaces**

- `@Composable internal fun CampusMapScreen(readings: List<Reading>, focusedReadingId: String?, onOpenReading: (Reading) -> Unit, onViewReadings: () -> Unit, runtime: MapRuntime = DefaultMapRuntime.instance, baseStyle: BaseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"))`. The last two parameters allow controlled map integration tests.
- Task 5 supplies the same filtered saved rows and `showOnMap` selection. The map returns the selected actual reading to the common details sheet.

- [ ] Write an integration test with a controlled local test style and located/unlocated test rows. Assert located rows produce named clickable geographic pin controls, unlocated rows produce none, a selected pin opens the same stored values as the list, and map attribution remains accessible. Use test-only fixtures, separate from the actual pilot database.

- [ ] Run `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sagapmo.app.map.CampusMapScreenTest`. Expected result is failure for the missing map integration.

- [ ] Implement `CampusMapScreen` using MapLibre Compose 0.20.0 `rememberMapState`, `BaseStyle.Uri`, and `MaplibreMap`. Initialize the camera at longitude 121.07428, latitude 13.78421 and zoom 17, observing the API's longitude-first `Position` order. Display per-reading geographic Compose overlays so each pin can carry a native accessible name and tap action. Include `MapOverlay.Default` in the custom overlay to preserve the attribution controls while adding pins. Use `GeographicLayout`/geographic placement APIs documented for this version, and remember camera state across recomposition. [Map state](https://maplibre.org/maplibre-compose/api/lib/maplibre-compose/org.maplibre.compose.map/remember-map-state.html) · [Geographic overlays and attribution](https://maplibre.org/maplibre-compose/controls/)

- [ ] Connect `focusedReadingId` to a camera move for **Show on map**. Retain the current camera during filter/database updates. Add the short explanation that pins represent individual recorded samples. Keep exact-coordinate records individually reachable from Readings. Connect both destinations to the common detail sheet.

- [ ] Rerun `CampusMapScreenTest`, then verify milestone 3 outdoors. Visit three recognizable campus spots, attempt both transports where available, and verify at least one correctly placed pin of each type. Compare raw values, ratings, IDs, and times with the list. Check all filters, unlocated entries, attribution, the starting camera, and actual landmark labels. Record evidence before marking the milestone passed. Commit with `feat: map saved campus signal readings`.

## Task 7. Handle map failures and verify the complete pilot

**Files**

Create `map/MapLoadState.kt` and unit test `map/MapLoadStateTest.kt`. Modify `map/CampusMapScreen.kt`, `map/CampusMapScreenTest.kt`, `ui/SagapMoAppTest.kt`, and the verification document.

**Interfaces**

- `MapLoadStatus { LOADING, READY, FAILED }` and `MapLoadState(attempt: Int, status: MapLoadStatus)`.
- `MapLoadEvent { STARTED, STYLE_FAILED, PARTIAL_FRAME, FULL_FRAME, DEADLINE }`.
- `fun nextMapLoadState(state: MapLoadState, event: MapLoadEvent, attempt: Int): MapLoadState` ignores events for an older attempt. STARTED begins the supplied newer attempt in LOADING. A current FULL_FRAME becomes READY, including after timeout. PARTIAL_FRAME changes READY to LOADING and otherwise preserves the current state. STYLE_FAILED becomes FAILED, and DEADLINE becomes FAILED only while still loading.

- [ ] Write `MapLoadStateTest` with `deadlineChangesLoadingToFailed`, `lateFullFrameClearsTimeout`, `oldAttemptCannotReplaceRetryState`, `readyMapSurvivesOldDeadline`, and `partialFrameChangesReadyToLoading`. Feed the actual reducer events and assert its state transitions. The device test below verifies the screen's real timer wiring, including repeated partial frames.

```kotlin
@Test fun staleEventsCannotUndoRetryOrRecovery() {
    val retry = MapLoadState(attempt = 2, status = MapLoadStatus.LOADING)
    assertEquals(retry, nextMapLoadState(retry, MapLoadEvent.STYLE_FAILED, 1))
    val failed = nextMapLoadState(retry, MapLoadEvent.DEADLINE, 2)
    assertEquals(MapLoadStatus.FAILED, failed.status)
    val recovered = nextMapLoadState(failed, MapLoadEvent.FULL_FRAME, 2)
    assertEquals(MapLoadStatus.READY, recovered.status)
    assertEquals(recovered, nextMapLoadState(recovered, MapLoadEvent.DEADLINE, 2))
}
```

- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests 'com.sagapmo.app.map.MapLoadStateTest'`. Expected result is failure for the missing reducer/timer behavior.

- [ ] Implement the reducer and wire MapLibre `MapState.events`. Begin collecting events before attaching the map surface, and keep the event handler brief because past events are not replayed. `MapEvent.StyleLoadFailed` fails immediately. `MapEvent.FrameRendered` with `RenderStats.Mode.Full` marks the visible basemap usable. Partial frames start/continue the bounded loading wait, including after a pan to unloaded content. Observe persistent `mapState.style.loadState` to catch a style failure as well. A failed style or 15 seconds of incomplete tile/glyph/sprite loading exposes **Map could not load**, **Retry map**, and **View readings**. Cancel the timer on readiness/disposal, and start it once per transition into LOADING. A resource failure causing incomplete frames is covered by this deadline. Never infer map success from `StyleLoaded` or `Idle` alone. Use a nonblocking error banner over rendered content. [Map events and render completion](https://raw.githubusercontent.com/maplibre/maplibre-compose/v0.20.0/lib/maplibre-compose/src/commonMain/kotlin/org/maplibre/compose/map/MapEvent.kt) · [Event flow behavior](https://maplibre.org/maplibre-compose/api/lib/maplibre-compose/org.maplibre.compose.map/-map-state/index.html)

- [ ] Implement retry by recreating the map attempt with the same requested camera and selected reading, cancelling the old event collection/timer. Make the attempt ID guard all callbacks. Keep existing rendered content until retry begins. Dispose map resources when leaving the map screen. A return creates a current attempt, with saved readings still coming from Room.

- [ ] Add device tests using test-only `MapResourceProvider` failure/delay fixtures and a local test style through Task 6's runtime/style parameters. Exercise style failure, tile/glyph/sprite failure, a stalled resource, late recovery, and retry after navigating away. In `stalledMapExposesRetryAfter15Seconds`, measure elapsed realtime from loading start and assert retry is absent before the deadline and appears after 15 seconds within a 20-second test allowance. Repeated partial frames must not postpone that deadline, and a full frame cancels it. Verify **View readings**, **Record reading**, list filters, and details all remain usable. Use the library's resource-provider hooks and test-runner arguments, with fakes confined to tests. [Map resource hooks](https://maplibre.org/maplibre-compose/requests/)

- [ ] Rerun `MapLoadStateTest` and the affected map/UI test classes. Expected result is passing failure/recovery tests and no leaked collectors or duplicate captures.

- [ ] Verify milestone 4 on the physical phone. Block basemap access independently of the phone's signal-bearing default network. Confirm the banner, retry, saved list, and new valid reading save. Cold-start in airplane mode and inspect stored readings, filters, and details. Restore network/map access and verify recovery. Re-enable Wi-Fi in airplane mode only if testing new Wi-Fi capture there, and record whether Android provides a supported default connection.

- [ ] Run the final build checks once after all changes with `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and `./gradlew.bat :app:connectedDebugAndroidTest`. Expected result is `BUILD SUCCESSFUL`, passing test reports, and a runnable debug APK. Inspect merged permissions/backup rules and exported Room schema. Complete actual app checks for rotation, rapid taps, save/read failures, process termination around insert, persistence after reboot, TalkBack, large text, and visible attribution.

- [ ] Populate the verification document with actual commands/results, test report paths, device/Android version, signal observations, campus checks, and remaining pending checks. Reference `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/androidTests/connected/`, and `app/build/reports/lint-results-debug.html`. Call the pilot complete only when all four physical milestones have passed. Commit with `feat: recover from map failures and verify pilot flow`.

## Coverage and execution handoff

| Approved requirement | Owning task |
|---|---|
| Real default-connection capture, cellular subscription, raw units, ratings/freshness | 1 |
| Foreground location, denied/approximate access, missing-pin reasons | 2 and 4 |
| Local data model, historical values, atomic/idempotent storage, backup controls | 3 |
| One capture per tap, permissions, lifecycle, save/read retry | 4 |
| Shared database/filter, list ordering, empty states, accessible details | 5 |
| Campus map, camera, selected reading, pins and attribution | 6 |
| Tile failure, bounded loading, retry, offline reading access | 7 |
| Phone Wi-Fi/Mobile evidence | Milestone 1 in Task 1 |
| Restart/reboot persistence and location failures | Milestone 2 in Task 4 |
| Three-spot campus survey | Milestone 3 in Task 6 |
| Independent basemap failure and airplane-mode cold start | Milestone 4 in Task 7 |

Plan self-review checks spec coverage, defined interfaces, exact threshold values, every Review Focus regression's owning test, and test/build/device commands. Hardware checks remain pending until execution supplies evidence.

Implementation awaits the user's direction. Native execution in this session is recommended because these tasks share capture and reading interfaces, and the device milestones require sequential integration. Subagent-driven execution is also available if the user chooses independent task reviews. Start execution using the corresponding Superpowers execution skill, and establish any needed isolated worktree at that point.
