---
description: Build inlined html file for all themes, and updates the Themmes folder
---

Review the cluster-widgets folder if there are changes in the files for any of the subfolders (each one is a specific theme).
For each folder with changes do the following
1. execute 'npm run build'
2. copy the generated inlined html file from dist folder to the cluster-widgets/Themes/<<folder>> where <<folder> would be the respective folder for that theme (it matches the name)
3. if the theme is 'default' we should also copy the generated inlined html file from dist folder to app\src\main\res\raw\app.html as the main theme
4. update the cluster-widgets/Themes/<<folder>>/theme.xml and increase the version in 0.0.1