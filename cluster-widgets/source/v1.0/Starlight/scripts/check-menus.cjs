/* Production menu painter checks. All input is local; no bridge or vehicle. */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {performance}=require('node:perf_hooks');
const {createCanvas,GlobalFonts}=require('@napi-rs/canvas');
const root=path.resolve(__dirname,'..');
const S=require('../src/menu-state');
const M=require('../src/theme-menus');
for(const [file,family] of [['ScaleDigits.ttf','Scale Digits'],['LabelSans.ttf','Label Sans']])
  assert(GlobalFonts.registerFromPath(path.join(root,'assets',file),family));
assert.deepEqual(M.BOX,{x:520,y:524,w:880,h:180});
const box=M.BOX;
const beforePath=path.resolve(root,'../../../../tools/headunit-dev/output/starlight-menu-legibility-20261013/source-1.0.7/src/theme-menus.js');
const Before=fs.existsSync(beforePath)?require(beforePath):null;
const seed={speed:64,odometer:25655,insideTemp:23,outsideTemp:29,tempUnit:0,fuel:35,battery:48,fuelRange:275,batteryRange:166,
  voltage:400,current:-45,tripADistance:18.6,tripATime:84,tripASpeed:36.8,tripAConsumption:14.1,
  tripBDistance:813.2,tripBTime:1582,tripBSpeed:48.1,tripBConsumption:17.2,
  tireFL:239,tireFR:242,tireRL:241,tireRR:238,driveMode:0,propulsionMode:1,steerMode:2,regenMode:1,esp:1,
  acPower:1,acFan:3,acTemp:22,acRecycle:0,acAuto:1};
function state(view,extra={},values=seed){
  const s=S.createState(values);s.card=view==='ac'?3:1;s.detail=view!=='menu'&&view!=='ac';
  if(s.detail)s.menuIndex=S.metadata.mainItems.findIndex(item=>item.view===view);
  return Object.assign(s,extra);
}
const cases=[];
for(let menuIndex=0;menuIndex<4;menuIndex++)cases.push(['menu-'+menuIndex,state('menu',{menuIndex})]);
for(let settingIndex=0;settingIndex<6;settingIndex++)cases.push(['settings-'+settingIndex,state('settings',{settingIndex})]);
for(let infoIndex=0;infoIndex<2;infoIndex++)cases.push(['info-'+infoIndex,state('info',{infoIndex})]);
for(let tripIndex=0;tripIndex<2;tripIndex++)cases.push(['trips-'+tripIndex,state('trips',{tripIndex})]);
for(let graphIndex=0;graphIndex<2;graphIndex++){
  const s=state('graphs',{graphIndex});s.history={speed:[0,12,34,48,64],power:[-45,-18,0,14,32]};cases.push(['graph-'+graphIndex,s]);
}
for(const acFocus of ['fan','temp'])for(const acTemp of [16,22,31.5,32])
  cases.push(['ac-'+acFocus+'-'+acTemp,state('ac',{acFocus},{...seed,acTemp,acFan:acTemp===32?7:3})]);
cases.push(['ac-off',state('ac',{}, {...seed,acPower:0,acAuto:0,acRecycle:1})]);
for(const view of ['menu','settings','info','trips','graphs','ac'])cases.push([view+'-absent',state(view,{}, {})]);
for(const feedback of ['AGUARDANDO RETORNO DO VEÍCULO','AGUARDANDO DADOS DO VEÍCULO','COMANDO INDISPONÍVEL','DISPLAY EXCLUSIVO DO APEX GT'])
  cases.push(['feedback-'+feedback,state('menu',{feedback})]);
const long={...seed,odometer:9999999,tripADistance:999999.9,tripATime:99999,tripASpeed:999.9,tripAConsumption:0};
cases.push(['info-long',state('info',{},long)],['trips-long',state('trips',{},long)]);
function capture(api,s,projection){
  const canvas=createCanvas(1920,720),c=canvas.getContext('2d'),glyphs=[],fills=[];let arcs=[];
  const traced=new Proxy(c,{
    get(target,key){
      if(key==='beginPath')return()=>{arcs=[];target.beginPath();};
      if(key==='arc')return(...args)=>{arcs.push(args.slice());target.arc(...args);};
      if(key==='fill')return(...args)=>{fills.push({style:String(target.fillStyle),arcs:arcs.slice()});target.fill(...args);};
      if(key==='fillText')return(label,x,y)=>{
        const metric=target.measureText(label);
        glyphs.push({label,size:parseFloat(target.font),font:target.font,x,y,
          left:x-metric.actualBoundingBoxLeft-1,top:y-metric.actualBoundingBoxAscent-1,
          right:x+metric.actualBoundingBoxRight+1,bottom:y+metric.actualBoundingBoxDescent+1,
          blur:target.shadowBlur});
        target.fillText(label,x,y);
      };
      return typeof target[key]==='function'?target[key].bind(target):target[key];
    },set(target,key,value){target[key]=value;return true;}
  });
  const input={speed:s.data.speed,readouts:{totalRange:441},projection};
  const json=JSON.stringify(s),inputJson=JSON.stringify(input);
  const hits=api.render(traced,s,input);
  assert.equal(JSON.stringify(s),json,'render must not mutate menu state or command descriptors');
  assert.equal(JSON.stringify(input),inputJson,'render must not mutate telemetry');
  return {canvas,glyphs,fills,hits};
}
function contains(bounds){return bounds.x>=box.x&&bounds.y>=box.y&&bounds.x+bounds.w<=box.x+box.w&&bounds.y+bounds.h<=box.y+box.h;}
let minimum=Infinity,totalGlyphs=0,outsidePixels=0;
const fontExamples={};
for(const [name,s]of cases)for(const projection of [false,true]){
  const result=capture(M,s,projection),data=result.canvas.getContext('2d').getImageData(0,0,1920,720).data;
  for(let y=0;y<720;y++)for(let x=0;x<1920;x++)if(x<box.x||x>=box.x+box.w||y<box.y||y>=box.y+box.h){
    if(data[(y*1920+x)*4+3])outsidePixels++;
  }
  for(const glyph of result.glyphs){
    assert(glyph.left>=box.x&&glyph.right<=box.x+box.w&&glyph.top>=box.y&&glyph.bottom<=box.y+box.h,
      name+' glyph escapes or clips: '+JSON.stringify(glyph));
    assert.equal(glyph.blur,0,name+' no glyph blur');
    assert(glyph.size>=13.99,name+' small font after fit: '+JSON.stringify(glyph));
    minimum=Math.min(minimum,glyph.size);totalGlyphs++;
    if(name==='menu-0'||name==='ac-fan-22')fontExamples[glyph.label]=glyph.size;
  }
  for(let i=0;i<result.glyphs.length;i++)for(let j=i+1;j<result.glyphs.length;j++){
    const a=result.glyphs[i],b=result.glyphs[j];
    assert(!(a.left<b.right&&a.right>b.left&&a.top<b.bottom&&a.bottom>b.top),
      name+' overlapping glyphs: '+a.label+' / '+b.label);
  }
  for(const hit of result.hits){
    assert(contains(hit),name+' hitbox must stay inside existing strip');
    assert.deepEqual(M.hitTest(s,hit.x+hit.w/2,hit.y+hit.h/2),hit.action,name+' hitbox action preserved');
  }
  assert.equal(M.hitTest(s,519,600),null);assert.equal(M.hitTest(s,1400,600),null);assert.equal(M.hitTest(s,1000,704),null);
  if(Before)assert.deepEqual(M.layout(s),Before.layout(s),name+' existing touch geometry/action unchanged');
  const surfaces=result.fills.filter(fill=>/rgba\(5, ?14, ?23/.test(fill.style));
  if(!projection){
    assert.equal(surfaces.length,1,name+' single normal panel');
    assert.equal(surfaces[0].arcs.length,0,name+' normal rounded surface');
    const probe=data[(590*1920+524)*4+3];assert(probe>=181&&probe<=185,name+' background .72 alpha');
  }else if(S.getView(s)==='ac')assert.equal(surfaces.length,0,'projection AC must remain surface-free');
  else{
    assert(surfaces.length>=3&&surfaces.length<=4,name+' projection local circular surfaces');
    for(const fill of surfaces){
      assert.equal(fill.arcs.length,1,name+' projection surface must be a circle');
      const [x,y,r]=fill.arcs[0];assert.equal(r,82);
      assert(contains({x:x-r,y:y-r,w:r*2,h:r*2}),name+' circle must fit entirely in strip');
    }
    assert.equal(data[(590*1920+524)*4+3],0,name+' no projection rectangle');
    const probe=data[(570*1920+650)*4+3];assert(probe<255,name+' circle cannot be opaque');
  }
}
assert.equal(outsidePixels,0,'every pixel outside the existing strip stays transparent');
const closed=S.createState(seed),empty=capture(M,closed,false);
assert.equal(empty.hits.length,0);assert.equal(empty.glyphs.length,0);assert.equal(empty.fills.length,0);
assert(empty.canvas.getContext('2d').getImageData(0,0,1920,720).data.every(value=>value===0),'closed painter stays fully transparent');
assert.equal(M.hitTest(closed,600,600),null);
assert.equal(M.hitTest(state('menu'),NaN,600),null);
for(const label of ['MENU PRINCIPAL','INFORMAÇÕES','GRÁFICOS','AJUSTES','TRIP A / TRIP B'])assert.equal(fontExamples[label],28,label+' intended main size');
assert.equal(fontExamples.TEMPERATURA,18);assert.equal(fontExamples.VENTILAÇÃO,18);
assert.equal(fontExamples['22°C'],44);assert.equal(fontExamples['3'],44);
function benchmark(api,s){
  const c=createCanvas(1920,720).getContext('2d');
  for(let i=0;i<8;i++){c.clearRect(0,0,1920,720);api.render(c,s,{readouts:{totalRange:441},speed:64});}
  const at=performance.now();for(let i=0;i<40;i++){c.clearRect(0,0,1920,720);api.render(c,s,{readouts:{totalRange:441},speed:64});}
  return (performance.now()-at)/40;
}
const cost={mainMs:benchmark(M,state('menu')),acMs:benchmark(M,state('ac'))};
if(Before)cost.before={mainMs:benchmark(Before,state('menu')),acMs:benchmark(Before,state('ac'))};
console.log(JSON.stringify({cases:cases.length,compositions:cases.length*2,totalGlyphs,minimumFontPx:minimum,
  outsidePixels,closedTransparent:true,hitboxesUnchanged:Before?true:'baseline unavailable',
  projectionAC:'no surface',projectionMain:'bounded circular .36 alpha',fontExamples,cpuMenuPaintMs:cost},null,2));
