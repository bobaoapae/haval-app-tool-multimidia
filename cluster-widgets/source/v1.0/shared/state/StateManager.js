export class StateManager {
    constructor(initialState) {
        this._state = {};
        for (var key in initialState) {
            if (initialState.hasOwnProperty(key)) {
                this._state[key] = initialState[key];
            }
        }
        this._listeners = new Map();
    }
    
    get(key) {
        return this._state[key];
    }
    
    set(key, value) {
        if (this._state[key] !== value) {
            this._state[key] = value;
            this._notifyListeners(key, value);
        }
    }
    
    getState() {
        var newState = {};
        for (var key in this._state) {
            if (this._state.hasOwnProperty(key)) {
                newState[key] = this._state[key];
            }
        }
        return newState;
    }
    
    subscribe(key, callback) {
        if (!this._listeners.has(key)) {
            this._listeners.set(key, new Set());
        }
        this._listeners.get(key).add(callback);
        
        var self = this;
        return function () {
            var listeners = self._listeners.get(key);
            if (listeners) {
                listeners.delete(callback);
            }
        };
    }
    
    _notifyListeners(key, value) {
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
    }
}

export function createStateProxy(stateManagerInstance) {
    return new Proxy({}, {
        get: function (target, prop) {
            return stateManagerInstance.get(prop);
        },
        set: function (target, prop, value) {
            stateManagerInstance.set(prop, value);
            return true;
        }
    });
}
