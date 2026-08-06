# Frontend Flow

Updated: 2026-08-06

## Layout

Active contract themes live under `cluster-widgets/source/v1.0/<theme>/`.
Legacy packages remain in `cluster-widgets/source/noncontract/`.
Packaged OTA output is `cluster-widgets/Themes/v1.0/<theme>/`.

Typical theme package:

- `index.html` / entry used by Parcel
- `src/` (JS components, styles)
- `theme.xml` (name, version, `minBridgeVersion`, `contractVersion`, settings)
- `inline.js` + `package.json`
- Build emits a single self-contained `app.html`

See [`themes-contract-v1.md`](themes-contract-v1.md) and [`THEME_GUIDE.md`](../../cluster-widgets/Themes/THEME_GUIDE.md).

## JS contract (v1.0)

- `window.Android.*` — host bridge (`subscribe`, telemetry, prefs, wallpaper, …)
- `window.onKeyEvent(key)` — raw steering keys from the host
- Theme-defined screens/menus — not a fixed Android screen enum

Legacy globals (`window.control`, `window.showScreen`, `window.focus`) may still appear in older packages; do not treat them as the v1.0 authoring model.

## Build

Parcel bundles the theme; `inline.js` produces one HTML with inlined CSS/JS/assets.

- **Default** → APK `res/raw/app.html` + `assets/Default/theme.xml`
- **OTA themes** → `Themes/v1.0/<theme>/` (commit + push to the ThemeManager catalog branch)

## Related paths

- `cluster-widgets/source/v1.0/default/`
- `cluster-widgets/source/v1.0/minimalist/`
- `cluster-widgets/source/v1.0/shared/`
- `cluster-widgets/Themes/v1.0/`

## Risks

- Renaming bridge globals breaks the host contract.
- Default build that skips APK copy leaves the car on stale HTML.
- OTA build that skips `Themes/v1.0/` never reaches the in-app catalog.
- Simulator-only CSS must stay gated out of production bundles.
