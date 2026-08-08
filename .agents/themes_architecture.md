# Classic Theme Layout & Visual Architecture

This document describes the design, structure, and compilation workflow for the classic themes (`basic` and `basic-light`) within the Haval Shisuku / Impulse dashboard widgets.

## Reorganization History

Historically, the custom dark and light themes were named `air-control` and `air-control-light` (referenced internally as `Basico` and `Light`). In the recent architecture revision, these were relocated and clean-coded into:
- `basic` (Dark Mode classic theme)
- `basic-light` (Light Mode classic theme)
- `default` (Sporty layout theme)

During this transition, the classic themes lost their top status bar backgrounds and the center-top status container (displaying the clock, current gear, and EV mode).

---

## Root Cause & Bug Restoration

### 1. Basic (Dark Theme) Status Bar Recovery
- **Issue**: Sporty-layout specific styling overrides (`.display-esportivo`) were carried over from the legacy `air-control` implementation.
- **Root Cause**: The dark stylesheet (`basic/src/styles/night.style.css`) contained aggressive negation overrides (`#app:not(.display-esportivo)`) that forcefully hid the `.dashboard-top-center` container (`display: none !important`) and stripped `.mask-top-bar` and `.dashboard-bottom-gauges` of their linear gradients (`background-image: none !important`).
- **Fix**: Removed the negations for top/bottom bar backgrounds and the clock/gear/mode containers, while retaining the hard-stops for actual sporty-only gauges (`.dashboard-speed-esportivo-widget` and `.dashboard-sport-fixed-overlay`) so they remain hidden in classic mode.

### 2. Basic-Light (Light Theme) Visual Depth Restoration
- **Issue**: The status bars (`.mask-top-bar` and `.mask-bottom-bar`) were visually flat or perceived as missing, blending completely with the background.
- **Root Cause**: The light stylesheet (`basic-light/src/styles/light.style.css`) used solid white gradients (`#ffffff` and `rgba(255,255,255,0.5)`) and solid white shadows (`var(--mask-circle-bg) = #ffffff`) over a pure white page background, resulting in zero contrast.
- **Fix**: Updated both status bars with elegant translucent overlay gradients and soft, premium shadows (`box-shadow: 0 4px 25px rgba(0, 0, 0, 0.06)`) to establish visual separation and a premium glassmorphic feel on the white backdrop.

### 3. Basic-Light (Light Theme) Bottom Gauges Harmonization
- **Issue**: The bottom fuel and battery gauges did not align correctly compared to the basic dark theme, and the fuel/battery icons were completely invisible (white-on-white). Additionally, empty progress bar tracks were invisible against the white page background.
- **Root Causes**:
  1. The layout elements in the light stylesheet (`basic-light/src/styles/light.style.css`) had a height of `72px` and lacked the matching negative margins (`margin-right: -40px`, `margin-left: -30px`) that are used in `night.style.css` to align gauges perfectly under the dial circles.
  2. The SVG icons used in `dashboardInfo.js` were hardcoded to white fills (`fill="white"` / `fill="#fff"`), blending completely with the light background.
  3. The `.segment-track` used `var(--color-white-15)`, which rendered as white on white, rendering empty track segments invisible.
  4. The fuel quantity/unit display in `basic-light` was using a generic class name `.fuel-percent` with default styling, whereas the dark theme used `.fuel-liters` which has custom right-align, top offset, and spacing optimizations.
- **Fixes**:
  1. Updated the light theme's stylesheet container height to `60px` and added matching negative margins for perfect alignment.
  2. Substituted base64 SVG fills in the light theme to `fill="black"` so the icons stand out on the white dashboard background.
  3. Changed the `.segment-track` background color to `rgba(0, 0, 0, 0.1)` so that empty progress bar tracks are clearly visible.
  4. Ported `.fuel-liters` CSS styles to `light.style.css` and updated `dashboardInfo.js` to utilize `.fuel-liters` instead of `.fuel-percent` for fuel display, making layouts completely identical across themes.

### 4. Dynamic Visual Mode (Light/Dark Switch) Restoration
- **Issue**: Toggling the visual mode dropdown to `Light` did not dynamically switch the dashboard background or gauges to light mode, despite the `.theme-light` class successfully appending to `#app`.
- **Root Cause**:
  1. The main container `#app` lacked any default background-color styling in CSS. Because its children are absolutely positioned or fixed, its height collapsed to `0px`, and it had a completely transparent background.
  2. In development mode, `main.js` hardcodes `document.body.style.backgroundColor = 'black'`, which showed through the transparent `#app` element.
  3. The `.mask-circle.right` (used behind the menus/right-circle) used hardcoded dark colors (`var(--bg-black-70)`) in its radial gradient, creating a dark smudge on the right side of the screen when visual mode was light.
  4. The circular dial background blockers (`.no-app-mask-l` and `.no-app-mask-r`) had a hardcoded background color (`var(--bg-black-100)`), keeping left and right circular areas solid black in light mode.
- **Fixes**:
  1. **Solid `#app` Canvas**: Explicitly styled `#app` in `night.style.css` to be a solid container (`position: absolute; top: 0; left: 0; width: 1920px; height: 720px; overflow: hidden;`) with `background-color: var(--bg-dark);` and a smooth 0.3s transition. When visual mode switches, the background transitions smoothly between black (`#000000`) and pure white (`#ffffff`).
  2. **Right Radial Mask Harmonization**: Overrode `.mask-circle.right` under `#app.theme-light` to transition its radial gradient to a soft light background (`rgba(255, 255, 255, 0.7)` and `rgba(255, 255, 255, 0.85)`), blending seamlessly with the light theme.
  3. **Solid Blockers Adaptation**: Overrode `.no-app-mask-l` and `.no-app-mask-r` under `#app.theme-light` to use `var(--mask-circle-bg)` (pure white) instead of solid black:
     ```css
     #app.theme-light .no-app-mask-l,
     #app.theme-light .no-app-mask-r {
       background: var(--mask-circle-bg) !important;
       box-shadow: 0 0 40px 40px rgba(255, 255, 255, 0.5) !important;
     }
     ```
  4. **High-Fidelity Dev Background Mockup**:
     * Injected a dynamic `.dev-background` div into `#app` in development mode (`nativeMockEnabled` is true).
     * Sized and styled `.dev-background` using the realistic car cockpit cockpit asset `dev-bg.png` positioned at `z-index: 0` (behind all gauges and text).
     * Programmed a hardware-accelerated opacity transition so `.dev-background` automatically fades out (`opacity: 0`) in light mode to reveal the clean, pure white dashboard background, and fades in (`opacity: 1`) in dark mode.
     * Changed the dev-mode body background to a premium dark slate gray (`#111315`) so that the 1920x720 instrument cluster frame stands out cleanly in both dark and light visual modes during browser previews.

---

## Structural Integrity

The core UI structure and layout initialization remains identical between the two classic themes, shared via:
- `basic/src/core/components/display/mask.js` (Background layer containing `.mask-top-bar`)
- `basic/src/core/components/dashboardInfo.js` (Foreground layer containing `.dashboard-top-center`)
- `basic/src/core/main.js` (Layout tree compilation)

The top-center widgets are injected into the DOM container at layout time, ensuring they float above the background mask overlay layers (`z-index: 140` vs. `z-index: 1`).

---

## Compilation & Packaging Workflow

All custom themes are built as unified, self-contained single-page HTML files (all CSS/JS inlined with dynamic assets Base64-encoded).

### 1. Build and Compile
From each theme directory (`cluster-widgets/basic` or `cluster-widgets/basic-light`), execute the Parcel compiler and inliner:
```powershell
npm run build
```
This automatically:
- Builds the optimized assets into `./dist/`.
- Runs `./inline.js` to parse and embed all external scripts, stylesheets, and assets.
- Copies the final unified file directly to the Android resources directory (`app/src/main/res/raw/app.html` or `app_light.html`).

### 2. Theme Package Distribution
For runtime theme discovery, the unified output must be manually placed inside the `Themes` package directory:
- **Basic (Dark)**: Copy `basic/dist/app.html` to `cluster-widgets/Themes/Basic/index.html`.
- **Basic Light**: Copy `basic-light/dist/app_light.html` to `cluster-widgets/Themes/BasicLight/app_light.html`.
- **Version Bump**: Increment the `<version>` tag in `theme.xml` by `0.0.1` (e.g., to `1.0.4`) to trigger the app's internal cache-refresh and update system.

---

## Odometer & Maintenance Warning Y-Axis Displacement

To ensure optimal layout balance when showing either a single line (odometer value only) or two lines (odometer value + maintenance/revision warnings), the widget uses a dynamic class-toggling mechanism.

### 1. JavaScript State Integration
In `odometerInfo.js`, the widget updates its container's class list dynamically based on the active display criteria:
- **Single-Line Mode**: Applied when only the odometer is active. The wrapper is assigned `.odometer-text-wrapper.single-line`.
- **Double-Line Mode**: Applied during startup flashing or active maintenance warnings. The wrapper is assigned `.odometer-text-wrapper.double-line`.

### 2. Styling and Y-Axis Displacement

The stylesheets (`night.style.css` / `light.style.css`) govern the exact vertical position via CSS transforms tailored for each theme's contrast and display parameters:

- **Basic (Dark Mode - `night.style.css`)**:
  - `.odometer-text-wrapper.single-line`: Uses `transform: translateY(-6px);` with larger text sizing to center the odometer perfectly.
  - `.odometer-text-wrapper.double-line`: Uses `transform: translateY(0px);` to balance the two text lines nicely.

- **Basic-Light (Light Mode - `light.style.css`)**:
  - `.odometer-text-wrapper.single-line`: Uses `transform: translateY(0px);` with larger text sizing to center the single-line odometer.
  - `.odometer-text-wrapper.double-line`: Uses `transform: translateY(6px);` to balance the two text lines nicely and prevent bottom mask collision.

---

## Default / Embedded Theme Update Architecture

To allow seamless upgrades of the built-in sporty theme without requiring app updates, the Default theme has been unified between raw assets and the GitHub update workflow.

### 1. Unified Card & Git SHA/Size Comparison
- Previously, a hardcoded "Default (Original)" card and a remote "Default" card from GitHub were shown separately.
- They have been merged into a single, cohesive **"Default" Card**.
- If the Default theme is not downloaded locally in `themesDir/Default`, the app reads from raw resources (`R.raw.app` / `app.html`).
- To check if the embedded version differs from the latest one on GitHub, the app retrieves the `size` and `sha` of the remote `index.html` file through GitHub's repo contents API.
- The app then calculates the local Git-compatible SHA-1 blob hash:
  `SHA-1("blob " + localSize + "\u0000" + localBytes)`
- If either the size or the SHA-1 hash is different, the app sets `hasUpdate = true`, prompting the user to click **"Atualizar" / "Baixar"**.

### 2. Selective Overriding & Fallback
- When downloaded, the updated Default theme is placed under `files/themes/Default`.
- The `ACTIVE_CUSTOM_THEME` preference is updated to `"Default"`, instructing `InstrumentProjector2` to load the custom HTML file from `files/themes/Default/index.html` instead of the raw resource.
- A **Delete / Excluir** action is provided for the Default theme when it has been downloaded. Clicking it deletes the downloaded folder and resets `ACTIVE_CUSTOM_THEME` to `""`, cleanly falling back to the raw APK resource.

---

## Theme Configurations Metadata Schema (theme.xml)

To support dynamic and flexible theme configuration without hardcoding options in the native app, a declarative `<configurations>` schema is supported inside the `theme.xml` descriptor. The Android app parses these options at discovery time and automatically generates a premium Jetpack Compose dialog containing custom controls.

### XML Schema Layout:
```xml
<configurations>
    <configuration>
        <id>[unique_setting_id]</id>
        <label>[Friendly display title in form]</label>
        <type>[boolean | text | number | combo | color]</type>
        <default>[default_fallback_value_string]</default>
        <stateVariable>[javascript_reactive_state_variable]</stateVariable>
        <options>[comma_separated_values_for_combo_and_color_types_only]</options>
        <group>[optional_tab_name; omitted = "Geral"]</group>
    </configuration>
</configurations>
```

### Supported Variable Types:
1. **`boolean`**: Renders as a native switch toggle.
2. **`text`**: Renders as a standard text input field.
3. **`number`**: Renders as a numeric-only input field.
4. **`combo`**: Renders as a dropdown selection box. Dropdown options are defined as a comma-separated list under `<options>` (e.g. `<options>Eco,Normal,Sport</options>`).
5. **`color`**: Renders a row of preset swatches plus a "Personalizado" HSV picker (saturation/brightness map + hue strip). `<options>` doubles as the preset list and holds comma-separated `#RRGGBB` values; when omitted, a built-in palette is used. `<default>` must be `#RRGGBB`. The stored value is the `#RRGGBB` string, which reaches the theme through the ordinary `getPreference` path with no bridge changes. In the local simulator the same declaration renders as a native `<input type="color">`.

> **Backwards compatibility.** `color` is an additive extension of the `v1.0` contract. An app build that predates it hits the `when (config.type)` in `ThemeSettingsDialog` with no matching branch and renders the label only, so the theme falls back to its `<default>` — degraded, not broken. Themes that do not declare `color` are unaffected either way.

### Configuration Groups (Tabs):
`<group>` is optional and names the tab a setting appears under in the settings dialog. It exists purely to keep long configuration lists navigable — it carries no runtime meaning and never reaches the theme's JavaScript.

- A setting with no `<group>` (or a blank one) falls into **`Geral`**.
- **`Geral` is always the first tab.** Every other tab follows the order its group name first appears in `theme.xml`.
- When a theme resolves to a single group, the tab row is **not rendered at all**, so ungrouped themes look exactly as they did before groups existed.
- Group names are free-form strings and are matched verbatim, so `Cores` and `cores` would produce two separate tabs.

The Default theme uses `Geral`, `Cores`, `Barras`, `Outros`.

> **Backwards compatibility.** `<group>` is additive and safe in both directions. A new app reading an old descriptor finds no `<group>` and puts everything in `Geral`. An old app reading a new descriptor never matches `group` in its tag `when` block, so the element is skipped and its text is never consumed — the remaining fields parse exactly as before. No contract version bump is required.

### JNI Scope Resolution:
To prevent settings desynchronization between different themes sharing the same `stateVariable` names, settings are saved and sandboxed under key `"theme_config_[themeFolderName]_[stateVariable]"`. The JNI bridge resolves these transparently so the active theme can query using the simple `stateVariable` name.

---

## Dynamic Visual Modes and Speedometer Customization

In the Default / Sporty theme (`cluster-widgets/source/v1.0/default`), dynamic styling and speedometer layouts are resolved reactively on the fly:

- **Modo Visual (mode)**: Pushes `"theme-light"` or `"theme-dark"` to the `#app` container. In Light mode (`#app.theme-light`), the stylesheet:
  - Overrides canvas backgrounds to pure white (`#ffffff`).
  - Darkens all top status bar header texts (clock, gear value, and EV/drive mode labels) and side labels to high-contrast black (`#0c0c0c`) by explicitly redefining `--dashboard-text-color`, `--text-light-blue`, and `--text-gray`.
  - Disables all text shadows and glowing effects (setting `--text-glow-blue` to transparent and using `text-shadow: none !important` rules) to prevent glows on white backdrops for pristine text legibility.
  - Sets SVG bottom gauge fills dynamically via CSS `--icon-color` to black (`#000000`).
  - Changes progress bar tracks to translucent gray (`rgba(0, 0, 0, 0.1)`) for visible progress empty-spaces.
  - Displays the high-fidelity cockpit background (`dev-bg.png`) symmetrically in both Dark and Light visual modes when `native-mock-enabled` is active, sized to the exact webview bounds (`width: 1920px; height: 596px; top: 62px;`).
- **Estilo dos Marcadores (gaugeStyle)**: Declarative dropdown with choices **Esportivo (Sporty)** and **Clássico (Classic)**:
  - Toggling to `Clássico` adds `.gauge-style-classico` to `#app` which forces the classic dial face and digital speed layout to show, while completely hiding the sporty canvas needle.
  - Toggling to `Esportivo` adds `.gauge-style-esportivo` to `#app` which displays the modern active canvas dial, needle rotation, glow FX, and mock ready overlays.
  - Inherits custom sport speedometer variables globally on the `:root` so the sporty cluster has perfect scale (`1.03`) and correct vertical offsets (`y = 45px`) in both the `Normal` (default) and `Esportivo` display modes.

### 2b. User-Selectable Accent Color (`accentColor`)

The Default theme's blue accent is user-configurable through the `accent_color` configuration (`<type>color</type>`, `stateVariable` = `accentColor`, default `#00A0FF`).

- **Palette derivation** lives in `src/core/accent.js`. Rather than flattening every blue to the picked color, `applyAccent(hex)` *rotates* the shipped palette: it reads the base value of each accent token off the computed `:root` once at boot (so `night.style.css` stays the single source of truth), then re-emits each one rotated toward the pick.
- **The rotation happens in OKLCH, not HSL.** HSL saturation is not a measure of how colorful something looks — it varies enormously with hue. Measured on this theme's own tokens, HSL `S=100%` at hue 229° (the cyan accent) is only `0.151` of real chroma, while `S=100%` at hue 26° (red) is `0.256`. Rotating in HSL holds the *number* at 100% and so inflates real colorfulness by ~70% while perceptual lightness drops ~0.13, which turned 48 of 119 tokens into over-saturated, too-dark versions of themselves and read as "pink". OKLCH separates the three cleanly.
- **Chroma and lightness deltas are weighted** by how "accent-like" a token already is (its share of the reference's chroma). A token sitting on the reference lands exactly on the pick; near-neutral tokens barely move; the pale ice/frost tints keep their airiness instead of being dragged into saturated salmon.
- **The hue *spread* compresses as you rotate away from blue.** Perceptual hue categories are not equally wide: this palette's blues span ~27° (OKLCH 233–260) and all still read as "blue", but rotated rigidly onto red that spread becomes 16–43°, straddling pink-red, red and orange — red only reads as red across roughly 20°. The spread factor is `1.0` at zero rotation, which keeps the shipped blue bit-identical, easing to `0.45` at a half-turn.
- **Reference accent** is `#00A0FF` (`--color-ring-glow`). Semantic colors — greens (success/regen), reds (danger), oranges (power), the fuel/battery bar fills (`--fuel-bar-color`, `--battery-bar-color`), and the speedometer's `hsla(120 - ratio*120, …)` RPM gradient — are deliberately excluded and never move.
- **Baked-in artwork** (top bar, Mapa tray, bottom gauges strip, Mapa speed badge, sport dial ring) is recolored **per pixel**, with the same OKLCH mapping the tokens get. `filter: hue-rotate()` is only the first-frame approximation and the failure fallback: it is a fixed linear matrix, not a real hue rotation, and over large angles it is badly wrong — the reference `#00A0FF` rotated onto red comes out `#FF5946`, a salmon, because the matrix holds luma constant and blue is far darker than red. Once the pixels carry the rotation, `--accent-hue-rotate` **must** be reset to `0deg`, or everything rotates twice.
- **Recolor cost and caching.** The artwork is 87–98% near-gray with a narrow blue band, so the gray majority takes a cheap early-out. Encoding is WebP q0.92, not PNG — measured 225 ms vs 1050 ms on the largest asset (1586×992), with alpha preserved exactly and mean channel error 0.16/255. Results are cached per color (bounded, blobs revoked on eviction) because `driveModeColors` ties the accent to `drivingMode`, which flips while the car is moving. Typical costs: ~2.3 s first change (includes one-time source capture), ~500 ms for a new color, ~70 ms for a cached one.
- **Artwork must be filtered on a dedicated layer**, not on the element that owns it: elements carrying `transition: all` never recompute a `filter` built from `var()` when only the custom property changes, and elements with real children would tint those children too. Each image therefore lives on a `::before` (or, for the dial, a standalone `<img>`), with the real children lifted to `position: relative; z-index: 1`.
- **Light mode is unaffected by design.** `#app.theme-light` overrides several accent tokens to black/white to kill glows on a white backdrop; because those rules are scoped to `#app` they still win over the `:root` inline properties written by `applyAccent`.
- Use `top/left/right/bottom: 0` rather than `inset: 0` on these layers — the head unit is Chrome ~80, which predates the `inset` shorthand (as it does `color-mix()`, `oklch()` and relative color syntax, which is why the derivation is done in JS at all).

### 2c. Drive-Mode Accent and Per-Gauge Colors

- **`driveModeColors`** (`boolean`, default `false`) ties the accent to the drive mode. While enabled, **Sport forces `#FF0000` and Eco forces `#00E676`**; every other mode — Normal, Neve, Areia, Lama — keeps the user's own `accentColor`. Resolution lives in `resolveAccent()` in `src/core/main.js`, which subscribes to `accentColor`, `driveModeColors` and `drivingMode`.
- `applyAccent()` short-circuits when the resolved color is unchanged. This is load-bearing, not an optimization: `drivingMode` pushes arrive on every mode flip and would otherwise restart the full artwork pass for no visible change.
- **`fuelColor`** (default `#3B82F6`) and **`batteryColor`** (default `#10B981`) drive `--fuel-bar-color` / `--battery-bar-color`. These tokens are deliberately **not** in `ACCENT_TOKENS`; staying out is what makes them independent of the accent. Note this is a behavior change: the fuel bar previously used `--primary-blue` and so silently followed the accent.

### 3. Local Simulator Segmented Pill Selectors
In the floating Agent Testing Console testing harness, when a dynamic layout configuration is a `combo` of exactly **two options** (e.g. `mode` with `Dark, Light` or `gaugeStyle` with `Esportivo, Clássico`), the default dropdown `<select>` is automatically replaced with a premium, glassmorphic **segmented pill control** divided in the middle:
- Active options are marked with a soft blue glowing backdrop (`rgba(56, 189, 248, 0.25)`) and cyan text.
- Interactive clicks trigger JNI preference updates and dispatch `onDataChanged` state events, seamlessly synced via reactive getters/setters mapped to the console controller block.

### 4. Interactive State Management Panel (Left Float Console)
For comprehensive state tracking, a floating glassmorphic **State Management Simulator** floats in the bottom-left corner of the emulator (positioned at `left: 20px` to coordinate nicely with other consoles):
- **Visual Toggle**: Minimizes and expands symmetrically alongside other panels by pressing the **`T`** key on the keyboard, or by clicking the header bar.
- **Two-Column Organization**:
  1. **Telemetria Básica (Core Telemetry)**: Sliders and numeric inputs to simulate variables like `carSpeed`, `engineRPM`, `evPowerKw`, `fuelPercent`, and `batteryPercent`.
  2. **Modos e Estados (Modes & Status)**: Control dials and input blocks to simulate states like `gearState` (P, R, N, D), `drivingMode` (Normal, Eco, Sport), `evMode` (HEV, EV), `steerMode` (Conforto, Normal, Esportivo), `clockTime`, `espStatus`, and `appInDash`.
- **Bidirectional Event Synchronization**: Changes inside the state manager (such as physical steering wheel button inputs changing drive/steer modes) automatically sync back to update the console's inputs and pill states in real time.

### 5. Symmetrical Mock Projection (appInDash Backgrounds)
To simulate the physical Haval cluster's projection behaviors during development (`nativeMockEnabled` is true):
- A custom **Map in Dash Projection** checkbox toggle is exposed inside the State Management panel, mapped to JNI state `appInDash`.
- When checked/enabled (`appInDash` is `true`), the cockpit background (`.dev-background`) displays the high-fidelity mock maps projection (`dev-bg.png`).
- When unchecked/disabled (`appInDash` is `false`), the stylesheet appends `.app-in-dash-disabled` to `#app`, which triggers a transition and replaces the map with a premium solid neutral gray background (`#22252a`), mimicking the physical car cluster display.
- **Production Isolation**: This visual projection switching is completely isolated from production bundles and only compiles in local test environments.

This dynamic configuration architecture completely decouples visual choices from native Android logic, enabling full layout customizability purely through standardized theme stylesheets and descriptors.

### 6. Sport Fixed Overlay & Speed Limit Sign Integration (v1.0 default)
- **Overlay Level-Up**: The `.dashboard-fixed-overlay` container (housing the `READY` status text, lane assistance mock graphics, and speed limit sign) is nested inside `.dashboard-speed-container` but rendered with `inset: 0` and custom `z-index: 145`, which positions it symmetrically on the dashboard layout.
- **Classic Mode Support**: With `display: block !important;` assigned via the native mock enabled rules under both layouts, overlays are completely visible and properly rendered in both **Classic** and **Sporty** speedometer gauge styles.
- **Unified 30 Speed Sign**: The "30" speed limit sign, previously implemented as a CSS `::after` pseudo-element on `.dashboard-speed-container`, has been fully converted into a real, high-performance DOM element (`.dashboard-sport-limit-sign` containing text `"30"`) inside `.dashboard-fixed-overlay`. This makes visual styling extremely modular and easily controllable via theme stylesheets.
- **Redundancy Clean-up**: Any redundant CSS layout overrides have been cleaned up and consolidated into standard conditional rules.

### 7. Display Selection Menu Optimization & Key Synchronization
- **Auto-Apply Selection Mode**: The display selection keyboard/steering wheel key event handler has been streamlined to cycle directly through the three valid layout options: `Normal`, `Reduzido` (Reduced), and `Clean` (Minimalist/Clean).
- **Instant layout feedback**: Pressing `UP` or `DOWN` dynamically updates and auto-applies the new theme layout instantly in real time (e.g. updating the active `display` and sync with JNI bridge).
- **Reactive UI carousel synchronization**: Added a reactive subscriber to `displaySelectionScreen` that listens to `display` changes and automatically updates `displayFocus`. This triggers the visual highlight, checked checkmark indicators, and slides the glassmorphic menu carousel to the chosen density option reactively.
