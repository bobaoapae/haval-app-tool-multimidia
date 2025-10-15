import { stateManager, subscribe } from '../state.js';
import { createFocusElementWithChildren } from './focusElement.js';

export function createTemperatureElement() {
    
    var tempDisplay = div({
        className: 'text-36 font-bold',
        children: [
            stateManager.get('temp') + '°C',
        ],
    });
    var focusArea = createFocusElementWithChildren({
        focused: stateManager.get('focusArea') === 'temp',
        styles: {
            width: '128px',
            height: '128px'
        },
        children: [
            div({
                className: 'text-white text-center',
                children: [tempDisplay, div({
                    className: 'text-20 text-gray',
                    children: [
                        'Temp',
                    ],
                })],
            }),
        ]
    });

    // Subscribe to state changes
    var unsubscribeFocus = subscribe('focusArea', function(newFocusArea) {
        var isFocused = newFocusArea === 'temp';
        
        // Apply base styles with browser prefixes
        focusArea.className = 'focus-area focus-area-small';
        
        if (isFocused) {
            focusArea.classList.add('transition-all', 'focus-active');
            focusArea.classList.remove('focus-inactive');
        } else {
            focusArea.classList.add('focus-inactive');
            focusArea.classList.remove('transition-all', 'focus-active');
        }
    });

    var unsubscribeTemp = subscribe('temp', function(newTemp) {
        tempDisplay.textContent = newTemp + '°C';
    });

    // Add cleanup method to the element
    focusArea.cleanup = function() {
        unsubscribeFocus();
        unsubscribeTemp();
    };

    return focusArea;
}
