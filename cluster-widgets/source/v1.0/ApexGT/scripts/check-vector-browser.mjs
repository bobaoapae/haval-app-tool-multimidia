// Real Chromium QA for the Vector display and theme-scoped display preference.
// Vehicle and preference snapshots below are test data; no hardware is contacted.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const sharp = require('sharp');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const out = process.env.APEX_EVIDENCE_DIR || path.resolve(root, '../../../../tools/headunit-dev/output/apex-gt-displays-20260919');
const origin = process.env.APEX_LAB_URL || 'http://localhost:1234';
const preference = 'app.preferences.apexDisplayMode';
const storageKey = 'haval.ApexGT.apexDisplayMode';
const transparentZones = [[520, 120, 880, 530]];
const oemForegroundZones = [[238, 315, 104, 60], [225, 475, 90, 90], [330, 445, 100, 100]];
const reservedZones = [...transparentZones, ...oemForegroundZones];
const transparentPixelCount = transparentZones.reduce((total, zone) => total + zone[2] * zone[3], 0);
const primaryIds = ['speed-value', 'gear-value', 'drive-mode', 'propulsion-mode', 'outside-temp', 'inside-temp',
    'clock', 'odometer', 'engine-rpm', 'power-value', 'regen-value', 'vector-power-value', 'fuel-percent', 'fuel-liters', 'fuel-range',
    'battery-percent', 'battery-range', 'trip-distance', 'average-consumption', 'total-range'];
const snapshot = {
    'car.basic.vehicle_speed': 80, 'car.basic.engine_speed': 1800,
    'car.basic.gear_status': 2, 'car.drive_setting.drive_mode': 1,
    'car.ev_setting.power_model_config': 0, 'car.basic.inside_temp': 22,
    'car.basic.outside_temp': 24, 'car.configure.default_temp_unit': 0,
    'car.basic.total_odometer': 18654, 'car.basic.remain_fuel_percentage': 68,
    'car.ev_info.cur_battery_power_percentage': 74,
    'car.ev_info.fuel_mode_remain_odometer': 455, 'car.ev_info.electric_mode_remain_odometer': 84,
    'car.basic.cur_journey_odometer': 1656.7, 'car.basic.cur_journey_avg_fuel_consume': 100 / 16.9,
    'car.ev_info.power_battery_voltage': 400, 'car.ev_info.cur_charge_current': 240,
};
fs.mkdirSync(out, { recursive: true });
const report = { viewport: '1920x720', status: 'INCOMPLETE', checks: [], screenshots: [], sweep: [], displaySwitches: [], alphaPixelsPerState: transparentPixelCount };
const errors = [];
const browser = await chromium.launch({ executablePath: process.env.APEX_CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1920, height: 720 }, deviceScaleFactor: 1,
    recordVideo: { dir: path.join(out, 'vector-video'), size: { width: 1920, height: 720 } } });
await context.addInitScript(({ snapshot }) => {
    window.__vectorQaCalls = [];
    window.__vectorQaPreference = 'Vector';
    window.Android = {
        getPreference(key, fallback) { return /^(?:app\.preferences\.)?(?:apexDisplayMode|apex_display_mode)$/.test(key) ? window.__vectorQaPreference : fallback; },
        savePreference(key, value) { window.__vectorQaCalls.push(['preference', key, value]); window.__vectorQaPreference = value; },
        getAvailableKeys() { return JSON.stringify(Object.keys(snapshot)); },
        subscribe(keys) { window.__vectorQaCalls.push(['subscribe', JSON.parse(keys)]); },
        unsubscribe(keys) { window.__vectorQaCalls.push(['unsubscribe', JSON.parse(keys)]); },
        getCarData(key) { return Object.hasOwn(snapshot, key) ? snapshot[key] : null; },
        updateCarData(...args) { window.__vectorQaCalls.push(['vehicle-write', ...args]); },
        triggerSystemAction(...args) { window.__vectorQaCalls.push(['vehicle-action', ...args]); },
    };
}, { snapshot });
const page = await context.newPage();
page.on('pageerror', error => errors.push(error.message));
let offlinePhase = false;
let contextClosed = false;
let labContext;
const offlineNetworkRequests = [];
page.on('request', request => { if (offlinePhase && /^https?:/.test(request.url())) offlineNetworkRequests.push(request.url()); });

async function ready(target = page) {
    await target.evaluate(() => document.fonts.ready);
    await target.waitForFunction(() => ['gauge-material', 'vector-material'].every(id => {
        const image = document.getElementById(id);
        return image && image.complete && image.naturalWidth === 1920 && image.naturalHeight === 720;
    }));
    await target.waitForFunction(() => ['gauge-motion', 'vector-gauge-motion'].every(id => document.getElementById(id)?.dataset.material === 'ready'));
}
async function send(values) {
    await page.evaluate(values => Object.entries(values).forEach(([key, value]) => window.onDataChanged(key, value)), values);
}
async function settled() {
    await page.waitForTimeout(260);
    await page.waitForFunction(() => ['gauge-motion', 'vector-gauge-motion'].every(id => document.getElementById(id).dataset.running === 'false'));
}
async function engineState(target = page) {
    return target.evaluate(() => Object.fromEntries(['gauge-motion', 'vector-gauge-motion'].map(id => [id, { ...document.getElementById(id).dataset }])));
}
async function alpha(file, checkZones = transparentZones) {
    const { data: pixels, info } = await sharp(file).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    assert.equal(info.width, 1920); assert.equal(info.height, 720);
    let checked = 0;
    for (const [x, y, width, height] of checkZones) {
        for (let row = y; row < y + height; row++) for (let col = x; col < x + width; col++) {
            assert.equal(pixels[(row * info.width + col) * info.channels + 3], 0, `${path.basename(file)} alpha at ${col},${row}`);
            checked++;
        }
    }
    return checked;
}
async function opaque(file, checkZones = oemForegroundZones) {
    const { data: pixels, info } = await sharp(file).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    for (const [x, y, width, height] of checkZones) {
        for (let row = y; row < y + height; row++) for (let col = x; col < x + width; col++) {
            assert.equal(pixels[(row * info.width + col) * info.channels + 3], 255,
                `${path.basename(file)} opaque OEM backing at ${col},${row}`);
        }
    }
}
async function capture(name) {
    const file = path.join(out, name + '.png');
    await page.screenshot({ path: file, omitBackground: true });
    assert.equal(await alpha(file), transparentPixelCount);
    await opaque(file);
    report.screenshots.push(name + '.png');
    return file;
}
async function assertLayout() {
    const layout = await page.evaluate(({ zones, ids }) => {
        const visible = node => node && node.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true });
        const rect = node => {
            const b = node.getBoundingClientRect();
            return { left: b.left, top: b.top, right: b.right, bottom: b.bottom, width: b.width, height: b.height };
        };
        const overlaps = (a, b) => a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
        const forbidden = zones.map(([x, y, width, height]) => ({ left: x, top: y, right: x + width, bottom: y + height }));
        const readouts = ids.map(id => ({ id, node: document.getElementById(id) })).filter(({ node }) => visible(node))
            .map(({ id, node }) => ({ id, text: node.textContent, ...rect(node) }));
        const scaleLabels = Array.from(document.querySelectorAll('#vector-gauge-scales text')).filter(visible)
            .map(node => ({ id: `scale:${node.textContent}`, ...rect(node) }));
        const protectedCollisions = readouts.concat(scaleLabels).filter(b => forbidden.some(zone => overlaps(b, zone)));
        const readoutCollisions = readouts.flatMap((a, index) => readouts.slice(index + 1).filter(b => overlaps(a, b)).map(b => [a.id, b.id]));
        return { readouts, scaleLabels, protectedCollisions, readoutCollisions };
    }, { zones: reservedZones, ids: primaryIds });
    assert.ok(layout.scaleLabels.length >= 10, 'Vector has real visible vector scale labels');
    assert.deepEqual(layout.protectedCollisions, [], 'primary data and SVG labels clear ADAS/OEM zones');
    assert.deepEqual(layout.readoutCollisions, [], 'primary readings must not overlap one another');
    const signedPower = layout.readouts.find(readout => readout.id === 'vector-power-value');
    assert.ok(signedPower && signedPower.left >= 1400 && signedPower.right <= 1920
        && signedPower.top >= 120 && signedPower.bottom <= 600, 'signed Vector power is visible inside its right instrument');
    return layout;
}

try {
    await page.goto(origin + '/source/v1.0/ApexGT/index.html');
    await ready(); await settled();
    assert.equal(await page.locator('#apex-gt').getAttribute('data-display'), 'Vector');
    assert.equal(await page.locator('#speed-value').textContent(), '80', 'bridge snapshot seeds telemetry before first interaction');
    await capture('vector-source');
    report.checks.push('editable source loads both 1920x720 materials and initializes Vector from preference plus vehicle snapshot');

    await context.setOffline(true); offlinePhase = true;
    await page.goto(pathToFileURL(path.resolve(root, '../../../Themes/v1.0/ApexGT/app.html')).href);
    await ready(); await settled();
    assert.equal(await page.locator('#apex-gt').getAttribute('data-display'), 'Vector');
    assert.equal(await page.locator('#speed-value').textContent(), '80');
    assert.equal(await page.locator('#power-value').textContent(), '96');
    assert.equal(await page.locator('#apex-menus').isVisible(), false);
    await capture('vector-offline-snapshot');
    await alpha(path.join(root, 'assets/vector-frame.webp'));
    report.checks.push('self-contained bundle initializes offline with central alpha and continuous material below OEM foreground icons');

    for (const [speed, power] of [[0, -100], [40, -50], [80, 0], [120, 50], [160, 150], [200, 200]]) {
        await send({ ...snapshot, 'car.basic.vehicle_speed': speed, 'car.ev_info.cur_charge_current': power * 2.5 });
        await settled();
        const position = await page.evaluate(() => {
            const canvas = document.getElementById('vector-gauge-motion');
            const geometry = window.ApexVectorGeometry;
            return { speed: Number(canvas.dataset.speed), power: Number(canvas.dataset.power),
                speedY: Number(canvas.dataset.speedY), powerY: Number(canvas.dataset.powerY),
                expectedSpeedY: geometry.speedY(Number(canvas.dataset.speed)), expectedPowerY: geometry.powerY(Number(canvas.dataset.power)) };
        });
        assert.equal(position.speed, speed); assert.equal(position.power, power);
        assert.equal(await page.locator('#vector-power-value').textContent(), String(power));
        assert.equal(position.speedY, position.expectedSpeedY); assert.equal(position.powerY, position.expectedPowerY);
        await assertLayout();
        await capture(`vector-sweep-${speed}`);
        report.sweep.push(position);
    }
    report.checks.push('six diagonal-track positions cover 0–200 km/h and −100–200 kW; scales and primary readings clear protected regions');

    await send({ 'car.basic.vehicle_speed': 40, 'car.ev_info.cur_charge_current': -125 }); await settled();
    await send({ 'car.basic.vehicle_speed': 160, 'car.ev_info.cur_charge_current': 375 });
    await page.waitForTimeout(65);
    const intermediate = Number((await engineState())['vector-gauge-motion'].speed);
    assert.ok(intermediate > 40 && intermediate < 160, `intermediate Vector speed ${intermediate}`);
    await capture('vector-animation-intermediate'); await settled();
    const idle = await engineState();
    await send({ 'car.basic.vehicle_speed': 160, 'car.ev_info.cur_charge_current': 375 });
    await page.waitForTimeout(300);
    assert.deepEqual(await engineState(), idle, 'unchanged telemetry leaves both engines idle');
    report.checks.push('Vector animates through intermediate frames, settles RAF and ignores duplicate telemetry');

    for (const [name, flags] of [
        ['preparing', { projectionPreparingD3: true }],
        ['carplay', { carPlayInDash: true, projectionPreparingD3: false }],
        ['android-auto', { carPlayInDash: false, projectionMirrorInDash: true }],
        ['exit', { projectionMirrorInDash: false }],
    ]) {
        await send(flags); await page.waitForTimeout(60); await capture('vector-projection-' + name);
    }
    await send({ 'car.basic.vehicle_speed': 0, 'car.basic.engine_speed': 0, 'car.ev_info.cur_charge_current': 0,
        'car.basic.remain_fuel_percentage': 0, 'car.ev_info.cur_battery_power_percentage': 0 });
    await settled();
    assert.equal(await page.locator('#speed-value').textContent(), '0');
    assert.equal(await page.locator('#power-value').textContent(), '0');
    assert.equal(await page.locator('#rpm-readout').isVisible(), false);
    await capture('vector-zero');
    await send({ 'car.basic.vehicle_speed': null, 'car.ev_info.power_battery_voltage': null });
    await settled();
    for (const id of ['speed-value', 'power-value', 'regen-value']) assert.equal(await page.locator('#' + id).textContent(), '--');
    assert.equal((await engineState())['vector-gauge-motion'].speed, '');
    assert.equal((await engineState())['vector-gauge-motion'].power, '');
    await capture('vector-missing');
    report.checks.push('zero differs from missing; stale light clears; projection stages retain 466400 transparent central pixels');

    await send({ ...snapshot, 'car.basic.vehicle_speed': 200, 'car.basic.total_odometer': 999999,
        'car.basic.cur_journey_odometer': 99999.9, 'car.basic.inside_temp': -40, 'car.basic.outside_temp': -40,
        'car.configure.default_temp_unit': 1 });
    await settled();
    assert.equal(await page.locator('#inside-temp').textContent(), '-40°F');
    assert.equal(await page.locator('#outside-temp').textContent(), '-40°F');
    report.extremeLayout = await assertLayout();
    await capture('vector-long-values');

    let mode = 'Vector';
    for (let index = 0; index < 10; index++) {
        const speed = 50 + index * 10, power = index % 2 ? -40 : 120;
        const activeId = mode === 'Vector' ? 'vector-gauge-motion' : 'gauge-motion';
        const inactiveId = mode === 'Vector' ? 'gauge-motion' : 'vector-gauge-motion';
        const before = await engineState();
        await send({ 'car.basic.vehicle_speed': speed, 'car.ev_info.cur_charge_current': power * 2.5 });
        await settled();
        const changed = await engineState();
        assert.equal(changed[inactiveId].paints, before[inactiveId].paints, `${mode}: inactive renderer must not repaint`);
        assert.equal(changed[activeId].active, 'true'); assert.equal(changed[inactiveId].active, 'false');
        mode = mode === 'Vector' ? 'Contour' : 'Vector';
        await send({ [preference]: mode });
        const switched = await engineState();
        assert.equal(switched[inactiveId].active, 'true'); assert.equal(switched[activeId].active, 'false');
        assert.equal(Number(switched[inactiveId].speed), speed, 'new display snaps to latest cached speed');
        const expectedPower = mode === 'Contour' ? Math.max(-100, Math.min(100, power)) : power;
        assert.equal(Number(switched[inactiveId].power), expectedPower, 'new display snaps to latest cached power in its calibrated range');
        assert.equal(switched[inactiveId].running, 'false', 'switch never replays a hidden animation');
        await page.waitForTimeout(230);
        assert.deepEqual(await engineState(), switched, 'switch settles without a trailing RAF repaint');
        report.displaySwitches.push({ mode, speed, power, activePaints: switched[inactiveId].paints });
    }
    report.checks.push('ten display switches keep one engine active, cache hidden telemetry and snap without stale interpolation');
    await capture('vector-after-ten-switches');
    await send({ 'car.basic.vehicle_speed': 15 }); await page.waitForTimeout(45);
    await page.evaluate(() => window.cleanup());
    const cleaned = await engineState();
    await send({ 'car.basic.vehicle_speed': 190, [preference]: 'Contour' });
    await page.waitForTimeout(300);
    assert.deepEqual(await engineState(), cleaned);
    for (const canvas of Object.values(cleaned)) assert.equal(canvas.running, 'false');
    assert.deepEqual(await page.evaluate(() => window.__vectorQaCalls.filter(([kind]) => kind.startsWith('vehicle-'))), []);
    assert.deepEqual(offlineNetworkRequests, []);
    report.checks.push('cleanup stops both renderers, no vehicle commands issued, no network dependencies in offline bundle');
    await context.close(); contextClosed = true;
    await page.video().saveAs(path.join(out, 'vector-animation.webm')); report.video = 'vector-animation.webm';

    // Separate browser storage and no injected bridge: exercise the actual Lab.
    labContext = await browser.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
    const lab = await labContext.newPage();
    lab.on('pageerror', error => errors.push(error.message));
    await lab.goto(origin + '/?theme=ApexGT');
    let frame;
    async function labReady() {
        frame = await (await lab.waitForSelector('#theme-frame')).contentFrame();
        await frame.waitForFunction(() => !!window.__TEST_HARNESS && window.Android?.__themeLabMock === 'ApexGT');
        await ready(frame);
        await frame.locator('#sim-control-apex_display_mode').waitFor({ state: 'attached' });
        await frame.evaluate(() => window.__TEST_HARNESS.stopSimulation());
        assert.equal(await lab.getByRole('button', { name: 'Aparência', exact: true }).isEnabled(), true);
    }
    async function labMode(expected) {
        await frame.waitForFunction(expected => document.getElementById('apex-gt').dataset.display === expected, expected);
        assert.equal(await frame.evaluate(() => window.__TEST_HARNESS.getState('apexDisplayMode')), expected);
        await frame.waitForFunction(expected => {
            const pill = Array.from(document.querySelectorAll('#sim-control-apex_display_mode button'))
                .find(button => button.textContent === expected);
            return pill && getComputedStyle(pill).color === 'rgb(56, 189, 248)';
        }, expected);
        await frame.waitForFunction(expected => {
            const menu = document.getElementById('apex-menus');
            return menu.hidden || menu.dataset.view !== 'settings'
                || document.getElementById('apex-menu-setting-displayMode').textContent === expected;
        }, expected);
        const pillColor = await frame.locator('#sim-control-apex_display_mode').getByRole('button', { name: expected, exact: true, includeHidden: true })
            .evaluate(button => getComputedStyle(button).color);
        assert.equal(pillColor, 'rgb(56, 189, 248)', 'selected appearance pill reflects current display');
    }
    async function appearance(open) {
        const button = lab.getByRole('button', { name: 'Aparência', exact: true });
        if ((await button.getAttribute('aria-pressed') === 'true') !== open) await button.click();
    }
    async function labKey(value) {
        await frame.evaluate(value => window.__TEST_HARNESS.injectKeystroke(value), value);
    }
    async function labCapture(name) {
        await lab.screenshot({ path: path.join(out, name + '.png'), fullPage: true });
        report.screenshots.push(name + '.png');
    }
    await labReady(); await labMode('Contour');
    await appearance(true);
    const pills = frame.locator('#sim-control-apex_display_mode');
    assert.equal(await pills.getByRole('button', { name: 'Contour', exact: true }).isVisible(), true);
    assert.equal(await pills.getByRole('button', { name: 'Vector', exact: true }).isVisible(), true);
    await pills.getByRole('button', { name: 'Vector', exact: true }).click();
    await labMode('Vector');
    assert.equal(await frame.evaluate(key => localStorage.getItem(key), storageKey), 'Vector');
    await labCapture('vector-lab-appearance'); await appearance(false);
    await frame.evaluate(() => {
        const original = window.Android;
        window.__vectorLabVehicleCalls = [];
        const audit = { ...original,
            updateCarData(...args) { window.__vectorLabVehicleCalls.push(['write', ...args]); return original.updateCarData(...args); },
            triggerSystemAction(...args) { window.__vectorLabVehicleCalls.push(['action', ...args]); return original.triggerSystemAction(...args); },
        };
        window.Android = audit;
        window.__restoreVectorQaBridge = () => { if (window.Android === audit) window.Android = original; };
        window.__TEST_HARNESS.setState('cardId', 0);
        window.__TEST_HARNESS.setState('cardId', 1);
    });
    await labKey('DOWN'); await labKey('DOWN'); await labKey('ENTER');
    for (let index = 0; index < 5; index++) await labKey('DOWN');
    assert.equal(await frame.locator('[data-apex-setting="5"]').isVisible(), true);
    assert.equal(await frame.locator('#apex-menu-setting-displayMode').textContent(), 'Vector');
    await labKey('ENTER'); await labMode('Contour');
    assert.equal(await frame.locator('#apex-menu-setting-displayMode').textContent(), 'Contour');
    await labCapture('vector-lab-menu-display');
    await appearance(true);
    await frame.locator('#sim-control-apex_display_mode').getByRole('button', { name: 'Vector', exact: true }).click();
    await labMode('Vector');
    assert.equal(await frame.locator('#apex-menu-setting-displayMode').textContent(), 'Vector');
    assert.deepEqual(await frame.evaluate(() => window.__vectorLabVehicleCalls), []);
    await frame.evaluate(() => window.__restoreVectorQaBridge());
    report.checks.push('real Lab Appearance exposes Contour/Vector; wheel navigates to sixth Display setting; preference and pills synchronize both ways without vehicle writes');

    await Promise.all([frame.waitForNavigation({ waitUntil: 'load' }), lab.locator('#reload-theme').click()]);
    await labReady(); await labMode('Vector'); await appearance(true);
    await labCapture('vector-lab-reloaded');
    const options = await lab.locator('#theme-select option').evaluateAll(nodes => nodes.map(node => ({ value: node.value, label: node.textContent })));
    const apexOption = options.find(option => option.value.endsWith('/ApexGT'));
    const otherOption = options.find(option => option.value.startsWith('package-legacy:'));
    assert.ok(apexOption && otherOption, 'real catalog exposes ApexGT and another theme');
    await Promise.all([frame.waitForNavigation({ waitUntil: 'load' }), lab.locator('#theme-select').selectOption(otherOption.value)]);
    await frame.waitForSelector('body');
    await Promise.all([frame.waitForNavigation({ waitUntil: 'load' }), lab.locator('#theme-select').selectOption(apexOption.value)]);
    await labReady(); await labMode('Vector'); await appearance(true);
    assert.equal(await frame.evaluate(key => localStorage.getItem(key), storageKey), 'Vector');
    await labCapture('vector-lab-return-from-other-theme');
    report.checks.push('Vector survives real iframe reload and switch to another theme/back; restored pills never overwrite saved selection with Contour');
    await appearance(false);
    await frame.evaluate(() => {
        const harness = window.__TEST_HARNESS;
        harness.stopSimulation();
        Object.entries({ cardId: 0, carSpeed: 80, engineRPM: 1800, gearState: 'D', drivingMode: 'Sport',
            evMode: 'HEV', evPowerKw: 96, inside_temp: 22, outside_temp: 24, fuelPercent: 68, batteryPercent: 74,
            fuelRange: 455, batteryRange: 84, odometer: 18654, carPlayInDash: false,
            projectionMirrorInDash: false, projectionPreparingD3: false,
            'car.basic.cur_journey_odometer': 1656.7, 'car.basic.cur_journey_avg_fuel_consume': 100 / 16.9,
        }).forEach(([key, value]) => harness.setState(key, value));
    });
    // Only the test page's outer preview is expanded. Capture the real Lab
    // foreground together with the iframe; production theme/layout is unchanged.
    await lab.setViewportSize({ width: 2048, height: 1280 });
    await lab.addStyleTag({ content: `
        .lab-main, .lab-header { width: 1960px !important; max-width: none !important; }
        #cluster-preview { position: fixed !important; left: 0 !important; top: 0 !important; z-index: 2147483647 !important;
            width: 1920px !important; height: 720px !important; border-radius: 0 !important; }
        #preview-stage { width: 1920px !important; height: 720px !important; max-width: none !important; }
        #theme-frame { transform: none !important; }
    ` });
    await frame.waitForFunction(() => document.getElementById('vector-gauge-motion').dataset.running === 'false'
        && document.getElementById('vector-gauge-motion').dataset.speed === '80');
    for (const name of ['ready', 'traffic-sign', 'esp']) {
        assert.equal(await frame.locator(`[data-fixed-cluster-icon="${name}"]`).isVisible(), true, `actual Lab ${name} foreground visible`);
    }
    assert.equal(await frame.locator('#apex-menus').isVisible(), false);
    const composedFile = path.join(out, 'vector-lab-oem-composition.png');
    await lab.locator('#preview-stage').screenshot({ path: composedFile });
    const composedSize = await sharp(composedFile).metadata();
    assert.equal(composedSize.width, 1920); assert.equal(composedSize.height, 720);
    report.screenshots.push('vector-lab-oem-composition.png');
    report.checks.push('1920x720 real Lab composition captured with READY, speed sign and ESP foreground, filled Vector readings and card 0');
    assert.deepEqual(errors, []);
    report.status = 'PASS';
} catch (error) {
    report.status = 'FAIL'; report.error = error.stack || String(error);
    throw error;
} finally {
    if (!contextClosed) {
        await context.close();
        await page.video()?.saveAs(path.join(out, 'vector-animation.webm'));
    }
    if (labContext) await labContext.close();
    await browser.close();
    report.pageErrors = errors;
    fs.writeFileSync(path.join(out, 'vector-browser-report.json'), JSON.stringify(report, null, 2) + '\n');
}
console.log(JSON.stringify(report, null, 2));
