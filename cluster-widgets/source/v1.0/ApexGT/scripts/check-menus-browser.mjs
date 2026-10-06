// Exercise the shipped menu code offline, then the actual Theme Lab adapter.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const sharp = require('sharp');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const out = process.env.APEX_EVIDENCE_DIR || path.resolve(root, '../../../../tools/headunit-dev/output/apex-gt-menus-20260919');
const displayMode = process.env.APEX_TEST_DISPLAY === 'Vector' ? 'Vector' : 'Contour';
fs.mkdirSync(out, { recursive: true });
const browser = await chromium.launch({ executablePath: process.env.APEX_CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1920, height: 720 }, deviceScaleFactor: 1 });
const page = await context.newPage();
const errors = [];
page.on('pageerror', error => errors.push(error.message));
const report = { displayMode, checks: [], screenshots: [], status: 'INCOMPLETE' };
const K = {
    fan: 'car.hvac.fan_speed', temp: 'car.hvac.driver_temperature', power: 'car.hvac.power_mode',
    auto: 'car.hvac.auto_enable', recycle: 'car.hvac.cycle_mode',
    drive: 'car.drive_setting.drive_mode', propulsion: 'car.ev_setting.power_model_config',
    steer: 'car.drive_setting.steering_wheel_assist_mode', regen: 'car.ev_setting.energy_recovery_level',
    esp: 'car.drive_setting.esp_enable'
};
const data = {
    [K.fan]: 3, [K.temp]: 22, [K.power]: 1, [K.auto]: 0, [K.recycle]: 0,
    [K.drive]: 1, [K.propulsion]: 0, [K.steer]: 2, [K.regen]: 0, [K.esp]: 1,
    'car.basic.vehicle_speed': 72, 'car.basic.engine_speed': 1800,
    'car.basic.gear_status': 2, 'car.basic.inside_temp': 22, 'car.basic.outside_temp': 24,
    'car.configure.default_temp_unit': 0, 'car.basic.total_odometer': 18654,
    'car.basic.remain_fuel_percentage': 68, 'car.ev_info.cur_battery_power_percentage': 74,
    'car.ev_info.fuel_mode_remain_odometer': 455, 'car.ev_info.electric_mode_remain_odometer': 84,
    'car.ev_info.power_battery_voltage': 400, 'car.ev_info.cur_charge_current': 240,
    'car.basic.cur_journey_odometer': 1656.7, 'car.basic.cur_journey_drivetime': 92,
    'car.basic.cur_journey_avg_fuel_consume': 100 / 16.9, 'car.basic.avg_vehicle_speed_since_startup': 42,
    'car.basic.accumulated_odometer': 2584.6, 'car.basic.accumulated_drivetime': 285,
    'car.basic.avg_fuel_consumption': 7, 'car.basic.vehicle_speed_since_reset': 51,
    'car.basic.tire_pressure_front_left': 250, 'car.basic.tire_pressure_front_right': 248,
    'car.basic.tire_pressure_rear_left': 240, 'car.basic.tire_pressure_rear_right': 249
};
async function send(values) {
    await page.evaluate(values => Object.entries(values).forEach(([key, value]) => window.onDataChanged(key, value)), values);
    await page.waitForTimeout(85);
}
async function key(value) { await page.evaluate(value => window.onKeyEvent(value), value); await page.waitForTimeout(85); }
async function card(value) { await page.evaluate(value => window.onCardChanged(value), value); await page.waitForTimeout(85); }
async function calls() { return page.evaluate(() => window.__menuTestCalls); }
async function clearCalls() { await page.evaluate(() => { window.__menuTestCalls.length = 0; }); }
async function shot(name) {
    if (displayMode === 'Vector') name = 'vector-' + name;
    const coveredScaleLabels = await page.evaluate(() => {
        const menu = document.getElementById('apex-menus');
        if (!menu.checkVisibility({ checkVisibilityCSS: true })) return [];
        const panel = menu.getBoundingClientRect();
        return Array.from(document.querySelectorAll('.gauge-scales text'))
            .filter(label => label.checkVisibility({ checkVisibilityCSS: true }))
            .filter(label => {
                const box = label.getBoundingClientRect();
                return box.left < panel.right && box.right > panel.left
                    && box.top < panel.bottom && box.bottom > panel.top;
            }).map(label => label.textContent);
    });
    assert.deepEqual(coveredScaleLabels, [], name + ' keeps instrument scale labels outside the menu');
    const file = path.join(out, name + '.png');
    await page.screenshot({ path: file, omitBackground: true });
    const pixels = await sharp(file).extract({ left: 520, top: 120, width: 880, height: 530 }).ensureAlpha().raw().toBuffer();
    for (let i = 3; i < pixels.length; i += 4) assert.equal(pixels[i], 0, name + ' preserves central alpha');
    report.screenshots.push(name + '.png');
}
async function contains(text) { assert.match(await page.locator('#apex-menus').innerText(), text); }
try {
    await context.setOffline(true);
    await page.goto(pathToFileURL(path.resolve(root, '../../../Themes/v1.0/ApexGT/app.html')).href);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForFunction(() => document.getElementById('gauge-motion').dataset.material === 'ready');
    await send({ 'app.preferences.apexDisplayMode': displayMode });
    if (displayMode === 'Vector') await page.waitForFunction(() => document.getElementById('vector-gauge-motion').dataset.material === 'ready');
    assert.equal(await page.locator('#apex-menus').isVisible(), false);
    await page.evaluate(() => {
        window.__menuTestCalls = [];
        window.__menuTestEcho = true;
        window.Android = {
            updateCarData(key, value) {
                window.__menuTestCalls.push(['write', key, value]);
                if (window.__menuTestEcho) window.onDataChanged(key, value);
            },
            triggerSystemAction(action) { window.__menuTestCalls.push(['action', action]); }
        };
    });
    await card(3);
    for (const value of ['UP', 'ENTER', 'UP', 'ENTER_LONG', 'BACK_LONG']) await key(value);
    assert.deepEqual(await calls(), [], 'unknown HVAC data cannot cause speculative writes');
    report.checks.push('missing HVAC starts absent and cannot issue calculated commands');
    await send(data);
    await card(0);
    await key('ENTER'); await key('UP');
    assert.deepEqual(await calls(), []);
    assert.equal(await page.locator('#apex-menus').isVisible(), false);
    await card(1);
    await contains(/INFORMAÇÕES/);
    await shot('apex-gt-mainmenu');
    assert.equal(await page.locator('#power-value').isVisible(), false);
    assert.equal(await page.locator('#regen-value').isVisible(), false);
    assert.equal(await page.locator('#engine-rpm').isVisible(), true);
    await key('ENTER'); await contains(/ODÔMETRO/);
    await shot('apex-gt-menu-info');
    await key('DOWN'); await contains(/PNEUS/);
    await shot('apex-gt-menu-tires');
    await key('BACK'); await key('DOWN'); await key('ENTER');
    await contains(/POTÊNCIA/); await shot('apex-gt-menu-graph');
    await key('DOWN'); await contains(/VELOCIDADE/);
    await key('BACK'); await key('DOWN'); await key('ENTER');
    await contains(/AJUSTES/); await shot('apex-gt-menu-settings');
    for (const [index, [setting, expected]] of [[K.drive, '0'], [K.propulsion, '1'], [K.steer, '0'], [K.regen, '1'], [K.esp, '0']].entries()) {
        if (index) await key('DOWN');
        await clearCalls(); await key('ENTER');
        assert.deepEqual(await calls(), [['write', setting, expected]], setting);
    }
    await shot('apex-gt-menu-settings-last');
    assert.equal(await page.locator('#drive-mode').textContent(), 'NORMAL');
    assert.equal(await page.locator('#propulsion-mode').textContent(), 'EVP');
    await key('BACK'); await key('DOWN'); await key('ENTER');
    await contains(/TRIP/); await shot('apex-gt-menu-trip-a');
    await clearCalls(); await key('ENTER_LONG'); assert.deepEqual(await calls(), []);
    await key('DOWN'); await shot('apex-gt-menu-trip-b');
    await key('ENTER_LONG'); assert.deepEqual(await calls(), [['action', 'RESET_DRIVE_INFO']]);
    report.checks.push('all main-menu pages, five setting commands, Trip B explicit long-press only');
    await card(3); await shot('apex-gt-ac');
    // Focus was switched to temperature by the missing-data check above.
    await clearCalls(); await key('UP');
    assert.deepEqual(await calls(), [['write', K.temp, '22.5']]);
    await key('DOWN'); await key('ENTER');
    await clearCalls(); await key('UP');
    assert.deepEqual(await calls(), [['write', K.fan, '4']]);
    await send({ [K.fan]: 1, [K.power]: 1 });
    await clearCalls(); await key('DOWN');
    assert.deepEqual(await calls(), [['write', K.power, '0'], ['write', K.fan, '0']]);
    await clearCalls(); await key('UP');
    assert.deepEqual(await calls(), [['write', K.power, '1'], ['write', K.fan, '1']]);
    await send({ [K.fan]: 7 }); await clearCalls(); await key('UP');
    assert.ok((await calls()).every(call => call[1] !== K.fan || Number(call[2]) <= 7));
    await key('ENTER'); await send({ [K.temp]: 32 }); await clearCalls(); await key('UP');
    assert.ok((await calls()).every(call => call[1] !== K.temp || Number(call[2]) <= 32));
    await send({ [K.temp]: 16 }); await clearCalls(); await key('DOWN');
    assert.ok((await calls()).every(call => call[1] !== K.temp || Number(call[2]) >= 16));
    await send({ [K.auto]: 0, [K.recycle]: 0 }); await clearCalls(); await key('ENTER_LONG');
    assert.deepEqual(await calls(), [['action', 'CANCEL_MAX_AC'], ['write', K.auto, '1']]);
    await clearCalls(); await key('BACK_LONG');
    assert.deepEqual(await calls(), [['write', K.recycle, '1']]);
    await send({ [K.temp]: 23.5, [K.fan]: 4 }); await shot('apex-gt-ac-adjusted');
    report.checks.push('AC half-degree steps, fan/power transitions, bounds, AUTO cancel-before-write, recirculation');
    await send({ [K.temp]: null });
    await page.evaluate(() => window.control('temp', 22));
    await clearCalls(); await key('UP'); assert.deepEqual(await calls(), [], 'null canonical cannot be replaced by stale alias');
    await send({ [K.temp]: 22 });
    await page.evaluate(() => { window.__menuTestEcho = false; });
    await clearCalls(); await key('UP');
    assert.deepEqual(await calls(), [['write', K.temp, '22.5']]);
    await contains(/22(?:[.,]0)?°C/);
    await page.evaluate(() => { window.Android.updateCarData = () => { throw new Error('test bridge failure'); }; });
    await key('DOWN'); await contains(/22(?:[.,]0)?°C/);
    await page.evaluate(() => { delete window.Android; });
    await key('UP'); await contains(/22(?:[.,]0)?°C/);
    await shot('apex-gt-ac-unavailable');
    report.checks.push('canonical null beats alias; delayed echo, throwing/missing bridge never invent successful state');
    await card(0); await shot('apex-gt-menu-closed');
    await card(1);
    const idBefore = await page.locator('#apex-gt').getAttribute('data-card-id');
    for (const invalid of [null, '', true, 2, 'nonsense']) await card(invalid);
    await page.evaluate(() => { window.control('cardId', 3); window.showScreen('aircon'); window.focus('temp'); });
    assert.equal(await page.locator('#apex-gt').getAttribute('data-card-id'), idBefore);
    await key('LEFT'); await key('RIGHT');
    assert.equal(await page.locator('#apex-gt').getAttribute('data-card-id'), idBefore);
    await page.evaluate(() => window.cleanup());
    const frozen = await page.locator('#apex-menus').innerHTML();
    await card(3); await key('UP'); await send({ [K.fan]: 7 });
    assert.equal(await page.locator('#apex-menus').innerHTML(), frozen);
    report.checks.push('host-only card authority, hidden-card isolation, cleanup freezes menu handlers');
    await context.setOffline(false);
    await page.goto((process.env.APEX_LAB_URL || 'http://localhost:1234') + '/?theme=ApexGT');
    const frame = await (await page.waitForSelector('iframe')).contentFrame();
    await frame.waitForFunction(() => !!window.__TEST_HARNESS && !!window.Android?.updateCarData);
    await frame.evaluate(mode => window.Android.savePreference('apexDisplayMode', mode), displayMode);
    await frame.evaluate(() => { window.__TEST_HARNESS.stopSimulation(); window.__TEST_HARNESS.setState('cardId', 3); });
    await page.waitForTimeout(150);
    await frame.evaluate(() => { window.__TEST_HARNESS.setState('fan', 3); window.__TEST_HARNESS.setState('temp', 22); });
    await page.getByRole('button', { name: 'Comando subir', exact: true }).click();
    await page.waitForTimeout(100);
    assert.equal(Number(await frame.evaluate(() => window.__TEST_HARNESS.getState('fan'))), 4);
    await frame.evaluate(() => window.__TEST_HARNESS.injectKeystroke('RIGHT'));
    await page.waitForTimeout(100);
    assert.equal(await frame.locator('#apex-gt').getAttribute('data-card-id'), '1');
    await frame.evaluate(() => window.__TEST_HARNESS.injectKeystroke('RIGHT'));
    await page.waitForTimeout(100);
    assert.equal(await frame.locator('#apex-menus').isVisible(), false);
    report.checks.push('real Theme Lab wrapper: wheel button changes HVAC; simulated RIGHT cycles 3 → 1 → 0');
    assert.deepEqual(errors, []);
    report.alphaPixelsPerScreenshot = 466400;
    report.status = 'PASS';
} finally {
    await browser.close();
    fs.writeFileSync(path.join(out, displayMode === 'Vector' ? 'vector-menus-report.json' : 'menus-report.json'), JSON.stringify(report, null, 2) + '\n');
}
console.log(JSON.stringify(report, null, 2));
