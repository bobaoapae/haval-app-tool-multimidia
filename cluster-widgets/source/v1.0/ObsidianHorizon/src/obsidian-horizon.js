(function () {
    "use strict";

    var root = document.getElementById("obsidian-horizon");
    var subscribedKeys = [];
    var heartbeatTimer = null;
    var currentCardId = 1;
    var batteryVoltage = null;
    var batteryCurrent = null;
    var FUEL_TANK_CAPACITY_LITERS = 55;
    var KEY_DEBOUNCE_MS = 50;
    var GRAPH_SAMPLE_INTERVAL_MS = 250;
    var VISIBLE_CONTEXT_OPTIONS = 3;
    var lastKeyTimeMs = 0;
    var contextMenuIndex = 0;
    var contextMenuMode = "menu";
    var graphIndex = 0;
    var settingsIndex = 0;
    var infoIndex = 0;
    var tripFocusIndex = 0;
    var tripResetFeedback = "";
    var tripResetTimer = null;

    var contextMenuItems = [
        { title: "INFORMAÇÕES", panelId: "context-panel-info", view: "info" },
        { title: "GRÁFICOS", panelId: "context-panel-graphs", view: "graphs" },
        { title: "AJUSTES", panelId: "context-panel-settings", view: "settings" },
        { title: "TRIP A / TRIP B", panelId: "context-panel-trips", view: "trips" }
    ];

    var settingsItems = [
        { key: "car.drive_setting.drive_mode", stateKey: "drivingMode", values: ["0", "2", "1"] },
        { key: "car.ev_setting.power_model_config", stateKey: "evMode", values: ["0", "1", "3"] },
        { key: "car.drive_setting.steering_wheel_assist_mode", stateKey: "steerMode", values: ["2", "0", "1"] },
        { key: "car.ev_setting.energy_recovery_level", stateKey: "regenMode", values: ["2", "0", "1"] },
        { key: "car.drive_setting.esp_enable", stateKey: "espStatus", values: ["1", "0"] }
    ];

    var graphHistory = { power: [], speed: [] };
    var graphLastSampleAt = { power: 0, speed: 0 };

    var state = {
        clockTime: null,
        outside_temp: null,
        inside_temp: null,
        tempUnit: "°C",
        drivingMode: null,
        evMode: null,
        gearState: null,
        odometer: null,
        carSpeed: null,
        evPowerKw: null,
        engineRPM: null,
        fuelPercent: null,
        batteryPercent: null,
        fuelRange: null,
        batteryRange: null,
        tripAOdometer: null,
        tripADriveTime: null,
        tripAAvgConsumption: null,
        tripAAvgSpeed: null,
        tripBOdometer: null,
        tripBDriveTime: null,
        tripBAvgConsumption: null,
        tripBAvgSpeed: null,
        tirePressureFrontLeft: null,
        tirePressureFrontRight: null,
        tirePressureRearLeft: null,
        tirePressureRearRight: null,
        regenMode: "Normal",
        steerMode: "Conforto",
        espStatus: "ON",
        focusArea: "fan",
        temp: 22,
        fan: 1,
        power: 1,
        auto: 0,
        recycle: 0,
        carPlayInDash: false,
        projectionMirrorInDash: false,
        projectionPreparingD3: false
    };

    var canonicalToFriendly = {
        "car.basic.vehicle_speed": "carSpeed",
        "car.basic.engine_speed": "engineRPM",
        "car.basic.total_odometer": "odometer",
        "car.basic.gear_status": "gearState",
        "car.drive_setting.drive_mode": "drivingMode",
        "car.ev_setting.power_model_config": "evMode",
        "car.basic.inside_temp": "inside_temp",
        "car.basic.outside_temp": "outside_temp",
        "car.configure.default_temp_unit": "tempUnit",
        "car.basic.remain_fuel_percentage": "fuelPercent",
        "car.ev_info.cur_battery_power_percentage": "batteryPercent",
        "car.ev_info.fuel_mode_remain_odometer": "fuelRange",
        "car.ev_info.electric_mode_remain_odometer": "batteryRange",
        "car.basic.cur_journey_odometer": "tripAOdometer",
        "car.basic.cur_journey_drivetime": "tripADriveTime",
        "car.basic.cur_journey_avg_fuel_consume": "tripAAvgConsumption",
        "car.basic.avg_vehicle_speed_since_startup": "tripAAvgSpeed",
        "car.basic.accumulated_odometer": "tripBOdometer",
        "car.basic.accumulated_drivetime": "tripBDriveTime",
        "car.basic.avg_fuel_consumption": "tripBAvgConsumption",
        "car.basic.vehicle_speed_since_reset": "tripBAvgSpeed",
        "car.basic.tire_pressure_front_left": "tirePressureFrontLeft",
        "car.basic.tire_pressure_front_right": "tirePressureFrontRight",
        "car.basic.tire_pressure_rear_left": "tirePressureRearLeft",
        "car.basic.tire_pressure_rear_right": "tirePressureRearRight",
        "tripOdometer": "tripBOdometer",
        "tripDriveTime": "tripBDriveTime",
        "tripAvgConsumption": "tripBAvgConsumption",
        "tripAvgSpeed": "tripBAvgSpeed",
        "car.ev_setting.energy_recovery_level": "regenMode",
        "car.drive_setting.steering_wheel_assist_mode": "steerMode",
        "car.drive_setting.esp_enable": "espStatus",
        "car.hvac.power_mode": "power",
        "car.hvac.fan_speed": "fan",
        "car.hvac.driver_temperature": "temp",
        "car.hvac.cycle_mode": "recycle",
        "car.hvac.auto_enable": "auto"
    };

    var driveModeLabels = {
        "0": "NORMAL",
        "1": "SPORT",
        "2": "ECO",
        "3": "NEVE",
        "4": "AREIA",
        "5": "LAMA",
        "11": "AWD"
    };

    var propulsionLabels = {
        "0": "HEV",
        "1": "EVP",
        "3": "EV"
    };

    var gearLabels = {
        "0": "N",
        "1": "N",
        "2": "D",
        "3": "P",
        "4": "R"
    };

    var telemetryKeys = [
        "car.basic.vehicle_speed",
        "car.basic.engine_speed",
        "car.basic.total_odometer",
        "car.basic.gear_status",
        "car.drive_setting.drive_mode",
        "car.ev_setting.power_model_config",
        "car.basic.inside_temp",
        "car.basic.outside_temp",
        "car.configure.default_temp_unit",
        "car.basic.remain_fuel_percentage",
        "car.ev_info.cur_battery_power_percentage",
        "car.ev_info.fuel_mode_remain_odometer",
        "car.ev_info.electric_mode_remain_odometer",
        "car.basic.cur_journey_odometer",
        "car.basic.cur_journey_drivetime",
        "car.basic.cur_journey_avg_fuel_consume",
        "car.basic.avg_vehicle_speed_since_startup",
        "car.basic.accumulated_odometer",
        "car.basic.accumulated_drivetime",
        "car.basic.avg_fuel_consumption",
        "car.basic.vehicle_speed_since_reset",
        "car.basic.tire_pressure_front_left",
        "car.basic.tire_pressure_front_right",
        "car.basic.tire_pressure_rear_left",
        "car.basic.tire_pressure_rear_right",
        "car.ev_setting.energy_recovery_level",
        "car.drive_setting.steering_wheel_assist_mode",
        "car.drive_setting.esp_enable",
        "car.hvac.power_mode",
        "car.hvac.fan_speed",
        "car.hvac.driver_temperature",
        "car.hvac.cycle_mode",
        "car.hvac.auto_enable",
        "car.ev_info.energy_output_percentage",
        "car.ev_info.power_battery_voltage",
        "car.ev_info.cur_charge_current",
        "carPlayInDash",
        "projectionMirrorInDash",
        "projectionPreparingD3"
    ];

    function element(id) {
        return document.getElementById(id);
    }

    function isMissing(value) {
        return value === null || value === undefined || value === "" || value === "--";
    }

    function asNumberOrNull(value) {
        if (isMissing(value)) return null;
        var parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    }

    function clamp(value, min, max) {
        return Math.min(max, Math.max(min, value));
    }

    function asBoolean(value) {
        return value === true || value === 1 || value === "1" || String(value).toLowerCase() === "true";
    }

    function setText(id, value) {
        var node = element(id);
        var text = String(value);
        if (node && node.textContent !== text) node.textContent = text;
    }

    function setWidth(id, percent) {
        var node = element(id);
        if (node) node.style.width = clamp(percent, 0, 100).toFixed(1) + "%";
    }

    function formatInteger(value) {
        return Math.round(value).toLocaleString("pt-BR");
    }

    function normalizeTemperature(value) {
        if (isMissing(value)) return null;
        var number = Number(value);
        if (Number.isFinite(number)) return String(Math.round(number * 10) / 10);
        var text = String(value).replace(/[°CF]/gi, "").trim();
        return text || null;
    }

    function displayTemperature(value) {
        var normalized = normalizeTemperature(value);
        return normalized === null ? "--" : normalized + state.tempUnit;
    }

    function normalizeGear(value) {
        if (isMissing(value)) return "--";
        var raw = String(value).trim().toUpperCase();
        return gearLabels[raw] || (/^[PRND]$/.test(raw) ? raw : "--");
    }

    function normalizeDriveMode(value) {
        if (isMissing(value)) return "--";
        var raw = String(value).trim();
        return driveModeLabels[raw] || raw.toUpperCase() || "--";
    }

    function normalizePropulsion(value) {
        if (isMissing(value)) return "--";
        var raw = String(value).trim();
        return propulsionLabels[raw] || raw.toUpperCase() || "--";
    }

    function normalizeRegenMode(value) {
        if (isMissing(value)) return "--";
        var raw = String(value).trim();
        return { "0": "NORMAL", "1": "ALTO", "2": "BAIXO" }[raw] || raw.toUpperCase() || "--";
    }

    function normalizeSteerMode(value) {
        if (isMissing(value)) return "--";
        var raw = String(value).trim();
        return { "0": "NORMAL", "1": "ESPORTIVA", "2": "CONFORTO" }[raw] || raw.toUpperCase() || "--";
    }

    function normalizeEspStatus(value) {
        if (isMissing(value)) return "--";
        var raw = String(value).trim().toUpperCase();
        if (raw === "1" || raw === "ON" || raw === "ATIVO") return "ON";
        if (raw === "0" || raw === "OFF" || raw === "INATIVO") return "OFF";
        return raw || "--";
    }

    function formatAcTemperature(value) {
        var temperature = asNumberOrNull(value);
        if (temperature === null) return "--";
        if (temperature <= 16) return "LO";
        if (temperature >= 32) return "HI";
        return temperature.toFixed(1) + state.tempUnit;
    }

    function setInstrumentProgress(name, value) {
        var baseline = document.querySelector(name === "speed" ? ".instrument-speed .instrument-baseline span" : ".instrument-power .instrument-baseline span");
        var progress = clamp(value, 0, 1);
        if (baseline) baseline.style.width = (progress * 100).toFixed(1) + "%";
    }

    function renderTemperatures() {
        setText("outside-temperature", displayTemperature(state.outside_temp));
        setText("inside-temperature", displayTemperature(state.inside_temp));
    }

    function renderPower() {
        var power = asNumberOrNull(state.evPowerKw);
        var isRegen = power !== null && power < -0.1;
        var absolutePower = power === null ? null : Math.abs(power);
        var powerSign = absolutePower === null || absolutePower < 0.05 ? "" : (isRegen ? "+" : "−");
        var powerText = absolutePower === null ? "--" : powerSign + Math.round(absolutePower);
        setText("power-whole", powerText);
        element("power-kw").setAttribute("aria-label", powerText);
        setText("power-direction", isRegen ? "REGENERAÇÃO" : "USO DE ENERGIA");
        var instrument = element("power-instrument");
        if (instrument) instrument.classList.toggle("is-regen", isRegen);
        setInstrumentProgress("power", absolutePower === null ? 0 : absolutePower / 140);
    }

    function renderRpm() {
        var rpm = asNumberOrNull(state.engineRPM);
        var active = rpm !== null && rpm >= 400;
        var container = element("rpm-readout");
        if (container) container.hidden = !active;
        setText("engine-rpm", rpm === null ? "--" : formatInteger(Math.max(0, rpm)));
        setWidth("rpm-fill", rpm === null ? 0 : Math.max(0, rpm) / 70);
    }

    function renderPercent(valueKey, textId, fillId) {
        var value = asNumberOrNull(state[valueKey]);
        if (value === null) {
            setText(textId, "--");
            setWidth(fillId, 0);
            return;
        }
        var level = clamp(value, 0, 100);
        setText(textId, Math.round(level) + "%");
        setWidth(fillId, level);
    }

    function renderFuelLevel() {
        var value = asNumberOrNull(state.fuelPercent);
        renderPercent("fuelPercent", "fuel-percent", "fuel-fill");
        if (value === null) {
            setText("fuel-liters", "--");
            return;
        }
        var liters = clamp(value, 0, 100) * FUEL_TANK_CAPACITY_LITERS / 100;
        setText("fuel-liters", liters.toFixed(1));
    }

    function renderRange(valueKey, textId) {
        var value = asNumberOrNull(state[valueKey]);
        setText(textId, value === null ? "--" : formatInteger(Math.max(0, value)) + " km");
    }

    function renderProjectionState() {
        root.classList.toggle("carplay-active", asBoolean(state.carPlayInDash));
        root.classList.toggle("android-auto-active", asBoolean(state.projectionMirrorInDash));
        root.classList.toggle("projection-preparing", asBoolean(state.projectionPreparingD3));
    }

    function activeContextView() {
        if (currentCardId !== 1 || contextMenuMode !== "detail") return null;
        return contextMenuItems[contextMenuIndex].view;
    }

    function renderSlidingWindow(panelId, selector, focusedIndex) {
        var panel = element(panelId);
        if (!panel) return;
        var items = Array.prototype.slice.call(panel.querySelectorAll(selector));
        var lastStart = Math.max(0, items.length - VISIBLE_CONTEXT_OPTIONS);
        var windowStart = clamp(focusedIndex - 1, 0, lastStart);
        var windowEnd = windowStart + VISIBLE_CONTEXT_OPTIONS;
        panel.classList.toggle("has-more-above", windowStart > 0);
        panel.classList.toggle("has-more-below", windowEnd < items.length);
        items.forEach(function (item, index) {
            var visible = index >= windowStart && index < windowEnd;
            item.classList.toggle("is-window-visible", visible);
            item.classList.toggle("is-window-before", index < windowStart);
            item.classList.toggle("is-window-after", index >= windowEnd);
            item.style.setProperty("--slide-slot", String(index - windowStart));
            item.setAttribute("aria-hidden", visible ? "false" : "true");
        });
    }

    function renderContextMenu() {
        Array.prototype.forEach.call(document.querySelectorAll("[data-context-menu-option]"), function (item) {
            item.classList.toggle("is-focused", Number(item.getAttribute("data-context-menu-option")) === contextMenuIndex);
        });
        renderSlidingWindow("context-panel-menu", "[data-context-menu-option]", contextMenuIndex);
    }

    function renderContextSettings() {
        setText("context-setting-drive", normalizeDriveMode(state.drivingMode));
        setText("context-setting-propulsion", normalizePropulsion(state.evMode));
        setText("context-setting-steer", normalizeSteerMode(state.steerMode));
        setText("context-setting-regen", normalizeRegenMode(state.regenMode));
        setText("context-setting-esp", normalizeEspStatus(state.espStatus));
        Array.prototype.forEach.call(document.querySelectorAll("[data-context-setting]"), function (item) {
            item.classList.toggle("is-focused", Number(item.getAttribute("data-context-setting")) === settingsIndex);
        });
        renderSlidingWindow("context-panel-settings", "[data-context-setting]", settingsIndex);
    }

    function recordGraphSample(metric, value) {
        var sample = asNumberOrNull(value);
        if (sample === null) return false;
        var now = Date.now();
        var history = graphHistory[metric];
        if (now - graphLastSampleAt[metric] < GRAPH_SAMPLE_INTERVAL_MS && history.length > 0) {
            history[history.length - 1] = sample;
            return false;
        }
        graphLastSampleAt[metric] = now;
        history.push(sample);
        if (history.length > 18) history.shift();
        return true;
    }

    function renderContextGraph() {
        var metric = graphIndex === 0 ? "power" : "speed";
        var value = metric === "power" ? asNumberOrNull(state.evPowerKw) : asNumberOrNull(state.carSpeed);
        var maxValue = metric === "power" ? 140 : 180;
        var unit = metric === "power" ? " KW" : " KM/H";
        var graph = element("context-graph-bars");
        setText("context-graph-label", metric === "power" ? "POTÊNCIA EV" : "VELOCIDADE");
        setText("context-graph-value", value === null ? "--" : (metric === "power" ? value.toFixed(1) : Math.round(value)) + unit);
        setText("context-graph-min", metric === "power" ? "−50" : "0");
        setText("context-graph-max", maxValue + unit);
        if (!graph) return;
        graph.classList.toggle("is-speed", metric === "speed");
        var history = graphHistory[metric];
        var emptyCount = 18 - history.length;
        Array.prototype.forEach.call(graph.children, function (bar, index) {
            var historyIndex = index - emptyCount;
            var sample = historyIndex >= 0 ? history[historyIndex] : null;
            var height = sample === null ? 4 : clamp(Math.abs(sample) / (sample < 0 ? 50 : maxValue), 0.04, 1) * 100;
            bar.style.height = height.toFixed(1) + "%";
            bar.classList.toggle("is-empty", sample === null);
            bar.classList.toggle("is-regen", metric === "power" && sample !== null && sample < 0);
        });
    }

    function renderContextInfo() {
        var odometer = asNumberOrNull(state.odometer);
        var fuel = asNumberOrNull(state.fuelPercent);
        var battery = asNumberOrNull(state.batteryPercent);
        var fuelRange = asNumberOrNull(state.fuelRange);
        var batteryRange = asNumberOrNull(state.batteryRange);
        var levels = (fuel === null ? "--" : Math.round(clamp(fuel, 0, 100)) + "%")
            + " COMB. · "
            + (battery === null ? "--" : Math.round(clamp(battery, 0, 100)) + "%")
            + " BATERIA";
        var ranges = (fuelRange === null ? "--" : formatInteger(Math.max(0, fuelRange)))
            + " + "
            + (batteryRange === null ? "--" : formatInteger(Math.max(0, batteryRange)))
            + " KM";
        setText("context-info-odometer", odometer === null ? "--" : formatInteger(Math.max(0, odometer)) + " KM");
        setText("context-info-temperatures", displayTemperature(state.inside_temp) + " · " + displayTemperature(state.outside_temp));
        setText("context-info-energy", levels + "  |  " + ranges);
        renderTirePressure("front-left", state.tirePressureFrontLeft);
        renderTirePressure("front-right", state.tirePressureFrontRight);
        renderTirePressure("rear-left", state.tirePressureRearLeft);
        renderTirePressure("rear-right", state.tirePressureRearRight);
        var generalPage = element("context-info-general");
        var tirePage = element("context-info-tires");
        if (generalPage) generalPage.hidden = infoIndex !== 0;
        if (tirePage) tirePage.hidden = infoIndex !== 1;
        Array.prototype.forEach.call(document.querySelectorAll("[data-context-info-tab]"), function (item) {
            item.classList.toggle("is-active", Number(item.getAttribute("data-context-info-tab")) === infoIndex);
        });
    }

    function tirePressurePsi(value) {
        var pressureKpa = asNumberOrNull(value);
        if (pressureKpa === null || pressureKpa <= 0) return null;
        return pressureKpa / 6.89476;
    }

    function renderTirePressure(position, value) {
        var pressurePsi = tirePressurePsi(value);
        var regular = pressurePsi !== null && pressurePsi >= 35;
        var cell = element("context-tire-cell-" + position);
        setText("context-tire-" + position, pressurePsi === null ? "--" : pressurePsi.toFixed(1));
        setText("context-tire-status-" + position, pressurePsi === null ? "SEM SINAL" : (regular ? "OK" : "BAIXA"));
        if (!cell) return;
        cell.classList.toggle("is-regular", regular);
        cell.classList.toggle("is-low", pressurePsi !== null && !regular);
    }
    function formatTripDistance(value) {
        var distance = asNumberOrNull(value);
        return distance === null ? "--" : Math.max(0, distance).toLocaleString("pt-BR", {
            minimumFractionDigits: 1,
            maximumFractionDigits: 1
        }) + " KM";
    }

    function formatTripTime(value) {
        var duration = asNumberOrNull(value);
        if (duration === null || duration < 0) return "--";
        var totalMinutes = Math.round(duration);
        var hours = Math.floor(totalMinutes / 60);
        var minutes = totalMinutes % 60;
        return hours > 0 ? hours + "H " + String(minutes).padStart(2, "0") + "MIN" : minutes + " MIN";
    }

    function formatTripSpeed(value) {
        var speed = asNumberOrNull(value);
        return speed === null ? "--" : Math.max(0, speed).toLocaleString("pt-BR", {
            minimumFractionDigits: 1,
            maximumFractionDigits: 1
        }) + " KM/H";
    }

    function formatTripConsumption(value) {
        var litersPerHundredKm = asNumberOrNull(value);
        if (litersPerHundredKm === null || litersPerHundredKm <= 0) return "--";
        return (100 / litersPerHundredKm).toLocaleString("pt-BR", {
            minimumFractionDigits: 1,
            maximumFractionDigits: 1
        }) + " KM/L";
    }

    function renderContextTrips() {
        setText("context-trip-a-distance", formatTripDistance(state.tripAOdometer));
        setText("context-trip-a-time", formatTripTime(state.tripADriveTime));
        setText("context-trip-a-speed", formatTripSpeed(state.tripAAvgSpeed));
        setText("context-trip-a-consumption", formatTripConsumption(state.tripAAvgConsumption));
        setText("context-trip-b-distance", formatTripDistance(state.tripBOdometer));
        setText("context-trip-b-time", formatTripTime(state.tripBDriveTime));
        setText("context-trip-b-speed", formatTripSpeed(state.tripBAvgSpeed));
        setText("context-trip-b-consumption", formatTripConsumption(state.tripBAvgConsumption));
        setText("context-trip-b-reset-status", tripResetFeedback || "SEGURE OK PARA ZERAR");
        Array.prototype.forEach.call(document.querySelectorAll("[data-context-trip]"), function (item) {
            item.classList.toggle("is-focused", Number(item.getAttribute("data-context-trip")) === tripFocusIndex);
        });
    }

    function requestTripBReset() {
        var sent = false;
        try {
            if (window.Android && typeof window.Android.triggerSystemAction === "function") {
                window.Android.triggerSystemAction("RESET_DRIVE_INFO");
                sent = true;
            }
        } catch (error) {}

        if (!window.Android) {
            updateState("tripBOdometer", 0);
            updateState("tripBDriveTime", 0);
            updateState("tripBAvgConsumption", null);
            updateState("tripBAvgSpeed", 0);
            sent = true;
        }

        tripResetFeedback = sent ? "ZERAGEM SOLICITADA" : "AÇÃO INDISPONÍVEL";
        renderContextTrips();
        if (tripResetTimer !== null) window.clearTimeout(tripResetTimer);
        tripResetTimer = window.setTimeout(function () {
            tripResetTimer = null;
            tripResetFeedback = "";
            if (activeContextView() === "trips") renderContextTrips();
        }, 2200);
    }

    function setStatusClass(id, active) {
        var node = element(id);
        if (!node) return;
        node.classList.toggle("is-on", active);
        var stateNode = node.parentElement && node.parentElement.parentElement;
        if (stateNode) stateNode.classList.toggle("is-on", active);
    }

    function renderContextAc() {
        var fan = asNumberOrNull(state.fan);
        var temperature = asNumberOrNull(state.temp);
        var powerOn = asNumberOrNull(state.power) > 0;
        var autoOn = asNumberOrNull(state.auto) > 0;
        var recycleOn = asNumberOrNull(state.recycle) > 0;
        var fanLevel = fan === null ? 0 : Math.round(clamp(fan, 0, 7));
        setText("context-ac-temp", formatAcTemperature(state.temp));
        setText("context-ac-fan", powerOn ? (fan === null ? "--" : String(fanLevel)) : "OFF");
        setText("context-ac-power", powerOn ? "LIGADO" : "DESL.");
        setText("context-ac-auto", autoOn ? "AUTO" : "MANUAL");
        setText("context-ac-recycle", recycleOn ? "RECIRC." : "EXTERNO");
        setStatusClass("context-ac-power", powerOn);
        setStatusClass("context-ac-auto", autoOn);
        setStatusClass("context-ac-recycle", recycleOn);
        var tempRow = element("context-ac-temp-row");
        var fanRow = element("context-ac-fan-row");
        var acPanel = element("context-panel-ac");
        var tempFill = element("context-ac-temp-fill");
        var fanSegments = document.querySelectorAll("#context-ac-fan-level i");
        if (tempRow) tempRow.classList.toggle("is-focused", state.focusArea === "temp");
        if (fanRow) fanRow.classList.toggle("is-focused", state.focusArea !== "temp");
        if (acPanel) acPanel.classList.toggle("is-off", !powerOn);
        if (tempFill) {
            var temperatureRatio = temperature === null ? 0 : clamp((temperature - 16) / 16, 0, 1);
            tempFill.style.width = (temperatureRatio * 100).toFixed(1) + "%";
        }
        Array.prototype.forEach.call(fanSegments, function (segment, index) {
            segment.classList.toggle("is-active", powerOn && index < fanLevel);
        });
    }

    function renderContextCard() {
        var nativeCard = currentCardId === 0;
        var acCard = currentCardId === 3;
        root.classList.toggle("card-native", nativeCard);
        root.classList.toggle("card-main", !nativeCard && !acCard);
        root.classList.toggle("card-ac", acCard);
        root.setAttribute("data-card-id", String(currentCardId));
        Array.prototype.forEach.call(document.querySelectorAll("[data-card-nav]"), function (item) {
            var active = Number(item.getAttribute("data-card-nav")) === currentCardId;
            item.classList.toggle("is-active", active);
            item.setAttribute("aria-current", active ? "true" : "false");
        });

        var card = element("context-card");
        if (card) card.hidden = nativeCard;
        if (card) card.classList.remove("has-back-hint");
        var kicker = element("context-card-kicker");
        if (kicker) kicker.hidden = acCard;
        var backHint = element("context-back-hint");
        if (backHint) backHint.hidden = true;
        ["context-panel-menu", "context-panel-info", "context-panel-graphs", "context-panel-settings", "context-panel-trips"].forEach(function (panelId) {
            var panel = element(panelId);
            if (panel) panel.hidden = true;
        });
        var acPanel = element("context-panel-ac");
        if (acPanel) acPanel.hidden = !acCard;
        if (nativeCard) return;

        if (acCard) {
            setText("context-card-counter", "CARD 03");
            setText("context-card-title", "CLIMATIZAÇÃO");
            renderContextAc();
            return;
        }

        if (contextMenuMode === "menu") {
            var menuPanel = element("context-panel-menu");
            if (menuPanel) menuPanel.hidden = false;
            setText("context-card-title", "INFORMAÇÕES");
            renderContextMenu();
            return;
        }

        var item = contextMenuItems[contextMenuIndex];
        if (backHint) backHint.hidden = false;
        if (card) card.classList.add("has-back-hint");
        var activePanel = element(item.panelId);
        if (activePanel) activePanel.hidden = false;
        setText("context-card-kicker", "");
        setText("context-card-counter", String(contextMenuIndex + 1).padStart(2, "0") + " / 04");
        setText("context-card-title", item.title);
        if (item.view === "info") renderContextInfo();
        if (item.view === "graphs") renderContextGraph();
        if (item.view === "settings") renderContextSettings();
        if (item.view === "trips") renderContextTrips();
    }

    function render(key) {
        switch (key) {
            case "clockTime":
                setText("clock-time", isMissing(state.clockTime) ? "--" : state.clockTime);
                break;
            case "outside_temp":
            case "inside_temp":
            case "tempUnit":
                renderTemperatures();
                if (activeContextView() === "info") renderContextInfo();
                if (currentCardId === 3) renderContextAc();
                break;
            case "drivingMode":
                setText("drive-mode", normalizeDriveMode(state.drivingMode));
                if (activeContextView() === "settings") renderContextSettings();
                break;
            case "evMode":
                setText("propulsion-mode", normalizePropulsion(state.evMode));
                if (activeContextView() === "settings") renderContextSettings();
                break;
            case "gearState":
                setText("gear-state", normalizeGear(state.gearState));
                break;
            case "odometer": {
                var odometer = asNumberOrNull(state.odometer);
                setText("odometer", odometer === null ? "--" : formatInteger(Math.max(0, odometer)) + " km");
                if (activeContextView() === "info") renderContextInfo();
                break;
            }
            case "carSpeed": {
                var speed = asNumberOrNull(state.carSpeed);
                setText("vehicle-speed", speed === null ? "--" : Math.round(clamp(speed, 0, 299)));
                setInstrumentProgress("speed", speed === null ? 0 : speed / 180);
                var speedSampled = recordGraphSample("speed", speed);
                if (activeContextView() === "graphs" && graphIndex === 1 && speedSampled) renderContextGraph();
                break;
            }
            case "evPowerKw":
                renderPower();
                var powerSampled = recordGraphSample("power", state.evPowerKw);
                if (activeContextView() === "graphs" && graphIndex === 0 && powerSampled) renderContextGraph();
                break;
            case "engineRPM":
                renderRpm();
                break;
            case "fuelPercent":
                renderFuelLevel();
                if (activeContextView() === "info") renderContextInfo();
                break;
            case "batteryPercent":
                renderPercent("batteryPercent", "battery-percent", "battery-fill");
                if (activeContextView() === "info") renderContextInfo();
                break;
            case "fuelRange":
                renderRange("fuelRange", "fuel-range");
                if (activeContextView() === "info") renderContextInfo();
                break;
            case "batteryRange":
                renderRange("batteryRange", "battery-range");
                if (activeContextView() === "info") renderContextInfo();
                break;
            case "tripAOdometer":
            case "tripADriveTime":
            case "tripAAvgConsumption":
            case "tripAAvgSpeed":
            case "tripBOdometer":
            case "tripBDriveTime":
            case "tripBAvgConsumption":
            case "tripBAvgSpeed":
                if (activeContextView() === "trips") renderContextTrips();
                break;
            case "tirePressureFrontLeft":
            case "tirePressureFrontRight":
            case "tirePressureRearLeft":
            case "tirePressureRearRight":
                if (activeContextView() === "info") renderContextInfo();
                break;
            case "regenMode":
            case "steerMode":
            case "espStatus":
                if (activeContextView() === "settings") renderContextSettings();
                break;
            case "focusArea":
            case "temp":
            case "fan":
            case "power":
            case "auto":
            case "recycle":
                if (currentCardId === 3) renderContextAc();
                break;
            case "carPlayInDash":
            case "projectionMirrorInDash":
            case "projectionPreparingD3":
                renderProjectionState();
                break;
        }
    }

    function updateCalculatedPower() {
        if (batteryVoltage === null || batteryCurrent === null) return;
        updateState("evPowerKw", (batteryVoltage * batteryCurrent) / 1000);
    }

    function updateState(key, value) {
        var friendlyKey = canonicalToFriendly[key] || key;

        if (key === "car.ev_info.energy_output_percentage") {
            return;
        }
        if (key === "car.ev_info.power_battery_voltage") {
            batteryVoltage = asNumberOrNull(value);
            updateCalculatedPower();
            return;
        }
        if (key === "car.ev_info.cur_charge_current") {
            batteryCurrent = asNumberOrNull(value);
            updateCalculatedPower();
            return;
        }
        // evModeLabel is the legacy friendly alias for the driving-mode label.
        // Obsidian renders drivingMode and evMode from their independent keys,
        // so accepting it as propulsion would overwrite HEV/EV with SPORT/ECO.
        if (friendlyKey === "evModeLabel") return;
        if (!(friendlyKey in state)) return;

        if (friendlyKey === "tempUnit") {
            value = String(value) === "1" || String(value).toUpperCase().indexOf("F") >= 0 ? "°F" : "°C";
        } else if (friendlyKey === "carPlayInDash" || friendlyKey === "projectionMirrorInDash" || friendlyKey === "projectionPreparingD3") {
            value = asBoolean(value);
        }

        if (state[friendlyKey] === value) return;
        state[friendlyKey] = value;
        render(friendlyKey);
    }

    function subscribeToTelemetry() {
        if (!window.Android || typeof window.Android.subscribe !== "function") return;
        var available = null;

        if (typeof window.Android.getAvailableKeys === "function") {
            try {
                available = JSON.parse(window.Android.getAvailableKeys());
                if (!Array.isArray(available)) available = null;
            } catch (error) {
                available = null;
            }
        }

        subscribedKeys = available ? telemetryKeys.filter(function (key) { return available.indexOf(key) >= 0; }) : telemetryKeys.slice();
        if (subscribedKeys.length === 0) return;

        try {
            window.Android.subscribe(JSON.stringify(subscribedKeys));
        } catch (error) {
            subscribedKeys = [];
        }
    }

    function startHeartbeat() {
        if (!window.Android || typeof window.Android.heartbeat !== "function") return;
        heartbeatTimer = window.setInterval(function () {
            try {
                window.Android.heartbeat();
            } catch (error) {
                window.clearInterval(heartbeatTimer);
                heartbeatTimer = null;
            }
        }, 2000);
    }

    function previewParameter(name) {
        return new URLSearchParams(window.location.search).get(name);
    }

    function seedPreview() {
        if (previewParameter("preview") !== "1") return;
        root.classList.add("preview-mode");
        updateState("clockTime", "08:29");
        updateState("outside_temp", 27);
        updateState("inside_temp", 22.5);
        updateState("drivingMode", "Sport");
        updateState("evMode", "HEV");
        updateState("gearState", "D");
        updateState("odometer", 18426);
        var previewSpeed = asNumberOrNull(previewParameter("speed"));
        var previewPower = asNumberOrNull(previewParameter("power"));
        var previewRegen = previewParameter("regen") === "1";
        updateState("carSpeed", previewSpeed === null ? 86 : previewSpeed);
        updateState("evPowerKw", previewPower === null ? (previewRegen ? -34.6 : 48.2) : (previewRegen ? -Math.abs(previewPower) : Math.abs(previewPower)));
        updateState("engineRPM", 1680);
        updateState("fuelPercent", 64);
        updateState("batteryPercent", 82);
        updateState("fuelRange", 412);
        updateState("batteryRange", 126);
        updateState("tripAOdometer", 18.6);
        updateState("tripADriveTime", 42);
        updateState("tripAAvgConsumption", 7.1);
        updateState("tripAAvgSpeed", 38.4);
        updateState("tripBOdometer", 286.4);
        updateState("tripBDriveTime", 425);
        updateState("tripBAvgConsumption", 7.4);
        updateState("tripBAvgSpeed", 41.2);
        updateState("tirePressureFrontLeft", 250);
        updateState("tirePressureFrontRight", 248);
        updateState("tirePressureRearLeft", 220);
        updateState("tirePressureRearRight", 249);
        updateState("regenMode", "Normal");
        updateState("steerMode", "Conforto");
        updateState("espStatus", "ON");
        var previewTemp = asNumberOrNull(previewParameter("temp"));
        var previewFan = asNumberOrNull(previewParameter("fan"));
        var previewPower = asNumberOrNull(previewParameter("acPower"));
        var previewFocus = previewParameter("focus");
        updateState("temp", previewTemp === null ? 22.5 : clamp(previewTemp, 16, 32));
        updateState("fan", previewFan === null ? 3 : Math.round(clamp(previewFan, 0, 7)));
        updateState("power", previewPower === null ? 1 : (previewPower > 0 ? 1 : 0));
        if (previewFocus === "temp" || previewFocus === "fan") updateState("focusArea", previewFocus);
        var previewCard = asNumberOrNull(previewParameter("card"));
        if ([0, 1, 3].indexOf(previewCard) >= 0) currentCardId = previewCard;
        if (previewParameter("projection") === "carplay") updateState("carPlayInDash", true);
    }

    function updateVehicleSetting(key, value) {
        try {
            if (window.Android && typeof window.Android.updateCarData === "function") {
                window.Android.updateCarData(key, String(value));
            } else {
                updateState(key, value);
            }
        } catch (error) {
            updateState(key, value);
        }
    }

    function handleAcKey(keyName) {
        if (keyName === "BACK") return;
        if (keyName === "ENTER") {
            updateState("focusArea", state.focusArea === "temp" ? "fan" : "temp");
            return;
        }
        if (keyName === "BACK_LONG") {
            var nextRecycle = asNumberOrNull(state.recycle) > 0 ? 0 : 1;
            updateState("recycle", nextRecycle);
            updateVehicleSetting("car.hvac.cycle_mode", nextRecycle);
            return;
        }
        if (keyName === "ENTER_LONG") {
            var nextAuto = asNumberOrNull(state.auto) > 0 ? 0 : 1;
            updateState("auto", nextAuto);
            updateVehicleSetting("car.hvac.auto_enable", nextAuto);
            try {
                if (window.Android && typeof window.Android.triggerSystemAction === "function") {
                    window.Android.triggerSystemAction("CANCEL_MAX_AC");
                }
            } catch (error) {}
            return;
        }
        if (keyName !== "UP" && keyName !== "DOWN") return;

        if (state.focusArea === "temp") {
            var currentTemp = asNumberOrNull(state.temp);
            var temperature = currentTemp === null ? 22 : currentTemp;
            var delta = keyName === "UP" ? 0.5 : -0.5;
            var nextTemp = clamp(Math.round((temperature + delta) * 2) / 2, 16, 32);
            updateVehicleSetting("car.hvac.driver_temperature", nextTemp.toFixed(1));
            return;
        }

        var currentFan = asNumberOrNull(state.fan);
        var fan = currentFan === null ? 0 : Math.round(currentFan);
        var nextFan = keyName === "UP" ? Math.min(7, fan + 1) : Math.max(0, fan - 1);
        if (fan === 0 && nextFan > 0) updateVehicleSetting("car.hvac.power_mode", 1);
        if (nextFan === 0) updateVehicleSetting("car.hvac.power_mode", 0);
        updateVehicleSetting("car.hvac.fan_speed", nextFan);
    }

    function currentSettingRaw(item) {
        var value = state[item.stateKey];
        if (item.stateKey === "drivingMode") {
            var drive = normalizeDriveMode(value);
            return { NORMAL: "0", ECO: "2", SPORT: "1" }[drive] || String(value);
        }
        if (item.stateKey === "evMode") {
            var propulsion = normalizePropulsion(value);
            return { HEV: "0", EVP: "1", EV: "3" }[propulsion] || String(value);
        }
        if (item.stateKey === "steerMode") {
            var steering = normalizeSteerMode(value);
            return { NORMAL: "0", ESPORTIVA: "1", CONFORTO: "2" }[steering] || String(value);
        }
        if (item.stateKey === "regenMode") {
            var regen = normalizeRegenMode(value);
            return { NORMAL: "0", ALTO: "1", BAIXO: "2" }[regen] || String(value);
        }
        return normalizeEspStatus(value) === "ON" ? "1" : "0";
    }

    function cycleContextSetting() {
        var item = settingsItems[settingsIndex];
        var current = currentSettingRaw(item);
        var currentIndex = item.values.indexOf(current);
        var next = item.values[(currentIndex + 1 + item.values.length) % item.values.length];
        updateState(item.key, next);
        updateVehicleSetting(item.key, next);
    }

    function handleContextMenuKey(keyName) {
        if (contextMenuMode === "menu") {
            if (keyName === "ENTER") {
                contextMenuMode = "detail";
                renderContextCard();
                return;
            }
            if (keyName !== "UP" && keyName !== "DOWN") return;
            var menuStep = keyName === "UP" ? -1 : 1;
            contextMenuIndex = (contextMenuIndex + menuStep + contextMenuItems.length) % contextMenuItems.length;
            renderContextCard();
            return;
        }

        if (keyName === "BACK") {
            contextMenuMode = "menu";
            renderContextCard();
            return;
        }

        var view = contextMenuItems[contextMenuIndex].view;
        if (view === "graphs" && (keyName === "UP" || keyName === "DOWN")) {
            graphIndex = graphIndex === 0 ? 1 : 0;
            renderContextGraph();
            return;
        }
        if (view === "info" && (keyName === "UP" || keyName === "DOWN")) {
            infoIndex = infoIndex === 0 ? 1 : 0;
            renderContextInfo();
            return;
        }
        if (view === "trips") {
            if (keyName === "UP" || keyName === "DOWN") {
                tripFocusIndex = tripFocusIndex === 0 ? 1 : 0;
                renderContextTrips();
                return;
            }
            if (keyName === "ENTER_LONG" && tripFocusIndex === 1) {
                requestTripBReset();
            }
            return;
        }
        if (view !== "settings") return;
        if (keyName === "ENTER") {
            cycleContextSetting();
            return;
        }
        if (keyName !== "UP" && keyName !== "DOWN") return;
        var settingStep = keyName === "UP" ? -1 : 1;
        settingsIndex = (settingsIndex + settingStep + settingsItems.length) % settingsItems.length;
        renderContextSettings();
    }

    window.control = function (key, value) {
        updateState(key, value);
    };

    window.onDataChanged = function (key, value) {
        updateState(key, value);
    };

    window.onCardChanged = function (cardId) {
        var nextCardId = Number(cardId);
        if ([0, 1, 3].indexOf(nextCardId) < 0) return;
        if (nextCardId === 1) contextMenuMode = "menu";
        currentCardId = nextCardId;
        renderContextCard();
    };

    window.onKeyEvent = function (keyName) {
        if (currentCardId === 0) return;
        var now = Date.now();
        if (now - lastKeyTimeMs < KEY_DEBOUNCE_MS) return;
        lastKeyTimeMs = now;
        var normalizedKey = String(keyName || "").toUpperCase();
        if (currentCardId === 3) {
            handleAcKey(normalizedKey);
            return;
        }
        handleContextMenuKey(normalizedKey);
    };

    window.showScreen = function () {};
    window.focus = function () {};

    window.cleanup = function () {
        if (heartbeatTimer !== null) {
            window.clearInterval(heartbeatTimer);
            heartbeatTimer = null;
        }
        if (tripResetTimer !== null) {
            window.clearTimeout(tripResetTimer);
            tripResetTimer = null;
        }
        if (subscribedKeys.length > 0 && window.Android && typeof window.Android.unsubscribe === "function") {
            try {
                window.Android.unsubscribe(JSON.stringify(subscribedKeys));
            } catch (error) {}
        }
        subscribedKeys = [];
    };

    Object.keys(state).forEach(render);
    seedPreview();
    renderContextCard();
    subscribeToTelemetry();
    startHeartbeat();
})();
