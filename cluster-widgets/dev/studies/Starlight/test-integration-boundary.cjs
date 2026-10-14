const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../../..');
const status = require('./integration-status.json');
assert.equal(status.developmentOnly, true);
assert.equal(status.hardwareReady, false);
assert.equal(status.installable, false);
for (const p of ['theme.xml']) {
  assert.equal(fs.existsSync(path.resolve(__dirname, p)), false, 'The development study has no installable manifest');
}
const baseline = require('./source-manifest.json').runtime;
const integrationSeams = new Set(['index.html','renderer.js']);
for (const [file, expected] of Object.entries(baseline)) {
  if (integrationSeams.has(file)) continue;
  const actual = crypto.createHash('sha256').update(fs.readFileSync(path.join(__dirname,file))).digest('hex');
  assert.equal(actual, expected, 'Frozen design runtime must remain identical: ' + file);
}
// Remove exactly the two integration substitutions, then verify the complete
// frozen renderer. This guards the visual path rather than exempting the file.
const renderer = fs.readFileSync(path.join(__dirname,'renderer.js'),'utf8')
  .replace('const s=options.state||panelState(t,mode);if(!options.state)applyMenuReadouts(s,options.menuState);',
    'const s=panelState(t,mode);applyMenuReadouts(s,options.menuState);')
  .replace('GalaxyStars.render(ctx,{time:t,...(options.starMotion||starMotion(t,mode))})',
    'GalaxyStars.render(ctx,{time:t,...starMotion(t,mode)})');
assert.equal(crypto.createHash('sha256').update(renderer).digest('hex'),baseline['renderer.js'],
  'No visual renderer changes beyond the explicit contract-input seam');
const scales = require('./scales');
function inZone(p,z) { return p.x>=z.x && p.x<z.x+z.width && p.y>=z.y && p.y<z.y+z.height; }
// Regression evidence, deliberately asserting known conflicts rather than
// passing a mock-overlay test off as composition clearance.
assert.ok(inZone(scales.leftTicks.find(t=>t.value===120),{x:225,y:475,width:90,height:90}));
assert.ok(inZone(scales.leftTicks.find(t=>t.value===80),{x:330,y:445,width:100,height:100}));
assert.ok(inZone({x:960,y:308},{x:520,y:120,width:880,height:530}));
const lab = fs.readFileSync(path.join(root, 'index.html'),'utf8');
assert.ok(lab.includes('/dev/studies/Starlight/index.html'));
for (const file of ['index.html','contract-lab.html']) {
  const html = fs.readFileSync(path.join(__dirname,file),'utf8');
  assert.ok(html.includes('html:not(.starlight-browser-lab)') || html.includes('main{display:none}'));
}
const nativeWindow = {};
let nativeReads = 0;
Object.defineProperty(nativeWindow,'Android',{get(){nativeReads++;throw new Error('Native bridge must remain untouched');}});
const visualEntry = fs.readFileSync(path.join(__dirname,'index.html'),'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
vm.runInNewContext(visualEntry,{window:nativeWindow,document:{},PreviewController:{create(){throw new Error('Native host must not bootstrap');}}});
vm.runInNewContext(fs.readFileSync(path.join(__dirname,'contract-lab.js'),'utf8'),{window:nativeWindow,document:{}});
assert.equal(nativeReads,0);
assert.equal(nativeWindow.impulsePreview,undefined);
assert.equal(nativeWindow.starlightContractLab,undefined);
import(path.join(root,'dev/theme-catalog.mjs')).then(({createThemeCatalog})=>{
  // Obsidian is a separate installable theme and may land independently. This
  // boundary applies only to our development study, not the rest of the catalog.
  assert.equal(createThemeCatalog(root).some(t=>t.folder.startsWith('dev/studies/')),false);
  console.log('Development boundary passed: frozen study modules, no study manifest/catalog entry; standalone themes remain separate');
});
