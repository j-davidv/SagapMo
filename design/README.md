# SagapMo? pilot UI reference

Open [index.html](index.html) directly in a browser. No install, build, server, API key, or network access is required. The prototype and all assets are local.

This is a design reference for the future Android application. It does not measure signal, request device permissions, load real map tiles, or persist readings. Sample data resets on reload. Select **Reset demo** to restart the first-use flow.

The behavioral authority remains the [approved pilot spec](../docs/superpowers/specs/2026-11-17-sagapmo-pilot-design.md). The original `design-ref/` files and approved spec were preserved. The user selected a clickable prototype, design guide, and direct construction on 17 November 2026.

## What to open

| Artifact | Use |
|---|---|
| [Interactive reference](index.html) | Tap pins and rows, record an illustrative sample, try filters and failure recovery. |
| [Design system](../DESIGN.md) | Color roles, type, spacing, shapes, component rules, light/dark mapping. |
| [Map](screenshots/01-map.png) | Main recording destination. |
| [Readings](screenshots/02-readings.png) | Newest-first collection, including samples without pins. |
| [Details](screenshots/03-detail.png) | Shared detail sheet with complete capture metadata. |
| [Location explanation](screenshots/04-location-explainer.png) | First recording, before the native Android permission dialog. |
| [Recording](screenshots/locating.png) | Active progress with a clear next step. |
| [No-pin outcome](screenshots/saved-no-pin.png) | A valid measurement remains a successful saved reading. |
| [Map failure](screenshots/map-error.png) | Recovery while readings remain available. |
| [First use](screenshots/empty.png) | An empty collection with the recording action in its usual place. |
| [Dark map](screenshots/dark-map.png) / [dark readings](screenshots/dark-readings.png) | Role-based dark appearance, not a color inversion. |
| [Desktop](screenshots/desktop.png) / [compact browser](screenshots/mobile.png) | The reference workbench adapts around the app preview. |

## Main improvements

- Keep the supplied collegiate crimson, but reserve it for recording, selected navigation, and recovery actions. Rating colors communicate measurement quality separately.
- Replace SSIDs, carriers, channels, bands, eNodeB, RSRP, packet loss, ping, sync, and export with fields the approved pilot actually stores. This avoids implying unavailable capabilities.
- Put connection type, rating name, raw value, time, and location status on each row. Use sentence case and plain explanations instead of internal enum names.
- Use one consistent recording position and one shared details surface. Scanning stays simple; technical metadata appears when a student asks for it.
- Treat location as optional for saving a valid signal. A missing pin is explained separately from acquisition and storage failures.
- Keep unsampled map areas neutral. No heatmap, inferred dead zones, live quality indicator, or invented landmark coordinates.

## App anatomy

The bottom bar follows the supplied floating pill reference: Map (home icon), a raised central Record reading button, and Readings (list icon). Icon-only controls retain accessible names and hover titles. The existing capture flow and duplicate-recording guard apply to the central button.

Reference phone: 412 × 846 CSS pixels; this is a layout reference, not an instruction to fix the native viewport size. The artwork is an explicitly labeled schematic. The production app must use the approved basemap and preserve its actual attribution.

| Region | Reference | Native implementation |
|---|---|---|
| System areas | 34-pixel status area, 20-pixel gesture area | Real system bars and `WindowInsets`; never draw the fake clock or gesture bar. |
| Header | Product name, campus, local-storage context | Compact top app bar; grow for system text. Wordmark is text, not a final logo asset. |
| Filters | All / Wi-Fi / Mobile; 48-pixel minimum height | Material `FilterChip` or equivalent accessible single-selection group. Share selection across destinations. |
| Content | Map or scrollable readings list | MapLibre viewport / `LazyColumn` observing Room. |
| Recording panel | Persistent hint and 52-pixel filled action | Bottom-anchored `Button`, 48 dp minimum target; apply navigation insets. |
| Navigation | Map and Readings | Keep the spec's two destinations. Show selected icon background plus label; do not add a filler destination. |
| Details/outcome | Bottom sheet, scrolls when needed | `ModalBottomSheet`, standard Android Back dismissal, TalkBack focus management. |

For the native app, use dp for spacing and sp for text. Prefer Android Roboto through Material typography; the browser uses the local system sans for reliable offline rendering. Map reference text roles to Material title/body/label roles, preserving scale and hierarchy. Use the reusable tokens in `DESIGN.md` rather than copying the browser's workbench layout.

At compact widths keep one column. At larger Android window sizes use an adaptive scaffold and consider a list/detail split; exact tablet composition is deliberately not claimed by the phone reference. Give the map, list, and sheet available space instead of hardcoding the reference height. When system text grows, allow headings, values, and metadata rows to wrap. Never sacrifice the 48 dp targets or accessible readings list to preserve map height.

## Core interactions

1. Start on Map. All filters show saved samples; only complete usable locations become pins.
2. **Record reading** measures the active default connection. Changing a display filter does not change that connection. The workbench's **Simulated active connection** control demonstrates this distinction; it is not an in-app selector.
3. On first use explain precise foreground location, then invoke the native permission UI. The reference's location buttons simulate the outcomes. Denial or approximate permission continues with the appropriate no-pin reason if the signal is valid.
4. Show **Getting location**, acquire the signal after the bounded location wait, then **Saving reading**. Disable repeated recording. **Keep browsing** demonstrates that an operation survives changing destinations.
5. A confirmed insert leads to **Reading saved**. If a filter hides the measured type, name that type. **Show saved reading** adjusts the filter and opens its details.
6. A pin and a list row open the same details. **Show on map** reveals and focuses the corresponding pin. Exact-coordinate duplicates remain available through the list.
7. Save failure keeps the candidate and operation ID. **Retry save** retries that candidate; it must not reacquire signal. Acquisition errors instead start a new operation on retry.

The browser uses shortened demonstration waits. Native behavior must use the spec's 10-second location deadline, freshness checks, and map-stall handling. Browser errors recover on retry to illustrate the flow; a real retry must reflect its actual outcome.

## States and recovery

Use **Preview a state** to reach these without performing real device operations.

| State | Visible behavior | Recovery |
|---|---|---|
| No readings | Explain where a first sample will appear | Record reading |
| Empty type filter | Name the missing connection type | Show all readings |
| Map loading | Small loading notice; access to list remains | Native timer/resource events determine the next state |
| Map error | “Map could not load”; retained samples stay intact | Retry map / View readings |
| List loading | Reserved rows rather than an empty collection claim | Replace from Room |
| List read error | Explain load failure without resetting the database | Retry readings |
| Permission explanation | Why precise access helps; saving without a pin remains possible | Continue to location / Save without a pin |
| Getting location | Stay at the sampling spot; up to 10 seconds | Continue, cancel, or keep browsing |
| Saving | No success claim yet; duplicate recording disabled | Wait for confirmed insert; browsing can continue |
| Saved with pin | Connection, rating, raw value, pin availability | Show saved reading / Done |
| Saved without pin | Same success plus a plain location reason | Show saved reading / Done |
| No supported connection / VPN | Explain the connection requirement | Retry reading after changing Android settings |
| Signal unavailable / stale / changed | Name acquisition failure; no row saved | New acquisition through retry |
| Storage failure | Candidate retained for the current process | Retry save with original ID |
| Interrupted | Explain that recording ended before saving | Record again |

The **Missing-location example** control previews all seven stored reasons: permission denied, approximate-only access, services off, unavailable, stale, inaccurate, and timeout. These remain distinct in the model; never collapse them into one database reason. The native implementation should offer **Open app settings** only when the user chooses to repair denied/approximate access; do not repeatedly prompt on browsing or every subsequent capture.

Details show full local acquisition time, type, historical rating, raw value/unit, signal age or the Wi-Fi timestamp limitation, and either usable coordinates/accuracy/location age or the stored no-location reason. The rating is not recalculated from a later policy. Mobile displays **Level n/4**, never fabricated dBm.

## Accessibility and implementation checks

- Use text with Good/Fair/Poor; Wi-Fi and Mobile use different icons. Never rely on color alone.
- Native target minimum is 48 × 48 dp. Mark pins with type, rating, value, and capture time; decorative SVG icons stay silent in the browser reference.
- Keep a logical focus order. Return focus after dismissal. Support Android Back and predictive Back with native components; the reference uses dialog dismissal and Escape.
- Use live status announcements for recording and saving. Avoid repeatedly announcing unchanged map data.
- Browser control transitions respect reduced motion. The prototype has no decorative entrance animations or indefinite spinners.
- Retain the list when basemap requests fail. A tile failure is not proof that the signal cannot be measured.
- Support dark appearance through semantic role pairs. On Android, follow system appearance; the workbench toggle is for reviewing the two schemes.
- Use TalkBack, 1.3× and 2× native text, actual system insets, and the pilot phone outdoors before accepting the Android UI.

## Verification

`verify.cjs` uses an existing Playwright installation; no dependency was added to the project. With Playwright available, run `node design/verify.cjs`. For an existing `playwright-core`, set `PLAYWRIGHT_MODULE` to its absolute path and `CHROMIUM_PATH` to the browser executable first.

The browser check exercises filters, shared details, both permission paths, signal retry, retained save ID and duplicate completion, capture across destinations, every selectable state and no-location reason, map/read retries, reset, dark appearance, keyboard navigation, and document overflow at 320, 390, 768, 1280, and 1440 pixels. The generated [check record](screenshots/checks.json) lists actual assertions. The screenshots are browser captures, not Android emulator or hardware evidence.

Physical capture, Room persistence, signal/location calibration, MapLibre resource events, genuine offline tiles, Android permission dialogs, TalkBack, process death, and system font scaling remain implementation acceptance checks from the spec. No native build or performance benchmark is claimed.
