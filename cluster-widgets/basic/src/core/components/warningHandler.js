import { stateManager } from './../state.js';
import { logger } from './../../utils/logger.js';

const VISUAL_ONLY_KEYS = [
    "car.ipk_info.bsd_lca_warning_reqleft",
    "car.ipk_info.bsd_lca_warning_reqright",
    "car.ipk_info.warning_tts_notify"
];

export function initWarningHandler() {
    window.updateWarning = function(key, value) {
        const state = stateManager.getState();
        const warnings = state.warnings || {};
        const newWarnings = Object.assign({}, warnings, { [key]: value });
        stateManager.set('warnings', newWarnings);
        
        let hasCriticalWarning = false;
        
        for (const [k, v] of Object.entries(newWarnings)) {
            const isThisActive = v !== "0" && v !== "{0,0,0,0}" && v !== "{0,0,0,0,0}" && v !== "" && v !== "false";
            
            if (isThisActive && !VISUAL_ONLY_KEYS.includes(k)) {
                hasCriticalWarning = true;
            }
            
            if (k === "car.ipk_info.bsd_lca_warning_reqleft") {
                stateManager.set('bsdLeft', isThisActive);
            }
            if (k === "car.ipk_info.bsd_lca_warning_reqright") {
                stateManager.set('bsdRight', isThisActive);
            }
            // TODO: show a message in screen for ipk tts notify (only if AppInDash in Display 3
            // Otherwise only needed if we want to show on top and replace original message)
            // These should indicate auto pilot relevant message as
            // 19: cant enable ACC
            // 21: ?? (deactivated?)
            // 22: Failed to activate inteligent cruise control
            // 1083: ACC activated
        }
        
        const currentActive = stateManager.get('warningActive');
        const cardId = stateManager.get('cardId');
        const shouldBeWarnActive = hasCriticalWarning;

        if (hasCriticalWarning) {
            stateManager.set('warningDismissed', false);
        }

        if (currentActive !== hasCriticalWarning) {
            stateManager.set('warningActive', hasCriticalWarning);
        }

        // Android size handling
        if (window.Android && window.Android.setWarningActive) {
            window.Android.setWarningActive(shouldBeWarnActive);
        }
    };

    window.clearWarnings = function() {
        logger.log('Clearing all warnings via DISMISS_WARNING');
        stateManager.set('warnings', {});
        stateManager.set('warningActive', false);
        stateManager.set('warningDismissed', true);
        
        const cardId = stateManager.get('cardId');

        if (window.Android && window.Android.setWarningActive) {
            window.Android.setWarningActive(false);
        }
    };
}
