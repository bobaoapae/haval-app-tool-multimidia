import { stateManager } from './../state.js';
import { logger } from '../../../../shared/utils/logger.js';

/**
 * Warning intake.
 *
 * The theme does NOT decide whether a warning is showing. `warningActive`, `warningDismissed`,
 * `bsdLeft` and `bsdRight` all arrive from the backend as typed booleans via window.control().
 * The backend is the only side that knows which CAN keys count as warnings, what an "active"
 * value looks like, and what the driver has already dismissed — so it is the only side that
 * can answer the question consistently.
 *
 * This used to be computed here and pushed *back* to the backend through
 * Android.setWarningActive(), which meant a downloadable theme decided a value the backend
 * uses for cluster visibility and app widths, and left two "which keys count" lists to drift
 * apart. What remains here is bookkeeping only.
 */
export function initWarningHandler() {
    /**
     * Record a warning's current raw value. Informational: nothing renders from this today,
     * but it gives a theme that wants to list individual warnings the raw material.
     * Keys are canonical CAN keys, values the car's raw strings.
     */
    window.updateWarning = function(key, value) {
        const warnings = stateManager.getState().warnings || {};

        // Object.assign always produces a new reference, which bypasses the !== guard in
        // setState and triggers spurious render() calls (causing screen flicker).
        if (warnings[key] === value) {
            return;
        }

        stateManager.set('warnings', Object.assign({}, warnings, { [key]: value }));
    };

    /**
     * Legacy/dev entry point. The backend no longer calls this — dismissal is per-warning and
     * the resulting `warningActive` is pushed directly. Kept because the mock cluster runtime
     * still routes its DISMISS_WARNINGS action here, and because older backends may call it.
     */
    window.clearWarnings = function() {
        logger.log('clearWarnings() — clearing local warning record');
        stateManager.set('warnings', {});
    };
}
