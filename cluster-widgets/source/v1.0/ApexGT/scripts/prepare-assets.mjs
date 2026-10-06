// Build-time raster processing only; never runs in the WebView.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import '../src/gauge-geometry.js';
const require = createRequire(import.meta.url);
const sharp = require('sharp');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'assets');
const { data, info } = await sharp(path.join(assets, 'apex-gt-frame-refined-master.png'))
    .resize(1920, 720).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
// Keep the prototype's metallic gauge bezels pixel-aligned. Only the INNER
// glass edges are compressed into the fixed host side areas; the reference's
// original glass would otherwise cover the protected ADAS rectangle.
const masterPixels = Buffer.from(data);
for (let row = 110; row < 570; row++) {
    const weight = Math.min(1, (row - 110) / 20, (570 - row) / 25);
    for (let col = 400; col < 520; col++) {
        const sourceX = col + (col - 400) * 55 / 120 * weight;
        const x0 = Math.floor(sourceX), mix = sourceX - x0;
        for (let channel = 0; channel < 4; channel++) {
            data[(row * info.width + col) * 4 + channel] = Math.round(
                masterPixels[(row * info.width + x0) * 4 + channel] * (1 - mix) +
                masterPixels[(row * info.width + x0 + 1) * 4 + channel] * mix);
            data[(row * info.width + 1919 - col) * 4 + channel] = Math.round(
                masterPixels[(row * info.width + 1919 - x0) * 4 + channel] * (1 - mix) +
                masterPixels[(row * info.width + 1918 - x0) * 4 + channel] * mix);
        }
    }
}
// Keep the centre footer and its two diagonals at the master's original
// vertical scale. Stretching this strip made the metallic edge beside the
// trip figures look wider and longer than the prototype.
// Redistribute the side instruments into the lower unused space. Sample the
// unmasked artwork so the central host exclusion is applied after geometry;
// the shared calibration also positions scales/needles.
const panelPixels = Buffer.from(data);
const geometry = globalThis.ApexGaugeGeometry;
// Keep the lower perimeter below the energy readouts instead of compressing
// its bright edge through them. Converge to the master geometry at x=520.
const footerEdge = [[0,653],[60,664],[100,671],[150,676],[200,680],[250,684],
    [300,688],[350,694],[400,700],[450,711],[480,719],[520,720]];
function edgeY(x) {
    const index = footerEdge.findIndex(point => point[0] >= x);
    if (index <= 0) return footerEdge[0][1];
    const [x0,y0] = footerEdge[index - 1], [x1,y1] = footerEdge[index];
    return y0 + (y1-y0) * (x-x0) / (x1-x0);
}
const sideColumns = Array.from({length:520}, (_, x) => {
    const t = Math.min(1, (520-x)/90), weight = t*t*(3-2*t);
    const originalEdge = edgeY(x);
    return {weight, base:570+54*weight, originalEdge,
        targetEdge:originalEdge+(718-originalEdge)*weight};
});
for (let row = 110; row < 720; row++) {
    for (let col = 0; col < 1920; col++) {
        if (col >= 520 && col < 1400) continue;
        const sideX = Math.min(col, 1919-col);
        const column = sideColumns[sideX];
        let sourceY;
        if (row <= 300) sourceY = row;
        else if (row <= column.base) {
            sourceY = column.weight === 1 ? geometry.inverseY(row)
                : 300 + (row-300)*270/(column.base-300);
        } else if (row <= column.targetEdge) {
            sourceY = 570 + (row-column.base)*(column.originalEdge-570)/(column.targetEdge-column.base);
        } else {
            sourceY = column.originalEdge + (row-column.targetEdge)*(720-column.originalEdge)/(720-column.targetEdge);
        }
        const y0 = Math.floor(sourceY), y1 = Math.min(719, y0 + 1), mix = sourceY - y0;
        for (let channel = 0; channel < 4; channel++) {
            data[(row * info.width + col) * 4 + channel] = Math.round(
                panelPixels[(y0 * info.width + col) * 4 + channel] * (1 - mix) +
                panelPixels[(y1 * info.width + col) * 4 + channel] * mix);
        }
    }
}
// Translate the untouched master footer below the trip figures without
// resampling it. The master already contains the clean diagonal junctions;
// moving the whole strip uniformly avoids adding a second curve at its edges.
for (let col = 520; col < 1400; col++) {
    const shift = 43;
    for (let row = 650; row < 720; row++) {
        const sourceY = row - shift;
        const y0 = Math.floor(sourceY), y1 = Math.min(719, y0 + 1), mix = sourceY - y0;
        for (let channel = 0; channel < 4; channel++) {
            data[(row * info.width + col) * 4 + channel] = Math.round(
                panelPixels[(y0 * info.width + col) * 4 + channel] * (1 - mix) +
                panelPixels[(y1 * info.width + col) * 4 + channel] * mix);
        }
    }
}
// Generated glows may retain faint alpha. Enforce the exact central host
// exclusion after resizing, including every pixel on its viewport boundary.
const clearZones = [[520, 120, 880, 530]];
for (const [x, y, w, h] of clearZones) {
    for (let row = y; row < y + h; row++) {
        for (let col = x; col < x + w; col++) {
            const offset = (row * info.width + col) * 4;
            data.fill(0, offset, offset + 4);
        }
    }
}
await sharp(data, { raw: info }).png().toFile(path.join(assets, 'apex-gt-frame.png'));
await sharp(data, { raw: info }).webp({ lossless: true, effort: 6 }).toFile(path.join(assets, 'apex-gt-frame.webp'));
// Reuse existing offline OEM-style fonts; avoid copying unused weight faces.
const fonts = fs.readFileSync(path.join(root, '../shared/assets/fonts/fonts.css'), 'utf8');
const faces = fonts.match(/@font-face\s*\{[^}]+\}/g) || [];
const selected = faces.filter(face => /Eurostile/.test(face) || /font-weight:\s*(400|500|600);/.test(face));
if (selected.length !== 4) throw new Error('Expected the existing Khand 400/500/600 and Eurostile faces');
fs.writeFileSync(path.join(assets, 'fonts.css'), selected.join('\n') + '\n');
console.log('Prepared 1920x720 RGBA/lossless WebP; central exclusion pixels alpha=0; four offline fonts.');
