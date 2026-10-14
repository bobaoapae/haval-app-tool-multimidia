'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createCanvas } = require('@napi-rs/canvas');
const source = path.join(__dirname,'../src');
const Apex = require(path.join(source,'apex-data.js'));
const Adapter = require(path.join(source,'contract-adapter.js'));
const Menus = require(path.join(source,'menu-state.js'));
const k = Apex.KEYS;
let passed = 0;
function test(name,body) { body(); passed += 1; console.log('PASS '+name); }

function surface() {
  const elements = Object.create(null), raf = new Map(), timers = new Map();
  let serial = 0, at = 0;
  class Target {
    constructor() { this.handlers = new Map(); this.hidden = false; this.style = {}; this.textContent = ''; }
    addEventListener(name,fn) { if (!this.handlers.has(name)) this.handlers.set(name,new Set()); this.handlers.get(name).add(fn); }
    removeEventListener(name,fn) { if (this.handlers.has(name)) this.handlers.get(name).delete(fn); }
    emit(name,event={}) { if (this.handlers.has(name)) [...this.handlers.get(name)].forEach(fn=>fn(event)); }
    count() { return [...this.handlers.values()].reduce((n,list)=>n+list.size,0); }
  }
  const doc = new Target(), win = new Target(), canvas = createCanvas(1920,720);
  doc.getElementById = id => elements[id] || null;
  ['starlight','starlight-canvas','native-readouts','native-speed','native-power','native-rpm','native-fuel','native-fuel-liters','native-battery','native-gear','native-drive-mode','native-propulsion-mode','native-inside-temp','native-outside-temp','native-odometer','native-clock'].forEach(id=>elements[id]=new Target());
  elements['starlight-canvas'].getContext = ()=>canvas.getContext('2d');
  win.document = doc; win.window = win;
  win.performance = {now:()=>at};
  win.requestAnimationFrame = fn => { const id=++serial; raf.set(id,fn); return id; };
  win.cancelAnimationFrame = id=>raf.delete(id);
  win.setTimeout = (fn,ms)=>{const id=++serial;timers.set(id,{fn,due:at+ms,period:0});return id;};
  win.clearTimeout = id=>timers.delete(id);
  win.setInterval = (fn,ms)=>{const id=++serial;timers.set(id,{fn,due:at+ms,period:ms});return id;};
  win.clearInterval = id=>timers.delete(id);
  win.matchMedia = ()=>({matches:false});
  const draws = [];
  win.MotionStudy = {
    functionalState(input) { return {functional:true,speed:input.speedRaw,speedRaw:input.speedRaw,powerKw:input.powerKw,batteryPowerKw:input.batteryPowerKw,batteryMagnitudeKw:Math.abs(input.batteryPowerKw||0),regen:input.batteryPowerKw<0,batteryState:input.batteryPowerKw===null?'SEM DADOS':input.batteryPowerKw<0?'REGENERANDO':input.batteryPowerKw>0?'CONSUMINDO':'NEUTRA',leftLength:70+Math.min(200,input.speedRaw||0)*3,rightLength:70+Math.min(100,Math.abs(input.batteryPowerKw||0))*3,tractionKw:input.tractionKw,left:Math.min(200,input.speedRaw||0)/200*8}; },
    render(ctx,time,mode,options) { draws.push({time,mode,options}); ctx.fillStyle='#abcdef';ctx.fillRect(0,0,1920,720); },
    cleanup() { this.cleanups=(this.cleanups||0)+1; },reset() {},setMaterialHz(value) { this.hz=value; }
  };
  const menuDraws = [];
  win.ThemeMenus = {render(ctx,menu) { menuDraws.push(menu.card); if(menu.card===1||menu.card===3){ctx.fillStyle='#aabbcc';ctx.fillRect(800,600,1,1);} }};
  const tick = ms => {
    at += ms;
    [...timers].forEach(([id,timer])=>{ if(timer.due<=at && timers.has(id)) { if(timer.period) timer.due=at+timer.period;else timers.delete(id);timer.fn(); } });
    const batch = [...raf];raf.clear();batch.forEach(([,fn])=>fn(at));
  };
  return {win,doc,elements,canvas,raf,timers,draws,menuDraws,tick,at:()=>at};
}
function boot(values={},options={}) {
  const env = surface(), calls = [], available = options.available || Object.keys(k).map(field=>k[field]).concat(Adapter.preferenceKeys,Adapter.projectionKeys);
  if(options.workers){
    env.workers=[];env.uiDraws=[];env.revoked=[];
    for(const id of ['starlight-background','starlight-material'])env.elements[id]={hidden:true,style:{},transfers:0,transferControlToOffscreen(){this.transfers+=1;return {layer:id};}};
    env.win.Worker=class {constructor(){this.messages=[];this.terminated=0;env.workers.push(this);}postMessage(data,transfers){this.messages.push({data,transfers});}terminate(){this.terminated+=1;}emit(data){if(this.onmessage)this.onmessage({data});}};
    env.win.OffscreenCanvas=function(){};env.win.Blob=Blob;
    env.win.URL=class extends URL{};env.win.URL.createObjectURL=()=> 'blob:runtime-workers';env.win.URL.revokeObjectURL=url=>env.revoked.push(url);env.win.StarlightWorkerSource='offline source';
    env.win.MotionStudy.renderForeground=(ctx,time,mode,options)=>{env.uiDraws.push({time,mode,options});ctx.clearRect(0,0,1920,720);ctx.fillStyle='#ffffff';ctx.fillRect(960,308,1,1);};
    env.workerFrame=index=>env.workers[index].messages.filter(item=>item.data.type==='frame').at(-1).data;
    env.workerAck=(index,frame=env.workerFrame(index))=>env.workers[index].emit({type:'frame',generation:frame.generation,sequence:frame.sequence,painted:true,costMs:4});
    env.warmWorkers=()=>{
      env.workers.forEach(worker=>worker.emit({type:'ready',generation:worker.messages.find(item=>item.data.type==='probe').data.generation,probeBitmap:{close(){}}}));
      env.workers.forEach(worker=>worker.emit({type:'bound',generation:worker.messages.find(item=>item.data.type==='bind').data.generation}));
      env.tick(40);env.workerAck(0);env.workerAck(1);env.tick(40);
    };
  }
  if(options.fontReady){env.doc.fonts={ready:{then(resolve){env.resolveFonts=resolve;}}};}
  const snapshot = Object.assign({},values);
  const bridge = {
    getPreference(name,fallback) { calls.push(['pref',name]); return options.preferences && Object.prototype.hasOwnProperty.call(options.preferences,name) ? String(options.preferences[name]) : fallback; },
    getAvailableKeys() { return JSON.stringify(available); },
    subscribe(json) { calls.push(['subscribe',JSON.parse(json)]); if(options.subscribe) options.subscribe(env.win,JSON.parse(json)); },
    getCarData(key) { calls.push(['read',key]); if(options.read) options.read(env.win,key); return Object.prototype.hasOwnProperty.call(snapshot,key) ? snapshot[key] : null; },
    unsubscribe(json) {calls.push(['unsubscribe',JSON.parse(json)]);},
    heartbeat() { calls.push(['heartbeat']); },
    updateCarData(key,value) { calls.push(['write',key,value]); if(options.ack) env.win.onDataChanged(key,value); },
    triggerSystemAction(action) { calls.push(['action',action]); if(action==='RESET_DRIVE_INFO' && options.ack) [k.tripB,k.tripBTime,k.tripBConsumption,k.tripBAvgSpeed].forEach(key=>env.win.onDataChanged(key,'0')); }
  };
  if(!options.noBridge) env.win.Android=bridge;
  const context = vm.createContext(env.win);
  ['apex-data.js','menu-state.js','contract-adapter.js','material-pipeline.js','host-runtime.js'].forEach(file=>vm.runInContext(fs.readFileSync(path.join(source,file),'utf8'),context,{filename:file}));
  const runtime = env.win.starlightRuntime;
  const press = key=>{env.tick(100);return env.win.onKeyEvent(key);};
  return Object.assign(env,{runtime,calls,bridge,press});
}

test('pure decoder preserves absent/zero, signed power and valid above-scale speed',()=>{
  const absent = Apex.decode({}, {tankCapacityL:55});
  assert.equal(absent.speed,null);assert.equal(absent.powerKw,null);assert.equal(absent.fuelLiters,null);assert.equal(absent.showRpm,false);
  const zero=Apex.decode({[k.speed]:0,[k.voltage]:400,[k.current]:0,[k.fuelPct]:0,[k.batteryPct]:0,[k.rpm]:0},{tankCapacityL:55});
  assert.equal(zero.speed,0);assert.equal(zero.powerKw,0);assert.equal(zero.fuelLiters,0);assert.equal(zero.batteryPct,0);assert.equal(zero.showRpm,false);
  assert.equal(Apex.decode({[k.speed]:255,[k.voltage]:400,[k.current]:-200}).powerKw,-80);
  assert.equal(Apex.decode({[k.speed]:255}).speed,255);
  assert.equal(Apex.decode({[k.speed]:'',[k.fuelPct]:101,[k.insideTemp]:255,[k.outsideTemp]:-1}).speed,null);
});
test('hooks precede subscription; filter keys and retain synchronous pushes over snapshot',()=>{
  const env=boot({[k.speed]:20,[k.voltage]:400,[k.current]:100},{available:[k.speed,k.voltage,k.current],subscribe(win,keys){
    ['control','onDataChanged','onCardChanged','onKeyEvent','focus','showScreen','cleanup','updateWarning'].forEach(name=>assert.equal(typeof win[name],'function'));
    win.onDataChanged(k.speed,'100');
  }});
  assert.deepEqual([...env.calls.find(c=>c[0]==='subscribe')[1]],[k.speed,k.voltage,k.current]);
  assert.equal(env.calls.some(c=>c[0]==='read'&&c[1]===k.speed),false);
  assert.equal(env.runtime.getState().decoded.speed,106);
  assert.equal(env.runtime.getState().decoded.powerKw,40);
  env.runtime.cleanup();
});
test('reentrant getCarData push wins; preference keys never use vehicle snapshot',()=>{
  const env=boot({[k.speed]:20},{read(win,key){if(key===k.speed)win.onDataChanged(k.speed,'160');}});
  assert.equal(env.runtime.getState().decoded.speed,171);
  assert.equal(env.calls.some(c=>c[0]==='read'&&Adapter.preferenceKeys.includes(c[1])),false);
  env.runtime.cleanup();
});
test('canonical telemetry owns aliases including missing values; no duplicate speed calibration',()=>{
  const env=boot({}, {available:[]});
  env.win.control('carSpeed','120');assert.equal(env.runtime.getState().decoded.speed,120);
  env.win.onDataChanged(k.speed,'120');assert.equal(env.runtime.getState().decoded.speed,128);
  env.win.control('carSpeed','200');assert.equal(env.runtime.getState().decoded.speed,128);
  env.win.onDataChanged(k.speed,null);env.win.control('carSpeed','100');assert.equal(env.runtime.getState().decoded.speed,null);
  env.win.control('gearState','D');assert.equal(env.runtime.getState().decoded.gear,'D');
  env.win.onDataChanged(k.gear,3);env.win.control('gearState','D');assert.equal(env.runtime.getState().decoded.gear,'P');
  env.runtime.cleanup();
});
test('existing HUD calibration and adjustment preferences preserve values above200',()=>{
  const env=boot({[k.speed]:255},{preferences:{enableSpeedAdjustment:true,speedAdjustmentOffset:5}});
  const expected=Math.floor((255*1.07-(255/180)*.02)*1.05);
  assert.equal(env.runtime.getState().decoded.speed,expected);
  env.tick(40);const visual=env.draws.at(-1).options.state;
  assert.equal(visual.speed,expected);assert.equal(visual.speedRaw,expected);assert.equal(visual.left,8);
  env.win.onDataChanged('app.preferences.speedAdjustmentOffset','10');
  env.win.control('speedAdjustmentOffset','80');assert.equal(env.runtime.getState().decoded.speed,Math.floor((255*1.07-(255/180)*.02)*1.10));
  env.runtime.cleanup();
});
test('real renderer input carries signed power, traction and explicitly integrated stars',()=>{
  const env=boot({[k.speed]:100,[k.voltage]:400,[k.current]:-200});env.tick(40);env.tick(40);
  const draw=env.draws.at(-1);assert.equal(draw.mode,'live');assert.equal(draw.options.state.powerKw,-80);assert.equal(draw.options.state.batteryPowerKw,-80);assert.equal(draw.options.state.tractionKw,0);
  assert.equal(draw.options.starMotion.speed,106);assert.ok(Math.abs(draw.options.starMotion.travel-106*0.0032*0.040)<0.00001);
  env.win.onDataChanged(k.current,'200');env.tick(40);assert.equal(env.draws.at(-1).options.state.powerKw,80);assert.equal(env.draws.at(-1).options.state.tractionKw,80);
  env.runtime.cleanup();
});
test('only host onCardChanged owns cards; local OPEN/CLOSE/CARD cannot write them',()=>{
  const env=boot({[k.driveMode]:0});env.win.control('cardId',1);env.win.onDataChanged('cardId',1);assert.equal(env.runtime.getState().cardId,null);assert.equal(env.press('ENTER'),false);
  env.win.onCardChanged(1);assert.equal(env.runtime.getMenuState().card,1);
  env.win.focus('ac',0);env.win.showScreen('ac');assert.equal(env.runtime.getMenuState().card,1);
  let menu=Menus.createState();assert.equal(Menus.dispatch(menu,{type:'CARD',card:3}).card,0);assert.equal(Menus.dispatch(menu,{type:'OPEN',section:'info'}).card,0);
  menu=Menus.dispatch(menu,{type:'HOST_CARD',card:1});assert.equal(Menus.dispatch(menu,{type:'CLOSE'}).card,1);
  env.win.onCardChanged(0);assert.equal(env.press('ENTER_LONG'),false);assert.equal(env.calls.some(c=>c[0]==='write'),false);env.runtime.cleanup();
});
test('writes use existing allowlist/string payload and await canonical acknowledgment',()=>{
  const env=boot({[k.driveMode]:0});env.win.onCardChanged(1);env.press('DOWN');env.press('DOWN');env.press('ENTER');env.press('ENTER');
  assert.deepEqual(env.calls.filter(c=>c[0]==='write'),[['write',k.driveMode,'2']]);
  assert.equal(env.runtime.getMenuState().data.driveMode,0);assert.equal(env.runtime.getStatus().pendingCommand,true);
  env.press('ENTER');assert.equal(env.calls.filter(c=>c[0]==='write').length,1);
  env.win.control('drivingMode','ECO');assert.equal(env.runtime.getStatus().pendingCommand,true);
  env.win.onDataChanged(k.driveMode,'2');assert.equal(env.runtime.getMenuState().data.driveMode,2);assert.equal(env.runtime.getStatus().pendingCommand,false);
  env.runtime.cleanup();
});
test('AC Auto cancels MAX AC before Auto write; synchronous acknowledgment remains real',()=>{
  const env=boot({[k.hvacAuto]:0,[k.hvacPower]:1,[k.fan]:3,[k.driverTemp]:22},{ack:true});env.win.onCardChanged(3);env.press('ENTER_LONG');
  assert.deepEqual(env.calls.filter(c=>c[0]==='action'||c[0]==='write'),[['action','CANCEL_MAX_AC'],['write',k.hvacAuto,'1']]);
  assert.equal(env.runtime.getMenuState().data.acAuto,1);assert.equal(env.runtime.getStatus().pendingCommand,false);env.runtime.cleanup();
});
test('Trip B reset requires long press with Trip B focused; no fake reset values',()=>{
  const values={[k.tripB]:1286,[k.tripBTime]:1417,[k.tripBConsumption]:6.4,[k.tripBAvgSpeed]:54};
  const env=boot(values);env.win.onCardChanged(1);env.press('DOWN');env.press('DOWN');env.press('DOWN');env.press('ENTER');env.press('ENTER_LONG');assert.equal(env.calls.some(c=>c[0]==='action'),false);
  env.press('DOWN');env.press('ENTER');assert.equal(env.calls.some(c=>c[0]==='action'),false);env.press('ENTER_LONG');
  assert.deepEqual(env.calls.filter(c=>c[0]==='action'),[['action','RESET_DRIVE_INFO']]);assert.equal(env.runtime.getMenuState().data.tripBDistance,1286);assert.equal(env.runtime.getMenuState().data.tripBTime,1417);
  [k.tripB,k.tripBTime,k.tripBConsumption,k.tripBAvgSpeed].forEach(key=>env.win.onDataChanged(key,'0'));assert.equal(env.runtime.getStatus().pendingCommand,false);assert.equal(env.runtime.getMenuState().data.tripBDistance,0);env.runtime.cleanup();
});
function requestTripBReset(env) {
  env.win.onCardChanged(1);env.press('DOWN');env.press('DOWN');env.press('DOWN');env.press('ENTER');env.press('DOWN');env.press('ENTER_LONG');
}
test('TripB reset needs four fresh canonicalzeros, rejecting stale/null/repeatedpartial updates',()=>{
  const values={[k.tripB]:1286,[k.tripBTime]:1417,[k.tripBConsumption]:6.4,[k.tripBAvgSpeed]:54};
  const env=boot(values);requestTripBReset(env);const keys=[k.tripB,k.tripBTime,k.tripBConsumption,k.tripBAvgSpeed];
  keys.forEach(key=>env.win.onDataChanged(key,String(values[key])));assert.equal(env.runtime.getStatus().pendingCommand,true);assert.equal(env.runtime.getMenuState().data.tripBDistance,1286);
  keys.forEach(key=>env.win.onDataChanged(key,null));assert.equal(env.runtime.getStatus().pendingCommand,true);assert.equal(env.runtime.getMenuState().data.tripBDistance,null);
  for(let i=0;i<4;i++)env.win.onDataChanged(k.tripB,'0');assert.equal(env.runtime.getStatus().pendingCommand,true);
  env.win.onDataChanged(k.tripBTime,'0');env.win.onDataChanged(k.tripBConsumption,'0');assert.equal(env.runtime.getStatus().pendingCommand,true);
  env.win.onDataChanged(k.tripB,null);env.win.onDataChanged(k.tripBAvgSpeed,'0');assert.equal(env.runtime.getStatus().pendingCommand,true);
  env.win.onDataChanged(k.tripB,'0');assert.equal(env.runtime.getStatus().pendingCommand,false);assert.equal(env.runtime.getMenuState().feedback,'');assert.equal(env.calls.filter(c=>c[0]==='action').length,1);env.runtime.cleanup();
});
test('TripB partial reset times out without retry or fabricated zero readings',()=>{
  const env=boot({[k.tripB]:1286,[k.tripBTime]:1417,[k.tripBConsumption]:6.4,[k.tripBAvgSpeed]:54});requestTripBReset(env);
  env.win.onDataChanged(k.tripB,'0');env.tick(9000);assert.equal(env.runtime.getStatus().pendingCommand,false);assert.equal(env.runtime.getMenuState().feedback,'SEM RETORNO DO VEÍCULO');assert.equal(env.runtime.getMenuState().data.tripBTime,1417);assert.equal(env.runtime.getMenuState().data.tripBConsumption,6.4);assert.equal(env.calls.filter(c=>c[0]==='action').length,1);env.runtime.cleanup();
});
test('TripB reset accepts four synchronous reentrant canonicalzero callbacks afteraction',()=>{
  const env=boot({[k.tripB]:1286,[k.tripBTime]:1417,[k.tripBConsumption]:6.4,[k.tripBAvgSpeed]:54},{ack:true});requestTripBReset(env);
  assert.equal(env.runtime.getStatus().pendingCommand,false);assert.equal(env.runtime.getMenuState().feedback,'');assert.equal(env.runtime.getMenuState().data.tripBDistance,0);assert.equal(env.runtime.getMenuState().data.tripBTime,0);assert.equal(env.runtime.getMenuState().data.tripBConsumption,0);assert.equal(env.runtime.getMenuState().data.tripBSpeed,0);assert.equal(env.calls.filter(c=>c[0]==='action').length,1);env.runtime.cleanup();
});
test('hidden/clusterfalse/criticalwarning block commands and pause/clear paint immediately',()=>{
  const env=boot({[k.hvacAuto]:0});env.win.onCardChanged(3);env.tick(40);const before=env.draws.length;
  env.doc.hidden=true;env.doc.emit('visibilitychange');assert.equal(env.raf.size,0);assert.equal(env.press('ENTER_LONG'),false);env.tick(1000);assert.equal(env.draws.length,before);
  env.doc.hidden=false;env.doc.emit('visibilitychange');env.tick(40);
  env.win.control('clusterEnabled',false);assert.equal(env.raf.size,0);assert.equal(env.press('ENTER_LONG'),false);assert.equal(env.elements.starlight.style.visibility,'hidden');
  env.win.control('clusterEnabled',true);env.tick(40);env.win.control('warningActive',true);assert.equal(env.press('ENTER_LONG'),false);assert.equal(env.elements['native-readouts'].hidden,true);assert.equal(env.canvas.getContext('2d').getImageData(960,308,1,1).data[3],0);
  env.win.updateWarning('arbitrary','false');env.win.clearWarnings();assert.equal(env.runtime.getState().warningActive,true);
  env.win.control('warningActive',false);env.tick(40);assert.equal(env.elements['starlight-canvas'].hidden,false);env.runtime.cleanup();
});
test('all native projection states open transparent canvas and expose13 real readouts',()=>{
  const env=boot({[k.speed]:0,[k.current]:-200,[k.voltage]:400,[k.fuelPct]:0,[k.batteryPct]:0,[k.rpm]:0,[k.gear]:3});env.win.onCardChanged(0);env.tick(40);
  Adapter.projectionKeys.forEach(key=>{
    env.win.control(key,true);assert.equal(env.elements['starlight-canvas'].hidden,true);assert.equal(env.canvas.getContext('2d').getImageData(960,308,1,1).data[3],0);assert.equal(env.press('ENTER_LONG'),false);env.tick(40);
    assert.equal(env.elements['native-readouts'].hidden,false);assert.equal(env.elements['native-speed'].textContent,'0 km/h');assert.equal(env.elements['native-power'].textContent,'−80,0 kW · REGEN.');assert.equal(env.elements['native-fuel'].textContent,'0 %');assert.equal(env.elements['native-gear'].textContent,'P');
    env.win.control(key,false);env.tick(40);assert.equal(env.elements['starlight-canvas'].hidden,false);
  });env.runtime.cleanup();
});
test('projection permits only canonicalcard1/3 menus/commands over transparent native map',()=>{
  const env=boot({[k.hvacAuto]:0},{ack:true});env.win.control('carPlayInDash',true);env.win.onCardChanged(3);env.tick(40);
  assert.equal(env.elements['starlight-canvas'].hidden,false);assert.equal(env.canvas.getContext('2d').getImageData(960,308,1,1).data[3],0);assert.equal(env.canvas.getContext('2d').getImageData(800,600,1,1).data[3],255);assert.equal(env.draws.length,0);
  env.press('ENTER_LONG');assert.deepEqual(env.calls.filter(c=>c[0]==='action'||c[0]==='write'),[['action','CANCEL_MAX_AC'],['write',k.hvacAuto,'1']]);assert.equal(env.runtime.getStatus().pendingCommand,false);
  env.tick(40);const count=env.menuDraws.length;env.tick(100);assert.equal(env.menuDraws.length,count);assert.equal(env.raf.size,0);
  env.win.control('cardId',1);assert.equal(env.runtime.getState().cardId,3);env.win.onCardChanged(0);assert.equal(env.elements['starlight-canvas'].hidden,true);assert.equal(env.canvas.getContext('2d').getImageData(800,600,1,1).data[3],0);assert.equal(env.press('ENTER_LONG'),false);env.runtime.cleanup();
});
test('projectionmenu render failure is caught, clears nativecanvas and keeps heartbeatalive',()=>{
  const env=boot({[k.hvacAuto]:0});env.win.ThemeMenus.render=(ctx)=>{ctx.fillRect(800,600,1,1);throw new Error('projection render failure');};
  env.win.control('carPlayInDash',true);env.win.onCardChanged(3);assert.doesNotThrow(()=>env.tick(40));assert.equal(env.runtime.getStatus().renderErrors,1);assert.equal(env.elements['starlight-canvas'].hidden,true);assert.equal(env.canvas.getContext('2d').getImageData(800,600,1,1).data[3],0);assert.equal(env.raf.size,0);
  const pulses=env.calls.filter(c=>c[0]==='heartbeat').length;env.tick(2100);assert.ok(env.calls.filter(c=>c[0]==='heartbeat').length>pulses);assert.equal(env.runtime.getStatus().disposed,false);env.runtime.cleanup();
});
test('single scheduler caps paints30Hz; no hidden travel jump; liveness2s/clock minute',()=>{
  const env=boot({[k.speed]:100});for(let i=0;i<100;i++)env.tick(10);assert.ok(env.draws.length<=30);assert.equal(env.raf.size,1);
  const elapsed=env.runtime.getStatus().travel;env.doc.hidden=true;env.doc.emit('visibilitychange');env.tick(6000);assert.equal(env.runtime.getStatus().travel,elapsed);assert.ok(env.calls.filter(c=>c[0]==='heartbeat').length>=2);
  env.doc.hidden=false;env.doc.emit('visibilitychange');env.tick(40);assert.equal(env.runtime.getStatus().travel,elapsed);
  assert.equal([...env.timers.values()].filter(timer=>timer.period===2000).length,1);assert.equal([...env.timers.values()].filter(timer=>timer.period===0).length,1);env.runtime.cleanup();
});
test('cold paint spike does not permanently lower rendererfrequency or freeze movingframes',()=>{
  const env=boot({[k.speed]:100,[k.voltage]:400,[k.current]:100});let coldCost=0;const rates=[];const draw=env.win.MotionStudy.render;
  env.win.performance.now=()=>env.at()+coldCost;
  env.win.MotionStudy.setMaterialHz=rate=>rates.push(rate);
  env.win.MotionStudy.render=function(...args){draw.apply(this,args);if(coldCost===0)coldCost=90;};
  env.tick(40);assert.equal(env.runtime.getStatus().lastPaintMs,90);assert.deepEqual(rates,[]);assert.equal(env.raf.size,1);
  const first=env.draws.length;for(let i=0;i<12;i++)env.tick(40);assert.equal(env.draws.length,first+12);assert.equal(env.runtime.getStatus().lastPaintMs,0);assert.deepEqual(rates,[]);assert.equal(env.raf.size,1);env.runtime.cleanup();
});
test('worker UI/input/canonicalACK render before delayedmaterialACK and remain eventonly',()=>{
  const env=boot({[k.speed]:100,[k.hvacAuto]:0},{workers:true,ack:true});env.warmWorkers();assert.equal(env.runtime.getStatus().renderMode,'workers');assert.equal(env.uiDraws.length,1);const pending=[env.workerFrame(0),env.workerFrame(1)];
  env.win.onDataChanged(k.speed,'120');env.tick(40);assert.equal(env.uiDraws.at(-1).options.state.speed,128);assert.equal(env.runtime.getStatus().material.workers.beams.inflight,true);assert.equal(env.workerFrame(1).sequence,pending[1].sequence);
  env.win.onCardChanged(3);env.press('ENTER_LONG');env.tick(40);assert.equal(env.runtime.getStatus().pendingCommand,false);assert.equal(env.runtime.getMenuState().data.acAuto,1);assert.equal(env.uiDraws.at(-1).options.state.readouts.hvacAuto,'AUTO');assert.equal(env.workerFrame(1).sequence,pending[1].sequence);
  const uiCount=env.uiDraws.length;for(let i=0;i<6;i++)env.tick(40);assert.equal(env.uiDraws.length,uiCount);assert.ok(env.runtime.getStatus().producerFrames>env.runtime.getStatus().uiFrames);assert.equal(env.elements['starlight-canvas'].transfers,undefined);env.runtime.cleanup();
});
test('tipdigits/positions/sign follow presentedbarACK while central latestUI stays independent',()=>{
  const env=boot({[k.speed]:100,[k.voltage]:400,[k.current]:100},{workers:true});env.warmWorkers();const initialTip=env.uiDraws.at(-1).options.tipState;assert.equal(initialTip.speed,106);assert.equal(initialTip.batteryPowerKw,40);const old=env.workerFrame(1);
  env.win.onDataChanged(k.speed,'160');env.win.onDataChanged(k.current,'-200');env.tick(40);const latest=env.uiDraws.at(-1).options;assert.equal(latest.state.speed,171);assert.equal(latest.state.batteryPowerKw,-80);assert.equal(latest.tipState.speed,106);assert.equal(latest.tipState.leftLength,initialTip.leftLength);assert.equal(latest.tipState.batteryPowerKw,40);assert.equal(latest.tipState.regen,false);
  const beforeUnchanged=env.uiDraws.length;env.workerAck(1,old);env.tick(40);assert.equal(env.uiDraws.length,beforeUnchanged);const presented=env.workerFrame(1).state;const timers=env.timers.size;assert.equal(env.raf.size,1);
  env.workerAck(1);assert.equal(env.raf.size,1);assert.equal(env.timers.size,timers);env.tick(40);const synchronized=env.uiDraws.at(-1).options;assert.equal(synchronized.state.speed,171);assert.equal(synchronized.tipState.speed,presented.speed);assert.equal(synchronized.tipState.leftLength,presented.leftLength);assert.equal(synchronized.tipState.batteryPowerKw,presented.batteryPowerKw);assert.equal(synchronized.tipState.rightLength,presented.rightLength);assert.equal(synchronized.tipState.regen,true);
  const beforePhase=env.uiDraws.length;env.workerAck(1);env.tick(40);assert.equal(env.uiDraws.length,beforePhase);env.runtime.cleanup();
});
test('fallbackmonolithic renderer receives lateststate without presentedtip override',()=>{
  const env=boot({[k.speed]:100,[k.voltage]:400,[k.current]:100},{workers:true});env.warmWorkers();env.win.onDataChanged(k.speed,'160');env.win.onDataChanged(k.current,'-200');env.workers[1].onerror();env.tick(40);const options=env.draws.at(-1).options;assert.equal(options.state.speed,171);assert.equal(options.state.batteryPowerKw,-80);assert.equal(Object.prototype.hasOwnProperty.call(options,'tipState'),false);env.runtime.cleanup();
});
test('workerlayers hide immediately for warnings/projection/cluster/hidden; staleACK cannot unhide',()=>{
  for(const transition of ['warning','projection','cluster','hidden']){
    const env=boot({[k.speed]:100},{workers:true});env.warmWorkers();const pending=[env.workerFrame(0),env.workerFrame(1)];
    if(transition==='warning')env.win.control('warningActive',true);
    if(transition==='projection')env.win.control('carPlayInDash',true);
    if(transition==='cluster')env.win.control('clusterEnabled',false);
    if(transition==='hidden'){env.doc.hidden=true;env.doc.emit('visibilitychange');}
    assert.equal(env.elements['starlight-background'].hidden,true);assert.equal(env.elements['starlight-material'].hidden,true);env.workerAck(0,pending[0]);env.workerAck(1,pending[1]);assert.equal(env.elements['starlight-background'].hidden,true);assert.equal(env.elements['starlight-material'].hidden,true);env.runtime.cleanup();assert.deepEqual(env.workers.map(w=>w.terminated),[1,1]);assert.deepEqual(env.revoked,['blob:runtime-workers']);
  }
});
test('workerfailure falls back originalpainter; fontreadiness never resetsvalidmaterial',()=>{
  const env=boot({[k.speed]:100},{workers:true,fontReady:true});env.warmWorkers();let resets=0;env.win.MotionStudy.reset=()=>resets++;env.resolveFonts();env.tick(40);assert.equal(resets,0);
  const original=env.draws.length;env.workers[1].onerror();assert.equal(env.runtime.getStatus().renderMode,'fallback');assert.equal(env.elements['starlight-background'].hidden,true);env.tick(40);assert.equal(env.draws.length,original+1);assert.equal(env.elements['starlight-canvas'].hidden,false);env.runtime.cleanup();
});
test('null/zero stationary state is event-only; duplicate telemetry creates no idleRAF',()=>{
  const env=boot();env.tick(40);assert.equal(env.raf.size,0);const first=env.draws.length;env.tick(1000);assert.equal(env.draws.length,first);
  env.win.onDataChanged(k.speed,0);env.win.onDataChanged(k.voltage,400);env.win.onDataChanged(k.current,0);env.tick(40);assert.equal(env.raf.size,0);const zero=env.draws.length;
  env.win.onDataChanged(k.speed,0);env.win.onDataChanged(k.current,0);assert.equal(env.raf.size,0);env.tick(1000);assert.equal(env.draws.length,zero);
  env.win.onDataChanged(k.speed,100);env.tick(40);assert.equal(env.raf.size,1);env.win.onDataChanged(k.speed,0);env.tick(40);assert.equal(env.raf.size,0);env.runtime.cleanup();
});
test('TripB canonical reset acknowledgment also accepts unchanged realzero readings',()=>{
  const env=boot({[k.tripB]:0,[k.tripBTime]:0,[k.tripBConsumption]:0,[k.tripBAvgSpeed]:0},{ack:true});env.win.onCardChanged(1);env.press('DOWN');env.press('DOWN');env.press('DOWN');env.press('ENTER');env.press('DOWN');env.press('ENTER_LONG');assert.equal(env.runtime.getStatus().pendingCommand,false);assert.equal(env.runtime.getMenuState().data.tripBDistance,0);env.runtime.cleanup();
});
test('late browser bridge can execute explicit commands without production simulator',()=>{
  const env=boot({}, {noBridge:true});env.win.Android=env.bridge;env.win.onDataChanged(k.hvacAuto,0);env.win.onCardChanged(3);env.press('ENTER_LONG');assert.deepEqual(env.calls.filter(c=>c[0]==='action'||c[0]==='write'),[['action','CANCEL_MAX_AC'],['write',k.hvacAuto,'1']]);env.runtime.cleanup();
});
test('cleanup is idempotent; removes timers/listeners/subscription and stale frames',()=>{
  const env=boot({[k.speed]:100});const stale=[...env.raf.values()][0];env.runtime.cleanup();env.runtime.cleanup();assert.equal(env.raf.size,0);assert.equal(env.timers.size,0);assert.equal(env.doc.count(),0);assert.equal(env.win.count(),0);assert.equal(env.calls.filter(c=>c[0]==='unsubscribe').length,1);assert.equal(env.win.MotionStudy.cleanups,1);
  stale(1000);env.win.control(k.speed,200);assert.equal(env.draws.length,0);assert.equal(env.runtime.getState().decoded.speed,106);
});
test('reentrant cleanup during subscription cannot leak post-start timers',()=>{
  const env=boot({}, {subscribe(win){win.cleanup();}});assert.equal(env.runtime.getStatus().disposed,true);assert.equal(env.raf.size,0);assert.equal(env.timers.size,0);assert.equal(env.calls.filter(c=>c[0]==='unsubscribe').length,1);
});
test('reload old cleanup does not overwrite newer globals',()=>{
  const env=boot();const old=env.runtime;const Runtime=require(path.join(source,'host-runtime.js'));const next=Runtime.create({window:env.win,document:env.doc});env.tick(40);const hook=env.win.control;old.cleanup();assert.equal(env.win.control,hook);assert.equal(env.canvas.getContext('2d').getImageData(960,308,1,1).data[3],255);assert.equal(env.win.MotionStudy.cleanups,undefined);env.win.onDataChanged(k.speed,80);assert.equal(next.getState().decoded.speed,85);next.cleanup();assert.equal(env.timers.size,0);
});
test('payloads are parsed numerically; quotes/control characters cannot become executableJS',()=>{
  const env=boot();env.win.onDataChanged(k.speed,"100');window.pwned=true;//");env.win.control('carSpeed','100\nalert(1)');assert.equal(env.runtime.getState().decoded.speed,null);assert.equal(env.win.pwned,undefined);env.win.onDataChanged(k.current,'-2e2');env.win.onDataChanged(k.voltage,'400');assert.equal(env.runtime.getState().decoded.powerKw,-80);env.runtime.cleanup();
});
console.log(`Starlight runtime: ${passed} checks passed`);
