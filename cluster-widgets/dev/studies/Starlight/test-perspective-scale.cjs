'use strict';
// Geometry/font verification for the v10 perspective revision. These checks do
// not establish readability on an installed cluster or in a moving vehicle.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {createCanvas,GlobalFonts}=require('@napi-rs/canvas');
const Scale=require('./scales');
const R=require('./renderer');
const out=path.join(__dirname,'qa');
fs.mkdirSync(out,{recursive:true});
for(const [file,font] of [['ScaleDigits.ttf','Scale Digits'],['LabelSans.ttf','Label Sans'],['SpeedDigits.ttf','Speed Digits'],['PanelSans.ttf','Panel Sans'],['HeaderSans.ttf','Header Sans']]) assert(GlobalFonts.registerFromPath(path.join(__dirname,'assets',file),font));
R.setCanvasFactory(createCanvas);
const ctx=createCanvas(1920,720).getContext('2d');
const report={result:'PASS',limits:'Native preview/font geometry only; no vehicle readability or hardware validation.',groups:[],fontBounds:{},samples:[]};
let assertions=0;
const ok=(v,m)=>{assertions++;assert(v,m);};
const near=(a,b,m,e=1e-6)=>ok(Number.isFinite(a)&&Math.abs(a-b)<=e,`${m}: ${a} versus ${b}`);
function group(name,fn){try{fn();report.groups.push({name,result:'PASS'});}catch(e){report.result='FAIL';report.groups.push({name,result:'FAIL',message:e.message});}}
function inkPolygon(context,text,x,y){const m=context.measureText(String(text)),t=context.getTransform();return {inkWidth:m.actualBoundingBoxLeft+m.actualBoundingBoxRight,inkHeight:m.actualBoundingBoxAscent+m.actualBoundingBoxDescent,points:[[x-m.actualBoundingBoxLeft,y-m.actualBoundingBoxAscent],[x+m.actualBoundingBoxRight,y-m.actualBoundingBoxAscent],[x+m.actualBoundingBoxRight,y+m.actualBoundingBoxDescent],[x-m.actualBoundingBoxLeft,y+m.actualBoundingBoxDescent]].map(([a,b])=>[t.a*a+t.c*b+t.e,t.b*a+t.d*b+t.f])};}
function box(points){return {left:Math.min(...points.map(p=>p[0])),right:Math.max(...points.map(p=>p[0])),top:Math.min(...points.map(p=>p[1])),bottom:Math.max(...points.map(p=>p[1]))};}
function dot(a,b){return a[0]*b[0]+a[1]*b[1];}
function axes(p){return p.map((q,i)=>{const r=p[(i+1)%p.length],x=r[0]-q[0],y=r[1]-q[1],d=Math.hypot(x,y);return [-y/d,x/d];});}
function separated(a,b){return [...axes(a),...axes(b)].some(n=>{const aa=a.map(p=>dot(p,n)),bb=b.map(p=>dot(p,n));return Math.max(...aa)<Math.min(...bb)||Math.max(...bb)<Math.min(...aa);});}
function pointSegment(p,a,b){const d=[b[0]-a[0],b[1]-a[1]],u=Math.max(0,Math.min(1,((p[0]-a[0])*d[0]+(p[1]-a[1])*d[1])/dot(d,d)));return Math.hypot(p[0]-a[0]-u*d[0],p[1]-a[1]-u*d[1]);}
function distance(a,b){if(!separated(a,b))return 0;return Math.min(...a.flatMap(p=>b.map((q,i)=>pointSegment(p,q,b[(i+1)%b.length]))),...b.flatMap(p=>a.map((q,i)=>pointSegment(p,q,a[(i+1)%a.length]))));}
function tickInk(tick){ctx.save();ctx.translate(tick.x,tick.y);ctx.rotate(tick.angle||0);ctx.font=`${tick.size}px "Scale Digits"`;ctx.textAlign='center';ctx.textBaseline='middle';const ink=inkPolygon(ctx,tick.value,0,0);ctx.restore();return {...tick,...ink,...box(ink.points)};}

group('Exact values, one smooth plane, increasing foreground text',()=>{
 assert.deepEqual(Scale.leftTicks.map(t=>t.value),[0,20,40,50,60,80,100,120,140,160,200]);
 assert.deepEqual(Scale.rightTicks.map(t=>t.value),[0,10,20,30,40,50,75,100]);
 for(const [name,ticks,sign] of [['speed',Scale.leftTicks,-1],['battery',Scale.rightTicks,1]])for(let i=0;i<ticks.length;i++){
  const tick=ticks[i],localX=sign<0?tick.x:1920-tick.x;
  near(tick.y,318+.35*(710-localX),`${name} follows continuous glass-parallel baseline`);
  near(tick.angle,sign*Math.atan(.35),`${name} rotation follows that same baseline`);
  if(i)ok(tick.size>ticks[i-1].size,`${name} ${tick.value} grows in foreground`);
  near(tick.y-(380+.35*(780-localX)),-86.5,`${name} constant perspective offset from seam`);
 }
});
group('Expanded urban range and progressive high-range compression',()=>{
 const low=Scale.speedX(0)-Scale.speedX(60),high=Scale.speedX(60)-Scale.speedX(200);
 ok(low/60>2*high/140,'Urban px/km must exceed twice the high-range average');
 const hi=Scale.leftTicks.filter(t=>t.value>=60),densities=hi.slice(1).map((t,i)=>(hi[i].x-t.x)/(t.value-hi[i].value));
 for(let i=1;i<densities.length;i++){ok(densities[i]<densities[i-1],'High-range px/km falls at every knot');ok(densities[i]/densities[i-1]>=.6,'High-range density changes remain gradual');}
 let previous=Infinity;for(let v=0;v<=200;v+=.25){ok(Scale.speedX(v)<=previous,'Frontier x remains monotone');previous=Scale.speedX(v);}
 report.allocation={lowPx:low,highPx:high,lowDensity:low/60,highDensity:high/140,highSegmentDensities:densities};
});
group('Native font oriented ink bounds and panel-safe margins',()=>{
 let minimum=Infinity,pair;
 for(const [side,ticks] of [['left',Scale.leftTicks],['right',Scale.rightTicks]]){
  const ink=ticks.map(tickInk);report.fontBounds[side]=ink;
  for(const b of ink){ok(b.inkHeight>=16,`${side} ${b.value}: native ink >=16px`);ok(b.left>=10&&b.right<=1910,`${side} ${b.value}: safe panel margins`);}
  for(let i=0;i<ink.length;i++)for(let j=i+1;j<ink.length;j++){const d=distance(ink[i].points,ink[j].points);ok(d>=10,`${side} ${ink[i].value}/${ink[j].value}: oriented clearance ${d.toFixed(3)}px`);if(d<minimum){minimum=d;pair=[side,ink[i].value,ink[j].value];}}
 }
 report.minimumOrientedInkClearance={pixels:minimum,pair};
});
group('Frontier x is the exact shared scale map; glass-height baseline preserved',()=>{
 for(const [side,ticks] of [[-1,Scale.leftTicks],[1,Scale.rightTicks]])for(const tick of ticks){
  const input=side<0?{speedRaw:tick.value,batteryPowerKw:0}:{speedRaw:0,batteryPowerKw:tick.value};
  const state=R.functionalState(input),length=side<0?state.leftLength:state.rightLength,rim=R.frontierGeometry(length,side);
  near(rim.bx,tick.x,'Every frontier knot shares its label x');
  near(rim.topY,Scale.scaleY(side<0?tick.x:1920-tick.x)-116,'Accepted tall blade follows old baseline');
 }
});
group('Actual renderer consumes native tick positions, sizes, and perspective angles',()=>{
 for(const time of [0,12,21,27,31,42]){
  const canvas=createCanvas(1920,720),target=canvas.getContext('2d'),labels=[];
  const wrapped=new Proxy(target,{get(t,k){if(k==='fillText')return(text,x,y,...args)=>{const tr=t.getTransform(),ink=inkPolygon(t,text,x,y);labels.push({text:String(text),font:t.font,fill:t.fillStyle,x:tr.a*x+tr.c*y+tr.e,y:tr.b*x+tr.d*y+tr.f,angle:Math.atan2(tr.b,tr.a),...ink});return t.fillText(text,x,y,...args);};const v=t[k];return typeof v==='function'?v.bind(t):v;},set(t,k,v){t[k]=v;return true;}});
  const state=R.renderPanel(wrapped,time,'city');
  for(const [side,ticks] of [['left',Scale.leftTicks],['right',Scale.rightTicks]])for(const tick of ticks){
   const matches=labels.filter(l=>l.text===String(tick.value)&&l.font.includes('Scale Digits')&&Math.abs(l.x-tick.x)<1e-3&&Math.abs(l.y-tick.y)<1e-3);
   ok(matches.length===1,`${side} ${tick.value} rendered once, t=${time}`);const actual=matches[0];
   near(parseFloat(actual.font),tick.size,'Native font size');near(actual.angle,tick.angle,'Native rotation');
   for(const other of labels){if(other===actual||!other.text.trim())continue;const d=distance(actual.points,other.points);ok(d>=2,`${side} ${tick.value} collides with ${other.text}, t=${time}, gap=${d}`);}
  }
  fs.writeFileSync(path.join(out,`perspective-v10-${time}.png`),canvas.toBuffer('image/png'));
  report.samples.push({time,speed:state.speed,batteryKw:state.batteryPowerKw});
 }
});
report.assertions=assertions;
fs.writeFileSync(path.join(out,'perspective-scale-report.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({result:report.result,assertions,groups:report.groups,allocation:report.allocation,minimumOrientedInkClearance:report.minimumOrientedInkClearance,samples:report.samples},null,2));
if(report.result!=='PASS')process.exitCode=1;
