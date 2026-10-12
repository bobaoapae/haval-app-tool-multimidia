/* Development-only real-browser QA. No APK/OTA output and no vehicle connection.
 * Run: node test-browser.cjs (Playwright), or node test-browser.cjs --serve and open
 * the printed loopback URL in a managed browser. Both modes run the same checks.
 * Lifecycle events/Android bridge are synthetic fixtures, never hardware tests. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const root = __dirname;
let result = null;
const artifactDir = process.env.STARLIGHT_QA_OUTPUT || fs.mkdtempSync(path.join(os.tmpdir(), 'starlight-browser-qa-'));
const mime = {'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.ttf':'font/ttf','.json':'application/json'};

function instrumentation(android) {
  window.__qa = {active:new Set(),requested:0,cancelled:0,frames:0,remoteAttempts:[],nativeCalls:[],errors:[]};
  window.addEventListener('error', event => window.__qa.errors.push(event.message));
  window.addEventListener('unhandledrejection', event => window.__qa.errors.push(String(event.reason))); 
  const qa = window.__qa, raf = window.requestAnimationFrame.bind(window), cancel = window.cancelAnimationFrame.bind(window);
  window.requestAnimationFrame = callback => {let id; id = raf(time => {qa.active.delete(id);qa.frames++;callback(time);});qa.active.add(id);qa.requested++;return id;};
  window.cancelAnimationFrame = id => {qa.active.delete(id);qa.cancelled++;cancel(id);};
  const record = url => {try {const u = new URL(url, location.href);if (u.origin !== location.origin && !['data:','blob:'].includes(u.protocol)) qa.remoteAttempts.push(u.href);} catch (_) {qa.remoteAttempts.push(String(url));}};
  const fetch = window.fetch.bind(window);window.fetch = (url,...args) => {record(typeof url === 'string' ? url : url.url);return fetch(url,...args);};
  const open = XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open = function(method,url,...args) {record(url);return open.call(this,method,url,...args);};
  const beacon = navigator.sendBeacon.bind(navigator);navigator.sendBeacon = (url,...args) => {record(url);return beacon(url,...args);};
  for (const name of ['WebSocket','EventSource']) {const Native = window[name];if (Native) window[name] = new Proxy(Native,{construct(target,args) {record(args[0]);return Reflect.construct(target,args);}});}
  if (android) window.Android = new Proxy({}, {get(_target,key) {qa.nativeCalls.push(String(key));return function () {qa.nativeCalls.push('call:'+String(key));throw new Error('Native bridge must never be called by development study');};}});
}

async function browserSuite() {
  const report = {passed:0,checks:[],failures:[],limitations:[
    'Real Chromium browser with browser-rendered Canvas; synthetic DOM events drive the repeatable suite.',
    'document.hidden and pagehide checks use explicit lifecycle fixtures; not an Android WebView/BFCache validation.',
    'Android is a sentinel fixture; no physical vehicle, native ADAS composition, GPU budget or embedded performance claim.'
  ],userAgent:navigator.userAgent,viewports:[],network:[]};
  const status = document.querySelector('#status'), frames = document.querySelector('#frames');
  const check = (value,label) => {if (!value) throw new Error(label);report.passed++;report.checks.push(label);};
  const eq = (actual,expected,label) => check(Object.is(actual,expected),label+' (got '+JSON.stringify(actual)+')');
  const delay = ms => new Promise(resolve => setTimeout(resolve,ms));
  async function until(predicate,label,timeout=12000) {
    const start=Date.now();
    while(!predicate()) {if(Date.now()-start>timeout) throw new Error('Timed out: '+label);await delay(16);}
  }
  async function frame(url,width=1920,height=720) {
    const f = document.createElement('iframe');f.style.cssText=`width:${width}px;height:${height}px;border:0;display:block`;f.width=width;f.height=height;
    await new Promise((resolve,reject) => {const timeout=setTimeout(()=>reject(new Error('Frame load timeout: '+url)),15000);f.onload=()=>{clearTimeout(timeout);resolve();};f.onerror=error=>{clearTimeout(timeout);reject(error);};f.src=url;frames.append(f);});
    const w=f.contentWindow,d=w.document;await Promise.race([d.fonts.ready,delay(15000).then(()=>{throw new Error('Fonts ready timeout: '+url);})]);
    report.viewports.push({url,width:w.innerWidth,height:w.innerHeight});
    return {f,w,d,el:id=>d.getElementById(id),click:id=>d.getElementById(id).click(),key:(target,key,extra={})=>target.dispatchEvent(new w.KeyboardEvent('keydown',{key,bubbles:true,cancelable:true,...extra}))};
  }
  function network(t) {
    const resources=t.w.performance.getEntriesByType('resource').map(item=>item.name);
    const outside=resources.filter(url=>new URL(url,t.w.location.href).origin!==location.origin&&!url.startsWith('data:'));
    report.network.push({page:t.w.location.pathname,remoteAttempts:t.w.__qa.remoteAttempts,externalResources:outside,localResources:resources.length});
    eq(t.w.__qa.errors.length,0,'no page errors or unhandled rejections');
    eq(t.w.__qa.remoteAttempts.length,0,'no outbound API/socket/beacon attempts');
    eq(outside.length,0,'all loaded assets are same-origin local');
  }
  async function screenshot(t,name) {
    const data=t.el('panel').toDataURL('image/png');
    await fetch('/__qa-artifact?name='+encodeURIComponent(name),{method:'POST',body:data});
  }
  async function section(name,fn) {
    status.textContent='Running: '+name;
    try {await fn();} catch(error) {report.failures.push({section:name,error:String(error.stack||error)});} finally {
      for(const f of frames.querySelectorAll('iframe')) {try {f.contentWindow.impulsePreview?.destroy();f.contentWindow.starlightContractLab?.cleanup();} catch(_) {} f.remove();}
    }
  }
  await section('Desktop preview, all menus, settings and AC',async()=>{
    const t=await frame('/index.html?qa=1'),{w,d,el,click,key}=t;
    check(!!w.impulsePreview,'browser preview bootstraps');
    const app=w.impulsePreview;app.seek(12);
    eq(el('panel').width,1920,'native canvas width');eq(el('panel').height,720,'native canvas height');
    eq(w.innerWidth,1920,'desktop iframe is exactly 1920px wide');eq(w.innerHeight,720,'desktop iframe is exactly 720px high');
    for(const name of ['Speed Digits','Scale Digits','Header Sans','Panel Sans','Label Sans']) {const faces=await d.fonts.load('20px "'+name+'"');check(faces.length>0&&faces.every(f=>f.status==='loaded'),'local '+name+' font loaded');}
    check(el('font-warning').hidden,'no font fallback warning');check(el('fatal-error').hidden,'no fatal preview error');
    await screenshot(t,'starlight-desktop-panel.png');
    for(let i=0;i<20;i++){click('open-main');eq(app.getState().card,1,'repeat '+i+' opens menu');eq(el('menu-main').querySelectorAll('button').length,4,'main rows stay bounded');click('menu-close');eq(app.getState().card,0,'repeat '+i+' closes menu');eq(d.activeElement,el('open-main'),'close restores opener');}
    click('open-main');
    const rows=()=>el('menu-main').querySelectorAll('button');
    rows()[0].click();eq(w.StarlightMenuState.getView(app.getState()),'info','information opens');check(el('stats-info').textContent.includes('18.654'),'odometer readout rendered');
    el('tabs-info').querySelectorAll('button')[1].click();check(el('stats-info').textContent.includes('PSI'),'tyre readings rendered');click('menu-back');
    rows()[1].click();eq(w.StarlightMenuState.getView(app.getState()),'graphs','graph opens');el('tabs-graph').querySelectorAll('button')[1].click();check(el('graph-reading').textContent.includes('km/h'),'speed graph readout rendered');check(el('graph-range').textContent.includes('200'),'graph scale stays 200 km/h');click('menu-back');
    rows()[2].click();eq(w.StarlightMenuState.getView(app.getState()),'settings','settings opens');
    const settings=el('menu-settings').querySelectorAll('button');eq(settings.length,6,'six setting rows');
    const before=app.getState().data.driveMode;settings[0].click();check(app.getState().data.driveMode!==before,'drive setting changes local value');const changed=app.getState().data.driveMode;app.seek(14);eq(app.getState().data.driveMode,changed,'painting preserves changed setting');
    const command=JSON.stringify(app.getState().lastCommand);eq(settings[5].getAttribute('aria-disabled'),'true','Apex Display remains disabled');settings[5].click();eq(JSON.stringify(app.getState().lastCommand),command,'disabled Display produces no action');click('menu-back');
    rows()[3].click();check(el('trip-reset').hidden,'Trip A has no reset');el('tabs-trip').querySelectorAll('button')[1].click();check(!el('trip-reset').hidden,'Trip B reset available');click('trip-reset');eq(app.getState().data.tripBDistance,0,'Trip B reset is local and visible');click('menu-close');
    click('open-ac');eq(w.StarlightMenuState.getView(app.getState()),'ac','AC opens');const fan=app.getState().data.acFan;click('ac-increase');eq(app.getState().data.acFan,fan+1,'AC fan increases one step');
    el('ac-fields').querySelectorAll('button')[0].click();const temp=app.getState().data.acTemp;click('ac-increase');eq(app.getState().data.acTemp,temp+0.5,'AC temp increases half degree');check(el('menu-status').textContent.includes(String(temp+0.5).replace('.',',')),'AC announces changed temperature');
    const auto=app.getState().data.acAuto;click('ac-auto');eq(app.getState().data.acAuto,1-auto,'AC Auto toggles');const recycle=app.getState().data.acRecycle;click('ac-recycle');eq(app.getState().data.acRecycle,1-recycle,'AC recirculation toggles');
    app.dispatch({type:'TELEMETRY',values:{acTemp:32}});check(el('ac-increase').disabled,'AC upper boundary disables increase');app.dispatch({type:'TELEMETRY',values:{acTemp:16}});check(el('ac-decrease').disabled,'AC lower boundary disables decrease');
    el('data').value='unavailable';el('data').dispatchEvent(new w.Event('change',{bubbles:true}));for(const id of ['ac-increase','ac-decrease','ac-auto','ac-recycle']) check(el(id).disabled,'missing data disables '+id);check(el('ac-fields').textContent.includes('--'),'missing AC values use --');click('menu-close');
    click('open-main');rows()[2].click();eq(el('menu-settings').querySelector('button').getAttribute('aria-disabled'),'true','missing drive settings disabled');
    el('data').value='demo';el('data').dispatchEvent(new w.Event('change',{bubbles:true}));eq(app.getState().data.driveMode,1,'demo values restore');click('menu-close');
    el('panel').focus();key(el('panel'),'Enter');eq(app.getState().card,1,'canvas Enter opens menu');const selected=app.getState().menuIndex;key(el('panel'),'ArrowDown');eq(app.getState().menuIndex,(selected+1)%4,'canvas ArrowDown changes selected row once');key(el('panel'),'Escape');eq(app.getState().card,0,'canvas Escape closes');
    const geometry=w.ThemeMenus;app.openMain();const hit=geometry.layout(app.getState()).find(h=>h.id==='close');check(!!hit,'canvas close hit target exists');const b=el('panel').getBoundingClientRect();el('panel').dispatchEvent(new w.MouseEvent('click',{bubbles:true,clientX:b.left+(hit.x+hit.w/2)*b.width/1920,clientY:b.top+(hit.y+hit.h/2)*b.height/720}));eq(app.getState().card,0,'canvas close pointer maps correctly');
    network(t);app.destroy();eq(w.__qa.active.size,0,'destroy cancels all pending preview RAF');t.f.remove();
  });
  await section('Responsive preview at 390 × 844',async()=>{
    const t=await frame('/index.html?qa=1',390,844),{w,d,el,click}=t;check(!!w.impulsePreview,'mobile preview bootstraps');w.impulsePreview.seek(12);click('open-main');
    eq(w.innerWidth,390,'mobile iframe is exactly 390px wide');eq(w.innerHeight,844,'mobile iframe is exactly 844px high');eq(el('panel').getBoundingClientRect().width,d.documentElement.clientWidth,'canvas fills available mobile width');check(d.documentElement.scrollWidth<=d.documentElement.clientWidth,'no horizontal page overflow');
    eq(w.getComputedStyle(el('menu-main')).gridTemplateColumns.split(' ').length,1,'mobile menu is a single column');
    const buttons=el('menu-main').querySelectorAll('button');for(const button of buttons) check(button.getBoundingClientRect().height>=44,'mobile menu touch target is at least 44px');
    buttons[2].click();check(d.documentElement.scrollWidth<=d.documentElement.clientWidth,'settings fit mobile width');click('open-ac');check(d.documentElement.scrollWidth<=d.documentElement.clientWidth,'AC fits mobile width');
    await screenshot(t,'starlight-mobile-panel.png');network(t);w.impulsePreview.destroy();t.f.remove();
  });
  await section('Visibility and pagehide cleanup',async()=>{
    const t=await frame('/index.html?qa=1'),{w,d,el,click}=t;const app=w.impulsePreview;check(!!app,'lifecycle preview bootstraps');app.seek(1);click('toggle');await until(()=>app.getPlayback().time>1,'preview animation starts');check(app.getPlayback().time>1,'real RAF advances animation');check(w.__qa.active.size<=1,'animation maintains at most one RAF');
    Object.defineProperty(d,'hidden',{configurable:true,value:true});d.dispatchEvent(new w.Event('visibilitychange'));eq(w.__qa.active.size,0,'hidden cancels pending RAF');const paused=app.getPlayback().time;await delay(80);eq(app.getPlayback().time,paused,'hidden time stays fixed');
    Object.defineProperty(d,'hidden',{configurable:true,value:false});d.dispatchEvent(new w.Event('visibilitychange'));await until(()=>app.getPlayback().time>paused,'visible preview resumes');check(app.getPlayback().time>paused,'visible resumes playback');
    w.dispatchEvent(new w.PageTransitionEvent('pagehide'));eq(w.__qa.active.size,0,'pagehide cancels pending RAF');const saved=JSON.stringify(app.getState()),time=app.getPlayback().time;click('open-main');click('open-ac');click('toggle');await delay(80);eq(JSON.stringify(app.getState()),saved,'pagehide removes interaction listeners');eq(app.getPlayback().time,time,'pagehide leaves no animation');w.dispatchEvent(new w.PageTransitionEvent('pagehide'));eq(w.__qa.active.size,0,'repeated pagehide is harmless');network(t);t.f.remove();
  });
  await section('Native Android host blocks preview bootstrap',async()=>{
    const t=await frame('/index.html?qa=1&android=1'),{w,d}=t;check(!w.impulsePreview,'Android sentinel prevents preview bootstrap');eq(w.__qa.requested,0,'Android sentinel starts no RAF');eq(w.__qa.nativeCalls.length,0,'Android sentinel is never read/called');check(w.getComputedStyle(d.querySelector('main')).display==='none'||w.getComputedStyle(d.querySelector('main')).visibility==='hidden','development main is hidden in native host');network(t);t.f.remove();
  });
  await section('Contract lab fixtures and host guard',async()=>{
    const t=await frame('/contract-lab.html?qa=1'),{w,d,el,click}=t;check(!!el('contract-scenario'),'contract lab scenario selector exists');check(!!w.starlightContractLab,'contract lab bootstraps');
    const lab=w.starlightContractLab;
    await until(()=>lab.last&&lab.adapter.getStatus().ready&&!lab.adapter.getStatus().pendingFrame,'contract initial font load and snapshot');
    const expected={missing:[null,null,null,null],zero:[0,0,0,74],drive:[83,40,0,74],regen:[83,-40,0,74],'missing-current':[83,null,0,74],'above-scale':[231,40,0,74]};
    for(const option of Array.from(el('contract-scenario').options)){
      const values=expected[option.value];check(!!values,'fixture has explicit expected values: '+option.value);
      el('contract-scenario').value=option.value;click('contract-apply');
      await until(()=>!lab.adapter.getStatus().pendingFrame&&lab.last.decoded.speed===values[0]&&lab.last.decoded.powerKw===values[1],'contract '+option.value+' snapshot');
      const reading=JSON.parse(el('contract-reading').textContent);
      eq(reading.rawCanSpeed,values[0],'contract '+option.value+' exact raw CAN speed');eq(reading.batteryPowerKw,values[1],'contract '+option.value+' exact signed battery power');
      eq(reading.fuelPct,values[2],'contract '+option.value+' preserves fuel zero/absence');eq(reading.batteryPct,values[3],'contract '+option.value+' battery value');eq(reading.hardwareReady,false,'contract remains hardware-unvalidated');
      const visual=w.StarlightContractRenderer.state(lab.last.decoded);eq(visual.speed,values[0],'render seam preserves numeric speed '+option.value);eq(visual.batteryPowerKw,values[1],'render seam preserves power '+option.value);
      eq(w.__qa.active.size,0,'coalesced contract render settles without RAF loop');
    }
    const framesBefore=w.__qa.frames;await delay(100);eq(w.__qa.frames,framesBefore,'idle contract lab does not animate');
    check(!('Android' in w),'contract lab adds no Android host');for(const name of ['onDataChanged','control','onCardChanged','onKeyEvent','cleanup'])check(!(name in w),'contract lab installs no native '+name+' callback');
    Object.defineProperty(d,'hidden',{configurable:true,value:true});d.dispatchEvent(new w.Event('visibilitychange'));
    el('contract-scenario').value='regen';click('contract-apply');eq(w.__qa.active.size,0,'hidden contract fixture does not schedule rendering');
    Object.defineProperty(d,'hidden',{configurable:true,value:false});d.dispatchEvent(new w.Event('visibilitychange'));
    await until(()=>lab.last.decoded.powerKw===-40&&!lab.adapter.getStatus().pendingFrame,'contract visibility resume');
    network(t);await screenshot(t,'starlight-contract-lab.png');
    w.dispatchEvent(new w.PageTransitionEvent('pagehide'));check(lab.disposed,'contract pagehide disposes lab');check(lab.adapter.getStatus().disposed,'contract pagehide disposes adapter');eq(w.__qa.active.size,0,'contract pagehide cancels RAF');
    const read=el('contract-reading').textContent;el('contract-scenario').value='zero';click('contract-apply');await delay(40);eq(el('contract-reading').textContent,read,'pagehide contract lab stays stable');t.f.remove();
    const blocked=await frame('/contract-lab.html?qa=1&android=1');check(!blocked.w.starlightContractLab,'Android sentinel prevents contract lab bootstrap');eq(blocked.w.__qa.nativeCalls.length,0,'contract lab never reads/calls Android sentinel');network(blocked);blocked.f.remove();
  });
  report.ok=report.failures.length===0;document.querySelector('#result').textContent=JSON.stringify(report,null,2);status.textContent=(report.ok?'PASS':'FAIL')+': '+report.passed+' assertions';document.title='Starlight browser QA '+(report.ok?'PASS':'FAIL');
  await fetch('/__qa-result',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(report)});
}

const html = `<!doctype html><meta charset="utf-8"><title>Starlight browser QA</title><style>body{font:15px monospace;background:#10151a;color:#dbe7ed}iframe{position:relative;margin:12px 0}pre{white-space:pre-wrap}</style><h1>Starlight development browser QA</h1><p id="status">Starting</p><pre id="result"></pre><div id="frames"></div><script>(${browserSuite.toString()})();</script>`;
const server = http.createServer(async (req,res)=>{
  const url=new URL(req.url,'http://127.0.0.1');
  if(req.method==='POST'&&['/__qa-result','/__qa-artifact'].includes(url.pathname)) {
    let body='';for await (const chunk of req) {body+=chunk;if(body.length>15e6){res.writeHead(413).end();return;}}
    if(url.pathname==='/__qa-result'){result=JSON.parse(body);fs.writeFileSync(path.join(artifactDir,'browser-results.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify({ok:result.ok,passed:result.passed,failures:result.failures,artifactDir}));}
    else {const name=url.searchParams.get('name');if(!/^starlight-[a-z-]+\.png$/.test(name||'')){res.writeHead(400).end();return;}fs.writeFileSync(path.join(artifactDir,name),Buffer.from(body.replace(/^data:image\/png;base64,/,''),'base64'));}
    res.writeHead(200,{'content-type':'text/plain'}).end('ok');return;
  }
  if(url.pathname==='/__qa-result'){res.writeHead(200,{'content-type':'application/json'}).end(JSON.stringify({result,artifactDir}));return;}
  if(url.pathname==='/__qa'){res.writeHead(200,{'content-type':'text/html; charset=utf-8'}).end(html);return;}
  const file=path.resolve(root,'.'+decodeURIComponent(url.pathname==='/'?'/index.html':url.pathname));
  if(!file.startsWith(root+path.sep)){res.writeHead(403).end();return;}
  try {let body=fs.readFileSync(file);if(path.extname(file)==='.html'&&url.searchParams.has('qa')) body=body.toString().replace('<head>','<head><script>('+instrumentation.toString()+')('+url.searchParams.has('android')+');<\/script>');res.writeHead(200,{'content-type':mime[path.extname(file)]||'application/octet-stream','cache-control':'no-store'}).end(body);}catch(_){res.writeHead(404).end('Not found');}
});
server.listen(Number(process.env.PORT||0),'127.0.0.1',async()=>{
  const url=`http://127.0.0.1:${server.address().port}/__qa`;console.log(JSON.stringify({url,artifactDir}));
  if(process.argv.includes('--serve'))return;
  let browser;
  try {const {chromium}=require('playwright');browser=await chromium.launch({headless:true,...(process.env.BROWSER_PATH?{executablePath:process.env.BROWSER_PATH}:{})});const page=await browser.newPage({viewport:{width:1920,height:720}});await page.goto(url);await page.waitForFunction(()=>document.title.endsWith('PASS')||document.title.endsWith('FAIL'),null,{timeout:120000});for(let i=0;!result&&i<100;i++)await page.waitForTimeout(20);assert.ok(result,'browser posted its report');assert.equal(result.ok,true,JSON.stringify(result.failures));console.log(`Browser QA: ${result.passed} assertions passed`);}catch(error){console.error(error);process.exitCode=1;}finally{if(browser)await browser.close();server.close();}
});
