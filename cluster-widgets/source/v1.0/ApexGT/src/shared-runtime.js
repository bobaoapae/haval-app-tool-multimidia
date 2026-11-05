// GERADO por scripts/shared-runtime.mjs a partir de cluster-widgets/source/v1.0/shared — não edite à mão.
// Cartão de turn-by-turn e derivações do carro compartilhados com Default e Minimalist; `npm run build` regenera.
(function () {
"use strict";
// ---- source/v1.0/shared/utils/clusterAcFocus.js (createAcFocusPreference) ----
function createAcFocusPreference(getAndroid, warn = console.warn) {
    function bridge() {
        const candidate = getAndroid();
        if (!candidate || typeof candidate.getClusterAcFocus !== 'function' ||
            typeof candidate.saveClusterAcFocus !== 'function') {
            warn('[AC focus] Persistence bridge unavailable; using in-memory focus.');
            return null;
        }
        return candidate;
    }
    return {
        available() {
            try {
                const target = getAndroid();
                return !!target && typeof target.getClusterAcFocus === 'function' &&
                    typeof target.saveClusterAcFocus === 'function';
            } catch (_) { return false; }
        },
        restore() {
            try {
                const target = bridge();
                if (!target) return 'fan';
                const value = target.getClusterAcFocus();
                if (value === 'fan' || value === 'temp') return value;
                warn('[AC focus] Invalid stored focus; using fan.');
            } catch (error) { warn('[AC focus] Restore failed; using fan.', error); }
            return 'fan';
        },
        save(focus) {
            if (focus !== 'fan' && focus !== 'temp') return false;
            try {
                const target = bridge();
                if (target && target.saveClusterAcFocus(focus) === true) return true;
                warn('[AC focus] Focus remains in memory; persistence not accepted.');
            } catch (error) { warn('[AC focus] Save failed; focus remains in memory.', error); }
            return false;
        }
    };
}
// ---- source/v1.0/shared/car/carDerivations.js (getAdjustedSpeed) ----
function getAdjustedSpeed(rawSpeed, enableAdjustment = false, offsetPercent = 0.0) {
    const speed = parseFloat(rawSpeed) || 0.0;

    // Formula to match the physical instrument cluster
    const adjustedSpeed = speed * 1.07 - (speed / 180.0) * 0.02;

    const finalSpeed = enableAdjustment
        ? adjustedSpeed * (1.0 + (parseFloat(offsetPercent) / 100.0))
        : adjustedSpeed;

    return String(Math.floor(finalSpeed));
}
// ---- source/v1.0/shared/runtime/clusterVisibility.js (applyClusterVisibility) ----
function applyClusterVisibility(enabled) {
    document.documentElement.classList.toggle('cluster-disabled', enabled === false);
}
// ---- source/v1.0/shared/utils/createElement.js ----
/**
 * createElement - A React-like utility for creating DOM elements
 * @param {string} tagName - The HTML tag name (e.g., 'div', 'span', 'button')
 * @param {Object} props - Element properties and attributes
 * @param {...(string|HTMLElement)} children - Child elements or text content
 * @returns {HTMLElement} The created DOM element
 */
function createElement(tagName, props = {}, ...childrenArgs) {
    // Create the element
    const element = document.createElement(tagName);

    // Consolidate children from props and extra arguments
    let children = [];
    if (props && props.children) {
        children = Array.isArray(props.children) ? props.children : [props.children];
        delete props.children;
    }
    children = children.concat(childrenArgs);

    // Handle props
    if (props) {
        // Handle className (convert to class)
        if (props.className) {
            element.className = props.className;
            delete props.className;
        }

        // Handle style object
        if (props.style && typeof props.style === 'object') {
            Object.assign(element.style, props.style);
            delete props.style;
        }

        // Handle event listeners (props starting with 'on')
        Object.keys(props).forEach(key => {
            if (key.startsWith('on') && typeof props[key] === 'function') {
                const eventName = key.slice(2).toLowerCase();
                element.addEventListener(eventName, props[key]);
                delete props[key];
            }
        });

        // Handle other attributes
        Object.keys(props).forEach(key => {
            if (props[key] !== undefined && props[key] !== null) {
                element.setAttribute(key, props[key]);
            }
        });
    }

    // Handle children
    children.forEach(child => {
        if (child === null || child === undefined) {
            return;
        }

        if (typeof child === 'string' || typeof child === 'number') {
            element.appendChild(document.createTextNode(child));
        } else if (child instanceof HTMLElement) {
            element.appendChild(child);
        } else if (Array.isArray(child)) {
            child.forEach(nestedChild => {
                if (nestedChild !== null && nestedChild !== undefined) {
                    if (typeof nestedChild === 'string' || typeof nestedChild === 'number') {
                        element.appendChild(document.createTextNode(nestedChild));
                    } else if (nestedChild instanceof HTMLElement) {
                        element.appendChild(nestedChild);
                    }
                }
            });
        }
    });

    return element;
}

/**
 * Shorthand functions for common elements
 */
const div = (props, ...children) => createElement('div', props, ...children);
const span = (props, ...children) => createElement('span', props, ...children);
const button = (props, ...children) => createElement('button', props, ...children);
const img = (props) => createElement('img', props);
const p = (props, ...children) => createElement('p', props, ...children);
const h1 = (props, ...children) => createElement('h1', props, ...children);
const h2 = (props, ...children) => createElement('h2', props, ...children);
const h3 = (props, ...children) => createElement('h3', props, ...children);

/**
 * Utility for creating elements with common patterns
 */
const createElementWithClasses = (tagName, className, props = {}, ...children) => {
    return createElement(tagName, { ...props, className }, ...children);
};

/**
 * Utility for creating styled elements
 */
const createStyledElement = (tagName, styles, props = {}, ...children) => {
    return createElement(tagName, { ...props, style: styles }, ...children);
};
// ---- source/v1.0/shared/tbt/tbtTurnIcons.js ----
const TBT_TURN_SVGS = {
    TURN_RIGHT: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M20.72,6.46,15.64,2.23A1,1,0,0,0,14,3V5H7a5,5,0,0,0-5,5V20a2,2,0,0,0,2,2H6a2,2,0,0,0,2-2V11h6v2a1,1,0,0,0,1.64.77l5.08-4.23a2,2,0,0,0,0-3.08Z"/></svg>',
    TURN_LEFT: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M17,5H10V3a1,1,0,0,0-1.64-.77L3.28,6.46a2,2,0,0,0,0,3.08l5.08,4.23A1,1,0,0,0,10,13V11h6v9a2,2,0,0,0,2,2h2a2,2,0,0,0,2-2V10A5,5,0,0,0,17,5Z"/></svg>',
    STRAIGHT: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M17.77,8.36,13.54,3.28a2.08,2.08,0,0,0-3.08,0L6.23,8.36A1,1,0,0,0,7,10H9V21a1,1,0,0,0,1,1h4a1,1,0,0,0,1-1V10h2a1,1,0,0,0,.77-1.64Z"/></svg>',
    ROUNDABOUT: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 486.039 486.039" fill="currentColor" aria-hidden="true"><path d="M296.897,41.504c-67.547,0-124.165,47.687-137.954,111.191l-48.547-0.024l0.016-29.836L6.759,182.62l103.593,59.888l0.016-29.836l75.352,0.038c0.007,0,0.011,0,0.016,0l30-30.045c0-44.752,36.41-81.16,81.162-81.16c44.75,0,81.158,36.408,81.158,81.16s-36.408,81.16-81.158,81.16l-30,30v192.214h60V320.617c63.475-13.789,111.158-70.406,111.158-137.952C438.056,104.829,374.731,41.504,296.897,41.504z"/><path d="M450.11,152.439c1.926,9.782,2.945,19.887,2.945,30.226c0,10.035-0.98,20.007-2.889,29.774h29.113v-60H450.11z"/><path d="M326.843,29.397V0h-60.001v29.45c9.856-1.945,19.923-2.945,30.056-2.945C307.136,26.504,317.147,27.506,326.843,29.397z"/><path d="M218.898,200.726l-26.944,26.983l-28.753-0.014c14.085,41.608,47.045,74.504,88.696,88.495v-28.578l27.119-27.121C249.31,253.708,225.852,230.37,218.898,200.726z"/></svg>',
    U_TURN: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M13.5,2A8.5,8.5,0,0,0,5,10.5V14H3a1,1,0,0,0-.77,1.64l5,6a1,1,0,0,0,1.54,0l5-6A1,1,0,0,0,13,14H11V10.5a2.5,2.5,0,0,1,5,0V21a1,1,0,0,0,1,1h4a1,1,0,0,0,1-1V10.5A8.51,8.51,0,0,0,13.5,2Z"/></svg>',
    MERGE: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path d="M17.89 17.707L16.892 20c-3.137-1.366-5.496-3.152-6.892-5.275-1.396 2.123-3.755 3.91-6.892 5.275l-.998-2.293C5.14 16.389 8.55 14.102 8.55 10V7H5.5L10 0l4.5 7h-3.05v3c0 4.102 3.41 6.389 6.44 7.707z"/></svg>',
    FORK: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="m9.78 11.16l-1.42 1.42a7.3 7.3 0 0 1-1.79-2.94l1.94-.49c.32.89.77 1.5 1.27 2.01M11 6L7 2L3 6h3.02c.02.81.08 1.54.19 2.17l1.94-.49C8.08 7.2 8.03 6.63 8.02 6zm10 0l-4-4l-4 4h2.99c-.1 3.68-1.28 4.75-2.54 5.88c-.5.44-1.01.92-1.45 1.55c-.34-.49-.73-.88-1.13-1.24L9.46 13.6c.93.85 1.54 1.54 1.54 3.4v5h2v-5c0-2.02.71-2.66 1.79-3.63c1.38-1.24 3.08-2.78 3.2-7.37z"/></svg>'
};

const TBT_TURN_GLYPHS = {
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

/**
 * Roundabout drawn with its real exit, for when the host sends turn_angle:
 * ring, entry at the bottom, the travelled arc bright (the rest dim) and an
 * arrow where the exit leaves. `angle` is degrees travelled around the ring
 * from the entry (1..360, 180 = straight across), counter-clockwise on screen
 * for right-hand traffic and clockwise when `clockwise`. Same geometry as
 * drawRoundabout in the HUD (HudOverlayModel RB_*). With `exitNumber`, the
 * exits passed before ours are short dim stubs: AA sends only our exit, so the
 * count is real but they are spread evenly between entry and exit (none when
 * closer than 25 degrees). Returns '' when the angle can't be drawn, so the
 * caller keeps the static icon.
 */
function roundaboutSvg(angle, clockwise, exitNumber) {
    if (angle == null || angle === '') return '';
    const a = Number(angle);
    if (!Number.isInteger(a) || a < 1 || a > 360) return '';
    const cx = 12, cy = 12, r = 5, tip = 11, head = 4, half = 3.2;
    const deg = clockwise ? 270 - a : 270 + a;
    const rad = (deg * Math.PI) / 180;
    const dx = Math.cos(rad);
    const dy = -Math.sin(rad);
    const f = (n) => n.toFixed(2);
    const ex = cx + dx * r, ey = cy + dy * r;
    // A full turn starts and ends at the same point, which an SVG arc can't draw.
    const arc = a >= 359
        ? `<circle cx="${cx}" cy="${cy}" r="${r}"/>`
        : `<path d="M${cx} ${cy + r}A${r} ${r} 0 ${a > 180 ? 1 : 0} ${clockwise ? 1 : 0} ${f(ex)} ${f(ey)}"/>`;
    const base = tip - head;
    const bx = cx + dx * base, by = cy + dy * base;
    const n = Number(exitNumber);
    const step = Number.isInteger(n) && n >= 2 ? a / n : 0;
    let stubs = '';
    if (step >= 25) {
        for (let k = 1; k < n; k++) {
            const sr = ((clockwise ? 270 - step * k : 270 + step * k) * Math.PI) / 180;
            const sx = Math.cos(sr), sy = -Math.sin(sr);
            stubs += `<path d="M${f(cx + sx * r)} ${f(cy + sy * r)}L${f(cx + sx * 7.8)} ${f(cy + sy * 7.8)}"/>`;
        }
    }
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true">' +
        '<g fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">' +
        `<g stroke-opacity="0.35"><circle cx="${cx}" cy="${cy}" r="${r}"/>${stubs}</g>` +
        arc +
        `<path d="M${cx} 22.5V${cy + r}"/>` +
        `<path d="M${f(ex)} ${f(ey)}L${f(cx + dx * (base + 0.5))} ${f(cy + dy * (base + 0.5))}"/>` +
        '</g>' +
        `<path fill="currentColor" d="M${f(cx + dx * tip)} ${f(cy + dy * tip)}L${f(bx - dy * half)} ${f(by + dx * half)}L${f(bx + dy * half)} ${f(by - dx * half)}Z"/>` +
        '</svg>';
}

/** pt-BR grouping: 1000 -> "1.000". Values under 1000 are returned unchanged. */
function groupThousands(value) {
    const n = Math.round(Number(value));
    if (!Number.isFinite(n)) return '';
    return String(Math.abs(n)).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

function formatRemainingDistance(remainingM) {
    // JSON null must not become Number(null)===0 → "0 m".
    if (remainingM == null || remainingM === '') return '';
    const meters = Number(remainingM);
    if (!Number.isFinite(meters) || meters < 0) return '';
    if (meters < 1000) return `${Math.round(meters)} m`;
    const km = meters / 1000;
    if (km >= 100) return `${groupThousands(km)} km`;
    return `${km.toFixed(1)} km`;
}

/**
 * Trip remaining time. Host owns AA/Waze quirks and must publish a usable
 * remaining_s (or omit/null it). Themes must not treat JSON null as 0 —
 * Number(null) === 0 in JS and showed as "<1 min" / "0 m".
 */
function formatTripEta(remainingS, remainingM) {
    if (remainingS != null && remainingS !== '') {
        const seconds = Number(remainingS);
        if (Number.isFinite(seconds) && seconds >= 0) {
            if (seconds < 45) return '<1 min';
            const minutes = Math.round(seconds / 60);
            if (minutes < 60) return `${minutes} min`;
            const hours = Math.floor(minutes / 60);
            const rest = minutes % 60;
            return rest ? `${hours} h ${rest} min` : `${hours} h`;
        }
    }
    return formatRemainingDistance(remainingM);
}

/**
 * Arrival clock. Prefer host `eta` ("HH:mm") when present; otherwise now + remaining_s.
 */
function formatArrivalClock(remainingS, now = new Date(), etaClock) {
    const raw = String(etaClock || '').trim();
    const match = /^(\d{1,2}):(\d{2})$/.exec(raw);
    if (match) {
        const hour = Number(match[1]);
        const minute = Number(match[2]);
        if (Number.isFinite(hour) && Number.isFinite(minute) && hour <= 23 && minute <= 59) {
            return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
        }
    }
    if (remainingS == null || remainingS === '') return '';
    const seconds = Number(remainingS);
    if (!Number.isFinite(seconds) || seconds < 0) return '';
    const eta = new Date(now.getTime() + seconds * 1000);
    const hrs = String(eta.getHours()).padStart(2, '0');
    const mins = String(eta.getMinutes()).padStart(2, '0');
    return `${hrs}:${mins}`;
}

/**
 * Folds a precise maneuver token onto the icon set that actually exists here.
 *
 * The telemetry now names maneuvers exactly (NAME_CHANGE, SLIGHT_LEFT,
 * ON_RAMP_RIGHT, ...) because the vendor enum carries that detail, but
 * TBT_TURN_SVGS only holds the broad shapes. Without this every precise token
 * missed the table and fell through to the generic arrow — a NAME_CHANGE, which
 * should read as "carry straight on", drew a plain chevron.
 *
 * Dedicated artwork for the slight / sharp / ramp variants is still worth doing;
 * until then they resolve to the nearest shape that exists.
 */
function resolveTurnIcon(turn) {
    const token = String(turn || '').toUpperCase();
    if (!token) return '';
    if (TBT_TURN_SVGS[token] || TBT_TURN_GLYPHS[token]) return token;
    // Carrying on along the same road, or departing, reads as straight ahead.
    if (token === 'NAME_CHANGE' || token === 'DEPART') return 'STRAIGHT';
    if (token.startsWith('U_TURN')) return 'U_TURN';
    if (token.startsWith('ROUNDABOUT')) return 'ROUNDABOUT';
    if (token.startsWith('DESTINATION')) return 'DESTINATION';
    if (token.startsWith('FERRY')) return 'STRAIGHT';
    if (token.startsWith('FORK')) return 'FORK';
    if (token.startsWith('MERGE')) return 'MERGE';
    if (token.startsWith('OFF_RAMP')) return 'EXIT';
    if (token.startsWith('ON_RAMP')) return 'MERGE';
    // Everything left is a plain turn of some sharpness; keep the side.
    if (token.endsWith('_LEFT')) return 'TURN_LEFT';
    if (token.endsWith('_RIGHT')) return 'TURN_RIGHT';
    return token;
}
// ---- source/v1.0/shared/tbt/tbtLanes.js ----
/**
 * Lane-guidance icons for the shared TBT card.
 *
 * `lanes` comes from app.navigation.directions: one array per lane, left to
 * right, each holding `{ s, h }` arrows. `s` is Google's LaneDirection.Shape
 * number, passed through unmapped by Impulse; `h` marks the arrows the route
 * uses. Older hosts omit the field, which renders as no lane row.
 */

// 24x24 box, the car enters bottom-centre. Right-hand shapes only; the left
// ones are the same paths mirrored. Each path ends at its arrow tip; `angle`
// is the travel direction at the tip, clockwise from straight up.
const RIGHT_SHAPES = {
    straight: { d: 'M12 22V3', tip: [12, 3], angle: 0 },
    slight: { d: 'M12 22V14L19 7', tip: [19, 7], angle: 45 },
    normal: { d: 'M12 22V13Q12 8 17 8H21', tip: [21, 8], angle: 90 },
    sharp: { d: 'M12 22V9L19 16', tip: [19, 16], angle: 135 },
    uturn: { d: 'M8 22V10A4 4 0 0 1 16 10V17', tip: [16, 17], angle: 180 }
};

// LaneDirection.Shape: 1 STRAIGHT, 2/3 SLIGHT, 4/5 NORMAL, 6/7 SHARP, 8/9 U_TURN (L/R).
const SHAPE_BY_NUMBER = {
    1: ['straight', false],
    2: ['slight', true],
    3: ['slight', false],
    4: ['normal', true],
    5: ['normal', false],
    6: ['sharp', true],
    7: ['sharp', false],
    8: ['uturn', true],
    9: ['uturn', false]
};

const HEAD_LENGTH = 5.5;
const HEAD_SPREAD_DEG = 40;

/** Two wings swept back from the tip, as a single open path segment. */
function arrowHead([x, y], angleDeg) {
    const rad = (angleDeg * Math.PI) / 180;
    // Unit vector pointing back along the path from the tip.
    const bx = -Math.sin(rad);
    const by = Math.cos(rad);
    const wing = (spreadDeg) => {
        const t = (spreadDeg * Math.PI) / 180;
        const wx = bx * Math.cos(t) - by * Math.sin(t);
        const wy = bx * Math.sin(t) + by * Math.cos(t);
        return `${(x + wx * HEAD_LENGTH).toFixed(1)} ${(y + wy * HEAD_LENGTH).toFixed(1)}`;
    };
    return `M${wing(HEAD_SPREAD_DEG)}L${x} ${y}L${wing(-HEAD_SPREAD_DEG)}`;
}

// Built once at load: full path (stem + head) per right-hand shape.
const SHAPE_PATHS = Object.fromEntries(
    Object.entries(RIGHT_SHAPES).map(([name, s]) => [name, `${s.d}${arrowHead(s.tip, s.angle)}`])
);

function arrowMarkup(shapeNumber, highlighted) {
    const entry = SHAPE_BY_NUMBER[shapeNumber];
    if (!entry) return '';
    const [name, mirrored] = entry;
    const transform = mirrored ? ' transform="matrix(-1 0 0 1 24 0)"' : '';
    const cls = highlighted ? 'is-on' : 'is-off';
    return `<g class="${cls}"${transform}><path d="${SHAPE_PATHS[name]}"/></g>`;
}

/** One lane's SVG. Unused arrows are drawn first so the route's arrow sits on top. */
function laneSvg(lane) {
    const arrows = Array.isArray(lane) ? lane : [];
    const known = arrows.filter((a) => a && SHAPE_BY_NUMBER[Number(a.s)]);
    const off = known.filter((a) => !a.h).map((a) => arrowMarkup(Number(a.s), false));
    const on = known.filter((a) => a.h).map((a) => arrowMarkup(Number(a.s), true));
    // A lane Google sent with no recognisable arrow still holds its slot.
    const body = known.length ? off.concat(on).join('') : arrowMarkup(1, false);
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
        'stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        `${body}</svg>`;
}

/** Normalized lanes, or [] for anything missing or malformed. */
function readLanes(directions) {
    const lanes = directions && directions.lanes;
    return Array.isArray(lanes) ? lanes.filter(Array.isArray) : [];
}
// ---- source/v1.0/shared/tbt/tbtCard.js ----
/**
 * Turn-by-turn card shared by the v1.0 contract themes (minimalist, default).
 *
 * Deliberately stateless: it builds the DOM and renders whatever directions it
 * is handed, and holds no subscriptions. Each theme wires its own state to
 * update(), so the themes show one card rather than copies that drift apart.
 * Styles live alongside in tbtCard.css.
 *
 * setRadar() hands it the host's speed-camera alert (app.navigation.radar,
 * additive in v1.0). While one is active the card shows the camera instead
 * of the maneuver (a speed-limit sign and the distance), even with no route,
 * and goes back to the guidance when it ends.
 */
function createTbtCard() {
    const strip = div({ className: 'dashboard-tbt-strip' });
    const maneuver = div({ className: 'dashboard-tbt-maneuver' });
    const glyph = span({ className: 'dashboard-tbt-glyph' });
    // Roundabout exit to take, drawn inside the roundabout icon's ring
    // (tbtCard.css). Absolutely placed in the 88px glyph, so it never moves
    // anything. exit_number is additive in v1.0: an older host omits it.
    const exitBadge = span({ className: 'dashboard-tbt-exit' });
    const turnDistance = span({ className: 'dashboard-tbt-distance' });
    const text = div({ className: 'dashboard-tbt-text' });
    const street = span({ className: 'dashboard-tbt-street' });
    // Lane guidance rides in a sub-card behind the bottom edge (tbtCard.css),
    // hidden when Google sends none, so the main card never resizes.
    const lanesRow = div({ className: 'dashboard-tbt-lanes' });
    lanesRow.style.display = 'none';
    let lanesKey = '';
    const remaining = div({ className: 'dashboard-tbt-remaining' });

    // Each figure is a fixed-width column with a small caption underneath, so
    // the three hold their place as the values change length.
    const makeMetric = (modifier, caption) => {
        const cell = div({ className: `dashboard-tbt-metric ${modifier}` });
        const value = span({ className: 'dashboard-tbt-metric-value' });
        const label = span({ className: 'dashboard-tbt-metric-label' });
        label.textContent = caption;
        cell.appendChild(value);
        cell.appendChild(label);
        return { cell, value };
    };
    const dist = makeMetric('is-dist', 'DISTÂNCIA');
    const eta = makeMetric('is-eta', 'TEMPO');
    const arrival = makeMetric('is-arrival', 'CHEGADA');

    maneuver.appendChild(glyph);
    maneuver.appendChild(turnDistance);
    remaining.appendChild(dist.cell);
    remaining.appendChild(eta.cell);
    remaining.appendChild(arrival.cell);
    text.appendChild(street);
    text.appendChild(remaining);
    strip.appendChild(maneuver);
    strip.appendChild(text);
    strip.appendChild(lanesRow);
    strip.style.display = 'none';

    const applyTurnGlyph = (rawTurn, exitNumber, exitAngle, clockwise) => {
        // Precise maneuver tokens are folded onto the shapes that exist.
        const turn = resolveTurnIcon(rawTurn);
        // With turn_angle the roundabout is drawn with its real exit; without
        // it (older host, or AA sent none) the static icon stays.
        const dynamic = turn === 'ROUNDABOUT'
            ? roundaboutSvg(exitAngle, clockwise === true, readExitNumber(exitNumber))
            : '';
        const svg = dynamic || TBT_TURN_SVGS[turn];
        if (svg) {
            glyph.classList.add('is-svg');
            glyph.innerHTML = svg;
        } else {
            glyph.classList.remove('is-svg');
            glyph.textContent = TBT_TURN_GLYPHS[turn] || '➤';
        }
        const exit = svg && turn === 'ROUNDABOUT' ? readExitNumber(exitNumber) : 0;
        if (!exit) return;
        exitBadge.textContent = String(exit);
        exitBadge.classList.toggle('is-two-digits', exit >= 10);
        exitBadge.classList.toggle('is-dynamic', !!dynamic);
        glyph.appendChild(exitBadge);
    };

    // ~1 Hz updates repeat the same lanes; only touch the DOM when they change.
    const applyLanes = (lanes) => {
        const key = lanes.length ? JSON.stringify(lanes) : '';
        if (key === lanesKey) return;
        lanesKey = key;
        if (!key) {
            lanesRow.style.display = 'none';
            lanesRow.innerHTML = '';
            return;
        }
        lanesRow.innerHTML = lanes
            .map((lane) => {
                const used = lane.some((a) => a && a.h);
                return `<span class="dashboard-tbt-lane${used ? ' is-used' : ''}">${laneSvg(lane)}</span>`;
            })
            .join('');
        lanesRow.style.display = 'flex';
    };

    let lastDirections = null;
    let radar = null;

    const isActive = (value) => !!value && (value.active === true || value.active === 'true');

    // Speed camera ahead: the limit sign in the glyph slot, the distance under it,
    // "Radar · 60 km/h" in the street line; the trip figures step aside.
    const renderRadar = (r) => {
        strip.style.display = 'flex';
        strip.classList.add('is-radar');
        strip.classList.toggle('is-over', r.over === true || r.over === 'true');
        const limit = readRadarLimit(r.limit_kmh);
        glyph.classList.add('is-svg');
        glyph.innerHTML = radarSignSvg(limit);
        street.textContent = limit ? `Radar · ${limit} km/h` : 'Radar';
        turnDistance.textContent = formatRadarDistance(r.distance_m);
        lanesKey = '';
        lanesRow.style.display = 'none';
        lanesRow.innerHTML = '';
    };

    const update = (directions) => {
        lastDirections = directions;
        if (isActive(radar)) {
            renderRadar(radar);
            return;
        }
        strip.classList.remove('is-radar', 'is-over');
        const d = directions || {};
        const active = d.active === true || d.active === 'true';
        strip.style.display = active ? 'flex' : 'none';
        if (!active) return;
        applyTurnGlyph(String(d.turn || '').toUpperCase(), d.exit_number, d.turn_angle, d.roundabout_cw);
        street.textContent = d.street || '';
        applyLanes(readLanes(d));
        const distanceM = d.distance_m;
        const hasDistanceM = distanceM != null && distanceM !== '' && Number.isFinite(Number(distanceM));
        turnDistance.textContent = d.distance ||
            (hasDistanceM ? `${Math.round(Number(distanceM))} m` : '');
        // A missing figure hides its whole column, caption included, but keeps
        // its third so the others stay anchored left / centre / right.
        const remainingDist = formatRemainingDistance(d.remaining_m);
        dist.value.textContent = remainingDist;
        dist.cell.style.visibility = remainingDist ? '' : 'hidden';
        // Host normalizes AA/Waze into remaining_s + eta; theme only formats.
        const remainingTime = formatTripEta(d.remaining_s, d.remaining_m);
        eta.value.textContent = remainingTime;
        eta.cell.style.visibility = remainingTime ? '' : 'hidden';
        const arrivalClock = formatArrivalClock(d.remaining_s, new Date(), d.eta);
        arrival.value.textContent = arrivalClock;
        arrival.cell.style.visibility = arrivalClock ? '' : 'hidden';
    };

    const setRadar = (value) => {
        const wasActive = isActive(radar);
        radar = value || null;
        if (isActive(radar)) renderRadar(radar);
        else if (wasActive) update(lastDirections);
    };

    return { element: strip, update, setRadar };
}

/** Limit inside the sign: a whole km/h 10..150, else 0 (camera icon instead). */
function readRadarLimit(raw) {
    if (raw == null || raw === '') return 0;
    const n = Number(raw);
    return Number.isInteger(n) && n >= 10 && n <= 150 ? n : 0;
}

/** "340 m" in 10 m steps, "1,2 km" from 1 km (pt-BR, as the other figures). */
function formatRadarDistance(raw) {
    if (raw == null || raw === '') return '';
    const m = Number(raw);
    if (!Number.isFinite(m)) return '';
    if (m < 1000) return `${Math.max(0, Math.round(m / 10) * 10)} m`;
    return `${(Math.round(m / 100) / 10).toFixed(1).replace('.', ',')} km`;
}

/**
 * Brazilian speed-limit sign (R-19): white disc, red ring, the limit in black,
 * and a small camera badge on its lower right. Without a limit, a camera.
 */
function radarSignSvg(limit) {
    const camera = (cx, cy, s) => {
        const w = s * 0.8;
        const h = s * 0.55;
        const top = cy - h / 2 + s * 0.06;
        return `<rect x="${cx - w / 2}" y="${top}" width="${w}" height="${h}" rx="${s * 0.08}" fill="none" stroke="#fff" stroke-width="${s * 0.09}"/>` +
            `<circle cx="${cx}" cy="${top + h / 2}" r="${h * 0.3}" fill="none" stroke="#fff" stroke-width="${s * 0.09}"/>` +
            `<path d="M${cx - w * 0.15} ${top}L${cx - w * 0.05} ${top - s * 0.1}H${cx + w * 0.15}L${cx + w * 0.25} ${top}" fill="none" stroke="#fff" stroke-width="${s * 0.09}" stroke-linejoin="round"/>`;
    };
    const ring = '<circle cx="12" cy="12" r="10.4" fill="#fff" stroke="#e53935" stroke-width="2.6"/>';
    if (!limit) {
        return `<svg viewBox="0 0 24 24" class="dashboard-tbt-radar-sign">` +
            '<circle cx="12" cy="12" r="10.4" fill="#1b1f27" stroke="#e53935" stroke-width="2.6"/>' +
            camera(12, 12, 11) + '</svg>';
    }
    const size = limit >= 100 ? 8.2 : 10;
    return `<svg viewBox="0 0 24 24" class="dashboard-tbt-radar-sign">${ring}` +
        `<text x="12" y="12" text-anchor="middle" dominant-baseline="central" font-size="${size}" font-weight="700" fill="#111">${limit}</text>` +
        '<circle cx="19.6" cy="19.6" r="4.4" fill="#1b1f27" stroke="#fff" stroke-width="0.6"/>' +
        camera(19.6, 19.6, 5.2) + '</svg>';
}

/** Roundabout exit from exit_number: a whole number 1..20, else 0 (none). */
function readExitNumber(raw) {
    // Number(null) === 0, so null / '' are caught before converting.
    if (raw == null || raw === '') return 0;
    const n = Number(raw);
    return Number.isInteger(n) && n >= 1 && n <= 20 ? n : 0;
}

/** Host radar alert JSON (app.navigation.radar); unreadable = no alert. */
function parseRadarAlert(raw) {
    return parseNavigationDirections(raw);
}

/** Bridge values arrive as JSON strings; anything unreadable means no guidance. */
function parseNavigationDirections(raw) {
    if (raw == null || raw === '') return { active: false };
    if (typeof raw === 'object') return raw;
    try {
        return JSON.parse(raw);
    } catch (e) {
        return { active: false };
    }
}
window.ApexShared = { createAcFocusPreference: createAcFocusPreference, getAdjustedSpeed: getAdjustedSpeed, applyClusterVisibility: applyClusterVisibility, createTbtCard: createTbtCard, parseNavigationDirections: parseNavigationDirections, parseRadarAlert: parseRadarAlert };
})();
