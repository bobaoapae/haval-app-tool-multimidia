import { stateManager, subscribe } from '../../state.js';
import { createFocusElementWithChildren } from './focusElement.js';
import { div, span, img } from '../../../utils/createElement.js';
import { updateProgressRings } from './mainAcControl.js'

export function createTempInfoElement() {

    const container = div({ className: 'temp-info-container' });

    var externalTempLabel = div({
       className: 'temp-info-label',
       children: [ 'Outside',],
    });
    function formatTemp(temp, unit) {
        const numericTemp = parseFloat(temp);
        if (numericTemp >= 85 || numericTemp <= -40) {
            return '--';
        }
        return temp + (unit || '°C');
    }

    var externalTemp = div({
       className: 'temp-info-value-left',
       children: [
           formatTemp(stateManager.get('outside_temp'), stateManager.get('tempUnit')),
       ],
    });
    var internalTempLabel = div({
       className: 'temp-info-label',
       children: [ 'Inside',],
    });
    var internalTemp = div({
       className: 'temp-info-value-right',
       children: [
           formatTemp(stateManager.get('inside_temp'), stateManager.get('tempUnit')),
       ],
    });

    externalTempLabel.append(externalTemp);
    container.append(externalTempLabel);
    internalTempLabel.append(internalTemp);
    container.append(internalTempLabel);

    container.onMount = () => {
       const internalSub = subscribe('inside_temp', (newValue) => {
           internalTemp.textContent = formatTemp(newValue, stateManager.get('tempUnit'));
       });
       const externalSub = subscribe('outside_temp', (newValue) => {
           externalTemp.textContent = formatTemp(newValue, stateManager.get('tempUnit'));
       });
       const unitSub = subscribe('tempUnit', (newUnit) => {
           internalTemp.textContent = formatTemp(stateManager.get('inside_temp'), newUnit);
           externalTemp.textContent = formatTemp(stateManager.get('outside_temp'), newUnit);
       });
       return () => {
           internalSub();
           externalSub();
       };
    };

    return container;

}


