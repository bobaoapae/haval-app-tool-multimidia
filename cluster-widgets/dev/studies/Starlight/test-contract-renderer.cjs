const assert = require('node:assert/strict');
const { createCanvas, GlobalFonts } = require('@napi-rs/canvas');
const path = require('node:path');
const R = require('./renderer');
const C = require('./contract-renderer');
const A = require('./apex-data');
for (const [file, name] of [['ScaleDigits','Scale Digits'],['SpeedDigits','Speed Digits'],['PanelSans','Panel Sans'],['HeaderSans','Header Sans'],['LabelSans','Label Sans']]) {
  GlobalFonts.registerFromPath(path.join(__dirname, 'assets', file + '.ttf'), name);
}
R.setCanvasFactory(createCanvas);
const canvas = createCanvas(1920, 720), ctx = canvas.getContext('2d');
const text = [], fillText = ctx.fillText.bind(ctx);
ctx.fillText = function (label, ...args) { text.push(String(label)); return fillText(label, ...args); };
function decoded(speed, current, voltage = 400) {
  return A.decode({[A.KEYS.speed]:speed,[A.KEYS.voltage]:voltage,[A.KEYS.current]:current}, {clockText:'12:34'});
}
for (const speed of [null, 0, 83, 231]) {
  for (const current of [null, 0, 100, -100]) {
    const d = decoded(speed, current);
    for (const time of [0, 5, 42]) {
      text.length = 0;
      const s = C.render(ctx, d, {time});
      assert.equal(s.speed, speed);
      assert.equal(s.batteryPowerKw, current === null ? null : current * .4);
      assert.equal(s.left, R.Scale.speedRatio(speed) * 8);
      assert.equal(s.right, R.Scale.batteryRatio(current === null ? 0 : Math.abs(current * .4)) * 8);
      assert.ok(text.includes(speed === null ? '--' : String(speed)), 'numeric reading must use explicit fixture');
      assert.equal(s.regen, current !== null && current < 0);
    }
  }
}
assert.equal(C.state(decoded(83, -100, null)).batteryPowerKw, null);
assert.equal(C.state(decoded(231, 0)).speed, 231);
assert.equal(C.state(decoded(231, 0)).left, 8);
console.log('Contract renderer: 48 explicit input/time combinations; null, zero, over-scale and independent battery power passed');
