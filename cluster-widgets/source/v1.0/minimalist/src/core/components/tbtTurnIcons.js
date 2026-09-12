export const TBT_TURN_SVGS = {
    TURN_RIGHT: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M20.72,6.46,15.64,2.23A1,1,0,0,0,14,3V5H7a5,5,0,0,0-5,5V20a2,2,0,0,0,2,2H6a2,2,0,0,0,2-2V11h6v2a1,1,0,0,0,1.64.77l5.08-4.23a2,2,0,0,0,0-3.08Z"/></svg>',
    TURN_LEFT: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M17,5H10V3a1,1,0,0,0-1.64-.77L3.28,6.46a2,2,0,0,0,0,3.08l5.08,4.23A1,1,0,0,0,10,13V11h6v9a2,2,0,0,0,2,2h2a2,2,0,0,0,2-2V10A5,5,0,0,0,17,5Z"/></svg>',
    STRAIGHT: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M17.77,8.36,13.54,3.28a2.08,2.08,0,0,0-3.08,0L6.23,8.36A1,1,0,0,0,7,10H9V21a1,1,0,0,0,1,1h4a1,1,0,0,0,1-1V10h2a1,1,0,0,0,.77-1.64Z"/></svg>',
    ROUNDABOUT: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 486.039 486.039" fill="currentColor" aria-hidden="true"><path d="M296.897,41.504c-67.547,0-124.165,47.687-137.954,111.191l-48.547-0.024l0.016-29.836L6.759,182.62l103.593,59.888l0.016-29.836l75.352,0.038c0.007,0,0.011,0,0.016,0l30-30.045c0-44.752,36.41-81.16,81.162-81.16c44.75,0,81.158,36.408,81.158,81.16s-36.408,81.16-81.158,81.16l-30,30v192.214h60V320.617c63.475-13.789,111.158-70.406,111.158-137.952C438.056,104.829,374.731,41.504,296.897,41.504z"/><path d="M450.11,152.439c1.926,9.782,2.945,19.887,2.945,30.226c0,10.035-0.98,20.007-2.889,29.774h29.113v-60H450.11z"/><path d="M326.843,29.397V0h-60.001v29.45c9.856-1.945,19.923-2.945,30.056-2.945C307.136,26.504,317.147,27.506,326.843,29.397z"/><path d="M218.898,200.726l-26.944,26.983l-28.753-0.014c14.085,41.608,47.045,74.504,88.696,88.495v-28.578l27.119-27.121C249.31,253.708,225.852,230.37,218.898,200.726z"/></svg>',
    U_TURN: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M13.5,2A8.5,8.5,0,0,0,5,10.5V14H3a1,1,0,0,0-.77,1.64l5,6a1,1,0,0,0,1.54,0l5-6A1,1,0,0,0,13,14H11V10.5a2.5,2.5,0,0,1,5,0V21a1,1,0,0,0,1,1h4a1,1,0,0,0,1-1V10.5A8.51,8.51,0,0,0,13.5,2Z"/></svg>',
    MERGE: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path d="M17.89 17.707L16.892 20c-3.137-1.366-5.496-3.152-6.892-5.275-1.396 2.123-3.755 3.91-6.892 5.275l-.998-2.293C5.14 16.389 8.55 14.102 8.55 10V7H5.5L10 0l4.5 7h-3.05v3c0 4.102 3.41 6.389 6.44 7.707z"/></svg>',
    FORK: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="m9.78 11.16l-1.42 1.42a7.3 7.3 0 0 1-1.79-2.94l1.94-.49c.32.89.77 1.5 1.27 2.01M11 6L7 2L3 6h3.02c.02.81.08 1.54.19 2.17l1.94-.49C8.08 7.2 8.03 6.63 8.02 6zm10 0l-4-4l-4 4h2.99c-.1 3.68-1.28 4.75-2.54 5.88c-.5.44-1.01.92-1.45 1.55c-.34-.49-.73-.88-1.13-1.24L9.46 13.6c.93.85 1.54 1.54 1.54 3.4v5h2v-5c0-2.02.71-2.66 1.79-3.63c1.38-1.24 3.08-2.78 3.2-7.37z"/></svg>'
};

export const TBT_TURN_GLYPHS = {
    TURN_RIGHT: '↱',
    TURN_LEFT: '↰',
    STRAIGHT: '↑',
    U_TURN: '↩',
    ROUNDABOUT: '↻',
    FORK: '⑂',
    MERGE: '⇉',
    EXIT: '↗',
    DESTINATION: '◎'
};

/** pt-BR grouping: 1000 -> "1.000". Values under 1000 are returned unchanged. */
export function groupThousands(value) {
    const n = Math.round(Number(value));
    if (!Number.isFinite(n)) return '';
    return String(Math.abs(n)).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

export function formatRemainingDistance(remainingM) {
    const meters = Number(remainingM);
    if (!Number.isFinite(meters) || meters < 0) return '';
    if (meters < 1000) return `${Math.round(meters)} m`;
    const km = meters / 1000;
    if (km >= 100) return `${groupThousands(km)} km`;
    return `${km.toFixed(1)} km`;
}

export function formatTripEta(remainingS, remainingM) {
    const seconds = Number(remainingS);
    if (Number.isFinite(seconds) && seconds >= 0) {
        if (seconds < 45) return '<1 min';
        const minutes = Math.round(seconds / 60);
        if (minutes < 60) return `${minutes} min`;
        const hours = Math.floor(minutes / 60);
        const rest = minutes % 60;
        return rest ? `${hours} h ${rest} min` : `${hours} h`;
    }
    return formatRemainingDistance(remainingM);
}

export function formatArrivalClock(remainingS, now = new Date()) {
    const seconds = Number(remainingS);
    if (!Number.isFinite(seconds) || seconds < 0) return '';
    const eta = new Date(now.getTime() + seconds * 1000);
    const hrs = String(eta.getHours()).padStart(2, '0');
    const mins = String(eta.getMinutes()).padStart(2, '0');
    return `${hrs}:${mins}`;
}
