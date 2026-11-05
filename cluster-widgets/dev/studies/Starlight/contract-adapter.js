/* DEVELOPMENT ONLY. Explicit synthetic contract fixture; never a vehicle adapter.
 * Nothing auto-starts or installs host globals. A real Android property, including
 * an inherited/accessor/undefined one, blocks use without reading that property.
 * Load apex-data.js first, provide the named mock and call start() explicitly. */
(function (root, factory) {
  'use strict';
  if (typeof module === 'object' && module.exports) module.exports = factory(root, require('./apex-data'));
  else root.StarlightContractAdapter = factory(root, root.ApexData);
}(typeof globalThis !== 'undefined' ? globalThis : this, function (root, apex) {
  'use strict';

  var MOCK_SENTINEL = 'StarlightReadOnly';
  var own = function (object, key) { return Object.prototype.hasOwnProperty.call(object, key); };
  var aliases = {
    carSpeed: 'speed', engineRPM: 'rpm', odometer: 'odometer', gearState: 'gear',
    drivingMode: 'driveMode', evMode: 'propulsionMode', inside_temp: 'insideTemp',
    outside_temp: 'outsideTemp', tempUnit: 'tempUnit', fuelPercent: 'fuelPct',
    batteryPercent: 'batteryPct', fuelRange: 'fuelRange', batteryRange: 'electricRange'
  };
  var enumAliases = {
    gear: { N: 0, D: 2, P: 3, R: 4 },
    driveMode: { NORMAL: 0, SPORT: 1, ECO: 2, NEVE: 3, AREIA: 4, LAMA: 5, AWD: 11 },
    propulsionMode: { HEV: 0, EVP: 1, EV: 3 }
  };
  var wheelKeys = ['UP', 'DOWN', 'ENTER', 'BACK', 'HOME', 'ENTER_LONG', 'BACK_LONG'];

  function hasAndroid(object) {
    return object !== null && (typeof object === 'object' || typeof object === 'function') && 'Android' in object;
  }
  function frozen(value) {
    Object.keys(value).forEach(function (key) {
      if (value[key] && typeof value[key] === 'object') frozen(value[key]);
    });
    return Object.freeze(value);
  }
  function booleanValue(value) {
    if (value === true || value === 1 || value === '1' || value === 'true') return true;
    if (value === false || value === 0 || value === '0' || value === 'false') return false;
    return null;
  }
  function aliasValue(field, value) {
    if (typeof value === 'string') {
      var label = value.trim().toUpperCase();
      if (enumAliases[field] && own(enumAliases[field], label)) return enumAliases[field][label];
      if (field === 'propulsionMode' && /^HEV (?:INTELIGENTE|PRIORIDADE \d+%)$/.test(label)) return 0;
      if (field === 'tempUnit' && (label === '°C' || label === '°F')) return label === '°F' ? 1 : 0;
    }
    return apex.finite(value);
  }

  function create(options) {
    options = options || {};
    var win = options.window || root;
    var doc = options.document || (win && win.document);
    var bridge = options.bridge; // Deliberately never fall back to window.Android.
    if (options.labMode !== true) throw new Error('Starlight contract adapter requires explicit labMode: true');
    if (hasAndroid(root) || hasAndroid(win)) throw new Error('Starlight contract lab is disabled in an Android host');
    if (!bridge || !own(bridge, '__themeLabMock') || bridge.__themeLabMock !== MOCK_SENTINEL) {
      throw new Error('Starlight contract adapter requires the explicit StarlightReadOnly mock sentinel');
    }
    if (!apex || !apex.KEYS || typeof apex.decode !== 'function' || typeof apex.finite !== 'function') throw new Error('Load apex-data.js before the contract adapter');
    ['getAvailableKeys', 'subscribe', 'getCarData', 'unsubscribe'].forEach(function (name) {
      if (typeof bridge[name] !== 'function') throw new Error('Missing read-only mock method: ' + name);
    });
    if (!win || typeof win.requestAnimationFrame !== 'function' || typeof win.cancelAnimationFrame !== 'function') throw new Error('A lab animation-frame scheduler is required');
    if (typeof options.onSnapshot !== 'function') throw new Error('An onSnapshot callback is required');

    var keys = Object.keys(apex.KEYS).map(function (field) { return apex.KEYS[field]; });
    var snapshot = Object.create(null), canonicalSeen = Object.create(null);
    keys.forEach(function (key) { snapshot[key] = null; });
    var decodeOptions = {
      tankCapacityL: options.decodeOptions && options.decodeOptions.tankCapacityL,
      clockText: options.decodeOptions && options.decodeOptions.clockText
    };
    var started = false, ready = false, disposed = false, dirty = true, manualHidden = false;
    var clusterEnabled = true, cardId = null, frame = null, epoch = 0;
    var subscribed = [], removers = [], errors = [];

    function hidden() { return manualHidden || !!(doc && doc.hidden); }
    function allowed() {
      return !hasAndroid(root) && !hasAndroid(win) && own(bridge, '__themeLabMock') && bridge.__themeLabMock === MOCK_SENTINEL;
    }
    function cancelFrame() {
      epoch += 1; // A stale callback stays inert even if a scheduler ignores cancel.
      if (frame !== null) win.cancelAnimationFrame(frame);
      frame = null;
    }
    function cleanup(unsubscribe) {
      if (disposed) return;
      disposed = true;
      cancelFrame();
      removers.forEach(function (remove) { remove(); });
      removers = [];
      var oldKeys = subscribed;
      subscribed = [];
      if (unsubscribe !== false && oldKeys.length && allowed()) {
        try { bridge.unsubscribe(JSON.stringify(oldKeys)); }
        catch (error) { errors.push('unsubscribe-failed'); }
      }
    }
    function usable() {
      if (disposed) return false;
      if (!allowed()) { cleanup(false); return false; }
      return true;
    }
    function getSnapshot() {
      var copy = {};
      keys.forEach(function (key) { copy[key] = snapshot[key]; });
      return Object.freeze(copy);
    }
    function getState() {
      var copy = getSnapshot();
      return frozen({ snapshot: copy, decoded: apex.decode(copy, decodeOptions), cardId: cardId,
        clusterEnabled: clusterEnabled, hidden: hidden() });
    }
    function schedule() {
      if (!usable() || !started || hidden() || !dirty || frame !== null) return;
      var scheduledEpoch = epoch;
      frame = win.requestAnimationFrame(function () {
        if (scheduledEpoch !== epoch) return;
        frame = null;
        if (!usable() || hidden()) return;
        dirty = false;
        options.onSnapshot(getState());
      });
    }
    function changed() { dirty = true; schedule(); }
    function update(key, value, allowAliases) {
      if (!usable() || typeof key !== 'string') return false;
      if (key === 'clusterEnabled') {
        var enabled = booleanValue(value);
        if (enabled === null || enabled === clusterEnabled) return false;
        clusterEnabled = enabled;
        changed();
        return true;
      }
      var canonical = own(snapshot, key);
      var field = allowAliases && own(aliases, key) ? aliases[key] : null;
      if (!canonical && !field) return false;
      var target = canonical ? key : apex.KEYS[field];
      if (canonical) canonicalSeen[target] = true; // Missing canonical data also owns the field.
      else if (canonicalSeen[target]) return false;
      var next = canonical ? apex.finite(value) : aliasValue(field, value);
      if (snapshot[target] === next) return false;
      snapshot[target] = next;
      changed();
      return true;
    }
    function onCardChanged(value) {
      if (!usable()) return false;
      var next = apex.finite(value);
      if ([0, 1, 3].indexOf(next) === -1 || cardId === next) return false;
      cardId = next; // Only this hook owns card state. Never snapshot or alias it.
      changed();
      return true;
    }
    function onKeyEvent(key) {
      if (!usable() || !started || hidden() || !clusterEnabled || (cardId !== 1 && cardId !== 3) || wheelKeys.indexOf(key) === -1) return false;
      if (typeof options.onKeyEvent === 'function') options.onKeyEvent(key, frozen({ cardId: cardId }));
      return true; // Read-only notification only; no command or card mutation.
    }
    function setHidden(value) {
      if (!usable() || typeof value !== 'boolean' || manualHidden === value) return;
      manualHidden = value;
      visibilityChanged();
    }
    function visibilityChanged() {
      if (!usable()) return;
      cancelFrame();
      dirty = true;
      schedule();
    }
    function listen(target, name, handler) {
      if (!target || typeof target.addEventListener !== 'function' || typeof target.removeEventListener !== 'function') return;
      target.addEventListener(name, handler);
      removers.push(function () { target.removeEventListener(name, handler); });
    }
    function start() {
      if (!usable()) return false;
      if (started) return ready;
      started = true;
      listen(doc, 'visibilitychange', visibilityChanged);
      listen(win, 'pagehide', hooks.cleanup);
      var available;
      try { available = JSON.parse(bridge.getAvailableKeys()); }
      catch (error) { errors.push('available-keys-failed'); schedule(); return false; }
      if (!usable()) return false;
      if (!Array.isArray(available)) { errors.push('available-keys-invalid'); schedule(); return false; }
      subscribed = keys.filter(function (key) { return available.indexOf(key) !== -1; });
      if (subscribed.length) {
        try { bridge.subscribe(JSON.stringify(subscribed)); }
        catch (error) {
          errors.push('subscribe-failed');
          // A throwing mock may already have registered part of the request.
          var failedKeys = subscribed;
          subscribed = [];
          if (usable()) {
            try { bridge.unsubscribe(JSON.stringify(failedKeys)); }
            catch (unsubscribeError) { errors.push('unsubscribe-failed'); }
          }
          schedule();
          return false;
        }
        if (!usable()) return false;
        subscribed.slice().forEach(function (key) {
          if (!usable() || canonicalSeen[key]) return;
          try {
            var value = bridge.getCarData(key);
            // A reentrant push while getCarData is returning is newer too.
            if (usable() && !canonicalSeen[key]) update(key, value, false);
          } catch (error) { errors.push('snapshot-failed:' + key); }
        });
      }
      schedule();
      ready = usable();
      return ready;
    }
    var hooks = Object.freeze({
      onDataChanged: function (key, value) { return update(key, value, false); },
      control: function (key, value) { return update(key, value, true); },
      onCardChanged: onCardChanged,
      onKeyEvent: onKeyEvent,
      cleanup: function () { cleanup(true); }
    });
    return Object.freeze({
      start: start, hooks: hooks, setHidden: setHidden, getSnapshot: getSnapshot,
      getState: getState, cleanup: hooks.cleanup,
      getStatus: function () { return frozen({ started: started, ready: ready && !disposed, disposed: disposed,
        subscribedKeys: subscribed.slice(), errors: errors.slice(), pendingFrame: frame !== null }); }
    });
  }

  return Object.freeze({ create: create, MOCK_SENTINEL: MOCK_SENTINEL });
}));
