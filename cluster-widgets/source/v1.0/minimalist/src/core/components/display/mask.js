import { getState as get, subscribe } from '../../state.js';
import { div } from '../../../../../shared/utils/createElement.js';

export function createMask() {
    // Background layer (Bars and side gradient panels) - z-index: 50
    const maskBg = div({ className: 'cluster-mask-bg' });
    const topBar = div({ className: 'mask-top-bar' });
    const bottomBar = div({ className: 'mask-bottom-bar' });
    const leftPanel = div({ className: 'mask-panel left' });
    const rightPanel = div({ className: 'mask-panel right' });

    maskBg.appendChild(topBar);
    maskBg.appendChild(bottomBar);
    maskBg.appendChild(leftPanel);
    maskBg.appendChild(rightPanel);

    // Overlay masks (partial / warn). Side no-app discs removed —
    // left/right mask-panel gradients already cover that role.
    const partialAppMask = div({ className: 'partial-app-mask' });
    const warnMask = div({ className: 'warn-mask' });

    maskBg.appendChild(partialAppMask);
    maskBg.appendChild(warnMask);

    const updateVisibility = () => {
        const appInDash = get('appInDash');
        const cardId = get('cardId');
        const isCard0 = cardId == 0 || cardId === '0';

        // Card 0: hide right side mask entirely
        rightPanel.style.opacity = isCard0 ? '0' : '1';
        rightPanel.style.visibility = isCard0 ? 'hidden' : 'visible';
        maskBg.classList.toggle('card-0-active', isCard0);

        // partialAppMask / warnMask opacity hooks left commented for future use
        // partialAppMask.style.opacity = ...
        // warnMask.style.opacity = ...

        if (appInDash === true || appInDash === 'left' || appInDash === 'right') {
            maskBg.classList.add('app-in-dash-active');
            document.body.classList.add('app-in-dash-active');
        } else {
            maskBg.classList.remove('app-in-dash-active');
            document.body.classList.remove('app-in-dash-active');
        }
    };

    const unsub1 = subscribe('appInDash', updateVisibility);
    const unsub2 = subscribe('cardId', updateVisibility);
    const unsub3 = subscribe('warningActive', updateVisibility);
    const unsub4 = subscribe('warningDismissed', updateVisibility);
    updateVisibility();

    return {
        background: maskBg,
        partial: partialAppMask,
        cleanup: () => {
            unsub1();
            unsub2();
            unsub3();
            unsub4();
        }
    };
}
