import { createTemperatureElement } from './temperature.js';
import { createFanElement } from './fan.js';
import { createSeparatorElement } from './separator.js';
import { div } from '../utils/createElement.js';

export function createControlElement() {
    const temperatureElement = createTemperatureElement();
    const fanElement = createFanElement();
    
    var container = div({
        className: 'control-container',
        children: [
            temperatureElement,
            createSeparatorElement(),
            fanElement,
        ],
    });

    // Add cleanup method to the element
    container.cleanup = function() {
        if (temperatureElement.cleanup) {
            temperatureElement.cleanup();
        }
        if (fanElement.cleanup) {
            fanElement.cleanup();
        }
    };

    return container;
}
