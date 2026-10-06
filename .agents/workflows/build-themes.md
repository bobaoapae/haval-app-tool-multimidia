---
description: Build inlined html file for all themes, and updates the Themes folder
---

## Theme Architecture & Location Conventions

1. **Contract vs. Non-Contract (Legacy) Themes**:
   - **Non-contract (legacy) themes**: Source is under `cluster-widgets/source/noncontract/<<folder>>/`.
   - **Contract themes**: Source is under `cluster-widgets/source/v1.0/<<folder>>/`.
   - **Rule**: Unless explicitly requested otherwise by the user, agents **MUST** always target the latest contract version folder (`cluster-widgets/source/v1.0/`).

2. **Default Theme (Bundled in APK)**:
   - Source: `cluster-widgets/source/v1.0/default/`
   - Target Output: `cluster-widgets/Themes/v1.0/Default/`
   - The Default theme is deployed **bundled directly inside the APK**.
   - Running `npm run build` in `cluster-widgets/source/v1.0/default/` automatically builds the inlined HTML and copies the required output files to:
     - `app/src/main/res/raw/app.html` (main APK theme HTML)
     - `app/src/main/assets/Default/theme.xml` (main APK theme manifest)

3. **Dynamic Themes (e.g. Minimalist - Downloaded OTA)**:
   - Source: `cluster-widgets/source/v1.0/minimalist/`
   - Target Output: `cluster-widgets/Themes/v1.0/minimalist/`
   - Dynamic themes like Minimalist are downloaded on-demand by the Android app from GitHub (`ThemeManager.kt`).
   - The GitHub target branch is defined in app code (`ThemeManager.kt`, currently `feature/new-screen-enhancements-v7`).
   - **Rule**: When requested to build or deploy a dynamic theme like Minimalist:
     1. Bump the theme version in `cluster-widgets/source/v1.0/minimalist/theme.xml`.
     2. Run `npm run build` in `cluster-widgets/source/v1.0/minimalist/` (which compiles `app.html` into `cluster-widgets/Themes/v1.0/minimalist/`).
     3. **Commit and push** the updated source and `Themes/v1.0/minimalist/` files to the designated git branch (currently `feature/new-screen-enhancements-v7`) so it becomes available for in-app download and update detection.

## Build Steps Checklist

For each theme folder with changes:
1. Run `npm run build` in `cluster-widgets/source/v1.0/<<folder>>/`.
2. Confirm output in `cluster-widgets/Themes/v1.0/<<folder>>/` (`app.html` / `index.html` and `theme.xml`).
3. For `Default`: verify `app/src/main/res/raw/app.html` and `app/src/main/assets/Default/theme.xml` were updated.
4. For `Minimalist` / dynamic themes: commit and push changes to the active release branch (`feature/new-screen-enhancements-v7`).