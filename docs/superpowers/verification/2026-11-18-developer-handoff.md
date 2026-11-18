# Developer handoff

Date: 18 November 2026, Asia/Taipei. Branch: `feat/sagapmo-pilot`.

## Included

The native Android pilot, updated tests, Room schemas 1 through 3 and non-destructive migrations, approximate and manually placed locations, descriptions saved automatically, confirmed reading deletion with temporary Undo, floating navigation, and campus map labels are included together. The root checkout's updated floating-navigation prototype and design guide were copied into this branch with `design-ref/` and `docs/design/`, so another developer can use one checkout.

`README.md` describes setup, build commands and current behavior. The original spec and older verification notes describe earlier versions. In particular, the description verification predates automatic saving. Browser screenshots use synthetic data; native screenshots linked from older verification notes are local artifacts excluded from Git.

## Fresh checks

- Java 25: `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest` succeeded.
- Unit XML reports: 29 tests, zero failures and zero errors.
- Lint: zero errors and seven warnings.
- `node --check design/app.js` and `node --check design/verify.cjs` succeeded. Full browser interaction checks were not rerun; `design/screenshots/` contains prior recorded evidence.
- The first `:app:connectedDebugAndroidTest` run failed five map tests with `No compose hierarchies found in the app`. The phone reported a lock screen during those failures. This observation does not establish their root cause. A focused rerun passed six of seven map tests and exposed one outdated attribution assertion. The test now supplies attribution in its local style fixture and opens the current attribution control before checking it, rather than expecting permanently visible production-provider credits on a synthetic map.
- After that test correction, the full `:app:connectedDebugAndroidTest` run succeeded on the connected Infinix X6710 / Android 14 with zero failures. The visual screenshot fixture was skipped because no `visualSet` argument was supplied. This does not replace the physical acceptance checks below.

## Continue here

Keep the phone unlocked and the app visible when running device tests. Complete the remaining physical capture, campus, offline, accessibility and lifecycle acceptance in the original pilot verification. Campus landmark coordinates are estimated from the supplied map and still need survey validation. Pending description saves continue after closing details but do not survive process termination; descriptions have a 200-character limit. Clearing app data or uninstalling removes readings; there is no export or recovery feature.

Local SDK configuration, signing material, APK/build outputs, IDE caches and `.impeccable/` session files are excluded. No push or release is part of this handoff.
