import { getState, subscribe } from '../state.js';
import { div, img, span } from '../../utils/createElement.js';


const iconESP = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAACXBIWXMAAAsTAAALEwEAmpwYAAADMklEQVR4nO1XS0hUURj+KKKCirKkVr2fix6rtEW5CXpQVqYFPXcFrYJqmQq1qCCtbGNRRkiLFkEbE2bOf9VME+xBtA0i0agWmW0yiC/+/547dxgfzIzjzg8uc+bc/5zz/c/zX2AK+SDBhWjjRjjuRZJ7bKxzk4oEV8KxFo7vIByGYx8cu/3T5+feQlhjsgVDB4shbIDjEIQPkeR+dHH2CDmdcyw3mVD2jq2dEBLcBsevED6G47Ks1wVcDsdmOA4gYGl+hztWQTiIgCfy28D2OOX3qMxH859Icnveh8ckdthewpLsFnSw2Mw+Ec1Hkjjp3bFodAHhDSR5yI8bzOeFRhgTt2ysZzleD1+0cB4cfyPgap9qQzkFXG6BOYR2rkAb19i4k3NV46Nw7PQsay2NJguOTRBW21jYZYEO4T04XvaT7xFwn417OQPCZ3BMpD2tSHApnnI6hBcgbEm9E54x7VQmlr9v8jGBcitW4Vk1EDbqoMfKqZZSrWZRkXnJBRDSWEaP+q6FMyE8B8cPcDyWeqdmDbgTwk9pa+6mDoyL1V+0ssgUdexWVj/guA4Bt0D4OSUcERgNwgdwvDhiPiTQm/Z/PoT/zJqxFb4gyU0IuB7Cb7rZMJJcbFYwRhkEHK+lPVeNfWjK7xDWQXgFwrIUAU3hSF7YAeHzjDh4jYC7EXAJhH9iAjqpL7MhEFqhzB9eZ2T0jhhJ4CwCzsqwXg+Eu2ICzrvAcbOZJxsXZMLxkrkl0wWjy/bZ9d3GDZELeuxezyUIE1ybmhMeh/Cj13Z8AulBqBZz6nJNwyg3NWLVv2On4QtLK025OP1akOR51HCaLzZhSo+GgAdTBMOa06iDIxC+8v6psWIxWXB8lKasNjJVymoOhL8sj8NCMmSaFBrR3uHvqrgUKxxvwrHCj2/bxVFoCJ9AWO/PqLAzx7mOB6yZKBSEpyHsz75xDVhqTYQ2E4VqSJLcmtvCgJXWTmkzMTHNByE8nO8GJd4dzTkFpgZa6PP+3DXPhLZR2smEjUqT1Ymx2nLhAS+jbXl9YT9W2k2ragjfWDXL/DDROX2nMio7qWhlkdVzvUGjT7PokpoCcsN/QF4Tpoju0DMAAAAASUVORK5CYII=";
const iconProfiles = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAYAAABXAvmHAAAACXBIWXMAAAsTAAALEwEAmpwYAAABaklEQVR4nO3W70cDcRwH8NW2Vmv92M/279z1pO1fKJEpRSIiEhGJiEhEpHYnUsaIpB+yliRJSpIkd5JVt3U1k3rwTiXaTtOzS71f3LPPl/fHfR68LRYiIqK/QpjVw2JUV0VJhyjdQ4xmVFHWQobBdYRLVl7U0uVnWJeeYI3nYItlYV98hH1eR9lcBg5Jg2PmFuXTKVRMXcM5eQXnhIrKcQWusUu4Ri9QNXKO6uEzVA+dombwBLUDx3D3H8HddwhP7wG8Pfvwdu/B17ULf+cO/O1J1de6ZczzKS+8lIEopyFIaaVwzpTwHdsItCURiCSU7xcoCP/xaSicMyt8XSSBYMsmiixgDC/Kd4YHZoYPNm8UWeD95vPDC9KN4ZdZ4znVrPDBxrUiJyRrobeb/xq+PppqMCwQy4ZsCw+KGeGDTauGPEREv4TALqSzC3nYhWR2IbALEdE/JbAL6exCHnYhmV0I7EJERESWn3kFtZubm7N84b8AAAAASUVORK5CYII=";
const iconDisplay = "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDgiIGhlaWdodD0iNDgiIHZpZXdCb3g9IjAgMCA0OCA0OCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KICA8cmVjdCB4PSI0IiB5PSI4IiB3aWR0aD0iNDAiIGhlaWdodD0iMjgiIHJ4PSI0IiBzdHJva2U9IiMwMEEwRTAiIHN0cm9rZS13aWR0aD0iMi41Ii8+CiAgPHJlY3QgeD0iOCIgeT0iMTIiIHdpZHRoPSIxNCIgaGVpZ2h0PSI5IiByeD0iMSIgZmlsbD0iIzAwQTBFMCIgZmlsbC1vcGFjaXR5PSIwLjIiLz4KICA8cmVjdCB4PSIyNiIgeT0iMTIiIHdpZHRoPSIxNCIgaGVpZ2h0PSI5IiByeD0iMSIgZmlsbD0iIzAwQTBFMCIgZmlsbC1vcGFjaXR5PSIwLjIiLz4KICA8cmVjdCB4PSI4IiB5PSIyNSIgd2lkdGg9IjMyIiBoZWlnaHQ9IjciIHJ4PSIxIiBmaWxsPSIjMDBBMEUwIiBmaWxsLW9wYWNpdHk9IjAuNSIvPgogIDxwYXRoIGQ9Ik0yMCA0MEgyOE0yNCAzNlY0MCIgc3Ryb2tlPSIjMDBBMEUwIiBzdHJva2Utd2lkdGg9IjIuNSIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIi8+Cjwvc3ZnPg==";
const iconMode = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAAACXBIWXMAAAsTAAALEwEAmpwYAAABQUlEQVR4nOVVS0oDQRB9uvaDeot4gOANdBcIyVI9Qa7gIRy3xqUnECFTNQs9RHYBg+ABJu7Mk+4ZME53j9M4QoIPCga66r3iVVcP8D+Q8gzKHEqWkSPjaXsCwrsV8iKE47bIjyGceQRmSNmJI7viNoQJhPcQDiG8gXDpkH+JLMucYVmTWI4gMl4GybRxXITId6B8bUHgDQ/ccwWUoxorXqDs45m7NjL2oJzWiIxcgQlPoFx4yR956OQ/8aAUrpIvLJcXyi6E75WCfnBmwkGlGVPbDQ+5EFldKFpLQjBef28mryePFZhwP07AZ1HGXjDf3P/GFoWGrJzagbr5RxDOmw/5p2sqHFjPTRSbO4+7pn++aAZmzX8rIDxHEORW5bFLoPyoITRn19Y+U2O+DUcUUnbae65DEI493d9ic36ZWGN8Am7jp8nKJdGxAAAAAElFTkSuQmCC";
const iconSteer = "data:image/png;base64,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";
const iconRegen = "data:image/png;base64,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";
const iconEV = "data:image/png;base64,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";

export const menuItems = [
    { id: 'option_1', label: 'ESP', stateKey: 'espStatus', iconSrc: iconESP },
    { id: 'option_2', label: '', stateKey: 'evMode', iconSrc: iconEV },
    { id: 'option_3', label: 'Modo', stateKey: 'drivingMode', iconSrc: iconMode },
    { id: 'option_7', label: 'Gráficos', iconSrc: iconProfiles },
    { id: 'option_5', label: 'Modo', stateKey: 'steerMode', iconSrc: iconSteer },
    { id: 'option_6', label: 'Regeneração', iconSrc: iconRegen },
    { id: 'option_4', label: 'Display', iconSrc: iconDisplay },
];

export function createMainMenu() {
    const container = div({ className: 'main-menu-container' });
    const carousel = div({ className: 'menu-carousel' });
    container.appendChild(carousel);
    const focusedItemId = getState('focusedMenuItem');
    const itemElements = {};

    const createLabelContent = (itemData) => {
        if (!itemData.stateKey) {
            return itemData.label;
        }
        let stateValue = getState(itemData.stateKey);
        let statusClass = String(stateValue).toLowerCase();

        if (itemData.stateKey === 'evMode') {
            const val = String(stateValue).toUpperCase().replace(/'/g, "");
            if (val === 'HEV') {
                stateValue = "Modo HEV";
                //statusClass = 'off';
            } else if (val === 'EVP') {
                stateValue = "Prior. EV";
                statusClass = 'eco';
            } else if (val === 'EV') {
                stateValue = "Modo EV";
                statusClass = 'on';
            }
        }

        return [
            itemData.label,
            ' ',
            span({
                className: `menu-label-status ${statusClass}`,
                children: [stateValue]
            })
        ];
    };

    menuItems.forEach((itemData, index) => {
        const itemEl = div({
            id: itemData.id,
            className: `menu-item ${itemData.id === focusedItemId ? 'focused' : ''}`,
            'data-index': index,
            children: [
                div({
                    className: 'icon-container',
                    children: [img({ className: 'menu-icon', src: itemData.iconSrc, alt: itemData.label })]
                }),
                span({
                    className: 'menu-label',
                    children: [createLabelContent(itemData)]
                })
            ]
        });

        carousel.appendChild(itemEl);
        itemElements[itemData.id] = itemEl;
    });

    const updateFocus = (newFocusedId) => {
        Object.values(itemElements).forEach(el => el.classList.remove('focused'));
        if (itemElements[newFocusedId]) {
            itemElements[newFocusedId].classList.add('focused');
        }
        carousel.className = 'menu-carousel';
        const focusedIndex = menuItems.findIndex(item => item.id === newFocusedId);
        if (focusedIndex !== -1) {
            carousel.classList.add(`focus-${focusedIndex}`);
        }
    };

    const updateValue = (newValue, key) => {
        const targetItemData = menuItems.find(item => item.stateKey === key);
        if (!targetItemData) return;

        const labelElement = itemElements[targetItemData.id]?.querySelector('.menu-label');
        if (labelElement) {
            labelElement.innerHTML = '';
            labelElement.append(...createLabelContent(targetItemData));
        }
    };

    const subscriptions = [];

    updateFocus(focusedItemId);
    subscriptions.push(subscribe('focusedMenuItem', updateFocus));

    menuItems.forEach(item => {
        if (item.stateKey) {
            subscriptions.push(subscribe(item.stateKey, updateValue));
        }
    });

    const cleanup = () => {
        subscriptions.forEach(unsubscribe => unsubscribe());
    };

    return { element: container, cleanup };
}
