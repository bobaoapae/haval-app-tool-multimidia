import { stateManager, subscribe } from '../../state.js';
import { div, img, span } from '../../../utils/createElement.js';
import { updateProgressRings } from './mainAcControl.js'

export function createImpulseAutoElement() {

    const impulse_auto_container = div({
        className: 'impulse-auto-container',
    });

    const speedText = span({
        className: 'impulse-auto-value-display text-center',
        children: [
            stateManager.get('fan'),
        ],
    });
    const icon = img({
        src: fanIconSvg(getFanIconColor()),
        className: 'w-32 h-32',
    });

    var fan = div({
        className: 'impulse-auto-ac-fan',
        children: [
            div({
                className: 'text-white flex-row-center',
                style: 'gap: 15px;',
                children: [
                    div({
                        className: 'text-24 font-bold flex-row-center w-60',
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

    var tempDisplay = div({
        className: 'impulse-auto-value-display font-bold',
        children: [
            getTempDisplayValue(stateManager.get('temp')),
        ],
    });

    var temp = div({
        className: 'impulse-auto-ac-temp',
        children: [
            div({
                className: 'text-white flex-row-center',
                style: 'gap: 15px;',
                children: [
                    div({
                        className: 'text-20 text-gray',
                        children: [
                            'Temp',
                        ],
                    }),
                    tempDisplay
                ],
            }),
        ]
    });

    impulse_auto_container.appendChild(fan);
    impulse_auto_container.appendChild(temp);

    const dividerTop = div({
        className: 'impulse-auto-divider-top',
    });
    impulse_auto_container.appendChild(dividerTop);

    const dividerBottom = div({
        className: 'impulse-auto-divider-bottom',
    });
    impulse_auto_container.appendChild(dividerBottom);

    var impulseAutoIconElement = span({
        children: ['IMPULSE AUTO']
    });
    var impulseAutoContainer = div({
        className: `impulse-auto-icon-container ${stateManager.get('impulseauto') === 1 ? '' : 'hidden'}`,
        children: [
            impulseAutoIconElement
        ],
    });
    impulse_auto_container.appendChild(impulseAutoContainer);

    var unsubscribeFan = subscribe('fan', function (newFanSpeed) {
        speedText.textContent = newFanSpeed;
    });

    var unsubsscribeAion = subscribe('aion', function () {
        icon.src = fanIconSvg(getFanIconColor());
    });
    var unsubsscribePower = subscribe('power', function () {
        icon.src = fanIconSvg(getFanIconColor());
    });

    var unsubscribeTemp = subscribe('temp', function (newTemp) {
        tempDisplay.textContent = getTempDisplayValue(newTemp);
    });

    var unsubscribeImpulseAuto = subscribe('impulseauto', function (newImpulseAuto) {
        if (newImpulseAuto == 1) {
            impulseAutoContainer.classList.remove('hidden');
        } else {
            impulseAutoContainer.classList.add('hidden');
        }
    });

    impulse_auto_container.cleanup = function () {
        unsubscribeFan();
        unsubsscribeAion();
        unsubsscribePower();
        unsubscribeTemp();
        unsubscribeImpulseAuto();
    };

    return impulse_auto_container;
}


