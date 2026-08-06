# Themes Architecture (Contract v1.0)

Updated: 2026-08-06

## Ownership

| Concern | Canonical doc | Update when… |
|---|---|---|
| Theme **authoring** (bridge API table, `theme.xml` fields, HTML/JS patterns) | [`cluster-widgets/Themes/THEME_GUIDE.md`](../../cluster-widgets/Themes/THEME_GUIDE.md) | New/changed bridge methods, telemetry keys, manifest fields, or authoring workflow |
| Theme **system** (layout on disk, APK vs OTA, compatibility policy) | **This file** | Deploy path, catalog branch, contract/bridge versioning rules, or host-side theme loading changes |
| Agent guardrails (do not break `v1.0`) | `AGENTS.md` (repo root; may be local-only) | Policy changes only — keep thin; link here / THEME_GUIDE |

Do **not** duplicate the full bridge reference into `docs/`. Link to `THEME_GUIDE.md`.

## Model

Cluster UI is **theme-owned**. The Android host loads a self-contained `app.html` in a WebView (`InstrumentProjector2`), exposes `window.Android`, and streams telemetry the theme subscribed to. Screens, menus, and navigation live in the theme — not in hardcoded Java screen classes.

### Bridge version vs contract version

1. **Bridge** (`minBridgeVersion` / host `CURRENT_BRIDGE_VERSION = "1.0.0"`)
   - Native `@JavascriptInterface` surface on `window.Android` (`subscribe`, `setClusterBackground`, …).
   - Back-compat polyfills: `CompatTranslationLayer.kt`.

2. **Contract** (`contractVersion = "v1.0"`)
   - Telemetry key dictionary, steering key-event protocol, `theme.xml` shape, layout boundaries.
   - Filtered by `ThemeManager.kt` / `TelasScreen.kt` — incompatible themes are hidden from the catalog.

**Breaking change rule:** if existing `v1.0` themes would miss data or break, stop and create a new contract version (e.g. `v2.0`) instead of silently changing `v1.0`.

## On-disk layout

```text
cluster-widgets/
├── source/
│   ├── noncontract/          # Legacy themes (not contract-compatible)
│   └── v1.0/                 # Active contract sources (default, minimalist, …)
│       └── shared/           # Shared bridge/adapters used by v1.0 themes
├── Themes/
│   ├── THEME_GUIDE.md        # Author guide (API + build)
│   └── v1.0/<theme>/         # Packaged OTA artifacts (app.html + theme.xml + assets)
└── …
```

Unless the user asks otherwise, edit **`source/v1.0/`**, not `noncontract/`.

## Default (APK) vs dynamic (OTA)

| Kind | Source | Build output | How the car gets it |
|---|---|---|---|
| **Default** | `source/v1.0/default/` | `app/src/main/res/raw/app.html` + `app/src/main/assets/Default/theme.xml` | Bundled in the APK |
| **OTA** (e.g. Minimalist) | `source/v1.0/<theme>/` | `Themes/v1.0/<theme>/app.html` + `theme.xml` | In-app catalog downloads from GitHub (`ThemeManager`) |

OTA publish checklist:

1. Bump `<version>` in the theme’s `theme.xml`.
2. `npm run build` in the theme source (Parcel + `inline.js`).
3. Commit **source and** `Themes/v1.0/<theme>/` artifacts.
4. Push to the branch `ThemeManager` crawls (currently `feature/new-screen-enhancements-v7` — see `ThemeManager.THEME_REPO_URL`).

`theme.xml` must ship with `app.html`; the updater compares `<version>` there.

## Native masks (OEM chrome cover)

The cluster still shows **OEM** gauges/strips outside what a WebView alone can paint. Themes
declare `<nativeMasks>` in `theme.xml`; `InstrumentProjector2` draws those regions natively on
Display 3 (wallpaper × optional `d3_mask.png`) so stock chrome can be hidden and the theme
owns the look.

When AA/CarPlay/an app is on D3, the host punches a **hole** in the mask layer for that app
rect so projection is not buried under the masks.

Author details (XML schema, named children, JS APIs): [`THEME_GUIDE.md` — Native masks](../../cluster-widgets/Themes/THEME_GUIDE.md#native-masks-covering-oem-chrome).

Host touchpoints: `ThemeManager` (parse), `InstrumentProjector2` (`updateNativeMaskViews`, punch), `ThemeBridgeImpl.setNativeMaskState` / `setNativeMasksConfig`.

## Host-side touchpoints

- `ThemeManager.kt` — catalog crawl, download, contract filter, metadata parse (incl. `nativeMasks`)
- `ThemeBridgeImpl.kt` / bridge translators — `window.Android` implementation
- `InstrumentProjector2.kt` — WebView load, theme swap, native masks, JS readiness / heartbeat
- `TelasScreen.kt` — UI listing of compatible themes
- `DisplayAppLauncher.kt` — D3 projection prep / mask punch coordination

## Related architecture notes

- Bridge runtime details (including legacy `control()` path): [`kotlin-js-bridge.md`](kotlin-js-bridge.md)
- WebView load / reload: [`webview-flow.md`](webview-flow.md)
- Frontend package shape: [`frontend-flow.md`](frontend-flow.md)
- Module-abort failure mode (orphan top-level call freezes theme): `.cursor/memory/project_theme_module_abort_failure_mode.md` (agent memory)

## Risks

- Editing bridge/contract without a compatibility check breaks installed OTA themes.
- Building Default but forgetting APK raw/assets paths leaves the car on stale HTML.
- Building an OTA theme but not pushing `Themes/v1.0/` means the catalog never sees the update.
- On case-insensitive disks, never introduce a parallel `DOCS/` folder beside `docs/`.
