(function () {
    "use strict";

    // Card ownership stays with the host. This component owns only each card's
    // internal navigation; it never subscribes or replaces the public bridge hooks.
    var keyFields = {
        "car.basic.vehicle_speed": "speed",
        "car.basic.total_odometer": "odometer",
        "car.basic.inside_temp": "insideTemp",
        "car.basic.outside_temp": "outsideTemp",
        "car.configure.default_temp_unit": "tempUnit",
        "car.basic.remain_fuel_percentage": "fuel",
        "car.ev_info.cur_battery_power_percentage": "battery",
        "car.ev_info.fuel_mode_remain_odometer": "fuelRange",
        "car.ev_info.electric_mode_remain_odometer": "batteryRange",
        "car.ev_info.power_battery_voltage": "voltage",
        "car.ev_info.cur_charge_current": "current",
        "car.basic.cur_journey_odometer": "tripADistance",
        "car.basic.cur_journey_drivetime": "tripATime",
        "car.basic.cur_journey_avg_fuel_consume": "tripAConsumption",
        "car.basic.avg_vehicle_speed_since_startup": "tripASpeed",
        "car.basic.accumulated_odometer": "tripBDistance",
        "car.basic.accumulated_drivetime": "tripBTime",
        "car.basic.avg_fuel_consumption": "tripBConsumption",
        "car.basic.vehicle_speed_since_reset": "tripBSpeed",
        "car.basic.tire_pressure_front_left": "tireFL",
        "car.basic.tire_pressure_front_right": "tireFR",
        "car.basic.tire_pressure_rear_left": "tireRL",
        "car.basic.tire_pressure_rear_right": "tireRR",
        "car.drive_setting.drive_mode": "driveMode",
        "car.ev_setting.power_model_config": "propulsionMode",
        "car.drive_setting.steering_wheel_assist_mode": "steerMode",
        "car.ev_setting.energy_recovery_level": "regenMode",
        "car.drive_setting.esp_enable": "esp",
        "car.hvac.power_mode": "acPower",
        "car.hvac.fan_speed": "acFan",
        "car.hvac.driver_temperature": "acTemp",
        "car.hvac.cycle_mode": "acRecycle",
        "car.hvac.auto_enable": "acAuto"
    };
    var aliases = {
        carSpeed: "speed", odometer: "odometer", inside_temp: "insideTemp", outside_temp: "outsideTemp",
        tempUnit: "tempUnit", fuelPercent: "fuel", batteryPercent: "battery", fuelRange: "fuelRange",
        batteryRange: "batteryRange", drivingMode: "driveMode", evMode: "propulsionMode",
        steerMode: "steerMode", regenMode: "regenMode", espStatus: "esp",
        tripOdometer: "tripBDistance", tripDriveTime: "tripBTime", tripAvgConsumption: "tripBConsumption",
        tripAvgSpeed: "tripBSpeed", temp: "acTemp", fan: "acFan", power: "acPower", recycle: "acRecycle", auto: "acAuto"
    };
    var labels = {
        driveMode: { 0: "NORMAL", 1: "SPORT", 2: "ECO", 3: "NEVE", 4: "AREIA", 5: "LAMA", 11: "AWD" },
        propulsionMode: { 0: "HEV", 1: "EVP", 3: "EV" },
        steerMode: { 0: "NORMAL", 1: "ESPORTIVA", 2: "CONFORTO" },
        regenMode: { 0: "NORMAL", 1: "ALTO", 2: "BAIXO" },
        esp: { 0: "OFF", 1: "ON" }
    };
    var menuItems = [
        { title: "INFORMAÇÕES", subtitle: "Veículo e pneus", view: "info" },
        { title: "GRÁFICOS", subtitle: "Potência e velocidade", view: "graphs" },
        { title: "AJUSTES", subtitle: "Preferências do veículo", view: "settings" },
        { title: "TRIP A / TRIP B", subtitle: "Dados de viagem", view: "trips" }
    ];
    var settingItems = [
        { title: "CONDUÇÃO", field: "driveMode", key: "car.drive_setting.drive_mode", values: [0, 2, 1] },
        { title: "PROPULSÃO", field: "propulsionMode", key: "car.ev_setting.power_model_config", values: [0, 1, 3] },
        { title: "DIREÇÃO", field: "steerMode", key: "car.drive_setting.steering_wheel_assist_mode", values: [2, 0, 1] },
        { title: "REGENERAÇÃO", field: "regenMode", key: "car.ev_setting.energy_recovery_level", values: [2, 0, 1] },
        { title: "ESTABILIDADE", field: "esp", key: "car.drive_setting.esp_enable", values: [1, 0] },
        { title: "DISPLAY", field: "displayMode", values: ["Contour", "Vector"] }
    ];

    function numberOrNull(value) {
        if (typeof value !== "number" && typeof value !== "string") return null;
        if (typeof value === "string" && !/^[+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?$/i.test(value.trim())) return null;
        var number = Number(value);
        return Number.isFinite(number) ? number : null;
    }

    function normalize(field, value) {
        if (labels[field]) {
            var raw = numberOrNull(value);
            if (raw !== null && Object.prototype.hasOwnProperty.call(labels[field], raw)) return raw;
            var label = typeof value === "string" ? value.trim().toUpperCase() : "";
            if (field === "propulsionMode" && /^HEV (?:INTELIGENTE|PRIORIDADE \d+%)$/.test(label)) return 0;
            if (field === "esp" && (label === "ATIVO" || label === "INATIVO")) return label === "ATIVO" ? 1 : 0;
            var found = Object.keys(labels[field]).filter(function (key) { return labels[field][key] === label; });
            return found.length ? Number(found[0]) : null;
        }
        if (field === "tempUnit" && (value === "°C" || value === "°F")) return value === "°F" ? 1 : 0;
        var numeric = numberOrNull(value);
        if (numeric === null) return null;
        if (field === "insideTemp" || field === "outsideTemp") return numeric === -1 || numeric === 255 ? null : numeric;
        if (field === "acTemp") return numeric >= 16 && numeric <= 32 ? numeric : null;
        if (field === "acFan") return Number.isInteger(numeric) && numeric >= 0 && numeric <= 7 ? numeric : null;
        if (field === "fuel" || field === "battery") return numeric >= 0 && numeric <= 100 ? numeric : null;
        if (field === "acPower" || field === "acAuto" || field === "acRecycle" || field === "tempUnit") return numeric === 0 || numeric === 1 ? numeric : null;
        if (field === "current") return numeric;
        return numeric >= 0 ? numeric : null;
    }

    function create(options) {
        var root = options.root;
        var mount = options.mount;
        var display = options.display || null;
        var disposed = false;
        var frame = null;
        var feedbackTimer = null;
        var feedback = "";
        var awaiting = null;
        var card = 0;
        var menuIndex = 0;
        var detail = false;
        var infoIndex = 0;
        var graphIndex = 0;
        var settingIndex = 0;
        var tripIndex = 0;
        var acFocus = "fan";
        var lastKeyAt = -Infinity;
        var seen = Object.create(null);
        var state = Object.create(null);
        var history = { speed: [], power: [] };
        var sampledAt = { speed: -Infinity, power: -Infinity };
        Object.keys(keyFields).forEach(function (key) { state[keyFields[key]] = null; });

        mount.classList.add("apex-menus");
        mount.hidden = true;
        mount.setAttribute("aria-label", "Menus do veículo");
        mount.innerHTML = [
            '<nav class="apex-menu-cards" aria-label="Card ativo"><span data-apex-card="0">PAINEL</span><i></i><span data-apex-card="1">MENU</span><i></i><span data-apex-card="3">AC</span></nav>',
            '<header class="apex-menu-heading"><h2 id="apex-menu-title">MENU PRINCIPAL</h2><span id="apex-menu-counter"></span></header>',
            '<div class="apex-menu-body">',
            '<section data-apex-panel="menu" class="apex-menu-window">',
            menuItems.map(function (item, index) {
                return '<button type="button" class="apex-menu-option" data-apex-menu="' + index + '"><small class="apex-menu-index">0' + (index + 1) + '</small><span><strong>' + item.title + '</strong><small>' + item.subtitle + '</small></span><b aria-hidden="true">›</b></button>';
            }).join(""), '</section>',
            '<section data-apex-panel="settings" class="apex-menu-window" hidden>',
            settingItems.map(function (item, index) {
                return '<button type="button" class="apex-menu-option apex-menu-setting" data-apex-setting="' + index + '"><span><small>' + item.title + '</small><strong id="apex-menu-setting-' + item.field + '">--</strong></span><b aria-hidden="true">›</b></button>';
            }).join(""), '</section>',
            '<section data-apex-panel="info" hidden><nav class="apex-menu-tabs" aria-label="Informações"><button type="button" data-apex-info="0">GERAL</button><button type="button" data-apex-info="1">PNEUS</button></nav>',
            '<div id="apex-menu-info-general"><div class="apex-menu-stat"><small>ODÔMETRO</small><strong id="apex-menu-info-odometer">--</strong></div><div class="apex-menu-stat"><small>TEMP. INTERNA · EXTERNA</small><strong id="apex-menu-info-temps">--</strong></div><div class="apex-menu-energy"><span><small>COMBUSTÍVEL</small><strong id="apex-menu-info-fuel">--</strong><em id="apex-menu-info-fuel-range">--</em></span><span><small>BATERIA</small><strong id="apex-menu-info-battery">--</strong><em id="apex-menu-info-battery-range">--</em></span></div></div>',
            '<div id="apex-menu-info-tires" hidden><div class="apex-menu-tire-caption">PRESSÃO <span>PSI</span></div><div class="apex-menu-tires">',
            ["FL", "FR", "RL", "RR"].map(function (position, index) {
                return '<div class="apex-menu-tire"><small>' + ["DIANT. ESQ.", "DIANT. DIR.", "TRAS. ESQ.", "TRAS. DIR."][index] + '</small><strong id="apex-menu-tire-' + position + '">--</strong></div>';
            }).join(""), '</div></div></section>',
            '<section data-apex-panel="graphs" hidden><nav class="apex-menu-tabs" aria-label="Gráfico"><button type="button" data-apex-graph="0">POTÊNCIA</button><button type="button" data-apex-graph="1">VELOCIDADE</button></nav><div class="apex-menu-graph-reading"><strong id="apex-menu-graph-value">--</strong><span id="apex-menu-graph-unit">kW</span></div><div id="apex-menu-graph-bars" class="apex-menu-graph-bars" aria-hidden="true">',
            Array(19).join('<i></i>'), '</div><div class="apex-menu-graph-scale"><span>HISTÓRICO</span><span id="apex-menu-graph-range">−100 / 100 kW</span></div></section>',
            '<section data-apex-panel="trips" hidden><nav class="apex-menu-tabs" aria-label="Viagem"><button type="button" data-apex-trip="0">TRIP A</button><button type="button" data-apex-trip="1">TRIP B</button></nav><div class="apex-menu-trip-distance"><strong id="apex-menu-trip-distance">--</strong><span>km</span></div><dl class="apex-menu-trip-metrics"><div><dt>TEMPO</dt><dd id="apex-menu-trip-time">--</dd></div><div><dt>VELOC. MÉDIA</dt><dd id="apex-menu-trip-speed">--</dd></div><div><dt>CONSUMO</dt><dd id="apex-menu-trip-consumption">--</dd></div></dl><small id="apex-menu-trip-reset" class="apex-menu-trip-reset">REINÍCIO AUTOMÁTICO</small></section>',
            '<section data-apex-panel="ac" hidden><button type="button" class="apex-menu-ac-control" data-apex-ac="temp"><span><small>TEMPERATURA</small><span class="apex-menu-temperature-rail" aria-hidden="true"><i id="apex-menu-ac-temp-fill"></i></span></span><strong id="apex-menu-ac-temp">--</strong></button>',
            '<button type="button" class="apex-menu-ac-control" data-apex-ac="fan"><span><small>VENTILAÇÃO</small><span id="apex-menu-ac-fan-level" class="apex-menu-fan-level" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i><i></i></span></span><strong id="apex-menu-ac-fan">--</strong></button>',
            '<div class="apex-menu-ac-status"><span><small>SISTEMA</small><strong id="apex-menu-ac-power">--</strong></span><span><small>MODO</small><strong id="apex-menu-ac-auto">--</strong></span><span><small>ENTRADA</small><strong id="apex-menu-ac-recycle">--</strong></span></div>',
            '<div class="apex-menu-ac-hold"><span>Segure <b>OK</b> · Auto</span><span>Segure <b>↩</b> · Recircular</span></div></section></div>',
            '<footer class="apex-menu-footer"><span id="apex-menu-hint"></span><span id="apex-menu-feedback" role="status" aria-live="polite"></span></footer>'
        ].join("");

        var elements = Object.create(null);
        Array.prototype.forEach.call(mount.querySelectorAll("[id]"), function (node) { elements[node.id.replace("apex-menu-", "")] = node; });
        var panels = Array.prototype.slice.call(mount.querySelectorAll("[data-apex-panel]"));
        var menuRows = Array.prototype.slice.call(mount.querySelectorAll("[data-apex-menu]"));
        var settingRows = Array.prototype.slice.call(mount.querySelectorAll("[data-apex-setting]"));
        var graphBars = Array.prototype.slice.call(elements["graph-bars"].children);
        var fanBars = Array.prototype.slice.call(elements["ac-fan-level"].children);
        var tabs = {};
        ["info", "graph", "trip", "ac", "card"].forEach(function (name) { tabs[name] = Array.prototype.slice.call(mount.querySelectorAll("[data-apex-" + name + "]")); });

        function text(id, value) {
            var next = String(value);
            if (elements[id].textContent !== next) elements[id].textContent = next;
        }

        function number(value, decimals) {
            return value === null || !Number.isFinite(value) ? "--" : value.toLocaleString("pt-BR", { minimumFractionDigits: decimals || 0, maximumFractionDigits: decimals || 0 });
        }

        function temperature(value) {
            return value === null ? "--" : number(value, value % 1 ? 1 : 0) + (state.tempUnit === 1 ? "°F" : "°C");
        }

        function power() {
            if (state.voltage === null || state.current === null) return null;
            var value = state.voltage * state.current / 1000;
            return Number.isFinite(value) ? value : null;
        }

        function selectTabs(name, selected) {
            tabs[name].forEach(function (node) {
                var active = node.getAttribute("data-apex-" + name) === String(selected);
                node.classList.toggle("is-selected", active);
                node.setAttribute(name === "card" ? "aria-current" : "aria-pressed", String(active));
            });
        }

        function slidingRows(rows, selected) {
            var start = Math.max(0, Math.min(rows.length - 3, selected - 1));
            rows.forEach(function (node, index) {
                node.hidden = index < start || index >= start + 3;
                node.classList.toggle("is-focused", index === selected);
                node.setAttribute("aria-current", String(index === selected));
            });
        }

        function renderInfo() {
            selectTabs("info", infoIndex);
            elements["info-general"].hidden = infoIndex !== 0;
            elements["info-tires"].hidden = infoIndex !== 1;
            text("info-odometer", state.odometer === null ? "--" : number(state.odometer) + " km");
            text("info-temps", temperature(state.insideTemp) + " · " + temperature(state.outsideTemp));
            text("info-fuel", state.fuel === null ? "--" : number(Math.min(100, state.fuel)) + "%");
            text("info-battery", state.battery === null ? "--" : number(Math.min(100, state.battery)) + "%");
            text("info-fuel-range", state.fuelRange === null ? "--" : number(state.fuelRange) + " km");
            text("info-battery-range", state.batteryRange === null ? "--" : number(state.batteryRange) + " km");
            ["FL", "FR", "RL", "RR"].forEach(function (position) {
                var value = state["tire" + position];
                text("tire-" + position, value === null || value <= 0 ? "--" : number(value / 6.89476, 1));
            });
        }

        function renderGraph() {
            selectTabs("graph", graphIndex);
            var metric = graphIndex === 0 ? "power" : "speed";
            var value = graphIndex === 0 ? power() : state.speed;
            text("graph-value", number(value, graphIndex === 0 ? 1 : 0));
            text("graph-unit", graphIndex === 0 ? "kW" : "km/h");
            text("graph-range", graphIndex === 0 ? "−100 / 100 kW" : "0 / 180 km/h");
            var samples = history[metric];
            graphBars.forEach(function (bar, index) {
                var sampleIndex = index - (18 - samples.length);
                var sample = sampleIndex >= 0 ? samples[sampleIndex] : null;
                var ratio = sample === null ? 0 : Math.min(1, Math.abs(sample) / (metric === "speed" ? 180 : 100));
                var height = Math.max(2, ratio * 100).toFixed(1) + "%";
                if (bar.style.height !== height) bar.style.height = height;
                bar.classList.toggle("is-empty", sample === null);
                bar.classList.toggle("is-regen", metric === "power" && sample !== null && sample < 0);
            });
        }

        function renderTrips() {
            selectTabs("trip", tripIndex);
            var prefix = tripIndex === 0 ? "tripA" : "tripB";
            var minutes = state[prefix + "Time"];
            var duration = minutes === null ? null : Math.round(minutes);
            var consumption = state[prefix + "Consumption"];
            text("trip-distance", number(state[prefix + "Distance"], 1));
            text("trip-time", duration === null ? "--" : Math.floor(duration / 60) + "h " + String(duration % 60).padStart(2, "0") + "min");
            text("trip-speed", state[prefix + "Speed"] === null ? "--" : number(state[prefix + "Speed"], 1) + " km/h");
            text("trip-consumption", consumption === null || (consumption > 0 && !Number.isFinite(100 / consumption)) ? "--" : consumption === 0 ? "0,0 L/100 km" : number(100 / consumption, 1) + " km/L");
            text("trip-reset", tripIndex === 0 ? "REINÍCIO AUTOMÁTICO" : "SEGURE OK PARA ZERAR");
        }

        function renderAc() {
            selectTabs("ac", acFocus);
            var temp = state.acTemp;
            text("ac-temp", temp === null ? "--" : temp <= 16 ? "LO" : temp >= 32 ? "HI" : temperature(temp));
            text("ac-fan", state.acPower === null ? "--" : state.acPower === 0 ? "OFF" : number(state.acFan));
            text("ac-power", state.acPower === null ? "--" : state.acPower === 1 ? "LIGADO" : "DESL.");
            text("ac-auto", state.acAuto === null ? "--" : state.acAuto === 1 ? "AUTO" : "MANUAL");
            text("ac-recycle", state.acRecycle === null ? "--" : state.acRecycle === 1 ? "RECIRC." : "EXTERNO");
            ["power", "auto", "recycle"].forEach(function (name) {
                var field = "ac" + name.charAt(0).toUpperCase() + name.slice(1);
                elements["ac-" + name].classList.toggle("is-on", state[field] === 1);
            });
            var fill = "scaleX(" + (temp === null ? 0 : (temp - 16) / 16) + ")";
            if (elements["ac-temp-fill"].style.transform !== fill) elements["ac-temp-fill"].style.transform = fill;
            fanBars.forEach(function (bar, index) { bar.classList.toggle("is-on", state.acPower === 1 && state.acFan !== null && index < state.acFan); });
        }

        function render() {
            frame = null;
            if (disposed || (card !== 1 && card !== 3)) return;
            var view = card === 3 ? "ac" : detail ? menuItems[menuIndex].view : "menu";
            mount.setAttribute("data-view", view);
            selectTabs("card", card);
            panels.forEach(function (panel) { panel.hidden = panel.getAttribute("data-apex-panel") !== view; });
            text("title", card === 3 ? "CLIMATIZAÇÃO" : detail ? menuItems[menuIndex].title : "MENU PRINCIPAL");
            var index = view === "settings" ? settingIndex : menuIndex;
            text("counter", view === "menu" || view === "settings" ? (index + 1) + "/" + (view === "menu" ? menuItems.length : settingItems.length) : "");
            text("hint", view === "ac" ? "↑ ↓ ajustar · OK alternar" : view === "menu" ? "↑ ↓ navegar · OK abrir" : view === "settings" ? "↑ ↓ escolher · OK alterar · ↩" : "↑ ↓ alternar · ↩ voltar");
            text("feedback", feedback);
            elements.hint.hidden = Boolean(feedback);
            elements.feedback.hidden = !feedback;
            if (view === "menu") slidingRows(menuRows, menuIndex);
            if (view === "settings") {
                slidingRows(settingRows, settingIndex);
                settingItems.forEach(function (item) {
                    var value = item.field === "displayMode" ? (display ? display.getMode() : "--") : (state[item.field] === null ? "--" : labels[item.field][state[item.field]]);
                    text("setting-" + item.field, value);
                });
            }
            if (view === "info") renderInfo();
            if (view === "graphs") renderGraph();
            if (view === "trips") renderTrips();
            if (view === "ac") renderAc();
        }

        function schedule() {
            if (!disposed && (card === 1 || card === 3) && frame === null) frame = window.requestAnimationFrame(render);
        }

        function status(message, expected) {
            feedback = message;
            awaiting = expected || null;
            if (feedbackTimer !== null) window.clearTimeout(feedbackTimer);
            feedbackTimer = window.setTimeout(function () {
                feedbackTimer = null;
                feedback = awaiting ? "AGUARDANDO DADOS" : "";
                awaiting = null;
                schedule();
            }, 2600);
            schedule();
        }

        function send(writes, action, expected) {
            var bridge = window.Android;
            if (!bridge || (writes.length && typeof bridge.updateCarData !== "function") || (action && typeof bridge.triggerSystemAction !== "function")) {
                status("AÇÃO INDISPONÍVEL");
                return;
            }
            // Mark pending before dispatch because a simulator can synchronously
            // publish the acknowledgement. Production always waits for telemetry.
            status(action === "RESET_DRIVE_INFO" ? "ZERAGEM SOLICITADA" : "AGUARDANDO RETORNO", expected);
            try {
                // CANCEL_MAX_AC restores the prior HVAC state, so it must run
                // before the requested AUTO value is written.
                if (action) bridge.triggerSystemAction(action);
                writes.forEach(function (write) { bridge.updateCarData(write[0], String(write[1])); });
            } catch (error) {
                status("AÇÃO INDISPONÍVEL");
            }
        }

        function known(fields) {
            var available = fields.every(function (field) { return state[field] !== null; });
            if (!available) status("AGUARDANDO DADOS");
            return available;
        }

        function changeSetting() {
            var item = settingItems[settingIndex];
            if (item.field === "displayMode") {
                if (!display) { status("AÇÃO INDISPONÍVEL"); return; }
                var nextMode = item.values[(item.values.indexOf(display.getMode()) + 1) % item.values.length];
                display.setMode(nextMode);
                schedule();
                return;
            }
            if (!known([item.field])) return;
            var next = item.values[(item.values.indexOf(state[item.field]) + 1) % item.values.length];
            var expected = {};
            expected[item.field] = next;
            send([[item.key, next]], null, expected);
        }

        function acKey(key) {
            if (key === "ENTER") { acFocus = acFocus === "fan" ? "temp" : "fan"; schedule(); return; }
            if (key === "BACK_LONG") {
                if (known(["acRecycle"])) {
                    var recycle = state.acRecycle === 1 ? 0 : 1;
                    send([["car.hvac.cycle_mode", recycle]], null, { acRecycle: recycle });
                }
                return;
            }
            if (key === "ENTER_LONG") {
                if (known(["acAuto"])) {
                    var auto = state.acAuto === 1 ? 0 : 1;
                    send([["car.hvac.auto_enable", auto]], "CANCEL_MAX_AC", { acAuto: auto });
                }
                return;
            }
            if (key !== "UP" && key !== "DOWN") return;
            if (acFocus === "temp") {
                if (!known(["acTemp"])) return;
                var temp = Math.max(16, Math.min(32, Math.round((state.acTemp + (key === "UP" ? 0.5 : -0.5)) * 2) / 2));
                if (temp !== state.acTemp) send([["car.hvac.driver_temperature", temp.toFixed(1)]], null, { acTemp: temp });
                return;
            }
            if (!known(["acFan", "acPower"])) return;
            var fan = state.acPower === 0 ? 0 : state.acFan;
            var nextFan = Math.max(0, Math.min(7, fan + (key === "UP" ? 1 : -1)));
            if (fan === nextFan) return;
            var writes = [];
            if (nextFan > 0 && state.acPower === 0) writes.push(["car.hvac.power_mode", 1]);
            if (nextFan === 0) writes.push(["car.hvac.power_mode", 0]);
            writes.push(["car.hvac.fan_speed", nextFan]);
            send(writes, null, { acFan: nextFan });
        }

        function mainKey(key) {
            var step = key === "UP" ? -1 : key === "DOWN" ? 1 : 0;
            if (!detail) {
                if (key === "ENTER") detail = true;
                if (step) menuIndex = (menuIndex + step + menuItems.length) % menuItems.length;
                schedule();
                return;
            }
            if (key === "BACK") { detail = false; schedule(); return; }
            var view = menuItems[menuIndex].view;
            if (view === "info" && step) infoIndex = 1 - infoIndex;
            if (view === "graphs" && step) graphIndex = 1 - graphIndex;
            if (view === "settings") {
                if (step) settingIndex = (settingIndex + step + settingItems.length) % settingItems.length;
                if (key === "ENTER") changeSetting();
            }
            if (view === "trips") {
                if (step) tripIndex = 1 - tripIndex;
                if (key === "ENTER_LONG" && tripIndex === 1) send([], "RESET_DRIVE_INFO", { tripBDistance: 0 });
            }
            schedule();
        }

        function onKeyEvent(keyName) {
            if (disposed || (card !== 1 && card !== 3)) return;
            var key = typeof keyName === "string" ? keyName.toUpperCase() : "";
            if (["UP", "DOWN", "ENTER", "BACK", "ENTER_LONG", "BACK_LONG"].indexOf(key) < 0) return;
            var now = Date.now();
            if (now - lastKeyAt < 50) return;
            lastKeyAt = now;
            if (card === 3) acKey(key); else mainKey(key);
        }

        function onCardChanged(value) {
            var next = numberOrNull(value);
            if (disposed || [0, 1, 3].indexOf(next) < 0) return;
            if (next === 1 && card !== 1) detail = false;
            card = next;
            mount.hidden = card !== 1 && card !== 3;
            root.classList.toggle("menu-open", !mount.hidden);
            root.classList.toggle("card-main", card === 1);
            root.classList.toggle("card-ac", card === 3);
            root.classList.toggle("card-native", card === 0);
            root.setAttribute("data-card-id", String(card));
            feedback = "";
            awaiting = null;
            if (feedbackTimer !== null) { window.clearTimeout(feedbackTimer); feedbackTimer = null; }
            schedule();
        }

        function sample(metric, value) {
            if (value === null) return;
            var now = Date.now();
            if (now - sampledAt[metric] < 250 && history[metric].length) history[metric][history[metric].length - 1] = value;
            else {
                sampledAt[metric] = now;
                history[metric].push(value);
                if (history[metric].length > 18) history[metric].shift();
            }
        }

        function update(key, value, allowAliases) {
            if (disposed) return;
            var field = keyFields[key] || (allowAliases ? aliases[key] : null);
            if (!field) return;
            if (keyFields[key]) seen[field] = true;
            else if (seen[field]) return;
            var next = normalize(field, value);
            if (awaiting && Object.prototype.hasOwnProperty.call(awaiting, field) && awaiting[field] === next) {
                delete awaiting[field];
                if (!Object.keys(awaiting).length) {
                    awaiting = null;
                    feedback = "";
                    if (feedbackTimer !== null) { window.clearTimeout(feedbackTimer); feedbackTimer = null; }
                    schedule();
                }
            }
            if (state[field] === next) return;
            state[field] = next;
            if (field === "speed") sample("speed", next);
            if (field === "voltage" || field === "current") sample("power", power());
            schedule();
        }

        function onClick(event) {
            if (disposed || mount.hidden) return;
            var button = event.target.closest("button");
            if (!button || !mount.contains(button)) return;
            if (card === 1) {
                if (button.hasAttribute("data-apex-menu")) { menuIndex = Number(button.getAttribute("data-apex-menu")); detail = true; }
                if (button.hasAttribute("data-apex-setting")) { settingIndex = Number(button.getAttribute("data-apex-setting")); changeSetting(); }
                ["info", "graph", "trip"].forEach(function (name) {
                    if (!button.hasAttribute("data-apex-" + name)) return;
                    var index = Number(button.getAttribute("data-apex-" + name));
                    if (name === "info") infoIndex = index;
                    if (name === "graph") graphIndex = index;
                    if (name === "trip") tripIndex = index;
                });
            }
            if (card === 3 && button.hasAttribute("data-apex-ac")) acFocus = button.getAttribute("data-apex-ac");
            schedule();
        }

        function cleanup() {
            if (disposed) return;
            disposed = true;
            if (frame !== null) window.cancelAnimationFrame(frame);
            if (feedbackTimer !== null) window.clearTimeout(feedbackTimer);
            frame = null;
            feedbackTimer = null;
            awaiting = null;
            history.speed.length = 0;
            history.power.length = 0;
            mount.removeEventListener("click", onClick);
            window.removeEventListener("apex-display-change", schedule);
            mount.hidden = true;
            root.classList.remove("menu-open", "card-main", "card-ac");
        }

        mount.addEventListener("click", onClick);
        window.addEventListener("apex-display-change", schedule);
        onCardChanged(0);
        return { keys: Object.keys(keyFields), update: update, onCardChanged: onCardChanged, onKeyEvent: onKeyEvent, cleanup: cleanup };
    }

    window.ApexMenus = { create: create };
})();
