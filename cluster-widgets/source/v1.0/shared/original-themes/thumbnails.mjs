// Original vector catalog icons, NOT dashboard screenshots or runtime-validation evidence.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { collection, sourceRoot } from './build.mjs';
const require = createRequire(import.meta.url);
const sharp = require('sharp');
const marks = {
    Horizonte: ['#15292e', '#98d6cc', '<path d="M35 94H165M47 78H153M63 62H137M82 46H118"/>'],
    Pulso: ['#21191c', '#ff855c', '<path d="M36 96L66 45H98L70 96M96 96L124 45H156L127 96"/>'],
    Orbita: ['#1b2030', '#aec1f5', '<ellipse cx="100" cy="71" rx="58" ry="30" transform="rotate(-28 100 71)"/><circle cx="100" cy="71" r="20"/><circle cx="151" cy="49" r="6" fill="#aec1f5"/>'],
    Sereno: ['#e9eee9', '#48695c', '<path d="M53 94V48H78M100 94V48H125M147 94V48"/>'],
    Eclipse: ['#161512', '#dac9a5', '<path d="M114 35A36 36 0 1 0 136 96A42 42 0 0 1 114 35Z"/>']
};
for (const name of collection) {
    const [background, accent, mark] = marks[name];
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200"><rect width="200" height="200" rx="14" fill="${background}"/><g stroke="${accent}" stroke-width="3" fill="none" stroke-linecap="round" stroke-linejoin="round">${mark}</g><text x="100" y="143" text-anchor="middle" font-family="sans-serif" font-size="20" fill="${accent}">${name === 'Orbita' ? 'Órbita' : name}</text><text x="100" y="172" text-anchor="middle" font-family="sans-serif" font-size="9" letter-spacing="2" fill="${accent}">TEMA ORIGINAL</text></svg>\n`;
    const directory = path.join(sourceRoot, name);
    fs.writeFileSync(path.join(directory, 'thumbnail.svg'), svg);
    await sharp(Buffer.from(svg)).png().toFile(path.join(directory, 'thumbnail.png'));
}
console.log('Five original vector catalog icons generated; these are not screenshots.');
