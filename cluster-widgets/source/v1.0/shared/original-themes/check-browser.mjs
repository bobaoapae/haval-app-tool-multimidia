// Real packaged-file QA. Run after build; captures are explicitly simulated, not vehicle evidence.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
import { collection, packageRoot, sourceRoot, bundle } from './build.mjs';
const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const sharp = require('sharp');
const out = path.resolve(process.env.IMPULSE_EVIDENCE_DIR || path.join(sourceRoot, '../../../test-artifacts/original-themes'));
fs.mkdirSync(out, { recursive: true });
const data = {
    'car.basic.vehicle_speed': 80, 'car.basic.engine_speed': 1800, 'car.basic.gear_status': 2,
    'car.drive_setting.drive_mode': 0, 'car.ev_setting.power_model_config': 0,
    'car.basic.total_odometer': 18654, 'car.basic.remain_fuel_percentage': 68,
    'car.ev_info.cur_battery_power_percentage': 74, 'car.ev_info.fuel_mode_remain_odometer': 455,
    'car.ev_info.electric_mode_remain_odometer': 84, 'car.basic.cur_journey_odometer': 165.7,
    'car.basic.cur_journey_avg_fuel_consume': 5.9, 'car.ev_info.power_battery_voltage': 400,
    'car.ev_info.cur_charge_current': 80, 'car.basic.outside_temp': 24, 'car.basic.inside_temp': 22,
    'car.configure.default_temp_unit': 0, 'car.hvac.driver_temperature': 22,
    'car.hvac.fan_speed': 3, 'car.hvac.power_mode': 1,
    'app.navigation.directions': JSON.stringify({ street: 'Avenida das Palmeiras', distance: '450 m', turn: 'TURN_RIGHT' })
};
const adas = [520, 120, 880, 530];
const reserved = [adas, [238,315,104,60], [225,475,90,90], [330,445,100,100]];
const report = { status: 'NOT_RUN', browser: 'Chromium desktop, not Android 9', viewport: '1920x720', simulated: true, themes: [] };
let browser;
try {
    browser = await chromium.launch({ headless: true, ...(process.env.IMPULSE_CHROME ? { executablePath: process.env.IMPULSE_CHROME } : {}) });
    report.browserVersion = browser.version();
    for (const name of collection) {
        const result = { name, checks: [] }; report.themes.push(result);
        const context = await browser.newContext({ viewport: { width: 1920, height: 720 }, deviceScaleFactor: 1, offline: true });
        const page = await context.newPage(), errors = [], externalRequests = [];
        page.on('pageerror', error => errors.push(error.message));
        page.on('request', request => { if (/^https?:/.test(request.url())) externalRequests.push(request.url()); });
        async function flush() { await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))); }
        async function send(values) { await page.evaluate(values => Object.keys(values).forEach(key => window.onDataChanged(key, values[key])), values); await flush(); }
        async function reading(field) { return page.locator(`[data-value="${field}"]`).first().textContent(); }
        // Sereno deliberately omits power instrumentation; all other themes must expose it.
        async function checkPower(expected) {
            if (name === 'Sereno') {
                assert.equal(await page.locator('[data-value="power"]').count(), 0, 'minimal Sereno omits power');
            } else {
                assert.equal(await reading('power'), expected);
            }
        }
        async function capture(suffix, entire = false) {
            const file = path.join(out, `${name.toLowerCase()}-${suffix}.png`);
            await page.screenshot({ path: file, omitBackground: true });
            const { data: pixels, info } = await sharp(file).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
            const [x,y,w,h] = entire ? [0,0,1920,720] : adas;
            for (let row = y; row < y+h; row++) for (let col = x; col < x+w; col++) {
                if (pixels[(row*info.width+col)*info.channels+3] !== 0) assert.fail(`${name}/${suffix}: opacity in protected pixel ${col},${row}`);
            }
            result.checks.push(`${suffix}: ${w*h} transparent pixels`);
            return file;
        }
        async function checkDataBounds() {
            const collisions = await page.evaluate(zones => Array.from(document.querySelectorAll('[data-value]')).flatMap(node => {
                const box = node.getBoundingClientRect();
                if (!box.width || !box.height) return [];
                const outside = box.left < 0 || box.right > 1920 || box.top < 0 || box.bottom > 720;
                const collision = zones.some(([x,y,w,h]) => box.left < x+w && box.right > x && box.top < y+h && box.bottom > y);
                return outside || collision ? [{ field: node.getAttribute('data-value'), box: box.toJSON() }] : [];
            }), reserved);
            if (collisions.length) {
                if (!result.layoutCollisions) result.layoutCollisions = [];
                result.layoutCollisions.push({ page: await page.locator('#impulse').getAttribute('data-page-active'), collisions });
            }
        }
        const file = path.join(packageRoot, name, 'app.html');
        assert.equal(fs.readFileSync(file, 'utf8'), bundle(name), 'rebuild stale package');
        await page.goto(pathToFileURL(file).href); await flush();
        assert.equal(await page.evaluate(() => typeof window.onDataChanged), 'function', 'packaged scripts initialize after DOM');
        assert.equal(await reading('speed'), '--'); await capture('missing');
        await send(data); assert.equal(await reading('speed'), '85'); await checkPower('32');
        assert.equal(await reading('totalRange'), '539');
        await checkDataBounds(); await capture('driving');
        for (const [key, expected] of [['DOWN','trip'],['DOWN','navigation'],['BACK','summary'],['UP','navigation'],['HOME','summary']]) {
            await page.evaluate(key => window.onKeyEvent(key), key); await flush();
            assert.equal(await page.locator('#impulse').getAttribute('data-page-active'), expected);
            await checkDataBounds(); await capture(`page-${expected}`);
        }
        await send({ 'car.ev_info.cur_charge_current': -85 }); await checkPower('-34'); await capture('regeneration');
        await send({ 'car.basic.vehicle_speed': 0, 'car.basic.remain_fuel_percentage': 0, 'car.ev_info.cur_battery_power_percentage': 0 });
        assert.equal(await reading('speed'), '0'); assert.equal(await reading('fuel'), '0'); await capture('zero');
        await send({ 'car.basic.vehicle_speed': null, 'car.ev_info.power_battery_voltage': null });
        assert.equal(await reading('speed'), '--'); await checkPower('--'); await capture('invalidated');
        await send({ ...data, 'car.basic.vehicle_speed': 180, 'car.basic.total_odometer': 999999,
            'car.basic.cur_journey_odometer': 99999.9, 'car.basic.outside_temp': -40,
            'app.navigation.directions': JSON.stringify({ street: '<img src=x onerror=alert(1)> Avenida muito longa '.repeat(8), distance: '999 km', turn: 'UNKNOWN' }) });
        await checkDataBounds(); await capture('long-values');
        await page.evaluate(() => window.onKeyEvent('UP')); await flush(); await checkDataBounds();
        assert.equal(await page.locator('[data-value="navStreet"] img').count(), 0); await capture('long-navigation');
        for (const flag of ['projectionPreparingD3','carPlayInDash','projectionMirrorInDash','aaClusterInDash']) {
            await page.evaluate(flag => window.control(flag, true), flag); await flush(); await capture(flag, true);
            await page.evaluate(flag => window.control(flag, false), flag); await flush(); await capture(`${flag}-restored`);
        }
        await page.evaluate(() => { window.onCardChanged(0); window.onKeyEvent('DOWN'); window.control('cardId',1); }); await flush();
        assert.equal(await page.locator('#impulse').getAttribute('data-card'), '0'); await capture('card-zero', true);
        await page.evaluate(() => { window.onCardChanged(3); window.onKeyEvent('BACK'); }); await flush();
        assert.equal(await page.locator('#impulse').getAttribute('data-page-active'), 'climate'); await checkDataBounds(); await capture('climate');
        await page.evaluate(() => { window.onCardChanged(1); window.control('clusterEnabled',false); }); await flush(); await capture('disabled', true);
        await page.evaluate(() => window.control('clusterEnabled',true)); await send(data); await capture('restored');
        // Delivery image: explicit synthetic label; checkerboard belongs ONLY to evidence, not the package.
        await page.evaluate(() => {
            const panel = document.createElement('div'); panel.id = 'qa-only';
            panel.style.cssText = 'position:absolute;left:520px;top:120px;width:880px;height:530px;display:flex;align-items:center;justify-content:center;text-align:center;background:repeating-linear-gradient(45deg,#17212a 0,#17212a 20px,#1c2731 20px,#1c2731 40px);color:#adb9c4;font:18px Arial;letter-spacing:2px;';
            panel.textContent = 'SIMULAÇÃO DE DADOS • ÁREA TRANSPARENTE ADAS'; document.body.appendChild(panel);
            document.querySelectorAll('[data-value="clock"]').forEach(n => n.textContent = '10:24');
        });
        await page.screenshot({ path: path.join(out, `${name.toLowerCase()}-preview-simulado.png`) });
        await page.evaluate(() => document.getElementById('qa-only').remove());
        await page.evaluate(() => { window.cleanup(); window.cleanup(); window.onDataChanged('car.basic.vehicle_speed',20); }); await flush();
        assert.equal(await reading('speed'), '85', 'cleanup prevents updates');
        assert.deepEqual(errors, []); assert.deepEqual(externalRequests, []);
        result.checks.push('offline bundle, missing/zero/invalid, calibrated speed, pages, safe text, cleanup, no JS errors');
        result.checks.push(name === 'Sereno' ? 'power intentionally omitted in minimalist layout' : 'signed power and invalidation');
        result.status = result.layoutCollisions ? 'FAIL' : 'PASS'; await context.close();
    }
    const failures = report.themes.filter(theme => theme.status !== 'PASS');
    if (failures.length) throw new Error('Protected-region layout collisions: ' + JSON.stringify(failures));
    report.status = 'PASS';
} catch (error) { report.status = 'FAIL'; report.error = error.message; throw error; }
finally { if (browser) await browser.close(); fs.writeFileSync(path.join(out, 'browser-report.json'), JSON.stringify(report,null,2)+'\n'); }
console.log(JSON.stringify(report,null,2));
