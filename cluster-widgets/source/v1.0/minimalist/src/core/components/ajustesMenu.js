import { getState, subscribe } from '../state.js';
import { div, span } from '../../../../shared/utils/createElement.js';
import { formatHevReserveSublabel } from '../../../../shared/car/carConstants.js';

export const ajustesItems = [
    { id: 'ajuste_driving', label: 'Condução', stateKey: 'drivingMode' },
    { id: 'ajuste_ev', label: 'Modo EV', stateKey: 'evMode' },
    { id: 'ajuste_steer', label: 'Direção', stateKey: 'steerMode' },
    { id: 'ajuste_regen', label: 'Regeneração', stateKey: 'regenMode' },
    { id: 'ajuste_esp', label: 'ESP', stateKey: 'espStatus' }
];

function isHevMode() {
    return String(getState('evMode')).toUpperCase().replace(/'/g, '') === 'HEV';
}

export function createAjustesMenu() {
    const container = div({ className: 'ajustes-screen' });
    const list = div({ className: 'ajustes-list' });

    container.appendChild(list);

    const itemElements = {};

    const formatValueNodes = (itemData) => {
        if (itemData.stateKey === 'evMode') {
            const val = String(getState('evMode')).toUpperCase().replace(/'/g, '');
            if (val === 'HEV') {
                const sub = formatHevReserveSublabel(getState('hevReserve'), getState('hevSocTarget'));
                return [
                    span({ className: 'ajustes-item-value-base', children: ['HEV'] }),
                    span({ className: 'ajustes-item-value-sub', children: [sub] })
                ];
            }
            if (val === 'EVP') return ['Prior. EV'];
            if (val === 'EV') return ['Modo EV'];
            return [String(getState('evMode'))];
        }
        if (itemData.stateKey === 'regenMode') {
            if (getState('onepedal')) return ['One-Pedal'];
            return [String(getState('regenMode'))];
        }
        return [String(getState(itemData.stateKey))];
    };

    const valueClassFor = (itemData) => {
        if (itemData.stateKey === 'regenMode' && getState('onepedal')) return 'onepedal';
        return String(getState(itemData.stateKey)).toLowerCase();
    };

    ajustesItems.forEach((itemData) => {
        const valueClass = valueClassFor(itemData);

        const itemEl = div({
            id: itemData.id,
            className: 'ajustes-item',
            children: [
                span({ className: 'ajustes-item-label', children: [itemData.label] }),
                span({ className: `ajustes-item-value ${valueClass}`, children: formatValueNodes(itemData) })
            ]
        });

        list.appendChild(itemEl);
        itemElements[itemData.id] = itemEl;
    });

    // Absolutely positioned under the list so showing/hiding never reflows the menu.
    const longPressHint = div({
        className: 'ajustes-longpress-hint hidden',
        children: [
            'Segure ',
            span({ className: 'ajustes-enter-icon', children: ['↵'] }),
            span({ className: 'ajustes-longpress-hint-tail', children: [''] })
        ]
    });
    const hintTail = longPressHint.querySelector('.ajustes-longpress-hint-tail');
    list.appendChild(longPressHint);

    const updateFocus = () => {
        const focusArea = getState('menuFocusArea');
        const focusedId = getState('focusedAjustesItem');
        const screen = getState('screen');

        if (screen === 'ajustes') {
            container.classList.add('active-menu');
        } else {
            container.classList.remove('active-menu');
        }

        Object.keys(itemElements).forEach((id) => {
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

        let hintText = '';
        if (focusArea === 'sub') {
            if (focusedId === 'ajuste_regen' && !getState('onepedal')) {
                hintText = ' para One-Pedal';
            } else if (focusedId === 'ajuste_ev' && isHevMode()) {
                hintText = ' para Reserva';
            }
        }
        if (hintText) {
            hintTail.textContent = hintText;
            longPressHint.classList.remove('hidden');
        } else {
            longPressHint.classList.add('hidden');
        }
    };

    const updateItemValue = (itemData) => {
        const el = itemElements[itemData.id];
        if (!el) return;
        const valEl = el.querySelector('.ajustes-item-value');
        if (!valEl) return;
        valEl.replaceChildren(
            ...formatValueNodes(itemData).map((n) => (typeof n === 'string' ? document.createTextNode(n) : n))
        );
        valEl.className = `ajustes-item-value ${valueClassFor(itemData)}`;
    };

    const refreshEvAndRegen = () => {
        updateItemValue(ajustesItems.find((i) => i.stateKey === 'evMode'));
        updateItemValue(ajustesItems.find((i) => i.stateKey === 'regenMode'));
        updateFocus();
    };

    const unsubs = [
        subscribe('menuFocusArea', updateFocus),
        subscribe('focusedAjustesItem', updateFocus),
        subscribe('screen', updateFocus),
        subscribe('espStatus', () => updateItemValue(ajustesItems.find((i) => i.stateKey === 'espStatus'))),
        subscribe('evMode', refreshEvAndRegen),
        subscribe('hevReserve', refreshEvAndRegen),
        subscribe('hevSocTarget', refreshEvAndRegen),
        subscribe('drivingMode', () => updateItemValue(ajustesItems.find((i) => i.stateKey === 'drivingMode'))),
        subscribe('steerMode', () => updateItemValue(ajustesItems.find((i) => i.stateKey === 'steerMode'))),
        subscribe('regenMode', () => updateItemValue(ajustesItems.find((i) => i.stateKey === 'regenMode'))),
        subscribe('onepedal', refreshEvAndRegen)
    ];

    updateFocus();

    return {
        element: container,
        cleanup: () => {
            unsubs.forEach((unsub) => unsub());
        }
    };
}
