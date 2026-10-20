import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { collection, shared, sourceRoot, packageRoot, contractSource, bundle } from './build.mjs';
import { createThemeCatalog } from '../../../../dev/theme-catalog.mjs';

const runtime = fs.readFileSync(path.join(shared, 'runtime.js'), 'utf8');
new vm.Script(runtime);
new vm.Script(contractSource());
assert.equal(fs.readFileSync(path.join(shared, 'contract.js'), 'utf8'), contractSource());
assert.doesNotMatch(runtime, /\.(?:updateCarData|triggerSystemAction|savePreference|saveSetting|launchApp|killApp|setNativeMaskState|setNativeMasksConfig|setClusterBackground|setAaClusterMapEnabled)\s*\(/);
assert.doesNotMatch(runtime, /setInterval\s*\(|fetch\s*\(|XMLHttpRequest|innerHTML\s*=/);
const catalog = createThemeCatalog(path.resolve(sourceRoot, '../..'));
for (const name of collection) {
    const root = path.join(sourceRoot, name);
    const manifest = fs.readFileSync(path.join(root, 'theme.xml'), 'utf8');
    assert.match(manifest, /<contractVersion>v1\.0<\/contractVersion>/);
    assert.match(manifest, /<minBridgeVersion>1\.0\.0<\/minBridgeVersion>/);
    assert.doesNotMatch(manifest, /<nativeMasks|<background>/);
    assert.equal(fs.readFileSync(path.join(packageRoot, name, 'theme.xml'), 'utf8'), manifest);
    assert.equal(fs.readFileSync(path.join(packageRoot, name, 'app.html'), 'utf8'), bundle(name));
    const localBundle = path.join(root, 'dist/app.html');
    if (fs.existsSync(localBundle)) assert.equal(fs.readFileSync(localBundle, 'utf8'), bundle(name));
    assert.ok(bundle(name).indexOf('<script>') > bundle(name).indexOf('</main>'), `${name}: inline script executes after theme DOM`);
    assert.ok(Buffer.byteLength(bundle(name)) < 80000, `${name} bundle stays lightweight`);
    assert.ok(fs.existsSync(path.join(packageRoot, name, 'thumbnail.png')), `${name} catalog thumbnail`);
    const found = catalog.find(theme => theme.folder === `source/v1.0/${name}`);
    assert.equal(found?.keyboard, 'none'); assert.equal(found?.telemetry, 'injected');
}

function harness(options = {}) {
    const values = {}, attrs = {}, queued = new Map(), timers = new Map(), events = {}, subscriptions = [], unsubscribes = [], reads = [];
    const fields = ['speed','power','gear','fuel','battery','totalRange','trip','navStreet','navDistance','navTurn','climateFan','climateLeft','rpm','fuelRange','batteryRange','odometer','consumption','pageLabel','clock'];
    const nodes = fields.map(name => ({ textContent: '--', getAttribute: () => name }));
    const panels = ['summary','trip','navigation','climate'].map(name => ({ hidden: true, getAttribute: () => name }));
    const classes = new Map();
    const root = { querySelectorAll: selector => selector === '[data-value]' ? nodes : selector === '[data-page]' ? panels : [], setAttribute: (k,v) => attrs[k] = v };
    let id = 0;
    const window = {
        requestAnimationFrame: callback => { queued.set(++id, callback); return id; }, cancelAnimationFrame: id => queued.delete(id),
        setTimeout: callback => { timers.set(++id, callback); return id; }, clearTimeout: id => timers.delete(id),
        addEventListener: (k,v) => events[k] = v, removeEventListener: k => delete events[k],
    };
    const document = { getElementById: () => root, documentElement: { classList: { toggle: (name,value) => classes.set(name,value) } } };
    if (options.bridge) window.Android = {
        getAvailableKeys: () => options.available || JSON.stringify(['car.basic.vehicle_speed','car.basic.remain_fuel_percentage']),
        subscribe: json => { subscriptions.push(JSON.parse(json)); if (options.pushDuringSubscribe) window.onDataChanged('car.basic.vehicle_speed', '80'); },
        unsubscribe: json => unsubscribes.push(JSON.parse(json)),
        getCarData: key => { reads.push(key); return key === 'car.basic.vehicle_speed' ? '10' : ''; },
        getPreference: (key, fallback) => fallback,
    };
    const context = vm.createContext({ window, document });
    vm.runInContext(contractSource(), context); vm.runInContext(runtime, context);
    function flush() { for (const [id, cb] of queued) { queued.delete(id); cb(); } nodes.forEach((n,i) => values[fields[i]] = n.textContent); }
    flush();
    return { window, values, attrs, classes, subscriptions, unsubscribes, reads, queued, timers, panels, events, flush };
}
let checks = 0;
const h = harness();
assert.equal(h.values.speed, '--'); checks++;
h.window.control('carSpeed', 80); h.flush(); assert.equal(h.values.speed, '80'); checks++;
h.window.onDataChanged('car.basic.vehicle_speed', 80); h.flush(); assert.equal(h.values.speed, '85'); checks++;
h.window.control('carSpeed', 99); h.flush(); assert.equal(h.values.speed, '85'); checks++;
for (const bad of [null, '', ' ', 'broken', '23foo', true, [], {}, -1, NaN, Infinity]) {
    h.window.onDataChanged('car.basic.vehicle_speed', bad); h.flush(); assert.equal(h.values.speed, '--'); checks++;
}
h.window.onDataChanged('car.basic.vehicle_speed', 0); h.flush(); assert.equal(h.values.speed, '0'); checks++;
h.window.onDataChanged('car.ev_info.power_battery_voltage', 400); h.flush(); assert.equal(h.values.power, '--'); checks++;
h.window.onDataChanged('car.ev_info.cur_charge_current', -85); h.flush(); assert.equal(h.values.power, '-34'); checks++;
h.window.onDataChanged('car.hvac.driver_temperature', 16); h.flush(); assert.equal(h.values.climateLeft, 'LO'); checks++;
h.window.onDataChanged('car.hvac.driver_temperature', 32); h.flush(); assert.equal(h.values.climateLeft, 'HI'); checks++;
h.window.onDataChanged('car.ev_info.power_battery_voltage', 1e308); h.window.onDataChanged('car.ev_info.cur_charge_current', 1e308); h.flush(); assert.equal(h.values.power, '--'); assert.equal(h.attrs['data-power'], 'unknown'); checks++;
h.window.onDataChanged('car.basic.vehicle_speed', 1e308); h.flush(); assert.equal(h.values.speed, '--'); checks++;
h.window.onDataChanged('car.ev_info.power_battery_voltage', null); h.flush(); assert.equal(h.values.power, '--'); checks++;
h.window.onDataChanged('car.ev_info.fuel_mode_remain_odometer', 400); h.flush(); assert.equal(h.values.totalRange, '--'); checks++;
h.window.onDataChanged('car.ev_info.electric_mode_remain_odometer', 0); h.flush(); assert.equal(h.values.totalRange, '400'); checks++;
h.window.onDataChanged('car.basic.remain_fuel_percentage', 101); h.flush(); assert.equal(h.values.fuel, '--'); checks++;
h.window.onDataChanged('car.basic.remain_fuel_percentage', 0); h.flush(); assert.equal(h.values.fuel, '0'); checks++;
h.window.onDataChanged('app.navigation.directions', '{broken'); h.flush(); assert.equal(h.values.navStreet, 'Sem orientação ativa'); checks++;
h.window.onDataChanged('app.navigation.directions', JSON.stringify({ street: '<img src=x onerror=alert(1)>', distance: '400 m', turn: 'UNKNOWN' })); h.flush();
assert.equal(h.values.navStreet, '<img src=x onerror=alert(1)>'); assert.equal(h.values.navTurn, 'SIGA O NAVEGADOR'); checks++;
h.window.onCardChanged(0); h.window.onKeyEvent('DOWN'); h.flush(); assert.equal(h.attrs['data-card'], '0'); assert.ok(h.panels.every(n => n.hidden)); checks++;
h.window.control('cardId', 1); h.window.showScreen('main_menu'); h.flush(); assert.equal(h.attrs['data-card'], '0'); checks++;
h.window.onCardChanged(3); h.window.onKeyEvent('BACK'); h.flush(); assert.equal(h.attrs['data-page-active'], 'climate'); checks++;
h.window.onCardChanged(1); h.window.onKeyEvent('RIGHT'); h.flush(); assert.equal(h.attrs['data-page-active'], 'summary'); checks++;
h.window.onKeyEvent('DOWN'); h.flush(); assert.equal(h.attrs['data-page-active'], 'trip'); checks++;
h.window.control('clusterEnabled', false); assert.equal(h.classes.get('cluster-disabled'), true); checks++;
h.window.control('clusterEnabled', true); assert.equal(h.classes.get('cluster-disabled'), false); checks++;
h.window.control('aaClusterInDash', true); assert.equal(h.attrs['data-projection'], 'active'); checks++;
h.window.control('aaClusterInDash', false); assert.equal(h.attrs['data-projection'], 'inactive'); checks++;
h.window.cleanup(); h.window.cleanup(); assert.equal(h.timers.size, 0); assert.equal(h.queued.size, 0); assert.equal(Object.keys(h.events).length, 0); checks++;
h.window.onDataChanged('car.basic.vehicle_speed', 42); assert.equal(h.queued.size, 0); checks++;
const seeded = harness({ bridge: true, pushDuringSubscribe: true });
assert.equal(seeded.values.speed, '85'); assert.ok(!seeded.reads.includes('car.basic.vehicle_speed')); checks++;
assert.ok(seeded.subscriptions[0].every(k => ['car.basic.vehicle_speed','car.basic.remain_fuel_percentage','app.preferences.enableSpeedAdjustment','app.preferences.speedAdjustmentOffset'].includes(k))); checks++;
seeded.window.cleanup(); seeded.window.cleanup(); assert.equal(seeded.unsubscribes.length, 1); checks++;
const invalidKeys = harness({ bridge: true, available: '{}' }); assert.equal(invalidKeys.subscriptions.length, 0); checks++;
for (const [key, field] of Object.entries({
    'car.basic.vehicle_speed':'speed', 'car.basic.engine_speed':'rpm', 'car.basic.remain_fuel_percentage':'fuel',
    'car.ev_info.cur_battery_power_percentage':'battery', 'car.ev_info.fuel_mode_remain_odometer':'fuelRange',
    'car.ev_info.electric_mode_remain_odometer':'batteryRange', 'car.basic.total_odometer':'odometer',
    'car.basic.cur_journey_odometer':'trip', 'car.basic.cur_journey_avg_fuel_consume':'consumption', 'car.hvac.fan_speed':'climateFan'
})) {
    const numeric = harness();
    for (const bad of [null, undefined, '', ' ', true, false, {}, [], NaN, Infinity, -Infinity, '17abc', -1]) {
        numeric.window.onDataChanged(key, bad); numeric.flush(); assert.equal(numeric.values[field], '--', `${field} rejects ${String(bad)}`); checks++;
    }
    numeric.window.onDataChanged(key, 0); numeric.flush(); assert.equal(numeric.values[field], ['trip','consumption'].includes(field) ? '0.0' : '0'); checks++;
    numeric.window.cleanup();
}
console.log(`Original collection: 5 manifests/packages/catalog entries verified; ${checks} runtime scenarios passed.`);
