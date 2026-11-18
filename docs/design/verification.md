# Reference verification

Scope: the standalone browser design artifact, not a native Android build. Checked 17 November 2026 with headless Chromium through an existing Playwright installation. No application dependency was added.

## Recorded evidence

- `node --check design/app.js` completed successfully.
- `design/verify.cjs`: **58 assertions passed**, zero runtime JavaScript errors. The full action/assertion list is in `design/screenshots/checks.json`.
- Captured the desktop workbench, compact browser workbench, main map/list/details, location explanation, capture, no-pin outcome, map failure, first use, and both dark main screens. These are synthetic-data browser captures.
- Document overflow assertions passed at 320, 390, 768, 1280, and 1440 pixels. A larger root text check also passed; this does not prove native system-font behavior.
- Light/dark foreground/background pairs, including the filled action, meet 4.5:1 in the actual computed styles. Ratios are recorded in `design/screenshots/contrast.json`.
- One Impeccable detector run found a small-label warning and device border/shadow advisory. The 10-pixel label was raised to 12 pixels, and the device shadow was removed. The detector was not repeatedly run.
- Screenshots embed their browser-capture origin. Source references were preserved.

## Anti Slop delivery gate

| Block | Result | Evidence |
|---|---|---|
| Hard gate | PASS at reference scope | Honest prototype/data/map labeling; working native browser controls; no external assets, fictional claims, or blocked zoom; empty/loading/error states exercised; named SVG controls, keyboard focus and Escape; no document overflow at the five checked widths; computed contrast pairs pass. |
| Purpose gate | PASS | Crimson preserves supplied identity and marks action/selection. Neutral map geometry separates samples without suggesting coverage. The system sans follows native tool conventions and renders offline. Sheets group selected sample metadata; map controls alone receive small elevation. Decisions are in the guide and surface brief. |
| Liveliness | PASS | ENERGY 1 / RHYTHM 1 / MOTION 1 recorded before construction. Recording is the focal action; consistent map/list anatomy and collegiate crimson form the identity. No decorative animation competes with the task. |
| Craftsmanship and quality | PASS at reference scope | Pin/list shared details, shared filters, hidden-type save recovery, both location choices, retained candidate retry, idempotent completion, capture across navigation, all preview states/reasons, recovery, reset, theme and keyboard interactions have recorded assertions. No extra product features or fabricated pilot evidence. |

The gate applies to the design artifact and its simulated interactions. The screen guide maps them to the approved pilot behavior; it is not evidence of phone permissions, radio data, database writes, downloaded tiles, or Android accessibility.

## Remaining native verification

The approved spec still requires the physical Android phone: real Wi-Fi and Mobile signal snapshots, timestamp and accuracy boundaries, location denials and revocation, active subscription/network changes, restart persistence, process death, Room failure behavior, MapLibre errors, basemap attribution, outdoor pin placement, TalkBack, increased system text, system insets and Android Back. Performance has not been benchmarked. The reference does not claim these checks passed.

## Independent finish review

Disposition: **ship** at local-reference scope. A fresh reviewer inspected the 12 screenshot plates, source, spec, guide, direction contract, 58 assertion record, and contrast evidence. No material findings. The five-pin description was corrected in the handoff. The reviewer noted that keyboard focus remains visibly distinct from destination selection in two state plates; this is legitimate focus behavior and required no recapture.
