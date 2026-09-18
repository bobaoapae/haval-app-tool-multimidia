import { getState, subscribe } from '../state.js';
import { div } from '../../../../shared/utils/createElement.js';

/**
 * Traction indicator for the bottom bar, between the temperature readout
 * and the fuel gauge. Hidden by the `showTractionIcon` theme config.
 *
 * Reads left to right as `I-[||||]-I`, front of the car on the left, with the
 * engine sitting between the front axle's two tyres rather than ahead of it:
 *
 *   - each axle drawn as a roman "I" — an upright stroke with a serif at top
 *     and bottom, the serifs being that axle's two tyres
 *   - the traction battery amidships, four bars for the level, the next bar
 *     up pulsing while the pack is charging
 *   - a short shaft joining each axle to the pack
 *   - the engine block, painted over the middle of the front axle's upright:
 *     dark idle/off, white firing, green while firing during regen (engine
 *     braking) — regen with the engine off stays dark, not green
 *
 * Each "I" and its shaft take that axle's colour:
 *   grey   - idle, that axle is doing nothing
 *   white  - consuming (the axle is driving the car)
 *   green  - regenerating (the axle is recovering energy)
 * One active axle reads as 4x2, both as 4x4, with nothing to read.
 *
 * Purely a renderer. The decode lives natively in PowerFlowMapper /
 * PowerFlowTracker, published as `haval.power.flow` (v1|state|ice|front|rear)
 * and unpacked by carDerivations.js into powerState / powerIce / powerFront /
 * powerRear. front/rear are 1 driving, -1 regenerating, 0 off.
 */

const SVG_NS = 'http://www.w3.org/2000/svg';

const VIEW_W = 86;
const VIEW_H = 34;

const MID_Y = 17;
const TYRE_TOP_Y = 6;
const TYRE_BOTTOM_Y = 28;
const TYRE_HALF_W = 5;

const FRONT_AXLE_X = 22;
const REAR_AXLE_X = 76;

// Engine box: same 11x14 proportions as before, now centred on the front
// axle instead of sitting ahead of it, so it reads as sitting between the
// two front tyres rather than at the nose. The axle upright is drawn first
// and runs full height regardless; the engine paints over its middle third.
const ENGINE_W = 11;
const ENGINE_H = 14;
const ENGINE_X = FRONT_AXLE_X - ENGINE_W / 2;
const ENGINE_Y = MID_Y - ENGINE_H / 2;

const BATTERY_X = 34;
const BATTERY_Y = 7;
const BATTERY_W = 30;
const BATTERY_H = 20;
const BATTERY_BARS = 4;

/** Below this the pack is critical and the last remaining bar turns red. */
const BATTERY_CRITICAL_PCT = 10;

function svg(tag, attrs) {
    const el = document.createElementNS(SVG_NS, tag);
    Object.keys(attrs || {}).forEach((k) => el.setAttribute(k, attrs[k]));
    return el;
}

/** `1` -> 'consume', `-1` -> 'regen', anything else -> '' (idle). */
function axleClass(dir) {
    const n = Number(dir);
    if (n > 0) return 'consume';
    if (n < 0) return 'regen';
    return '';
}

/**
 * One axle: the roman "I" (upright plus the two tyre serifs) and the shaft
 * linking it to the battery. Grouped so a single class swap recolours the axle,
 * its tyres and its shaft together.
 *
 * @param {string} className 'front' or 'rear'
 * @param {number} axleX     where the upright sits
 * @param {number} shaftToX  the battery edge this axle connects to
 */
function createAxle(className, axleX, shaftToX) {
    const group = svg('g', { class: `pf-axle ${className}` });

    group.appendChild(svg('line', {
        class: 'pf-shaft',
        x1: axleX, y1: MID_Y, x2: shaftToX, y2: MID_Y
    }));

    group.appendChild(svg('line', {
        class: 'pf-upright',
        x1: axleX, y1: TYRE_TOP_Y, x2: axleX, y2: TYRE_BOTTOM_Y
    }));

    [TYRE_TOP_Y, TYRE_BOTTOM_Y].forEach((y) => {
        group.appendChild(svg('line', {
            class: 'pf-tyre',
            x1: axleX - TYRE_HALF_W, y1: y, x2: axleX + TYRE_HALF_W, y2: y
        }));
    });

    return group;
}

/** Pack gauge: an outlined box holding four level bars. */
function createBattery() {
    const group = svg('g', { class: 'pf-battery' });

    group.appendChild(svg('rect', {
        class: 'pf-battery-shell',
        x: BATTERY_X, y: BATTERY_Y, width: BATTERY_W, height: BATTERY_H, rx: 2
    }));

    // Fit the bars to the shell rather than hard-coding positions, so the box
    // can be resized without the bars drifting off-centre.
    const padX = 3.4;
    const padY = 3.6;
    const gap = 2;
    const usable = BATTERY_W - padX * 2;
    const barW = (usable - gap * (BATTERY_BARS - 1)) / BATTERY_BARS;

    const bars = [];
    for (let i = 0; i < BATTERY_BARS; i++) {
        const bar = svg('rect', {
            class: 'pf-battery-bar',
            x: BATTERY_X + padX + i * (barW + gap),
            y: BATTERY_Y + padY,
            width: barW,
            height: BATTERY_H - padY * 2,
            rx: 0.8
        });
        group.appendChild(bar);
        bars.push(bar);
    }

    return { group, bars };
}

export function createPowerFlowIcon() {
    const container = div({ className: 'dashboard-power-flow' });

    const root = svg('svg', {
        class: 'power-flow-svg',
        viewBox: `0 0 ${VIEW_W} ${VIEW_H}`,
        'aria-hidden': 'true'
    });

    const engine = svg('rect', {
        class: 'pf-engine',
        x: ENGINE_X, y: ENGINE_Y, width: ENGINE_W, height: ENGINE_H, rx: 1.5
    });

    const frontAxle = createAxle('front', FRONT_AXLE_X, BATTERY_X);
    const rearAxle = createAxle('rear', REAR_AXLE_X, BATTERY_X + BATTERY_W);
    const battery = createBattery();

    // Front axle first so the engine paints over its middle third — the
    // upright still runs full height underneath, tyres stay clear above
    // and below the block.
    root.appendChild(frontAxle);
    root.appendChild(rearAxle);
    root.appendChild(engine);
    root.appendChild(battery.group);
    container.appendChild(root);

    let lastFlowSignature = null;
    let lastBatterySignature = null;

    // Native only publishes on change, but card entry replays the last value
    // and the same payload can arrive twice. Skipping identical renders keeps a
    // pointless style recalc out of the cluster's hot path.
    const renderFlow = () => {
        const state = String(getState('powerState') || 'idle');
        const ice = getState('powerIce') === true;
        const front = axleClass(getState('powerFront'));
        const rear = axleClass(getState('powerRear'));

        const signature = `${state}|${front}|${rear}|${ice}`;
        if (signature === lastFlowSignature) return;
        lastFlowSignature = signature;

        frontAxle.setAttribute('class', `pf-axle front${front ? ' ' + front : ''}`);
        rearAxle.setAttribute('class', `pf-axle rear${rear ? ' ' + rear : ''}`);
        // `ice` is RPM-gated natively, so it means the engine is actually
        // firing rather than merely that the ignition is on. Regen only
        // overrides the colour while the engine is actually spinning (engine
        // braking, or 13 "energy recovery + driving charging") — colouring it
        // white there would read as "producing power" during the one state
        // where it is doing the opposite. When the engine is off, regen is
        // happening purely electrically and the block must stay idle, not
        // green — green implies the ICE itself is recovering energy.
        const engineClass = state === 'regen' && ice ? ' regen' : ice ? ' on' : '';
        engine.setAttribute('class', `pf-engine${engineClass}`);
        container.setAttribute('data-tone', state);
    };

    const renderBattery = () => {
        const raw = Number(getState('batteryPercent'));
        const pct = Number.isFinite(raw) ? Math.max(0, Math.min(100, raw)) : 0;

        // Quarter per bar, but any charge at all keeps one bar lit — a pack at
        // 4% should still show something, and that bar is the one that turns
        // red as the critical warning.
        const lit = pct <= 0 ? 0 : Math.max(1, Math.min(BATTERY_BARS, Math.ceil(pct / (100 / BATTERY_BARS))));
        const critical = pct > 0 && pct < BATTERY_CRITICAL_PCT;

        // While charging, the bar above the current level pulses — the usual
        // "filling up" cue. At a full pack there is no bar above, so the top one
        // pulses in place instead of the animation silently disappearing.
        const charging = String(getState('powerState') || '') === 'charge';
        const pulseIndex = charging ? Math.min(lit, BATTERY_BARS - 1) : -1;

        const signature = `${lit}|${critical}|${pulseIndex}`;
        if (signature === lastBatterySignature) return;
        lastBatterySignature = signature;

        battery.bars.forEach((bar, i) => {
            const on = i < lit;
            const isLast = i === lit - 1;
            const classes = ['pf-battery-bar'];
            if (on) classes.push(critical && isLast ? 'on critical' : 'on');
            if (i === pulseIndex) classes.push('pulse');
            bar.setAttribute('class', classes.join(' '));
        });
    };

    renderFlow();
    renderBattery();

    const subscriptions = [
        subscribe('powerState', renderFlow),
        subscribe('powerState', renderBattery),
        subscribe('powerIce', renderFlow),
        subscribe('powerFront', renderFlow),
        subscribe('powerRear', renderFlow),
        subscribe('batteryPercent', renderBattery)
    ];

    const cleanup = () => subscriptions.forEach((unsubscribe) => unsubscribe());

    return { element: container, cleanup };
}
