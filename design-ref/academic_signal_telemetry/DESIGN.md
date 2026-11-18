---
name: Academic Signal Telemetry
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#eaedff'
  surface-container-high: '#e2e7ff'
  surface-container-highest: '#dae2fd'
  on-surface: '#131b2e'
  on-surface-variant: '#5a4041'
  inverse-surface: '#283044'
  inverse-on-surface: '#eef0ff'
  outline: '#8e7070'
  outline-variant: '#e2bebe'
  surface-tint: '#b71e38'
  primary: '#79001d'
  on-primary: '#ffffff'
  primary-container: '#a3082c'
  on-primary-container: '#ffaeb0'
  inverse-primary: '#ffb3b4'
  secondary: '#006c4a'
  on-secondary: '#ffffff'
  secondary-container: '#85f5c1'
  on-secondary-container: '#00714e'
  tertiary: '#5a2e00'
  on-tertiary: '#ffffff'
  tertiary-container: '#7c4100'
  on-tertiary-container: '#ffb273'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdada'
  primary-fixed-dim: '#ffb3b4'
  on-primary-fixed: '#40000b'
  on-primary-fixed-variant: '#920025'
  secondary-fixed: '#88f8c4'
  secondary-fixed-dim: '#6bdba9'
  on-secondary-fixed: '#002114'
  on-secondary-fixed-variant: '#005137'
  tertiary-fixed: '#ffdcc3'
  tertiary-fixed-dim: '#ffb77d'
  on-tertiary-fixed: '#2f1500'
  on-tertiary-fixed-variant: '#6e3900'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fd'
typography:
  headline-xl:
    fontFamily: Space Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 26px
  headline-sm:
    fontFamily: Space Grotesk
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  title-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  label-lg:
    fontFamily: Space Grotesk
    fontSize: 14px
    fontWeight: '700'
    lineHeight: 18px
    letterSpacing: 0.02em
  label-md:
    fontFamily: Space Grotesk
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.04em
  label-sm:
    fontFamily: Space Grotesk
    fontSize: 10px
    fontWeight: '700'
    lineHeight: 14px
    letterSpacing: 0.06em
  metric-display:
    fontFamily: Space Grotesk
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 42px
    letterSpacing: -0.03em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.5rem
  gutter-lg: 1.5rem
  margin: 1rem
  margin-sm: 0.75rem
  margin-lg: 1.5rem
  space-2xs: 0.25rem
  space-xs: 0.375rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
  space-2xl: 2rem
---

## Brand & Style
The brand personality is collegiate, utilitarian, field-ready, and modern. Built for active use across campus grounds, the interface balances high-density utility with the polished fluidity of mobile ride-hailing interfaces. The emotional tone is authoritative, dependable, and swift—empowering students and campus network administrators to log, inspect, and benchmark telecommunication performance under harsh outdoor sunlight.

The design movement combines **Modern High-Contrast Utility** with **Bottom-Sheet Mobility Architecture**:
- **Field Ergonomics:** Heavy structural reliance on 48dp minimum touch envelopes, bottom-anchored sheets, thumb-driven interaction clusters, and high visual contrast.
- **Sunlight Readability:** Solid white and deep slate surfaces take precedence over delicate gradients, eliminating optical washout in outdoor campus conditions.
- **Academic Pride:** Energetic institutional berry-crimson accents ground the telemetry data within the university identity while avoiding visual fatigue.

## Colors
The color palette balances collegiate institutional energy with urgent, glanceable RF (radio frequency) telemetry metrics:

- **Primary (`#A3082C` - Academic Crimson / Deep Berry):** The driving focal color used for primary system actions, key data nodes, active bottom tab selection, recording triggers, and campus brand identification.
- **Secondary (`#0D9468` - Deep Emerald):** Designates optimal RF telemetry (Good / Excellent signal, 5G full strength, low latency). Saturated enough to remain distinct against bright map vectors.
- **Tertiary (`#D97706` - Signal Amber):** Signals degraded performance (Fair / Moderate latency, handoff buffer zones).
- **Destructive / Poor (`#DC2626` - Vibrant Coral Red):** High-alert RF status indicating dead zones, packet loss, or failed pings.
- **Neutral (`#0F172A` - Slate 900):** Anchors deep typographic contrast on crisp white sheet surfaces.
- **Surface Foundations:** Background card surfaces utilize pristine `#FFFFFF`, complemented by low-intensity neutral tint boundaries (`#E2E8F0`) to maintain separation against vibrant satellite or vectorized tile maps.

## Typography
The system employs a dual-font pairing optimized for telemetry and mobile wayfinding:

- **Display, Metrics & Labels (`Space Grotesk`):** Injects a technical, engineering edge into metric readings (dBm, RSRP, SINR, latency), numerical badges, and prominent card headers. Its distinct glyph architecture prevents character ambiguity in tabular data.
- **Body & Structural Text (`Plus Jakarta Sans`):** Provides a friendly, clear, and highly legible reading experience across variable zoom levels and outdoor ambient glare.
- **Tabular Data Optimization:** Metric levels (`metric-display`, `label-md`) strictly employ tabular figures (`tnum`) when reporting fluctuating signal measurements to avoid layout jitter during live packet streaming.

## Layout & Spacing
The layout follows a **Map-Centric Layered Canvas** model derived from ride-hailing conventions:

- **Backdrop Canvas:** Full-viewport interactive map plane (displaying campus buildings, towers, and signal heatmaps).
- **Floating HUD Layer:** Top search bar, operator filter pills (e.g., Smart, Globe, DITO), and calibration shortcuts float with an outer margin of `margin` (16px) and `space-sm` (8px) internal gaps.
- **Interactive Sheet Anchor:** Anchored modal and non-modal bottom sheets house recorded data lists, speed test gauges, and logging forms. The sheet has three detent positions: peek (120dp), half-expanded (50vh), and fully expanded (92vh).
- **Touch Target Safeguards:** All interactive floating map controls, recording toggles, and list items maintain an inviolable minimum height and width of 48dp.

## Elevation & Depth
Depth separates structural controls from the dynamic geospatial map without relying on heavy skeuomorphic overlays:

- **Surface Layer 0 (Map Canvas):** Base tile engine.
- **Surface Layer 1 (Floating Map Controls & Pills):** Pure white background (`#FFFFFF`) with a 1px hairline border in `#E2E8F0` and an ambient drop shadow: `0 4px 12px -2px rgba(15, 23, 42, 0.12), 0 2px 4px -1px rgba(15, 23, 42, 0.06)`.
- **Surface Layer 2 (Bottom Sheet Container):** Grounded `#FFFFFF` surface featuring top-only border radiuses (`rounded-xl`), an ambient elevation shadow of `0 -6px 24px -4px rgba(15, 23, 42, 0.16)`, and a top drag handle pill (`#CBD5E1`).
- **Surface Layer 3 (Active Recording Modal / Dialogs):** High-priority overlays with a 32% `#0F172A` scrim, drawing focus to active signal sync or campus location verification.

## Shapes
The shape philosophy favors tactile, modern rounded structures that mirror the ergonomic physical contours of mobile hardware:

- **Standard Cards & Surfaces:** Employ `rounded-lg` (16px) for interior bottom-sheet cards, nested metric blocks, and search bars.
- **Main Sheet Container:** Utilizes a specialized top corner radius of 24px (`rounded-xl` equivalent in mobile context) to deliver the signature bottom-sheet silhouette.
- **Status Badges, Filter Pills & Floating Map Actions:** Use pill-shaped contours (`rounded-full`) to immediately communicate horizontal scrollability and map interaction.

## Components

### 1. Primary Action & Recording Buttons
- **Trigger Button:** Full-width 52dp height in bottom sheets, solid Academic Crimson (`#A3082C`) fill, pure white text in `label-lg` uppercase. 16px corner radius (`rounded-lg`). Pressed state dims to `#820623`.
- **Floating FAB Record:** Circular 56dp FAB hovering over the map, Academic Crimson with a white telemetry icon and ambient Layer 2 shadow.

### 2. Signal Status Badges & Metric Chips
- **Status Indicator Tokens:**
  - **Good:** Light emerald surface (`#ECFDF5`), border `#A7F3D0`, text `#065F46`.
  - **Fair:** Light amber surface (`#FFFBEB`), border `#FDE68A`, text `#92400E`.
  - **Poor / Dead Zone:** Light coral surface (`#FEF2F2`), border `#FECACA`, text `#991B1B`.
- **Shape & Specs:** Fully rounded pills (`rounded-full`), `space-xs` vertical padding, `space-md` horizontal padding, paired with a solid 6dp color circle reflecting the live RF category.

### 3. Bottom Sheet Telemetry Cards
- **Structure:** `#FFFFFF` cards encased in a subtle 1px border (`#F1F5F9`).
- **Metric Row Items:** Left-aligned carrier/band tag (e.g., "Band 28 • LTE"), centered numerical signal reading in `Space Grotesk` tabular figures (e.g., "-84 dBm"), and right-aligned timestamp or sync confirmation.

### 4. Floating Search & Campus Filter Pills
- **Search Header:** 48dp tall floating bar with white background, subtle slate placeholder (`#64748B`), leading magnifying icon, and trailing campus sector shortcut (e.g., "CEAFA", "Gymnasium").
- **Filter Row:** Horizontally scrolling pill rail beneath search bar. Default state: `#FFFFFF` fill with `#CBD5E1` border. Selected state: `#0F172A` solid fill with white text.

### 5. Input Fields & Calibration Forms
- **Field Height:** 48dp minimal vertical dimension with 12px corner radius.
- **Border States:** Rest `#CBD5E1`, focused 2px `#A3082C` border with subtle 3px crimson glow (`rgba(163, 8, 44, 0.15)`). Text renders in Slate 900.

### 6. Bottom Sheet Drag Handle
- Centered at the top of the bottom sheet: 36dp width, 4dp height, `#CBD5E1` background color, fully rounded ends (`rounded-full`), with `space-sm` margin above and below.