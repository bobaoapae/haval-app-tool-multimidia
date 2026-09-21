import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderBundle } from './bundle.mjs';
import { renderSharedRuntimeScript } from './shared-runtime.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const published = path.resolve(root, '../../../Themes/v1.0/ApexGT');
const html = fs.readFileSync(path.join(root, 'dist/app.html'), 'utf8');
const source = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const manifest = fs.readFileSync(path.join(root, 'theme.xml'), 'utf8');
const expectedHtml = renderBundle(root);
assert.match(manifest, /<contractVersion>v1\.0<\/contractVersion>/);
assert.match(manifest, /<minBridgeVersion>1\.0\.1<\/minBridgeVersion>/);
assert.match(html, /data:image\/webp;base64,/);
assert.match(html, /data:font\/woff/);
assert.doesNotMatch(html, /\b(?:src|href)=["']\.\//);
assert.doesNotMatch(html, /url\(["']?(?:https?:|\.\.)/);
assert.doesNotMatch(html, /theme-lab|evPowerKw|setAppDefaultDimensions|setNativeMask|setClusterBackground/);
assert.match(html, /ApexMenus/);
assert.match(html, /ApexSpeed/);
assert.match(html, /ApexProjection/);
assert.match(html, /window\.ApexShared = \{/);
assert.match(manifest, /<stateVariable>apexProjectionMode<\/stateVariable>/);
// Projecao integrada: a rampa chega a transparente na borda do viewport e as quinas abrem.
assert.match(html, /transparent 520px,transparent 1400px/);
assert.match(html, /transparent 112px,transparent 656px/);
assert.equal(fs.readFileSync(path.join(root, 'src/shared-runtime.js'), 'utf8'), renderSharedRuntimeScript(root),
    'src/shared-runtime.js is stale; run npm run build');
assert.equal(html, expectedHtml, 'dist/app.html is stale; run npm run build');
assert.equal(fs.readFileSync(path.join(published, 'app.html'), 'utf8'), expectedHtml,
    'published app.html is stale; run npm run build');
assert.equal(fs.readFileSync(path.join(published, 'theme.xml'), 'utf8'), manifest,
    'published theme.xml is stale; run npm run build');
assert.deepEqual(fs.readFileSync(path.join(published, 'thumbnail.png')), fs.readFileSync(path.join(root, 'thumbnail.png')),
    'published thumbnail.png is stale; run npm run build');
const ids = [...source.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);
assert.equal(ids.length, new Set(ids).size, 'duplicate DOM IDs');
assert.ok(Buffer.byteLength(html) < 2000000, 'bundle unexpectedly large');
console.log('PASS ApexGT offline bundle, manifest, source/package parity and size budget.');
