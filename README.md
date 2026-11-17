# SagapMo?

Native Android pilot: record the active default Wi-Fi or Mobile signal, save each reading privately with Room, and find it in Readings or as a MapLibre pin. Android 12 (API 31) or later is required.

Open this checkout in Android Studio. Point `local.properties` at your Android SDK, with Windows drive colons escaped, for example `sdk.dir=C\:/Users/USER/AppData/Local/Android/Sdk`. Use Java 25 to run the pinned Gradle wrapper; app bytecode targets Java 17.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew.bat :app:connectedDebugAndroidTest
./gradlew.bat :app:installDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. No account, API key, billing setup, or server is needed. The Liberty basemap requires network access; saved readings, filters and details do not. Clearing app data or uninstalling removes the collection. The pilot has no export or recovery feature.

For Mobile capture, turn Wi-Fi off in Android settings and wait for Mobile to become the default network. Display filters never select the measured connection. A valid signal can be saved without a location; details explain why there is no pin. Retry save retains the captured candidate and operation ID until the process ends.

The [approved spec](docs/superpowers/specs/2026-11-17-sagapmo-pilot-design.md) owns behavior. See the [verification record](docs/superpowers/verification/2026-11-17-sagapmo-pilot.md) for automated results, visual comparisons, rulings, and pending physical acceptance. Test readings and screenshot fixtures never seed the production database.
