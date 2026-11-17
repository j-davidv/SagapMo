# SagapMo pilot verification

Implementation branch: `feat/sagapmo-pilot`, isolated checkout `.worktrees/pilot`.

| Physical milestone | Status | Evidence |
|---|---|---|
| Real Wi-Fi and active-data Mobile capture | Pending | No physical phone connected at execution start |
| Location, restart and reboot persistence | Pending | Requires pilot phone |
| Three outdoor campus spots and both pin types | Pending | Requires on-site survey |
| Independent tile failure and offline cold start | Pending | Requires pilot phone |

The pilot is not accepted until all four physical milestones pass. Test fixtures are isolated from production storage.

## Automated evidence

Execution date: 17 November 2026, Asia/Taipei. The test device is a dedicated headless Android 16 / API 36 x86_64 emulator, 1080 × 2160 pixels, density 420 (about 412 dp wide). No physical phone was attached. Running on API 31 remains an additional device check.

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`: BUILD SUCCESSFUL. Unit results: 25 tests, zero failures (3 signal-rule, 4 location-rule, 12 coordinator, 6 map-state tests).
- `./gradlew.bat :app:connectedDebugAndroidTest`: BUILD SUCCESSFUL. File-backed Room, Compose interaction, and real MapLibre resource-provider tests passed. The separately invoked visual fixture is skipped without its `visualSet` argument.
- Reports: `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/androidTests/connected/debug/`, and `app/build/reports/lint-results-debug.html`.
- Merged manifest inspected: only normal network/Wi-Fi state permissions, coarse/fine location, and AndroidX's non-exported internal receiver permission. No phone-state or background-location permission. `allowBackup=false` and database exclusions for both cloud backup and device transfer are present.
- Exported schema inspected at `app/schemas/com.sagapmo.app.reading.ReadingDatabase/1.json`: one readings table, all capture/location columns, enum names stored as text, UUID primary key, no destructive fallback migration.

Signal/location boundaries use elapsed time, including the inclusive 60,000 ms / 30,000 ms freshness limits and 30 m accuracy limit. The coordinator tests cover rapid taps, one permission continuation, background cancellation, retained save candidates, retry without reacquisition, read retry, all seven no-pin reasons, and a Mobile capture hidden by a Wi-Fi filter.

Room tests cover reopen persistence, enum/raw-value/rating preservation, newest-first ordering with ID tie-breaking, all filters, invalid row rejection, concurrent identical inserts, and conflicting payload rejection without overwriting the original.

Map tests use the actual native OpenGL renderer with controlled styles and resource providers. They cover geographic pins, unlocated exclusion, shared details, camera focus, attribution, style/tile/glyph/sprite failure, retry after leaving and returning, and late recovery. The stall test measures Android elapsed realtime, repeatedly requests camera frames, checks the error is absent before the deadline, then checks recovery actions appear within 20 seconds and a late full frame clears them.

## Native visual verification

The supplied HTML, tokens, behavior mapping, and phone screenshots were read before implementation. Only native app surfaces were ported. Browser workbench controls, fake system bars, preview-step buttons, illustrative geometry, and sample data are excluded from the shipped application.

Two bounded screenshot rounds compare eight states: map, readings, details, permission explanation, locating, saved without a pin, empty collection, and map failure. Each round includes light and dark appearance, plus actual Android system font settings of 1.3 and 2.0. Native screenshots use the real launcher Activity and `UiAutomation.takeScreenshot`, with isolated in-memory fixtures. The real OpenFreeMap style is used for map captures; a refused local HTTPS URL exercises the full-app map failure.

The first pass found overly prominent outcome values, missing recording-step structure, filter tint differences, and a 200% map error overlapped by floating filters. The batch correction restores the reference's compact saved summary, ordinary-weight explanatory copy, three recording stages, neutral filter icons/outlines, smaller dBm units, and reference no-pin details. At larger text sizes filters move above the map; error/empty content reserves the floating-filter area at normal text sizes. Details and status sheets scroll, targets remain at least 48 dp, and a native assertion checks the map error title stays below filters.

Final capture evidence and comparison links are recorded after confirmation. These captures verify native appearance and exercised controls; they do not establish TalkBack usability, outdoor readability, sensor calibration, or performance.

## Rulings and conflicts

1. The approved reference wins over generic design-skill defaults: preserve two destinations, Roboto, crimson, a bottom Button, and the existing identity. No extra destinations, theme toggles, or features were added. Cost if wrong: visual expectations would need revisiting, with no stored-data impact.
2. The reference's Cancel recording and Keep browsing controls are retained. Cancel stops pre-save acquisition; dismissing progress keeps it running. An insert already underway continues. Cost if wrong: the reference's cancellation UX would need clarification; committed data stays intact.
3. One complete Room stream feeds both views; the shared display filter is derived in memory. This supplies the reference's live filter counts and total saved count without a second query. Cost if wrong: memory grows with the collection; this is suitable for the local pilot, not an unbounded shared survey.
4. The plan assumed full frames alone identify all resource failures. Native testing disproved that for sprites, while a broad missing-image check falsely rejected optional Liberty icons. Each production map attempt therefore owns a runtime with a narrow sprite resource provider: platform HTTP preserves response validators/cache metadata, typed failures drive the existing map error, and cancelled attempt scopes prevent callbacks reaching a replacement. Tile/glyph incompleteness still uses native events and the 15-second timer. Cost if wrong: provider-specific HTTP/cache behavior could need adjustment; physical blocked-resource validation remains pending.
5. Native style readiness and full-frame delivery can arrive in either order. Readiness requires both observations; style loading alone never declares success. Cost if wrong: an untested renderer ordering could delay recovery; failure actions and Readings stay available.
6. The spec fixes the Liberty basemap URL. Its actual geography and provider palette replace the illustrative schematic, including in system dark mode. All app chrome uses the approved dark token pairs. Cost if wrong: matching a dark basemap would require an explicitly approved style change; this implementation keeps the specified provider/style.

All planned library pins resolved and compiled without a version change. AndroidX Test runner 1.7.0 and JUnit extension 1.3.0 are recorded in the version catalog. Build versions were checked against [MapLibre's release metadata](https://raw.githubusercontent.com/maplibre/maplibre-compose/v0.20.0/gradle/libs.versions.toml) and [AGP compatibility documentation](https://developer.android.com/build/releases/agp-9-3-0-release-notes).

## Remaining acceptance

All four physical milestones in the table remain pending. In particular: real RSSI refresh after movement/reconnect, Mobile active-data/dual-SIM behavior and cache age, permission/one-time/revocation scenarios, real location accuracy and listener cancellation, rotation and process termination around capture/insert, force-stop/reboot persistence, three campus sampling spots, independent basemap blocking, airplane-mode cold start, TalkBack focus/announcements, and outdoor readability.

Lint's pinned-version/target advisories do not alter the approved build baseline. No final launcher-logo artwork was supplied, so the pilot retains Android's default launcher icon rather than inventing a brand asset. No production readings were created by test fixtures.
