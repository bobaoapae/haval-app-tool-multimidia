import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createThemeCatalog } from './theme-catalog.mjs';

const clusterWidgetsRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const themes = createThemeCatalog(clusterWidgetsRoot);
const requiredFolders = [
  'source/v1.0/default',
  'source/v1.0/minimalist',
  'Themes/SportRed',
  'Themes/SportRedLite',
];

const missingFolders = requiredFolders.filter(
  (requiredFolder) => !themes.some((theme) => theme.folder === requiredFolder),
);

if (missingFolders.length > 0) {
  console.error(`Catálogo inválido. Pastas ausentes: ${missingFolders.join(', ')}`);
  process.exitCode = 1;
} else {
  console.log(`Catálogo válido: ${themes.length} temas encontrados.`);
  for (const theme of themes) {
    console.log(`- ${theme.label} ${theme.version} [${theme.kind}] -> ${theme.folder}`);
  }
}
