# Architecture Overview

Updated: 2026-08-06

## What this app is

Android app `HavalShisuku` (`br.com.redesurftank.havalshisuku`) for Haval/GWM head units: vehicle integration via AIDL/Shizuku, custom cluster UI on secondary displays, and projection helpers (Android Auto / CarPlay tooling lives partly under `scripts/`).

## Stack

- Android Kotlin/Java (Gradle Kotlin DSL)
- Jetpack Compose for the main settings UI
- Shizuku / privileged shell for OEM service access
- WebView inside a `Presentation` for the cluster dashboard
- Theme frontends in JS/CSS/HTML (Parcel + inline packaging)
- `tools/headunit-dev/` for deploy / logs / diagnostics

## Cluster UI (current)

Themes are **self-contained** Web apps under contract `v1.0`. The host does not own screen FSMs for the dashboard; the loaded theme owns screens and navigation.

- Architecture summary: [`themes-contract-v1.md`](themes-contract-v1.md)
- Author guide: [`cluster-widgets/Themes/THEME_GUIDE.md`](../../cluster-widgets/Themes/THEME_GUIDE.md)
- Sources: `cluster-widgets/source/v1.0/`
- OTA packages: `cluster-widgets/Themes/v1.0/`
- Legacy (non-contract): `cluster-widgets/source/noncontract/`

## Related files

- `app/src/main/java/br/com/redesurftank/havalshisuku/services/ForegroundService.java`
- `…/managers/ServiceManager.java`
- `…/managers/ProjectorManager.java`
- `…/managers/ThemeManager.kt`
- `…/projectors/InstrumentProjector2.kt`
- `…/bridge/ThemeBridgeImpl.kt`

## Risks

- Bootstrap / Shizuku / ServiceManager changes can block startup.
- Secondary-display / projector changes can blank the cluster or break AA/CarPlay cutouts.
- Theme bridge or contract edits without a compatibility check break installed themes.
- WebView/JS errors can freeze a theme (“pretty but frozen” if a module aborts at load).

## Open questions

- Which firmwares/models are primary targets.
- How long `noncontract/` themes remain supported in the field.
