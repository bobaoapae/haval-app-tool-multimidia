/* Node-only controller/DOM contract QA. This is not a browser or WebView test. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const Controller = require('./preview-controller');
const State = require('./menu-state');
const Apex = require('./apex-data');
let assertions = 0;
const equal = (actual, expected, label) => { assert.equal(actual, expected, label); assertions++; };
const same = (actual, expected, label) => { assert.deepEqual(actual, expected, label); assertions++; };
const ok = (actual, label) => { assert.ok(actual, label); assertions++; };

class NodeFixture {
  constructor(tagName, doc) {
    this.tagName = tagName.toUpperCase(); this.ownerDocument = doc;
    this.children = []; this.parentElement = null; this.attributes = {};
    this.events = {}; this._text = ''; this.hidden = false; this.disabled = false;
    this.value = ''; this.width = 1920; this.height = 720;
  }
  addEventListener(type, handler) { (this.events[type] ||= []).push(handler); }
  removeEventListener(type, handler) { this.events[type] = (this.events[type] || []).filter(item => item !== handler); }
  emit(type, values = {}) {
    const event = Object.assign({type,target:this,defaultPrevented:false,preventDefault() { this.defaultPrevented = true; }}, values);
    (this.events[type] || []).slice().forEach(handler => handler(event)); return event;
  }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  getAttribute(name) { return this.attributes[name] ?? null; }
  hasAttribute(name) { return Object.hasOwn(this.attributes,name); }
  append(...nodes) { for (const node of nodes) { node.parentElement = this; this.children.push(node); } }
  replaceChildren(...nodes) { this.children.forEach(node => { node.parentElement = null; }); this.children = []; this._text = ''; this.append(...nodes); }
  contains(node) { for (; node; node = node.parentElement) if (node === this) return true; return false; }
  set textContent(value) { this._text = String(value); this.children.forEach(node => { node.parentElement = null; }); this.children = []; }
  get textContent() { return this._text + this.children.map(node => node.textContent).join(''); }
  focus() { if (!this.disabled && this.ownerDocument.activeElement !== this) { this.ownerDocument.activeElement = this; this.emit('focus'); } }
  click() { if (!this.disabled) this.emit('click'); }
  getContext() { return {}; }
  getBoundingClientRect() { return {left:10,top:20,width:960,height:360}; }
}
function fixture(reducedMotion = false) {
  const doc = new NodeFixture('document'); doc.ownerDocument = doc; doc.hidden = false;
  doc.body = new NodeFixture('body',doc); doc.activeElement = doc.body;
  const elements = {};
  doc.getElementById = id => elements[id] || null;
  doc.createElement = tag => new NodeFixture(tag,doc);
  const html = fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
  for (const id of Controller.requiredIds) {
    const match = html.match(new RegExp('<([a-z][a-z0-9]*)\\b[^>]*\\bid="' + id + '"[^>]*>'));
    assert.ok(match,`HTML includes ${id}`);
    const node = elements[id] = doc.createElement(match[1]); node.id = id; doc.body.append(node);
    if (/\bhidden\b/.test(match[0])) node.hidden = true;
    if (/\bdisabled\b/.test(match[0])) node.disabled = true;
  }
  function reparent(child,parent) {
    const node = elements[child]; node.parentElement.children = node.parentElement.children.filter(item => item !== node);
    elements[parent].append(node);
  }
  ['menu-title','menu-main','menu-settings','menu-info','menu-graphs','menu-trips','menu-ac','menu-hint','key-up','key-down','key-enter'].forEach(id => reparent(id,'menu-shell'));
  ['tabs-info','stats-info'].forEach(id => reparent(id,'menu-info'));
  ['tabs-graph','graph-reading','graph-history','graph-range'].forEach(id => reparent(id,'menu-graphs'));
  ['tabs-trip','stats-trip','trip-reset','trip-reset-hint'].forEach(id => reparent(id,'menu-trips'));
  ['ac-fields','stats-ac','ac-adjust-label','ac-decrease','ac-increase','ac-auto','ac-recycle'].forEach(id => reparent(id,'menu-ac'));
  elements.mode.value = 'motion'; elements.data.value = 'demo';
  const media = new NodeFixture('media',doc); media.matches = reducedMotion;
  let nextId = 0, profile = 'demo';
  const scheduled = new Map(), paints = [], menuPaints = [], hits = [];
  const win = {
    document:doc,matchMedia:() => media,
    requestAnimationFrame:handler => { scheduled.set(++nextId,handler); return nextId; },
    cancelAnimationFrame:id => scheduled.delete(id)
  };
  const motion = {
    setDataProfile:value => { profile = value; },duration:mode => mode === 'drive' ? 16 : 13,
    panelState:(time,mode) => ({speed:profile === 'demo' ? 72+time : null,speedRaw:72+time,batteryPowerKw:profile === 'demo' ? 30+time : null,powerKw:30+time,mode}),
    renderPanel:(ctx,time,mode,options) => { const sample = motion.panelState(time,mode); paints.push({time,mode,options,sample}); return sample; }
  };
  let hitAction = null;
  const menus = {render:(ctx,state,panel) => menuPaints.push({state,panel}),hitTest:(state,x,y) => { hits.push({state,x,y}); return hitAction; }};
  const app = Controller.create({window:win,document:doc,motion,stateApi:State,menus,apex:Apex});
  return {
    doc,elements,media,scheduled,paints,menuPaints,hits,app,
    flush(now) { const frames = [...scheduled.values()]; scheduled.clear(); frames.forEach(handler => handler(now)); },
    key(key,target = doc.activeElement,extra = {}) { return doc.emit('keydown',{key,target,...extra}); },
    row(host,index) { return elements[host].children[index]; },
    hit(action) { hitAction = action; },
    profile() { return profile; }
  };
}

equal(Controller.clampTime(-2,16),0);
equal(Controller.clampTime(50,16),16);
equal(Controller.clampTime('bad',16),0);
equal(Controller.timeLabel(1.5,16),'1,50 / 16,00 s');
same(Controller.dynamicTelemetry({speed:84,batteryPowerKw:-12},false),{speed:84,voltage:400,current:-30});
same(Controller.dynamicTelemetry({speed:84,batteryPowerKw:-12},true),{speed:null,voltage:null,current:null});
same(Controller.dynamicTelemetry({speed:NaN,batteryPowerKw:Infinity},false),{speed:null,voltage:null,current:null});

const f = fixture(true), {app,elements:e,doc} = f;
equal(app.getPlayback().running,false,'reduced motion starts paused');
equal(f.scheduled.size,0,'no hidden animation work when paused');
equal(e['menu-shell'].hidden,true);
equal(e['menu-close'].disabled,true);
equal(f.menuPaints.at(-1).panel,f.paints.at(-1).sample,'full panel state passed to menu renderer');
equal(f.paints.at(-1).options.menuState,app.getState(),'renderer receives simulated menu state');
equal(app.getState().data.tripADistance,56.7,'initial menu and panel use same canonical demo telemetry');

e['open-main'].click();
equal(State.getView(app.getState()),'menu');
equal(e['menu-shell'].hidden,false);
equal(e['open-main'].getAttribute('aria-expanded'),'true');
equal(e['menu-main'].children.length,4,'all source main menu rows are real DOM buttons');
equal(doc.activeElement,f.row('menu-main',0),'opening focuses selected row');
equal(f.key('ArrowDown').defaultPrevented,true);
equal(app.getState().menuIndex,1);
equal(doc.activeElement,f.row('menu-main',1),'keyboard selection moves native focus');
equal(f.key('Enter').defaultPrevented,false,'native row Enter is not double-activated');
f.row('menu-main',1).click();
equal(State.getView(app.getState()),'graphs');
equal(doc.activeElement,f.row('tabs-graph',0));
f.key('ArrowRight');
equal(app.getState().graphIndex,1);
equal(doc.activeElement,f.row('tabs-graph',1));
ok(e['graph-reading'].textContent.includes('km/h'));
ok(e['graph-range'].textContent.includes('200'),'speed graph reflects current source-adapted domain');
f.key('Escape');
equal(State.getView(app.getState()),'menu');
equal(doc.activeElement,f.row('menu-main',1),'back restores main row focus');
f.key('Escape');
equal(app.getState().card,0);
equal(doc.activeElement,e['open-main'],'close restores opener focus');
app.close();
equal(app.getState().card,0,'repeated close is harmless');

e.panel.focus();
f.key('Enter');
equal(State.getView(app.getState()),'menu','canvas Enter opens main menu');
equal(doc.activeElement,e.panel,'canvas keyboard operation retains canvas focus');
f.row('menu-main',2).focus();
equal(app.getState().menuIndex,2,'native Tab focus synchronizes canvas selection');
f.row('menu-main',2).click();
equal(State.getView(app.getState()),'settings');
equal(e['menu-settings'].children.length,6);
const previousDrive = app.getState().data.driveMode;
f.row('menu-settings',0).click();
ok(app.getState().data.driveMode !== previousDrive,'setting action cycles simulated value');
const simulatedDrive = app.getState().data.driveMode;
app.seek(4);
equal(app.getState().data.driveMode,simulatedDrive,'telemetry paint does not overwrite simulated settings');
f.row('menu-settings',4).focus();
equal(app.getState().settingIndex,4,'setting focus does not change values');
f.key('ArrowDown');
equal(app.getState().settingIndex,5);
equal(doc.activeElement,f.row('menu-settings',5),'disabled source-specific row remains inspectable');
equal(f.row('menu-settings',5).getAttribute('aria-disabled'),'true');
const beforeDisplay = app.getState().lastCommand;
f.row('menu-settings',5).click();
equal(app.getState().lastCommand,beforeDisplay,'disabled Display never issues a simulated write');
ok(e['menu-status'].textContent.includes('Contour / Vector'));
e['menu-back'].click();
equal(State.getView(app.getState()),'menu');
equal(doc.activeElement,f.row('menu-main',2));

e['open-ac'].click();
equal(State.getView(app.getState()),'ac');
equal(e['open-main'].getAttribute('aria-expanded'),'false');
equal(e['open-ac'].getAttribute('aria-expanded'),'true');
equal(doc.activeElement,f.row('ac-fields',1));
equal(e['menu-back'].disabled,true,'source short-back is not misleadingly offered on AC root');
const fan = app.getState().data.acFan;
f.key('ArrowUp');
equal(app.getState().data.acFan,fan+1);
f.key('ArrowLeft');
equal(app.getState().acFocus,'temp');
equal(doc.activeElement,f.row('ac-fields',0));
const temp = app.getState().data.acTemp;
e['ac-increase'].click();
equal(app.getState().data.acTemp,temp+.5);
ok(e['menu-status'].textContent.includes('22,5°C'),'live feedback includes changed value');
e['ac-increase'].click();
ok(e['menu-status'].textContent.includes('23°C'),'repeated changes update live feedback');
const auto = app.getState().data.acAuto;
e['ac-auto'].click();
equal(app.getState().data.acAuto,1-auto);
const recycle = app.getState().data.acRecycle;
e['ac-recycle'].click();
equal(app.getState().data.acRecycle,1-recycle);
app.dispatch({type:'TELEMETRY',values:{acTemp:32}});
equal(e['ac-increase'].disabled,true,'upper AC bound disables increase');
app.dispatch({type:'TELEMETRY',values:{acTemp:16}});
equal(e['ac-decrease'].disabled,true,'lower AC bound disables decrease');
app.dispatch({type:'TELEMETRY',values:{acTemp:31.5}});
e['ac-increase'].focus(); e['ac-increase'].click();
equal(doc.activeElement,f.row('ac-fields',0),'focus leaves action button when adjustment disables it at bound');
e['menu-close'].click();
equal(doc.activeElement,e['open-ac']);

app.openMain();
const stateBeforeNativeKeys = app.getState().menuIndex;
for (const target of [e.mode,e.data,e.time,e.toggle,e.restart,e['open-ac']]) {
  for (const key of ['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Enter','Escape',' ']) equal(f.key(key,target).defaultPrevented,false,'native control keeps '+key);
}
equal(app.getState().menuIndex,stateBeforeNativeKeys);
const editor = doc.createElement('div'); editor.setAttribute('contenteditable','true');
equal(f.key('ArrowDown',editor).defaultPrevented,false);
equal(f.key('ArrowDown',e.panel,{ctrlKey:true}).defaultPrevented,false);
equal(f.key('ArrowDown',e.panel,{isComposing:true}).defaultPrevented,false);
equal(f.key('Enter',e.panel,{repeat:true}).defaultPrevented,false);

f.row('menu-main',0).click();
equal(State.getView(app.getState()),'info');
ok(e['stats-info'].textContent.includes('18.654'));
f.row('tabs-info',1).click();
ok(e['stats-info'].textContent.includes('PSI'));
e['menu-back'].click();
f.row('menu-main',3).click();
equal(State.getView(app.getState()),'trips');
equal(e['trip-reset'].hidden,true,'Trip A has no manual reset action');
f.row('tabs-trip',1).click();
equal(e['trip-reset'].hidden,false);
app.dispatch({type:'TELEMETRY',values:{tripBTime:null}});
equal(e['trip-reset'].disabled,true,'partial Trip B data cannot be reset');
app.dispatch({type:'TELEMETRY',values:{tripBTime:1417}});
equal(e['trip-reset'].disabled,false);
e['trip-reset'].click();
equal(app.getState().data.tripBDistance,0);
ok(e['stats-trip'].textContent.includes('0,0 km'));

e.data.value = 'unavailable'; e.data.emit('change');
equal(f.profile(),'unavailable');
equal(State.getView(app.getState()),'trips','profile switch preserves current menu navigation');
equal(app.getState().data.tripBDistance,null);
equal(e['trip-reset'].disabled,true);
const missing = JSON.stringify(app.getState().data);
f.key('Enter',e.panel,{shiftKey:true});
equal(JSON.stringify(app.getState().data),missing,'keyboard reset cannot fabricate unavailable data');
app.openAC();
equal(e['ac-increase'].disabled,true);
equal(e['ac-decrease'].disabled,true);
equal(e['ac-auto'].disabled,true);
equal(e['ac-recycle'].disabled,true);
ok(e['ac-fields'].textContent.includes('--'));
f.key('ArrowUp',e.panel);
equal(app.getState().data.acFan,null);
app.openMain(); f.row('menu-main',2).click();
equal(f.row('menu-settings',0).getAttribute('aria-disabled'),'true');
f.row('menu-settings',0).click();
equal(app.getState().data.driveMode,null);
e.data.value = 'demo'; e.data.emit('change');
equal(app.getState().data.driveMode,1);
equal(f.row('menu-settings',0).getAttribute('aria-disabled'),'false');

f.hit({type:'OPEN',section:'info'});
e.panel.emit('click',{clientX:60,clientY:70});
equal(State.getView(app.getState()),'info','canvas hitTest action routed through same reducer');
equal(f.hits.at(-1).x,100,'CSS-scaled canvas pointer uses native panel coordinates');
equal(f.hits.at(-1).y,100);
equal(doc.activeElement,e.panel);

app.seek(99);
equal(app.getPlayback().time,13);
equal(app.getPlayback().running,false);
e.toggle.click();
equal(app.getPlayback().time,0,'play at end restarts');
equal(app.getPlayback().running,true,'explicit play allowed under reduced motion');
equal(f.scheduled.size,1);
f.flush(1000); f.flush(1250);
equal(app.getPlayback().time,.25);
doc.hidden = true; doc.emit('visibilitychange');
equal(f.scheduled.size,0);
doc.hidden = false; doc.emit('visibilitychange');
f.flush(10000);
equal(app.getPlayback().time,.25,'hidden duration does not jump playback');
f.flush(10250);
equal(app.getPlayback().time,.5);
e.time.value = '6'; e.time.emit('input');
equal(app.getPlayback().time,6);
equal(app.getPlayback().running,false);
equal(f.scheduled.size,0);
e.restart.click();
equal(app.getPlayback().time,0);
equal(app.getPlayback().running,false,'restart respects paused/reduced-motion state');
e.mode.value = 'drive'; e.mode.emit('change');
equal(e.time.max,'16');
equal(app.getPlayback().time,0);
app.seek(12); app.seek(2);
ok(app.getState().history.speed.length <= 1,'rewind clears graph history from future timestamps');
e.toggle.click(); f.flush(0); f.flush(20000);
equal(app.getPlayback().time,16);
equal(app.getPlayback().running,false,'playback stops at duration');
equal(f.scheduled.size,0);

const g = fixture(false);
equal(g.app.getPlayback().running,true,'default motion preference starts playback');
equal(g.scheduled.size,1);
g.media.matches = true; g.media.emit('change');
equal(g.app.getPlayback().running,false,'new reduced-motion preference pauses');
equal(g.scheduled.size,0);
g.media.matches = false; g.media.emit('change');
equal(g.app.getPlayback().running,false,'preference change does not unexpectedly restart');
g.elements.toggle.click();
equal(g.scheduled.size,1);
g.app.destroy();
equal(g.scheduled.size,0,'destroy cancels pending frame');
g.elements['open-main'].click();
equal(g.app.getState().card,0,'destroy detaches event listeners');
g.app.destroy(); app.destroy();

const html = fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
const source = fs.readFileSync(path.join(__dirname,'preview-controller.js'),'utf8');
const scripts = [...html.matchAll(/<script src="([^"]+)"/g)].map(match => match[1]);
same(scripts,['apex-data.js','scales.js','galaxy-stars.js','glass-smoke.js','menu-state.js','renderer.js','theme-menus.js','preview-controller.js']);
ok(scripts.every(file => !/^(https?:|\/\/)/.test(file)),'scripts load locally without network');
for (const file of scripts) ok(fs.existsSync(path.join(__dirname,file)),'local script exists: '+file);
for (const match of html.matchAll(/src:url\('([^']+)'\)/g)) ok(fs.existsSync(path.join(__dirname,match[1])),'local font exists: '+match[1]);
new vm.Script(source); assertions++;
new vm.Script(html.match(/<script>([\s\S]*?)<\/script>/)[1]); assertions++;
ok(!/\b(fetch|XMLHttpRequest|WebSocket|EventSource|localStorage|sessionStorage)\s*\(/.test(source),'controller has no network or persisted storage APIs');
ok(!/\b(Android|CarBridge|jsBridge)\s*\./.test(source),'controller has no vehicle bridge API');
ok(html.indexOf('<canvas') < html.indexOf('class="controls"'),'controls and branding remain below panel');
ok(html.includes('prefers-reduced-motion:reduce'));

const report = {result:'PASS',assertions,scope:'Node controller helpers and explicit DOM fixture; not a real browser',
  covers:['open/back/close and focus restoration','all four source main-menu sections','settings simulation and disabled Display',
    'AC selection, fan/temperature, Auto, recirculation and bounds','unavailable data','native control keyboard isolation',
    'Canvas coordinate mapping','playback, seeking, hidden tab and reduced motion','local-only script/font references'],
  browser_verified:false,webview_verified:false,vehicle_verified:false};
fs.mkdirSync(path.join(__dirname,'qa'),{recursive:true});
fs.writeFileSync(path.join(__dirname,'qa/preview-controller-validation.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));
