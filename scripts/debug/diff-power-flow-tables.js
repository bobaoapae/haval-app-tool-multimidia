const fs = require('fs');

const VIEWER = 'C:/Users/vanes/AppData/Local/Temp/claude/C--Users-vanes-StudioProjects-haval-app-tool-multimidia/e0a0fd30-2b24-481a-93d5-9ca9e2de8d95/scratchpad/viewer/unz/assets/www/index.html';
const KOTLIN = 'C:/Users/vanes/StudioProjects/haval-app-tool-multimidia/app/src/main/java/br/com/redesurftank/havalshisuku/managers/PowerFlowMapper.kt';

// ---- viewer: CAR_POWER_FLOW (the field-tested table) ----
const src = fs.readFileSync(VIEWER, 'utf8');
const start = src.indexOf('const CAR_POWER_FLOW = {');
const body = src.slice(start, src.indexOf('\n};', start));

const viewer = {};
const lineRe = /^\s*(\d+):\s*(.+?),?\s*$/gm;
let m;
while ((m = lineRe.exec(body)) !== null) {
  const key = Number(m[1]);
  const val = m[2];
  if (/CAR_POWER_IDLE/.test(val)) {
    viewer[key] = { tone: 'idle', front: 0, rear: 0 };
    continue;
  }
  const tone = /tone:\s*"([a-z]+)"/.exec(val);
  const front = /front:\s*(-?\d+)/.exec(val);
  const rear = /rear:\s*(-?\d+)/.exec(val);
  if (!tone || !front || !rear) continue;
  viewer[key] = { tone: tone[1], front: Number(front[1]), rear: Number(rear[1]) };
}

// ---- native: PowerFlowMapper.TABLE ----
const kt = fs.readFileSync(KOTLIN, 'utf8');
const kBody = kt.slice(kt.indexOf('private val TABLE'), kt.indexOf('@JvmStatic'));

const native = {};
const ktRe = /(\d+)\s+to\s+(IDLE|CHARGE|PowerFlow\(STATE_(\w+),\s*(?:true|false),\s*(-?\d+),\s*(-?\d+)\))/g;
while ((m = ktRe.exec(kBody)) !== null) {
  const key = Number(m[1]);
  if (m[2] === 'IDLE') native[key] = { tone: 'idle', front: 0, rear: 0 };
  else if (m[2] === 'CHARGE') native[key] = { tone: 'charge', front: 0, rear: 0 };
  else native[key] = { tone: m[3].toLowerCase(), front: Number(m[4]), rear: Number(m[5]) };
}

const keys = [...new Set([...Object.keys(viewer), ...Object.keys(native)])]
  .map(Number).sort((a, b) => a - b);

const diffs = [];
let identical = 0;
for (const k of keys) {
  const v = viewer[k], n = native[k];
  if (!v) { diffs.push({ k, kind: 'native-only', v: null, n }); continue; }
  if (!n) { diffs.push({ k, kind: 'viewer-only', v, n: null }); continue; }
  if (v.tone === n.tone && v.front === n.front && v.rear === n.rear) { identical++; continue; }
  diffs.push({ k, kind: 'mismatch', v, n });
}

const fmt = (o) => o ? `tone=${o.tone.padEnd(6)} front=${String(o.front).padStart(2)} rear=${String(o.rear).padStart(2)}` : '(absent)';

console.log(`viewer states: ${Object.keys(viewer).length}   native states: ${Object.keys(native).length}`);
console.log(`identical: ${identical}   differing: ${diffs.length}\n`);
if (!diffs.length) { console.log('No differences.'); process.exit(0); }
console.log('state | viewer (tested)                  | native (PowerFlowMapper)');
console.log('------+----------------------------------+---------------------------------');
for (const d of diffs) {
  console.log(String(d.k).padStart(5) + ' | ' + fmt(d.v).padEnd(32) + ' | ' + fmt(d.n));
}
