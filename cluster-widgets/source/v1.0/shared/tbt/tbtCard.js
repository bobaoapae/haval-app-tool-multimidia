import { div, span } from '../utils/createElement.js';
import {
    TBT_TURN_GLYPHS,
    TBT_TURN_SVGS,
    formatArrivalClock,
    formatRemainingDistance,
    formatTripEta,
    resolveTurnIcon
} from './tbtTurnIcons.js';

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
    const turnDistance = span({ className: 'dashboard-tbt-distance' });
    const text = div({ className: 'dashboard-tbt-text' });
    const street = span({ className: 'dashboard-tbt-street' });
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
    strip.style.display = 'none';

    const applyTurnGlyph = (rawTurn) => {
        // Precise maneuver tokens are folded onto the shapes that exist.
        const turn = resolveTurnIcon(rawTurn);
        const svg = TBT_TURN_SVGS[turn];
        if (svg) {
            glyph.classList.add('is-svg');
            glyph.innerHTML = svg;
            return;
        }
        glyph.classList.remove('is-svg');
        glyph.textContent = TBT_TURN_GLYPHS[turn] || '➤';
    };

    const update = (directions) => {
        const d = directions || {};
        const active = d.active === true || d.active === 'true';
        strip.style.display = active ? 'flex' : 'none';
        if (!active) return;
        applyTurnGlyph(String(d.turn || '').toUpperCase());
        street.textContent = d.street || '';
        turnDistance.textContent = d.distance ||
            (Number.isFinite(Number(d.distance_m)) ? `${Math.round(Number(d.distance_m))} m` : '');
        // A missing figure hides its whole column, caption included.
        const remainingDist = formatRemainingDistance(d.remaining_m);
        dist.value.textContent = remainingDist;
        dist.cell.style.display = remainingDist ? '' : 'none';
        const remainingTime = formatTripEta(d.remaining_s);
        eta.value.textContent = remainingTime;
        eta.cell.style.display = remainingTime ? '' : 'none';
        const arrivalClock = formatArrivalClock(d.remaining_s);
        arrival.value.textContent = arrivalClock;
        arrival.cell.style.display = arrivalClock ? '' : 'none';
    };

    return { element: strip, update };
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
