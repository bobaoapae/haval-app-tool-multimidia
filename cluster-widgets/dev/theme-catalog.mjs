import fs from 'node:fs';
import path from 'node:path';

function readTag(xml, tagName, fallback = '') {
  const match = xml.match(new RegExp(`<${tagName}>([\\s\\S]*?)</${tagName}>`, 'i'));
  return match ? match[1].trim() : fallback;
}

function toWebPath(...segments) {
  return `/${segments
    .flatMap((segment) => String(segment).split(path.sep))
    .filter(Boolean)
    .map(encodeURIComponent)
    .join('/')}`;
}

function listDirectories(parentPath) {
  if (!fs.existsSync(parentPath)) return [];
  return fs.readdirSync(parentPath, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && !entry.name.startsWith('.'))
    .map((entry) => entry.name);
}

function readTheme(directory, relativeDirectory, kind, preferredEntry) {
  const manifestPath = path.join(directory, 'theme.xml');
  if (!fs.existsSync(manifestPath)) return null;

  const manifest = fs.readFileSync(manifestPath, 'utf8');
  const declaredMainFile = readTag(manifest, 'mainFile', 'index.html');
  const candidates = [preferredEntry, declaredMainFile, 'index.html', 'app.html'].filter(Boolean);
  const mainFile = candidates.find((candidate) => {
    const safeName = path.basename(candidate);
    return safeName === candidate && fs.existsSync(path.join(directory, safeName));
  });
  if (!mainFile) return null;

  const folderName = path.basename(directory);
  const contractVersion = readTag(manifest, 'contractVersion');
  const isSport = /^SportRed(?:Lite)?$/i.test(folderName);
  const label = readTag(manifest, 'name', folderName);
  const route = `${toWebPath(relativeDirectory, mainFile)}?nativeMocks=1&themeLab=1`;

  return {
    id: `${kind}:${relativeDirectory.replaceAll(path.sep, '/')}`,
    label,
    folderName,
    version: readTag(manifest, 'version', 'A confirmar'),
    description: readTag(manifest, 'description'),
    contractVersion: contractVersion || null,
    kind,
    editable: kind === 'source-v1',
    keyboard: kind === 'source-v1' ? 'native' : (isSport ? 'legacy-sport' : 'legacy-generic'),
    folder: relativeDirectory.replaceAll(path.sep, '/'),
    route,
    manifestRoute: toWebPath(relativeDirectory, 'theme.xml'),
  };
}

export function createThemeCatalog(clusterWidgetsRoot) {
  const themes = [];
  const sourceV1Relative = path.join('source', 'v1.0');
  const sourceV1Root = path.join(clusterWidgetsRoot, sourceV1Relative);

  for (const folderName of listDirectories(sourceV1Root)) {
    if (folderName === 'shared') continue;
    const relativeDirectory = path.join(sourceV1Relative, folderName);
    const theme = readTheme(
      path.join(clusterWidgetsRoot, relativeDirectory),
      relativeDirectory,
      'source-v1',
      'index.html',
    );
    if (theme) themes.push(theme);
  }

  const legacyRelative = 'Themes';
  const legacyRoot = path.join(clusterWidgetsRoot, legacyRelative);
  for (const folderName of listDirectories(legacyRoot)) {
    if (folderName === 'v1.0') continue;
    const relativeDirectory = path.join(legacyRelative, folderName);
    const theme = readTheme(
      path.join(clusterWidgetsRoot, relativeDirectory),
      relativeDirectory,
      'package-legacy',
    );
    if (theme) themes.push(theme);
  }

  const packagedV1Relative = path.join('Themes', 'v1.0');
  const packagedV1Root = path.join(clusterWidgetsRoot, packagedV1Relative);
  const sourceFolderNames = new Set(
    themes
      .filter((theme) => theme.kind === 'source-v1')
      .map((theme) => theme.folderName.toLowerCase()),
  );
  for (const folderName of listDirectories(packagedV1Root)) {
    if (sourceFolderNames.has(folderName.toLowerCase())) continue;
    const relativeDirectory = path.join(packagedV1Relative, folderName);
    const theme = readTheme(
      path.join(clusterWidgetsRoot, relativeDirectory),
      relativeDirectory,
      'package-v1',
    );
    if (theme) themes.push(theme);
  }

  const kindOrder = new Map([
    ['source-v1', 0],
    ['package-v1', 1],
    ['package-legacy', 2],
  ]);

  return themes.sort((left, right) => {
    const kindDifference = kindOrder.get(left.kind) - kindOrder.get(right.kind);
    if (kindDifference !== 0) return kindDifference;
    const sportDifference = Number(!/^Sport Colors/i.test(left.label)) - Number(!/^Sport Colors/i.test(right.label));
    if (sportDifference !== 0) return sportDifference;
    return left.label.localeCompare(right.label, 'pt-BR');
  });
}
