# Location descriptions

Historical verification of the explicit-save version. The current implementation saves automatically after a 400 ms typing pause; see the README and developer handoff for the current behavior and verification limits.

An optional 200-character description is editable after capture and in Reading details. Save description explicitly persists the text; clearing and saving removes it. Nonempty descriptions appear as a two-line preview in Readings. Save failures retain the draft for retry. Pin coordinates, signal, capture time and reading identity are unchanged by a description update.

Room schema 3 adds a non-null empty-default description column. Both 1-to-3 migration chaining and 2-to-3 migration were exercised, preserving existing readings and manually placed pins. The persistence test verifies a description survives reopening and subsequent location editing.

Validation: build, lint and 28 unit tests passed. Ten targeted device tests passed, including migration/persistence, description creation after capture, editing, the 200-character cap, clearing and failed-save retry. A broader run passed 20 UI cases and exposed an older camera-focus test whose target reading was missing from its fixture; the fixture was corrected and that case passed on rerun. Native description layout was inspected on the connected Infinix without modifying a production description. Screenshot: `screenshots/2026-11-18-description.png`.

The APK was installed with replacement, without clearing application data. Tablet and dark appearance were not rechecked. Descriptions require an explicit Save description action; closing the editor with unsaved text does not save it.
