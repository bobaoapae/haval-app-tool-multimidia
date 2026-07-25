/**
 * Minimalist AC screen — split top/bottom linear gauges.
 *
 * TOP:  Fan value + 1–7 ticks above segmented gauge
 * ─── separator ───
 * BOTTOM: LO–HI ticks + gauge above large temp value
 *
 * White digits/gauges; green accents on focus / status ON.
 */
import { stateManager, subscribe, setState } from '../../state.js';
import { div, span } from '../../../../../shared/utils/createElement.js';

const FAN_MAX = 7;
const TEMP_MIN = 16;
const TEMP_MAX = 32;

function formatTempDisplay(temp, unit) {
    if (temp === null || temp === undefined || temp === '--' || temp === -1 || temp === 255 || Number.isNaN(Number(temp))) {
        return '--';
    }
    const n = parseFloat(temp);
    if (n >= 85 || n <= -40) return '--';
    if (n <= TEMP_MIN) return 'LO';
    if (n >= TEMP_MAX) return 'HI';
    const u = unit === '°C' || unit === '°F' ? unit : (unit || '°');
    return `${n.toFixed(1)}${u}`;
}

function createSegmentGauge(count) {
    const track = div({ className: 'ac-seg-gauge' });
    const segs = [];
    for (let i = 0; i < count; i++) {
        const seg = div({ className: 'ac-seg-gauge-item' });
        track.appendChild(seg);
        segs.push(seg);
    }
    return {
        element: track,
        setLevel(level) {
            const n = Math.max(0, Math.min(count, Math.floor(Number(level) || 0)));
            segs.forEach((seg, i) => {
                seg.classList.toggle('is-on', i < n);
            });
        },
    };
}

function createLinearFillGauge() {
    const fill = div({ className: 'ac-linear-fill' });
    const track = div({ className: 'ac-linear-track', children: [fill] });
    return {
        element: track,
        setRatio(ratio) {
            const r = Math.max(0, Math.min(1, Number(ratio) || 0));
            fill.style.width = `${(r * 100).toFixed(1)}%`;
        },
    };
}

function makePill(className, label, on) {
    return span({
        className: `ac-pill ${className}${on ? ' is-on' : ''}`,
        children: [label],
    });
}

export function createAcControlScreen() {
    const main = div({ className: 'main-container ac-main' });
    const panel = div({ className: 'ac-minimal-panel' });

    // ── TOP: Fan — label, value, ticks 1–7, then gauge ─────────
    const fanLabel = span({ className: 'ac-section-label', children: ['FAN'] });
    const fanValue = span({
        className: 'ac-section-value',
        children: [String(stateManager.get('fan') ?? '—')],
    });
    const fanGauge = createSegmentGauge(FAN_MAX);
    fanGauge.setLevel(stateManager.get('fan'));

    const fanTicks = div({ className: 'ac-gauge-ticks' });
    for (let i = 1; i <= FAN_MAX; i++) {
        fanTicks.appendChild(span({ className: 'ac-tick', children: [String(i)] }));
    }

    const fanSection = div({
        className: 'ac-section ac-section-fan',
        children: [
            fanLabel,
            fanValue,
            fanTicks,          // 1–7 above gauge
            fanGauge.element,
        ],
    });
    if (stateManager.get('focusArea') === 'fan') {
        fanSection.classList.add('is-focused');
    }

    const separator = div({ className: 'ac-separator' });

    // ── BOTTOM: Temp — label, LO/HI selector ticks, gauge, value ─
    const tempLabel = span({ className: 'ac-section-label', children: ['TEMP'] });
    const tempValue = span({
        className: 'ac-section-value ac-temp-value',
        children: [formatTempDisplay(stateManager.get('temp'), stateManager.get('tempUnit'))],
    });
    const tempGauge = createLinearFillGauge();
    const tempNow = parseFloat(stateManager.get('temp'));
    if (Number.isFinite(tempNow)) {
        tempGauge.setRatio((tempNow - TEMP_MIN) / (TEMP_MAX - TEMP_MIN));
    }

    const tempTicks = div({
        className: 'ac-gauge-ticks ac-temp-ticks',
        children: [
            span({ className: 'ac-tick', children: ['LO'] }),
            span({ className: 'ac-tick ac-tick-mid', children: ['22'] }),
            span({ className: 'ac-tick', children: ['HI'] }),
        ],
    });

    // Gauge + ticks below gauge, big value, then TEMP label underneath
    const tempSection = div({
        className: 'ac-section ac-section-temp',
        children: [
            tempGauge.element,
            tempTicks,
            tempValue,
            tempLabel,
        ],
    });
    if (stateManager.get('focusArea') === 'temp') {
        tempSection.classList.add('is-focused');
    }

    // ── Footer: power / auto / max auto / recycle (no IN/OUT) ──
    const powerPill = makePill('ac-pill-power', stateManager.get('power') === 1 ? 'AC ON' : 'AC OFF', stateManager.get('power') === 1);
    const autoPill = makePill('ac-pill-auto', 'AUTO', stateManager.get('auto') === 1);
    const maxAutoPill = makePill('ac-pill-maxauto', 'MAX AUTO', stateManager.get('maxauto') === 1);
    // Always labeled RECYCLE; green when activated (recycle === 1)
    const recyclePill = makePill(
        'ac-pill-recycle',
        'RECYCLE',
        stateManager.get('recycle') === 1 || stateManager.get('recycle') === '1'
    );

    const footer = div({
        className: 'ac-footer',
        children: [
            div({
                className: 'ac-footer-pills',
                children: [powerPill, autoPill, maxAutoPill, recyclePill],
            }),
        ],
    });

    panel.appendChild(fanSection);
    panel.appendChild(separator);
    panel.appendChild(tempSection);
    panel.appendChild(footer);
    main.appendChild(panel);

    const unsubs = [];

    unsubs.push(subscribe('focusArea', (area) => {
        fanSection.classList.toggle('is-focused', area === 'fan');
        tempSection.classList.toggle('is-focused', area === 'temp');
    }));

    unsubs.push(subscribe('fan', (v) => {
        fanValue.textContent = v == null || v === '' ? '—' : String(v);
        fanGauge.setLevel(v);
    }));

    unsubs.push(subscribe('temp', (v) => {
        tempValue.textContent = formatTempDisplay(v, stateManager.get('tempUnit'));
        const n = parseFloat(v);
        if (Number.isFinite(n)) {
            tempGauge.setRatio((n - TEMP_MIN) / (TEMP_MAX - TEMP_MIN));
        }
    }));

    unsubs.push(subscribe('tempUnit', (unit) => {
        tempValue.textContent = formatTempDisplay(stateManager.get('temp'), unit);
    }));

    const isPowerOn = () => {
        const p = stateManager.get('power');
        return p === 1 || p === '1';
    };

    const applyPowerUi = (v) => {
        const on = v === 1 || v === '1';
        powerPill.textContent = on ? 'AC ON' : 'AC OFF';
        powerPill.classList.toggle('is-on', on);
        panel.classList.toggle('ac-power-off', !on);
        // When AC is off: gray selector stays available, force focus on fan
        if (!on) {
            setState('focusArea', 'fan');
        }
    };

    unsubs.push(subscribe('power', applyPowerUi));

    unsubs.push(subscribe('auto', (v) => {
        autoPill.classList.toggle('is-on', v === 1 || v === '1');
    }));

    unsubs.push(subscribe('maxauto', (v) => {
        const on = v === 1 || v === '1';
        maxAutoPill.classList.toggle('is-on', on);
        maxAutoPill.classList.toggle('is-hidden', !on);
    }));

    unsubs.push(subscribe('recycle', (v) => {
        recyclePill.classList.toggle('is-on', v === 1 || v === '1');
    }));

    // Initial paint from power only (fan=0 does not mean AC off)
    applyPowerUi(stateManager.get('power'));
    if (stateManager.get('maxauto') !== 1 && stateManager.get('maxauto') !== '1') {
        maxAutoPill.classList.add('is-hidden');
    }

    const onMount = () => {
        if (!stateManager.get('focusArea') || !isPowerOn()) {
            setState('focusArea', 'fan');
        }
    };

    const cleanup = () => {
        unsubs.forEach((u) => {
            try { u(); } catch (_) { /* ignore */ }
        });
    };

    return { element: main, onMount, cleanup };
}

export function updateProgressRings() {
    // no-op — linear gauges subscribe to state
}
