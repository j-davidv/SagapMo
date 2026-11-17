# SagapMo? pilot design

Date 2026-11-17. Approved by the user on 2026-11-17. Implementation starts after separate user direction.

## Purpose and success

Students at Batangas State University, Alangilan Campus, can tap **Record reading**, save a real signal measurement from their Android phone, and find it again after reopening the app. Readings with a usable outdoor location appear as tappable map pins. Every saved reading appears in the readings list.

The pilot succeeds when a physical phone captures both campus Wi-Fi and mobile readings, preserves them across restarts, displays correctly placed pins for both connection types, and keeps saved readings accessible when the basemap fails.

## Confirmed constraints and scope decision

- Use MapLibre and a basemap that requires no billing account.
- Store individual readings locally on the device. The pilot has no accounts, server, or shared measurements.
- The user selected offline access to saved readings as sufficient for the pilot on 2026-11-17.
- Capture one reading for each completed, user-initiated recording operation.
- Preserve valid measurements when a usable location is unavailable. Explain the missing pin.
- Leave areas without readings unsampled.

## Approaches considered

| Approach | Benefit | Cost | Decision |
|---|---|---|---|
| Local readings with an online basemap | Proves capture, persistence, and mapping with a small first release | Basemap availability depends on connectivity and cached assets | Selected by the user |
| Local readings with a downloadable campus map pack | Supports a deliberately downloaded campus basemap offline | Adds download progress, storage, incomplete-download handling, and map-pack verification | Deferred by the user's scope decision |

The proposed implementation follows the brief's native Android stack. Kotlin, Compose, Room, and MapLibre Compose keep the signal and permission handling close to Android's platform APIs. Stack approval is part of reviewing this document.

## Assumptions for review

These are proposed pilot defaults. Approval accepts them for the first implementation, with hardware validation required before declaring the pilot successful.

| Decision | Proposed default | Reason |
|---|---|---|
| Platform | Android 12 / API 31 minimum | Fits the proposed pilot phone and limits compatibility branches |
| App stack | Kotlin, Jetpack Compose, Room over SQLite, MapLibre Compose | Follows the supplied proposal |
| Basemap | OpenFreeMap Liberty at `https://tiles.openfreemap.org/styles/liberty` | Public basemap with no API key or registration |
| Connection selection | Android's active default network at signal acquisition | Gives a repeatable instruction for choosing Wi-Fi or Mobile |
| Wi-Fi Good | RSSI at least -65 dBm | Supplied pilot threshold |
| Wi-Fi Fair | RSSI at least -75 dBm and below -65 dBm | Supplied pilot threshold |
| Wi-Fi Poor | Valid RSSI below -75 dBm | Supplied pilot threshold |
| Mobile Good / Fair / Poor | Android level 3-4 / 2 / 1 | Supplied pilot thresholds |
| Mobile level 0 | Unavailable for this pilot | Conservatively avoids treating an unknown value as a measured Poor reading |
| Maximum cellular measurement age | 60 seconds, inclusive | Supplied pilot freshness limit |
| Location request deadline | 10 seconds after permission handling | Bounds the recording delay |
| Maximum location age | 30 seconds, inclusive, at signal acquisition | Supplied pilot freshness limit |
| Maximum reported horizontal accuracy | 30 metres, inclusive | Supplied outdoor pin criterion |
| Initial camera | Latitude 13.78421, longitude 121.07428, zoom about 17 | Supplied starting position, requiring on-site confirmation |

OpenFreeMap documents its public service and Liberty style. MapLibre Compose renders through MapLibre Native on Android, and its evolving API requires pinning a compatible dependency version during planning. [OpenFreeMap service](https://openfreemap.org/) · [Liberty setup](https://openfreemap.org/quick_start/) · [MapLibre Compose overview](https://maplibre.org/maplibre-compose/)

## Capture flow

Both the map and readings list expose **Record reading** through the same capture operation.

1. Create an operation ID and disable further recording while it is active. Check that the current default network is a supported Wi-Fi or cellular connection. A missing or unsupported connection produces a message and a retry action.
2. Explain that precise foreground location enables an outdoor pin. Request coarse and fine location together when a permission request is appropriate. Browsing saved readings requires no runtime permission. A denial or approximate grant continues the capture with a missing-location reason.
3. With precise permission and location services enabled, accept a qualifying recent fix or request a current fix within the 10-second deadline. Show **Getting location** and ask the student to stay at the sampling spot until recording finishes. Expiry continues with a missing-location reason.
4. Acquire a signal snapshot from the current default network. Record the UTC acquisition time here, after permission handling and the location wait. Recheck the network identity and, for Mobile, the active data subscription around acquisition. A change during signal acquisition fails the attempt with **Connection changed. Try again.** A change during the preceding location wait is allowed, and the acquired connection type is shown in the result.
5. Validate the signal and its available freshness information. Revalidate the candidate location against the acquisition instant. Invalid signal data ends the operation with a specific retry message. A valid signal proceeds with either a qualifying location or its missing-location reason.
6. Save the complete reading in one Room insert using the operation ID as its primary key. Show **Saving reading** until the insert is confirmed.
7. Show **Reading saved** with connection type, rating, raw value, and either pin availability or the missing-location explanation. Refresh the map and list from the database.

This records Android's available signal snapshot in response to a tap. Android controls modem and Wi-Fi update timing. Cellular cache age is checked explicitly, and Wi-Fi's timestamp limitation is disclosed below. [Android cellular signal acquisition](https://developer.android.com/reference/android/telephony/TelephonyManager#getSignalStrength())

The UI retains a single operation across rotation and screen switching. Once a database insert starts, navigation within the app does not start another operation. Location requests and temporary network observation end when acquisition ends or is cancelled. Backgrounding before the save phase cancels acquisition and reports interruption on return. A system permission dialog is part of permission handling and does not trigger that cancellation.

After a storage failure, retain the candidate and its ID in memory and offer **Retry save**. That retries the same insert without reacquiring a measurement. Treat an already-present identical ID as the existing saved result. A signal retry starts a new acquisition. Each successful insert is atomic, so process termination leaves either a complete record or no record. Only confirmed saves receive success feedback. An uncommitted candidate can be lost when the process terminates.

## Signal rules

### Connection selection

Use `ConnectivityManager.activeNetwork` and its capabilities. Select Wi-Fi or cellular only when the default network identifies one supported physical transport. VPN, Ethernet, missing capabilities, and ambiguous transport combinations produce an unsupported/unavailable message. For a VPN, explain that the pilot needs it disconnected to identify the connection reliably.

Students choose the connection through Android settings. To collect Mobile, turn Wi-Fi off and wait for cellular to become the default connection. Filters affect displayed records only. Internet validation and basemap reachability are independent of signal eligibility. A supported default network can supply a reading even when internet access is unavailable. [ConnectivityManager](https://developer.android.com/reference/android/net/ConnectivityManager#getActiveNetwork()) · [Network capabilities](https://developer.android.com/reference/android/net/NetworkCapabilities)

### Wi-Fi

Obtain the connected network's `WifiInfo` from the capabilities associated with the selected default network, and read `rssi` in dBm. Re-read this information for each capture. A Wi-Fi network being enabled, or an old snapshot from a previous operation, is insufficient. Missing or redacted required data is unavailable. Identity fields such as SSID and BSSID are unnecessary for recording eligibility.

As an additional proposed validation rule, accept integer RSSI strictly between -127 and 0 dBm. Reject -127, zero, positive, and out-of-range values as unavailable. Validate this conservative range against the pilot phone before changing it. Apply rating thresholds only after validation. [WifiInfo RSSI](https://developer.android.com/reference/android/net/wifi/WifiInfo#getRssi())

`WifiInfo` exposes no per-RSSI measurement timestamp. Acquisition uses a snapshot fetched during the current operation and verifies that the default network remains selected. This checks connection consistency, while the age of the underlying radio measurement remains unknown. Store that age as null and display **Wi-Fi measurement age unavailable** in reading details. If testing shows that current API snapshots stay stale on the pilot phone, the Wi-Fi milestone remains unresolved until the acquisition method is revised and verified.

### Mobile

Resolve `SubscriptionManager.getActiveDataSubscriptionId()` and use a `TelephonyManager` created for that subscription. Read the overall `SignalStrength.level` and its measurement timestamp. Invalid subscription, absent radio hardware, null signal, or an exception produces an unavailable result. Recheck the active subscription before accepting the sample. The active data subscription can differ from the configured default subscription. [Active data subscription](https://developer.android.com/reference/android/telephony/SubscriptionManager#getActiveDataSubscriptionId()) · [Subscription-specific telephony manager](https://developer.android.com/reference/android/telephony/TelephonyManager#createForSubscriptionId(int))

Accept levels 1 through 4 only. Compute age from `SignalStrength.timestampMillis` and `SystemClock.elapsedRealtime()` in the same boot session. An untrustworthy timestamp, timestamp in the future, or age above 60,000 ms is unavailable/stale. Store the validated age at acquisition for inspection after restart. Apply the separate Mobile rating table and display the raw value as, for example, **Level 3/4**. The overall Android level can incorporate multiple radio technologies. [SignalStrength level and timestamp](https://developer.android.com/reference/kotlin/android/telephony/SignalStrength)

Level 0 is conservatively excluded under the supplied pilot policy. It can represent very poor or unknown signal depending on the Android signal API and report. This means the pilot cannot count level-0 outcomes as recorded Poor measurements. [Android cellular signal levels](https://developer.android.com/reference/android/telephony/CellSignalStrength)

## Location rules and permissions

Use Android's platform location service for a bounded foreground fix, selecting an available enabled provider. Provider selection and cancellation wiring belong to the implementation plan. Evaluate cache age with elapsed realtime, using API-31-compatible timestamp access. A provider result still passes the app's validation rules. [Current location API](https://developer.android.com/reference/android/location/LocationManager#getCurrentLocation(java.lang.String,android.os.CancellationSignal,java.util.concurrent.Executor,java.util.function.Consumer)) · [Monotonic location timestamp](https://developer.android.com/reference/android/location/Location#getElapsedRealtimeNanos())

A usable pin requires precise foreground permission, enabled location services, finite coordinates within latitude [-90, 90] and longitude [-180, 180], a valid monotonic timestamp with age 0-30,000 ms, and a present, finite horizontal accuracy in [0, 30] metres. Apply these checks at signal acquisition. The reported accuracy is an estimate, so field placement still needs verification.

Otherwise all location columns are null and `noLocationReason` explains why. Choose the first applicable reason in this order.

| Stored reason | Condition | Student-facing explanation |
|---|---|---|
| `PERMISSION_DENIED` | Foreground location denied or revoked | Location permission unavailable. Saved without a pin. |
| `APPROXIMATE_ONLY` | Coarse access granted without precise access | Precise location is needed for a campus pin. Saved without a pin. |
| `LOCATION_DISABLED` | Device location services disabled | Location services are off. Saved without a pin. |
| `LOCATION_UNAVAILABLE` | No usable provider or provider failure/invalid result | Location unavailable. Saved without a pin. |
| `LOCATION_STALE` | Best otherwise valid candidate is older than 30 seconds at acquisition | Location was too old. Saved without a pin. |
| `LOCATION_INACCURATE` | Best recent candidate has missing or worse-than-30-metre accuracy | Location was too imprecise. Saved without a pin. |
| `LOCATION_TIMEOUT` | Deadline reached without a candidate | Location timed out. Saved without a pin. |

While the deadline remains, allow a better fix to replace a stale or inaccurate candidate. A permission change takes precedence over any candidate. A valid reading without a pin remains in the list permanently, and collecting a later location creates a separate reading through the normal capture flow.

Request `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` together on Android 12+. Handle denial, one-time grants, approximate grants, and revocation. Avoid repeated permission prompts after denial. Explain how to enable precise access through app settings when the student chooses to fix it. [Android runtime location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)

Declare normal network access permissions needed for capabilities, Wi-Fi state, and basemap requests. Use the minimum permissions required by the selected APIs. The baseline `getSignalStrength()` path does not justify requesting broad phone data access. Any API-specific permission restriction discovered on the pilot phone becomes a clear capture error and a planning adjustment. If location restrictions also prevent obtaining a valid Wi-Fi measurement, explain the signal failure and allow retry.

## Local data model

One Room `readings` table is the source for map and list. Store enums as stable strings.

| Field | Storage | Rule |
|---|---|---|
| `id` | Text primary key | UUID assigned once per operation and reused for save retries |
| `capturedAtEpochMillis` | Integer, required | UTC time of validated signal acquisition |
| `connectionType` | Text, required | `WIFI` or `MOBILE` |
| `rawSignalValue` | Integer, required | RSSI or Android level, validated for its type |
| `signalUnit` | Text, required | `DBM` for Wi-Fi, `ANDROID_LEVEL` for Mobile |
| `rating` | Text, required | `GOOD`, `FAIR`, or `POOR` under the rules applied at capture |
| `signalAgeMillisAtCapture` | Nullable integer | Validated Mobile age, null for Wi-Fi |
| `latitude`, `longitude` | Nullable real numbers | Present together only for usable pins |
| `accuracyMetres` | Nullable real number | Present only for usable pins |
| `locationAgeMillisAtCapture` | Nullable integer | Present only for usable pins |
| `noLocationReason` | Nullable text | Exactly one reason when location columns are null |

Validate row invariants in the common capture/save path. Store all location attributes together or store the reason. Capture timestamps and ratings remain historical after restart. If rating policy changes during pilot tuning, add a policy version/migration before saving under changed rules so older records retain their meaning.

Query newest capture time first, with ID as a deterministic tie-breaker. Query by connection type for the shared filter. Both views observe the same committed data. Room handles local persistence over SQLite. [Room overview](https://developer.android.com/training/data-storage/room)

Keep the database in app-private storage. As an additional proposal to honor device-local readings, exclude it from automatic cloud backup and device transfer where platform controls allow. Collect only the listed attributes. Basemap requests necessarily disclose requested map tile areas to the tile provider. Reading records are stored on the phone. Clearing app data or uninstalling removes the local collection, and the pilot has no recovery/export feature.

## Map and readings list behavior

The initial destination is **Map**, with an always-accessible **Readings** destination. Both expose **All**, **Wi-Fi**, and **Mobile** filters and **Record reading**. Share the filter during the current session. Capture works independently of the chosen filter. When the captured type is hidden, the save message names it and offers **Show saved reading**, which opens its details and adjusts the filter as needed.

The map starts at the proposed campus camera. Only rows with complete usable coordinates become pins. Tapping a pin opens details with connection type, rating text, raw value and unit, full local date/time, reported accuracy, location age, and signal age or its unavailable explanation. Preserve the attribution supplied by the basemap sources, including OpenFreeMap, OpenMapTiles, and OpenStreetMap. [OpenFreeMap setup and attribution example](https://openfreemap.org/quick_start/)

The list shows every saved row, newest first. Each entry includes connection type, rating text, raw value, capture time, and location status. Tapping an entry opens the same reading details. A located entry offers **Show on map**. Entries without coordinates show their stored reason. Overlapping pins remain discoverable through the list, including exact-coordinate duplicates.

An empty database explains **No readings yet** and presents **Record reading**. A filter with no matching rows explains **No Wi-Fi readings** or **No Mobile readings** and offers **All**. The map's unsampled areas carry no invented quality overlay. Include a brief map explanation that pins represent individual recorded samples.

Use rating names alongside consistent colors. All controls have accessible names and normal Android touch targets. Details and recording status support TalkBack and larger system text. Visual styling can be settled during implementation using standard Compose components, with outdoor readability as the priority.

The supplied camera and landmark names require campus validation. Use basemap-provided labels, and add custom landmarks only after their names and positions have been checked on site.

## Offline behavior and failure states

Saved readings, filters, and details operate from Room without internet access. Map cache contents may render opportunistically. Complete campus basemap availability is outside the approved pilot scope.

| Condition | Required behavior |
|---|---|
| Basemap loading | Show loading feedback. Keep Readings and recording accessible. |
| Style, tile, glyph, or sprite request fails | Show **Map could not load** with **Retry map** and **View readings**. Preserve any already-rendered map content and saved data. |
| Map requests stall | Additional proposed default of 15 seconds without a usable visible basemap exposes the same retry/readings actions. Late success clears the failure feedback. |
| No supported default connection | Explain connection unavailability or unsupported transport. Offer retry. |
| Missing, invalid, or stale signal | Explain the specific issue and offer retry. Create a row only after a valid signal is acquired. |
| No usable location | Save a valid signal with its missing-location reason. |
| Storage insert fails | Explain **Reading could not be saved** and offer **Retry save** for the retained candidate. |
| Database read fails | Show an explicit saved-readings error and retry. Preserve the database for diagnosis and recovery. |
| Capture interrupted before saving | Explain interruption on return and offer a new capture. |

Determine map loading errors from MapLibre resource/loading events and a bounded loading timer. The implementation plan chooses the exact supported events for the pinned SDK. A connectivity indicator alone cannot determine that tiles loaded successfully.

Airplane mode verifies saved-reading access. A new sample in airplane mode depends on Android exposing a supported default connection and a valid signal, such as re-enabled Wi-Fi. Offline access to stored readings requires neither.

## Acceptance checks and pilot milestones

These are required verification outcomes for the later implementation plan. Hardware checks require the team's physical Android phone.

| Milestone | Verification and acceptance |
|---|---|
| 1. Signal capture | On the pilot phone, capture real Wi-Fi RSSI and a Mobile level from the active data subscription. Check null/invalid data, cellular age, level 0, network switching during acquisition, and VPN behavior. Where dual SIM is available, verify active-data selection. Review Wi-Fi snapshot behavior after reconnecting and moving between sampling spots. |
| 2. Location and persistence | Save located and unlocated readings. Force-stop and reopen, then reboot and reopen. Confirm IDs, raw values, ratings, reasons, and times persist. Exercise permission denial, approximate access, disabled services, timeout, stale fixes, and inaccurate fixes. A valid signal survives every unusable-location outcome. |
| 3. Map and list | Walk to three recognizable outdoor spots. Attempt Wi-Fi and Mobile at each where available. Verify at least one correctly placed pin for each type, agreement between pins and list details, all three filters, newest-first ordering, no-pin entries, overlapping locations through the list, and visible attribution. Confirm camera and landmarks on site. |
| 4. Tile failure and offline access | Separately block basemap requests while leaving a signal-bearing connection available. Confirm the map error, retry, usable list, and ability to save a valid reading. Cold-start in airplane mode and inspect all stored readings and details. Restore map access and verify retry recovery without duplicated or lost records. |

Use targeted automated checks for exact boundaries and save consistency. Wi-Fi -65 is Good, -66 and -75 are Fair, and -76 is Poor. Invalid RSSI never reaches the rating table. Mobile 1/2/3/4 maps to Poor/Fair/Good/Good, and 0 is unavailable. Age boundaries of 30,000 ms for location and 60,000 ms for Mobile are inclusive. Reject future timestamps and non-finite coordinates/accuracy. Accuracy 30 metres qualifies, while a value above 30 does not. Verify one row for repeated callbacks/save retries with one operation ID, plus Room persistence and identical map/list filtering. Automated fixtures stay isolated from real pilot data.

Check rotation during capture, rapid repeated taps, screen switching during save, cancellation on backgrounding, process termination around insert, storage failure, and database-read failure. Verify success feedback follows a confirmed insert. Check TalkBack, large text, rating labels, and attribution visibility.

Record the phone model, Android version, connection type, observed values/ages, and acceptance results during the pilot. Approve threshold changes based on those observations before presenting ratings as calibrated for that device.

## Deferred scope and builder discretion

Later releases can add downloadable campus maps, manual building/floor selection, indoor plans, heat zones, speed and latency tests, background scanning, shared readings, accounts, export, and record-management actions. The pilot delivers the capture, save, list, and map flow described here.

The implementation plan may choose compatible pinned dependency versions, package names, small class boundaries, location-provider selection, map event wiring, marker rendering, and standard Compose styling. Behavioral changes to thresholds, supported Android versions, transport selection, or the local-only model require a design revision.

After approval of this spec, use Superpowers writing-plans to produce implementation milestones with verification for each. Begin implementation only when the user directs it.
