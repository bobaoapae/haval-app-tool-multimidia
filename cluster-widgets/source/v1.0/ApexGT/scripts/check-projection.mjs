import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

// Modo de projeção no painel: "Ampla" (padrão, equivalente ao Analógico V2) esmaece moldura e
// superfícies; "Janela" mantém o recorte do viewport. Só a classe do root muda aqui; o desenho fica
// em apex-projection.css.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = fs.readFileSync(path.join(root, 'src/apex-projection.js'), 'utf8');

function create(bridge) {
    const classes = new Set();
    const attributes = new Map();
    const frame = { src: 'data:image/webp;base64,AAAA', style: {} };
    const node = {
        classList: { toggle(name, enabled) { if (enabled) classes.add(name); else classes.delete(name); }, contains: (name) => classes.has(name) },
        setAttribute(name, value) { attributes.set(name, value); },
        querySelectorAll: () => [frame],
    };
    const window = { Android: bridge };
    const context = vm.createContext({ window });
    vm.runInContext(source, context, { filename: 'apex-projection.js' });
    return { projection: window.ApexProjection.create({ root: node }), classes, attributes, frame };
}

let cases = 0;
function test(name, callback) { callback(); cases++; console.log(`PASS ${name}`); }

test('defaults to Ampla and exposes the preference key', () => {
    const e = create();
    assert.equal(e.projection.getMode(), 'Ampla');
    assert.equal(e.classes.has('projection-wide'), true);
    assert.equal(e.attributes.get('data-projection-mode'), 'Ampla');
    assert.deepEqual([...e.projection.keys], ['app.preferences.apexProjectionMode']);
    assert.deepEqual([...e.projection.preferenceKeys], ['app.preferences.apexProjectionMode']);
});

test('restores the saved preference, accepting case and English synonyms', () => {
    const e = create({ getPreference: (key, fallback) => (key === 'apexProjectionMode' ? 'janela' : fallback) });
    assert.equal(e.projection.getMode(), 'Janela');
    assert.equal(e.classes.has('projection-wide'), false);
    const f = create({ getPreference: () => 'wide' });
    assert.equal(f.projection.getMode(), 'Ampla');
    const g = create({ getPreference: () => 'anything' });
    assert.equal(g.projection.getMode(), 'Ampla', 'unknown value keeps the default');
    const h = create({ getPreference: () => { throw new Error('down'); } });
    assert.equal(h.projection.getMode(), 'Ampla');
});

test('canonical preference wins over aliases; control alias needs allowAliases', () => {
    const e = create();
    e.projection.update('app.preferences.apex_projection_mode', 'Janela', false);
    assert.equal(e.projection.getMode(), 'Janela', 'preference alias accepted before canonical');
    e.projection.update('apexProjectionMode', 'Ampla', false);
    assert.equal(e.projection.getMode(), 'Janela', 'control alias without allowAliases ignored');
    e.projection.update('apexProjectionMode', 'Ampla', true);
    assert.equal(e.projection.getMode(), 'Ampla');
    e.projection.update('app.preferences.apexProjectionMode', 'Janela', false);
    assert.equal(e.projection.getMode(), 'Janela');
    e.projection.update('app.preferences.apex_projection_mode', 'Ampla', false);
    assert.equal(e.projection.getMode(), 'Janela', 'aliases ignored once canonical was seen');
    e.projection.update('app.preferences.apexProjectionMode', 'nonsense', false);
    assert.equal(e.projection.getMode(), 'Janela');
});

test('cleanup freezes the mode', () => {
    const e = create();
    e.projection.cleanup();
    e.projection.update('app.preferences.apexProjectionMode', 'Janela', false);
    assert.equal(e.projection.getMode(), 'Ampla');
});

console.log(`Apex GT projection mode: ${cases} behavioral cases passed.`);
