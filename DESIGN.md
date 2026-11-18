---
name: "SagapMo?"
description: "A calm campus field tool for recording and finding individual signal readings."
colors:
  primary: "#a3082c"
  primary-hover: "#810623"
  primary-soft: "#fbecef"
  ink: "#202725"
  muted: "#59635e"
  surface: "#ffffff"
  canvas: "#f3f4ef"
  subtle: "#f7f8f4"
  line: "#dce1d9"
  good: "#096644"
  good-bg: "#e6f4eb"
  fair: "#805000"
  fair-bg: "#fff1d6"
  poor: "#a12626"
  poor-bg: "#fcebea"
  map: "#eaece6"
  map-block: "#d8ddd4"
  map-road: "#fffefa"
  map-green: "#d5e3ca"
  focus: "#125f9a"
  dark-primary: "#ffb1c1"
  dark-primary-hover: "#ffd1db"
  dark-primary-soft: "#45212a"
  dark-ink: "#e5ece5"
  dark-muted: "#b2beb4"
  dark-surface: "#1c241f"
  dark-canvas: "#111813"
  dark-subtle: "#252f28"
  dark-line: "#445147"
  dark-good: "#9bd9b5"
  dark-good-bg: "#193d2b"
  dark-fair: "#efc778"
  dark-fair-bg: "#42351a"
  dark-poor: "#ffb3ad"
  dark-poor-bg: "#482826"
  dark-map: "#29322b"
  dark-map-block: "#3e4c3f"
  dark-map-road: "#202a23"
  dark-map-green: "#354b34"
  dark-focus: "#a6cfff"
  on-primary: "#ffffff"
  dark-on-primary: "#390816"
  pin-good-dark: "#17613e"
  filter-outline: "#9da99f"
  studio-field-outline: "#a5afa5"
typography:
  display:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: "36px"
    fontWeight: 650
    lineHeight: 1.2
    letterSpacing: "-1px"
  brand:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: "1.5rem"
    fontWeight: 750
    letterSpacing: "-1px"
  headline:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: "22px"
    fontWeight: 650
    letterSpacing: "-0.5px"
  sheet-title:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: "20px"
    fontWeight: 650
    letterSpacing: "-0.4px"
  status-title:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: "18px"
    fontWeight: 650
  body:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: ".875rem"
    fontWeight: 400
    lineHeight: 1.7
  row-title:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: ".9375rem"
    fontWeight: 650
    lineHeight: 1.5
  action-label:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: ".9375rem"
    fontWeight: 650
  filter-label:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: ".8125rem"
    fontWeight: 600
  metadata:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: ".75rem"
    fontWeight: 400
    lineHeight: 1.5
  rating:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: ".75rem"
    fontWeight: 650
    lineHeight: 1.3
  micro:
    fontFamily: "\"Segoe UI\",Roboto,Arial,sans-serif"
    fontSize: "11px"
    fontWeight: 400
rounded:
  "pin-label": "4px"
  "rating": "5px"
  "small-note": "6px"
  "text-action": "8px"
  "control": "10px"
  "action": "12px"
  "map-status": "16px"
  "nav-indicator": "20px"
  "sheet": "24px"
spacing:
  "4": "4px"
  "6": "6px"
  "8": "8px"
  "10": "10px"
  "12": "12px"
  "14": "14px"
  "16": "16px"
  "18": "18px"
  "20": "20px"
  "24": "24px"
  "26": "26px"
  "32": "32px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.action-label}"
    rounded: "{rounded.action}"
    padding: "12px 16px"
    width: "100%"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
    textColor: "{colors.on-primary}"
  button-primary-dark:
    backgroundColor: "{colors.dark-primary}"
    textColor: "{colors.dark-on-primary}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    rounded: "{rounded.control}"
    padding: "10px 14px"
  button-text:
    textColor: "{colors.primary}"
    rounded: "{rounded.text-action}"
    padding: "10px 12px"
  filter:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.filter-label}"
    rounded: "{rounded.control}"
    padding: "8px 12px"
  filter-selected:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.surface}"
  rating-good:
    backgroundColor: "{colors.good-bg}"
    textColor: "{colors.good}"
    typography: "{typography.rating}"
    rounded: "{rounded.rating}"
    padding: "3px 7px"
  rating-fair:
    backgroundColor: "{colors.fair-bg}"
    textColor: "{colors.fair}"
    typography: "{typography.rating}"
    rounded: "{rounded.rating}"
    padding: "3px 7px"
  rating-poor:
    backgroundColor: "{colors.poor-bg}"
    textColor: "{colors.poor}"
    typography: "{typography.rating}"
    rounded: "{rounded.rating}"
    padding: "3px 7px"
  reading-row:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    padding: "16px 20px"
    width: "100%"
  map-status:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.map-status}"
    padding: "20px"
  detail-sheet:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "24px 24px 0 0"
    padding: "12px 24px 26px"
  navigation:
    backgroundColor: "{colors.subtle}"
    padding: "8px 20px 4px"
  studio-select:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "12px 32px 12px 12px"
    width: "100%"
---
# Design System: SagapMo?

## Overview

**Creative North Star: "The Campus Field Notebook"**

SagapMo? keeps individual signal readings easy to record and find while walking around campus. Collegiate crimson identifies action; warm neutral surfaces keep the map and saved samples readable. Named ratings, connection icons, and explicit location status carry meaning together.

This documents the implemented local reference in design/, refined from the supplied screens in Operate mode. The app is a compact field tool; the surrounding browser studio is a review aid. System sans is intentional for an offline reference and the future native Android interface.

**Key Characteristics:**

- Persistent recording action on both destinations.
- Quiet map, readable rows, and one shared details sheet.
- A valid saved signal remains a success when no pin is available.

The approved [pilot spec](docs/superpowers/specs/2026-11-17-sagapmo-pilot-design.md) owns behavior. [design/README.md](design/README.md) maps states and implementation boundaries; this file owns the extracted visual language. Review disposition: **SHIP** for the browser design reference; Android acceptance remains separate.

## Colors

Crimson guides action; green, amber, and red describe individual measurements. Frontmatter values are normative. Unprefixed roles are light; `dark-*` roles are their implemented dark counterparts.

### Primary

- `primary`, `primary-hover`, `primary-soft`: recording and recovery, hover feedback, selected navigation and progress marks.
- `on-primary` and `dark-on-primary`: filled-action text. The dark scheme uses a pale crimson action with deep crimson text.

### Semantic ratings

- `good` / `good-bg`: Good rating and confirmed-result icon.
- `fair` / `fair-bg`: Fair rating and missing-location explanations.
- `poor` / `poor-bg`: Poor rating and acquisition/storage error emphasis.
- Pins keep white connection glyphs. Their dark fills deliberately use `pin-good-dark`, light `fair`, and light `poor`; pale dark-scheme text colors would lose the glyph contrast.

### Neutral

- `ink`, `muted`: primary and secondary text; selected filters use ink as fill with surface-colored text.
- `surface`, `canvas`, `subtle`, `line`: task surfaces, outer studio canvas, quiet app chrome, and separators.
- `map`, `map-block`, `map-road`, `map-green`: illustrative ground, buildings, roads, and campus grounds. These describe the schematic, not production basemap styling or verified geometry.
- `focus`: visible keyboard focus. `filter-outline` and `studio-field-outline` preserve the explicit light control strokes; the source does not override these in dark mode.

**The Separate Meanings Rule.** Crimson means action. A rating color describes one recorded sample; unsampled map areas have no rating.

## Typography

Use the local system stack in the frontmatter. No font download is required. In Compose use Android's platform sans/Roboto through Material typography; preserve the hierarchy rather than adding a display-font dependency. Font-weight values describe the browser reference, so use the nearest supported native weight if necessary.

| Role | Observed reference | Initial native mapping |
|---|---|---|
| Detail value (`display`) | 36 px, 650, 1.2 line height; 18 px/500 unit | 36 sp value with 18 sp unit; tabular digits |
| Wordmark (`brand`) | 1.5 rem = 24 px at default root, 750 | 24 sp text mark; not a final logo |
| List heading (`headline`) | 22 px, 650 | 22 sp title |
| Sheet title | 20 px, 650 | 20 sp title |
| Map-status title | 18 px, 650 | 18 sp title |
| Body | .875 rem = 14 px, 400, 1.7 line height | 14 sp body, allow wrapping |
| Row title / primary action | .9375 rem = 15 px, 650 | 15 sp emphasized text |
| Filter label | .8125 rem = 13 px, 600 | 13 sp label |
| Metadata / rating | .75 rem = 12 px; 400 / 650 | 12 sp metadata / emphasized rating |
| Map legend, counts, attribution | 11 px | 11 sp reference baseline; enlarge if outdoor legibility needs it |

These are starting sizes, not fixed-pixel native constraints. Honor system font scaling, wrap metadata, and keep units with their values. Rows and detail values use tabular numerals. The studio's 38 px heading, desktop copy, and captions belong to the browser guide, not the app type scale.

## Layout

The phone reference is at most 412 CSS px wide and 846 px tall. Within it, a compact header sits above flexible map/list content, a persistent record panel, two destinations, and a simulated gesture area. Map filters sit 16 px from the top/sides. The list uses 20 px side padding, 16 px row padding vertically, and a secondary location line below its first row. Repeated gaps follow the extracted spacing steps.

The record panel uses 16 px vertical / 20 px horizontal padding; its action is at least 52 px high. Filters, secondary/text actions, and icon actions have at least 48 px targets. Navigation is at least 72 px high with 60 px button targets. Pins expose a 58 by at-least-62 px button around a smaller marker. Sheet content uses 12 / 24 / 26 px padding; the reference limits it to 740 px and scrolls within available browser height.

For Compose, interpret layout pixels as initial dp values and type pixels as initial sp values. Use real `WindowInsets`, flexible content, `LazyColumn`, and `ModalBottomSheet`; never fix the app to the preview height or draw its 34 px status / 20 px gesture placeholders. Keep the bottom action reachable, attribution clear of it, and the saved list accessible without map tiles. Larger text can expand rows, headers, and sheets.

The browser studio alone uses a three-column grid (max 1400 px), two columns below 1120 px, one stacked column below 720 px, and compact spacing below 370 px. The 1550 px rule adjusts outer padding. These are reference-workbench breakpoints, not Android window size classes. Native expanded layouts need their own adaptive validation.

## Elevation & Depth

Task surfaces and list rows are flat with subtle tonal separation and dividers. Shadows separate map overlays and sheets from the map rather than decorating every container. The shared sheet uses a dim scrim (`rgb(20 32 25 / .38)`); its browser dialog backdrop stays transparent so dimming is confined to the device.

The sidecar carries exact shadows: map note (2/6 px), pin (3/5 px), campus reset (3/9 px), raised map status (10/28 px), and top-cast sheet (-8/24 px). State transitions use 160 ms ease-out for background/text; pressed buttons move 1 px. Reduced-motion mode removes transitions and smooth scrolling. Progress tracks show phases without an indefinite spinner or decorative motion.

## Shapes

Controls use gently rounded rectangles: 10 px filters/secondary actions and 12 px primary/icon-container shapes. Compact labels use 4?6 px corners; text actions use 8 px. Map status uses 16 px corners; the sheet rounds only its top corners at 24 px. Navigation's active icon background is a 52 px circle inside the floating pill bar. Circular close/step marks and the rotated teardrop map pin remain distinct functional shapes. The 30 px desktop / 24 px compact phone shell is studio framing only.

## Components

- **Primary, secondary, text actions:** one filled crimson recording action in the center of the floating navigation, with a 30 px icon and accessible label. Sheet primary actions remain full width with visible labels and hover color. Outlined secondary and transparent text actions handle recovery and dismissal. All controls show a 3 px focus outline with 3 px offset. Disabled recording has .58 opacity and blocks another operation.
- **Filter chips:** All / Wi-Fi / Mobile share one selection across both destinations. Inactive chips use surface, an explicit outline, and connection icons. The selected chip uses ink fill and surface text; counts are supplemental and hide in the narrowest browser view. Filters affect display only.
- **Rating badges:** text, 7 px dot, and tonal background; 3 by 7 px padding. Good/Fair/Poor are always named. They are labels, not interactive filters.
- **Reading rows:** full-width button surface with bottom divider, 36 px connection container, connection/time at left and rating/raw value at right. Location is a second line; missing-pin text uses amber and gives the reason. Keep Wi-Fi dBm and Mobile level distinct.
- **Map pins:** rating-colored teardrop with a connection glyph and separate rating label. Selected/hover markers get a 2 px ink outline with 2 px offset; keyboard focus remains on the full target. Pins and list rows open the same details. List access handles exact-coordinate overlaps.
- **Map status card:** surface panel, 20 px padding, 16 px radius and restrained shadow. Failure names the problem and offers Retry map / View readings; it does not erase saved samples.
- **Bottom sheet:** shared shape and toolbar for details, permission explanation, progress, success, and errors. Close action has a circular 48 px target. Details expose acquisition time, rating, signal age or Wi-Fi timestamp limitation, and usable location or stored missing-location reason. Maintain focus entry/return and dismissal semantics. Saving suppresses close/cancel while allowing Keep browsing; native Back behavior must follow the approved operation rules.
- **Recording progress and result:** persistent action stays disabled during one operation. Location, acquisition, and saving have explicit phases. Success appears after confirmed storage, including without a pin. Retry save retains the original candidate; signal retry starts new acquisition. The reference uses shortened simulated waits; native timing comes from the spec.
- **Bottom navigation:** match the user-supplied floating pill reference: surface fill, 72 px height, 12 px side insets, 14 px bottom gap, and a soft downward shadow. Map uses a home glyph; Readings uses a list glyph. Both have 52 px circular targets, accessible names, and hover titles. The selected destination has a crimson tonal circle. A raised 64 px crimson recording button sits between them, reusing the existing capture flow and disabled state. It is an action, not a third destination.
- **Studio selector:** browser-only 48 px native select for scenario, simulated connection, and missing-location example. It is included in the sidecar because the reference has this field; it is not a native in-app setting.

## Do's and Don'ts

### Do

- Do pair Good/Fair/Poor colors with their names and Wi-Fi/Mobile icons.
- Do keep Map and Readings available while recording and when the basemap fails.
- Do preserve all seven no-location reasons and explain the stored reason in details.
- Do adapt spacing in dp and text in sp, keeping 48 dp targets and room for larger text.
- Do label prototype readings and schematic geometry illustrative.

### Don't

- Don't treat a display filter as a connection selector.
- Don't confirm saving before storage succeeds or reacquire a sample on Retry save.
- Don't add heatmaps, inferred coverage, continuous monitoring, sync, export, or extra telemetry to this pilot.
- Don't port browser studio controls, fixed phone dimensions, or fake system bars into Android.
- Don't treat browser checks as evidence of native permissions, persistence, TalkBack, or sensor accuracy.
