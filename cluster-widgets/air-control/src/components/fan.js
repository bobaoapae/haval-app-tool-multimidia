import { stateManager, subscribe } from '../state.js';
import { createFocusElementWithChildren } from './focusElement.js';
import { div, img, span } from '../utils/createElement.js';

const ionColor = {
    0: '#60a5fa', // on air
    1: '#018786', // on ion
    2: '#FFFFFF', // fan only
}
// Fan icon SVG as data URL
var fanIconSvg = (ion) => 'data:image/svg+xml;base64,' + btoa('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 640"><path fill="'+ ionColor[ion] +'" d="M352 96C352 113.7 366.3 128 384 128L424 128C437.3 128 448 138.7 448 152C448 165.3 437.3 176 424 176L96 176C78.3 176 64 190.3 64 208C64 225.7 78.3 240 96 240L424 240C472.6 240 512 200.6 512 152C512 103.4 472.6 64 424 64L384 64C366.3 64 352 78.3 352 96zM416 448C416 465.7 430.3 480 448 480L480 480C533 480 576 437 576 384C576 331 533 288 480 288L96 288C78.3 288 64 302.3 64 320C64 337.7 78.3 352 96 352L96 352L480 352C497.7 352 512 366.3 512 384C512 401.7 497.7 416 480 416L448 416C430.3 416 416 430.3 416 448zM192 576L232 576C280.6 576 320 536.6 320 488C320 439.4 280.6 400 232 400L96 400C78.3 400 64 414.3 64 432C64 449.7 78.3 464 96 464L232 464C245.3 464 256 474.7 256 488C256 501.3 245.3 512 232 512L192 512C174.3 512 160 526.3 160 544C160 561.7 174.3 576 192 576z"/></svg>');

const getFanIconColor = () => {
    const powerState = stateManager.get('power');
    const aionState = stateManager.get('aion');
    
    if (powerState === 0) {
        return 2;
    }

    return aionState;
}

export function createFanElement() {
    const speedText = span({
        className: 'w-54 text-center',
        children: [
            stateManager.get('fan'),
        ],
    });
    const icon = img({
        src: fanIconSvg(getFanIconColor()),
        className: 'w-48 h-48',
    });
    
    var focusArea = createFocusElementWithChildren({
        focused: stateManager.get('focusArea') === 'fan',
        styles: {
            width: '128px',
            height: '128px'
        },
        children: [
            div({
                className: 'text-white text-center',
                children: [
                    div({
                        className: 'text-36 font-bold flex-row-center w-94',
                        children: [
                            icon,
                            speedText,
                        ],
                    }),
                    div({
                        className: 'text-20 text-gray',
                        children: [
                            'Fan',
                        ],
                    }),
                ],
            }),
        ],
    });

    // Subscribe to state changes
    var unsubscribeFocus = subscribe('focusArea', function(newFocusArea) {
        var isFocused = newFocusArea === 'fan';
        
        // Update focus area styling
        if (isFocused) {
            focusArea.classList.add('transition-all', 'focus-active');
            focusArea.classList.remove('focus-inactive');
        } else {
            focusArea.classList.add('focus-inactive');
            focusArea.classList.remove('transition-all', 'focus-active');
        }
    });

    var unsubscribeFan = subscribe('fan', function(newFanSpeed) {
        speedText.textContent = newFanSpeed;
    });

    var unsubsscribeAion = subscribe('aion', function() {
        icon.src = fanIconSvg(getFanIconColor());
    });
    var unsubsscribeAion = subscribe('power', function() {
        icon.src = fanIconSvg(getFanIconColor());
    });

    // Add cleanup method to the element
    focusArea.cleanup = function() {
        unsubscribeFocus();
        unsubscribeFan();
        unsubsscribeAion();
    };

    return focusArea;
}
