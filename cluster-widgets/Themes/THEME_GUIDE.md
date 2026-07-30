# Theme Development & Customization Guide

This guide explains how to create, build, and deploy custom themes for the Haval Impuse app. In this decentralized architecture, **themes are completely self-contained applications** that run inside a WebView, receive raw steering wheel keypress events, and dynamically subscribe to car telemetry. 

There are **no hardcoded screens on the Android backend**—the frontend theme HTML/JS/CSS completely defines all screens, transitions, and menus, allowing theme authors to design **unlimited custom screens** (e.g. Main Menu, AC, Regeneration, Trip Stats, Tyre Pressures, Ambient Lighting, or custom Graphics screens).

---

## Table of Contents
1. [Decentralized Theme Architecture](#decentralized-theme-architecture)
2. [Three-Tier Customization Model](#three-tier-customization-model)
3. [Package Structure](#package-structure)
4. [Theme Metadata & Manifests](#theme-metadata--manifests)
5. [The JavaScript Bridge & Primitives](#the-javascript-bridge--primitives)
6. [Development Workflow & Local Simulation](#development-workflow--local-simulation)
7. [Build, Inlining & Deployment](#build-inlining--deployment)

---

## Decentralized Theme Architecture

Themes are designed as self-contained modular applications built on two main concepts:
- **Source Modularity, Compile-Time Inlining**: You write modular JavaScript (using imports from shared packages) and standard CSS. The build pipeline bundles, minifies, and base64-inlines everything (including fonts and assets) into a **single self-contained `app.html`** file with zero external HTTP requests.
- **Client-Driven Subscriptions**: To maximize performance and battery, your theme explicitly tells the Android backend which CAN-bus keys it wants to monitor via `Android.subscribe(keysJson)`. The backend streams updates only for those keys.
- **Raw Event Processing**: The backend dispatches physical steering wheel buttons as raw strings (`'UP'`, `'DOWN'`, `'ENTER'`, `'BACK'`, `'ENTER_LONG'`, etc.) directly to `window.onKeyEvent(key)`. Your theme owns the navigation logic entirely.

---

## Three-Tier Customization Model

To provide both lightning-fast development and infinite creative freedom, you can build themes using three distinct layers:

### Level 1 — Manifest Only (Zero JS Customization)
If you only want to customize menu lists, columns, or setting values, you don't need to write any Javascript. Define your grid columns and cycling behaviors inside the theme's manifest, and write standard HTML referencing item IDs:

```json
// manifest.json or theme.xml
{
  "menu": [
    { "id": "esp",         "action": "cycle",    "key": "CAR_DRIVE_SETTING_ESP_ENABLE",        "values": ["0", "1"] },
    { "id": "evMode",      "action": "cycle",    "key": "CAR_EV_SETTING_POWER_MODEL_CONFIG",   "values": ["3", "1", "0"] },
    { "id": "ac",          "action": "navigate", "screen": "aircon" },
    { "id": "trip",        "action": "navigate", "screen": "tripStats" }
  ],
  "layout": "grid",
  "gridColumns": 2
}
```

```html
<!-- HTML Structure -->
<div class="menu-grid">
  <div class="menu-tile" data-menu-id="esp"></div>
  <div class="menu-tile" data-menu-id="evMode"></div>
  <div class="menu-tile" data-menu-id="ac"></div>
  <div class="menu-tile" data-menu-id="trip"></div>
</div>
```
The headless `MainMenu` component in the runtime automatically registers focus, moves active `.is-focused` styles using keyboard events, and writes settings back to the car.

### Level 2 — Custom Render Callback
If you want animated focus indicators or custom DOM structures while retaining automatic cycling and key traversal logic, pass a custom render function:

```javascript
import { createMainMenu } from "@cluster/shared/mainMenu";

createMainMenu({
  items: manifest.menu,
  container: document.querySelector(".menu-grid"),
  renderItem: (item, state) => `
    <div class="tile ${state.focused ? 'focused' : ''}">
      <span class="label">${state.currentLabel}</span>
    </div>
  `
});
```

### Level 3 — Full Override (Total Freedom)
For highly non-linear, radial, or gesture-driven UI designs, ignore the prebuilt components and import our core reactive primitives to craft your own logic:

```javascript
import { useFocusCycle, useValueCycle, bridge } from "@cluster/shared/runtime";

class RadialDialController {
    constructor() {
        this.focus = useFocusCycle(["esp", "evMode", "ac"], { namespace: "radial" });
    }
    handleKey(keyName) {
        if (keyName === 'LEFT') this.focus.prev();
        if (keyName === 'RIGHT') this.focus.next();
    }
}
```

---

## Package Structure

A finished theme package consists of a folder structured as follows:

```text
Themes/MyCustomTheme/
├── theme.xml        # Core metadata (name, minBridgeVersion)
├── manifest.json    # Level 1 menu and grid configurations
├── thumbnail.png    # 200x200px dashboard preview image
└── app.html         # The compiled, fully self-contained bundle
```

---

## Theme Metadata & Manifests

### 1. The `theme.xml` Configuration
The metadata file tells the launcher how to identify and validate your theme:

```xml
<theme>
    <name>Sports Radial</name>
    <description>Vibrant neon instrumentation dials with radial grid layouts.</description>
    <version>1.2.0</version>
    <minBridgeVersion>1.0.0</minBridgeVersion>
    <contractVersion>v1.0</contractVersion>
    <thumbnail>thumbnail.png</thumbnail>
    <mainFile>app.html</mainFile>
</theme>
```

* **`<minBridgeVersion>`**: Required. Specifies the minimum Android JavaScript Bridge API version required (`1.0.0` for current standardized bridge, `0.1.0` for legacy bridge).
* **`<contractVersion>`**: Required. Specifies the telemetry schema and layout contract (`v1.0`).

---

## Architectural Separation: Bridge Version vs. Contract Version

* **Bridge Version (`minBridgeVersion` / `CURRENT_BRIDGE_VERSION = "1.0.0"`)**:
  - Defines the **native Java/Kotlin API capabilities** exposed to WebView JS via `window.Android`.
  - Controls whether methods like `window.Android.subscribe()`, `setClusterBackground()`, or `launchApp()` exist.
  - Backward compatibility is maintained dynamically via JS polyfills in `CompatTranslationLayer.kt`.
* **Contract Version (`contractVersion = "v1.0"`)**:
  - Defines the **telemetry data key dictionary** (`car.basic.vehicle_speed`), steering key event protocol (`'UP'`, `'DOWN'`, `'ENTER'`, `'BACK'`), and layout rules.
  - Used by `ThemeManager.kt` and `TelasScreen.kt` to ensure only compatible themes are displayed in the Telas tab.

---

## The JavaScript Bridge & Primitives

Theme interactions occur via the global `window.Android` namespace and standard reactive hooks.

### Complete Bridge Function Reference (`v1.0` / `1.0.0`)

| Category | Function | Direction | Description |
| :--- | :--- | :--- | :--- |
| **Diagnostics** | `heartbeat()` | JS → Host | Periodic ping (every 2s) to signal WebView renderer liveness. |
| **Diagnostics** | `getAvailableKeys(): String` | JS → Host | Returns JSON array of all supported CAN-bus/app telemetry keys. |
| **Telemetry** | `subscribe(keysJson: String)` | JS → Host | Subscribes theme to live CAN-bus streaming for JSON array of keys. |
| **Telemetry** | `unsubscribe(keysJson: String)` | JS → Host | Unsubscribes theme from telemetry updates. |
| **Telemetry** | `getCarData(key: String): String` | JS → Host | Synchronously reads current cached telemetry value for a key. |
| **Telemetry** | `updateCarData(key: String, val: String)` | JS → Host | Sends CAN-bus setting command back to vehicle hardware. |
| **Layout & Cutouts** | `setAppDefaultDimensions(x, y, w, h)` | JS → Host | Informs Android of theme canvas bounds for CarPlay/AA cutouts. |
| **Layout & Cutouts** | `setWarningActive(isActive: Boolean)` | JS → Host | Toggles cluster warning banner overlay state. |
| **Wallpaper** | `setClusterBackground(type, val)` | JS → Host | Sets Display-1 cluster background (`THEME`, `PRESET`, `IMAGE_URL`, `FILE`, `COLOR`). |
| **Wallpaper** | `setThemeBackground(relativePath)` | JS → Host | Registers theme package wallpaper asset (e.g. `car-bg.png`). |
| **Preferences** | `savePreference(key, val)` | JS → Host | Persists theme-scoped user configuration. |
| **Preferences** | `getPreference(key, defaultVal): String` | JS → Host | Reads theme-scoped user configuration. |
| **Preferences** | `saveSetting(key, val)` | JS → Host | Saves cluster display setting. |
| **System Actions** | `triggerSystemAction(action, payload)` | JS → Host | Triggers vehicle action (`CANCEL_MAX_AC`, `TRIGGER_AVM_CAMERA`, `BRING_ALL_TO_MAIN`). |
| **Multi-Display** | `launchApp(packageName, displayId)` | JS → Host | Launches target Android app on main or cluster display. |
| **Multi-Display** | `killApp(packageName)` | JS → Host | Kills target Android app process. |

### Lifecycle Execution Sequence

```mermaid
sequenceDiagram
    participant Android as Android Host App
    participant WebView as WebView Engine
    participant Theme as Theme JS Frontend

    Android->>WebView: 1. addJavascriptInterface(ThemeBridgeImpl, "Android")
    Android->>WebView: 2. loadUrl("file:///.../app.html")
    WebView->>Android: 3. onPageFinished() event
    Android->>WebView: 4. CompatTranslationLayer.injectPolyfillsIfNecessary()
    Theme->>Android: 5. window.Android.subscribe(keysJson)
    Theme->>Android: 6. window.Android.setAppDefaultDimensions(x, y, w, h)
    Android->>Theme: 7. updateValuesWebView() [Initial telemetry snapshot push]
    Theme->>Android: 8. window.Android.heartbeat() [Every 2s ping]
    Android->>Theme: 9. window.onCarDataChanged(key, value) [Live telemetry push]
    Android->>Theme: 10. window.onKeyEvent(keyName) [Steering wheel button press]
    Android->>Theme: 11. window.onCardChanged(cardId) [Vehicle switched active card]
```

Note the direction of step 11: card changes originate in the vehicle, not in the theme and
not in the host. LEFT/RIGHT wheel presses are consumed by the vehicle's own cluster
navigation and never reach step 10.

### Reacting to Backend Pushes
Exposed globally in the window scope:
* **`window.onKeyEvent(keyName)`**: Triggered on physical wheel inputs (`'UP'`, `'DOWN'`, `'ENTER'`, `'BACK'`, `'HOME'`, `'ENTER_LONG'`, `'BACK_LONG'`).
  **`LEFT` and `RIGHT` are never delivered.** They are reserved for cluster card
  navigation, which the vehicle performs itself; the host deliberately withholds them so a
  theme cannot fight the car for card control. A theme needing a two-way toggle should use
  `ENTER` (as the Default theme's AC screen does for fan/temp focus).
* **`window.onDataChanged(key, value)`**: Triggered when a subscribed telemetry key emits a new value.
* **`window.onCardChanged(cardId)`**: Fired when the active cluster card changes
  (0 = Hidden, 1 = Main Widgets, 3 = AC). **Required — every v1.0 theme must implement it.**
  This is the single channel by which a theme learns the active card, and it is one-way:
  the card is owned by the vehicle and flows car → host → theme. There is deliberately no
  reverse channel; a theme must never report a card back to the host.

  Note that `cardId` is **not** a 1:1 map of what the cluster is displaying. Card 0 has
  vehicle-state-dependent sub-pages (two of them while the car is charging), and every one
  of them reports `cardId` 0. A theme must therefore not infer a card ordering, assume a
  fixed rotation, or treat "same `cardId` as before" as "nothing changed on the cluster".

  **A theme must ignore wheel input while `cardId` is 0.** Card 0 is the vehicle's own
  card; your theme is not what the driver is looking at. `onKeyEvent` still fires — the
  host forwards wheel input unconditionally, because the theme owns its navigation logic
  and gating it host-side would put card semantics in two places. Acting on those presses
  is therefore a theme bug, and not a harmless one: several theme controls write to the
  CAN bus, so a theme that keeps handling `UP`/`DOWN` on card 0 will change vehicle state
  (this was observed raising the AC fan while the driver was on the native card).

---

## Development Workflow & Local Simulation

You don't need a real car or an Android device to build your themes. The local development environment comes with a fully-featured **Local Keyboard & Telemetry Simulator**:

1. **Local Boot**: From your theme's folder, run:
   ```bash
   npm run dev-controls
   ```
2. **Local Simulation**:
   * Opens a hot-reloading development server in your browser.
   * Desktop arrow keys automatically map to steering wheel inputs:
     * `ArrowUp` / `ArrowDown` &rarr; `UP` / `DOWN`
     * `Enter` &rarr; `ENTER`
     * `Escape` &rarr; `BACK`
   * Stubbed `Android.subscribe` and `updateCarData` APIs allow you to view data changes directly in your browser's Developer Tools Console in real-time.

---

## Build, Inlining & Deployment

### 1. The Build Command
To compile your modular theme into a single bundle:
```bash
npm run build
```
This runs `parcel build` and executes `inline.js` to create your self-contained `app.html` inside the `dist/` directory.

### 2. Automatic Syncing
During development, the reference project is configured to automatically copy the compiled `dist/app.html` file into the main Android application's resources at `app/src/main/res/raw/app.html`.

### 3. Submitting Custom Themes
To publish your theme for all users to download:
1. Save your compiled package (containing `theme.xml`, `manifest.json`, `thumbnail.png`, and `app.html`) under a unique subfolder in `cluster-widgets/Themes/[ThemeFolder]`.
2. Commit and push your changes to your feature branch.
3. The launcher dynamically crawls the directory structure and populates compatible themes in the **Telas** screen.

---

## Unlimited Custom Screens & Navigation

Because the native backend is completely decentralized, the theme frontend functions as a modern Single-Page Application (SPA) where the state variable `screen` controls the layout. Developers can create unlimited high-fidelity custom views (e.g. tyre pressure panels, ambient lighting controls, trip computer statistics, secondary menus) without touching the native Android application or writing complex event-routing boilerplate.

### 1. Screen Cache vs. Dynamic Lifecycle
To optimize memory and performance on low-resource head units, the rendering pipeline categorizes screens:
* **Cached Screens** (e.g., `'main_menu'`, `'aircon'`): Initialized once at boot time. They remain in the DOM and are simply toggled between `display: 'block'` and `display: 'none'` for instant response.
* **Dynamic Screens** (e.g., `'regen'`, `'display_selection'`, `'graph'`): Instantiated on the fly when navigated to, and **fully destroyed and garbage-collected** when navigated away.

### 2. How to Build a Custom Screen (Example: Tyre Pressure)

#### Step A: Create the Component
Create a new file in your theme source (e.g., `src/core/components/tirePressure.js`). Export a factory function returning the root DOM element and a critical `cleanup` hook:

```javascript
import { getState, subscribe } from '../state.js';
import { div, span } from '../../../../shared/utils/createElement.js';

export function createTirePressureScreen() {
    const container = div({ className: 'tire-pressure-container' });
    const title = span({ className: 'screen-title', children: ['Tire Pressure'] });
    container.appendChild(title);

    const subscriptions = [];

    // Subscribe to telemetry states reactively
    subscriptions.push(subscribe('frontLeftTirePressure', (pressure) => {
        // Smoothly update the visual layout
    }));

    // Cleanup hook - critical to prevent memory leaks and background CPU cycles
    const cleanup = () => {
        subscriptions.forEach(unsubscribe => unsubscribe());
    };

    return { element: container, cleanup };
}
```

#### Step B: Register the Screen in the Main Renderer
1. Import your factory in `main.js`:
   ```javascript
   import { createTirePressureScreen } from './components/tirePressure.js';
   ```
2. In the dynamic screen rendering block within `render()` in `main.js`, add a route handler:
   ```javascript
   } else if (screen === 'tire_pressure') {
       componentResult = createTirePressureScreen();
   }
   ```

#### Step C: Hook Key and Steering Wheel Inputs
Add a key handler block inside the steering wheel listener in `main.js`:
```javascript
} else if (screen === 'tire_pressure') {
    if (keyName === 'BACK') {
        setState('screen', 'main_menu'); // Smoothly navigate back
    }
}
```

#### Step D: Link from the Declarative Menu
1. **Visual Declaration** in `mainMenu.js`:
   ```javascript
   { id: 'option_8', label: 'Pneus', iconSrc: iconTires },
   ```
2. **Behavioral Declaration** in `main.js` under `manifest.menu`:
   ```javascript
   { id: 'option_8', action: 'navigate', screen: 'tire_pressure' }
   ```
   
The routing engine automatically maps focus transitions, carousel scroll positions, keyboard selection triggers, and subscription states cleanly.


