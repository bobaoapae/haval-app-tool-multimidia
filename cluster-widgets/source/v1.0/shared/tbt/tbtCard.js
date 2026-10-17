import { div, span } from '../utils/createElement.js';
import {
    TBT_TURN_GLYPHS,
    TBT_TURN_SVGS,
    formatArrivalClock,
    formatRemainingDistance,
    formatTripEta,
    resolveTurnIcon,
    roundaboutSvg
} from './tbtTurnIcons.js';
import { laneSvg, readLanes } from './tbtLanes.js';

/**
 * Turn-by-turn card shared by the v1.0 contract themes (minimalist, default).
 *
 * Deliberately stateless: it builds the DOM and renders whatever directions it
 * is handed, and holds no subscriptions. Each theme wires its own state to
 * update(), so the themes show one card rather than copies that drift apart.
 * Styles live alongside in tbtCard.css.
 */
export function createTbtCard() {
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

    const update = (directions) => {
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

    return { element: strip, update };
}

/** Roundabout exit from exit_number: a whole number 1..20, else 0 (none). */
export function readExitNumber(raw) {
    // Number(null) === 0, so null / '' are caught before converting.
    if (raw == null || raw === '') return 0;
    const n = Number(raw);
    return Number.isInteger(n) && n >= 1 && n <= 20 ? n : 0;
}

/** Bridge values arrive as JSON strings; anything unreadable means no guidance. */
export function parseNavigationDirections(raw) {
    if (raw == null || raw === '') return { active: false };
    if (typeof raw === 'object') return raw;
    try {
        return JSON.parse(raw);
    } catch (e) {
        return { active: false };
    }
}
