import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { StateManager, createStateProxy } from '../source/v1.0/shared/state/StateManager.js';

// Dependency-free host-control regression. Execute the shipped controllers,
// state modules, renderers and viewport calculations with a minimal DOM; this
// is not a substitute for pixel/compositor validation in Android WebView.
const read = relative => readFileSync(new URL(relative, import.meta.url), 'utf8');
const visibility = read('../source/v1.0/shared/runtime/clusterVisibility.js').replace('export function ', 'function ');
const css = read('../source/v1.0/shared/runtime/clusterVisibility.css');
function node() {
    const classes = new Set();
    return {
        className: '', style: {}, dataset: {},
        classList: {
            toggle(name, on) { if (on) classes.add(name); else classes.delete(name); },
            contains(name) { return classes.has(name); },
        },
    };
}
function block(source, name) {
    const match = source.match(new RegExp(`^function ${name}\\([^]*?^\\}`, 'm'));
    assert.ok(match, `Missing runtime function ${name}`);
    return match[0];
}
function environment(theme) {
    const source = read(`../source/v1.0/${theme}/src/core/main.js`);
    const state = read(`../source/v1.0/${theme}/src/core/state.js`)
        .replace(/^import .*;\s*$/gm, '').replace(/^export \{.*\};\s*$/gm, '');
    const document = { documentElement: node(), body: node(), querySelector: () => null };
    const app = node();
    const dimensions = [];
    const window = { Android: { setAppDefaultDimensions: (...bounds) => dimensions.push(bounds) } };
    const cache = Object.fromEntries(['main_menu', 'aircon', 'graph', 'graphs', 'regen', 'display_selection', 'ajustes', 'info'].map(name => [name, node()]));
    const context = vm.createContext({
        window, document, StateManager, createStateProxy,
        appContainer: app, screenCache: cache, currentComponent: null,
        menuWrapper: node(), nativeMockEnabled: false,
        logger: { enter() {}, leave() {}, log() {} },
        console: { log() {}, error(...args) { throw new Error(args.join(' ')); } },
        FRIENDLY_KEY_TO_CAN_KEY: {},
    });
    // Use the real helper definitions, including the unchanged projection and
    // viewport policies, so an off/on cycle cannot silently alter their output.
    let helpers = source.slice(source.indexOf('function isProjectionMapDisplayActive()'), source.indexOf('// Initial state from URL parameters'));
    if (theme === 'default') {
        helpers += source.slice(source.indexOf('function isProjectionInDashActive()'), source.indexOf("subscribe('warningActive'"));
    } else {
        helpers += block(source, 'updateAppDimensions');
    }
    const control = source.match(/^window\.control = function \(key, value\) \{[^]*?^\};/m)?.[0];
    const subscription = source.match(/^subscribe\('clusterEnabled', render\);/m)?.[0];
    assert.ok(control && subscription, `${theme}: existing host control and subscription`);
    assert.match(source, /import \{ applyClusterVisibility \} from '.*clusterVisibility\.js'/);
    vm.runInContext(`${state}\nconst get = getState;\n${visibility}\n${helpers}\n${block(source, 'render')}\n${control}\n${subscription}\nrender();`, context);
    return { window, document, app, cache, dimensions, context, render: () => vm.runInContext('render()', context), get: key => context.getState(key) };
}

for (const theme of ['default', 'minimalist']) {
    const e = environment(theme);
    const hidden = () => e.document.documentElement.classList.contains('cluster-disabled');
    assert.equal(e.get('clusterEnabled'), true);
    assert.equal(hidden(), false, `${theme}: older host stays visible by default`);
    // Every display/screen/projection combination runs the actual full render,
    // preserving the existing body and app classes as well as native bounds.
    for (const display of ['Normal', 'Esportivo', 'Reduzido', 'Clean', 'Mapa']) {
        for (const screen of ['main_menu', 'aircon', 'graph', 'regen', 'display_selection']) {
            for (const projection of [false, true]) {
                e.window.control('display', display);
                e.window.control('screen', screen);
                e.window.control('carPlayInDash', projection);
                e.window.control('projectionMirrorInDash', projection);
                e.render();
                const bounds = e.dimensions.at(-1);
                const appClasses = e.app.className;
                const menu = e.cache[screen];
                const menuDisplay = menu.style.display;
                e.window.control('clusterEnabled', false);
                assert.equal(hidden(), true, `${theme}: immediate off ${display}/${screen}/${projection}`);
                assert.deepEqual(e.dimensions.at(-1), bounds, 'off preserves native viewport dimensions');
                e.window.control('clusterEnabled', false);
                e.window.control('carSpeed', 73);
                e.window.control('cardId', 0);
                e.window.control('warningActive', true);
                e.render();
                assert.equal(hidden(), true, 'card/telemetry/warning render cannot expose page chrome');
                e.window.control('cardId', 1);
                e.window.control('warningActive', false);
                e.window.control('clusterEnabled', true);
                assert.equal(hidden(), false, 'explicit true restores page');
                assert.equal(e.get('carSpeed'), 73, 'latest telemetry retained');
                assert.equal(e.get('screen'), screen, 'current screen retained');
                assert.equal(e.app.className, appClasses, 'all display/projection classes restored');
                assert.equal(menu.style.display, menuDisplay, 'cached screen restored');
                assert.deepEqual(e.dimensions.at(-1), bounds, 'on preserves native viewport dimensions');
            }
        }
    }
    e.window.control('clusterEnabled', false);
    e.context.setState('clusterEnabled', undefined);
    assert.equal(hidden(), false, 'missing value uses backward-compatible visible default');
    console.log(`PASS ${theme}: 50 display/screen/projection off-render-on cycles and legacy default`);
}

// Only a page-wide ancestor can cover portals, right-side masks, fixed bars,
// root backgrounds and new chrome without maintaining a partial selector list.
assert.match(css, /html\.cluster-disabled\s*\{[^}]*opacity:\s*0\s*!important/);
assert.match(css, /html\.cluster-disabled\s*\{[^}]*pointer-events:\s*none\s*!important/);
assert.match(css, /html\.cluster-disabled,\s*html\.cluster-disabled body\s*\{\s*background:\s*transparent\s*!important/);
assert.doesNotMatch(css, /\b(?:display|width|height|position|transform|transition|animation)\s*:/);
for (const theme of ['default', 'minimalist', 'ApexGT']) {
    assert.match(read(`../source/v1.0/${theme}/index.html`), /href="(?:\.\/)?\.\.\/shared\/runtime\/clusterVisibility\.css"/);
}
for (const theme of ['default', 'minimalist']) {
    assert.doesNotMatch(read(`../source/v1.0/${theme}/src/styles/night.style.css`), /\.cluster-disabled\s/,
        'obsolete partial hiding must not alter layout while the page is disabled');
}

for (const [source, folder, main] of [['default', 'Default', 'index.html'], ['minimalist', 'minimalist', 'app.html'], ['ApexGT', 'ApexGT', 'app.html']]) {
    const html = read(`../Themes/v1.0/${folder}/${main}`);
    const manifest = read(`../source/v1.0/${source}/theme.xml`);
    assert.equal(read(`../Themes/v1.0/${folder}/theme.xml`), manifest, `${folder}: OTA manifest matches source`);
    assert.match(manifest, /<contractVersion>v1\.0<\/contractVersion>/);
    const styles = [...html.matchAll(/<style[^>]*>([^]*?)<\/style>/g)].map(match => match[1]).join('\n');
    assert.match(styles, /html\.cluster-disabled\s*\{[^}]*opacity:\s*0\s*!important/, `${folder}: compiled page gate`);
    assert.match(styles, /html\.cluster-disabled\s+body\s*\{\s*background:\s*(?:transparent|0 0)\s*!important/, `${folder}: compiled canvas background gate`);
    assert.doesNotMatch(html, /(?:src|href)=["']?(?:\.\/)?\.\.\/shared\//, 'shared code must be inlined in OTA');
    if (source === 'ApexGT') {
        assert.match(html, /window\.ApexShared\.applyClusterVisibility\(value\)/);
    } else {
        // Parcel inlines the tiny helper into render. Execute that emitted
        // statement as well, preserving its compiled strict-boolean condition.
        const compiled = html.match(/document\.documentElement\.classList\.toggle\("cluster-disabled",!1===([\w$]+)\)/);
        assert.ok(compiled, `${folder}: compiled boolean visibility controller`);
        const document = { documentElement: node() };
        for (const value of [undefined, true, false, false, true]) {
            vm.runInNewContext(compiled[0], { document, [compiled[1]]: value });
            assert.equal(document.documentElement.classList.contains('cluster-disabled'), value === false);
        }
    }
    console.log(`PASS ${folder}: compiled visibility controller/CSS and OTA manifest`);
}
assert.equal(read('../../app/src/main/res/raw/app.html'), read('../Themes/v1.0/Default/index.html'), 'bundled Default matches OTA');
assert.equal(read('../../app/src/main/assets/Default/theme.xml'), read('../source/v1.0/default/theme.xml'), 'bundled Default manifest matches source');
console.log('PASS full-page CSS gate, backwards-compatible v1.0 packaging and APK/OTA parity');
