# Theme Development Guide

This guide explains how to create, build, and deploy custom themes for the Application. Themes are essentially self-contained HTML/JS/CSS applications that run in a WebView and interact with the car's data.

## Table of Contents
1. [Theme Architecture](#theme-architecture)
2. [Package Structure](#package-structure)
3. [Metadata (theme.xml)](#metadata-themexml)
4. [Development Workflow](#development-workflow)
5. [Build & Inlining](#build--inlining)
6. [Deployment](#deployment)

---

## Theme Architecture

Themes are built as Single Page Applications (SPAs). To ensure compatibility and performance across different Android environments:
- **Self-Contained**: All CSS and JavaScript must be inlined into a single `.html` file.
- **Data Driven**: Themes receive car data updates through a JavaScript bridge (defined in the widget's core logic).
- **Mode-Specific**: Themes are typically designed for a specific lighting condition (Light or Dark). The application or user selects the appropriate theme package based on preference or environmental data.

## Package Structure

A theme consists of a folder containing at least three files:

```text
Themes/MyCustomTheme/
├── theme.xml        # Metadata (name, version, etc.)
├── thumbnail.png    # Preview image (recommended 200x200px)
└── index.html       # The bundled theme application
```

> [!IMPORTANT]
> The filename of the HTML file must match the `<mainFile>` tag in `theme.xml`.

---

## Metadata (theme.xml)

The `theme.xml` file provides the `ThemeManager` with information about your theme.

```xml
<theme>
    <name>My Awesome Theme</name>
    <description>A premium dark theme with neon accents.</description>
    <version>1.0.0</version>
    <thumbnail>thumbnail.png</thumbnail>
    <mainFile>index.html</mainFile>
</theme>
```

| Tag | Description |
| :--- | :--- |
| `<name>` | Display name in the theme selector. |
| `<description>` | Short summary of the theme's aesthetics. |
| `<version>` | Semantic version (e.g., 1.0.1). Used for update detection. |
| `<thumbnail>`| Filename of the preview image in the same folder. |
| `<mainFile>` | The entry point HTML file (must contain inlined JS/CSS). |

---

## Development Workflow

While you can write a raw HTML file, it is recommended to use a modern build system. The `cluster-widgets/air-control` serves as the official reference:

1.  **Frameworks**: Use any logic (React, Vue, Vanilla JS).
2.  **Styles**: Standard CSS. Use variables for easy color switching.
3.  **Assets**: Icons should be SVGs (inlined) or Base64 encoded to ensure they stay within the single HTML file.

### Reference Project
Check [cluster-widgets/air-control](../air-control) for:
- [index.html](../air-control/index.html): Entry point.
- [src/core/main.js](../air-control/src/core/main.js): Core logic.

For development, you can clone the reference project to a new folder fo your theme, such as `cluster-widgets/Themes/[YourThemeName]/`


---

## Build & Inlining

The `ThemeManager` expects a **single HTML file**. All external scripts and stylesheets must be inlined.

### 1. Bundling with Parcel
Most widgets use [Parcel](https://parceljs.org/) to bundle the source code. From your theme folder run:
```bash
npm run build
```

### 2. Inlining Script
After bundling, run the `inline.js` script (usually automatically called by `npm run build`). This script:
1. Replaces `<link rel="stylesheet">` with `<style>` tags containing the actual CSS.
2. Replaces `<script src="...">` with `<script>` tags containing the JavaScript.
3. Converts dynamic assets (like CSS referenced in JS) to Base64 Data URIs.

Reference: [inline.js](../air-control/inline.js).

### 3. Copying Build Output
By default, the `inline.js` script handles the following:
-   **Generation**: Creates a unified `dist/app.html` (or similar name specified in the script).
-   **Automatic Sync**: Copies the generated file directly to the Android project's resources at `app/src/main/res/raw/app.html`.

If you are creating a **Theme Package** for the repository:
1.  Locate the generated HTML in the `dist/` folder.
2.  Copy it to your theme's folder under `cluster-widgets/Themes/[YourThemeName]/`.
3.  Ensure the `<mainFile>` tag in your `theme.xml` matches the filename.

---

## Testing

Before deploying your theme, you should verify the layout and logic:

### Local Web Testing
You can run a local development server to see your changes in real-time:
```bash
npm run dev-controls
```
This command starts a Parcel development server (usually at `http://localhost:1234`). While this won't show real car data, it allows you to:
-   **Elements Tab**: Inspect the DOM structure and live-edit CSS to fine-tune layouts.
-   **Console Tab**: Monitor for JavaScript errors and use `console.log` for debugging logic.
-   **Network Tab**: Ensure all assets (if any are left external) are loaded correctly.
-   **Responsive Mode**: Use Chrome's device toolbar to simulate the car's screen resolution.

### Instrumented Testing
To test with real data, you must build the project and run it on a device or emulator as part of the main application.

---

## Remote Debugging

If a theme behaves differently in the car than in your browser, you can use Chrome's remote debugging tools.

### 1. Enable USB Debugging
Ensure your car's Android unit has **Developer Options** enabled and **USB Debugging** turned on. Connect your laptop to the car (usually via a USB-C/A data cable).

### 2. Chrome Inspect
Open Chrome on your laptop and navigate to:
`chrome://inspect/#devices`

### 3. Trace the WebView
1.  Launch the App on the car and navigate to the screen using your theme.
2.  In `chrome://inspect`, you should see a "WebView" entry for the application.
3.  Click **inspect**. 

This opens a full set of Chrome DevTools connected directly to the car's screen, allowing you to:
-   View the live console from the car.
-   Inspect and modify styles directly on the vehicle's display.
-   Debug performance or rendering issues specific to the car's hardware.

---

## Deployment

### Repository Discovery
To make your theme available for all users:
1. Create a subfolder under [cluster-widgets/Themes/\<YOUR THEME FOLDER\>](./).
2. Add your `theme.xml`, `thumbnail.png`, and the inlined `.html` file.
3. Make sure your `theme.xml` file is updated with your theme's information.
4. Commit and push your changes.

> [!IMPORTANT]
> Currently the app is looking for themes into the `feature/new-screen-enhancements-v6` branch. This will be reviewed in future.

The `Haval Impulse` application will automatically detect the new folder and show it in the app's theme page.

## Imported Sport theme packages

`SportRed` and `SportRedLite` are distributed here as prebuilt, self-contained theme packages at
version `0.16.44`. Their generated `index.html` files are intentionally kept unchanged because the
corresponding editable frontend source is not part of this repository.

The packages preserve the cluster content bounds declared by their metadata: `1920x596` at
`x=0`, `y=62`, inside the physical `1920x720` display. They do not change Android display bounds or
the fullscreen behavior of native projections.

Run the static package contract before publishing changes:

```bash
./cluster-widgets/Themes/validate_sport_themes.sh
```

The validator checks the canonical SHA-256 hashes, metadata/bounds, Android-WebView global
functions, and the frontend keys used by the color palettes, Now Playing, trip/TPMS panel,
speed-limit sign, and map speedometer preference. Canonical hashes are also recorded in
`SPORT_THEMES_SHA256SUMS`.

The generated payload contains a map image URL used only by its native mock mode. That mode is
gated by `?nativeMocks=1` or `window.__ENABLE_NATIVE_MOCKS`; neither flag is part of the normal
local-file loading path. Keep this boundary in the vehicle smoke test because the editable source
needed to remove or replace that development asset is not available here.
