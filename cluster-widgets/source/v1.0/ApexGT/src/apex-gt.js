(function () {
    "use strict";

    var root = document.getElementById("apex-gt");
    if (!root) return;

    var FUEL_TANK_LITERS = 55;
    var display = window.ApexDisplay ? window.ApexDisplay.create({ root: root }) : null;
    var menus = window.ApexMenus ? window.ApexMenus.create({ root: root, mount: document.getElementById("apex-menus"), display: display }) : null;
    var speedCalibration = window.ApexSpeed ? window.ApexSpeed.create() : null;
    var projection = window.ApexProjection ? window.ApexProjection.create({ root: root }) : null;
    var disposed = false;
    var renderFrame = null;
    var clockTimer = null;
    var heartbeatTimer = null;
    var subscribedKeys = [];
    var receivedKeys = Object.create(null);
    var canonicalFields = Object.create(null);
    var lastGaugeReading = null;
    var state = {
        speed: null, rpm: null, odometer: null, gear: null,
        driveMode: null, propulsionMode: null, insideTemp: null, outsideTemp: null,
        tempUnit: null, fuel: null, battery: null, fuelRange: null, batteryRange: null,
        trip: null, consumption: null, voltage: null, current: null,
        carPlayInDash: false, projectionMirrorInDash: false, aaClusterInDash: false, projectionPreparingD3: false
    };
    var keys = {
        "car.basic.vehicle_speed": "speed",
        "car.basic.engine_speed": "rpm",
        "car.basic.total_odometer": "odometer",
        "car.basic.gear_status": "gear",
        "car.drive_setting.drive_mode": "driveMode",
        "car.ev_setting.power_model_config": "propulsionMode",
        "car.basic.inside_temp": "insideTemp",
        "car.basic.outside_temp": "outsideTemp",
        "car.configure.default_temp_unit": "tempUnit",
        "car.basic.remain_fuel_percentage": "fuel",
        "car.ev_info.cur_battery_power_percentage": "battery",
        "car.ev_info.fuel_mode_remain_odometer": "fuelRange",
        "car.ev_info.electric_mode_remain_odometer": "batteryRange",
        "car.basic.cur_journey_odometer": "trip",
        "car.basic.cur_journey_avg_fuel_consume": "consumption",
        "car.ev_info.power_battery_voltage": "voltage",
        "car.ev_info.cur_charge_current": "current",
        // CarPlay chega como carPlayInDash; o espelho do Android Auto como projectionMirrorInDash e a
        // Surface própria dele como aaClusterInDash. Qualquer um deles é "projeção no painel".
        "carPlayInDash": "carPlayInDash",
        "projectionMirrorInDash": "projectionMirrorInDash",
        "aaClusterInDash": "aaClusterInDash",
        "projectionPreparingD3": "projectionPreparingD3"
    };
    // Legacy aliases are only a fallback before the field's canonical reading.
    // The host also emits adjusted/formatted aliases after its canonical push;
    // those must not replace native contract values (including missing values).
    // Signed electrical power always comes from the two canonical operands.
    var aliases = {
        carSpeed: "speed", engineRPM: "rpm", odometer: "odometer", gearState: "gear",
        drivingMode: "driveMode", evMode: "propulsionMode", inside_temp: "insideTemp",
        outside_temp: "outsideTemp", tempUnit: "tempUnit", fuelPercent: "fuel",
        batteryPercent: "battery", fuelRange: "fuelRange", batteryRange: "batteryRange"
    };
    var gearLabels = { "0": "N", "1": "N", "2": "D", "3": "P", "4": "R" };
    var driveLabels = { "0": "NORMAL", "1": "SPORT", "2": "ECO", "3": "NEVE", "4": "AREIA", "5": "LAMA", "11": "AWD" };
    var propulsionLabels = { "0": "HEV", "1": "EVP", "3": "EV" };
    var ids = [
        "speed-value", "power-value", "regen-value", "gear-value", "drive-mode", "propulsion-mode",
        "vector-power-value", "vector-power-state", "vector-power-direction",
        "outside-temp", "inside-temp", "clock", "odometer", "engine-rpm", "rpm-readout",
        "fuel-percent", "fuel-liters", "fuel-range", "fuel-fill", "battery-percent", "battery-range",
        "battery-fill", "trip-distance", "average-consumption", "consumption-unit", "total-range"
    ];
    var elements = Object.create(null);
    ids.forEach(function (id) { elements[id] = document.getElementById(id); });

    function numberOrNull(value) {
        if (typeof value !== "number" && typeof value !== "string") return null;
        if (typeof value === "string" && !/^[+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?$/i.test(value.trim())) return null;
        var parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    }

    function booleanValue(value) {
        return value === true || value === 1 || value === "1" || value === "true";
    }

    function enumValue(value, labels) {
        var number = numberOrNull(value);
        if (number !== null && Object.prototype.hasOwnProperty.call(labels, String(number))) return labels[String(number)];
        var label = typeof value === "string" ? value.trim().toUpperCase() : "";
        return Object.keys(labels).some(function (key) { return labels[key] === label; }) ? label : null;
    }

    function text(id, value) {
        var node = elements[id];
        var next = String(value);
        if (node && node.textContent !== next) node.textContent = next;
    }

    function format(value, decimals) {
        if (value === null || !Number.isFinite(value)) return "--";
        // Do not leave a negative zero on the regeneration readout after rounding.
        var fixed = value.toFixed(decimals || 0);
        return Number(fixed) === 0 ? (0).toFixed(decimals || 0) : fixed;
    }

    function fill(id, percentage) {
        var node = elements[id];
        if (!node) return;
        var next = "scaleX(" + (percentage === null ? 0 : percentage / 100) + ")";
        if (node.style.transform !== next) node.style.transform = next;
    }

    function signedPower() {
        if (state.voltage === null || state.current === null) return null;
        var power = state.voltage * state.current / 1000;
        return Number.isFinite(power) ? power : null;
    }

    function render() {
        renderFrame = null;
        if (disposed) return;
        var power = signedPower();
        var totalRange = state.fuelRange === null || state.batteryRange === null ? null : state.fuelRange + state.batteryRange;
        var tempUnit = state.tempUnit === 1 ? "°F" : "°C";
        // Mostrador e ponteiro usam a velocidade calibrada (a do HUD), não a bruta do CAN.
        var shownSpeed = speedCalibration ? speedCalibration.display(state.speed) : state.speed;
        text("speed-value", format(shownSpeed));
        root.classList.toggle("speed-three-digits", shownSpeed !== null && Math.round(shownSpeed) >= 100);
        text("power-value", format(power === null ? null : Math.max(0, power)));
        text("regen-value", format(power === null ? null : Math.min(0, power)));
        text("vector-power-value", format(power));
        text("vector-power-state", power === null ? "--" : power < 0 ? "REGEN" : "TRAÇÃO");
        text("vector-power-direction", power === null ? "" : power < 0 ? "‹" : "›");
        text("gear-value", state.gear || "--");
        text("drive-mode", state.driveMode || "--");
        if (elements["drive-mode"]) {
            elements["drive-mode"].classList.toggle("is-eco", state.driveMode === "ECO");
            elements["drive-mode"].classList.toggle("is-sport", state.driveMode === "SPORT");
        }
        text("propulsion-mode", state.propulsionMode || "--");
        text("outside-temp", state.outsideTemp === null ? "--" : format(state.outsideTemp) + tempUnit);
        text("inside-temp", state.insideTemp === null ? "--" : format(state.insideTemp) + tempUnit);
        text("odometer", format(state.odometer));
        text("engine-rpm", format(state.rpm));
        if (elements["rpm-readout"]) elements["rpm-readout"].hidden = state.rpm === null || state.rpm <= 0;
        text("fuel-percent", state.fuel === null ? "--" : format(state.fuel) + "%");
        text("fuel-liters", format(state.fuel === null ? null : state.fuel * FUEL_TANK_LITERS / 100, 1));
        text("fuel-range", format(state.fuelRange));
        fill("fuel-fill", state.fuel);
        text("battery-percent", state.battery === null ? "--" : format(state.battery) + "%");
        text("battery-range", format(state.batteryRange));
        fill("battery-fill", state.battery);
        text("trip-distance", format(state.trip, 1));
        // The source is L/100 km. Zero is real but its reciprocal is undefined.
        text("average-consumption", format(state.consumption === null ? null : (state.consumption > 0 ? 100 / state.consumption : 0), 1));
        text("consumption-unit", state.consumption === 0 ? "L/100 km" : "km/L");
        text("total-range", format(Number.isFinite(totalRange) ? totalRange : null));
        root.classList.toggle("is-regenerating", power !== null && power < 0);
        root.classList.toggle("projection-active", state.carPlayInDash || state.projectionMirrorInDash || state.aaClusterInDash);
        root.classList.toggle("projection-preparing", state.projectionPreparingD3);
        if (!lastGaugeReading || lastGaugeReading.speed !== shownSpeed || lastGaugeReading.power !== power) {
            lastGaugeReading = { speed: shownSpeed, power: power };
            window.dispatchEvent(new CustomEvent("apex-telemetry", { detail: { speed: shownSpeed, power: power } }));
        }
    }

    function scheduleRender() {
        if (!disposed && renderFrame === null) renderFrame = window.requestAnimationFrame(render);
    }

    function update(key, value, allowAliases) {
        if (disposed) return;
        if (display) display.update(key, value, allowAliases);
        if (menus) {
            if (menus.keys.indexOf(key) !== -1) receivedKeys[key] = true;
            menus.update(key, value, allowAliases);
        }
        if (speedCalibration) {
            if (speedCalibration.keys.indexOf(key) !== -1) receivedKeys[key] = true;
            if (speedCalibration.update(key, value, allowAliases)) scheduleRender();
        }
        if (projection) {
            if (projection.keys.indexOf(key) !== -1) receivedKeys[key] = true;
            projection.update(key, value, allowAliases);
        }
        var field = keys[key] || (allowAliases ? aliases[key] : undefined);
        if (!field) return;
        if (keys[key]) {
            receivedKeys[key] = true;
            canonicalFields[field] = true;
        } else if (canonicalFields[field]) return;
        if (field === "carPlayInDash" || field === "projectionMirrorInDash" || field === "aaClusterInDash" || field === "projectionPreparingD3") value = booleanValue(value);
        else if (field === "gear") value = enumValue(value, gearLabels);
        else if (field === "driveMode") value = enumValue(value, driveLabels);
        else if (field === "propulsionMode") {
            // These are the two legacy HEV labels published by the projector.
            if (key === "evMode" && typeof value === "string" && /^HEV (?:INTELIGENTE|PRIORIDADE \d+%)$/i.test(value.trim())) value = "HEV";
            value = enumValue(value, propulsionLabels);
        }
        else {
            if (allowAliases && key === "tempUnit" && value === "°F") value = 1;
            value = numberOrNull(value);
            // InstrumentProjector2.formatTemp uses these OEM missing sentinels;
            // canonical subscriptions/snapshots still carry the raw numbers.
            if ((field === "insideTemp" || field === "outsideTemp") && (value === -1 || value === 255)) value = null;
            if (field !== "insideTemp" && field !== "outsideTemp" && field !== "current" && value !== null && value < 0) value = null;
            if ((field === "fuel" || field === "battery") && value !== null && value > 100) value = null;
        }
        if (state[field] === value) return;
        state[field] = value;
        scheduleRender();
    }

    function subscribe() {
        var bridge = window.Android;
        if (!bridge || typeof bridge.subscribe !== "function") return;
        var available;
        try {
            available = typeof bridge.getAvailableKeys === "function" ? JSON.parse(bridge.getAvailableKeys()) : [];
        } catch (error) { return; }
        if (!Array.isArray(available)) return;
        var preferenceKeys = (display ? display.keys : [])
            .concat(speedCalibration ? speedCalibration.preferenceKeys : [], projection ? projection.preferenceKeys : []);
        var requestedKeys = Object.keys(keys).concat(menus ? menus.keys : [], preferenceKeys);
        subscribedKeys = requestedKeys.filter(function (key, index) {
            return requestedKeys.indexOf(key) === index && (available.indexOf(key) !== -1 || preferenceKeys.indexOf(key) !== -1);
        });
        if (!subscribedKeys.length) return;
        try { bridge.subscribe(JSON.stringify(subscribedKeys)); }
        catch (error) { subscribedKeys = []; return; }
        // Subscribe first so the host starts monitoring optional keys. Snapshot
        // only untouched keys: a synchronous initial push must keep precedence.
        if (typeof bridge.getCarData === "function") {
            subscribedKeys.forEach(function (key) {
                // Preference snapshots use getPreference in the controller;
                // getCarData is only the vehicle telemetry channel.
                if (receivedKeys[key] || preferenceKeys.indexOf(key) !== -1) return;
                try { update(key, bridge.getCarData(key), false); } catch (error) {}
            });
        }
    }

    function updateClock() {
        if (disposed) return;
        var now = new Date();
        text("clock", String(now.getHours()).padStart(2, "0") + ":" + String(now.getMinutes()).padStart(2, "0"));
        clockTimer = window.setTimeout(updateClock, 60000 - (now.getSeconds() * 1000 + now.getMilliseconds()));
    }

    window.onDataChanged = function (key, value) { update(key, value, false); };
    window.control = function (key, value) { update(key, value, true); };
    window.onCardChanged = function (cardId) {
        if (!disposed && menus) menus.onCardChanged(cardId);
    };
    window.onKeyEvent = function (keyName) {
        if (!disposed && menus) menus.onKeyEvent(keyName);
    };
    // Contract v1 themes own navigation; these legacy entry points never change
    // cards, vehicle settings, native masks, bounds, or projection state.
    window.showScreen = function () {};
    window.focus = function () {};
    window.cleanup = function () {
        if (disposed) return;
        disposed = true;
        if (menus) menus.cleanup();
        if (speedCalibration) speedCalibration.cleanup();
        if (projection) projection.cleanup();
        if (display) display.cleanup();
        if (renderFrame !== null) window.cancelAnimationFrame(renderFrame);
        if (clockTimer !== null) window.clearTimeout(clockTimer);
        if (heartbeatTimer !== null) window.clearInterval(heartbeatTimer);
        renderFrame = clockTimer = heartbeatTimer = null;
        window.removeEventListener("pagehide", window.cleanup);
        if (subscribedKeys.length && window.Android && typeof window.Android.unsubscribe === "function") {
            try { window.Android.unsubscribe(JSON.stringify(subscribedKeys)); } catch (error) {}
        }
        subscribedKeys = [];
        window.dispatchEvent(new CustomEvent("apex-cleanup"));
    };

    window.addEventListener("pagehide", window.cleanup);
    scheduleRender();
    updateClock();
    subscribe();
    if (window.Android && typeof window.Android.heartbeat === "function") {
        heartbeatTimer = window.setInterval(function () {
            try { window.Android.heartbeat(); }
            catch (error) { window.clearInterval(heartbeatTimer); heartbeatTimer = null; }
        }, 2000);
    }
})();
