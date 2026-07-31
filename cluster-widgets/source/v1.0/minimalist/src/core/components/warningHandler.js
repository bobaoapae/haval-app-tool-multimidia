import { stateManager } from './../state.js';
import { logger } from '../../../../shared/utils/logger.js';

const VISUAL_ONLY_KEYS = [
    "car.ipk_info.bsd_lca_warning_reqleft",
    "car.ipk_info.bsd_lca_warning_reqright",
    "car.ipk_info.warning_tts_notify"
];

function isWarningValueActive(value) {
    return value !== "0" &&
        value !== "{0,0,0,0}" &&
        value !== "{0,0,0,0,0}" &&
        value !== "" &&
        value !== "false";
}

export function initWarningHandler() {
    // Warnings the user acknowledged, as key -> the exact value that was acknowledged.
    //
    // A dismissal must never delete entries from `warnings`: warningActive is a fold
    // over that whole map, so dropping a still-active key (an unbuckled seat belt) makes
    // the *next* update of an unrelated key (a door closing) recompute the fold without
    // it and clear the badge even though the car is still reporting the warning.
    // Masking keeps `warnings` a truthful picture of the car and keeps the dismissal
    // per-warning: when the car changes a key's value the acknowledgement for that key
    // no longer applies and the warning comes back on its own.
    let dismissedWarnings = {};

    function recomputeWarningState() {
        const warnings = stateManager.getState().warnings || {};
        let hasCriticalWarning = false;

        for (const [k, v] of Object.entries(warnings)) {
            const isThisActive = isWarningValueActive(v);

            if (isThisActive && !VISUAL_ONLY_KEYS.includes(k) && dismissedWarnings[k] !== v) {
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

        if (hasCriticalWarning) {
            stateManager.set('warningDismissed', false);
        }

        if (stateManager.get('warningActive') !== hasCriticalWarning) {
            stateManager.set('warningActive', hasCriticalWarning);
        }

        // Android size handling
        if (window.Android && window.Android.setWarningActive) {
            window.Android.setWarningActive(hasCriticalWarning);
        }

        return hasCriticalWarning;
    }

    window.updateWarning = function(key, value) {
        // A value that moved away from what was acknowledged is a new event, so the
        // acknowledgement stops covering it. The backend drops the key from its own
        // dismissed map on exactly the same condition, so the two stay in step.
        if (Object.prototype.hasOwnProperty.call(dismissedWarnings, key) &&
                dismissedWarnings[key] !== value) {
            delete dismissedWarnings[key];
        }

        const warnings = stateManager.getState().warnings || {};
        stateManager.set('warnings', Object.assign({}, warnings, { [key]: value }));

        recomputeWarningState();
    };

    /**
     * Backend -> theme replay of the acknowledged set, as key -> acknowledged value.
     * Sent on theme load (so a warning the user already dismissed does not pop up
     * again) and whenever a fresh critical warning resets the acknowledgements.
     */
    window.setDismissedWarnings = function(map) {
        dismissedWarnings = Object.assign({}, map || {});
        const stillWarning = recomputeWarningState();
        if (!stillWarning && Object.keys(dismissedWarnings).length > 0) {
            stateManager.set('warningDismissed', true);
        }
    };

    window.clearWarnings = function() {
        logger.log('Clearing all warnings via DISMISS_WARNING');
        const warnings = stateManager.getState().warnings || {};
        dismissedWarnings = {};
        for (const [k, v] of Object.entries(warnings)) {
            if (isWarningValueActive(v)) {
                dismissedWarnings[k] = v;
            }
        }
        recomputeWarningState();
        stateManager.set('warningDismissed', true);
    };
}
