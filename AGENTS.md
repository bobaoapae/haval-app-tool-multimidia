# Agent Guidelines — Haval Impulse / Shisuku

This document defines core operating conventions and safety rules for AI coding agents (Antigravity, Gemini, Claude, Cursor, Copilot, etc.) working in this repository.

## 🚨 Interface Contract Safeguard (CRITICAL)

> [!CAUTION]
> **INTERFACE CONTRACT SAFEGUARD**
> Any agent attempting to modify or extend the interface contract between the Android backend host and frontend themes (bridge methods, telemetry keys, payload schemas, `theme.xml` specification, key event contracts, or status bar layout boundaries) **MUST** verify retro-compatibility first.
>
> If 100% backwards compatibility cannot be guaranteed for existing themes, the agent **MUST STOP** and explicitly ask the user if a new contract version (e.g. `v2.0`) needs to be created instead of modifying the existing `v1.0` contract.

---

## 🏗️ Architectural Separation: Bridge vs. Contract

When working on cluster widgets or Android bridge code, distinguish between:

1. **Bridge Version (`minBridgeVersion` / `CURRENT_BRIDGE_VERSION = "1.0.0"`)**:
   - Represents the native Java/Kotlin `@JavascriptInterface` API methods on `window.Android`.
   - Controls runtime feature support (`window.Android.subscribe()`, `setClusterBackground()`, etc.).
   - Managed in `CompatTranslationLayer.kt` using JavaScript polyfills for backwards compatibility.

2. **Contract Version (`contractVersion = "v1.0"`)**:
   - Represents the standardized telemetry key dictionary (`car.basic.vehicle_speed`), steering key event protocol, and `theme.xml` manifest structure.
   - Used by `ThemeManager.kt` and `TelasScreen.kt` to filter compatible themes.

---

## 🎨 Theme Building & Deployment Rules

1. **Target Contract Versions**:
   - `cluster-widgets/source/noncontract/` holds legacy, non-contract themes.
   - Active, supported themes are under contract version directories (e.g. `cluster-widgets/source/v1.0/`).
   - **Rule**: Unless explicitly specified otherwise by the user, agents **MUST** always target the latest contract version folder (`cluster-widgets/source/v1.0/`).

2. **Default Theme (Bundled in APK)**:
   - `cluster-widgets/source/v1.0/default/` is deployed **bundled directly inside the APK**.
   - Running `npm run build` in `cluster-widgets/source/v1.0/default/` automatically compiles `app.html` to `app/src/main/res/raw/app.html` and `theme.xml` to `app/src/main/assets/Default/theme.xml`.

3. **Dynamic Themes (e.g. Minimalist - Downloaded OTA)**:
   - Dynamic themes like `Minimalist` (`cluster-widgets/source/v1.0/minimalist/`) are downloaded on-demand by the app over-the-air from GitHub (`ThemeManager.kt`).
   - The target release branch is defined in app code (`ThemeManager.kt`, currently `feature/new-screen-enhancements-v7`).
   - **Build output (unlike Default)**: `npm run build` in the theme source runs Parcel + `inline.js`, which:
     1. Produces a self-contained minified `dist/app.html` (CSS/JS inlined).
     2. Copies `dist/app.html` → `cluster-widgets/Themes/v1.0/<theme>/app.html`.
     3. Copies `theme.xml` → `cluster-widgets/Themes/v1.0/<theme>/theme.xml` (must travel with `app.html` — the updater compares `<version>` here).
   - Dynamic themes are **not** copied into the APK (`res/raw` / `assets/`). `Themes/` on the release branch is the only publish destination the in-app catalog crawls.
   - **Rule**: When requested to build or deploy a dynamic theme like Minimalist, agents **MUST**:
     1. Bump the theme version in `cluster-widgets/source/v1.0/minimalist/theme.xml`.
     2. Run `npm run build` in `cluster-widgets/source/v1.0/minimalist/`.
     3. **Commit and push** the updated source **and** the compiled `cluster-widgets/Themes/v1.0/minimalist/` assets (`app.html`, `theme.xml`, and any other package files) to the designated release branch (currently `feature/new-screen-enhancements-v7`) so it appears for in-app download and update detection.

---

## 📋 Pre-Flight Checklist Before Modifying Theme Interfaces

Before making any change to `ThemeBridgeImpl.kt`, `BridgeContractTranslator.kt`, `InstrumentProjector2.kt`, or `cluster-widgets/source/`:
- [ ] Check if the change introduces new keys or modifies existing key names.
- [ ] Verify if legacy themes or `v1.0` themes will continue to operate without missing data.
- [ ] Update `THEME_GUIDE.md` and `docs/architecture/themes-contract-v1.md` if new bridge methods or telemetry keys are added.
- [ ] If backwards compatibility is broken, **STOP** and ask the user to declare a new contract version (e.g. `v2.0`).
