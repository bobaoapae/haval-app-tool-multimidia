import { getState as get, subscribe } from '../../state.js';
import { div } from '../../../../../shared/utils/createElement.js';

export function createMask() {
    // Background layer (Bars and side gradient panels) - z-index: 50
    const maskBg = div({ className: 'cluster-mask-bg' });

    const topBar = div({ className: 'mask-top-bar' });
    const bottomBar = div({ className: 'mask-bottom-bar' });
    const leftPanel = div({ className: 'mask-panel left' });
    const rightPanel = div({ className: 'mask-panel right' });

    // Notches: the backdrop a hidden bar leaves behind so the menu (top) and the
    // time/gear readout (bottom) stay legible over a map. Always in the DOM; the
    // show-top-notch / show-bottom-notch classes on #app decide what renders, and
    // night.style.css clips each one to its S-shouldered shape.
    const topNotch = div({ className: 'mask-top-notch' });
    const bottomNotch = div({ className: 'mask-bottom-notch' });

    maskBg.appendChild(topBar);
    maskBg.appendChild(bottomBar);
    maskBg.appendChild(topNotch);
    maskBg.appendChild(bottomNotch);
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
        const carPlayInDash = get('carPlayInDash') === true || get('projectionMirrorInDash') === true;
        const cardId = get('cardId');
        const isCard0 = cardId == 0 || cardId === '0';

        // Card 0: hide right side mask entirely
        rightPanel.style.opacity = isCard0 ? '0' : '1';
        rightPanel.style.visibility = isCard0 ? 'hidden' : 'visible';
        maskBg.classList.toggle('card-0-active', isCard0);

        const isAppActive = (appInDash === true || appInDash === 'left' || appInDash === 'right') && !carPlayInDash;

        maskBg.classList.toggle('app-in-dash-active', isAppActive);
        maskBg.classList.toggle('carplay-in-dash', carPlayInDash);
        maskBg.classList.toggle('projection-map-display-active', carPlayInDash);
        document.body.classList.toggle('app-in-dash-active', isAppActive);
        document.body.classList.toggle('carplay-in-dash', carPlayInDash);
        document.body.classList.toggle('projection-map-display-active', carPlayInDash);
    };

    const unsub1 = subscribe('appInDash', updateVisibility);
    const unsub2 = subscribe('cardId', updateVisibility);
    const unsub3 = subscribe('warningActive', updateVisibility);
    const unsub4 = subscribe('warningDismissed', updateVisibility);
    const unsub5 = subscribe('carPlayInDash', updateVisibility);
    const unsub6 = subscribe('projectionMirrorInDash', updateVisibility);
    updateVisibility();

    return {
        background: maskBg,
        partial: partialAppMask,
        cleanup: () => {
            unsub1();
            unsub2();
            unsub3();
            unsub4();
            unsub5();
            unsub6();
        }
    };
}
