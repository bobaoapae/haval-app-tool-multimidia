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
export function laneSvg(lane) {
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
export function readLanes(directions) {
    const lanes = directions && directions.lanes;
    return Array.isArray(lanes) ? lanes.filter(Array.isArray) : [];
}
