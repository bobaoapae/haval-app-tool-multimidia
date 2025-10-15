// Create a reactive state manager
function StateManager(initialState) {
    this._state = {};
    for (var key in initialState) {
        if (initialState.hasOwnProperty(key)) {
            this._state[key] = initialState[key];
        }
    }
    this._listeners = new Map();
}

StateManager.prototype.get = function(key) {
    return this._state[key];
};

StateManager.prototype.set = function(key, value) {
    if (this._state[key] !== value) {
        this._state[key] = value;
        this._notifyListeners(key, value);
    }
};

StateManager.prototype.getState = function() {
    var newState = {};
    for (var key in this._state) {
        if (this._state.hasOwnProperty(key)) {
            newState[key] = this._state[key];
        }
    }
    return newState;
};

StateManager.prototype.subscribe = function(key, callback) {
    if (!this._listeners.has(key)) {
        this._listeners.set(key, new Set());
    }
    this._listeners.get(key).add(callback);
    
    // Return unsubscribe function
    var self = this;
    return function() {
        var listeners = self._listeners.get(key);
        if (listeners) {
            listeners.delete(callback);
        }
    };
};

StateManager.prototype._notifyListeners = function(key, value) {
    var listeners = this._listeners.get(key);
    if (listeners) {
        listeners.forEach(function(callback) {
            try {
                callback(value, key);
            } catch (error) {
                console.error('Error in state listener:', error);
            }
        });
    }
};

// Create the state instance
var stateManager = new StateManager({
    focusArea: 'power',
    temp: 25,
    fan: 1,
    power: 0,
    auto: 0,
    recycle: 0,
    aion: 0,
});

// Convenience functions for easier usage
var getState = function(key) { return stateManager.get(key); };
var setState = function(key, value) { stateManager.set(key, value); };
var subscribe = function(key, callback) { return stateManager.subscribe(key, callback); };

// For backward compatibility, export the state object with getters/setters
var state = new Proxy({}, {
    get: function(target, prop) {
        return stateManager.get(prop);
    },
    set: function(target, prop, value) {
        stateManager.set(prop, value);
        return true;
    }
});

// Export all functions and objects
export { stateManager, getState, setState, subscribe, state };
