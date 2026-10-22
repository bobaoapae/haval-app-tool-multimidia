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
 *
 * setRadar() hands it the host's speed-camera alert (app.navigation.radar,
 * additive in v1.0). While one is active the card shows the camera instead
 * of the maneuver (a speed-limit sign and the distance), even with no route,
 * and goes back to the guidance when it ends.
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
export function readRadarLimit(raw) {
    if (raw == null || raw === '') return 0;
    const n = Number(raw);
    return Number.isInteger(n) && n >= 10 && n <= 150 ? n : 0;
}

/** "340 m" in 10 m steps, "1,2 km" from 1 km (pt-BR, as the other figures). */
export function formatRadarDistance(raw) {
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
export function radarSignSvg(limit) {
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
export function readExitNumber(raw) {
    // Number(null) === 0, so null / '' are caught before converting.
    if (raw == null || raw === '') return 0;
    const n = Number(raw);
    return Number.isInteger(n) && n >= 1 && n <= 20 ? n : 0;
}

/** Host radar alert JSON (app.navigation.radar); unreadable = no alert. */
export function parseRadarAlert(raw) {
    return parseNavigationDirections(raw);
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
