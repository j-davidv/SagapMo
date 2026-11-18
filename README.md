# SagapMo?

Native Android pilot: record the active default Wi-Fi or Mobile signal, save each reading privately with Room, and find it in Readings or as a MapLibre pin. Android 12 (API 31) or later is required.

After a reading is saved, an optional Location description field accepts up to 200 characters. Descriptions save automatically after a 400 ms pause in typing. The status shows saving, saved, or a retryable error. Pending saves continue when Reading details is closed. Reading details offers the same editor for later changes or clearing; the list previews nonempty descriptions. Editing a description preserves capture data and the map pin. Room schema 3 adds descriptions with an empty default and migrations from both earlier schema versions.

Current location behavior (user revision, 18 November 2026): keep valid, recent phone coordinates even when accuracy exceeds 30 metres, accuracy is unknown, or Android grants only approximate location. These pins are labelled approximate. Invalid or stale coordinates remain excluded. In Reading details, Set location opens a map picker; move the map under the centre marker and save. Manual edits preserve the reading ID, capture time and signal values, clear sensor accuracy metadata, and are labelled Set manually. Existing no-pin readings can be placed manually; previously discarded coordinates cannot be reconstructed. Room schema 2 includes a non-destructive migration preserving existing readings.

In Readings, swipe a row left or use the TalkBack Delete reading action, then confirm Delete in the dialog. Cancel or dismiss the dialog to keep the reading. The Undo snackbar restores the original reading while available. Deletion updates the shared list, filter counts, and map pins, and persists across restarts. Failed deletion keeps the row and shows an error. Undo is temporary and does not survive closing the app.

Active app development is in this native checkout. The HTML design prototype is complete. The appbar Settings button opens Android's app settings. Compact screens use the supplied floating navigation design with Map, a central Record reading / Retry save action, and Readings; expanded screens retain the navigation rail. Icon buttons have TalkBack labels and capture continues to block duplicate recording. UI changes require rebuilding and installing the APK; they do not update an already installed APK automatically.

Open this checkout in Android Studio. Point `local.properties` at your Android SDK, with Windows drive colons escaped, for example `sdk.dir=C\:/Users/USER/AppData/Local/Android/Sdk`. Use Java 25 to run the pinned Gradle wrapper; app bytecode targets Java 17.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew.bat :app:connectedDebugAndroidTest
./gradlew.bat :app:installDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. No account, API key, billing setup, or server is needed. The Liberty basemap requires network access; saved readings, filters and details do not. Clearing app data or uninstalling removes the collection. The pilot has no export or recovery feature.

For Mobile capture, turn Wi-Fi off in Android settings and wait for Mobile to become the default network. Display filters never select the measured connection. A valid signal can be saved without a location; details explain why there is no pin. Retry save retains the captured candidate and operation ID until the process ends.

The [approved spec](docs/superpowers/specs/2026-11-17-sagapmo-pilot-design.md) defines the original baseline; the user revisions above describe later behavior. See the [verification record](docs/superpowers/verification/2026-11-17-sagapmo-pilot.md) for historical automated results, visual comparisons, rulings, and pending physical acceptance. Test readings and screenshot fixtures never seed the production database.

## Developer handoff

Continue on `feat/sagapmo-pilot`. This branch contains the Android project at its repository root; `.worktrees/pilot` is only the original developer's local checkout path. A fresh checkout does not need that nested folder.

The [handoff verification](docs/superpowers/verification/2026-11-18-developer-handoff.md) records the latest checks and limitations. `design/` contains the browser prototype and its recorded screenshots, `design-ref/` preserves the supplied references, and `docs/design/` contains the original surface brief and browser verification. These are design context; the browser prototype does not implement every later native feature. Campus label coordinates are estimates, pending survey.

Use the current source and the revisions above when historical notes differ. In particular, descriptions now save automatically after a 400 ms typing pause; the earlier description verification records the superseded explicit-save interface. Pending description saves survive closing details, but not process termination. Native screenshots referenced by historical verification notes were local artifacts and are not included in Git.
