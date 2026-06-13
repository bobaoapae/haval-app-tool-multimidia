import { getState as get, subscribe } from '../../state.js';
import { div } from '../../../utils/createElement.js';

export function createMask() {
    // Background layer (Bars and Solid Circles) - z-index: 50
    const maskBg = div({ className: 'cluster-mask-bg' });
    const topBar = div({ className: 'mask-top-bar' });
    const bottomBar = div({ className: 'mask-bottom-bar' });
    const leftCircle = div({ className: 'mask-circle left' });
    const rightCircle = div({ className: 'mask-circle right' });

    maskBg.appendChild(topBar);
    maskBg.appendChild(bottomBar);
    maskBg.appendChild(leftCircle);
    maskBg.appendChild(rightCircle);

    // Foreground layer (Borders) - z-index: 150
    //const maskFg = div({ className: 'cluster-mask' });
    const leftCircleBorder = div({ className: 'mask-circle-border left' });
    const rightCircleBorder = div({ className: 'mask-circle-border right' });

    maskBg.appendChild(leftCircleBorder);
    maskBg.appendChild(rightCircleBorder);

    // No App Mask layer (Spatial)
    const noAppMaskL = div({ className: 'no-app-mask-l' });
    const noAppMaskR = div({ className: 'no-app-mask-r' });
    const partialAppMask = div({ className: 'partial-app-mask' });
    const warnMask = div({ className: 'warn-mask' });

    // Note: noAppMaskL and R are appended in main.js to appContainer for z-index
    maskBg.appendChild(partialAppMask);
    maskBg.appendChild(warnMask);

    const updateVisibility = () => {
        const appInDash = get('appInDash');
        const carPlayInDash = get('carPlayInDash');
        const projectionMirrorInDash = get('projectionMirrorInDash');
        const cardId = get('cardId');
        const mirrorThemeActive = carPlayInDash === true || projectionMirrorInDash === true;
        const warningActive = get('warningActive');
        const warningDismissed = get('warningDismissed');
        const rightVisible = warningDismissed || (cardId != 0 && !warningActive);

        let showL = true;
        let showR = true;

        if (appInDash === true) {
            showL = false;
            showR = false;
        } else if (appInDash === 'left') {
            showL = false;
            showR = true;
        } else if (appInDash === 'right') {
            showL = true;
            showR = false;
        } else {
            showL = true;
            showR = true;
        }

        // Hide right mask if card=0 (right panel hidden)
        if (!rightVisible) {
            showR = false;
        }

        if (mirrorThemeActive) {
            showL = false;
            showR = false;
        }

        noAppMaskL.style.opacity = showL ? '1' : '0';
        noAppMaskR.style.opacity = showR ? '1' : '0';
        noAppMaskL.style.visibility = showL ? 'visible' : 'hidden';
        noAppMaskR.style.visibility = showR ? 'visible' : 'hidden';

        partialAppMask.style.opacity = '0'; // Removed for better experience. Will decide if we keep this mask in future.
        partialAppMask.style.visibility = 'hidden';
        warnMask.style.opacity = '0'; // TODO: enhance this mask in future
        warnMask.style.visibility = 'hidden';
    };

    const unsub1 = subscribe('appInDash', updateVisibility);
    const unsub2 = subscribe('cardId', updateVisibility);
    const unsub3 = subscribe('warningActive', updateVisibility);
    const unsub4 = subscribe('warningDismissed', updateVisibility);
    const unsub5 = subscribe('carPlayInDash', updateVisibility);
    const unsub6 = subscribe('projectionMirrorInDash', updateVisibility);
    updateVisibility();

    // Use a combined object for cleanup
    const result = {
        background: maskBg,
        noAppL: noAppMaskL,
        noAppR: noAppMaskR,
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

    return result;
}
