/* Starlight production painter. Geometry/material copied from the approved study.
   Only explicit runtime state is rendered; no demonstration tracks or polling. */
(function(root,factory){const api=factory();if(typeof module==='object')module.exports=api;else root.MotionStudy=api;})(typeof globalThis!=='undefined'?globalThis:this,function(){'use strict';
const W=1920,H=720;
const Scale=typeof module==='object'?require('./scales'):globalThis.ThemeScales;
const GlassSmoke=typeof module==='object'?require('./glass-smoke'):globalThis.GlassSmoke;
const ApexData=typeof module==='object'?require('./apex-data'):globalThis.ApexData;
const GalaxyStars=typeof module==='object'?require('./galaxy-stars'):globalThis.GalaxyStars;
const SPEED_SCALE_MAX_KMH=200,DRIVE_MAX_KW=160,REGEN_MAX_KW=100,BATTERY_MAX_KW=100;
const SCALE_X=[702.72,643.2,574.08,495.36,422.4,347.52,259.2,174.72,84.48];
// Existing READY and gauge captions; keep near-tip text clear of their ink.
const TIP_OCCLUDERS={left:[{left:258,top:335,right:322,bottom:355},{left:342,top:269,right:537,bottom:287}],right:[{left:1378,top:244,right:1582,bottom:291}]};
const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
let factory=null,background={},lensTexture=null,sampleCount=0,sampleIndex=0;
const materialSamples=new Float64Array(32);
const counts={stars:0,left:0,right:0,frames:0,beamFrames:0,lastMaterialMs:0,maxMaterialMs:0,coldPaintMs:0,dustNominal:0,dustComputed:0,dustCulled:0,dustLeft:0,dustRight:0};
const now=()=>typeof performance!=='undefined'&&typeof performance.now==='function'?performance.now():Date.now();
function make(w,h){if(factory)return factory(w,h);const c=document.createElement('canvas');c.width=w;c.height=h;return c;}
function releaseCanvas(c){if(c){c.width=1;c.height=1;}}
function cleanup(){
 for(const name of Object.keys(background))releaseCanvas(background[name]);background={};
 releaseCanvas(lensTexture);lensTexture=null;GlassSmoke.cleanup();
 counts.stars=counts.left=counts.right=counts.frames=counts.beamFrames=0;
 counts.lastMaterialMs=counts.maxMaterialMs=counts.coldPaintMs=0;
 counts.dustNominal=counts.dustComputed=counts.dustCulled=counts.dustLeft=counts.dustRight=0;
 panelOptions.state=panelOptions.starMotion=panelOptions.menuState=panelOptions.tipState=null;
 sampleCount=sampleIndex=0;materialSamples.fill(0);
}
function reset(){cleanup();}
function setCanvasFactory(f){if(f!=null&&typeof f!=='function')throw TypeError('Canvas factory must be a function');cleanup();factory=f||null;GlassSmoke.setCanvasFactory(f);}
// Kept for callers of the initial diagnostic API. Timing never gates motion:
// the host owns the shared <=30fps scheduler and stops it when idle/hidden.
function setMaterialHz(){return 30;}
function cacheStats(){const samples=Array.from(materialSamples).slice(0,sampleCount).sort((a,b)=>a-b);return {stars:counts.stars,left:counts.left,right:counts.right,frames:counts.frames,beamFrames:counts.beamFrames,materialHz:30,materialMotionReduced:false,lastMaterialMs:counts.lastMaterialMs,maxMaterialMs:counts.maxMaterialMs,coldPaintMs:counts.coldPaintMs,p95MaterialMs:samples.length?samples[Math.ceil(samples.length*.95)-1]:0,allocated:Object.keys(background).length+(lensTexture?1:0),dustNominal:counts.dustNominal,dustComputed:counts.dustComputed,dustCulled:counts.dustCulled,dustLeft:counts.dustLeft,dustRight:counts.dustRight};}
const leftOptions={functional:true,battery:false,length:0,tipHeat:0};
const rightOptions={functional:true,battery:true,length:0,tipHeat:0,green:false,intensity:0};
const panelOptions={clear:false,state:null,starMotion:null,menuState:null,tipState:null};
const tipColor=[255,68,102];
const beamPalettes=[{ice:[166,197,247],prefixes:{}},{ice:[116,244,171],prefixes:{}}];
for(const palette of beamPalettes){
 palette.rgb=palette.ice.join(',');
 for(const white of [0,.69,.74,.40])palette.prefixes[white]='rgba('+palette.ice.map(v=>Math.round(v*(1-white)+255*white)).join(',')+',';
}
function speedHeat(speed){return Number.isFinite(speed)?clamp((speed-120)/80,0,1):0;}
function powerLabel(power){if(!Number.isFinite(power))return '-- kW';const v=Math.round(Math.abs(power));return (v===0?'':power<0?'−':'+')+v+' kW';}
function batteryLabel(power){if(power===null||!Number.isFinite(power))return '-- kW';const a=Math.abs(power),sign=power>0?'+':power<0?'−':'';if(a===0)return '0 kW';if(a<.1)return sign+'<0,1 kW';if(a<1)return sign+a.toFixed(1).replace('.',',')+' kW';return powerLabel(power);}
function tipBatteryLabel(power){if(!Number.isFinite(power))return '-- kW';const a=Math.abs(power),sign=power>0?'+':power<0?'−':'';return sign+(a>0&&a<.1?'<0,1':a.toFixed(1).replace('.',','))+' kW';}
function resolveTipState(latest,presented){
 const tip=presented||latest,safeSpeed=!Number.isFinite(latest.speed)||latest.speed===0,safePower=!Number.isFinite(latest.batteryPowerKw)||latest.batteryPowerKw===0;
 if(!safeSpeed&&!safePower)return tip;
 return {speed:safeSpeed?(latest.speed===0?0:null):tip.speed,speedRaw:safeSpeed?latest.speedRaw:tip.speedRaw,
  batteryPowerKw:safePower?(latest.batteryPowerKw===0?0:null):tip.batteryPowerKw,batteryMagnitudeKw:safePower?0:tip.batteryMagnitudeKw,
  regen:safePower?false:tip.regen,leftLength:tip.leftLength,rightLength:tip.rightLength,
  batteryState:safePower?(latest.batteryPowerKw===0?'NEUTRA':'SEM DADOS'):tip.batteryState};
}
// Saturate only the visual scales; keep received speed/power values in readouts.
function functionalState(input){
 input=input||{};const time=Number.isFinite(input.time)?input.time:0,mode=input.mode||'live';
 const speedRaw=Number.isFinite(input.speedRaw)&&input.speedRaw>=0?input.speedRaw:null;
 const powerKw=Number.isFinite(input.powerKw)?input.powerKw:null;
 const tractionKw=Number.isFinite(input.tractionKw)?Math.max(0,input.tractionKw):0;
 const batteryPowerKw=Number.isFinite(input.batteryPowerKw)?input.batteryPowerKw:null;
 const batteryMagnitudeKw=batteryPowerKw===null?0:Math.abs(batteryPowerKw),consumedKw=Math.max(0,batteryPowerKw||0),recoveredKw=Math.max(0,-(batteryPowerKw||0));
 const left=speedRaw===null?0:Scale.speedRatio(speedRaw)*8,right=Scale.batteryRatio(batteryMagnitudeKw)*8;
 const leftLength=speedRaw===null?70:780-Scale.speedX(speedRaw),rightLength=780-Scale.batteryX(batteryMagnitudeKw);
 return {time,mode,functional:true,speed:speedRaw===null?null:Math.round(speedRaw),speedRaw,powerKw,tractionKw,batteryPowerKw,batteryMagnitudeKw,consumedKw,recoveredKw,left,right,level:Math.max(left,right),regen:batteryPowerKw!==null&&batteryPowerKw<0,greenIntensity:clamp(recoveredKw/BATTERY_MAX_KW,0,1),batteryIntensity:clamp(batteryMagnitudeKw/BATTERY_MAX_KW,0,1),batteryState:batteryPowerKw===null?'SEM DADOS':batteryPowerKw>0?'CONSUMINDO':batteryPowerKw<0?'REGENERANDO':'NEUTRA',leftLength,rightLength,tipHeat:speedHeat(speedRaw),phase:powerKw===null?'SEM DADOS':powerKw>0?'ACELERAÇÃO':powerKw<0?'DESACELERAÇÃO':'RODA LIVRE'};
}
let seed=99181;function random(){seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;}
// Advance the same seeded sequence used by the approved study's 2200 legacy stars.
for(let i=0;i<2200*5;i++)random();
const dust=Array.from({length:8200},()=>({u:random(),v:random(),z:random(),p:random()*6.28,r:random()*.8+.35}));
// Irregularly distributed eddies, not equidistant columns.
const particleEddies=Array.from({length:43},()=>({u:random()*760,width:12+random()*37,phase:random()*6.28,twist:.025+random()*.065,rate:.21+random()*.38}));
// The same immutable particle inputs and arithmetic order as the study. No
// random calls are added, and the two sides share only side-independent motion.
for(let i=0;i<dust.length;i++){
 const p=dust[i],e=particleEddies[i%particleEddies.length],anchor=i%4===0?p.u*760:e.u;
 p.eddy=e;p.anchor=anchor;p.base=anchor+(i%4===0?0:(p.u-.5)*e.width);
 p.driftAmplitude=4+e.width*.17;
 // Both sine terms lie in [-1,1]. The guard covers floating-point rounding.
 p.driftBound=p.driftAmplitude+3+1e-9;
 p.motionRate=.032+.019*p.z;p.heightRange=108+anchor*.050;
 p.radius=p.r*(.48+.70*p.z);p.opacityScale=.21+.62*p.z;
 p.offsetX=(p.z-.5)*9;p.offsetY=(p.z-.5)*10;p.active=false;
}
function prepareDust(t,leftLength,rightLength){
 const length=Math.max(leftLength,rightLength);
 counts.dustNominal=dust.length*((leftLength>0?1:0)+(rightLength>0?1:0));
 counts.dustComputed=counts.dustCulled=counts.dustLeft=counts.dustRight=0;
 if(length<=0)return;
 for(const p of dust){
  p.active=false;
  if(p.base+p.driftBound<0||p.base-p.driftBound>length-1){counts.dustCulled++;continue;}
  counts.dustComputed++;
  const e=p.eddy,v=(p.v+t*p.motionRate)%1,h=Math.pow(v,1.42)*p.heightRange;
  const drift=Math.sin(h*e.twist+e.phase+t*e.rate)*p.driftAmplitude+Math.sin(h*.107-e.phase-t*.29)*3;
  const u=p.base+drift;if(u<0||u>length-1)continue;
  const density=.30+.70*Math.pow(.5+.5*Math.sin(e.phase+h*.028+t*.23),2);
  const fade=Math.sin(Math.PI*v)**.8*(1-Math.exp(-u/55));
  p.currentU=u;p.currentH=h;p.currentAlpha=fade*p.opacityScale*density;
  p.floorY=380+u*.35;p.currentY=p.floorY-h+p.offsetY;p.active=true;
 }
}
function txt(ctx,s,x,y,size=20,color='#fff',font='Panel Sans',align='center'){ctx.font=`${size}px "${font}"`;ctx.fillStyle=color;ctx.textAlign=align;ctx.textBaseline='middle';ctx.fillText(s,x,y);}
function tracked(ctx,s,x,y,size,color,spacing=7){ctx.font=`${size}px "Label Sans"`;ctx.textBaseline='middle';ctx.fillStyle=color;const widths=[...s].map(a=>ctx.measureText(a).width),total=widths.reduce((a,b)=>a+b,0)+(s.length-1)*spacing;let xx=x-total/2;[...s].forEach((a,i)=>{ctx.textAlign='left';ctx.fillText(a,xx,y);xx+=widths[i]+spacing;});}
function line(ctx,x1,y1,x2,y2,col,w=1){ctx.beginPath();ctx.moveTo(x1,y1);ctx.lineTo(x2,y2);ctx.strokeStyle=col;ctx.lineWidth=w;ctx.stroke();}
function ellipse(ctx,cx,cy,rx,ry){ctx.beginPath();ctx.ellipse(cx,cy,rx,ry,0,0,Math.PI*2);}
function base(){const key='functional';if(background[key])return background[key];const c=make(W,H),ctx=c.getContext('2d');ctx.fillStyle='#000';ctx.fillRect(0,0,W,H);
 // The wide glass surface, top light source and original star distribution.
 let g=ctx.createLinearGradient(0,0,0,410);g.addColorStop(0,'#12171f');g.addColorStop(.25,'#040608');g.addColorStop(1,'#000');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
 const top=ctx.createRadialGradient(960,2,0,960,2,900);top.addColorStop(0,'rgba(176,201,242,.32)');top.addColorStop(.27,'rgba(98,130,188,.14)');top.addColorStop(1,'rgba(50,60,80,0)');ctx.save();ctx.scale(1,.24);ctx.fillStyle=top;ctx.fillRect(0,0,W,1500);ctx.restore();
 // Owner-selected model 3: no fixed lower planes, diagonal backdrop or top brand.


 ctx.save();ctx.translate(960,14);ctx.scale(1,.14);const haze=ctx.createRadialGradient(0,0,0,0,0,540);haze.addColorStop(0,'rgba(213,227,255,.50)');haze.addColorStop(.14,'rgba(154,180,225,.3)');haze.addColorStop(1,'rgba(60,88,143,0)');ctx.fillStyle=haze;ctx.fillRect(-650,-300,1300,1500);ctx.restore();background[key]=c;return c;
}
// A shared perspective wedge provides exact bilateral geometry. Each side is driven independently.
// Narrow caustics, reflected dust, chromatic refraction and a thin glass frontier replace the flat fog bar.
// The moving rim rises past its local scale and tapers to a single glass point.
// Its height follows scale geometry, not an invented speed threshold.
function frontierGeometry(length,side=-1){const u=clamp(length,0,768),xx=780-u,xs=SCALE_X,ys=[318,324,331,339,352,361,371,384,394];let scaleY=ys[0];
 if(xx<=xs[xs.length-1])scaleY=ys[ys.length-1];else for(let n=0;n<xs.length-1;n++)if(xx<=xs[n]&&xx>=xs[n+1]){const f=(xs[n]-xx)/(xs[n]-xs[n+1]);scaleY=ys[n]+f*(ys[n+1]-ys[n]);break;}
 const bx=960+side*(180+u),by=380+.35*u,topY=scaleY-116;
 return{length:u,side,bx,by,scaleY,topY,topRelative:topY-by,pointX:bx+side*9,baseX:bx-side*10,baseY:by+58};
}
function crystallineBeam(ctx,side,level,t,options={}){
 const ext=clamp(level/8,0,1);if(ext<=.001)return;
 const length=options.length??(83+650*ext),green=!!options.green,heat=green?0:clamp(options.tipHeat||0,0,1);
 const x=u=>960+side*(180+u),y=u=>380+u*.35,alpha=options.functional?(options.battery?(options.intensity??ext):Math.min(1,ext*8)):1;
 const palette=beamPalettes[green?1:0],ice=palette.ice,rgb=palette.rgb,tip=tipColor;
 function path(offset=0){ctx.beginPath();ctx.moveTo(x(0),y(0)+offset);ctx.lineTo(x(length),y(length)+offset);}
 function tipAmount(u){const q=clamp((u/length-.68)/.32,0,1);return heat*q*q*(3-2*q);}
 function color(u,opacity,white=0){
  if(heat===0&&palette.prefixes[white]!==undefined)return palette.prefixes[white]+opacity+')';
  const a=tipAmount(u);return `rgba(${ice.map((v,i)=>Math.round((v+(tip[i]-v)*a)*(1-white)+255*white)).join(',')},${opacity})`;
 }
 ctx.save();ctx.globalAlpha=alpha;
 // The interior occupies a translucent glass body above the floor, not merely a luminous line.
 // Rear face, front face and the narrow end face share the moving source frontier.
 const rim=frontierGeometry(length,side);
 ctx.save();ctx.translate(x(0),380);ctx.transform(side,.35,0,1,0,0);
 const body=ctx.createLinearGradient(0,-210,0,60);body.addColorStop(0,`rgba(${rgb},0)`);body.addColorStop(.30,`rgba(${rgb},.009)`);body.addColorStop(.65,`rgba(${rgb},.046)`);body.addColorStop(.85,`rgba(${rgb},.070)`);body.addColorStop(1,`rgba(${rgb},.028)`);
 ctx.fillStyle=body;ctx.beginPath();ctx.moveTo(0,-124);ctx.lineTo(length+9,rim.topRelative);ctx.lineTo(length-9,56);ctx.lineTo(0,31);ctx.closePath();ctx.fill();
 const back=ctx.createLinearGradient(0,-160,0,40);back.addColorStop(0,'rgba(52,69,94,0)');back.addColorStop(.72,`rgba(${rgb},.022)`);back.addColorStop(1,`rgba(${rgb},.06)`);ctx.fillStyle=back;ctx.beginPath();ctx.moveTo(0,-120);ctx.lineTo(length-6,rim.topRelative+22);ctx.lineTo(length-24,44);ctx.lineTo(0,23);ctx.closePath();ctx.fill();ctx.restore();
 // A solid but transparent crystal slab beneath the emissive seam, with the observed knife-edge ending.
 const slab=ctx.createLinearGradient(0,380,0,660);slab.addColorStop(0,'rgba(8,16,28,.2)');slab.addColorStop(.6,`rgba(${rgb},.065)`);slab.addColorStop(1,`rgba(${rgb},.16)`);
 ctx.beginPath();ctx.moveTo(x(0),380);ctx.lineTo(x(length),y(length));ctx.lineTo(x(length-10),y(length)+58);ctx.lineTo(x(0),411);ctx.closePath();ctx.fillStyle=slab;ctx.fill();
 // The surface reflection is compressed and softer than the airborne particle volume.
 for(let k=2;k<63;k++){const a=.07*Math.exp(-k/21)*(1-k/67);ctx.strokeStyle=`rgba(${rgb},${a})`;ctx.lineWidth=3;path(k);ctx.stroke();}
 const reflect=ctx.createLinearGradient(x(0),0,x(length),0);reflect.addColorStop(0,'rgba(190,215,248,0)');reflect.addColorStop(.5,`rgba(${rgb},.09)`);reflect.addColorStop(1,`rgba(${rgb},.24)`);ctx.strokeStyle=reflect;ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(x(0),411);ctx.lineTo(x(length-10),y(length)+58);ctx.stroke();
 // The upper light volume follows the oblique beam normal and becomes wider in perspective.
 ctx.save();ctx.beginPath();ctx.moveTo(x(0),260);ctx.lineTo(x(length-6),y(length)-200);ctx.lineTo(x(length+2),y(length)+59);ctx.lineTo(x(0),412);ctx.closePath();ctx.clip();
 for(let k=-110;k<=36;k++){const a=.029*Math.exp(-Math.pow((k+5)/27,2))+.003*Math.exp(-Math.pow((k+35)/65,2))+.16*Math.exp(-Math.pow((k+15)/23,2));const g=ctx.createLinearGradient(x(0),0,x(length),0);g.addColorStop(0,`rgba(${rgb},${a*.22})`);g.addColorStop(.60,`rgba(${rgb},${a})`);g.addColorStop(.85,color(length*.85,a*1.08));g.addColorStop(1,color(length,a*1.3));ctx.strokeStyle=g;ctx.lineWidth=4;ctx.beginPath();ctx.moveTo(x(0),y(0)+k*.65);ctx.lineTo(x(length),y(length)+k);ctx.stroke();}
 // Noise-driven internal mist evolves even while the frontier is stationary.
 GlassSmoke.render(ctx,{side,length,time:t,green,heat,alpha:1});
 // Outer-tip warm bloom: localized colour, not an overall change to the beam or the power scale.
 if(heat>0){const bx=x(length),by=y(length);ctx.save();ctx.translate(bx,by-9);ctx.scale(1,.71);const g=ctx.createRadialGradient(0,0,0,0,0,146);g.addColorStop(0,`rgba(255,78,105,${heat*.84})`);g.addColorStop(.34,`rgba(248,52,99,${heat*.51})`);g.addColorStop(.7,`rgba(147,56,195,${heat*.24})`);g.addColorStop(1,'rgba(98,72,190,0)');ctx.fillStyle=g;ctx.fillRect(-155,-155,310,310);ctx.restore();}
 // Tiny discontinuous curls and eddies sit at varying depths INSIDE the glass.
 // A fixed spatial domain is revealed by growth; no stretching texture or outward rain.
 for(let i=0;i<dust.length;i++){
  const p=dust[i];if(!p.active||p.currentU>length-1)continue;
  const u=p.currentU,h=p.currentH,xx=x(u)+p.offsetX,yy=p.currentY,a=p.currentAlpha,r=p.radius;
  if(side===-1)counts.dustLeft++;else counts.dustRight++;
  ctx.fillStyle=color(u,a,.69);ctx.fillRect(xx,yy,r,r*.90);
  if(i%9===0){ctx.strokeStyle=color(u,a*.66,.74);ctx.lineWidth=.62;ctx.beginPath();ctx.moveTo(xx,yy);ctx.quadraticCurveTo(xx+side*1.8,yy-1.7,xx+side*.2,yy-3.2);ctx.stroke();}
  if(i%3===0){ctx.fillStyle=color(u,a*.14,.40);ctx.fillRect(xx+side*2,p.floorY+h*.30+4,r*.8,Math.max(.4,r*.45));}
 }
 // Broad, stationary specular caustic: the source ridge swells near its outer half.
 const caustic=ctx.createLinearGradient(x(0),0,x(length),0);caustic.addColorStop(0,'rgba(239,248,255,0)');caustic.addColorStop(.45,'rgba(239,248,255,.10)');caustic.addColorStop(.72,'rgba(249,252,255,.72)');caustic.addColorStop(.90,'rgba(237,247,255,.40)');caustic.addColorStop(1,'rgba(239,248,255,.18)');ctx.strokeStyle=caustic;ctx.lineWidth=6.25;ctx.shadowColor=green?'#b9ffda':'#e0ecff';ctx.shadowBlur=14;path(-.5);ctx.stroke();
 ctx.shadowBlur=0;
 // White-hot seam stays predominantly white through the violet/red region, as in the reference.
 const seam=ctx.createLinearGradient(x(0),0,x(length),0);seam.addColorStop(0,'rgba(219,236,255,.14)');seam.addColorStop(.35,green?'rgba(194,255,219,.72)':'rgba(225,239,255,.72)');seam.addColorStop(.74,'rgba(249,252,255,.99)');seam.addColorStop(1,'rgba(246,251,255,.98)');ctx.strokeStyle=seam;ctx.lineWidth=2.25;ctx.shadowColor=green?'#a4f9c5':'#dce8ff';ctx.shadowBlur=9;path();ctx.stroke();ctx.shadowBlur=0;
 ctx.restore();
 // Tall pointed refracting frontier, matched to the owner's marked source frame.
 // Fine upper edge remains visible at the scale; brightness gathers at the lower glass.
 const bx=rim.bx,by=rim.by,edge=ctx.createLinearGradient(bx,rim.topY,bx,rim.baseY);
 edge.addColorStop(0,`rgba(${rgb},0)`);edge.addColorStop(.09,`rgba(${rgb},.009)`);edge.addColorStop(.24,`rgba(${rgb},.10)`);edge.addColorStop(.40,`rgba(${rgb},.34)`);edge.addColorStop(.63,`rgba(${rgb},.68)`);edge.addColorStop(.82,heat?`rgba(255,154,182,${.72*heat+.17})`:'rgba(230,243,255,.90)');edge.addColorStop(.92,'rgba(247,251,255,.99)');edge.addColorStop(1,`rgba(${rgb},.025)`);
 ctx.fillStyle=edge;ctx.globalAlpha=alpha*.23;ctx.beginPath();ctx.moveTo(rim.pointX,rim.topY);ctx.lineTo(rim.baseX,rim.baseY);ctx.lineTo(bx-side*20,by+54);ctx.lineTo(rim.pointX,rim.topY);ctx.closePath();ctx.fill();ctx.globalAlpha=alpha;
 // A continuous narrow blade. The hairline taper has no flat or luminous cap.
 ctx.strokeStyle=edge;ctx.lineWidth=1.22;ctx.shadowColor=green?'#a9efc5':'#c7ddf2';ctx.shadowBlur=6;ctx.beginPath();ctx.moveTo(rim.pointX,rim.topY);ctx.lineTo(rim.baseX,rim.baseY);ctx.stroke();ctx.shadowBlur=0;
 ctx.globalAlpha=alpha*.13;ctx.lineWidth=.72;ctx.beginPath();ctx.moveTo(rim.pointX+side*.8,rim.topY+18);ctx.lineTo(bx-side*16,by+50);ctx.stroke();ctx.restore();
}
function drawScaleTick(ctx,tick,color){ctx.save();ctx.translate(tick.x,tick.y);ctx.rotate(tick.angle||0);ctx.shadowColor='rgba(0,0,0,.92)';ctx.shadowBlur=3;ctx.shadowOffsetY=1;txt(ctx,String(tick.value),0,0,tick.size,color,'Scale Digits');ctx.restore();}
function scales(ctx,s){
 tracked(ctx,Scale.speedLabel.text,440,278,16,'#a9bcd3',2);tracked(ctx,'POTÊNCIA BATERIA · kW',1480,256,14,'#a9bcd3',1.5);tracked(ctx,s.batteryState,1480,283,17,s.regen?'#8bddb0':s.batteryPowerKw>0?'#b4d4ff':'#8c9bac',1.2);
}
function lens(ctx,t){
 if(!lensTexture){const c=make(520,550),lc=c.getContext('2d'),im=lc.createImageData(520,550);
  for(let y=0;y<550;y++)for(let x=0;x<520;x++){
   const dx=x-260,dy=y-250,r1=Math.hypot(dx/126,dy/129),r2=Math.hypot(dx/173,dy/189);
   const lower=clamp((dy/129+1)/2,0,1),lower2=clamp((dy/189+1)/2,0,1);
   const inner=Math.exp(-Math.pow((r1-1)/.050,2))*(12+135*lower**3);
   const innerGlow=Math.exp(-Math.pow((r1-.985)/.14,2))*32*lower**1.8;
   const outer=Math.exp(-Math.pow((r2-1)/.033,2))*(3+140*lower2**5);
   const outerGlow=Math.exp(-Math.pow((r2-1)/.095,2))*32*lower2**3.5;
   const spec=120*Math.exp(-Math.pow(dx/35,2)-Math.pow((dy-129)/6.5,2))+150*Math.exp(-Math.pow(dx/35,2)-Math.pow((dy-189)/6.5,2));
   const light=inner+innerGlow+outer+outerGlow;
   const alpha=r1<.90?1:clamp((1.25-r2)/.36,0,1)*.85;
   const aa=Math.max(alpha,clamp((light+spec)/230,0,1));
   const i=(y*520+x)*4;if(aa<.002)continue;
   im.data[i]=clamp((light*.72+spec*.97)/Math.max(aa,.01),0,255);im.data[i+1]=clamp((light*.84+spec*.99)/Math.max(aa,.01),0,255);im.data[i+2]=clamp((light+spec)/Math.max(aa,.01),0,255);im.data[i+3]=aa*255;
  }
  lc.putImageData(im,0,0);lensTexture=c;
 }
 ctx.drawImage(lensTexture,700,60);
}
function headerData(ctx,s){
 const d=s.readouts,f=ApexData.fmt;txt(ctx,'INTERNA',56,57,12,'#8090a3','Label Sans','left');txt(ctx,d.insideTemp===null?'--':f(d.insideTemp)+d.tempUnit,56,82,21,'#dbe4ee','Panel Sans','left');txt(ctx,'EXTERNA',190,57,12,'#8090a3','Label Sans','left');txt(ctx,d.outsideTemp===null?'--':f(d.outsideTemp)+d.tempUnit,190,82,21,'#dbe4ee','Panel Sans','left');txt(ctx,d.clock,388,68,23,'#dce5ef','Scale Digits','left');txt(ctx,d.gear,960,121,40,'#f2f5f7','Scale Digits');txt(ctx,d.driveMode,896,156,14,'#a8c5df','Label Sans');txt(ctx,d.propulsionMode,1025,156,17,'#c4d4e3','Scale Digits');txt(ctx,'ODÔMETRO',1860,112,12,'#8090a3','Label Sans','right');txt(ctx,f(d.odometer)+' km',1860,137,21,'#d9e2ee','Scale Digits','right');
}
// Owner-selected typography: option 1, Rajdhani SemiBold; adapt only when 3 digits need room.
// Chrome 60 may only provide TextMetrics.width; never put NaN in Canvas coordinates.
function inkMetrics(ctx,label,size){
 const m=ctx.measureText(label),left=Number.isFinite(m.actualBoundingBoxLeft)?m.actualBoundingBoxLeft:0;
 const right=Number.isFinite(m.actualBoundingBoxRight)?m.actualBoundingBoxRight:m.width;
 const ascent=Number.isFinite(m.actualBoundingBoxAscent)?m.actualBoundingBoxAscent:size*.7;
 const descent=Number.isFinite(m.actualBoundingBoxDescent)?m.actualBoundingBoxDescent:size*.15;
 return {left,right,ascent,descent,width:left+right,height:ascent+descent};
}
function drawSpeed(ctx,value){const label=value===null||!Number.isFinite(value)?'--':String(Math.max(0,Math.round(value)));ctx.save();ctx.textAlign='left';ctx.textBaseline='alphabetic';let size=142.1875;ctx.font=`${size}px "Scale Digits"`;let m=inkMetrics(ctx,label,size);if(m.width>202){size*=202/m.width;ctx.font=`${size}px "Scale Digits"`;m=inkMetrics(ctx,label,size);}ctx.fillStyle='#f6f6f4';ctx.fillText(label,960-(m.right-m.left)/2,308+(m.ascent-m.descent)/2);ctx.restore();return {font:'Rajdhani SemiBold',size,width:m.width,height:m.height};}
function tipReadout(ctx,side,state){
 const value=side===-1?state.speed:state.batteryPowerKw;
 const label=side===-1?(Number.isFinite(value)?String(Math.max(0,Math.round(value))):'--'):tipBatteryLabel(value);
 const length=side===-1?state.leftLength:state.rightLength;
 const blade=frontierGeometry(Number.isFinite(length)?length:70,side);
 const size=side===-1?40:36;
 ctx.save();ctx.font=`${size}px "Scale Digits"`;ctx.textAlign='left';ctx.textBaseline='alphabetic';
 const ink=inkMetrics(ctx,label,size),stroke=1,padding=8,gap=4;
 // The .24 stop is the visible beginning of the unchanged tapered glass edge.
 const anchor={x:blade.pointX+(blade.baseX-blade.pointX)*.24,y:blade.topY+(blade.baseY-blade.topY)*.24};
 const leftX=anchor.x-gap-stroke-ink.right,rightX=anchor.x+gap+stroke+ink.left;
 const minX=padding+stroke+ink.left,maxX=W-padding-stroke-ink.right;
 let preferredX=side===-1?leftX:rightX;
 if(preferredX<minX||preferredX>maxX)preferredX=side===-1?rightX:leftX;
 let x=clamp(preferredX,minX,maxX);
 let y=clamp(anchor.y+(ink.ascent-ink.descent)/2,padding+stroke+ink.ascent,H-padding-stroke-ink.descent);
 const obstacles=side===-1?TIP_OCCLUDERS.left:TIP_OCCLUDERS.right;
 const intersects=(r,xx=x)=>xx-ink.left-stroke<r.right+gap&&xx+ink.right+stroke>r.left-gap&&y-ink.ascent-stroke<r.bottom+gap&&y+ink.descent+stroke>r.top-gap;
 if(obstacles.some(r=>intersects(r))){
  const alternateX=clamp(preferredX===leftX?rightX:leftX,minX,maxX);
  if(!obstacles.some(r=>intersects(r,alternateX)))x=alternateX;
  else for(const r of obstacles)if(intersects(r)){
   const up=r.top-gap-(y+ink.descent+stroke),down=r.bottom+gap-(y-ink.ascent-stroke);
   y=clamp(y+(Math.abs(up)<Math.abs(down)?up:down),padding+stroke+ink.ascent,H-padding-stroke-ink.descent);
  }
 }
 ctx.restore();
 return {side,value,label,x,y,size,color:side===1?(state.regen?'#9ceac0':Number.isFinite(value)&&value!==0?'#b4d4ff':'#8c9bac'):'#dbe8f4',blade,anchor,
  bounds:{left:x-ink.left-stroke,right:x+ink.right+stroke,top:y-ink.ascent-stroke,bottom:y+ink.descent+stroke}};
}
function drawTipReadout(ctx,side,state){
 const tip=tipReadout(ctx,side,state);
 ctx.save();ctx.font=`${tip.size}px "Scale Digits"`;ctx.textAlign='left';ctx.textBaseline='alphabetic';ctx.shadowBlur=0;ctx.shadowOffsetX=ctx.shadowOffsetY=0;
 ctx.lineJoin='round';ctx.lineWidth=2;ctx.strokeStyle='#000';ctx.strokeText(tip.label,tip.x,tip.y);ctx.fillStyle=tip.color;ctx.fillText(tip.label,tip.x,tip.y);ctx.restore();
 return tip;
}
function segmentFillRatio(value,index,count=12){return value===null?0:clamp(value/100*count-index,0,1);}
function segmentedBar(ctx,x,y,width,value){const count=12,gap=4,w=(width-gap*(count-1))/count;for(let n=0;n<count;n++){ctx.fillStyle='#202d39';ctx.fillRect(x+n*(w+gap),y,w,7);const fill=segmentFillRatio(value,n,count);if(fill>0){ctx.fillStyle='#a8c2d8';ctx.fillRect(x+n*(w+gap),y,w*fill,7);}}ctx.strokeStyle='#566978';ctx.lineWidth=1;ctx.strokeRect(x-5,y-5,width+10,17);}
function fuelIcon(ctx,x,y){ctx.save();ctx.translate(x,y);ctx.strokeStyle='#a8bccd';ctx.lineWidth=1.7;ctx.beginPath();ctx.moveTo(1,19);ctx.lineTo(1,1);ctx.lineTo(12,1);ctx.lineTo(12,19);ctx.moveTo(-1,19);ctx.lineTo(15,19);ctx.moveTo(2,8);ctx.lineTo(11,8);ctx.moveTo(12,4);ctx.lineTo(17,8);ctx.lineTo(17,16);ctx.bezierCurveTo(17,20,22,20,22,16);ctx.lineTo(22,9);ctx.lineTo(17,3);ctx.stroke();ctx.restore();}
function batteryIcon(ctx,x,y){ctx.save();ctx.translate(x,y);ctx.strokeStyle='#a8bccd';ctx.lineWidth=1.7;ctx.strokeRect(0,3,24,16);ctx.strokeRect(5,0,4,3);ctx.strokeRect(16,0,4,3);line(ctx,5,11,11,11,'#a8bccd',1.5);line(ctx,8,8,8,14,'#a8bccd',1.5);line(ctx,16,11,21,11,'#a8bccd',1.5);ctx.restore();}
function floatingText(ctx,text,x,y,size=25,color='#d5e1ea',align='left',font='Scale Digits'){
 ctx.save();ctx.font=`${size}px "${font}"`;ctx.textAlign=align;ctx.textBaseline='middle';
 ctx.shadowBlur=0;ctx.shadowOffsetX=ctx.shadowOffsetY=0;ctx.lineJoin='round';ctx.lineWidth=2;ctx.strokeStyle='#000';ctx.strokeText(text,x,y);
 ctx.fillStyle=color;ctx.fillText(text,x,y);ctx.restore();
}
function floatingRun(ctx,pieces,x,y,align='left'){
 const widths=pieces.map(p=>{ctx.font=`${p[1]}px "${p[3]||'Scale Digits'}"`;return ctx.measureText(p[0]).width;}),gap=10,total=widths.reduce((a,b)=>a+b,0)+gap*(pieces.length-1);let xx=align==='right'?x-total:align==='center'?x-total/2:x;
 pieces.forEach((p,i)=>{floatingText(ctx,p[0],xx,y,p[1],p[2]||'#d5e1ea','left',p[3]||'Scale Digits');xx+=widths[i]+gap;});
}
function informationStrip(ctx,s){const d=s.readouts,f=ApexData.fmt,secondary='#a2b2c0',muted='#8e9faf';
 // Owner revision: secondary travel data up; larger fuel/SOC readings down.
 floatingRun(ctx,[['Trip A',18,secondary,'Label Sans'],[f(d.tripA,1)+' km',29]],70,177);
 floatingRun(ctx,[['Média',18,secondary,'Label Sans'],[f(d.consumption.value,1)+' '+d.consumption.unit,29]],1848,187,'right');
 floatingRun(ctx,[['Combustível',19,secondary,'Label Sans'],[f(d.fuelPct)+'%',41]],230,646);
 floatingText(ctx,f(d.fuelLiters,1)+' L est. · '+f(d.fuelRange)+' km',230,687,26,'#bdcbd7');
 floatingRun(ctx,[['Bateria',19,secondary,'Label Sans'],[f(d.batteryPct)+'%',41]],1690,646,'right');
 floatingText(ctx,f(d.electricRange)+' km elétricos',1690,687,26,'#bdcbd7','right');
 if(!s.menuOpen)floatingRun(ctx,[['Total',18,secondary,'Label Sans'],[f(d.totalRange)+' km',33],['est.',18,secondary,'Label Sans']],960,677,'center');
 if(d.showRpm)txt(ctx,f(d.rpm)+' rpm',960,471,20,'#c2d4e0','Scale Digits');
}
function clearLayer(ctx,options){if(options.clear!==false)ctx.clearRect(0,0,W,H);}
function explicitState(options,readouts=false){
 const s=options.state;if(!s||(readouts&&!s.readouts))throw TypeError('Starlight render requires explicit state'+(readouts?' and readouts':''));
 return s;
}
// Public seams own no surfaces: the pipeline supplies their independent Canvas
// contexts. Only immutable source assets are cached inside this painter.
function renderBackground(ctx,t,mode='live',options={}){
 if(!options.starMotion)throw TypeError('Starlight render requires explicit integrated starMotion');
 clearLayer(ctx,options);ctx.drawImage(base(),0,0);GalaxyStars.render(ctx,options.starMotion);counts.stars++;
 return options.state||null;
}
function renderBeams(ctx,t,mode='live',options={}){
 const s=explicitState(options),time=Number.isFinite(t)?t:0;
 clearLayer(ctx,options);
 leftOptions.length=s.leftLength;leftOptions.tipHeat=s.tipHeat;
 rightOptions.length=s.rightLength;rightOptions.green=s.regen;rightOptions.intensity=s.batteryIntensity;
 const started=now();
 prepareDust(time,s.left>.008?s.leftLength:0,s.right>.008?s.rightLength:0);
 crystallineBeam(ctx,-1,s.left,time,leftOptions);if(s.left>.008)counts.left++;
 crystallineBeam(ctx,1,s.right,time,rightOptions);if(s.right>.008)counts.right++;
 const elapsed=now()-started;
 if(counts.beamFrames===0)counts.coldPaintMs=elapsed;
 else{
  counts.lastMaterialMs=elapsed;counts.maxMaterialMs=Math.max(counts.maxMaterialMs,elapsed);
  materialSamples[sampleIndex]=elapsed;sampleIndex=(sampleIndex+1)%materialSamples.length;sampleCount=Math.min(sampleCount+1,materialSamples.length);
 }
 counts.beamFrames++;return s;
}
function renderForeground(ctx,t,mode='live',options={}){
 const s=explicitState(options,true),time=Number.isFinite(t)?t:0;
 s.menuOpen=!!(options.menuState&&(options.menuState.card===1||options.menuState.card===3));
 clearLayer(ctx,options);
 const tipState=resolveTipState(s,options.tipState);
 scales(ctx,tipState);lens(ctx,time);headerData(ctx,s);drawSpeed(ctx,s.speed);txt(ctx,'KMH',960,380,20,'#929596','Label Sans');informationStrip(ctx,s);
 drawTipReadout(ctx,-1,tipState);drawTipReadout(ctx,1,tipState);counts.frames++;
 return s;
}
function renderPanel(ctx,t,mode='live',options={}){
 const s=explicitState(options,true);
 if(!options.starMotion)throw TypeError('Starlight render requires explicit integrated starMotion');
 panelOptions.state=s;panelOptions.starMotion=options.starMotion;panelOptions.menuState=options.menuState;panelOptions.tipState=options.tipState;
 ctx.clearRect(0,0,W,H);
 renderBackground(ctx,t,mode,panelOptions);renderBeams(ctx,t,mode,panelOptions);renderForeground(ctx,t,mode,panelOptions);
 return s;
}
return {Scale,W,H,SPEED_SCALE_MAX_KMH,DRIVE_MAX_KW,REGEN_MAX_KW,BATTERY_MAX_KW,functionalState,SCALE_X,speedHeat,powerLabel,batteryLabel,tipBatteryLabel,resolveTipState,tipReadout,frontierGeometry,drawSpeed,segmentFillRatio,setCanvasFactory,setMaterialHz,cacheStats,reset,cleanup,renderBackground,renderBeams,renderForeground,render:renderPanel,renderPanel};
});
