/* Local browser host for the simulated Impulse panel. No vehicle bridge, storage,
 * network, subscriptions, or commands leave this page. CommonJS supports Node QA. */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.PreviewController = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  var FONT_NAMES = ['Speed Digits', 'Scale Digits', 'Header Sans', 'Panel Sans', 'Label Sans'];
  var IDS = ['panel','open-main','open-ac','menu-back','menu-close','menu-shell','menu-title',
    'menu-main','menu-settings','menu-info','menu-graphs','menu-trips','menu-ac',
    'tabs-info','tabs-graph','tabs-trip','stats-info','stats-trip','stats-ac',
    'graph-reading','graph-history','graph-range','trip-reset','trip-reset-hint',
    'ac-fields','ac-adjust-label','ac-decrease','ac-increase','ac-auto','ac-recycle',
    'menu-hint','menu-status','key-up','key-down','key-enter','mode','data',
    'toggle','restart','time','clock','motion-note','font-warning'];

  function finite(value, fallback) { var n = Number(value); return Number.isFinite(n) ? n : fallback; }
  function clampTime(value, duration) { return Math.max(0, Math.min(duration, finite(value, 0))); }
  function timeLabel(value, duration) { return value.toFixed(2).replace('.', ',') + ' / ' + duration.toFixed(2).replace('.', ',') + ' s'; }
  function hasAttribute(node, name) { return !!(node && node.hasAttribute && node.hasAttribute(name)); }
  function closest(node, predicate) {
    for (var current = node; current; current = current.parentElement) if (predicate(current)) return current;
    return null;
  }
  function isEditor(node) {
    return !!closest(node, function (item) {
      return /^(INPUT|SELECT|TEXTAREA)$/.test(item.tagName || '') || item.isContentEditable ||
        (hasAttribute(item, 'contenteditable') && item.getAttribute('contenteditable') !== 'false');
    });
  }
  function keyboardAction(event, state, context) {
    if (!event || event.defaultPrevented || event.isComposing || event.ctrlKey || event.metaKey || event.altKey || isEditor(event.target)) return null;
    var target = event.target;
    var isPanel = target === context.panel;
    var row = closest(target, function (node) { return hasAttribute(node, 'data-menu-keyboard'); });
    var inMenu = context.shell && context.shell.contains(target);
    if (event.key === 'Escape') {
      if ((!isPanel && !inMenu) || state.card === 0) return null;
      return state.card === 1 && state.detail ? {type:'KEY',key:'BACK'} : {type:'CLOSE'};
    }
    if (!isPanel && !row) return null;
    if (state.card === 0) return isPanel && event.key === 'Enter' ? {type:'CARD',card:1} : null;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      if (state.card === 3) return {type:'AC_FOCUS',field:state.acFocus === 'fan' ? 'temp' : 'fan'};
      return {type:'KEY',key:event.key === 'ArrowLeft' ? 'UP' : 'DOWN'};
    }
    if (event.key === 'ArrowUp' || event.key === 'ArrowDown') return {type:'KEY',key:event.key === 'ArrowUp' ? 'UP' : 'DOWN'};
    if (event.key === 'Enter') {
      if (event.repeat) return null;
      if (event.shiftKey && (state.card === 3 || (state.card === 1 && state.detail && state.menuIndex === 3))) return {type:'KEY',key:'ENTER_LONG'};
      // Native row buttons own Enter/Space activation; don't cause a second click.
      return isPanel ? {type:'KEY',key:'ENTER'} : null;
    }
    return null;
  }
  function dynamicTelemetry(panelState, unavailable) {
    return unavailable ? {speed:null,voltage:null,current:null} : {
      speed:Number.isFinite(panelState.speed) ? panelState.speed : null,
      voltage:Number.isFinite(panelState.batteryPowerKw) ? 400 : null,
      current:Number.isFinite(panelState.batteryPowerKw) ? panelState.batteryPowerKw * 2.5 : null
    };
  }
  function valueText(item) { return String(item.value) + (item.unit ? ' ' + item.unit : ''); }

  function create(options) {
    options = options || {};
    var win = options.window || (typeof window !== 'undefined' ? window : null);
    var doc = options.document || (win && win.document);
    var motion = options.motion || (win && win.MotionStudy);
    var stateApi = options.stateApi || (win && win.StarlightMenuState);
    var menus = options.menus || (win && win.ThemeMenus);
    var apex = options.apex || (win && win.ApexData);
    if (!win || !doc || !motion || !stateApi || !menus) throw new Error('Arquivos da prévia incompletos');
    var elements = {};
    IDS.forEach(function (id) { elements[id] = doc.getElementById(id); if (!elements[id]) throw new Error('Controle ausente: ' + id); });
    var ctx = elements.panel.getContext('2d');
    if (!ctx) throw new Error('Canvas 2D indisponível');
    var media = win.matchMedia ? win.matchMedia('(prefers-reduced-motion: reduce)') : {matches:false};
    var running = !media.matches, time = 0, last = null, raf = null, disposed = false;
    var listeners = [], dynamicNodes = [], opener = elements['open-main'], currentContent = null;
    var rowCache = {}, statsCache = {}, state;

    function on(node, name, handler) {
      node.addEventListener(name, handler);
      listeners.push(function () { node.removeEventListener(name, handler); });
    }
    function setText(node, value) { var text = String(value == null ? '' : value); if (node.textContent !== text) node.textContent = text; }
    function attr(node, name, value) { node.setAttribute(name, String(value)); }
    function make(tag, className, text) {
      var node = doc.createElement(tag);
      if (className) node.className = className;
      if (text !== undefined) node.textContent = text;
      return node;
    }
    function demoSeed() {
      return apex && typeof apex.demo === 'function' ? apex.demo(time, motion.panelState(time, elements.mode.value)) : stateApi.demoData();
    }
    state = stateApi.createState(elements.data.value === 'unavailable' ? {} : demoSeed());
    motion.setDataProfile(elements.data.value || 'demo');

    function announce(message) { setText(elements['menu-status'], message); }
    function findFocusKey(key) {
      return dynamicNodes.find(function (node) { return node.getAttribute('data-focus-key') === key; });
    }
    function selectedFocusKey() {
      var view = stateApi.getView(state);
      if (view === 'menu') return 'menu-' + state.menuIndex;
      if (view === 'settings') return 'setting-' + state.settingIndex;
      if (view === 'info') return 'info-' + state.infoIndex;
      if (view === 'graphs') return 'graph-' + state.graphIndex;
      if (view === 'trips') return 'trip-' + state.tripIndex;
      if (view === 'ac') return 'ac-' + state.acFocus;
      return null;
    }
    function focusSelected() {
      var node = findFocusKey(selectedFocusKey());
      if (node && !node.disabled) node.focus({preventScroll:true});
    }
    function focusOpener() {
      var target = opener && !opener.disabled ? opener : elements['open-main'];
      target.focus({preventScroll:true});
    }
    function tripResetAvailable() {
      return ['tripBDistance','tripBTime','tripBConsumption','tripBSpeed'].every(function (field) { return state.data[field] !== null; });
    }
    function perform(action, behavior) {
      if (disposed || !action) return;
      behavior = behavior || {};
      if (action.type === 'KEY' && action.key === 'ENTER_LONG' && stateApi.getView(state) === 'trips' && state.tripIndex === 1 && !tripResetAvailable()) {
        announce('Trip B indisponível nesta simulação');
        return;
      }
      var before = state;
      state = stateApi.dispatch(state, action);
      paint();
      if (state.card === 0 && before.card !== 0) focusOpener();
      else if (behavior.focus) focusSelected();
      else if (doc.activeElement && doc.activeElement.disabled && elements['menu-shell'].contains(doc.activeElement)) focusSelected();
      if (behavior.announce !== false) {
        if (state.card === 0) announce('Menus fechados');
        else {
          var selected = (currentContent.rows || []).concat(currentContent.tabs || []).find(function (row) { return row.selected; });
          var message = currentContent.title + (selected ? ': ' + (selected.title || selected.label) + (selected.value ? ', ' + valueText(selected) : '') : '');
          // Include the new value so repeated increases are actually announced;
          // a repeated generic "adjustment simulated" message would stay silent.
          if (state.feedback && (state.feedback !== before.feedback || state.lastCommand !== before.lastCommand)) message += '. ' + state.feedback;
          announce(message);
        }
      }
    }
    function selectionAction(prefix, item) {
      if (prefix === 'menu') return {type:'FOCUS',group:'menu',index:item.index};
      if (prefix === 'setting') return {type:'FOCUS',group:'setting',index:item.index};
      return item.action;
    }
    function rows(hostId, contentRows, prefix) {
      var host = elements[hostId];
      var cache = rowCache[hostId] || (rowCache[hostId] = []);
      contentRows.forEach(function (item, index) {
        var entry = cache[index];
        if (!entry) {
          var button = make('button', 'option'); button.type = 'button';
          var copy = make('span'), title = make('strong'), subtitle = make('small'), value = make('span','value');
          copy.append(title, subtitle); button.append(copy, value); host.append(button);
          entry = cache[index] = {button:button,title:title,subtitle:subtitle,value:value};
          dynamicNodes.push(button);
          attr(button, 'data-menu-keyboard', 'true');
          on(button, 'click', function () {
            if (button.getAttribute('aria-disabled') === 'true') { announce(button._disabledReason || 'Dados indisponíveis nesta simulação'); return; }
            perform(button._action, {focus:prefix === 'menu'});
          });
          on(button, 'focus', function () {
            if (button.getAttribute('aria-current') !== 'true') perform(button._selection, {announce:false});
          });
        }
        var key = prefix + '-' + (prefix === 'ac' ? item.field : item.index);
        attr(entry.button, 'data-focus-key', key);
        attr(entry.button, 'aria-current', !!item.selected);
        var missing = prefix === 'setting' && item.value === '--';
        attr(entry.button, 'aria-disabled', !!item.disabled || missing);
        entry.button._disabledReason = item.reason || (missing ? 'Aguardando dados simulados para ' + item.title.toLowerCase() : '');
        entry.button._action = item.action;
        entry.button._selection = selectionAction(prefix,item);
        setText(entry.title, item.title || item.label);
        setText(entry.subtitle, item.reason || item.secondary || item.subtitle || '');
        setText(entry.value, item.value ? valueText(item) : '›');
      });
    }
    function tabs(group, items) {
      var hostId = 'tabs-' + group, host = elements[hostId];
      var cache = rowCache[hostId] || (rowCache[hostId] = []);
      items.forEach(function (item,index) {
        var button = cache[index];
        if (!button) {
          button = cache[index] = make('button'); button.type = 'button'; host.append(button); dynamicNodes.push(button);
          attr(button, 'data-menu-keyboard', 'true'); attr(button, 'data-focus-key', group + '-' + index);
          on(button, 'click', function () { perform(button._action); });
          on(button, 'focus', function () { if (button.getAttribute('aria-pressed') !== 'true') perform(button._action, {announce:false}); });
        }
        button._action = item.action;
        attr(button, 'aria-pressed', !!item.selected);
        setText(button, item.title || item.label);
      });
    }
    function stats(hostId, items) {
      var signature = JSON.stringify(items.map(function (item) { return [item.id,item.label,item.value,item.unit,item.secondary]; }));
      if (statsCache[hostId] === signature) return;
      statsCache[hostId] = signature;
      var nodes = items.map(function (item) {
        var node = make('div', 'stat'), label = make('dt', '', item.label || item.title), value = make('dd', '', valueText(item));
        node.append(label, value);
        if (item.secondary) value.append(make('small', '', item.secondary));
        return node;
      });
      elements[hostId].replaceChildren.apply(elements[hostId], nodes);
    }
    function syncMenu() {
      var content = currentContent = stateApi.getContent(state), view = content.view;
      elements['menu-shell'].hidden = !content.visible;
      attr(elements['open-main'], 'aria-expanded', state.card === 1);
      attr(elements['open-ac'], 'aria-expanded', state.card === 3);
      elements['menu-close'].disabled = state.card === 0;
      elements['menu-back'].disabled = !(state.card === 1 && state.detail);
      setText(elements['menu-title'], content.title);
      setText(elements['menu-hint'], [content.hint,content.footnote].filter(Boolean).join(' · '));
      ['main','settings','info','graphs','trips','ac'].forEach(function (name) { elements['menu-' + name].hidden = view !== (name === 'main' ? 'menu' : name); });
      if (view === 'menu') rows('menu-main',content.rows,'menu');
      if (view === 'settings') rows('menu-settings',content.rows,'setting');
      if (view === 'info') { tabs('info',content.tabs); stats('stats-info',content.stats); }
      if (view === 'graphs') {
        tabs('graph',content.tabs);
        setText(elements['graph-reading'], content.chart.formatted + ' ' + content.chart.unit);
        setText(elements['graph-range'], 'Escala desta prévia: ' + content.chart.rangeLabel + '. Histórico de até 18 amostras; os valores numéricos não são limitados pela escala.');
        setText(elements['graph-history'], content.chart.samples.length ? 'Histórico simulado, do mais antigo ao mais recente: ' + content.chart.samples.map(function (value) { return value.toLocaleString('pt-BR', {maximumFractionDigits:1}); }).join(' · ') + ' ' + content.chart.unit : 'Sem amostras disponíveis');
      }
      if (view === 'trips') {
        tabs('trip',content.tabs); stats('stats-trip',[content.primary].concat(content.stats));
        setText(elements['trip-reset-hint'], content.footnote);
        elements['trip-reset'].hidden = !content.resetAction;
        elements['trip-reset'].disabled = !tripResetAvailable();
      }
      if (view === 'ac') {
        rows('ac-fields',content.rows,'ac'); stats('stats-ac',content.stats);
        var temp = state.acFocus === 'temp', known = temp ? state.data.acTemp !== null : state.data.acFan !== null && state.data.acPower !== null;
        var raw = temp ? state.data.acTemp : state.data.acPower === 0 ? 0 : state.data.acFan;
        setText(elements['ac-adjust-label'], temp ? 'Ajustar temperatura' : 'Ajustar ventilação');
        elements['ac-decrease'].disabled = !known || raw <= (temp ? 16 : 0);
        elements['ac-increase'].disabled = !known || raw >= (temp ? 32 : 7);
        elements['ac-auto'].disabled = state.data.acAuto === null;
        elements['ac-recycle'].disabled = state.data.acRecycle === null;
        attr(elements['ac-auto'], 'aria-pressed', state.data.acAuto === 1);
        attr(elements['ac-recycle'], 'aria-pressed', state.data.acRecycle === 1);
      }
    }
    function paint() {
      if (disposed) return;
      var mode = elements.mode.value, duration = motion.duration(mode);
      time = clampTime(time,duration);
      var sampled = motion.panelState(time,mode);
      state = stateApi.dispatch(state,{type:'TELEMETRY',values:dynamicTelemetry(sampled,elements.data.value === 'unavailable'),now:time*1000});
      var panelState = motion.renderPanel(ctx,time,mode,{menuState:state});
      menus.render(ctx,state,panelState);
      elements.time.max = String(duration); elements.time.value = String(time);
      attr(elements.time, 'aria-valuetext', time.toFixed(2).replace('.',',') + ' segundos de ' + duration.toFixed(2).replace('.',',') + ' segundos');
      setText(elements.clock,timeLabel(time,duration));
      setText(elements.toggle,running ? 'Pausar' : 'Reproduzir');
      attr(elements.toggle,'aria-label',running ? 'Pausar animação' : 'Reproduzir animação');
      setText(elements['motion-note'], media.matches ? 'Movimento reduzido detectado: reprodução começa pausada. Use Reproduzir se quiser animar.' : 'Arrastar a linha do tempo pausa a animação. Reiniciar conserva o estado de reprodução e os ajustes simulados.');
      syncMenu();
    }
    function cancelFrame() { if (raf !== null) win.cancelAnimationFrame(raf); raf = null; last = null; }
    function scheduleFrame() { if (!disposed && running && !doc.hidden && raf === null) raf = win.requestAnimationFrame(tick); }
    function tick(now) {
      raf = null;
      if (disposed || !running || doc.hidden) { last = null; return; }
      if (last !== null) time = Math.min(motion.duration(elements.mode.value),time+Math.max(0,(now-last)/1000));
      last = now;
      if (time >= motion.duration(elements.mode.value)) running = false;
      paint(); scheduleFrame();
    }
    function resetHistory() { state = stateApi.dispatch(state,{type:'RESET_HISTORY'}); }
    function seek(value) {
      var next = clampTime(value,motion.duration(elements.mode.value));
      if (next < time) resetHistory();
      time = next; running = false; cancelFrame(); paint();
    }
    function open(card, button) {
      opener = button || (card === 3 ? elements['open-ac'] : elements['open-main']);
      perform({type:'CARD',card:card},{focus:true});
    }

    on(elements['open-main'],'click',function () { open(1,elements['open-main']); });
    on(elements['open-ac'],'click',function () { open(3,elements['open-ac']); });
    on(elements['menu-back'],'click',function () { perform({type:'KEY',key:'BACK'},{focus:true}); });
    on(elements['menu-close'],'click',function () { perform({type:'CLOSE'}); });
    [['key-up','UP'],['key-down','DOWN'],['key-enter','ENTER'],['ac-decrease','DOWN'],['ac-increase','UP'],['ac-auto','ENTER_LONG'],['ac-recycle','BACK_LONG'],['trip-reset','ENTER_LONG']].forEach(function (entry) {
      on(elements[entry[0]],'click',function () { perform({type:'KEY',key:entry[1]},{focus:entry[0] === 'key-enter'}); });
    });
    on(doc,'keydown',function (event) {
      var action = keyboardAction(event,state,{panel:elements.panel,shell:elements['menu-shell']});
      if (!action) return;
      event.preventDefault();
      if (state.card === 0 && action.type === 'CARD') opener = elements['open-main'];
      perform(action,{focus:event.target !== elements.panel});
    });
    on(elements.panel,'click',function (event) {
      if (typeof menus.hitTest !== 'function') return;
      var bounds = elements.panel.getBoundingClientRect();
      if (bounds.width <= 0 || bounds.height <= 0) return;
      var action = menus.hitTest(state,(event.clientX-bounds.left)*elements.panel.width/bounds.width,(event.clientY-bounds.top)*elements.panel.height/bounds.height);
      if (action) { elements.panel.focus({preventScroll:true}); perform(action); }
    });
    on(elements.toggle,'click',function () {
      if (time >= motion.duration(elements.mode.value)) { time = 0; resetHistory(); running = true; }
      else running = !running;
      cancelFrame(); paint(); scheduleFrame();
    });
    on(elements.restart,'click',function () { time = 0; resetHistory(); cancelFrame(); paint(); scheduleFrame(); });
    on(elements.time,'input',function () { seek(elements.time.value); });
    on(elements.mode,'change',function () { time = 0; resetHistory(); cancelFrame(); paint(); scheduleFrame(); });
    on(elements.data,'change',function () {
      motion.setDataProfile(elements.data.value);
      state = stateApi.dispatch(state,{type:'RESET_DATA',values:elements.data.value === 'unavailable' ? {} : demoSeed()});
      last = null; paint();
      announce(elements.data.value === 'unavailable' ? 'Dados indisponíveis. Ajustes da simulação bloqueados.' : 'Dados simulados restaurados');
    });
    on(doc,'visibilitychange',function () { cancelFrame(); scheduleFrame(); });
    function motionChange() { if (media.matches) { running = false; cancelFrame(); } paint(); }
    if (media.addEventListener) on(media,'change',motionChange);
    else if (media.addListener) { media.addListener(motionChange); listeners.push(function () { media.removeListener(motionChange); }); }
    paint(); scheduleFrame();
    if (doc.fonts && doc.fonts.load) {
      Promise.all(FONT_NAMES.map(function (name) { return doc.fonts.load('20px "'+name+'"'); })).then(function () { return doc.fonts.ready; }).then(function () { if (!disposed) paint(); }).catch(function () {
        if (disposed) return;
        elements['font-warning'].hidden = false;
        setText(elements['font-warning'],'Alguma fonte local não carregou; a prévia continua com fontes de reserva. Confira se a pasta assets acompanha index.html.');
        paint();
      });
    }
    return Object.freeze({
      paint:paint,openMain:function () { open(1); },openAC:function () { open(3); },
      close:function () { perform({type:'CLOSE'}); },dispatch:perform,seek:seek,
      getState:function () { return state; },
      getPlayback:function () { return {time:time,running:running,mode:elements.mode.value,dataProfile:elements.data.value}; },
      destroy:function () { if (disposed) return; disposed = true; cancelFrame(); listeners.forEach(function (remove) { remove(); }); }
    });
  }
  return Object.freeze({create:create,keyboardAction:keyboardAction,isEditor:isEditor,clampTime:clampTime,timeLabel:timeLabel,dynamicTelemetry:dynamicTelemetry,requiredIds:IDS.slice()});
}));
