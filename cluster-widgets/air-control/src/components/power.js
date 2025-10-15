import { stateManager, subscribe, setState } from '../state.js';
import { createFocusElementWithChildren } from './focusElement.js';
import { button as createButton, img, span } from '../utils/createElement.js';

// Power icon SVG as data URL
var powerIconSvg = 'data:image/svg+xml;base64,' + btoa('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 640"><path fill="#ffffff" d="M352 64C352 46.3 337.7 32 320 32C302.3 32 288 46.3 288 64L288 320C288 337.7 302.3 352 320 352C337.7 352 352 337.7 352 320L352 64zM210.3 162.4C224.8 152.3 228.3 132.3 218.2 117.8C208.1 103.3 188.1 99.8 173.6 109.9C107.4 156.1 64 233 64 320C64 461.4 178.6 576 320 576C461.4 576 576 461.4 576 320C576 233 532.6 156.1 466.3 109.9C451.8 99.8 431.9 103.3 421.7 117.8C411.5 132.3 415.1 152.2 429.6 162.4C479.4 197.2 511.9 254.8 511.9 320C511.9 426 425.9 512 319.9 512C213.9 512 128 426 128 320C128 254.8 160.5 197.1 210.3 162.4z"/></svg>');

function getButtonColor() {
    const isFocused = stateManager.get('focusArea') === 'power';
    const isOn = stateManager.get('power') === 1;
    
    if (!isOn) {
        return '#6b7280'; // Gray color when OFF
    }
    
    return isFocused ? '#1d4ed8' : '#2563eb';
}

export function createPowerElement() {
    var textSpan = span({
        className: 'text-white text-28 font-medium w-96 text-center',
        children: [stateManager.get('power') === 1 ? 'ON' : 'OFF']
    });

    var button = createButton({
        className: 'w-208 h-80 rounded-24 flex-row-center transition-all shadow-default',
        style: {
            backgroundColor: getButtonColor()
        },
        children: [
            img({
                src: powerIconSvg,
                className: 'w-32 h-32'
            }),
            textSpan
        ]
    });

    // Add click handler
    button.addEventListener('click', function() {
        var currentPower = stateManager.get('power');
        setState('power', currentPower === 1 ? 0 : 1);
    });

    // Create the main container
    var focusArea = createFocusElementWithChildren({
        focused: stateManager.get('focusArea') === 'power',
        styles: {
            width: '208px',
            height: '50px',
            marginBottom: '32px'
        },
        children: [
            button
        ]
    });

    // Subscribe to state changes
    var unsubscribeFocus = subscribe('focusArea', function(newFocusArea) {
        var isFocused = newFocusArea === 'power';
        
        // Update focus area styling
        if (isFocused) {
            focusArea.classList.add('transition-all', 'focus-active');
            focusArea.classList.remove('focus-inactive');
        } else {
            focusArea.classList.add('focus-inactive');
            focusArea.classList.remove('transition-all', 'focus-active');
        }
        
        // Update button styling
        button.style.backgroundColor = getButtonColor();
        if (isFocused) {
            button.classList.remove('shadow-default');
            button.classList.add('glow-effect', 'power-focus');
        } else {
            button.classList.remove('glow-effect', 'power-focus');
            button.classList.add('shadow-default');
        }
    });

    var unsubscribePower = subscribe('power', function(newPower) {
        textSpan.textContent = newPower === 1 ? 'ON' : 'OFF';
        button.style.backgroundColor = getButtonColor();
    });

    // Add cleanup method to the element
    focusArea.cleanup = function() {
        unsubscribeFocus();
        unsubscribePower();
        button.removeEventListener('click', button.clickHandler);
    };

    return focusArea;
}
