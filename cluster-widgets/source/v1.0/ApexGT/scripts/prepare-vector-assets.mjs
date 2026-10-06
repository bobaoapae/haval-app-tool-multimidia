// Deterministic build-time packaging of the imagegen-edited Concept 03.
// No raster transformations or dependencies from this script run in the car.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const sharp = require('sharp');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'assets');
const reference = path.join(root, 'reference');
fs.mkdirSync(assets, { recursive: true });
fs.mkdirSync(reference, { recursive: true });
if (process.argv[2]) fs.copyFileSync(process.argv[2], path.join(assets, 'vector-frame-master.png'));
if (process.argv[3]) fs.copyFileSync(process.argv[3], path.join(reference, 'concept-03.png'));

const { data, info } = await sharp(path.join(assets, 'vector-frame-master.png'))
    .resize(1920, 720).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
const source = Buffer.from(data);

function sampleX(pixels, x, y, channel) {
    const start = Math.floor(x);
    const fraction = x - start;
    return Math.round(pixels[(y * info.width + start) * 4 + channel] * (1 - fraction)
        + pixels[(y * info.width + Math.min(info.width - 1, start + 1)) * 4 + channel] * fraction);
}

// The metal bands stay on the normalized prototype's pixel coordinates.
// Compress only the inner glass to the conservative 520px native side panels.
for (let y = 110; y < 550; y++) {
    const weight = Math.min(1, (y - 110) / 20, (550 - y) / 25);
    for (let x = 400; x < 520; x++) {
        const sourceX = x + (x - 400) * 70 / 120 * weight;
        for (let channel = 0; channel < 4; channel++) {
            data[(y * info.width + x) * 4 + channel] = sampleX(source, sourceX, y, channel);
            data[(y * info.width + 1919 - x) * 4 + channel] = sampleX(source, 1919 - sourceX, y, channel);
        }
    }
}

const glass = Buffer.from(data);
// The source's central footer occupies approximately y=560..640. Move that
// recess into y=650..720, with a continuous transition above it and across the
// shoulders. This preserves the complete lower metal trim instead of cropping
// it at the mandatory transparent center boundary.
for (let x = 0; x < 1920; x++) {
    const weight = Math.max(0, Math.min(1, (x - 350) / 170, (1570 - x) / 170));
    // Broaden the blank central recess for the four mandatory information
    // groups. The angular shoulders must not run underneath Trip/ODO text.
    const footerX = x < 350 || x > 1570 ? x
        : x < 520 ? 350 + (x - 350) * 310 / 170
        : x <= 1400 ? 660 + (x - 520) * 600 / 880
            : 1260 + (x - 1400) * 310 / 170;
    for (let y = 530; y < 720; y++) {
        const remapped = y <= 650 ? 530 + (y - 530) * 30 / 120 : 560 + (y - 650) * 80 / 70;
        // Give each side's energy panel room below the OEM sign (ends y=565).
        // Its original lower trim at ~635 lands at 675, while the center keeps
        // the separate native-clearance mapping above. Gauge bands y<530 stay
        // untouched; the shoulders interpolate smoothly between both recesses.
        const sideY = y <= 675 ? 530 + (y - 530) * 105 / 145 : 635 + (y - 675) * 85 / 45;
        const sourceY = sideY + (remapped - sideY) * weight;
        const start = Math.floor(sourceY);
        const fraction = sourceY - start;
        const sourceX = x + (footerX - x) * Math.min(1, (y - 530) / 120);
        for (let channel = 0; channel < 4; channel++) {
            data[(y * info.width + x) * 4 + channel] = Math.round(
                sampleX(glass, sourceX, start, channel) * (1 - fraction)
                + sampleX(glass, sourceX, Math.min(719, start + 1), channel) * fraction);
        }
    }
}

// READY, traffic-sign recognition and lane assistance are native foreground
// overlays. Keeping opaque theme material below them prevents lower chrome
// from bleeding through while the vehicle remains free to draw the icons.
const clearZones = [[520, 120, 880, 530]];
for (const [x, y, width, height] of clearZones) {
    for (let row = y; row < y + height; row++) {
        for (let col = x; col < x + width; col++) {
            const offset = (row * info.width + col) * 4;
            data.fill(0, offset, offset + 4);
        }
    }
}

await sharp(data, { raw: info }).png().toFile(path.join(assets, 'vector-frame.png'));
await sharp(data, { raw: info }).webp({ lossless: true, effort: 6 }).toFile(path.join(assets, 'vector-frame.webp'));
console.log('Vector: 1920x720 lossless PNG/WebP; metal coordinates preserved; 466400 central pixels alpha=0.');
