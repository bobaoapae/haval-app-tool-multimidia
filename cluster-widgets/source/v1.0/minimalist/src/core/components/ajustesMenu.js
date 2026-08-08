import { getState, subscribe } from '../state.js';
import { div, span } from '../../../../shared/utils/createElement.js';

export const ajustesItems = [
    { id: 'ajuste_driving', label: 'Condução', stateKey: 'drivingMode' },
    { id: 'ajuste_ev', label: 'Modo EV', stateKey: 'evMode' },
    { id: 'ajuste_steer', label: 'Direção', stateKey: 'steerMode' },
    { id: 'ajuste_regen', label: 'Regeneração', stateKey: 'regenMode' },
    { id: 'ajuste_esp', label: 'ESP', stateKey: 'espStatus' }
];

export function createAjustesMenu() {
    const container = div({ className: 'ajustes-screen' });
    const list = div({ className: 'ajustes-list' });

    container.appendChild(list);

    const itemElements = {};

    const formatValue = (itemData) => {
        let value = getState(itemData.stateKey);
        if (itemData.stateKey === 'evMode') {
            const val = String(value).toUpperCase().replace(/'/g, "");
            if (val === 'HEV') return 'HEV';
            if (val === 'EVP') return 'Prior. EV';
            if (val === 'EV') return 'Modo EV';
        }
        return value;
    };

    ajustesItems.forEach((itemData, index) => {
        const itemVal = formatValue(itemData);
        const valueClass = String(getState(itemData.stateKey)).toLowerCase();

        const itemEl = div({
            id: itemData.id,
            className: `ajustes-item`,
            children: [
                span({ className: 'ajustes-item-label', children: [itemData.label] }),
                span({ className: `ajustes-item-value ${valueClass}`, children: [itemVal] })
            ]
        });

        list.appendChild(itemEl);
        itemElements[itemData.id] = itemEl;
    });

    const updateFocus = () => {
        const focusArea = getState('menuFocusArea');
        const focusedId = getState('focusedAjustesItem');
        const screen = getState('screen');

        if (screen === 'ajustes') {
            container.classList.add('active-menu');
        } else {
            container.classList.remove('active-menu');
        }

        Object.keys(itemElements).forEach(id => {
            const el = itemElements[id];
            el.classList.remove('focused');
            el.classList.remove('active-focus');
        });

        if (itemElements[focusedId]) {
            itemElements[focusedId].classList.add('focused');
            if (focusArea === 'sub') {
                itemElements[focusedId].classList.add('active-focus');
            }
        }
    };

    const updateItemValue = (itemData) => {
        const el = itemElements[itemData.id];
        if (el) {
            const valEl = el.querySelector('.ajustes-item-value');
            if (valEl) {
                const val = formatValue(itemData);
                valEl.textContent = val;
                valEl.className = `ajustes-item-value ${String(getState(itemData.stateKey)).toLowerCase()}`;
            }
        }
    };

    // Subscriptions
    const unsubs = [
        subscribe('menuFocusArea', updateFocus),
        subscribe('focusedAjustesItem', updateFocus),
        subscribe('screen', updateFocus),
        subscribe('espStatus', () => updateItemValue(ajustesItems.find(i => i.stateKey === 'espStatus'))),
        subscribe('evMode', () => updateItemValue(ajustesItems.find(i => i.stateKey === 'evMode'))),
        subscribe('drivingMode', () => updateItemValue(ajustesItems.find(i => i.stateKey === 'drivingMode'))),
        subscribe('steerMode', () => updateItemValue(ajustesItems.find(i => i.stateKey === 'steerMode'))),
        subscribe('regenMode', () => updateItemValue(ajustesItems.find(i => i.stateKey === 'regenMode')))
    ];

    updateFocus();

    return {
        element: container,
        cleanup: () => {
            unsubs.forEach(unsub => unsub());
        }
    };
}
