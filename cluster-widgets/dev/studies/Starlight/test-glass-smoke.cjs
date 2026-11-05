'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const {performance}=require('node:perf_hooks');
const {createCanvas}=require('@napi-rs/canvas');
const Smoke=require('./glass-smoke');
let created=0;const factory=(w,h)=>{created++;return createCanvas(w,h);};
const renderer=Smoke.createRenderer(factory),W=1920,H=720;
const base={side:-1,length:700,time:10,alpha:1};
const pixels=c=>Buffer.from(c.getContext('2d').getImageData(0,0,c.width,c.height).data);
function frame(options={},r=renderer){const c=createCanvas(W,H);r.render(c.getContext('2d'),{...base,...options});return c;}
const first=frame(),a=pixels(first);assert(a.some(v=>v));
const allocations=created;frame({time:11});frame({time:0});frame({time:8,green:true,heat:1});
assert.deepEqual(pixels(frame()),a,'absolute-time seeking must be byte-identical');
assert.equal(created,allocations,'all offscreen canvases are reused after warmup');
assert.equal(allocations,7,'only mask, wisp brush, three periodic textures, and two side-specific frame buffers are cached');
let b=pixels(frame({time:10.4})),changed=0,totalDiff=0,maxAlpha=0,upperEnergy=0,lowerEnergy=0,outside=0;
for(let y=0;y<H;y++)for(let x=0;x<W;x++){
 const i=(y*W+x)*4,alpha=a[i+3];
 if(alpha!==b[i+3])changed++;totalDiff+=Math.abs(alpha-b[i+3]);maxAlpha=Math.max(maxAlpha,alpha);
 if(!alpha)continue;
 const u=780-x,v=y-380-u*.35;
 if(u < -1||u>701||v < -133||v>60)outside++;
 if(v<0)upperEnergy+=alpha;else lowerEnergy+=alpha;
}
assert(changed>2500,'interior texture must evolve at fixed frontier');
assert(totalDiff/(W*H)<.7,'0.4s drift must remain restrained, with no flashes');
assert(maxAlpha<150,'internal volume must stay translucent');assert.equal(outside,0,'no clouds outside wing clipping bounds');
assert(lowerEnergy/upperEnergy<.2,'lower reflection must stay subtle');
const short=pixels(frame({length:240}));
for(let y=300;y<510;y++)for(let x=570;x<735;x++){
 const u=780-x,v=y-380-u*.35;if(v>=-2)continue;const i=(y*W+x)*4;assert.equal(short[i+3],a[i+3],'growth reveals rather than stretches or resets smoke');
}
const untouched=Smoke.createRenderer(()=>{throw Error('zero state must not allocate textures');});
for(const options of [{alpha:0},{alpha:-2},{length:0},{length:-10},{length:NaN},{length:Infinity}])assert(pixels(frame(options,untouched)).every(v=>v===0));
for(const options of [{time:NaN},{time:Infinity},{time:-Infinity},{time:-500},{time:1e100},{heat:Infinity},{heat:NaN},{alpha:Infinity},{alpha:NaN},{side:NaN}])assert.doesNotThrow(()=>frame(options));
assert.deepEqual(pixels(frame({time:NaN})),pixels(frame({time:0})));
assert.deepEqual(pixels(frame({length:100000})),pixels(frame({length:768})));
const warm=pixels(frame({heat:1})),green=pixels(frame({green:true})),greenHot=pixels(frame({green:true,heat:1}));
assert.deepEqual(greenHot,green,'regen suppresses warm tip');
let warmDiff=0;for(let y=0;y<H;y++)for(let x=0;x<W;x++){
 const i=(y*W+x)*4;if(warm[i]!==a[i]){warmDiff++;assert(x<230,'warmth stays within 148px of outer frontier');}
}
assert(warmDiff>1000);assert.notDeepEqual(green,a);
const half=pixels(frame({alpha:.5}));for(let i=3;i<a.length;i+=4)assert(Math.abs(half[i]-a[i]*.5)<=1,'caller alpha scales each material pixel within 8-bit quantization');
const c=createCanvas(W,H),ctx=c.getContext('2d');ctx.translate(12,7);ctx.scale(.7,.9);ctx.globalAlpha=.43;ctx.globalCompositeOperation='screen';ctx.fillStyle='#12abef';ctx.imageSmoothingEnabled=false;
const m=ctx.getTransform(),savedAlpha=ctx.globalAlpha;renderer.render(ctx,base);const n=ctx.getTransform();
assert.deepEqual([n.a,n.b,n.c,n.d,n.e,n.f],[m.a,m.b,m.c,m.d,m.e,m.f]);
assert.equal(ctx.globalAlpha,savedAlpha);assert.equal(ctx.globalCompositeOperation,'screen');assert.equal(ctx.fillStyle,'#12abef');assert.equal(ctx.imageSmoothingEnabled,false);
// Opposite colors in the same destination must not contaminate a prior draw.
const duo=createCanvas(W,H),dc=duo.getContext('2d');
renderer.render(dc,base);renderer.render(dc,{...base,side:1,green:true});
const both=pixels(duo),onlyRight=pixels(frame({side:1,green:true}));
for(let y=0;y<H;y++)for(let x=0;x<W;x++){
 const i=(y*W+x)*4,expected=x<960?a:onlyRight;
 for(let j=0;j<4;j++)assert.equal(both[i+j],expected[i+j],'simultaneous blue/green sides remain independent');
}
// Browser-style UMD uses the identical source, with an injected canvas constructor.
const sandbox={};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'glass-smoke.js'),'utf8'),sandbox);
assert(sandbox.GlassSmoke);sandbox.GlassSmoke.setCanvasFactory(createCanvas);
assert.deepEqual(pixels(frame({},sandbox.GlassSmoke)),a,'Node and browser-style UMD produce identical pixels');
// Cached hot path has no ImageData creation or readback: only texture compositing.
const benchmark=createCanvas(W,H),bc=benchmark.getContext('2d'),start=performance.now();
for(let i=0;i<120;i++){bc.clearRect(0,0,W,H);renderer.render(bc,{...base,time:10+i/60});}
const elapsed=performance.now()-start;
const report={passed:true,checks:['deterministic seek','fixed-domain growth','gentle time evolution','strict wedge bounds','zero alpha and zero/invalid length','finite invalid state sanitization','localized warmth','green heat suppression','context restoration','simultaneous blue/green side isolation','cached allocation count','UMD parity'],cachedCanvases:created,maxAlpha,changedPixelsAt04Seconds:changed,meanAlphaChangeAt04Seconds:totalDiff/(W*H),lowerUpperEnergyRatio:lowerEnergy/upperEnergy,renderMillisecondsPerFrame:elapsed/120};
fs.writeFileSync(path.join(__dirname,'qa','glass-smoke-test-results.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));
