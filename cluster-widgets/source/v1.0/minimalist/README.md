# Minimalist Theme (v1.0 contract)

OTA cluster theme — **not** bundled in the APK. The in-app catalog downloads it from `cluster-widgets/Themes/v1.0/minimalist/` on the release branch (`ThemeManager.kt`, currently `bobaoapae/preview`).

## Local testing

```bash
npm run dev
```

Dev server (usually `http://localhost:1234` / next free port) with simulator harness via `testing-utils.js`. Append `?nativeMocks=1` for mock cluster backgrounds.

## Build & publish (OTA)

Unlike the Default theme (which copies into `app/src/main/res/raw` + `assets/`), Minimalist's build **only** publishes into `Themes/`:

```bash
# 1. Bump <version> in theme.xml (required for in-car update detection)
# 2. Build
npm run build
```

`npm run build` clears `dist/`, runs Parcel, then `inline.js`, which:

1. Inlines CSS/JS into a self-contained `dist/app.html`
2. Copies `dist/app.html` → `cluster-widgets/Themes/v1.0/minimalist/app.html`
3. Copies `theme.xml` → `cluster-widgets/Themes/v1.0/minimalist/theme.xml`

Then **commit and push both** the source changes and `Themes/v1.0/minimalist/` to the release branch so the app can list/download the new version.

> Do not copy Minimalist into `res/raw` / APK assets — that path is Default-only.
