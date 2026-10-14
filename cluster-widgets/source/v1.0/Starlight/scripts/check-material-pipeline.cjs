'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {createCanvas} = require('@napi-rs/canvas');
const Pipeline = require('../src/material-pipeline.js');
let passed=0;
function test(name,body){body();passed+=1;console.log('PASS '+name);}
function fixture(options={}){
  const workers=[],revoked=[],timers=new Map(),modes=[],tipChanges=[],layers={};let id=0;
  for(const name of ['background','beams'])layers[name]={hidden:false,transfers:0,getContext(){throw Error('Main must never create layer contexts');},transferControlToOffscreen(){this.transfers+=1;if(options.transferFails)throw Error('Transfer failed');return {name};}};
  class MockWorker{
    constructor(url){this.url=url;this.messages=[];this.terminated=0;workers.push(this);}
    postMessage(data,transfer){if(options.postFails)throw Error('Post failed');this.messages.push({data,transfer});}
    terminate(){this.terminated+=1;}
    emit(data){if(this.onmessage)this.onmessage({data});}
  }
  class MockURL extends URL{}
  MockURL.createObjectURL=()=> 'blob:starlight-material';MockURL.revokeObjectURL=url=>revoked.push(url);
  const doc={getElementById(id){return id==='starlight-background'?layers.background:id==='starlight-material'?layers.beams:null;}};
  const win={document:doc,Worker:MockWorker,OffscreenCanvas:function(){},Blob,URL:MockURL,StarlightWorkerSource:'offline worker',
    setTimeout(fn){const token=++id;timers.set(token,fn);return token;},clearTimeout(token){timers.delete(token);}};
  if(options.unsupported)delete win.OffscreenCanvas;
  if(options.sourceURL)delete win.StarlightWorkerSource;
  const api=options.api||Pipeline;
  const pipeline=api.create({window:win,document:doc,layers,onModeChange:mode=>modes.push(mode),onTipChange:state=>tipChanges.push(state),workerURL:options.workerURL});
  const input=(travel,speed=100)=>({state:{speed,speedRaw:speed,left:4,right:5,powerKw:40,batteryPowerKw:40,batteryMagnitudeKw:40,regen:false,leftLength:370,rightLength:190,batteryState:'CONSUMINDO',readouts:{speed}},starMotion:{speed,travel},menuState:{card:0}});
  function ready(index){const worker=workers[index],probe=worker.messages.find(item=>item.data.type==='probe').data;let closed=0;worker.emit({type:'ready',generation:probe.generation,probeBitmap:{close(){closed+=1;}}});assert.equal(closed,1);}
  function bound(index){const worker=workers[index],bind=worker.messages.find(item=>item.data.type==='bind').data;worker.emit({type:'bound',generation:bind.generation});}
  function start(){pipeline.setVisible(true);ready(0);ready(1);bound(0);bound(1);}
  function frame(index){return workers[index].messages.filter(item=>item.data.type==='frame').at(-1).data;}
  function ack(index,data=frame(index)){workers[index].emit({type:'frame',generation:data.generation,sequence:data.sequence,painted:true,costMs:3+index});}
  return {pipeline,win,doc,layers,workers,revoked,timers,modes,tipChanges,input,ready,bound,start,frame,ack};
}
test('both transferable2D probes precede layertransfer; UIcanvas is never referenced',()=>{
  const e=fixture();e.pipeline.setVisible(true);e.ready(0);assert.equal(e.layers.background.transfers,0);assert.equal(e.layers.beams.transfers,0);e.ready(1);assert.equal(e.layers.background.transfers,1);assert.equal(e.layers.beams.transfers,1);assert.equal(e.workers[0].messages.find(x=>x.data.type==='bind').transfer.length,1);e.bound(0);e.bound(1);assert.equal(e.pipeline.canCompose(),false);e.pipeline.cleanup();
});
test('warming sends firstframes and reveals only after both validACKs',()=>{
  const e=fixture();e.pipeline.setVisible(true);e.pipeline.submit(0,e.input(0));e.ready(0);e.ready(1);e.bound(0);e.bound(1);assert.equal(e.frame(0).state.speed,100);assert.equal(e.pipeline.canCompose(),false);e.ack(0);assert.equal(e.layers.background.hidden,true);e.ack(1);assert.equal(e.pipeline.canCompose(),true);assert.equal(e.layers.background.hidden,false);assert.equal(e.layers.beams.hidden,false);assert.equal(e.pipeline.getStatus().workers.beams.frames,1);e.pipeline.cleanup();
});
test('one frame inflight perworker and only latestpending survives highfrequencyinput',()=>{
  const e=fixture();e.start();e.pipeline.submit(0,e.input(0));for(let i=1;i<=50;i++)e.pipeline.submit(i/30,e.input(i/10));
  for(const worker of e.workers)assert.equal(worker.messages.filter(x=>x.data.type==='frame').length,1);assert.equal(e.pipeline.getStatus().workers.background.pending,true);e.ack(0);e.ack(1);assert.equal(e.frame(0).starMotion.travel,5);assert.equal(e.frame(1).time,50/30);assert.equal(e.workers[0].messages.filter(x=>x.data.type==='frame').length,2);assert.equal(e.pipeline.getStatus().workers.beams.pending,false);e.pipeline.cleanup();
});
test('stars dedupe identicalspeed/travel despite advancingtime',()=>{
  const e=fixture();e.start();e.pipeline.submit(0,e.input(0,0));e.ack(0);e.ack(1);e.pipeline.submit(1,e.input(0,0));assert.equal(e.workers[0].messages.filter(x=>x.data.type==='frame').length,1);assert.equal(e.workers[1].messages.filter(x=>x.data.type==='frame').length,2);e.pipeline.cleanup();
});
test('pause hides immediately and staleACK never reveals/incrementsframes',()=>{
  const e=fixture();e.start();e.pipeline.submit(0,e.input(0));const old=[e.frame(0),e.frame(1)];e.pipeline.setVisible(false);assert.equal(e.layers.background.hidden,true);assert.equal(e.layers.beams.hidden,true);e.ack(0,old[0]);e.ack(1,old[1]);assert.equal(e.pipeline.canCompose(),false);assert.equal(e.pipeline.getStatus().workers.background.frames,0);assert.equal(e.layers.background.hidden,true);e.pipeline.cleanup();
});
test('resume keeps oldinflight bounded; only newgeneration firstframes reveal',()=>{
  const e=fixture();e.start();e.pipeline.submit(0,e.input(0));const old=[e.frame(0),e.frame(1)];e.pipeline.setVisible(false);e.pipeline.setVisible(true);e.pipeline.submit(1,e.input(1));assert.equal(e.workers[0].messages.filter(x=>x.data.type==='frame').length,1);e.ack(0,old[0]);e.ack(1,old[1]);assert.notEqual(e.frame(0).generation,old[0].generation);assert.equal(e.layers.background.hidden,true);e.ack(0);e.ack(1);assert.equal(e.pipeline.canCompose(),true);assert.equal(e.pipeline.getStatus().workers.background.frames,1);e.pipeline.cleanup();
});
test('visibility changes during probing/binding cannot strand startupgeneration',()=>{
  const e=fixture();e.pipeline.setVisible(true);e.pipeline.setVisible(false);e.ready(0);e.ready(1);e.pipeline.setVisible(true);e.bound(0);e.bound(1);e.pipeline.submit(0,e.input(0));e.ack(0);e.ack(1);assert.equal(e.pipeline.canCompose(),true);e.pipeline.cleanup();
});
test('cleanup terminates bothworkers/revokesURL once and queuedcallbacks stay inert',()=>{
  const e=fixture();e.start();e.pipeline.submit(0,e.input(0));const handler=e.workers[0].onmessage,old=e.frame(0);e.pipeline.cleanup();e.pipeline.cleanup();assert.deepEqual(e.workers.map(w=>w.terminated),[1,1]);assert.deepEqual(e.revoked,['blob:starlight-material']);assert.equal(e.timers.size,0);handler({data:{type:'frame',generation:old.generation,sequence:old.sequence,painted:true}});assert.equal(e.layers.background.hidden,true);assert.equal(e.pipeline.getStatus().mode,'disposed');
});
test('unsupported APIs preserve fallback without transferringlayers',()=>{
  const e=fixture({unsupported:true});assert.equal(e.pipeline.getStatus().mode,'fallback');assert.equal(e.workers.length,0);assert.equal(e.layers.background.transfers,0);assert.equal(e.layers.background.hidden,true);e.pipeline.cleanup();
});
test('probe/render/load/transfer failures stopworkers and releaseofflineURL',()=>{
  for(const failure of ['probe','render','load','transfer']){
    const e=fixture({transferFails:failure==='transfer'});
    if(failure==='probe')e.workers[0].emit({type:'unsupported',reason:'probe'});
    if(failure==='render'){e.start();e.pipeline.submit(0,e.input(0));e.workers[1].emit({type:'error',reason:'render-failed'});}
    if(failure==='load')e.workers[1].onerror();
    if(failure==='transfer'){e.ready(0);e.ready(1);}
    assert.equal(e.pipeline.getStatus().mode,'fallback');assert.deepEqual(e.workers.map(w=>w.terminated),[1,1]);assert.deepEqual(e.revoked,['blob:starlight-material']);assert.equal(e.layers.beams.hidden,true);e.pipeline.cleanup();
  }
});
test('startup timeout releasesworkers without retry',()=>{
  const e=fixture();[...e.timers.values()][0]();assert.equal(e.pipeline.getStatus().reason,'worker-init-timeout');assert.equal(e.workers.length,2);assert.deepEqual(e.workers.map(w=>w.terminated),[1,1]);assert.equal(e.timers.size,0);e.pipeline.cleanup();
});
test('sourceThemeLab workerURL is relative to capturedmoduleURL',()=>{
  const context=vm.createContext({document:{currentScript:{src:'http://localhost:1234/src/material-pipeline.js'}}});vm.runInContext(fs.readFileSync(path.join(__dirname,'../src/material-pipeline.js'),'utf8'),context);
  const e=fixture({api:context.StarlightMaterialPipeline,sourceURL:true});assert.equal(e.workers[0].url,'http://localhost:1234/src/material-worker.js');e.pipeline.cleanup();
});
test('presentedtips match exactBEAMS inflightACK not latestqueuedstate or backgroundACK',()=>{
  const e=fixture();e.start();const posted=e.input(0,100);e.pipeline.submit(0,posted);posted.state.speed=777;posted.state.leftLength=999;
  const latest=e.input(1,160);latest.state.leftLength=550;latest.state.batteryPowerKw=-80;latest.state.regen=true;e.pipeline.submit(1,latest);
  e.ack(0);assert.equal(e.pipeline.getPresentedTipState(),null);assert.equal(e.tipChanges.length,0);e.ack(1);const shown=e.pipeline.getPresentedTipState();assert.equal(shown.speed,100);assert.equal(shown.leftLength,370);assert.equal(shown.batteryPowerKw,40);assert.equal(shown.regen,false);assert.equal(e.tipChanges.length,1);
  shown.speed=999;assert.equal(e.pipeline.getPresentedTipState().speed,100);e.tipChanges[0].leftLength=777;assert.equal(e.pipeline.getPresentedTipState().leftLength,370);
  e.ack(1);assert.equal(e.pipeline.getPresentedTipState().speed,160);assert.equal(e.pipeline.getPresentedTipState().leftLength,550);assert.equal(e.pipeline.getPresentedTipState().batteryPowerKw,-80);assert.equal(e.tipChanges.length,2);e.pipeline.cleanup();
});
test('phase/time-only BEAMS acknowledgments never notify tip UIchanges',()=>{
  const e=fixture();e.start();e.pipeline.submit(0,e.input(0));e.ack(0);e.ack(1);const count=e.tipChanges.length;
  for(let time=1;time<=5;time++){e.pipeline.submit(time,e.input(0));e.ack(1);assert.equal(e.tipChanges.length,count);assert.equal(e.pipeline.getPresentedTipState().speed,100);}e.pipeline.cleanup();
});
test('presentedtip cache clears on visibilitygeneration/fallback/cleanup and staleACK cannot restore',()=>{
  for(const reset of ['generation','fallback','cleanup']){
    const e=fixture();e.start();e.pipeline.submit(0,e.input(0));e.ack(0);e.ack(1);e.pipeline.submit(1,e.input(1,160));const old=e.frame(1),handler=e.workers[1].onmessage;
    if(reset==='generation'){e.pipeline.setVisible(false);e.pipeline.setVisible(true);}
    if(reset==='fallback')e.workers[0].onerror();
    if(reset==='cleanup')e.pipeline.cleanup();
    assert.equal(e.pipeline.getPresentedTipState(),null);handler({data:{type:'frame',generation:old.generation,sequence:old.sequence,painted:true,costMs:4}});assert.equal(e.pipeline.getPresentedTipState(),null);assert.equal(e.layers.beams.hidden,true);assert.equal(e.tipChanges.at(-1),null);e.pipeline.cleanup();
  }
});
test('tips preserve null/zero/signedpower/abovescale readings and position/sign changes',()=>{
  const e=fixture();e.start();const cases=[
    {speed:null,speedRaw:null,batteryPowerKw:null,batteryMagnitudeKw:0,regen:false,leftLength:70,rightLength:70,batteryState:'SEM DADOS'},
    {speed:0,speedRaw:0,batteryPowerKw:0,batteryMagnitudeKw:0,regen:false,leftLength:70,rightLength:70,batteryState:'NEUTRA'},
    {speed:255,speedRaw:255,batteryPowerKw:80,batteryMagnitudeKw:80,regen:false,leftLength:700,rightLength:590,batteryState:'CONSUMINDO'},
    {speed:255,speedRaw:255,batteryPowerKw:-80,batteryMagnitudeKw:80,regen:true,leftLength:700,rightLength:590,batteryState:'REGENERANDO'}
  ];
  cases.forEach((state,index)=>{const value=e.input(index);Object.assign(value.state,state);e.pipeline.submit(index,value);e.ack(0);e.ack(1);assert.deepEqual(e.pipeline.getPresentedTipState(),state);});assert.equal(e.tipChanges.length,4);e.pipeline.cleanup();
});

function workerFixture(options={}){
  const messages=[];
  class Offscreen{
    constructor(w,h){this.canvas=createCanvas(w,h);}
    getContext(){return options.noContext?null:this.canvas.getContext('2d');}
    transferToImageBitmap(){return {close(){}};}
  }
  const painter={setCanvasFactory(factory){this.factory=factory;},renderBackground(ctx,time,mode,state){ctx.fillStyle='#112233';ctx.fillRect(0,0,1,1);},renderBeams(ctx){if(options.paintFails)throw Error('paint');ctx.fillStyle='#ddeeff';ctx.fillRect(0,0,1,1);}};
  const self={MotionStudy:painter,postMessage(data,transfers){messages.push({data,transfers});}};
  const context=vm.createContext({self,OffscreenCanvas:Offscreen,performance:{now:()=>1}});
  const entry=fs.readFileSync(path.join(__dirname,'../src/material-worker.js'),'utf8').replace(/\/\/\s*STARLIGHT_DEV_IMPORTS_START[\s\S]*?\/\/\s*STARLIGHT_DEV_IMPORTS_END/,'');vm.runInContext(entry,context);
  const emit=data=>self.onmessage({data});return {messages,Offscreen,painter,emit};
}
test('worker probes real2D and transferbitmap before binding then paints/ACKs and clears',()=>{
  const e=workerFixture();e.emit({type:'probe',generation:1,layer:'background'});assert.equal(e.messages[0].data.type,'ready');assert.equal(e.messages[0].transfers.length,1);const layer=new e.Offscreen(1920,720);e.emit({type:'bind',generation:2,layer:'background',canvas:layer});e.emit({type:'frame',generation:2,sequence:1,time:0,state:{},starMotion:{speed:0,travel:0},menuState:{card:0}});assert.equal(e.messages.at(-1).data.painted,true);assert.equal(layer.getContext().getImageData(0,0,1,1).data[3],255);e.emit({type:'clear',generation:3});assert.equal(layer.getContext().getImageData(0,0,1,1).data[3],0);e.emit({type:'frame',generation:2,sequence:2});assert.equal(e.messages.at(-1).data.painted,false);assert.equal(layer.getContext().getImageData(0,0,1,1).data[3],0);
});
test('worker unsupported2D and material exceptions return failures without uncaughtexception',()=>{
  const unsupported=workerFixture({noContext:true});unsupported.emit({type:'probe',generation:1,layer:'background'});assert.equal(unsupported.messages[0].data.type,'unsupported');
  const e=workerFixture({paintFails:true});e.emit({type:'probe',generation:1,layer:'beams'});e.emit({type:'bind',generation:1,layer:'beams',canvas:new e.Offscreen(1920,720)});assert.doesNotThrow(()=>e.emit({type:'frame',generation:1,sequence:1,time:0,state:{},starMotion:{},menuState:{}}));assert.equal(e.messages.at(-1).data.type,'error');
});
console.log(`Starlight material pipeline: ${passed} checks passed`);
