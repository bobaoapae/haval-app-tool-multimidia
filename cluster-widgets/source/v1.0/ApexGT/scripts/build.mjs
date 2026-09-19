import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderBundle } from './bundle.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const published = path.resolve(root, '../../../Themes/v1.0/ApexGT');
const html = renderBundle(root);

for (const directory of [path.join(root, 'dist'), published]) {
    fs.mkdirSync(directory, { recursive: true });
    fs.writeFileSync(path.join(directory, 'app.html'), html);
}
for (const file of ['theme.xml', 'thumbnail.png']) {
    if (!fs.existsSync(path.join(root, file))) throw new Error(`Missing ${file}`);
    fs.copyFileSync(path.join(root, file), path.join(published, file));
}
const { version } = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
console.log(`Apex GT ${version}: ${Buffer.byteLength(html)} bytes, self-contained.\n${published}`);
