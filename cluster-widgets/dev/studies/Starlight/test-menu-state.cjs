'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const M = require('./menu-state.js');
const step = (s,type,rest={}) => M.dispatch(s,{type,...rest});
const key = (s,k,now) => step(s,'KEY',{key:k,...(now===undefined?{}:{now})});
const demo = () => M.createState(M.demoData());
const open = (s,section) => step(s,'OPEN',{section});
const ac = s => step(s,'CARD',{card:3});
function freeze(value) {
  if (value && typeof value==='object') { Object.values(value).forEach(freeze); Object.freeze(value); }
  return value;
}

test('CommonJS exports stable preview-only API and immutable source metadata',()=>{
  for(const name of ['createState','demoData','dispatch','getView','getContent','normalize','power']) assert.equal(typeof M[name],'function');
  assert.equal(M.metadata.simulationOnly,true);
  assert.ok(Object.isFrozen(M.metadata.mainItems[0]));
  assert.deepEqual(M.metadata.mainItems.map(x=>x.title),['INFORMAÇÕES','GRÁFICOS','AJUSTES','TRIP A / TRIP B']);
  assert.deepEqual(M.metadata.settingItems.map(x=>x.title),['CONDUÇÃO','PROPULSÃO','DIREÇÃO','REGENERAÇÃO','ESTABILIDADE','DISPLAY']);
  assert.deepEqual(M.metadata.cards,{panel:0,main:1,ac:3});
});

test('browser UMD loads and runs without any Android, DOM or network access',()=>{
  const deny=()=>{throw Error('External access is forbidden');};
  const context=vm.createContext({Android:new Proxy({},{get:deny}),document:new Proxy({},{get:deny}),fetch:deny,setTimeout:deny,setInterval:deny,localStorage:new Proxy({},{get:deny})});
  vm.runInContext(fs.readFileSync(require.resolve('./menu-state.js'),'utf8'),context);
  assert.equal(typeof context.StarlightMenuState.dispatch,'function');
  vm.runInContext("var s=StarlightMenuState.createState(StarlightMenuState.demoData());s=StarlightMenuState.dispatch(s,{type:'CARD',card:3});s=StarlightMenuState.dispatch(s,{type:'KEY',key:'ENTER_LONG'});",context);
  assert.equal(context.s.data.acAuto,0);
  assert.equal(context.s.lastCommand.simulationOnly,true);
});

test('initial state has no fabricated telemetry and no visible menu',()=>{
  const s=M.createState();
  assert.ok(Object.values(s.data).every(x=>x===null));
  assert.equal(s.card,0); assert.equal(M.getView(s),'panel');
  assert.equal(M.getContent(s).visible,false);
  assert.equal(s.lastCommand,null);
});

test('demo data is a fresh object; inputs and output state are not shared',()=>{
  const a=M.demoData(),b=M.demoData();a.acFan=7;assert.equal(b.acFan,3);
  const s=M.createState(b);b.fuel=5;assert.equal(s.data.fuel,68);
});

test('demo telemetry matches the existing ApexData closed-panel profile',()=>{
  const ApexData=require('./apex-data.js');
  const fromPanel=M.createState(ApexData.demo(0,{speed:72,powerKw:72,batteryPowerKw:72}));
  assert.deepEqual(fromPanel.data,demo().data);
});

test('canonical telemetry fields and local simulation field names normalize equally',()=>{
  const s=M.createState({'car.hvac.fan_speed':'3','car.basic.tire_pressure_front_left':'234.4',driveMode:'Eco'});
  assert.equal(s.data.acFan,3);assert.equal(s.data.tireFL,234.4);assert.equal(s.data.driveMode,2);
  const a=step(s,'TELEMETRY',{values:{acTemp:'22.5','car.ev_info.cur_charge_current':'-50',unknown:123}});
  assert.equal(a.data.acTemp,22.5);assert.equal(a.data.current,-50);assert.ok(!('unknown' in a.data));
});

test('invalid values, zero, temperatures and range domains follow Apex normalization',()=>{
  for(const value of [null,undefined,'',true,{},NaN,Infinity,'22px']) assert.equal(M.normalize('fuel',value),null);
  assert.equal(M.normalize('fuel',0),0);assert.equal(M.normalize('fuel',101),null);assert.equal(M.normalize('battery',-1),null);
  assert.equal(M.normalize('insideTemp',-1),null);assert.equal(M.normalize('outsideTemp',255),null);assert.equal(M.normalize('outsideTemp',-5),-5);
  assert.equal(M.normalize('acFan',7),7);assert.equal(M.normalize('acFan',7.5),null);assert.equal(M.normalize('acFan',8),null);
  assert.equal(M.normalize('acTemp',16),16);assert.equal(M.normalize('acTemp',32),32);assert.equal(M.normalize('acTemp',15.5),null);
  assert.equal(M.normalize('acPower',2),null);assert.equal(M.normalize('current',-20),-20);assert.equal(M.normalize('voltage',-20),null);
});

test('enum labels preserve source-supported readouts and legacy HEV fallback',()=>{
  assert.equal(M.normalize('driveMode','AWD'),11);
  assert.equal(M.normalize('propulsionMode','HEV PRIORIDADE 50%'),0);
  assert.equal(M.normalize('propulsionMode','HEV INTELIGENTE'),0);
  assert.equal(M.normalize('esp','ATIVO'),1);assert.equal(M.normalize('esp','INATIVO'),0);
  assert.equal(M.normalize('steerMode','CONFORTO'),2);assert.equal(M.normalize('regenMode','ALTO'),1);
  assert.equal(M.normalize('tempUnit','°F'),1);assert.equal(M.normalize('driveMode',99),null);
});

test('dispatch is immutable even when the incoming state is deeply frozen',()=>{
  const s=freeze(ac(demo()));const before=JSON.stringify(s);
  const changed=key(s,'UP');assert.equal(changed.data.acFan,4);assert.equal(s.data.acFan,3);
  assert.equal(JSON.stringify(s),before);assert.notEqual(changed.data,s.data);assert.notEqual(changed.history.speed,s.history.speed);
});

test('host cards open main and AC; invalid cards cannot change presentation',()=>{
  let s=demo();s=step(s,'CARD',{card:1});assert.equal(M.getView(s),'menu');
  s=step(s,'CARD',{card:3});assert.equal(M.getView(s),'ac');
  for(const card of [2,4,-1,'bad',null]) assert.equal(step(s,'CARD',{card}).card,3);
  s=step(s,'CARD',{card:'0'});assert.equal(M.getView(s),'panel');
});

test('main-menu UP/DOWN wraps through the exact four sections',()=>{
  let s=step(demo(),'CARD',{card:1});s=key(s,'UP');assert.equal(s.menuIndex,3);
  s=key(s,'DOWN');assert.equal(s.menuIndex,0);
  for(let i=0;i<4;i++){assert.equal(s.menuIndex,i);s=key(s,'DOWN');}
  assert.equal(s.menuIndex,0);
});

test('ENTER opens every selected section and BACK returns to its menu row',()=>{
  for(let i=0;i<4;i++){
    let s=step(demo(),'CARD',{card:1});for(let n=0;n<i;n++)s=key(s,'DOWN');
    s=key(s,'ENTER');assert.equal(M.getView(s),M.metadata.mainItems[i].view);
    s=key(s,'BACK');assert.equal(M.getView(s),'menu');assert.equal(s.menuIndex,i);
  }
});

test('root BACK and AC short BACK are no-ops; explicit preview CLOSE is host-like',()=>{
  let s=step(demo(),'CARD',{card:1});s=key(s,'BACK');assert.equal(s.card,1);
  s=ac(s);s=key(s,'BACK');assert.equal(s.card,3);
  s=step(s,'CLOSE');assert.equal(s.card,0);assert.equal(s.feedback,'');assert.equal(s.lastCommand,null);
});

test('host reentry resets detail but preserves selected row and AC focus',()=>{
  let s=open(demo(),'trips');s=ac(s);s=key(s,'ENTER');assert.equal(s.acFocus,'temp');
  s=step(s,'CARD',{card:1});assert.equal(s.detail,false);assert.equal(s.menuIndex,3);
  s=key(s,'ENTER');assert.equal(M.getView(s),'trips');s=step(s,'CARD',{card:1});assert.equal(s.detail,true);
  s=ac(s);assert.equal(s.acFocus,'temp');
});

test('OPEN, TAB and click-equivalent settings only affect their proper views',()=>{
  let s=open(demo(),'info');s=step(s,'TAB',{group:'info',index:1});assert.equal(s.infoIndex,1);
  assert.equal(step(s,'TAB',{group:'trip',index:1}).tripIndex,0);
  assert.equal(step(s,'TAB',{group:'info',index:3}).infoIndex,1);
  assert.equal(step(s,'SETTING',{index:0}).data.driveMode,1);
  assert.equal(M.getView(open(s,'invented')),'info');
});

test('FOCUS synchronizes native keyboard/pointer focus without opening or changing a value',()=>{
  let s=step(demo(),'CARD',{card:1});s=step(s,'FOCUS',{group:'menu',index:2});
  assert.equal(s.menuIndex,2);assert.equal(s.detail,false);assert.equal(M.getView(s),'menu');
  s=key(s,'ENTER');assert.equal(M.getView(s),'settings');const before={...s.data};
  s=step(s,'FOCUS',{group:'setting',index:3});assert.equal(s.settingIndex,3);assert.deepEqual(s.data,before);assert.equal(s.lastCommand,null);
  assert.equal(step(s,'FOCUS',{group:'setting',index:99}).settingIndex,3);
  assert.equal(step(s,'FOCUS',{group:'menu',index:0}).menuIndex,2);
});

test('info, graph and trip tabs toggle on either arrow',()=>{
  for(const [view,field] of [['info','infoIndex'],['graphs','graphIndex'],['trips','tripIndex']]){
    let s=open(demo(),view);s=key(s,'UP');assert.equal(s[field],1);s=key(s,'DOWN');assert.equal(s[field],0);
  }
});

test('settings navigation wraps six rows including disabled source-only display',()=>{
  let s=open(demo(),'settings');s=key(s,'UP');assert.equal(s.settingIndex,5);
  s=key(s,'DOWN');assert.equal(s.settingIndex,0);
  for(let i=0;i<6;i++)s=key(s,'DOWN');assert.equal(s.settingIndex,0);
});

test('setting cycles use exact Apex selectable orders, not all readable drive enums',()=>{
  for(let i=0;i<5;i++){
    const item=M.metadata.settingItems[i];let s=open(M.createState({...M.demoData(),[item.field]:item.values[0]}),'settings');
    for(let cycle=1;cycle<=item.values.length;cycle++){
      s=step(s,'SETTING',{index:i});assert.equal(s.data[item.field],item.values[cycle%item.values.length]);
      assert.equal(s.lastCommand.writes[0].key,item.key);assert.equal(s.lastCommand.simulationOnly,true);
    }
  }
  let s=open(M.createState({...M.demoData(),driveMode:11}),'settings');s=key(s,'ENTER');assert.equal(s.data.driveMode,0);
});

test('DISPLAY never changes the selected model or emits a pretend command',()=>{
  let s=open(demo(),'settings');s=step(s,'SETTING',{index:5});
  assert.equal(s.lastCommand,null);assert.match(s.feedback,/EXCLUSIVO/);assert.ok(!('displayMode' in s.data));
  const row=M.getContent(s).rows[5];assert.equal(row.disabled,true);assert.match(row.reason,/modelo 3/);
});

test('missing setting values do not become real-looking adjustments',()=>{
  let s=open(M.createState(),'settings');s=key(s,'ENTER');assert.equal(s.data.driveMode,null);
  assert.equal(s.lastCommand,null);assert.match(s.feedback,/AGUARDANDO DADOS SIMULADOS/);
});

test('AC starts with fan focus and ENTER alternates only its two source controls',()=>{
  let s=ac(demo());assert.equal(s.acFocus,'fan');s=key(s,'ENTER');assert.equal(s.acFocus,'temp');
  s=key(s,'ENTER');assert.equal(s.acFocus,'fan');s=step(s,'AC_FOCUS',{field:'temp'});assert.equal(s.acFocus,'temp');
  assert.equal(step(s,'AC_FOCUS',{field:'seat'}).acFocus,'temp');
});

test('AC temperature steps by half-degree and clamps at source LO/HI limits',()=>{
  let s=step(ac(demo()),'AC_FOCUS',{field:'temp'});s=key(s,'UP');assert.equal(s.data.acTemp,22.5);
  assert.equal(s.lastCommand.writes[0].value,'22.5');s=key(s,'DOWN');assert.equal(s.data.acTemp,22);
  for(let i=0;i<60;i++)s=key(s,'DOWN');assert.equal(s.data.acTemp,16);assert.equal(M.getContent(s).rows[0].value,'LO');
  for(let i=0;i<60;i++)s=key(s,'UP');assert.equal(s.data.acTemp,32);assert.equal(M.getContent(s).rows[0].value,'HI');
});

test('fan clamps 0..7, powers system off at0 and on when rising from off',()=>{
  let s=ac(demo());for(let i=0;i<10;i++)s=key(s,'DOWN');assert.equal(s.data.acFan,0);assert.equal(s.data.acPower,0);
  assert.equal(M.getContent(s).rows[1].value,'OFF');s=key(s,'UP');assert.equal(s.data.acPower,1);assert.equal(s.data.acFan,1);
  assert.deepEqual(s.lastCommand.writes.map(x=>x.key),['car.hvac.power_mode','car.hvac.fan_speed']);
  for(let i=0;i<10;i++)s=key(s,'UP');assert.equal(s.data.acFan,7);
});

test('fan effective value is0 while system off even if last fan telemetry is nonzero',()=>{
  let s=ac(M.createState({...M.demoData(),acPower:0,acFan:5}));s=key(s,'UP');assert.equal(s.data.acFan,1);assert.equal(s.data.acPower,1);
});

test('AC long ENTER toggles auto with CANCEL_MAX_AC ordered before write descriptor',()=>{
  let s=ac(demo());s=key(s,'ENTER_LONG');assert.equal(s.data.acAuto,0);
  assert.equal(s.lastCommand.systemAction,'CANCEL_MAX_AC');assert.deepEqual(s.lastCommand.order,['systemAction','writes']);
  assert.deepEqual(s.lastCommand.writes,[{key:'car.hvac.auto_enable',value:'0'}]);
  s=key(s,'ENTER_LONG');assert.equal(s.data.acAuto,1);assert.match(s.feedback,/SIMULADO/);
});

test('AC long BACK toggles recirculation without closing the card',()=>{
  let s=ac(demo());s=key(s,'BACK_LONG');assert.equal(s.data.acRecycle,0);assert.equal(s.card,3);
  assert.deepEqual(s.lastCommand.writes,[{key:'car.hvac.cycle_mode',value:'0'}]);
  s=key(s,'BACK_LONG');assert.equal(s.data.acRecycle,1);
});

test('unknown AC values cannot be altered until simulated data is supplied',()=>{
  for(const keyName of ['UP','ENTER_LONG','BACK_LONG']){
    let s=key(ac(M.createState()),keyName);assert.equal(s.lastCommand,null);assert.match(s.feedback,/AGUARDANDO/);
  }
  let s=step(ac(M.createState()),'AC_FOCUS',{field:'temp'});s=key(s,'UP');assert.equal(s.data.acTemp,null);
});

test('TripA cannot be reset by any short or long key',()=>{
  let s=open(demo(),'trips');const tripA=s.data.tripADistance;
  s=key(s,'ENTER_LONG');assert.equal(s.data.tripADistance,tripA);assert.equal(s.lastCommand,null);
  assert.equal(M.getContent(s).resetAction,null);
});

test('TripB reset requires deliberate long ENTER and only resets simulated B metrics',()=>{
  let s=open(demo(),'trips');s=key(s,'DOWN');const a=s.data.tripADistance,odo=s.data.odometer;
  s=key(s,'ENTER');assert.equal(s.data.tripBDistance,1286.4);
  s=key(s,'ENTER_LONG');for(const field of ['tripBDistance','tripBTime','tripBConsumption','tripBSpeed'])assert.equal(s.data[field],0);
  assert.equal(s.data.tripADistance,a);assert.equal(s.data.odometer,odo);
  assert.equal(s.lastCommand.systemAction,'RESET_DRIVE_INFO');assert.match(s.feedback,/SIMULAÇÃO/);
});

test('preview safeguard prevents TripB reset from inventing zeroes in unavailable or partial data',()=>{
  for(const seed of [{},{tripBDistance:100}]){
    let s=open(M.createState(seed),'trips');s=key(s,'DOWN');const before={...s.data};
    s=key(s,'ENTER_LONG');assert.deepEqual(s.data,before);assert.equal(s.lastCommand,null);assert.match(s.feedback,/AGUARDANDO/);
  }
});

test('source key whitelist and50ms debounce are deterministic and scoped to active menus',()=>{
  let s=step(demo(),'CARD',{card:1});s=key(s,'DOWN',100);assert.equal(s.menuIndex,1);
  assert.equal(key(s,'DOWN',149),s);s=key(s,'DOWN',150);assert.equal(s.menuIndex,2);
  assert.equal(key(s,'RIGHT',200),s);s=step(s,'CLOSE');assert.equal(key(s,'ENTER',250).card,0);
});

test('simulated data updates do not overwrite user-adjusted settings or HVAC',()=>{
  let s=key(ac(demo()),'UP');const before=s.data.acFan;
  s=step(s,'TELEMETRY',{values:{speed:84,voltage:400,current:-65},now:1000});
  assert.equal(s.data.acFan,before);assert.equal(s.data.driveMode,1);assert.equal(M.power(s.data),-26);
});

test('power needs both operands, preserves signed regeneration and real zero',()=>{
  assert.equal(M.power({voltage:400,current:null}),null);assert.equal(M.power({voltage:null,current:2}),null);
  assert.equal(M.power({voltage:400,current:-65}),-26);assert.equal(M.power({voltage:400,current:0}),0);
  assert.equal(M.power({voltage:1e308,current:1e308}),null);
});

test('history stores18 samples, coalesces under250ms and ignores missing values',()=>{
  let s=M.createState();
  s=step(s,'TELEMETRY',{values:{speed:10,voltage:400,current:20},now:0});
  s=step(s,'TELEMETRY',{values:{speed:11,current:25},now:100});assert.deepEqual(s.history.speed,[11]);assert.deepEqual(s.history.power,[10]);
  s=step(s,'TELEMETRY',{values:{speed:12,current:30},now:250});assert.deepEqual(s.history.speed,[11,12]);
  s=step(s,'TELEMETRY',{values:{speed:null,current:null},now:500});assert.equal(s.history.speed.length,2);
  for(let i=0;i<30;i++)s=step(s,'TELEMETRY',{values:{speed:30+i,current:i},now:1000+i*250});
  assert.equal(s.history.speed.length,18);assert.equal(s.history.power.length,18);assert.equal(s.history.speed.at(-1),59);
});

test('keyboard clock cannot corrupt independent telemetry timeline',()=>{
  let s=step(M.createState(),'CARD',{card:1});s=key(s,'DOWN',1000000000);
  s=step(s,'TELEMETRY',{values:{speed:10},now:0});s=step(s,'TELEMETRY',{values:{speed:20},now:250});
  assert.deepEqual(s.history.speed,[10,20]);assert.equal(s.telemetryTime,250);assert.equal(s.time,1000000000);
});

test('RESET_HISTORY safely starts a replay timeline without clearing settings or navigation',()=>{
  let s=open(demo(),'graphs');s=step(s,'TELEMETRY',{values:{speed:80},now:5000});
  s=step(s,'RESET_HISTORY');assert.deepEqual(s.history,{speed:[],power:[]});assert.equal(s.telemetryTime,0);assert.equal(M.getView(s),'graphs');assert.equal(s.data.acTemp,22);
  s=step(s,'TELEMETRY',{values:{speed:20},now:0});s=step(s,'TELEMETRY',{values:{speed:30},now:250});assert.deepEqual(s.history.speed,[20,30]);
});

test('RESET_DATA changes availability/profile while preserving all navigation positions',()=>{
  let s=open(demo(),'trips');s=key(s,'DOWN');s=step(s,'RESET_DATA',{values:{}});
  assert.equal(M.getView(s),'trips');assert.equal(s.tripIndex,1);assert.ok(Object.values(s.data).every(v=>v===null));
  s=step(s,'RESET_DATA',{values:M.demoData()});assert.equal(s.tripIndex,1);assert.equal(s.data.acTemp,22);
});

test('content has stable uniform rows, titles, navigation actions and no mutation',()=>{
  for(const section of ['menu','info','graphs','settings','trips','ac']){
    const base=demo();const s=freeze(section==='menu'?step(base,'CARD',{card:1}):section==='ac'?ac(base):open(base,section));
    const before=JSON.stringify(s),c=M.getContent(s);assert.equal(JSON.stringify(s),before);
    assert.equal(c.view,section);assert.equal(c.simulationOnly,true);assert.ok(c.title);
    for(const row of [...c.rows,...c.stats,...(c.primary?[c.primary]:[])])for(const property of ['id','title','label','value','unit','secondary','subtitle','selected','disabled'])assert.ok(property in row,property);
    for(const tab of c.tabs){assert.equal(tab.label,tab.title);assert.equal(tab.action.type,'TAB');}
  }
});

test('tire pressures convert native kPa toPSI; zero and missing never pretend to be pressure',()=>{
  let s=open(demo(),'info');s=step(s,'TAB',{group:'info',index:1});let c=M.getContent(s);
  assert.equal(c.stats[0].value,'34,8');assert.equal(c.stats[0].unit,'PSI');
  s=step(s,'TELEMETRY',{values:{tireFL:0,tireFR:null}});c=M.getContent(s);assert.equal(c.stats[0].value,'--');assert.equal(c.stats[1].value,'--');
});

test('trip units, rounding and zero-consumption sentinel match Apex',()=>{
  let s=open(M.createState({...M.demoData(),tripATime:59.6,tripAConsumption:0}),'trips');let c=M.getContent(s);
  assert.equal(c.primary.unit,'km');assert.equal(c.stats[0].value,'1h 00min');assert.equal(c.stats[2].value,'0,0 L/100 km');
  s=step(s,'TELEMETRY',{values:{tripAConsumption:5}});assert.equal(M.getContent(s).stats[2].value,'20,0 km/L');
  s=step(s,'TELEMETRY',{values:{tripAConsumption:null}});assert.equal(M.getContent(s).stats[2].value,'--');
});

test('graph content preserves power domain and adapts speed to owner-approved200km/h',()=>{
  let s=open(demo(),'graphs');s=step(s,'TELEMETRY',{values:{current:-50},now:0});let c=M.getContent(s);
  assert.deepEqual(c.chart.domain,[-100,100]);assert.equal(c.chart.value,-20);assert.equal(c.chart.unit,'kW');
  c.chart.samples.push(999);assert.equal(s.history.power.length,1);
  s=key(s,'DOWN');c=M.getContent(s);assert.deepEqual(c.chart.domain,[0,200]);assert.equal(c.chart.unit,'km/h');
  s=step(s,'TELEMETRY',{values:{speed:200},now:250});c=M.getContent(s);assert.equal(c.chart.value,200);assert.equal(c.chart.formatted,'200');assert.equal(c.chart.samples.at(-1),200);assert.equal(c.chart.rangeLabel,'0 / 200 km/h');
  s=step(s,'TELEMETRY',{values:{speed:205},now:500});c=M.getContent(s);assert.equal(c.chart.value,205);assert.equal(c.chart.samples.at(-1),205);
});

test('AC content accurately exposes its status, segmented fan and temperature rail',()=>{
  let s=ac(demo());let c=M.getContent(s);assert.equal(c.rows[0].ratio,.375);assert.equal(c.rows[1].activeSegments,3);
  assert.deepEqual(c.stats.map(x=>x.value),['LIGADO','AUTO','RECIRC.']);
  s=step(s,'RESET_DATA',{values:{}});c=M.getContent(s);assert.ok(c.rows.every(x=>x.value==='--'));assert.ok(c.stats.every(x=>x.value==='--'));
});

test('host navigation and explicit CLEAR_FEEDBACK clear command audit/status',()=>{
  let s=key(ac(demo()),'UP');assert.ok(s.lastCommand);s=step(s,'CLEAR_FEEDBACK');assert.equal(s.lastCommand,null);assert.equal(s.feedback,'');
  s=key(s,'DOWN');s=step(s,'CARD',{card:1});assert.equal(s.lastCommand,null);assert.equal(s.feedback,'');
});

test('long randomized input sequences keep all reducer invariants and remain serializable',()=>{
  let s=demo(),rng=37;
  function random(){rng=(rng*1664525+1013904223)>>>0;return rng;}
  for(let i=0;i<3000;i++){
    const actions=[{type:'CARD',card:[0,1,3][random()%3]},{type:'OPEN',section:M.metadata.mainItems[random()%4].view},
      {type:'KEY',key:M.metadata.keys[random()%6]},{type:'SETTING',index:random()%6},{type:'AC_FOCUS',field:random()%2?'temp':'fan'}];
    const old=JSON.stringify(s);s=M.dispatch(freeze(s),actions[random()%actions.length]);
    assert.ok([0,1,3].includes(s.card));assert.ok(s.menuIndex>=0&&s.menuIndex<4);assert.ok(s.settingIndex>=0&&s.settingIndex<6);
    assert.ok(s.data.acTemp>=16&&s.data.acTemp<=32);assert.ok(s.data.acFan>=0&&s.data.acFan<=7);
    assert.ok(['temp','fan'].includes(s.acFocus));assert.equal(s.simulationOnly,true);
    assert.ok(JSON.stringify(s));assert.ok(old);
  }
});
