import { getState, subscribe } from '../../../state.js';
import { div, span } from '../../../../utils/createElement.js';

export function createOdometerInfo() {
    const container = div({ className: 'odometer-info-container' });
    const textWrapper = div({ className: 'odometer-text-wrapper double-line' });
    
    const odometerLine = div({ className: 'odometer-line', children: [
        span({ className: 'odometer-label', children: ['ODO '] }),
        span({ className: 'odometer-value', children: ['0'] }),
        span({ className: 'odometer-unit', children: [' km'] })
    ]});
    
    const revisionLine = div({ className: 'revision-line', children: [
        span({ className: 'revision-text', children: ['--'] })
    ]});
    
    textWrapper.appendChild(odometerLine);
    textWrapper.appendChild(revisionLine);
    container.appendChild(textWrapper);
    let showDoubleLine = true;
    let initialTimeout = setTimeout(() => {
        showDoubleLine = false;
        textWrapper.classList.remove('startup-flash');
        updateDisplay();
    }, 120000); 

    textWrapper.classList.add('startup-flash');

    const updateDisplay = () => {
        const testMode = window.__AIR_CONTROL_TEST_MODE === true;
        const odometerEnabled = getState('enableOdometer');
        const warningEnabled = getState('enableRevisionWarning') || testMode;
        const currentKm = getState('odometer') || 0;
        const nextKm = getState('nextRevisionKm') || (testMode ? 12000 : 0);
        const nextDateMillis = getState('nextRevisionDate') || (testMode ? Date.now() + 15 * 24 * 60 * 60 * 1000 : 0);

        if (!odometerEnabled && !warningEnabled) {
            container.style.display = 'none';
            return;
        }
        container.style.display = 'block';
        
        odometerLine.querySelector('.odometer-value').textContent = currentKm;
        odometerLine.style.display = odometerEnabled ? 'block' : 'none';
        
        if (warningEnabled) {
            const remainingKm = nextKm - currentKm;
            let remainingDays = 999;
            if (nextDateMillis > 0) {
                const remainingMillis = nextDateMillis - Date.now();
                remainingDays = Math.ceil(remainingMillis / (1000 * 60 * 60 * 24));
            }

            const isNearMaintenance = (remainingKm < 1000 || remainingDays < 30);
            
            if (testMode || showDoubleLine || isNearMaintenance) {
                let text = `Manutenção em: ${remainingKm} Km`;
                if (nextDateMillis > 0 && remainingDays > 0) {
                    text += ` ou ${remainingDays} dias`;
                }
                
                revisionLine.querySelector('.revision-text').textContent = text;
                revisionLine.style.display = 'block';
                textWrapper.className = `odometer-text-wrapper ${odometerEnabled ? 'double-line' : 'single-line'}`;
            } else {
                revisionLine.style.display = 'none';
                textWrapper.className = 'odometer-text-wrapper single-line';
            }
        } else {
            revisionLine.style.display = 'none';
            textWrapper.className = 'odometer-text-wrapper single-line';
        }
    };

    const subscriptions = [
        subscribe('odometer', updateDisplay),
        subscribe('nextRevisionKm', updateDisplay),
        subscribe('nextRevisionDate', updateDisplay),
        subscribe('enableRevisionWarning', updateDisplay),
        subscribe('enableOdometer', updateDisplay)
    ];

    updateDisplay();

    const cleanup = () => {
        if (initialTimeout) clearTimeout(initialTimeout);
        subscriptions.forEach(unsubscribe => unsubscribe());
    };

    return { element: container, cleanup };
}
