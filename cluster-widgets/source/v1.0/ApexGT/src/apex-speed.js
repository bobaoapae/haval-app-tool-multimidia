(function () {
    "use strict";

    // Velocidade mostrada = velocidade do HUD. A chave canônica `car.basic.vehicle_speed` é a do CAN,
    // e o painel físico (e o HUD) mostram um valor calibrado — o app e os temas Default e Minimalist
    // aplicam a mesma fórmula, `getAdjustedSpeed` em shared/car/carDerivations.js (window.ApexShared).
    // As duas preferências GLOBAIS do app entram por cima: "Habilitar ajuste de velocidade" e o fator
    // em %, lidas por getPreference (que cai na chave global quando não há versão do tema) e
    // atualizadas por `app.preferences.*`, o mesmo canal que o Default usa.
    var enableKey = "enableSpeedAdjustment";
    var offsetKey = "speedAdjustmentOffset";
    var enableCanonical = "app.preferences." + enableKey;
    var offsetCanonical = "app.preferences." + offsetKey;

    function booleanValue(value) {
        if (value === true || value === false) return value;
        if (typeof value !== "string") return null;
        var token = value.trim().toLowerCase();
        if (token === "true" || token === "1") return true;
        if (token === "false" || token === "0") return false;
        return null;
    }

    function numberValue(value) {
        if (typeof value !== "number" && typeof value !== "string") return null;
        var parsed = parseFloat(value);
        return Number.isFinite(parsed) ? parsed : null;
    }

    function create() {
        var enabled = false;
        var offset = 0;
        var disposed = false;
        var shared = window.ApexShared || null;

        function readInitial() {
            try {
                var bridge = window.Android;
                if (!bridge || typeof bridge.getPreference !== "function") return;
                var initialEnabled = booleanValue(bridge.getPreference(enableKey, "false"));
                var initialOffset = numberValue(bridge.getPreference(offsetKey, "0"));
                if (initialEnabled !== null) enabled = initialEnabled;
                if (initialOffset !== null) offset = initialOffset;
            } catch (error) {}
        }

        function update(key, value, allowAliases) {
            if (disposed) return false;
            var isEnable = key === enableCanonical || (allowAliases && key === enableKey);
            var isOffset = key === offsetCanonical || (allowAliases && key === offsetKey);
            if (isEnable) {
                var nextEnabled = booleanValue(value);
                if (nextEnabled === null || nextEnabled === enabled) return false;
                enabled = nextEnabled;
                return true;
            }
            if (isOffset) {
                var nextOffset = numberValue(value);
                if (nextOffset === null || nextOffset === offset) return false;
                offset = nextOffset;
                return true;
            }
            return false;
        }

        // Nulo continua nulo: "sem leitura" nunca vira zero calibrado. Só número ou texto numérico
        // entram (mesma régua de apex-gt.js: `Number("")` seria 0).
        function display(rawSpeed) {
            var raw = numberValue(rawSpeed);
            if (raw === null || (typeof rawSpeed === "string" && !/^\s*[+-]?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?\s*$/i.test(rawSpeed))) return null;
            if (!shared || typeof shared.getAdjustedSpeed !== "function") return raw;
            var calibrated = Number(shared.getAdjustedSpeed(raw, enabled, offset));
            return Number.isFinite(calibrated) ? calibrated : raw;
        }

        readInitial();

        return {
            keys: [enableCanonical, offsetCanonical],
            preferenceKeys: [enableCanonical, offsetCanonical],
            update: update,
            display: display,
            isAdjustmentEnabled: function () { return enabled; },
            offsetPercent: function () { return offset; },
            cleanup: function () { disposed = true; }
        };
    }

    window.ApexSpeed = { create: create };
})();
