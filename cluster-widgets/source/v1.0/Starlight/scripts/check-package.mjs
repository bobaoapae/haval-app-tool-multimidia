import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const published = path.resolve(root, '../../../Themes/v1.0/Starlight');
const html = fs.readFileSync(path.join(published, 'app.html'), 'utf8');
const manifest = fs.readFileSync(path.join(root, 'theme.xml'), 'utf8');
const { version } = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
assert.ok(manifest.includes(`<version>${version}</version>`));
assert.ok(manifest.includes('<contractVersion>v1.0</contractVersion>'));
assert.ok(manifest.includes('<minBridgeVersion>1.0.1</minBridgeVersion>'));
assert.ok(!manifest.includes('NativeMasks'));
for (const [first, second] of [['dist/app.html', 'app.html'], ['theme.xml', 'theme.xml'], ['thumbnail.png', 'thumbnail.png']]) {
    assert.deepEqual(fs.readFileSync(path.join(root, first)), fs.readFileSync(path.join(published, second)));
}
assert.ok(html.includes('data:font/ttf;base64,'), 'Fonts must be offline');
assert.ok(!/<script\b[^>]*\bsrc=|\b(?:src|href)=["'](?:\.\/|\.\.\/|https?:)/i.test(html));
const css = [...html.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/gi)].map((match) => match[1]).join('\n');
assert.ok([...css.matchAll(/url\(\s*["']?([^)'"\s]+)["']?\s*\)/gi)]
    .every((match) => match[1].startsWith('data:')));
const workerBootstrap = html.match(/<script>window\.StarlightWorkerSource=([\s\S]*?);<\/script>/);
assert.ok(workerBootstrap, 'Worker code must be embedded in the flat package');
const workerCode = JSON.parse(workerBootstrap[1].replace(/<\\\/script/gi, '</script'));
assert.ok(!/\bimportScripts\s*\(/.test(workerCode), 'Worker must not fetch external source files');
new vm.Script(workerCode);
assert.ok(!/preview-controller|contract-lab\.js|dynamicTelemetry|demoSeed/.test(html), 'No study controller in production');
assert.ok(!/Object\.hasOwn\s*\(|\.at\s*\(/.test(html), 'Unsupported runtime API');
for (const [, code] of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)) new vm.Script(code);
assert.ok(Buffer.byteLength(html) < 2_500_000, 'Unexpected package growth');
const files = fs.readdirSync(published);
assert.ok(files.filter((file) => file.startsWith('LICENSE-')).length >= 5);
assert.ok(files.every((file) => !file.startsWith('.') && fs.statSync(path.join(published, file)).isFile()), 'Host requires flat packages without dotfiles');
console.log(`Starlight package PASS: ${Buffer.byteLength(html)} bytes, source/OTA parity, offline fonts/licenses.`);
