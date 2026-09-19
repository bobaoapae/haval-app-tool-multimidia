// Real Chromium + raster QA. npm install --save-dev playwright sharp, or use NODE_PATH.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
import { renderBundle } from './bundle.mjs';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const sharp = require('sharp');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
// Keep captures outside Vite's source tree so QA never triggers hot reload.
const out = process.env.APEX_EVIDENCE_DIR || path.resolve(root, '../../../../tools/headunit-dev/output/apex-gt-validation-20260919');
fs.mkdirSync(out, { recursive: true });
const origin = process.env.APEX_LAB_URL || 'http://localhost:1234';
const executablePath = process.env.APEX_CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const packagedApp = path.resolve(root, '../../../Themes/v1.0/ApexGT/app.html');
assert.equal(fs.readFileSync(path.join(root, 'dist/app.html'), 'utf8'), renderBundle(root),
    'dist/app.html is stale; run npm run build before browser QA');
assert.equal(fs.readFileSync(packagedApp, 'utf8'), renderBundle(root),
    'published app.html is stale; run npm run build before browser QA');
const browser = await chromium.launch({ executablePath, headless: true });
const context = await browser.newContext({ viewport: { width: 1920, height: 720 }, deviceScaleFactor: 1, recordVideo: { dir: path.join(out, 'video'), size: { width: 1920, height: 720 } } });
const page = await context.newPage();
const errors = [];
page.on('pageerror', error => errors.push(error.message));
const data = {
    'car.basic.vehicle_speed': 72, 'car.basic.engine_speed': 1800,
    'car.basic.gear_status': 2, 'car.drive_setting.drive_mode': 1,
    'car.ev_setting.power_model_config': 0, 'car.basic.inside_temp': 22,
    'car.basic.outside_temp': 24, 'car.configure.default_temp_unit': 0,
    'car.basic.total_odometer': 18654, 'car.basic.remain_fuel_percentage': 68,
    'car.ev_info.cur_battery_power_percentage': 74,
    'car.ev_info.fuel_mode_remain_odometer': 455,
    'car.ev_info.electric_mode_remain_odometer': 84,
    'car.basic.cur_journey_odometer': 1656.7,
    'car.basic.cur_journey_avg_fuel_consume': 100 / 16.9,
    'car.ev_info.power_battery_voltage': 400, 'car.ev_info.cur_charge_current': 240,
};
const transparentZones = [[520, 120, 880, 530]];
const oemForegroundZones = [[238, 315, 104, 60], [225, 475, 90, 90], [330, 445, 100, 100]];
const reservedZones = [...transparentZones, ...oemForegroundZones];
const transparentPixelCount = transparentZones.reduce((total, zone) => total + zone[2] * zone[3], 0);
async function send(values) {
    await page.evaluate(values => Object.entries(values).forEach(([k, v]) => window.onDataChanged(k, v)), values);
}
async function screenshot(name) {
    const output = path.join(out, name + '.png');
    await page.screenshot({ path: output, omitBackground: true });
    return output;
}
async function assertAlpha(file, zones = transparentZones) {
    const { data: pixels, info } = await sharp(file).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    let checked = 0;
    for (const [x, y, w, h] of zones) {
        for (let row = y; row < y + h; row++) for (let col = x; col < x + w; col++) {
            assert.equal(pixels[(row * info.width + col) * info.channels + 3], 0, `${path.basename(file)} alpha at ${col},${row}`);
            checked++;
        }
    }
    return checked;
}
async function assertOpaque(file, zones = oemForegroundZones) {
    const { data: pixels, info } = await sharp(file).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    for (const [x, y, w, h] of zones) {
        for (let row = y; row < y + h; row++) for (let col = x; col < x + w; col++) {
            assert.equal(pixels[(row * info.width + col) * info.channels + 3], 255,
                `${path.basename(file)} opaque OEM backing at ${col},${row}`);
        }
    }
}
const report = { viewport: '1920x720', demoData: 'simulator only, injected by test', checks: [], alphaPixelsPerState: 0 };
try {
    // Keep a source capture as evidence without modifying the checked-in thumbnail or package.
    await page.goto(origin + '/source/v1.0/ApexGT/index.html');
    await page.evaluate(() => document.fonts.ready);
    await page.waitForFunction(() => document.getElementById('gauge-motion').dataset.material === 'ready');
    await send(data);
    await page.waitForTimeout(550);
    await screenshot('apex-gt-source');
    await page.waitForTimeout(700);
    await context.setOffline(true);
    await page.goto(pathToFileURL(packagedApp).href);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForFunction(() => document.getElementById('gauge-motion').dataset.material === 'ready');
    assert.equal(await page.locator('#speed-value').textContent(), '--');
    await send(data);
    await page.waitForFunction(() => {
        const gauge = document.getElementById('gauge-motion').dataset;
        return gauge.running === 'false' && gauge.speed === '72' && gauge.power === '96';
    }, null, { timeout: 1200 });
    const driving = await screenshot('apex-gt-driving');
    report.alphaPixelsPerState = await assertAlpha(driving);
    await assertOpaque(driving);
    // A checkerboard belongs only to this evidence capture, never the package.
    await page.evaluate(() => document.documentElement.style.setProperty('background', 'repeating-conic-gradient(#192530 0% 25%,#283a46 0% 50%) 0 0 / 32px 32px', 'important'));
    await screenshot('apex-gt-transparency-checker');
    await page.evaluate(() => document.documentElement.style.removeProperty('background'));
    await assertAlpha(path.join(root, 'assets/apex-gt-frame.png'));
    assert.equal(report.alphaPixelsPerState, transparentPixelCount,
        'full composition keeps only the central native viewport transparent');
    assert.equal(await page.locator('#speed-value').textContent(), '72');
    assert.equal(await page.locator('#power-value').textContent(), '96');
    assert.equal(await page.locator('#regen-value').textContent(), '0');
    assert.equal(await page.locator('#total-range').textContent(), '539');
    report.checks.push('offline bundled artwork/fonts/data hooks; signed96kW; derived539km');
    const layout = await page.evaluate(() => {
        const box = selector => {
            const bounds = document.querySelector(selector).getBoundingClientRect();
            return { left: bounds.left, top: bounds.top, right: bounds.right, bottom: bounds.bottom };
        };
        return {
            signatureCount: document.querySelectorAll('.signature').length,
            propulsionLabelCount: document.querySelectorAll('.propulsion small').length,
            propulsionText: document.querySelector('.propulsion').textContent.trim(),
            inside: box('.temperature-inside'), outside: box('.temperature-outside'),
            propulsion: box('.propulsion'), odometer: box('.odometer'),
            traction: box('.right-mode-guide'), clock: box('#clock'),
            fuelDetail: box('.energy-fuel .energy-detail'),
            fuelRange: box('.energy-fuel .energy-range'), batteryRange: box('.energy-battery .energy-range'),
            fuelBar: box('.energy-fuel .energy-track'), batteryBar: box('.energy-battery .energy-track')
        };
    });
    assert.equal(layout.signatureCount, 0, 'legacy Apex GT signature removed');
    assert.equal(layout.propulsionLabelCount, 0, 'propulsion label removed');
    assert.equal(layout.propulsionText, 'HEV', 'only propulsion value remains');
    assert.ok(layout.inside.left >= 35 && layout.inside.bottom <= 60);
    assert.ok(layout.outside.left >= 200 && layout.outside.right < 370 && layout.outside.bottom <= 60);
    assert.ok(layout.clock.left >= 700 && layout.clock.right <= 835 && layout.clock.top >= 50 && layout.clock.bottom <= 92, 'clock enlarged below the header trim, left of gear');
    assert.ok(layout.propulsion.left >= 1070 && layout.propulsion.right <= 1235 && layout.propulsion.top >= 50 && layout.propulsion.bottom <= 92, 'propulsion enlarged below the header trim, right of gear');
    assert.ok(layout.clock.right < 850 && layout.propulsion.left > 1070, 'header values stay clear of the central gear');
    assert.ok(layout.odometer.left >= 1625 && layout.odometer.right <= 1885 && layout.odometer.top <= 25 && layout.odometer.bottom <= 60, 'odometer occupies former upper-right clock area');
    for (const range of [layout.fuelRange, layout.batteryRange]) assert.ok(range.top >= 675 && range.bottom <= 718, 'large range stays inside the footer');
    for (const bar of [layout.fuelBar, layout.batteryBar]) assert.ok(bar.top >= 680 && bar.bottom <= 714, 'larger energy bar stays inside the lower space');
    const scaleLabels = await page.evaluate(() => Object.fromEntries(['.speed-scale-labels', '.power-scale-labels'].map(group => {
        const labels = Array.from(document.querySelectorAll(group + ' text')).map(node => {
            const box = node.getBoundingClientRect();
            return { text: node.textContent, left: box.left, top: box.top, right: box.right, bottom: box.bottom };
        });
        const vertical = labels.toSorted((first, second) => first.top - second.top);
        const gaps = vertical.slice(1).flatMap((second, index) => {
            const first = vertical[index];
            return first.left < second.right && first.right > second.left
                ? [{ pair: `${first.text}/${second.text}`, gap: second.top - first.bottom }] : [];
        });
        return [group, { texts: labels.map(label => label.text), gaps }];
    })));
    assert.deepEqual(scaleLabels['.speed-scale-labels'].texts, ['0', '10', '20', '30', '40', '50', '60', '80', '100', '140', '180']);
    assert.deepEqual(scaleLabels['.power-scale-labels'].texts, ['−100', '−50', '−30', '−20', '−10', '0', '10', '20', '30', '50', '100']);
    for (const [group, measurement] of Object.entries(scaleLabels)) {
        assert.ok(measurement.gaps.every(({ gap }) => gap >= 5), `${group} keeps at least 5px between adjacent labels: ${JSON.stringify(measurement.gaps)}`);
    }
    report.scaleLabels = scaleLabels;
    const headerTrimClearance = await page.evaluate(() => {
        const material = document.getElementById('gauge-material');
        const canvas = document.createElement('canvas');
        canvas.width = 1920; canvas.height = 110;
        const context = canvas.getContext('2d', { willReadFrequently: true });
        context.drawImage(material, 0, 0);
        const pixels = context.getImageData(0, 0, 1920, 110).data;
        const textBounds = selector => {
            const range = document.createRange();
            range.selectNodeContents(document.querySelector(selector));
            const bounds = range.getBoundingClientRect();
            return { left: bounds.left, right: bounds.right, top: bounds.top, bottom: bounds.bottom };
        };
        return Object.fromEntries([['clock', '#clock'], ['propulsion', '#propulsion-mode']].map(([name, selector]) => {
            const bounds = textBounds(selector);
            let lastBrightY = -1;
            for (let y = 0; y < Math.floor(bounds.top); y++) for (let x = Math.floor(bounds.left) - 2; x <= Math.ceil(bounds.right) + 2; x++) {
                const index = (y * 1920 + x) * 4;
                if (pixels[index + 3] > 32 && pixels[index] + pixels[index + 1] + pixels[index + 2] > 420) lastBrightY = y;
            }
            return [name, { ...bounds, lastBrightY, clearance: bounds.top - lastBrightY }];
        }));
    });
    assert.ok(headerTrimClearance.clock.clearance >= 8, `clock clears luminous header trim: ${JSON.stringify(headerTrimClearance.clock)}`);
    assert.ok(headerTrimClearance.propulsion.clearance >= 8, `propulsion clears luminous header trim: ${JSON.stringify(headerTrimClearance.propulsion)}`);
    report.headerTrimClearance = headerTrimClearance;
    report.headerLayout = layout;
    report.checks.push('clock and propulsion clear the luminous header trim; odometer remains upper-right; enlarged energy values remain inside footer');
    await send({
        'car.basic.inside_temp': -40, 'car.basic.outside_temp': -40,
        'car.configure.default_temp_unit': 1, 'car.ev_setting.power_model_config': 1
    });
    await page.waitForTimeout(60);
    assert.equal(await page.locator('#inside-temp').textContent(), '-40°F');
    assert.equal(await page.locator('#outside-temp').textContent(), '-40°F');
    assert.equal(await page.locator('#propulsion-mode').textContent(), 'EVP');
    const extremeHeader = await page.evaluate(() => {
        const bounds = selector => document.querySelector(selector).getBoundingClientRect();
        const inside = bounds('.temperature-inside'), outside = bounds('.temperature-outside');
        return { insideRight: inside.right, outsideRight: outside.right };
    });
    assert.ok(extremeHeader.insideRight < 190 && extremeHeader.outsideRight < 370);
    await screenshot('apex-gt-header-extremes');
    await send({
        'car.basic.inside_temp': data['car.basic.inside_temp'],
        'car.basic.outside_temp': data['car.basic.outside_temp'],
        'car.configure.default_temp_unit': data['car.configure.default_temp_unit'],
        'car.ev_setting.power_model_config': data['car.ev_setting.power_model_config']
    });
    await page.waitForTimeout(60);
    report.checks.push('header remains separated with -40°F / -40°F and EVP');
    const pngPixels = await sharp(path.join(root, 'assets/apex-gt-frame.png')).ensureAlpha().raw().toBuffer();
    const webpPixels = await sharp(path.join(root, 'assets/apex-gt-frame.webp')).ensureAlpha().raw().toBuffer();
    let alphaMismatches = 0, visibleRgbMismatches = 0;
    for (let index = 0; index < pngPixels.length; index += 4) {
        if (pngPixels[index + 3] !== webpPixels[index + 3]) alphaMismatches++;
        // WebP can discard invisible RGB under alpha=0 without changing the
        // rendered image. Every visible channel and every alpha must be exact.
        if (pngPixels[index + 3]) for (let channel = 0; channel < 3; channel++) {
            if (pngPixels[index + channel] !== webpPixels[index + channel]) visibleRgbMismatches++;
        }
    }
    assert.equal(alphaMismatches, 0, 'lossless WebP alpha');
    assert.equal(visibleRgbMismatches, 0, 'lossless WebP visible RGB');
    report.losslessFrame = { alphaMismatches, visibleRgbMismatches };
    report.checks.push('lossless visible pixels and alpha; cached groove material ready online and offline');

    await send({ 'car.basic.vehicle_speed': 150, 'car.ev_info.cur_charge_current': 380 });
    await page.waitForTimeout(65);
    const midSpeed = Number(await page.locator('#gauge-motion').getAttribute('data-speed'));
    assert.ok(midSpeed > 72 && midSpeed < 150, `intermediate animation ${midSpeed}`);
    await screenshot('apex-gt-motion-intermediate');
    await page.waitForFunction(() => {
        const gauge = document.getElementById('gauge-motion').dataset;
        return gauge.running === 'false' && gauge.speed === '150' && gauge.power === '100';
    }, null, { timeout: 1200 });
    assert.equal(await page.locator('#gauge-motion').getAttribute('data-running'), 'false');
    const paints = await page.locator('#gauge-motion').getAttribute('data-paints');
    await send({ 'car.basic.vehicle_speed': 150, 'car.ev_info.cur_charge_current': 380 });
    await page.waitForTimeout(120);
    assert.equal(await page.locator('#gauge-motion').getAttribute('data-paints'), paints);
    report.checks.push('intermediate speed frame; settled RAF; repeated telemetry no repaint');

    await send({ 'car.basic.vehicle_speed': 58, 'car.ev_info.cur_charge_current': -85 });
    await page.waitForTimeout(350);
    assert.equal(await page.locator('#regen-value').textContent(), '-34');
    assert.equal(await page.locator('#power-value').textContent(), '0');
    await assertAlpha(await screenshot('apex-gt-regeneration'));
    await send({ projectionPreparingD3: true });
    await page.waitForTimeout(50);
    await assertAlpha(await screenshot('apex-gt-projection-preparing'));
    await send({ carPlayInDash: true, projectionPreparingD3: false });
    await page.waitForTimeout(50);
    await assertAlpha(await screenshot('apex-gt-projection-active'));
    await send({ carPlayInDash: false });
    await page.waitForTimeout(50);
    await assertAlpha(await screenshot('apex-gt-projection-exit'));
    report.checks.push('regeneration -34kW; transparent normal/preparing/active/exit');

    await send({ 'car.basic.vehicle_speed': 0, 'car.basic.engine_speed': 0, 'car.ev_info.cur_charge_current': 0, 'car.basic.remain_fuel_percentage': 0, 'car.ev_info.cur_battery_power_percentage': 0 });
    await page.waitForTimeout(350);
    assert.equal(await page.locator('#speed-value').textContent(), '0');
    assert.equal(await page.locator('#power-value').textContent(), '0');
    assert.equal(await page.locator('#rpm-readout').isVisible(), false);
    await assertAlpha(await screenshot('apex-gt-zero'));
    await send({ 'car.ev_info.power_battery_voltage': null, 'car.basic.vehicle_speed': null, 'car.basic.remain_fuel_percentage': null });
    await page.waitForTimeout(60);
    assert.equal(await page.locator('#power-value').textContent(), '--');
    assert.equal(await page.locator('#regen-value').textContent(), '--');
    assert.equal(await page.locator('#speed-value').textContent(), '--');
    await assertAlpha(await screenshot('apex-gt-missing'));
    report.checks.push('real zeros distinct from missing; stale power cleared; RPM hidden');

    // Fixed screen anchors make this an independent calibration check instead
    // of comparing the renderer only with the geometry function it already uses.
    // Reduced motion settles each anchor synchronously; timing behavior is
    // exercised separately above and in check-motion.mjs.
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const speedAnchors = [[0, 554.4], [20, 459], [40, 379], [60, 315], [80, 263],
        [100, 221], [120, 189], [140, 165], [160, 147], [180, 132]];
    for (const [speed, expectedY] of speedAnchors) {
        await send({ ...data, 'car.basic.vehicle_speed': speed, 'car.ev_info.cur_charge_current': 0 });
        await page.waitForTimeout(350);
        const positions = await page.evaluate(() => {
            const canvas = document.getElementById('gauge-motion');
            return { speed: Number(canvas.dataset.speedY), needle: Number(canvas.dataset.needleY) };
        });
        assert.ok(Math.abs(positions.speed - expectedY) < 0.01, `${speed}km/h fixed anchor`);
        assert.ok(Math.abs(positions.needle - 336) < 0.01, '0kW fixed anchor');
        await assertAlpha(await screenshot(`apex-gt-sweep-${speed}`));
    }
    const powerAnchors = [[-100, 543.6], [-75, 522], [-50, 495], [-40, 470], [-30, 442],
        [-20, 410], [-10, 375], [0, 336], [10, 300], [20, 268], [30, 240],
        [40, 216], [50, 195], [75, 172], [100, 153]];
    for (const [power, expectedY] of powerAnchors) {
        await send({ ...data, 'car.basic.vehicle_speed': 40, 'car.ev_info.cur_charge_current': power * 2.5 });
        await page.waitForTimeout(250);
        const needleY = Number(await page.locator('#gauge-motion').getAttribute('data-needle-y'));
        assert.ok(Math.abs(needleY - expectedY) < 0.01, `${power}kW fixed anchor`);
    }
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    report.checks.push('fixed progressive anchors cover every speed node and every traction/regeneration node; center alpha remains zero');

    // No primary data may overlap the conservative ADAS or immutable OEM zones.
    await send({ ...data, 'car.basic.vehicle_speed': 180, 'car.basic.total_odometer': 999999, 'car.basic.cur_journey_odometer': 99999.9 });
    await page.waitForTimeout(350);
    const collisions = await page.evaluate(zones => {
        const ids = ['speed-value','gear-value','drive-mode','propulsion-mode','outside-temp','inside-temp','clock','odometer','engine-rpm','power-value','regen-value','fuel-percent','fuel-liters','fuel-range','battery-percent','battery-range','trip-distance','average-consumption','total-range'];
        return ids.flatMap(id => {
            const b = document.getElementById(id).getBoundingClientRect();
            return zones.some(([x,y,w,h]) => b.width && b.height && b.left < x+w && b.right > x && b.top < y+h && b.bottom > y) ? [id] : [];
        });
    }, reservedZones);
    assert.deepEqual(collisions, []);
    await assertAlpha(await screenshot('apex-gt-limits'));
    await send({ 'car.basic.vehicle_speed': 20 });
    await page.waitForTimeout(40);
    await page.evaluate(() => window.cleanup());
    const cleanupPaints = await page.locator('#gauge-motion').getAttribute('data-paints');
    await page.waitForTimeout(300);
    assert.equal(await page.locator('#gauge-motion').getAttribute('data-paints'), cleanupPaints);
    assert.equal(await page.locator('#gauge-motion').getAttribute('data-running'), 'false');
    assert.deepEqual(errors, []);
    report.checks.push('long values clear exclusion zones; cleanup stops motion; no JS errors');
    report.status = 'PASS';
} finally {
    await context.close();
    const video = await page.video()?.path();
    if (video) { fs.copyFileSync(video, path.join(out, 'apex-gt-animation.webm')); report.video = 'apex-gt-animation.webm'; }
    await browser.close();
    fs.writeFileSync(path.join(out, 'browser-report.json'), JSON.stringify(report, null, 2) + '\n');
}
console.log(JSON.stringify(report, null, 2));
