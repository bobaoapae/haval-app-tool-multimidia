/* Existing v1.0 host surface only. Globals are installed by host-runtime before start(). */
(function (root, factory) {
  'use strict';
  if (typeof module === 'object' && module.exports) module.exports = factory(require('./apex-data'));
  else root.StarlightContractAdapter = factory(root.ApexData);
}(typeof globalThis !== 'undefined' ? globalThis : this, function (apex) {
  'use strict';
  var own = function (object, key) { return Object.prototype.hasOwnProperty.call(object, key); };
  var aliases = {
    carSpeed:'speed', engineRPM:'rpm', odometer:'odometer', gearState:'gear', drivingMode:'driveMode',
    evMode:'propulsionMode', inside_temp:'insideTemp', outside_temp:'outsideTemp', tempUnit:'tempUnit',
    fuelPercent:'fuelPct', batteryPercent:'batteryPct', fuelRange:'fuelRange', batteryRange:'electricRange'
  };
  var enumAliases = {gear:{N:0,D:2,P:3,R:4},driveMode:{NORMAL:0,SPORT:1,ECO:2,NEVE:3,AREIA:4,LAMA:5,AWD:11},propulsionMode:{HEV:0,EVP:1,EV:3}};
  var projectionKeys = ['carPlayInDash','projectionPreparingD3','projectionMirrorInDash','aaClusterInDash','appInDash'];
  var preferenceKeys = ['app.preferences.enableSpeedAdjustment','app.preferences.speedAdjustmentOffset'];

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
    var bridge = options.bridge || null;
    var keys = Object.keys(apex.KEYS).map(function (field) { return apex.KEYS[field]; });
    var snapshot = Object.create(null), canonicalSeen = Object.create(null), projections = {};
    keys.forEach(function (key) { snapshot[key] = null; });
    projectionKeys.forEach(function (key) { projections[key] = false; });
    var disposed = false, started = false, ready = false, cardId = null, clusterEnabled = true, warningActive = false;
    var speedAlias = false, adjustmentEnabled = false, adjustmentOffset = 0, subscribed = [], errors = [];

    function changed(key, value, canonical) {
      if (typeof options.onChange === 'function') options.onChange(key, value, canonical);
    }
    function update(key, value, allowAliases) {
      if (disposed || typeof key !== 'string') return false;
      if (key === 'clusterEnabled' || key === 'warningActive' || projectionKeys.indexOf(key) !== -1) {
        var boolean = booleanValue(value);
        if (boolean === null) return false;
        var old = key === 'clusterEnabled' ? clusterEnabled : key === 'warningActive' ? warningActive : projections[key];
        if (key === 'clusterEnabled') clusterEnabled = boolean;
        else if (key === 'warningActive') warningActive = boolean;
        else projections[key] = boolean;
        canonicalSeen[key] = true;
        if (old !== boolean) changed(key, boolean, true);
        return old !== boolean;
      }
      var preference = preferenceKeys.indexOf(key) !== -1 ? key : allowAliases && (key === 'enableSpeedAdjustment' || key === 'speedAdjustmentOffset') ? 'app.preferences.' + key : null;
      if (preference) {
        var preferenceCanonical = key === preference;
        if (!preferenceCanonical && canonicalSeen[preference]) return false;
        if (preferenceCanonical) canonicalSeen[preference] = true;
        var pref = preference === preferenceKeys[0] ? booleanValue(value) : apex.finite(value);
        if (pref === null) return false;
        var previous = preference === preferenceKeys[0] ? adjustmentEnabled : adjustmentOffset;
        if (preference === preferenceKeys[0]) adjustmentEnabled = pref;
        else adjustmentOffset = pref;
        if (pref !== previous) changed(preference, pref, preferenceCanonical);
        return pref !== previous;
      }
      var canonical = own(snapshot, key), field = allowAliases && own(aliases, key) ? aliases[key] : null;
      if (!canonical && !field) return false;
      var target = canonical ? key : apex.KEYS[field];
      if (canonical) canonicalSeen[target] = true;
      else if (canonicalSeen[target]) return false;
      var next = canonical ? apex.finite(value) : aliasValue(field, value);
      var sourceChanged = target === apex.KEYS.speed && speedAlias !== !canonical;
      if (target === apex.KEYS.speed) speedAlias = !canonical;
      var oldValue = snapshot[target];
      snapshot[target] = next;
      if (oldValue !== next || sourceChanged) changed(target, next, canonical);
      // A duplicate reading can acknowledge a reset without scheduling another paint.
      else if (canonical && typeof options.onCanonical === 'function') options.onCanonical(target,next);
      return oldValue !== next || sourceChanged;
    }
    function onCardChanged(value) {
      if (disposed) return false;
      var next = apex.finite(value);
      if ([0,1,3].indexOf(next) === -1 || next === cardId) return false;
      cardId = next;
      changed('cardId', next, true);
      return true;
    }
    function shownSpeed(raw) {
      if (raw === null) return null;
      if (speedAlias) return raw; // Legacy carSpeed may already be HUD-calibrated.
      var adjusted = (raw * 1.07 - (raw / 180) * 0.02) * (adjustmentEnabled ? 1 + adjustmentOffset / 100 : 1);
      return Number.isFinite(adjusted) ? Math.max(0,Math.floor(adjusted)) : null;
    }
    function getSnapshot() {
      var copy = {};
      keys.forEach(function (key) { copy[key] = snapshot[key]; });
      return copy;
    }
    function getState(clockText) {
      var copy = getSnapshot(), decoded = apex.decode(copy,{tankCapacityL:55,clockText:clockText});
      decoded.rawSpeed = decoded.speed;
      decoded.speed = shownSpeed(decoded.speed);
      return {snapshot:copy,decoded:decoded,cardId:cardId,clusterEnabled:clusterEnabled,warningActive:warningActive,
        projection:projectionKeys.some(function (key) { return projections[key]; }),projections:Object.assign({},projections)};
    }
    function cleanup() {
      if (disposed) return;
      disposed = true;
      var oldKeys = subscribed;
      subscribed = [];
      if (oldKeys.length && bridge && typeof bridge.unsubscribe === 'function') {
        try { bridge.unsubscribe(JSON.stringify(oldKeys)); } catch (error) { errors.push('unsubscribe-failed'); }
      }
    }
    function start() {
      if (disposed) return false;
      if (started) return ready;
      started = true;
      if (!bridge || typeof bridge.getAvailableKeys !== 'function' || typeof bridge.subscribe !== 'function') return false;
      // A preference read may reenter a callback: never overwrite a newer canonical push.
      if (typeof bridge.getPreference === 'function') preferenceKeys.forEach(function (key,index) {
        if (disposed) return;
        try {
          var initial = bridge.getPreference(key.slice('app.preferences.'.length), index === 0 ? 'false' : '0');
          if (!disposed && !canonicalSeen[key]) update(key.slice('app.preferences.'.length), initial, true);
        } catch (error) { errors.push('preference-failed:' + key); }
      });
      if (disposed) return false;
      var available;
      try { available = JSON.parse(bridge.getAvailableKeys()); }
      catch (error) { errors.push('available-keys-failed'); return false; }
      if (disposed || !Array.isArray(available)) return false;
      subscribed = keys.concat(projectionKeys,preferenceKeys,['warningActive']).filter(function (key) { return available.indexOf(key) !== -1; });
      if (subscribed.length) {
        try { bridge.subscribe(JSON.stringify(subscribed)); }
        catch (error) {
          errors.push('subscribe-failed');
          var failed = subscribed; subscribed = [];
          if (!disposed && typeof bridge.unsubscribe === 'function') {
            try { bridge.unsubscribe(JSON.stringify(failed)); } catch (unsubscribeError) { errors.push('unsubscribe-failed'); }
          }
          return false;
        }
        if (disposed) return false;
        if (typeof bridge.getCarData === 'function') subscribed.slice().forEach(function (key) {
          if (disposed || canonicalSeen[key] || preferenceKeys.indexOf(key) !== -1) return;
          try {
            var value = bridge.getCarData(key);
            if (!disposed && !canonicalSeen[key]) update(key, value, false);
          } catch (error) { errors.push('snapshot-failed:' + key); }
        });
      }
      ready = !disposed;
      return ready;
    }
    return {start:start,update:update,onCardChanged:onCardChanged,getState:getState,getSnapshot:getSnapshot,cleanup:cleanup,
      getStatus:function () { return {started:started,ready:ready&&!disposed,disposed:disposed,subscribedKeys:subscribed.slice(),errors:errors.slice()}; }};
  }
  return {create:create,booleanValue:booleanValue,projectionKeys:projectionKeys,preferenceKeys:preferenceKeys};
}));
