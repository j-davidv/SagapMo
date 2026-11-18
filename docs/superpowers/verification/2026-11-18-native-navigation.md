# Native navigation update

The Android checkout is now the active development target; the HTML prototype is complete.

Implemented the requested appbar Settings button and reference-based floating pill navigation in Compose. Settings uses the existing Android app-settings callback. Compact screens have Map, a central recording action, and Readings. Expanded screens retain the existing rail and labeled recording action. Save retry and duplicate-recording guards are preserved; no database or capture logic changed.

Validation on 2026-11-18:

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`: successful; 25 unit tests passed. Lint reports zero errors and seven warnings.
- Installed the debug APK with `adb install -r` on the connected Infinix X6710, retaining app data.
- Ran all eight `SagapMoAppTest` instrumentation tests successfully on the phone. Initial execution had three failures while the device was asleep; the full suite passed after waking it.
- Captured and inspected the running native app using `adb shell screencap`: `screenshots/2026-11-18-navigation.png`. The production app still shows the user's two existing readings after installation. No production readings were inserted by UI fixtures.
- Launched `com.sagapmo.app/.MainActivity` after testing. APK: `app/build/outputs/apk/debug/app-debug.apk`.

This pass checks the compact phone layout and UI behavior. Tablet layout, dark appearance, outdoor readability, and physical capture acceptance were not rechecked. Earlier physical acceptance items remain pending. APK updates require a build and reinstall; source edits alone do not update the phone.
