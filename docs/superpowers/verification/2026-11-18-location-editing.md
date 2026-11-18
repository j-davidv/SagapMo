# Approximate and manual reading locations

The user's 18 November instruction supersedes the original strict 30-metre pin criterion. Approximate and unknown-accuracy coordinates now survive capture with an explicit warning. Approximate-only permission may also produce a pin. Invalid, future-dated and stale coordinates are still rejected; missing coordinates are never fabricated.

Reading details offers a map-centre location picker with Save location and Cancel. The database update changes only the selected row's location columns. It preserves capture time, signal and identity; manual placement clears sensor-derived accuracy/age and records a manual-source flag. Map, list and details reflect the update. Save failures keep the editor open; deleted rows cannot be recreated by an edit.

Room 1-to-2 migration adds a default-false manual-source column. The migration test opens a version-1 database containing an existing reading, upgrades it, edits its pin, and verifies both preservation and persistence across reopening. No destructive migration or app-data clearing is used.

Verified: build and lint; 27 unit tests; nine device tests covering Room persistence and migration, approximate capture, manual editing, cancellation and signal preservation. A native screenshot review caught map labels overflowing the editor viewport; clipping was added and confirmed in the final capture. A physical pan changed the displayed coordinates from 13.78421 to 13.78397 latitude without saving an edit to the user's reading. UI test fixtures use separate test data.

The updated APK was installed on the connected Infinix. Native captures: `screenshots/2026-11-18-location-editor.png` and `screenshots/2026-11-18-location-editor-final.png`. Tablet, dark mode and outdoor positioning accuracy were not rechecked. Automatic coordinates discarded by previous versions remain unavailable; those readings can use manual placement.
