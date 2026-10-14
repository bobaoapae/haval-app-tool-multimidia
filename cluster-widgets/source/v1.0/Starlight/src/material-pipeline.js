/* Two bounded material workers; the UI canvas always remains on the main thread. */
(function (root,factory) {
  'use strict';
  var script = root.document && root.document.currentScript;
  var sourceURL = script && script.src || '';
  var api = factory(sourceURL);
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.StarlightMaterialPipeline = api;
}(typeof globalThis !== 'undefined' ? globalThis : this,function (sourceURL) {
  'use strict';
  function create(options) {
    var win = options.window, doc = options.document || win.document;
    var layers = options.layers || {background:doc.getElementById('starlight-background'),beams:doc.getElementById('starlight-material')};
    var names = ['background','beams'], channels = {}, mode = 'initializing', reason = '', disposed = false, visible = false;
    var generation = 1, sequence = 0, objectURL = null, initTimer = null, presentedTipState = null, presentedTipGeneration = 0;
    var tipFields = ['speed','speedRaw','batteryPowerKw','batteryMagnitudeKw','regen','leftLength','rightLength','batteryState'];
    function copyTipState(state) {
      if (!state) return null;
      var copy = {};
      tipFields.forEach(function (key) { copy[key] = state[key]; });
      return copy;
    }
    function setPresentedTipState(state) {
      var next = copyTipState(state);
      var changed = !!next !== !!presentedTipState || next && tipFields.some(function (key) { return next[key] !== presentedTipState[key]; });
      presentedTipState = next; presentedTipGeneration = next ? generation : 0;
      if (changed && typeof options.onTipChange === 'function') options.onTipChange(copyTipState(next));
    }
    names.forEach(function (name) { channels[name] = {worker:null,ready:false,bound:false,inflight:null,pending:null,lastKey:null,painted:false,frames:0,lastCostMs:0,maxCostMs:0}; });
    function hide() { names.forEach(function (name) { if (layers[name]) layers[name].hidden = true; }); }
    function modeChanged(next) {
      if (mode === next) return;
      mode = next;
      if (typeof options.onModeChange === 'function') options.onModeChange(next);
    }
    function stopWorkers() {
      if (initTimer !== null) win.clearTimeout(initTimer);
      initTimer = null;
      names.forEach(function (name) {
        var channel = channels[name];
        if (channel.worker) { channel.worker.onmessage = channel.worker.onerror = channel.worker.onmessageerror = null; channel.worker.terminate(); }
        channel.worker = null; channel.inflight = channel.pending = channel.inflightTipState = null;
      });
      if (objectURL !== null) { win.URL.revokeObjectURL(objectURL); objectURL = null; }
    }
    function fallback(detail) {
      if (disposed || mode === 'fallback') return;
      reason = detail; generation += 1; hide(); setPresentedTipState(null); stopWorkers(); modeChanged('fallback');
    }
    function post(channel,message,transfers) {
      try { channel.worker.postMessage(message,transfers || []); return true; }
      catch (error) { fallback('worker-post-failed'); return false; }
    }
    function flush(name) {
      var channel = channels[name];
      if (disposed || mode === 'fallback' || !visible || !channel.bound || channel.inflight || !channel.pending) return;
      var next = channel.pending; channel.pending = null; channel.inflight = next;
      // Snapshot exactly what is posted, independent of later main-thread mutations.
      channel.inflightTipState = name === 'beams' ? copyTipState(next.state) : null;
      post(channel,next);
    }
    function reveal() {
      if (!visible || disposed || !names.every(function (name) { return channels[name].painted; })) return;
      names.forEach(function (name) { layers[name].hidden = false; });
      modeChanged('workers');
    }
    function message(name,event) {
      var data = event.data || {}, channel = channels[name];
      if (data.probeBitmap && typeof data.probeBitmap.close === 'function') data.probeBitmap.close();
      if (disposed || mode === 'fallback') return;
      if (data.type === 'unsupported' || data.type === 'error') { fallback('worker-'+(data.reason||data.type)); return; }
      if (data.type === 'ready') {
        if (channel.ready || data.generation !== channel.probeGeneration) return;
        channel.ready = true;
        if (names.every(function (item) { return channels[item].ready; })) {
          // Only after both real 2D probes succeeded may either layer be transferred.
          try {
            var transferred = {};
            names.forEach(function (item) { transferred[item] = layers[item].transferControlToOffscreen(); });
            names.forEach(function (item) { channels[item].bindGeneration = generation; post(channels[item],{type:'bind',generation:generation,layer:item,canvas:transferred[item]},[transferred[item]]); });
          } catch (error) { fallback('layer-transfer-failed'); }
        }
      } else if (data.type === 'bound') {
        if (data.generation !== channel.bindGeneration) return;
        channel.bound = true;
        if (data.generation !== generation) post(channel,{type:'clear',generation:generation});
        if (names.every(function (item) { return channels[item].bound; })) {
          if (initTimer !== null) win.clearTimeout(initTimer); initTimer = null;
          modeChanged('warming');
          names.forEach(flush);
        }
      } else if (data.type === 'frame') {
        var inflight = channel.inflight;
        if (!inflight || data.sequence !== inflight.sequence || data.generation !== inflight.generation) return;
        var tipState = channel.inflightTipState;
        channel.inflightTipState = null;
        channel.inflight = null;
        if (visible && data.generation === generation && data.painted === true) {
          channel.painted = true; channel.frames += 1;
          channel.lastCostMs = Number.isFinite(data.costMs) ? data.costMs : 0;
          channel.maxCostMs = Math.max(channel.maxCostMs,channel.lastCostMs);
          if (name === 'beams') setPresentedTipState(tipState);
          reveal();
        }
        flush(name);
      }
    }
    function setVisible(next) {
      next = next === true;
      if (disposed || next === visible) return;
      visible = next; generation += 1; hide(); setPresentedTipState(null);
      names.forEach(function (name) {
        var channel = channels[name]; channel.pending = null; channel.lastKey = null; channel.painted = false;
        if (channel.worker && channel.bound) post(channel,{type:'clear',generation:generation});
      });
      if (mode === 'workers') modeChanged('warming');
    }
    function submit(time,value) {
      if (disposed || mode === 'fallback' || !visible) return;
      names.forEach(function (name) {
        var channel = channels[name];
        var key = name === 'background' ? String(value.starMotion.speed)+'/'+String(value.starMotion.travel) : String(time)+'/'+JSON.stringify(value.state);
        if (key === channel.lastKey) return;
        channel.lastKey = key;
        // Replace the one queued frame with the newest input. Never queue an animation history.
        channel.pending = {type:'frame',generation:generation,sequence:++sequence,time:time,state:value.state,starMotion:value.starMotion,menuState:{card:value.menuState.card}};
        flush(name);
      });
    }
    function cleanup() {
      if (disposed) return;
      disposed = true; generation += 1; visible = false; hide(); setPresentedTipState(null); stopWorkers(); mode = 'disposed';
    }
    function status() {
      var workers = {};
      names.forEach(function (name) {
        var c = channels[name]; workers[name] = {ready:c.ready,bound:c.bound,frames:c.frames,lastCostMs:c.lastCostMs,maxCostMs:c.maxCostMs,inflight:!!c.inflight,pending:!!c.pending};
      });
      return {mode:mode,reason:reason,visible:visible,generation:generation,workers:workers};
    }
    hide();
    var supported = typeof win.Worker === 'function' && typeof win.OffscreenCanvas === 'function' && names.every(function (name) {
      return layers[name] && typeof layers[name].transferControlToOffscreen === 'function';
    });
    if (!supported) fallback('unsupported');
    else {
      try {
        var url;
        if (typeof win.StarlightWorkerSource === 'string' && typeof win.Blob === 'function' && win.URL && typeof win.URL.createObjectURL === 'function') {
          objectURL = win.URL.createObjectURL(new win.Blob([win.StarlightWorkerSource],{type:'text/javascript'})); url = objectURL;
        } else {
          if (!sourceURL && !options.workerURL) throw new Error('Missing worker source URL');
          url = options.workerURL || new win.URL('material-worker.js',sourceURL).href;
        }
        names.forEach(function (name) {
          if (mode === 'fallback') return;
          var channel = channels[name]; channel.worker = new win.Worker(url);
          channel.worker.onmessage = function (event) { message(name,event); };
          channel.worker.onerror = function () { fallback('worker-load-failed'); };
          channel.worker.onmessageerror = function () { fallback('worker-message-failed'); };
          channel.probeGeneration = generation;
          post(channel,{type:'probe',generation:generation,layer:name});
        });
        if (mode !== 'fallback') initTimer = win.setTimeout(function () { fallback('worker-init-timeout'); },5000);
      } catch (error) { fallback('worker-init-failed'); }
    }
    return {submit:submit,setVisible:setVisible,canCompose:function () { return mode === 'workers' && visible && !disposed; },
      getPresentedTipState:function () { return !disposed && visible && mode === 'workers' && presentedTipGeneration === generation ? copyTipState(presentedTipState) : null; },
      cleanup:cleanup,getStatus:status};
  }
  return {create:create};
}));
