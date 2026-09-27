import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createThemeCatalog } from './theme-catalog.mjs';

const clusterWidgetsRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const themes = createThemeCatalog(clusterWidgetsRoot);
const requiredFolders = [
  'source/v1.0/default',
  'source/v1.0/minimalist',
  'source/v1.0/ApexGT',
  'Themes/SportRed',
  'Themes/SportRedLite',
];

const missingFolders = requiredFolders.filter(
  (requiredFolder) => !themes.some((theme) => theme.folder === requiredFolder),
);

const apexGT = themes.find((theme) => theme.folder === 'source/v1.0/ApexGT');

if (missingFolders.length > 0 || apexGT?.keyboard !== 'none' || apexGT?.telemetry !== 'injected') {
  console.error(`Catálogo inválido. Pastas ausentes: ${missingFolders.join(', ')}`);
  if (apexGT?.keyboard !== 'none' || apexGT?.telemetry !== 'injected') {
    console.error('Apex GT deve usar telemetria injetada sem navegação duplicada no Theme Lab.');
  }
  process.exitCode = 1;
} else {
  console.log(`Catálogo válido: ${themes.length} temas encontrados.`);
  for (const theme of themes) {
    console.log(`- ${theme.label} ${theme.version} [${theme.kind}] -> ${theme.folder}`);
  }
}
