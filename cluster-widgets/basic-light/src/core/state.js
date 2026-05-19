function StateManager(initialState) {
    this._state = {};
    for (var key in initialState) {
        if (initialState.hasOwnProperty(key)) {
            this._state[key] = initialState[key];
        }
    }
    this._listeners = new Map();
}

StateManager.prototype.get = function (key) {
    return this._state[key];
};

StateManager.prototype.set = function (key, value) {
    if (this._state[key] !== value) {
        this._state[key] = value;
        this._notifyListeners(key, value);
    }
};

StateManager.prototype.getState = function () {
    var newState = {};
    for (var key in this._state) {
        if (this._state.hasOwnProperty(key)) {
            newState[key] = this._state[key];
        }
    }
    return newState;
};

StateManager.prototype.subscribe = function (key, callback) {
    if (!this._listeners.has(key)) {
        this._listeners.set(key, new Set());
    }
    this._listeners.get(key).add(callback);

    // Return unsubscribe function
    var self = this;
    return function () {
        var listeners = self._listeners.get(key);
        if (listeners) {
            listeners.delete(callback);
        }
    };
};

StateManager.prototype._notifyListeners = function (key, value) {
    var listeners = this._listeners.get(key);
    if (listeners) {
        listeners.forEach(function (callback) {
            try {
                callback(value, key);
            } catch (error) {
                console.error('Error in state listener:', error);
            }
        });
    }
};

var stateManager = new StateManager({
    // Main Menu state
    screen: 'main_menu',
    cardId: 1,
    tempUnit: '°C',
    focusedMenuItem: 'option_4',
    espStatus: 'ON',
    drivingMode: 'Normal',
    steerMode: 'Conforto',
    evMode: 'HEV',

    // Ac screen states
    focusArea: 'fan',
    temp: '--',
    fan: '-',
    power: 0,
    auto: 0,
    recycle: 0,
    aion: 0,
    maxauto: 0,
    impulseauto: 0, // TODO: for future implementation of AC automated control, should replace maxauto
    targetTemp: '--',
    outside_temp: '--',
    inside_temp: '--',

    // Regen screen states
    regenMode: 'Normal',
    lastRegenValue: 0,
    onepedal: false,

    // Graph values
    currentGraph: 'evConsumption',
    evPowerFactor: 0,
    evPowerKw: 0,
    instantEVConsumption: 0,
    gasConsumption: 0.0,
    gasConsumptionMetric: 'Km/l',
    gasConsumptionIdle: 0.0,
    gasConsumptionMetricIdle: 'L/hora',
    gasConsumptionMode: 'Running',
    carSpeed: 0,
    engineRPM: 0,
    evPowerKwAvg: 0,

    // Template states
    display: 'Normal', //Display mode, should be either Normal, Reduzido or Clean
    displayFocus: 'sel_template',
    appInDash: false,
    clusterEnabled: true,
    brightness: 100,
    fuelRange: 0,
    fuelPercent: 0,
    batteryRange: 0,
    batteryPercent: 0,
    clockTime: '--:--',
    gearState: 'P',
    evModeLabel: 'NORMAL',

    // Revision Info / Odometer
    odometer: 0,
    nextRevisionKm: 0,
    nextRevisionDate: 0,
    enableRevisionWarning: false,
    enableOdometer: false,
    warningActive: false,
    bsdLeft: false,
    bsdRight: false,
});

var getState = function (key) { return stateManager.get(key); };
var setState = function (key, value) { stateManager.set(key, value); };
var subscribe = function (key, callback) { return stateManager.subscribe(key, callback); };

var state = new Proxy({}, {
    get: function (target, prop) {
        return stateManager.get(prop);
    },
    set: function (target, prop, value) {
        stateManager.set(prop, value);
        return true;
    }
});

let instantEVEMA = 0;
const EMA_ALPHA = 0.3;

const updateInstantConsumption = () => {
    const power = getState('evPowerKw') || 0;
    const speed = parseFloat(getState('carSpeed')) || 0;

    let consumption = 0;
    const speedThreshold = 10;

    if (speed <= 0) {
        consumption = 0;
        instantEVEMA = 0; // Force immediate 0 to avoid trailing values
    } else {
        const physicalValue = (power * 100) / speed;

        if (speed < speedThreshold) {
            // Smooth blending: w = x^3 * (4 - 3x) where x = speed / threshold
            // This ensures O(s^3) fade at 0 and zero-derivative match at threshold.
            const x = speed / speedThreshold;
            const weight = Math.pow(x, 3) * (4 - 3 * x);
            consumption = physicalValue * weight;
        } else {
            consumption = physicalValue;
        }
    }

    // Noise reduction (EMA)
    instantEVEMA = (instantEVEMA * (1 - EMA_ALPHA)) + (consumption * EMA_ALPHA);

    setState('instantEVConsumption', instantEVEMA);
};

subscribe('evPowerKw', updateInstantConsumption);
subscribe('carSpeed', updateInstantConsumption);

// Synchronize driving mode with top bar label
subscribe('drivingMode', (val) => {
    setState('evModeLabel', val.toUpperCase());
});

export { stateManager, getState, setState, subscribe, state };
