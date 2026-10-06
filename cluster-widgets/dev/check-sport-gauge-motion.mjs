import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  isSportGaugeMotionPath,
  pauseHiddenSportCanvas,
  tuneSportGaugeMotion,
} from './sport-gauge-motion.mjs';

const clusterWidgetsRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

assert.equal(isSportGaugeMotionPath('/Themes/SportRed/index.html'), true);
assert.equal(isSportGaugeMotionPath('/Themes/SportRedLite/index.html'), true);
assert.equal(isSportGaugeMotionPath('/Themes/Default/app.html'), false);

for (const folder of ['SportRed', 'SportRedLite']) {
  const original = fs.readFileSync(path.join(clusterWidgetsRoot, 'Themes', folder, 'index.html'), 'utf8');
  const result = tuneSportGaugeMotion(original);
  assert.equal(result.tuned, true, `${folder} deve corresponder exatamente ao bundle 0.16.44`);
  assert.match(result.source, /Math\.exp\(-i\/100\)/);
  assert.match(result.source, /transition:x1 34ms linear,y1 34ms linear/);
  assert.doesNotMatch(result.source, /Math\.exp\(-i\/180\)/);
  assert.doesNotMatch(result.source, /Math\.abs\(eU\.speed-eK\)>=30/);
  assert.doesNotMatch(result.source, /Math\.abs\(eU\.powerKw-ew\)>=80/);

  const canvasResult = pauseHiddenSportCanvas(result.source);
  assert.equal(canvasResult.paused, true);
  assert.match(
    canvasResult.source,
    /if\(!P\.closest\("\.display-normal"\)\)\{f=requestAnimationFrame\(A\);return\}/,
  );
  const repeatedCanvas = pauseHiddenSportCanvas(canvasResult.source);
  assert.equal(repeatedCanvas.paused, false);
  assert.equal(repeatedCanvas.source, canvasResult.source);

  const repeated = tuneSportGaugeMotion(result.source);
  assert.equal(repeated.tuned, false);
  assert.equal(repeated.source, result.source);
}

const incomplete =
  'Math.exp(-i/180);Math.abs(eU.speed-eK)>=30&&(eK=eU.speed),' +
  'transition:x1 80ms linear,y1 80ms linear,x2 80ms linear,y2 80ms linear';
assert.deepEqual(tuneSportGaugeMotion(incomplete), { source: incomplete, tuned: false });
assert.deepEqual(pauseHiddenSportCanvas('!function A(){s.clearRect(0,0,500,500)}'), {
  source: '!function A(){s.clearRect(0,0,500,500)}',
  paused: false,
});

console.log(
  'Movimento Sport válido: 30 fps preservados e canvas Normal pausado fora do display ativo.',
);
