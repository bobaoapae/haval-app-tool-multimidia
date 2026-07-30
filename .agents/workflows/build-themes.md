---
description: Build inlined html file for all themes, and updates the Themmes folder
---

Review the `cluster-widgets/source/` folder if there are changes in any theme:
- **Non-contract (legacy) themes**: Source is under `cluster-widgets/source/noncontract/<<folder>>/`.
- **v1.0 contract themes**: Source is under `cluster-widgets/source/v1.0/<<folder>>/`.

For each folder with changes, execute the following:
1. Run `npm run build` in the source directory of the theme.
2. Copy the generated inlined HTML file from the `dist/` folder to the target output destination:
   - For **non-contract** themes: `cluster-widgets/Themes/<<folder>>/` (retains the legacy root structure, e.g. `Basic`, `BasicLight`, `Default`).
   - For **v1.0 contract** themes: `cluster-widgets/Themes/v1.0/<<folder>>/` (retains the versioned structure, e.g. `Default`).
3. If the theme is `v1.0/default`, also copy the generated inlined HTML file to `app/src/main/res/raw/app.html` as the main APK theme, and copy `theme.xml` to `app/src/main/assets/Default/theme.xml` (only Default theme copies XML to `assets`).
4. Update the `theme.xml` in the respective output folder and increase the version by 0.0.1.