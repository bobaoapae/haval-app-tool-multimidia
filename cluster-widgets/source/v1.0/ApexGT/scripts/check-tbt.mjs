import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

// Turn-by-turn: apex-tbt.js monta o cartão compartilhado (shared/tbt via window.ApexShared) no root e
// repassa a ele as direções e o radar do host; o desenho e a leitura do JSON ficam no cartão.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = fs.readFileSync(path.join(root, 'src/apex-tbt.js'), 'utf8');

function create(shared) {
    const children = [];
    const node = { appendChild(child) { children.push(child); child.parentNode = node; }, removeChild(child) { children.splice(children.indexOf(child), 1); } };
    const window = { ApexShared: shared };
    vm.runInContext(source, vm.createContext({ window }), { filename: 'apex-tbt.js' });
    return { tbt: window.ApexTbt.create({ root: node }), children };
}

function fakeShared() {
    const calls = { update: [], radar: [] };
    const classes = new Set();
    return {
        calls,
        classes,
        createTbtCard: () => ({
            element: { classList: { add: (name) => classes.add(name) } },
            update: (d) => calls.update.push(d),
            setRadar: (r) => calls.radar.push(r),
        }),
        parseNavigationDirections: (raw) => ({ parsed: raw }),
        parseRadarAlert: (raw) => ({ radar: raw }),
    };
}

let cases = 0;
function test(name, callback) { callback(); cases++; console.log(`PASS ${name}`); }

test('mounts the shared card and subscribes to directions and radar', () => {
    const shared = fakeShared();
    const e = create(shared);
    assert.equal(e.children.length, 1);
    assert.equal(shared.classes.has('apex-tbt'), true);
    assert.deepEqual([...e.tbt.keys], ['app.navigation.directions', 'app.navigation.radar']);
});

test('routes each key through the shared parser into the card', () => {
    const shared = fakeShared();
    const e = create(shared);
    e.tbt.update('app.navigation.directions', '{"active":true}');
    e.tbt.update('app.navigation.radar', '{"active":true}');
    e.tbt.update('car.basic.vehicle_speed', '50');
    assert.deepEqual(shared.calls.update, [{ parsed: '{"active":true}' }]);
    assert.deepEqual(shared.calls.radar, [{ radar: '{"active":true}' }]);
});

test('cleanup removes the card and ignores later pushes', () => {
    const shared = fakeShared();
    const e = create(shared);
    e.tbt.cleanup();
    e.tbt.update('app.navigation.directions', '{"active":true}');
    assert.equal(e.children.length, 0);
    assert.equal(shared.calls.update.length, 0);
});

test('without the shared runtime it stays inert', () => {
    const e = create(undefined);
    assert.deepEqual([...e.tbt.keys], []);
    e.tbt.update('app.navigation.directions', '{}');
    e.tbt.cleanup();
});

console.log(`Apex GT turn-by-turn: ${cases} behavioral cases passed.`);
