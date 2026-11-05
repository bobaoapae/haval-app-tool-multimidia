/* Independently reconstructed motion study of Dribbble 24751128.
   No reference pixels, third-party logo, audio or vehicle interface embedded. */
(function(root,factory){const api=factory();if(typeof module==='object')module.exports=api;else root.MotionStudy=api;})(typeof globalThis!=='undefined'?globalThis:this,function(){'use strict';
const W=1920,H=720,DURATION=782/60;
const Scale=typeof module==='object'?require('./scales'):globalThis.ThemeScales;
const GlassSmoke=typeof module==='object'?require('./glass-smoke'):globalThis.GlassSmoke;
const ApexData=typeof module==='object'?require('./apex-data'):globalThis.ApexData;
const GalaxyStars=typeof module==='object'?require('./galaxy-stars'):globalThis.GalaxyStars;const starSamplers={};
function starMotion(t,mode){if(dataProfile==='unavailable')return {speed:0,travel:0};if(!starSamplers[mode])starSamplers[mode]=GalaxyStars.createTravelSampler(x=>{const s=sample(x,mode);return s.speedRaw??s.speed;},duration(mode));const s=sample(t,mode);return {speed:s.speedRaw??s.speed,travel:starSamplers[mode](t)};}

let dataProfile='demo';function setDataProfile(value){if(!['demo','unavailable'].includes(value))throw Error('Unknown data profile');dataProfile=value;}
function readouts(t,s){return dataProfile==='unavailable'?ApexData.decode():ApexData.decode(ApexData.demo(t,s),{tankCapacityL:55,clockText:'15:40'});}

const SPEED=[26,31,35,40,44,49,53,57,59,62,64,69,86,104,122,132,146,163,179,193,199,204,210,215,220,226,231];
let FRONTIER=[606,602,588,560,514,464,444,494,602,638,650,638,200,390,570,546,150,80,468,530,530,530,530,530,530,530,530];
const SPIKE=[638,600,500,320,234,200,186,182,190,286,390,462,510,540,560,570,570,570,566,558,546,524,484,392,214,150,120,102,94,90,80,164,258,348,418,468,504,526,530,530,530];
let LEVEL=FRONTIER.map(x=>Math.max(0,Math.min(8,((780-x*.96)-83)/650*8)));
let factory=null,background={};const clamp=(x,a,b)=>Math.max(a,Math.min(b,x));
function setCanvasFactory(f){factory=f;background={};GlassSmoke.setCanvasFactory(f);}function make(w,h){if(factory)return factory(w,h);const c=document.createElement('canvas');c.width=w;c.height=h;return c;}
function interp(arr,t){t=clamp(Number.isFinite(t)?t:0,0,DURATION)*2;const i=Math.min(Math.floor(t),arr.length-1),j=Math.min(i+1,arr.length-1),f=t-i;return arr[i]+(arr[j]-arr[i])*f;}
function setLevels(a){if(a.length!==27||a.some(v=>!Number.isFinite(v)))throw Error('Invalid 0.5s motion track');LEVEL=[...a];}
function sourceFront(t){if(t>=5.5&&t<=9.5){const n=(t-5.5)*10,i=Math.min(40,Math.floor(n)),j=Math.min(40,i+1);return SPIKE[i]+(SPIKE[j]-SPIKE[i])*(n-i);}return interp(FRONTIER,t);}
function sampleReference(t,mode='reference'){const speed=interp(SPEED,t),front=sourceFront(clamp(Number.isFinite(t)?t:0,0,DURATION)),beam=clamp(((780-front*.96+10)-83)/650*8,0,8);return {time:clamp(Number.isFinite(t)?t:0,0,DURATION),speed:Math.round(mode==='regen'?257-speed:speed),level:beam,left:mode==='regen'?0:beam,right:mode==='accel'?0:beam,regen:mode==='regen',mode};}
// Demonstration scales only: these are not measured vehicle maximum ratings.
const DRIVE_MAX_KW=160,REGEN_MAX_KW=100,BATTERY_MAX_KW=100,DEMO_DURATION=16,CITY_DURATION=50;
const SCALE_X=[702.72,643.2,574.08,495.36,422.4,347.52,259.2,174.72,84.48];
const POWER_TRACK=[[0,0],[1,40],[2.5,80],[4,140],[5,160],[6,80],[7,0],[8,0],[9,-20],[10.5,-40],[12,-70],[13,-80],[14,-40],[15,0],[16,0]];
// Independent battery-flow mock: positive discharge, negative recovery, per owner contract.
// Voltage/current polarity and actual vehicle signal still require vehicle validation.
const BATTERY_TRACK=[[0,0],[1,18],[2.5,36],[4,58],[5,68],[6,32],[7,12],[7.6,0],[8.35,0],[9,-20],[10.5,-40],[12,-70],[13,-80],[14,-40],[15,0],[16,0]];
function trackValue(track,t){for(let i=0;i<track.length-1;i++){const [a,pa]=track[i],[b,pb]=track[i+1];if(t<=b)return pa+(pb-pa)*clamp((t-a)/(b-a),0,1);}return track[track.length-1][1];}
const CITY_SPEED=[[0,0],[2,0],[4,20],[5,20],[7,40],[8,40],[9.5,50],[10.5,50],[12,60],[13,60],[15,80],[16,80],[18,100],[19,100],[21,120],[22,120],[24,140],[25,140],[27,160],[28,160],[31,200],[32,200],[35,160],[38,120],[40,80],[42,60],[44,60],[46,40],[48,0],[50,0]];
const CITY_BATTERY=[[0,0],[2,0],[4,10],[5,12],[6,20],[7,25],[8,30],[9,35],[10,40],[11,45],[12,50],[13,40],[15,60],[16,50],[18,75],[19,60],[21,100],[22,80],[24,65],[25,45],[27,55],[28,30],[29,15],[30,0],[32,0],[33,-10],[34,-20],[35,-25],[36,-30],[37,-35],[38,-40],[39,-45],[40,-50],[41,-75],[42,-100],[43,-50],[44,-20],[45,-10],[46,0],[48,0],[50,0]];
function speedHeat(speed){return Number.isFinite(speed)?clamp((speed-120)/80,0,1):0;}
function duration(mode='drive'){return mode==='city'?CITY_DURATION:mode==='drive'?DEMO_DURATION:DURATION;}
function telemetry(t){t=clamp(Number.isFinite(t)?t:0,0,DEMO_DURATION);let power=0,speed=72;
 for(let i=0;i<POWER_TRACK.length-1;i++){const [a,pa]=POWER_TRACK[i],[b,pb]=POWER_TRACK[i+1];if(t<a)break;const dt=Math.min(t,b)-a,u=dt/(b-a),p=pa+(pb-pa)*u;speed+=(pa+p)*dt*.5*(pa<0||pb<0?.20:.10);power=p;if(t<=b)break;}
 return {powerKw:power,speedRaw:speed,batteryPowerKw:trackValue(BATTERY_TRACK,t)};
}
// Source-observed warmth appears when the frontier extends to its outermost region.
// This is a fitted visual envelope, not a speed threshold or vehicle warning.
const MAX_PREVIEW_KMH=200;
function tipHeat(length){if(!Number.isFinite(length))return 0;return clamp((length-622)/33.6,0,1);}
function powerLabel(power){const v=Math.round(Math.abs(power));return (v===0?'':power<0?'−':'+')+v+' kW';}
function batteryLabel(power){if(power===null||!Number.isFinite(power))return '-- kW';const a=Math.abs(power),sign=power>0?'+':power<0?'−':'';if(a===0)return '0 kW';if(a<.1)return sign+'<0,1 kW';if(a<1)return sign+a.toFixed(1).replace('.',',')+' kW';return powerLabel(power);}
function scaleX(ratio){ratio=Number.isFinite(ratio)?ratio:0;const q=clamp(ratio,0,1)*8,i=Math.min(8,Math.floor(q)),j=Math.min(8,i+1);return SCALE_X[i]+(SCALE_X[j]-SCALE_X[i])*(q-i);}
function scaleRatio(x){x=clamp(x,SCALE_X[8],SCALE_X[0]);for(let i=0;i<8;i++)if(x>=SCALE_X[i+1])return (i+(SCALE_X[i]-x)/(SCALE_X[i]-SCALE_X[i+1]))/8;return 1;}
// Public independent input contract. Battery sign is never inferred from speed or traction.
function functionalState(input){
 const {time=0,mode='drive',speedRaw:rawSpeed=0,powerKw=0}=input;
 const speedRaw=clamp(Number.isFinite(rawSpeed)?rawSpeed:0,0,MAX_PREVIEW_KMH);
 const tractionKw=clamp(Number.isFinite(input.tractionKw)?input.tractionKw:0,0,DRIVE_MAX_KW);
 const batteryPowerKw=Number.isFinite(input.batteryPowerKw)?input.batteryPowerKw:null;
 const batteryMagnitudeKw=batteryPowerKw===null?0:Math.abs(batteryPowerKw),consumedKw=Math.max(0,batteryPowerKw??0),recoveredKw=Math.max(0,-(batteryPowerKw??0)),left=Scale.speedRatio(speedRaw)*8,right=Scale.batteryRatio(batteryMagnitudeKw)*8;
 const leftLength=780-Scale.speedX(speedRaw),rightLength=780-Scale.batteryX(batteryMagnitudeKw);
 return {time,mode,functional:true,speed:Math.round(clamp(speedRaw,0,MAX_PREVIEW_KMH)),speedRaw:clamp(speedRaw,0,MAX_PREVIEW_KMH),powerKw,tractionKw,batteryPowerKw,batteryMagnitudeKw,consumedKw,recoveredKw,left,right,level:Math.max(left,right),regen:batteryPowerKw<0,greenIntensity:clamp(recoveredKw/BATTERY_MAX_KW,0,1),batteryIntensity:clamp(batteryMagnitudeKw/BATTERY_MAX_KW,0,1),batteryState:batteryPowerKw===null?'SEM DADOS':batteryPowerKw>0?'CONSUMINDO':batteryPowerKw<0?'REGENERANDO':'NEUTRA',leftLength,rightLength,tipHeat:speedHeat(speedRaw),phase:powerKw>0?'ACELERAÇÃO':powerKw<0?'DESACELERAÇÃO':'RODA LIVRE'};
}
function sample(t,mode='reference'){
 if(mode==='reference')return {...sampleReference(t),functional:false,powerKw:423};
 if(mode==='city'){
  const time=clamp(Number.isFinite(t)?t:0,0,CITY_DURATION),speedRaw=trackValue(CITY_SPEED,time),batteryPowerKw=trackValue(CITY_BATTERY,time);
  const delta=trackValue(CITY_SPEED,Math.min(CITY_DURATION,time+.01))-trackValue(CITY_SPEED,Math.max(0,time-.01));
  const powerKw=Math.abs(delta)<1e-8?0:delta>0?80:-40;
  const s=functionalState({time,mode,speedRaw,powerKw,tractionKw:Math.max(0,powerKw),batteryPowerKw});
  s.phase=speedRaw===0?'PARADO':powerKw===0?'VELOCIDADE CONSTANTE':powerKw>0?'ACELERAÇÃO SIMULADA':'DESACELERAÇÃO SIMULADA';return s;
 }
 if(mode==='motion'){
  const q=sampleReference(t),s=functionalState({time:q.time,mode,speedRaw:interp(SPEED,t)*MAX_PREVIEW_KMH/SPEED[SPEED.length-1],powerKw:80,tractionKw:80,batteryPowerKw:trackValue(BATTERY_TRACK,q.time)});
  s.phase='VELOCIDADE ADAPTADA · ESCALAS FUNCIONAIS';return s;
 }
 const time=clamp(Number.isFinite(t)?t:0,0,duration(mode)),driveTime=mode==='accel'?time/DURATION*7:mode==='regen'?8+time/DURATION*7:time;
 const q=telemetry(driveTime),powerKw=Math.abs(q.powerKw)<1e-8?0:q.powerKw;
 return functionalState({time,mode,speedRaw:q.speedRaw,powerKw,tractionKw:Math.max(0,powerKw),batteryPowerKw:Math.abs(q.batteryPowerKw)<1e-8?0:q.batteryPowerKw});
}
let seed=99181;function random(){seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/4294967296;}
const stars=Array.from({length:2200},()=>({x:random()*W,y:random()*435,r:random()**3*1.9+.22,a:random()*.7+.12,phase:random()*6.28}));
const dust=Array.from({length:8200},()=>({u:random(),v:random(),z:random(),p:random()*6.28,r:random()*.8+.35}));
// Irregularly distributed eddies, not equidistant columns.
const particleEddies=Array.from({length:43},()=>({u:random()*760,width:12+random()*37,phase:random()*6.28,twist:.025+random()*.065,rate:.21+random()*.38}));
function txt(ctx,s,x,y,size=20,color='#fff',font='Panel Sans',align='center'){ctx.font=`${size}px "${font}"`;ctx.fillStyle=color;ctx.textAlign=align;ctx.textBaseline='middle';ctx.fillText(s,x,y);}
function tracked(ctx,s,x,y,size,color,spacing=7){ctx.font=`${size}px "Label Sans"`;ctx.textBaseline='middle';ctx.fillStyle=color;const widths=[...s].map(a=>ctx.measureText(a).width),total=widths.reduce((a,b)=>a+b,0)+(s.length-1)*spacing;let xx=x-total/2;[...s].forEach((a,i)=>{ctx.textAlign='left';ctx.fillText(a,xx,y);xx+=widths[i]+spacing;});}
function line(ctx,x1,y1,x2,y2,col,w=1){ctx.beginPath();ctx.moveTo(x1,y1);ctx.lineTo(x2,y2);ctx.strokeStyle=col;ctx.lineWidth=w;ctx.stroke();}
function ellipse(ctx,cx,cy,rx,ry){ctx.beginPath();ctx.ellipse(cx,cy,rx,ry,0,0,Math.PI*2);}
function base(reference=true){const key=reference?'reference':'functional';if(background[key])return background[key];const c=make(W,H),ctx=c.getContext('2d');ctx.fillStyle='#000';ctx.fillRect(0,0,W,H);
 // The wide glass surface, top light source and original star distribution.
 let g=ctx.createLinearGradient(0,0,0,410);g.addColorStop(0,'#12171f');g.addColorStop(.25,'#040608');g.addColorStop(1,'#000');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
 const top=ctx.createRadialGradient(960,2,0,960,2,900);top.addColorStop(0,'rgba(176,201,242,.32)');top.addColorStop(.27,'rgba(98,130,188,.14)');top.addColorStop(1,'rgba(50,60,80,0)');ctx.save();ctx.scale(1,.24);ctx.fillStyle=top;ctx.fillRect(0,0,W,1500);ctx.restore();
 ctx.fillStyle='#a5a9b8';ctx.fillRect(0,16,W,5);ctx.fillStyle='#454854';ctx.fillRect(0,21,W,4);
 // Owner-selected model 3: no fixed lower planes, diagonal backdrop or top brand.
 if(reference)txt(ctx,'17°C  ·  12:35 PM',75,66,16,'#dde3eb','Header Sans','left');
 
 ctx.save();ctx.translate(960,14);ctx.scale(1,.14);const haze=ctx.createRadialGradient(0,0,0,0,0,540);haze.addColorStop(0,'rgba(213,227,255,.50)');haze.addColorStop(.14,'rgba(154,180,225,.3)');haze.addColorStop(1,'rgba(60,88,143,0)');ctx.fillStyle=haze;ctx.fillRect(-650,-300,1300,1500);ctx.restore();background[key]=c;return c;
}
const innerX=780,innerY=380;
function beam(ctx,side,level,t,options={}){if(options.polished)return crystallineBeam(ctx,side,level,t,options);const ext=clamp(level/8,0,1),length=options.length??(83+650*ext),green=!!options.green;const rgb=green?'106,231,163':'157,188,235',dustRgb=green?'172,255,207':'206,221,250';const sx=side;const x=u=>960+sx*(180+u);const y=u=>innerY+u*.326;
 if(level<=0.01)return;
 ctx.save();ctx.globalAlpha=options.functional?ext:1;ctx.beginPath();ctx.moveTo(x(0),innerY-180);ctx.lineTo(x(length-13),y(length)-220);ctx.lineTo(x(length+16),y(length)+66);ctx.lineTo(x(0),innerY+20);ctx.closePath();ctx.clip();
 // Dense refractive beam: smooth normal-direction density falloff rather than an outlined bar.
 for(let k=-145;k<=46;k++){const a=(Math.exp(-Math.pow((k+6)/30,2))*.29+Math.exp(-Math.pow((k+35)/80,2))*.055);ctx.strokeStyle=`rgba(${rgb},${a})`;ctx.lineWidth=4;ctx.beginPath();ctx.moveTo(x(0),y(0)+k*.6);ctx.lineTo(x(length),y(length)+k);ctx.stroke();}
 const red=options.functional?0:clamp((175-sourceFront(t))/35,0,1);if(red>0){const g=ctx.createRadialGradient(x(length),y(length),1,x(length),y(length),130);g.addColorStop(0,`rgba(255,66,88,${red*.75})`);g.addColorStop(.6,`rgba(199,46,118,${red*.30})`);g.addColorStop(1,'rgba(180,30,50,0)');ctx.fillStyle=g;ctx.fillRect(Math.min(x(0),x(length))-140,innerY-150,length+280,460);}
 // 3-D-inspired dust volume, spread over height and depth, in the reference's fine-grained style.
 for(let i=0;i<dust.length;i++){const p=dust[i];const u=p.u*760;if(u>length)continue;const vv=(p.v+t*(.105+.055*p.z))%1;const h=Math.pow(vv,1.6)*(100+u*.07);const xx=x(u)+Math.sin(p.p+t*1.2+h*.06)*(1.5+5*p.z);const yy=y(u)-h+Math.cos(p.p*2+t)*2;const a=(1-vv)*(.25+.9*p.z)*(1-Math.exp(-u/70));ctx.fillStyle=`rgba(${dustRgb},${a})`;ctx.fillRect(xx,yy,p.r,p.r*(1+.65*p.z));}
 // Very bright lower ridge, softened by a narrow luminous skirt.
 const bright=ctx.createLinearGradient(x(0),y(0),x(length),y(length));bright.addColorStop(0,green?'rgba(137,255,183,.08)':'rgba(182,211,255,.08)');bright.addColorStop(.55,green?'rgba(190,255,216,.75)':'rgba(208,226,255,.75)');bright.addColorStop(1,green?'rgba(230,255,239,.95)':'rgba(239,247,255,.95)');ctx.strokeStyle=bright;ctx.lineWidth=3;ctx.shadowColor=green?'#8df6b9':'#c7dcff';ctx.shadowBlur=17;ctx.beginPath();ctx.moveTo(x(0),y(0));ctx.lineTo(x(length),y(length));ctx.stroke();ctx.shadowBlur=0;
 ctx.restore();
 // Moving translucent glass frontier: tip reaches above the numerical scale.
 const bx=x(length),by=y(length);const edge=ctx.createLinearGradient(bx,by-240,bx,by+66);edge.addColorStop(0,'rgba(112,136,173,0)');edge.addColorStop(.4,green?'rgba(114,231,163,.16)':'rgba(137,178,231,.16)');edge.addColorStop(.73,green?'rgba(207,255,224,.92)':'rgba(225,240,255,.92)');edge.addColorStop(.89,green?'rgba(86,176,126,.3)':'rgba(116,146,189,.3)');edge.addColorStop(1,'rgba(30,48,69,0)');
 ctx.beginPath();ctx.moveTo(bx+side*13,by-235);ctx.lineTo(bx-side*16,by+67);ctx.lineTo(bx-side*32,by+51);ctx.lineTo(bx+side*10,by-235);ctx.closePath();ctx.fillStyle=edge;ctx.globalAlpha=.23*(options.functional?ext:1);ctx.fill();ctx.globalAlpha=options.functional?ext:1;
 ctx.strokeStyle=edge;ctx.lineWidth=1.7;ctx.shadowColor=green?'#8df6b9':'#c7dcff';ctx.shadowBlur=8;ctx.beginPath();ctx.moveTo(bx+side*13,by-235);ctx.lineTo(bx-side*16,by+65);ctx.stroke();ctx.shadowBlur=0;ctx.globalAlpha=1;
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
 const ice=green?[116,244,171]:[166,197,247],rgb=ice.join(','),tip=[255,68,102];
 function path(offset=0){ctx.beginPath();ctx.moveTo(x(0),y(0)+offset);ctx.lineTo(x(length),y(length)+offset);}
 function tipAmount(u){const q=clamp((u/length-.68)/.32,0,1);return heat*q*q*(3-2*q);}
 function color(u,opacity,white=0){const a=tipAmount(u);return `rgba(${ice.map((v,i)=>Math.round((v+(tip[i]-v)*a)*(1-white)+255*white)).join(',')},${opacity})`;}
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
  const p=dust[i],cell=i%particleEddies.length,e=particleEddies[cell],anchor=i%4===0?p.u*760:e.u;
  const v=(p.v+t*(.032+.019*p.z))%1,h=Math.pow(v,1.42)*(108+anchor*.050);
  const drift=Math.sin(h*e.twist+e.phase+t*e.rate)*(4+e.width*.17)+Math.sin(h*.107-e.phase-t*.29)*3;
  const u=anchor+(i%4===0?0:(p.u-.5)*e.width)+drift;if(u<0||u>length-1)continue;
  const xx=x(u)+(p.z-.5)*9,yy=y(u)-h+(p.z-.5)*10;
  const density=.30+.70*Math.pow(.5+.5*Math.sin(e.phase+h*.028+t*.23),2);
  const fade=Math.sin(Math.PI*v)**.8*(1-Math.exp(-u/55)),a=fade*(.21+.62*p.z)*density,r=p.r*(.48+.70*p.z);
  ctx.fillStyle=color(u,a,.69);ctx.fillRect(xx,yy,r,r*.90);
  if(i%9===0){ctx.strokeStyle=color(u,a*.66,.74);ctx.lineWidth=.62;ctx.beginPath();ctx.moveTo(xx,yy);ctx.quadraticCurveTo(xx+side*1.8,yy-1.7,xx+side*.2,yy-3.2);ctx.stroke();}
  if(i%3===0){ctx.fillStyle=color(u,a*.14,.40);ctx.fillRect(xx+side*2,y(u)+h*.30+4,r*.8,Math.max(.4,r*.45));}
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
 if(!s.functional){for(const side of [-1,1])for(let n=0;n<=8;n++)txt(ctx,String(n),side<0?SCALE_X[n]:1920-SCALE_X[n],Scale.scaleY(SCALE_X[n]),23+27*n/8,n===8?'#db392f':'#bcc2cc','Scale Digits');return;}
 for(const tick of Scale.leftTicks){const p=tick.value/200;drawScaleTick(ctx,tick,`rgb(${170+75*p},${178+70*p},${188+60*p})`);}
 for(const tick of Scale.rightTicks)drawScaleTick(ctx,tick,s.batteryMagnitudeKw>0?(s.regen?'#9ceac0':'#a9cafa'):'#a7b3c1');
 tracked(ctx,Scale.speedLabel.text,440,278,16,'#a9bcd3',2);tracked(ctx,'POTÊNCIA BATERIA · kW',1480,256,14,'#a9bcd3',1.5);tracked(ctx,batteryLabel(s.batteryPowerKw)+' · '+s.batteryState,1480,283,17,s.regen?'#8bddb0':s.batteryPowerKw>0?'#b4d4ff':'#8c9bac',1.2);
}
let lensTexture=null;
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
function movingStars(ctx,t){
 for(const p of stars){const depth=(p.phase/6.28+t*.022)%1;const scale=.35+depth*1.5;const xx=960+(p.x-960)*scale,yy=350+(p.y-350)*scale;if(xx<0||xx>W||yy<17||yy>430)continue;const fade=yy<220?1:clamp((470-yy)/250,0,1);const a=Math.min(1,p.a*2.2)*Math.min(1,depth*5)*fade;ctx.fillStyle=`rgba(241,245,252,${a})`;const r=p.r*(.65+depth*.65);ellipse(ctx,xx,yy,r,r);ctx.fill();if(p.r>.5&&p.a>.3){ctx.fillStyle=`rgba(250,252,255,${Math.min(1,a*1.3)})`;const z=p.r>1.5?2:1;ctx.fillRect(Math.round(xx),Math.round(yy),z,z);}}
}
function headerData(ctx,s){
 if(!s.functional){ctx.fillStyle='#e4bd4b';ellipse(ctx,48,54,8,8);ctx.fill();ctx.fillStyle='#a7b9c4';ellipse(ctx,40,63,10,8);ctx.fill();ctx.fillStyle='#d4e0e5';ellipse(ctx,50,64,10,7);ctx.fill();}else{const d=s.readouts,f=ApexData.fmt;txt(ctx,'INTERNA',56,57,12,'#8090a3','Label Sans','left');txt(ctx,d.insideTemp===null?'--':f(d.insideTemp)+d.tempUnit,56,82,21,'#dbe4ee','Panel Sans','left');txt(ctx,'EXTERNA',190,57,12,'#8090a3','Label Sans','left');txt(ctx,d.outsideTemp===null?'--':f(d.outsideTemp)+d.tempUnit,190,82,21,'#dbe4ee','Panel Sans','left');txt(ctx,d.clock,388,68,23,'#dce5ef','Scale Digits','left');txt(ctx,d.gear,960,121,40,'#f2f5f7','Scale Digits');txt(ctx,d.driveMode,896,156,14,'#a8c5df','Label Sans');txt(ctx,d.propulsionMode,1025,156,17,'#c4d4e3','Scale Digits');txt(ctx,'ODÔMETRO',1860,112,12,'#8090a3','Label Sans','right');txt(ctx,f(d.odometer)+' km',1860,137,21,'#d9e2ee','Scale Digits','right');}
 if(s.functional)return;
 const kw=s.functional?(s.powerKw===null?'-- kW':powerLabel(s.powerKw)):'423 kW',color=s.functional&&s.regen?'#9af2bd':'#d8dfea';txt(ctx,kw,1730,66,16,color,'Header Sans','right');const end=s.functional?1750+160*(s.regen?s.recoveredKw/REGEN_MAX_KW:s.tractionKw/DRIVE_MAX_KW):1875;if(s.functional){line(ctx,1750,66,1910,66,'#353a43',3);if(end>1750)line(ctx,1750,66,end,66,color,3);}else{line(ctx,1750,66,1875,66,'#b6beca',3);line(ctx,1875,66,1910,66,'#353a43',3);}
 ctx.fillStyle='#e5edf5';ctx.beginPath();ctx.moveTo(1603,56);ctx.lineTo(1597,67);ctx.lineTo(1604,67);ctx.lineTo(1602,76);ctx.lineTo(1611,63);ctx.lineTo(1605,63);ctx.lineTo(1608,56);ctx.closePath();ctx.fill();
}
// Owner-selected typography: option 1, Rajdhani SemiBold; adapt only when 3 digits need room.
function drawSpeed(ctx,value){const label=value===null?'--':String(Math.max(0,Math.round(value)));ctx.save();ctx.textAlign='left';ctx.textBaseline='alphabetic';let size=142.1875;ctx.font=`${size}px "Scale Digits"`;let m=ctx.measureText(label);const width=m.actualBoundingBoxLeft+m.actualBoundingBoxRight;if(width>202){size*=202/width;ctx.font=`${size}px "Scale Digits"`;m=ctx.measureText(label);}ctx.textAlign='left';ctx.textBaseline='alphabetic';ctx.fillStyle='#f6f6f4';ctx.fillText(label,960-(m.actualBoundingBoxRight-m.actualBoundingBoxLeft)/2,308+(m.actualBoundingBoxAscent-m.actualBoundingBoxDescent)/2);ctx.restore();return {font:'Rajdhani SemiBold',size,width:m.actualBoundingBoxLeft+m.actualBoundingBoxRight,height:m.actualBoundingBoxAscent+m.actualBoundingBoxDescent};}
function segmentFillRatio(value,index,count=12){return value===null?0:clamp(value/100*count-index,0,1);}
function segmentedBar(ctx,x,y,width,value){const count=12,gap=4,w=(width-gap*(count-1))/count;for(let n=0;n<count;n++){ctx.fillStyle='#202d39';ctx.fillRect(x+n*(w+gap),y,w,7);const fill=segmentFillRatio(value,n,count);if(fill>0){ctx.fillStyle='#a8c2d8';ctx.fillRect(x+n*(w+gap),y,w*fill,7);}}ctx.strokeStyle='#566978';ctx.lineWidth=1;ctx.strokeRect(x-5,y-5,width+10,17);}
function fuelIcon(ctx,x,y){ctx.save();ctx.translate(x,y);ctx.strokeStyle='#a8bccd';ctx.lineWidth=1.7;ctx.beginPath();ctx.moveTo(1,19);ctx.lineTo(1,1);ctx.lineTo(12,1);ctx.lineTo(12,19);ctx.moveTo(-1,19);ctx.lineTo(15,19);ctx.moveTo(2,8);ctx.lineTo(11,8);ctx.moveTo(12,4);ctx.lineTo(17,8);ctx.lineTo(17,16);ctx.bezierCurveTo(17,20,22,20,22,16);ctx.lineTo(22,9);ctx.lineTo(17,3);ctx.stroke();ctx.restore();}
function batteryIcon(ctx,x,y){ctx.save();ctx.translate(x,y);ctx.strokeStyle='#a8bccd';ctx.lineWidth=1.7;ctx.strokeRect(0,3,24,16);ctx.strokeRect(5,0,4,3);ctx.strokeRect(16,0,4,3);line(ctx,5,11,11,11,'#a8bccd',1.5);line(ctx,8,8,8,14,'#a8bccd',1.5);line(ctx,16,11,21,11,'#a8bccd',1.5);ctx.restore();}
function floatingText(ctx,text,x,y,size=25,color='#d5e1ea',align='left',font='Scale Digits'){
 ctx.save();ctx.font=`${size}px "${font}"`;ctx.textAlign=align;ctx.textBaseline='middle';
 ctx.fillStyle='#000';ctx.shadowColor='#000';ctx.shadowBlur=6;ctx.shadowOffsetX=0;ctx.shadowOffsetY=0;ctx.fillText(text,x,y);
 ctx.fillStyle=color;ctx.shadowColor='rgba(0,0,0,.95)';ctx.shadowBlur=5;ctx.shadowOffsetY=2;ctx.fillText(text,x,y);ctx.restore();
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
function applyMenuReadouts(s,menu){if(!s.functional||!menu?.data||dataProfile==='unavailable')return;const m=menu.data,d=s.readouts;
 const maps={driveMode:[{0:'NORMAL',1:'SPORT',2:'ECO',3:'NEVE',4:'AREIA',5:'LAMA',11:'AWD'},'driveMode'],propulsionMode:[{0:'HEV',1:'EVP',3:'EV'},'propulsionMode'],steerMode:[{0:'NORMAL',1:'ESPORTIVA',2:'CONFORTO'},'steering'],regenMode:[{0:'NORMAL',1:'ALTO',2:'BAIXO'},'recovery'],esp:[{0:'OFF',1:'ON'},'esp']};
 for(const[k,[labels,dest]]of Object.entries(maps))if(Object.hasOwn(m,k))d[dest]=labels[m[k]]??'--';
 if(Object.hasOwn(m,'acPower'))d.hvacPower=m.acPower===null?'--':m.acPower?'LIGADO':'DESL.';
 if(Object.hasOwn(m,'acFan'))d.fan=m.acFan===null?'--':String(m.acFan);
 if(Object.hasOwn(m,'acTemp'))d.driverTemp=m.acTemp===null?'--':ApexData.fmt(m.acTemp,1)+'°C';
 if(Object.hasOwn(m,'acAuto'))d.hvacAuto=m.acAuto===null?'--':m.acAuto?'AUTO':'MANUAL';
 if(Object.hasOwn(m,'acRecycle'))d.hvacCycle=m.acRecycle===null?'--':m.acRecycle?'RECIRC.':'EXTERNO';
}
function panelState(t,mode){const s=sample(t,mode);if(!s.functional)return s;s.readouts=readouts(t,s);if(dataProfile==='unavailable'){s.speed=null;s.speedRaw=null;s.powerKw=null;s.batteryPowerKw=null;s.left=s.right=s.tractionKw=s.recoveredKw=s.greenIntensity=s.batteryIntensity=s.batteryMagnitudeKw=s.consumedKw=0;s.batteryState='SEM DADOS';s.regen=false;s.tipHeat=0;s.phase='DADOS INDISPONÍVEIS';}return s;}
function renderPanel(ctx,t,mode='reference',options={}){const s=options.state||panelState(t,mode);if(!options.state)applyMenuReadouts(s,options.menuState);s.menuOpen=[1,3].includes(options.menuState?.card);ctx.clearRect(0,0,W,H);ctx.drawImage(base(mode==='reference'),0,0);if(s.functional)GalaxyStars.render(ctx,{time:t,...(options.starMotion||starMotion(t,mode))});else{movingStars(ctx,t);headerData(ctx,s);}beam(ctx,-1,s.left,t,{functional:s.functional,polished:s.functional||options.materialStudy,length:s.functional?s.leftLength:undefined,tipHeat:s.functional?s.tipHeat:clamp((175-sourceFront(t))/35,0,1)});beam(ctx,1,s.right,t,{functional:s.functional,polished:s.functional||options.materialStudy,length:s.functional?s.rightLength:undefined,green:s.functional&&s.regen,battery:s.functional,intensity:s.batteryIntensity,tipHeat:s.functional?0:clamp((175-sourceFront(t))/35,0,1)});scales(ctx,s);lens(ctx,t);if(s.functional)headerData(ctx,s);
 if(mode==='reference'){ctx.save();ctx.translate(960,0);ctx.scale(1.23,1);txt(ctx,String(s.speed),0,311,128,'#f6f6f4','Speed Digits');ctx.restore();}else{drawSpeed(ctx,s.speed);}txt(ctx,'KMH',960,380,20,'#929596','Label Sans');if(s.functional)informationStrip(ctx,s);
 return s;
}
function renderVideo(ctx,t,mode='reference'){ctx.fillStyle='#000';ctx.fillRect(0,0,1920,1080);ctx.save();ctx.translate(0,165);renderPanel(ctx,t,mode);ctx.restore();
 const s=panelState(t,mode);txt(ctx,mode==='reference'?'RECONSTRUÇÃO DO MOVIMENTO DE REFERÊNCIA':s.phase+' · BATERIA '+batteryLabel(s.batteryPowerKw),960,947,20,s.regen&&s.functional?'#8bdfac':'#a8bbd2','Label Sans');if(s.functional)txt(ctx,'Escalas: velocidade 0–200 km/h · bateria ±100 kW visuais · dados simulados',960,978,15,'#818993','Label Sans');
 txt(ctx,'Dados simulados · tanque ilustrativo de 55 L · sem validação no veículo',960,1004,15,'#5a626d','Label Sans');
}
return {Scale,CITY_DURATION,CITY_SPEED,CITY_BATTERY,speedHeat,W,H,DURATION,DEMO_DURATION,MAX_PREVIEW_KMH,DRIVE_MAX_KW,REGEN_MAX_KW,BATTERY_MAX_KW,POWER_TRACK,BATTERY_TRACK,trackValue,functionalState,SCALE_X,duration,telemetry,tipHeat,scaleRatio,scaleX,powerLabel,batteryLabel,frontierGeometry,drawSpeed,setDataProfile,panelState,starMotion,segmentFillRatio,SPEED,get LEVEL(){return LEVEL},setLevels,setCanvasFactory,sourceFront,FRONTIER,SPIKE,sample,renderPanel,renderVideo};
});
