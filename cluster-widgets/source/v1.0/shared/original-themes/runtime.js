/* Original Impulse collection. Read-only v1.0 consumer: no CAN writes or projection actions. */
(function () {
    'use strict';
    var root = document.getElementById('impulse');
    if (!root) return;
    var disposed = false, frame = null, clockTimer = null;
    var subscribed = [], received = Object.create(null), canonical = Object.create(null);
    var card = 1, pageIndex = 0, pages = ['summary', 'trip', 'navigation'];
    var state = Object.create(null), enabledAdjustment = false, speedOffset = 0;
    var keys = {
        'car.basic.vehicle_speed': 'speed', 'car.basic.engine_speed': 'rpm',
        'car.basic.gear_status': 'gear', 'car.drive_setting.drive_mode': 'driveMode',
        'car.ev_setting.power_model_config': 'propulsion', 'car.basic.total_odometer': 'odometer',
        'car.basic.remain_fuel_percentage': 'fuel', 'car.ev_info.cur_battery_power_percentage': 'battery',
        'car.ev_info.fuel_mode_remain_odometer': 'fuelRange', 'car.ev_info.electric_mode_remain_odometer': 'batteryRange',
        'car.basic.cur_journey_odometer': 'trip', 'car.basic.cur_journey_avg_fuel_consume': 'consumption',
        'car.ev_info.power_battery_voltage': 'voltage', 'car.ev_info.cur_charge_current': 'current',
        'car.basic.outside_temp': 'outsideTemp', 'car.basic.inside_temp': 'insideTemp',
        'car.configure.default_temp_unit': 'tempUnit', 'car.hvac.driver_temperature': 'climateLeft',
        'car.hvac.fan_speed': 'climateFan', 'car.hvac.power_mode': 'climatePower',
        'app.navigation.directions': 'navigation'
    };
    var aliases = {
        carSpeed: 'speed', engineRPM: 'rpm', gearState: 'gear', drivingMode: 'driveMode', evMode: 'propulsion',
        odometer: 'odometer', fuelPercent: 'fuel', batteryPercent: 'battery', fuelRange: 'fuelRange',
        batteryRange: 'batteryRange', outside_temp: 'outsideTemp', inside_temp: 'insideTemp', tempUnit: 'tempUnit',
        temp: 'climateLeft', fan: 'climateFan', power: 'climatePower'
    };
    var enums = {
        gear: { 0: 'N', 1: 'N', 2: 'D', 3: 'P', 4: 'R' },
        driveMode: { 0: 'NORMAL', 1: 'SPORT', 2: 'ECO', 3: 'NEVE', 4: 'AREIA', 5: 'LAMA', 11: 'AWD' },
        propulsion: { 0: 'HEV', 1: 'EVP', 3: 'EV' }
    };
    var preferenceKeys = ['app.preferences.enableSpeedAdjustment', 'app.preferences.speedAdjustmentOffset'];
    var textNodes = {}, fillNodes = {}, ringNodes = {};
    function collect(selector, attribute, map) {
        Array.prototype.forEach.call(root.querySelectorAll(selector), function (node) {
            var name = node.getAttribute(attribute);
            if (!map[name]) map[name] = [];
            map[name].push(node);
        });
    }
    collect('[data-value]', 'data-value', textNodes);
    collect('[data-fill]', 'data-fill', fillNodes);
    collect('[data-ring]', 'data-ring', ringNodes);
    function number(value) {
        if (typeof value !== 'number' && typeof value !== 'string') return null;
        if (typeof value === 'string' && !/^[+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?$/i.test(value.trim())) return null;
        var n = Number(value);
        return isFinite(n) ? n : null;
    }
    function bool(value) {
        if (value === true || value === 1 || value === 'true' || value === '1') return true;
        if (value === false || value === 0 || value === 'false' || value === '0') return false;
        return null;
    }
    function label(value, labels) {
        var n = number(value), token = typeof value === 'string' ? value.trim().toUpperCase() : '';
        if (n !== null && Object.prototype.hasOwnProperty.call(labels, String(n))) return labels[n];
        return Object.keys(labels).some(function (key) { return labels[key] === token; }) ? token : null;
    }
    function format(value, decimals) {
        if (value == null || !isFinite(value)) return '--';
        var result = Number(value).toFixed(decimals || 0);
        return Number(result) === 0 ? (0).toFixed(decimals || 0) : result;
    }
    function text(name, value) {
        (textNodes[name] || []).forEach(function (node) {
            var next = String(value);
            if (node.textContent !== next) node.textContent = next;
        });
    }
    function safeText(value, limit) {
        return typeof value === 'string' ? value.replace(/[\u0000-\u001f]/g, ' ').trim().slice(0, limit) : '';
    }
    function navigation(value) {
        var data;
        try { data = typeof value === 'string' ? JSON.parse(value) : value; } catch (error) { return null; }
        if (!data || typeof data !== 'object' || Array.isArray(data) || data.active === false) return null;
        var street = safeText(data.street, 180), distance = safeText(data.distance, 40), turn = safeText(data.turn, 60);
        if (!street && !distance && !turn) return null;
        return { street: street, distance: distance, turn: turn };
    }
    function turnLabel(turn) {
        var labels = { TURN_RIGHT: 'VIRE À DIREITA', TURN_LEFT: 'VIRE À ESQUERDA', STRAIGHT: 'SIGA EM FRENTE',
            SLIGHT_RIGHT: 'MANTENHA À DIREITA', SLIGHT_LEFT: 'MANTENHA À ESQUERDA',
            U_TURN_LEFT: 'RETORNO À ESQUERDA', U_TURN_RIGHT: 'RETORNO À DIREITA', U_TURN: 'RETORNO',
            DESTINATION: 'DESTINO', ARRIVE: 'DESTINO', NAME_CHANGE: 'CONTINUE', ROUNDABOUT: 'ROTATÓRIA' };
        if (labels[turn]) return labels[turn];
        // Never invent a direction for a token unknown to this read-only theme.
        if (/^ROUNDABOUT/.test(turn)) return 'ROTATÓRIA';
        return 'SIGA O NAVEGADOR';
    }
    function bar(name, percentage) {
        var valid = percentage != null && isFinite(percentage);
        var amount = valid ? Math.max(0, Math.min(100, percentage)) : 0;
        (fillNodes[name] || []).forEach(function (node) {
            var transform = 'scaleX(' + amount / 100 + ')';
            if (node.style.transform !== transform) node.style.transform = transform;
            node.setAttribute('data-available', valid ? 'true' : 'false');
        });
        (ringNodes[name] || []).forEach(function (node) {
            // Absolute SVG lengths work on the older Android 9 WebViews too.
            node.removeAttribute('pathLength');
            var length = node.getTotalLength(), dash = length * amount / 100 + ' ' + length;
            if (node.getAttribute('stroke-dasharray') !== dash) node.setAttribute('stroke-dasharray', dash);
            node.style.strokeDasharray = dash;
            node.setAttribute('data-available', valid ? 'true' : 'false');
        });
    }
    function render() {
        frame = null;
        if (disposed) return;
        var rawSpeed = state.speed == null ? null : state.speed;
        // Host friendly carSpeed already includes calibration; only canonical CAN data is raw.
        var speed = rawSpeed === null ? null : canonical.speed ? Number(window.ImpulseContract.getAdjustedSpeed(rawSpeed, enabledAdjustment, speedOffset)) : rawSpeed;
        var power = state.voltage == null || state.current == null ? null : state.voltage * state.current / 1000;
        if (power !== null && !isFinite(power)) power = null;
        if (speed !== null && (!isFinite(speed) || speed < 0 || speed > 999)) speed = null;
        var range = state.fuelRange == null || state.batteryRange == null ? null : state.fuelRange + state.batteryRange;
        var temperatureUnit = state.tempUnit === 1 ? '°F' : '°C';
        ['fuel','battery','fuelRange','batteryRange','rpm','odometer'].forEach(function (key) { text(key, format(state[key])); });
        ['gear','driveMode','propulsion'].forEach(function (key) { text(key, state[key] || '--'); });
        ['outsideTemp','insideTemp'].forEach(function (key) {
            text(key, state[key] == null ? '--' : format(state[key], state[key] % 1 ? 1 : 0) + temperatureUnit);
        });
        text('climateLeft', state.climateLeft == null ? '--' : state.climateLeft <= 16 ? 'LO' : state.climateLeft >= 32 ? 'HI' : format(state.climateLeft, state.climateLeft % 1 ? 1 : 0) + temperatureUnit);
        text('speed', format(speed)); text('power', format(power)); text('totalRange', format(range));
        text('trip', format(state.trip, 1)); text('consumption', format(state.consumption, 1));
        text('climateFan', state.climatePower === 0 ? 'OFF' : format(state.climateFan));
        text('connection', speed === null ? 'SEM LEITURA DE VELOCIDADE' : 'HAVAL IMPULSE');
        text('powerLabel', power === null ? 'POTÊNCIA ELÉTRICA' : power < 0 ? 'REGENERAÇÃO' : 'POTÊNCIA ELÉTRICA');
        var nav = state.navigation;
        text('navStreet', nav ? nav.street || 'Rota em andamento' : 'Sem orientação ativa');
        text('navDistance', nav ? nav.distance || '--' : '--');
        text('navTurn', nav ? turnLabel(nav.turn) : 'AGUARDANDO NAVEGADOR');
        root.setAttribute('data-navigation', nav ? 'active' : 'inactive');
        root.setAttribute('data-power', power === null ? 'unknown' : power < 0 ? 'regen' : 'traction');
        bar('fuel', state.fuel); bar('battery', state.battery);
        bar('speed', speed === null ? null : speed / 220 * 100);
        bar('power', power === null ? null : Math.abs(power) / 250 * 100);
        var pageName = card === 3 ? 'climate' : pages[pageIndex];
        Array.prototype.forEach.call(root.querySelectorAll('[data-page]'), function (node) {
            node.hidden = card !== 1 && card !== 3 || node.getAttribute('data-page') !== pageName;
        });
        text('pageLabel', { summary: 'VISÃO GERAL', trip: 'VIAGEM', navigation: 'NAVEGAÇÃO', climate: 'CLIMATIZAÇÃO · LEITURA' }[pageName]);
        root.setAttribute('data-page-active', pageName);
    }
    function schedule() {
        if (!disposed && frame === null) frame = window.requestAnimationFrame(render);
    }
    function update(key, value, allowAliases) {
        if (disposed) return;
        if (key === 'clusterEnabled') {
            document.documentElement.classList.toggle('cluster-disabled', bool(value) === false);
            return;
        }
        if (preferenceKeys.indexOf(key) !== -1 || allowAliases && (key === 'enableSpeedAdjustment' || key === 'speedAdjustmentOffset')) {
            if (key.indexOf('enableSpeedAdjustment') !== -1) {
                var flag = bool(value); if (flag !== null) enabledAdjustment = flag;
            } else { var offset = number(value); if (offset !== null) speedOffset = offset; }
            schedule(); return;
        }
        if (['carPlayInDash','projectionMirrorInDash','aaClusterInDash','projectionPreparingD3'].indexOf(key) !== -1) {
            state[key] = bool(value) === true;
            root.setAttribute('data-projection', state.carPlayInDash || state.projectionMirrorInDash || state.aaClusterInDash || state.projectionPreparingD3 ? 'active' : 'inactive');
            return;
        }
        var field = keys[key] || (allowAliases ? aliases[key] : null);
        if (!field) return;
        var sourceChanged = Boolean(keys[key]) && !canonical[field];
        if (keys[key]) { received[key] = true; canonical[field] = true; }
        else if (canonical[field]) return;
        if (field === 'navigation') value = navigation(value);
        else if (enums[field]) value = label(value, enums[field]);
        else {
            if (field === 'tempUnit' && value === '°F') value = 1;
            if (field === 'tempUnit' && value === '°C') value = 0;
            value = number(value);
            if (['outsideTemp','insideTemp','climateLeft'].indexOf(field) !== -1) {
                if (value === -1 || value === 255 || value < -60 || value > 160) value = null;
            } else if (field !== 'current' && value !== null && value < 0) value = null;
            if ((field === 'battery' || field === 'fuel') && value > 100) value = null;
            if (field === 'tempUnit' && value !== 0 && value !== 1) value = null;
            if (field === 'climateFan' && (value > 7 || value !== null && Math.floor(value) !== value)) value = null;
        }
        if (state[field] === value && !sourceChanged) return;
        state[field] = value; schedule();
    }
    function onKey(key) {
        if (disposed || card !== 1) return;
        if (key === 'DOWN' || key === 'ENTER') pageIndex = (pageIndex + 1) % pages.length;
        else if (key === 'UP') pageIndex = (pageIndex + pages.length - 1) % pages.length;
        else if (key === 'BACK' || key === 'HOME') pageIndex = 0;
        else return;
        schedule();
    }
    function keyboard(event) {
        // Development convenience only. A real host owns steering keys.
        if (window.Android || event.repeat || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
        var key = { ArrowUp: 'UP', ArrowDown: 'DOWN', Enter: 'ENTER', Escape: 'BACK', Backspace: 'BACK', Home: 'HOME' }[event.key];
        if (key) { event.preventDefault(); onKey(key); }
    }
    window.onDataChanged = function (key, value) { update(key, value, false); };
    window.onCarDataChanged = window.onDataChanged;
    window.control = function (key, value) { update(key, value, true); };
    window.onCardChanged = function (value) {
        if (disposed) return;
        var next = number(value);
        if (next === null || Math.floor(next) !== next || next < 0) return;
        card = next; pageIndex = 0; root.setAttribute('data-card', String(card)); schedule();
    };
    window.onKeyEvent = onKey;
    // Compatibility hooks must never change the vehicle-owned card.
    window.showScreen = function () {};
    window.focus = function () {};
    window.cleanup = function () {
        if (disposed) return;
        disposed = true;
        if (frame !== null) window.cancelAnimationFrame(frame);
        if (clockTimer !== null) window.clearTimeout(clockTimer);
        window.removeEventListener('keydown', keyboard);
        window.removeEventListener('pagehide', window.cleanup);
        if (subscribed.length && window.Android && typeof window.Android.unsubscribe === 'function') {
            try { window.Android.unsubscribe(JSON.stringify(subscribed)); } catch (error) {}
        }
        subscribed = []; frame = clockTimer = null;
    };
    function clock() {
        if (disposed) return;
        var now = new Date();
        text('clock', ('0' + now.getHours()).slice(-2) + ':' + ('0' + now.getMinutes()).slice(-2));
        clockTimer = window.setTimeout(clock, 60000 - now.getSeconds() * 1000 - now.getMilliseconds());
    }
    function connect() {
        var bridge = window.Android;
        if (!bridge) return;
        if (typeof bridge.getPreference === 'function') {
            try { update('app.preferences.enableSpeedAdjustment', bridge.getPreference('enableSpeedAdjustment', 'false'), false); } catch (error) {}
            try { update('app.preferences.speedAdjustmentOffset', bridge.getPreference('speedAdjustmentOffset', '0'), false); } catch (error) {}
        }
        if (typeof bridge.subscribe !== 'function' || typeof bridge.getAvailableKeys !== 'function') return;
        var available;
        try { available = JSON.parse(bridge.getAvailableKeys()); } catch (error) { return; }
        if (!Array.isArray(available)) return;
        subscribed = Object.keys(keys).filter(function (key) { return available.indexOf(key) !== -1; }).concat(preferenceKeys);
        try { bridge.subscribe(JSON.stringify(subscribed)); } catch (error) { subscribed = []; return; }
        subscribed.forEach(function (key) {
            if (received[key] || preferenceKeys.indexOf(key) !== -1 || typeof bridge.getCarData !== 'function') return;
            try { update(key, bridge.getCarData(key), false); } catch (error) {}
        });
    }
    window.addEventListener('keydown', keyboard);
    window.addEventListener('pagehide', window.cleanup);
    // v9 owns __havalHeartbeatTimer. Do not create a second heartbeat or any telemetry poll.
    connect(); clock(); schedule();
})();
