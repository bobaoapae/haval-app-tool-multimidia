/* Starlight real-host lifecycle. No demo, simulator or host/projection actions. */
(function (root, factory) {
  'use strict';
  if (typeof module === 'object' && module.exports) module.exports = factory(require('./apex-data'),require('./contract-adapter'),require('./menu-state'));
  else {
    root.StarlightHostRuntime = factory(root.ApexData,root.StarlightContractAdapter,root.StarlightMenuState);
    if (root.document && root.document.getElementById('starlight')) root.starlightRuntime = root.StarlightHostRuntime.create({window:root,document:root.document});
  }
}(typeof globalThis !== 'undefined' ? globalThis : this, function (apex, adapterApi, menuApi) {
  'use strict';
  var own = function (object,key) { return Object.prototype.hasOwnProperty.call(object,key); };
  var writesAllowlist = {
    'car.drive_setting.drive_mode':[0,2,1], 'car.ev_setting.power_model_config':[0,1,3],
    'car.drive_setting.steering_wheel_assist_mode':[2,0,1], 'car.ev_setting.energy_recovery_level':[2,0,1],
    'car.drive_setting.esp_enable':[0,1], 'car.hvac.power_mode':[0,1], 'car.hvac.cycle_mode':[0,1],
    'car.hvac.auto_enable':[0,1], 'car.hvac.fan_speed':[0,1,2,3,4,5,6,7]
  };
  var resetKeys = ['car.basic.accumulated_odometer','car.basic.accumulated_drivetime','car.basic.avg_fuel_consumption','car.basic.vehicle_speed_since_reset'];

  function create(options) {
    var win = options.window, doc = options.document || win.document;
    var root = doc.getElementById('starlight'), canvas = doc.getElementById('starlight-canvas');
    if (!root || !canvas) throw new Error('Missing Starlight canvas');
    var ctx = canvas.getContext('2d'), painter = options.renderer || win.MotionStudy;
    var themeMenus = options.themeMenus || win.ThemeMenus;
    if (!ctx || !painter) throw new Error('Missing Starlight painter');
    var overlay = doc.getElementById('native-readouts');
    var disposed = false, frame = null, epoch = 0, clockTimer = null, heartbeatTimer = null;
    var menu = menuApi.createState(), pending = null, clockText = '--:--', dirty = true;
    var travel = 0, lastTravelAt = null, lastSpeed = 0, lastPaintAt = -Infinity;
    var framesRendered = 0, renderErrors = 0, paintCostMs = 0, uiFrames = 0, uiPaintMs = 0, lastRenderMode = null;
    var lastEnabled = null, lastProjection = null, lastCard = null, pipeline = null;
    var removers = [], ownedGlobals = {}, reducedMotion = false;
    var now = function () { return win.performance && typeof win.performance.now === 'function' ? win.performance.now() : Date.now(); };
    if (typeof win.matchMedia === 'function') reducedMotion = !!win.matchMedia('(prefers-reduced-motion: reduce)').matches;

    function active(state) { return !disposed && !doc.hidden && state.clusterEnabled && !state.warningActive; }
    function commandsAllowed(state) { return active(state) && (state.cardId === 1 || state.cardId === 3); }
    function animating(state) {
      return !reducedMotion && !state.projection && (state.decoded.speed > 0 || state.decoded.powerKw !== null && state.decoded.powerKw !== 0);
    }
    function clearCanvas() {
      ctx.clearRect(0,0,1920,720);
      canvas.hidden = true;
      if (pipeline) pipeline.setVisible(false);
    }
    function cancelFrame() {
      epoch += 1;
      if (frame !== null) win.cancelAnimationFrame(frame);
      frame = null;
      lastTravelAt = null;
    }
    function visibility(state) {
      var enabled = active(state);
      if (enabled === lastEnabled && state.projection === lastProjection && state.cardId === lastCard) return;
      lastEnabled = enabled; lastProjection = state.projection; lastCard = state.cardId;
      root.style.visibility = enabled ? 'visible' : 'hidden';
      canvas.hidden = !enabled || state.projection;
      if (overlay) overlay.hidden = !enabled || !state.projection;
      if (!enabled || state.projection) clearCanvas();
      else if (pipeline) pipeline.setVisible(true);
      if (!enabled) cancelFrame();
    }
    function schedule() {
      if (disposed || frame !== null) return;
      var state = adapter.getState(clockText);
      if (!active(state)) return;
      var scheduledEpoch = epoch;
      frame = win.requestAnimationFrame(function (timestamp) {
        if (scheduledEpoch !== epoch || disposed) return;
        frame = null;
        var current = adapter.getState(clockText);
        if (!active(current)) return;
        var at = Number.isFinite(timestamp) ? timestamp : now();
        if (at-lastPaintAt >= 1000/30-0.1 && (dirty || animating(current))) {
          lastPaintAt = at;
          render(current,at);
        }
        if (dirty || animating(current)) schedule();
      });
    }
    function text(id,value) {
      var element = doc.getElementById('native-'+id);
      if (element && element.textContent !== value) element.textContent = value;
    }
    function updateOverlay(decoded) {
      var f = apex.fmt, value = function (n,unit,decimals) { return f(n,decimals||0)+(n === null ? '' : ' '+unit); };
      text('speed',value(decoded.speed,'km/h'));
      var power = decoded.powerKw;
      text('power',power === null ? '--' : (power > 0 ? '+' : power < 0 ? '−' : '')+f(Math.abs(power),1)+' kW · '+(power < 0 ? 'REGEN.' : power > 0 ? 'CONSUMO' : 'NEUTRA'));
      text('rpm',decoded.showRpm ? value(decoded.rpm,'rpm') : '--');
      text('fuel',value(decoded.fuelPct,'%'));
      text('fuel-liters',value(decoded.fuelLiters,'L est.',1));
      text('battery',value(decoded.batteryPct,'%'));
      text('gear',decoded.gear);
      text('drive-mode',decoded.driveMode);
      text('propulsion-mode',decoded.propulsionMode);
      text('inside-temp',value(decoded.insideTemp,decoded.tempUnit));
      text('outside-temp',value(decoded.outsideTemp,decoded.tempUnit));
      text('odometer',value(decoded.odometer,'km'));
      text('clock',decoded.clock);
    }
    function paintFailed() {
      renderErrors += 1;
      // Native content stays uncovered; heartbeat survives until host teardown.
      clearCanvas(); cancelFrame(); reducedMotion = true;
      dirty = false;
    }
    function render(state,at) {
      var uiDirty = dirty;
      dirty = false;
      visibility(state);
      if (state.projection) {
        // The native map stays visible; only real host card1/3 overlays may paint.
        try {
          ctx.clearRect(0,0,1920,720);
          updateOverlay(state.decoded);
          canvas.hidden = state.cardId !== 1 && state.cardId !== 3;
          if (!canvas.hidden && themeMenus && typeof themeMenus.render === 'function') {
            var projectionStarted = now();
            themeMenus.render(ctx,menu,{speed:state.decoded.speed,readouts:state.decoded,projection:true});
            uiFrames += 1; uiPaintMs = now()-projectionStarted;
          }
        } catch (error) { paintFailed(); }
        lastTravelAt = null;
        return;
      }
      canvas.hidden = false;
      var speed = state.decoded.speed, power = state.decoded.powerKw;
      if (lastTravelAt !== null) {
        var delta = Math.max(0,Math.min(250,at-lastTravelAt))/1000;
        // Galaxy travel is depth phase, not metres. Integrate its original speed rate.
        var speedRate = win.GalaxyStars && typeof win.GalaxyStars.speedRate === 'function' ? win.GalaxyStars.speedRate : function (value) { return Math.max(0,Math.min(360,value))*0.0032; };
        travel += (speedRate(lastSpeed)+speedRate(speed===null?0:speed))*0.5*delta;
      }
      lastTravelAt = at;
      lastSpeed = speed===null?0:speed;
      var input = {time:at/1000,mode:'live',speedRaw:speed,powerKw:power,batteryPowerKw:power,tractionKw:Math.max(0,power===null?0:power)};
      var visual = painter.functionalState(input);
      // Numeric readings preserve absence and above-scale speed; geometry alone saturates.
      visual.speed = speed; visual.speedRaw = speed; visual.powerKw = power;
      visual.readouts = state.decoded;
      var began = now();
      try {
        var renderOptions = {state:visual,starMotion:{speed:speed===null?0:speed,travel:travel},menuState:menu,polished:true};
        if (pipeline) { pipeline.setVisible(true); pipeline.submit(at/1000,renderOptions); }
        var workerMode = pipeline && pipeline.canCompose() && typeof painter.renderForeground === 'function';
        if (!workerMode || uiDirty || lastRenderMode !== 'workers') {
          var uiStarted = now();
          var draw = workerMode ? painter.renderForeground : painter.render || painter.renderPanel;
          // Tips follow the bar actually presented; central readings remain latest CAN data.
          if (workerMode) renderOptions.tipState = pipeline.getPresentedTipState();
          draw(ctx,at/1000,'live',renderOptions);
          if (themeMenus && typeof themeMenus.render === 'function') themeMenus.render(ctx,menu,visual);
          uiFrames += 1; uiPaintMs = now()-uiStarted;
        }
        lastRenderMode = workerMode ? 'workers' : 'fallback';
        framesRendered += 1;
        paintCostMs = now()-began;
      } catch (error) {
        paintFailed();
      }
    }
    function acknowledge(key,value,canonical) {
      if (!pending || !canonical || !own(pending.expected,key)) return;
      var complete;
      if (pending.reset) {
        // Require a fresh valid zero for every Trip B field after the request.
        // A stale/null reading also revokes a previous zero for that same field.
        pending.receivedByKey[key] = apex.finite(value) === 0;
        complete = resetKeys.every(function (resetKey) { return pending.receivedByKey[resetKey] === true; });
      } else {
        if (apex.finite(value) === pending.expected[key]) delete pending.expected[key];
        complete = !Object.keys(pending.expected).length;
      }
      if (complete) {
        pending = null; menu.feedback = ''; menu.lastCommand = null;
      }
    }
    function onChange(key,value,canonical) {
      if (disposed) return;
      if (key === 'cardId') {
        menu = menuApi.dispatch(menu,{type:'HOST_CARD',card:value});
        pending = null;
      } else if (own(menuApi.metadata.keyFields,key)) {
        var values = {}; values[key] = value;
        if (key === apex.KEYS.speed) values[key] = adapter.getState(clockText).decoded.speed;
        menu = menuApi.dispatch(menu,{type:'TELEMETRY',values:values,now:now()});
        acknowledge(key,value,canonical);
      } else if (adapterApi.preferenceKeys.indexOf(key) !== -1) {
        var speedValues = {}; speedValues[apex.KEYS.speed] = adapter.getState(clockText).decoded.speed;
        menu = menuApi.dispatch(menu,{type:'TELEMETRY',values:speedValues,now:now()});
      }
      dirty = true;
      // Visibility is urgent: native surfaces must open before the next paint.
      var state = adapter.getState(clockText);
      visibility(state);
      if (state.projection || !active(state)) lastTravelAt = null;
      schedule();
    }
    var adapter = adapterApi.create({bridge:win.Android,onChange:onChange,onCanonical:function (key,value) {
      var awaited = !!pending;
      acknowledge(key,value,true);
      if (awaited && !pending) { dirty = true; schedule(); }
    }});
    if (typeof painter.renderForeground === 'function' && win.StarlightMaterialPipeline) {
      pipeline = win.StarlightMaterialPipeline.create({window:win,document:doc,onModeChange:function () {
        if (!disposed) { dirty = true; schedule(); }
      },onTipChange:function () {
        if (!disposed) { dirty = true; schedule(); }
      }});
    }
    function validWrite(write) {
      var value = apex.finite(write.value);
      if (value === null) return false;
      if (write.key === 'car.hvac.driver_temperature') return value >= 16 && value <= 32 && Math.round(value*2) === value*2;
      return own(writesAllowlist,write.key) && writesAllowlist[write.key].indexOf(value) !== -1;
    }
    function send(command,physicalKey,state) {
      if (!command || !commandsAllowed(state)) return false;
      if (pending) { menu.feedback = 'AGUARDANDO RETORNO DO VEÍCULO'; return false; }
      var writes = command.writes || [], action = command.systemAction;
      if (!writes.every(validWrite) || action && action !== 'CANCEL_MAX_AC' && action !== 'RESET_DRIVE_INFO') return false;
      if (action === 'RESET_DRIVE_INFO' && (physicalKey !== 'ENTER_LONG' || state.cardId !== 1 || menuApi.getView(menu) !== 'trips' || menu.tripIndex !== 1)) return false;
      if (action === 'CANCEL_MAX_AC' && (physicalKey !== 'ENTER_LONG' || state.cardId !== 3 || writes.length !== 1 || writes[0].key !== 'car.hvac.auto_enable')) return false;
      // The real bridge is stable; ThemeLab may inject its external mock after iframe load.
      var bridge = win.Android;
      if (!bridge || writes.length && typeof bridge.updateCarData !== 'function' || action && typeof bridge.triggerSystemAction !== 'function') {
        menu.feedback = 'COMANDO INDISPONÍVEL'; return false;
      }
      var expected = {};
      if (action === 'RESET_DRIVE_INFO') resetKeys.forEach(function (key) { expected[key] = 0; });
      else writes.forEach(function (write) { expected[write.key] = apex.finite(write.value); });
      pending = {expected:expected,receivedByKey:{},reset:action==='RESET_DRIVE_INFO',at:now()};
      menu.feedback = 'AGUARDANDO RETORNO DO VEÍCULO';
      try {
        // MAX AC restores previous values; cancel before the explicit Auto write.
        if (action) bridge.triggerSystemAction(action);
        writes.forEach(function (write) { bridge.updateCarData(write.key,String(write.value)); });
      } catch (error) { pending = null; menu.feedback = 'COMANDO NÃO ENVIADO'; return false; }
      return true;
    }
    function onKeyEvent(key) {
      if (disposed || typeof key !== 'string') return false;
      key = key.toUpperCase();
      if (menuApi.metadata.keys.indexOf(key) === -1) return false;
      var state = adapter.getState(clockText);
      if (!commandsAllowed(state)) return false;
      menu = menuApi.dispatch(menu,{type:'KEY',key:key,now:now()});
      var command = menu.lastCommand;
      if (command) send(command,key,state);
      dirty = true; schedule();
      return true;
    }
    function install(name,handler) { win[name] = handler; ownedGlobals[name] = handler; }
    install('control',function (key,value) { return adapter.update(key,value,true); });
    install('onDataChanged',function (key,value) { return adapter.update(key,value,false); });
    install('onCardChanged',function (card) { return adapter.onCardChanged(card); });
    install('onKeyEvent',onKeyEvent);
    install('focus',function () {});
    install('showScreen',function () {});
    // Visual-only pulses do not own the critical-warning composition state.
    install('updateWarning',function () {});
    install('clearWarnings',function () {});
    function listen(target,name,handler) {
      if (!target || typeof target.addEventListener !== 'function') return;
      target.addEventListener(name,handler);
      removers.push(function () { target.removeEventListener(name,handler); });
    }
    function cleanup() {
      if (disposed) return;
      var ownsSurface = win.cleanup === ownedGlobals.cleanup;
      disposed = true;
      cancelFrame();
      if (clockTimer !== null) win.clearTimeout(clockTimer);
      if (heartbeatTimer !== null) win.clearInterval(heartbeatTimer);
      clockTimer = heartbeatTimer = null;
      removers.forEach(function (remove) { remove(); }); removers = [];
      adapter.cleanup(); pending = null;
      if (pipeline) pipeline.cleanup();
      if (ownsSurface && typeof painter.cleanup === 'function') painter.cleanup();
      if (ownsSurface) { clearCanvas(); if (overlay) overlay.hidden = true; }
      // A delayed cleanup from the previous runtime never overwrites a reload's hooks.
      Object.keys(ownedGlobals).forEach(function (name) { if (win[name] === ownedGlobals[name]) win[name] = function () {}; });
    }
    install('cleanup',cleanup);
    listen(doc,'visibilitychange',function () {
      cancelFrame(); dirty = true; visibility(adapter.getState(clockText)); schedule();
    });
    listen(win,'pagehide',cleanup);
    function updateClock() {
      if (disposed) return;
      var date = new Date();
      var pad = function (value) { return value<10?'0'+value:String(value); };
      clockText = pad(date.getHours())+':'+pad(date.getMinutes());
      dirty = true; schedule();
      clockTimer = win.setTimeout(updateClock,60000-date.getSeconds()*1000-date.getMilliseconds());
    }
    function heartbeat() {
      if (disposed) return;
      var bridge = win.Android;
      if (bridge && typeof bridge.heartbeat === 'function') {
        try { bridge.heartbeat(); } catch (error) { /* No retry loop; next regular pulse is bounded. */ }
      }
      if (pending && now()-pending.at > 8000) {
        pending = null; menu.feedback = 'SEM RETORNO DO VEÍCULO'; dirty = true; schedule();
      }
    }
    // These hooks exist before the first subscription/snapshot call can reenter.
    adapter.start(); updateClock(); heartbeat();
    if (!disposed) heartbeatTimer = win.setInterval(heartbeat,2000);
    if (doc.fonts && doc.fonts.ready && typeof doc.fonts.ready.then === 'function') doc.fonts.ready.then(function () {
      if (!disposed) { dirty = true; schedule(); }
    },function () {});
    visibility(adapter.getState(clockText)); schedule();
    return {cleanup:cleanup,getState:function () { return adapter.getState(clockText); },getMenuState:function () { return menu; },
      getStatus:function () { return {disposed:disposed,pendingFrame:frame!==null,pendingCommand:!!pending,framesRendered:framesRendered,
        producerFrames:framesRendered,uiFrames:uiFrames,uiPaintMs:uiPaintMs,renderMode:pipeline?pipeline.getStatus().mode:'fallback',
        material:pipeline?pipeline.getStatus():null,renderErrors:renderErrors,lastPaintMs:paintCostMs,travel:travel,adapter:adapter.getStatus()}; }};
  }
  return {create:create};
}));
