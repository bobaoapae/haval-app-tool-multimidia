import { createPowerElement } from './components/power.js';
import { createControlElement } from './components/control.js';
import { createStatusElement } from './components/status.js';
import { setState } from './state.js';

// Create the main container
var main = document.createElement('main');
main.className = 'main-container';

// Create the circular container
var circleContainer = document.createElement('div');
circleContainer.className = 'circle-container';

// Create power element
var powerElement = createPowerElement();

// Create control element
var controlElement = createControlElement();

// Create status element
var statusElement = createStatusElement();

// Assemble the DOM structure
circleContainer.appendChild(powerElement);
circleContainer.appendChild(controlElement);
circleContainer.appendChild(statusElement)
main.appendChild(circleContainer);

// Add to the document
document.getElementById('app').appendChild(main);

// Global control functions
window.control = function(area, value) {
    setState(area, value);
};

window.focus = function(area) {
    setState('focusArea', area);
};

// Cleanup function for when the app is destroyed
window.cleanup = function() {
    if (powerElement.cleanup) {
        powerElement.cleanup();
    }
    if (controlElement.cleanup) {
        controlElement.cleanup();
    }
    if (main.parentNode) {
        main.parentNode.removeChild(main);
    }
};
