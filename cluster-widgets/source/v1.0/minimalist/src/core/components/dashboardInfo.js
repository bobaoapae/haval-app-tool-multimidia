import { getState, setState, subscribe } from '../state.js';
import { div, span, img } from '../../../../shared/utils/createElement.js';
import { logger } from '../../../../shared/utils/logger.js';
import { createOdometerInfo } from './display/odometer/odometerInfo.js';

const fuelIconBase64 = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0id2hpdGUiPjxwYXRoIGQ9Ik0xLDEyTDUsOVYxNVoiLz48cGF0aCBkPSJNMjIsMTBWOGEyLDIsMCwwLDAtMi0yaC0zVjRhMiwyLDAsMCwwLTItMkg5QTIsMiwwLDAsMCw3LDR2MTZhMiwyLDAsMCwwLDIsMmg4YTIsMiwwLDAsMCw2LTJWMTJoMXY0YTIsMiwwLDAsMCw0LDBWMTBaTTksNGg4djZIOVptOCwxNkg5VjEyaDhaIi8+PC9zdmc+";
const batteryIconBase64 = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0id2hpdGUiPjwhLS0gQm9keSAtLT48cGF0aCBkPSJNMyw2aDE4YzEuMSwwLDIsMC45LDIsMnYxMGMwLDEuMS0wLjksMi0yLDJIM2MtMS4xLDAtMi0wLjktMi0yVjhDMSw2LjksMS45LDYsMyw2eiBNMyw4djEwaDE4VjhIM3oiLz48IS0tIFBvbGVzIC0tPjxyZWN0IHg9IjUiIHk9IjMiIHdpZHRoPSI0IiBoZWlnaHQ9IjMiLz48cmVjdCB4PSIxNSIgeT0iMyIgd2lkdGg9IjQiIGhlaWdodD0iMyIvPjwhLS0gTWludXMgc2lnbiAoLSkgLS0+PHJlY3QgeD0iNiIgeT0iMTIiIHdpZHRoPSI0IiBoZWlnaHQ9IjMiLz48IS0tIFBsdXMgc2lnbiAoKykgLS0+PHBhdGggZD0iTTE2LDEwaC0ydjJoLTJ2MmgydjJoMnYtMmgydi0yaC0yVjEweiIvPjwvc3ZnPg==";
const FUEL_TANK_CAPACITY_LITERS = 55;


function formatFuelLiters(percent) {
    const value = Number(percent);
    if (!Number.isFinite(value)) return '--';
    const clamped = Math.max(0, Math.min(100, value));
    return Math.round(clamped * FUEL_TANK_CAPACITY_LITERS / 100).toFixed(0);
}

function formatFuelDisplay(percent, unit) {
    if (unit === 'percent') {
        const value = Number(percent);
        if (!Number.isFinite(value)) return { value: '--', unit: '%' };
        return { value: String(Math.round(Math.max(0, Math.min(100, value)))), unit: '%' };
    }

    return { value: formatFuelLiters(percent), unit: 'L' };
}

export function createDashboardInfo() {
    logger.enter('createDashboardInfo');

    // Fallback simulation for showcase mode (same spirit as READY and right-side icon simulation).
    // Only seed values when the real odometer has not arrived yet.
    const currentOdometer = Number(getState('odometer')) || 0;
    if (currentOdometer <= 0) {
        setState('odometer', 15000);
    }
    if (!getState('enableOdometer')) {
        setState('enableOdometer', true);
    }

    const container = div({ className: 'dashboard-info-container' });
    const menuWrapper = div({ className: 'dashboard-menu-container' });

    // Card title: the top-center slot normally holds the Gráficos/Ajustes/Informações
    // carousel (screen 'main_menu', card 1). Cards 0 (native car content) and 3 (AC)
    // never populate that carousel, so the slot sits empty — this fills it with a
    // static label naming what's on screen instead.
    const CARD_TITLES = { 0: 'Principal', 3: 'Climatização' };
    const cardTitle = div({ className: 'dashboard-card-title' });
    const updateCardTitle = (cardId) => {
        const text = CARD_TITLES[Number(cardId)];
        cardTitle.textContent = text || '';
        cardTitle.style.display = text ? 'flex' : 'none';
    };
    updateCardTitle(getState('cardId'));

    // 1. Top Bar Elements (Clock, Gear, Mode)
    const topCenter = div({ className: 'dashboard-top-center' });
    const clock = span({ className: 'dashboard-clock', children: [getState('clockTime')] });
    const gearValue = getState('gearState');
    const gear = span({ className: 'dashboard-gear', children: [gearValue] });

    // Initial color setup
    const updateGearColor = (val) => {
        gear.setAttribute('data-gear', val);
    };
    updateGearColor(gearValue);

    const evMode = span({ className: 'dashboard-ev-mode', children: [getState('evModeLabel')] });

    const updateEvModeColor = (val) => {
        const lowerVal = String(val).toLowerCase();
        let mode = 'normal';
        if (lowerVal.includes('eco')) mode = 'eco';
        else if (lowerVal.includes('sport')) mode = 'sport';
        evMode.setAttribute('data-ev-mode', mode);
    };
    updateEvModeColor(getState('evModeLabel'));

    // EV Mode Display (placed to the left of the clock)
    const bottomEvMode = div({ className: 'dashboard-bottom-ev-mode' });
    const bottomEvLabel = span({ className: 'bottom-ev-label' });

    const updateBottomEv = (val) => {
        const cleanVal = String(val).toUpperCase().replace(/'/g, "");
        if (cleanVal === 'EV') {
            bottomEvLabel.textContent = 'EV';
        } else if (cleanVal === 'EVP') {
            bottomEvLabel.textContent = 'EVP';
        } else if (cleanVal === 'HEV') {
            bottomEvLabel.textContent = 'HEV';
        } else {
            bottomEvLabel.textContent = val;
        }
        bottomEvLabel.setAttribute('data-bottom-ev', cleanVal);
    };
    updateBottomEv(getState('evMode'));

    bottomEvMode.appendChild(bottomEvLabel);

    topCenter.appendChild(clock);
    topCenter.appendChild(gear);
    topCenter.appendChild(evMode);

    // Clock auto-update
    const clockInterval = setInterval(() => {
        const now = new Date();
        const hrs = String(now.getHours()).padStart(2, '0');
        const mins = String(now.getMinutes()).padStart(2, '0');
        clock.textContent = `${hrs}:${mins}`;
    }, 30000);

    // 2. Speed Gauge Elements (Flat)
    const speedDial = div({ className: 'dashboard-speed-dial minimalist-speed' });

    const speedContent = div({ className: 'dashboard-speed-content' });
    const speedValue = div({ className: 'dashboard-speed-value', children: [getState('carSpeed')] });
    const speedMetric = div({ className: 'dashboard-speed-metric', children: ['km/h'] });

    // Bi-directional Power Bar below Speedometer
    const powerBarTrack = div({ className: 'speed-power-bar-track' });
    const powerBarFill = div({ className: 'speed-power-bar-fill' });
    const powerBarCenterTick = div({ className: 'speed-power-bar-center-tick' });

    const powerBarLabels = div({
        className: 'speed-power-bar-labels',
        children: [
            span({ className: 'label-left', children: ['-100'] }),
            span({ className: 'label-right', children: ['100'] })
        ]
    });

    const powerBarValue = div({ className: 'speed-power-bar-value', children: ['0%'] });

    const powerBarContainer = div({
        className: 'speed-power-bar-container',
        children: [
            div({
                className: 'speed-power-bar-track-wrapper',
                children: [powerBarTrack, powerBarFill, powerBarCenterTick]
            }),
            powerBarLabels,
            powerBarValue
        ]
    });

    speedContent.appendChild(speedValue);
    speedContent.appendChild(speedMetric);

    const speedContainer = div({ className: 'dashboard-speed-container' });
    speedContainer.appendChild(speedDial);
    speedContainer.appendChild(speedContent);
    speedContainer.appendChild(powerBarContainer);


    // 3. Bottom Gauges
    const bottomGauges = div({ className: 'dashboard-bottom-gauges' });

    // Fuel Gauge
    const fuelContainer = div({ className: 'dashboard-fuel-container' });
    const initialFuelDisplay = formatFuelDisplay(getState('fuelPercent'), getState('fuelDisplayUnit'));
    const fuelTop = div({
        className: 'gauge-top-info', children: [
            div({
                className: 'gauge-icon-container', children: [
                    img({ className: 'fuel-indicator-icon', src: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAAACXBIWXMAAAsTAAALEwEAmpwYAAABD0lEQVR4nMWVv0oDQRCHPwstBTuJYCFE8gCmSFKkME8gvoVPELQ1Jq21ZZ4i6XKkyQOEFNcId9aWEiIrgTlYjtvsH/fID4a7m52Zb3e43YUj6QyYAF+AMlgOvEmst8YHCquSjUIAuSR3DsR0tZV4q5ida1wTeAeurRmlRNe4oTxT4Mqa5dF/JXYOrOR9A1zGBux1oX0vqQGAwVcpU6GdBZD8B5ACd9ovbJptEOATaMlYG/iNCVgDjdL4NCZgUBo7AR5jAm41/ynQB3oxAc+a/x54AJ4MgAKc+QC2wIv453J6flv2xasPYAbcVPS9yjIpbr0j9KQPacmPw052VuhRcXxAElBcie1zawUsfFYSTX/ovPhv8xUjRAAAAABJRU5ErkJggg==", alt: "fuel-gas" })
                ]
            }),
            span({ className: 'fuel-range', children: [getState('fuelRange'), span({ className: 'dashboard-unit', children: [' km'] })] })
        ]
    });

    // 4 Segments
    const fuelSegments = Array.from({ length: 4 }, () => div({
        className: 'segment-track',
        children: [div({ className: 'bar-segment' })]
    }));
    const fuelBar = div({
        className: 'segmented-bar-container fuel', children: [
            ...fuelSegments
        ]
    });
    const fuelLabels = div({
        className: 'gauge-labels-container',
        children: [span({}, 'E'), span({}, 'F')]
    });

    fuelContainer.appendChild(fuelTop);
    fuelContainer.appendChild(fuelBar);
    fuelContainer.appendChild(fuelLabels);

    // Battery Gauge
    const batteryContainer = div({ className: 'dashboard-battery-container' });
    const batteryTop = div({
        className: 'gauge-top-info', children: [
            div({
                className: 'gauge-icon-container', children: [
                    img({ className: 'battery-indicator-icon', src: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAmklEQVR4nGNgwA4sGBgY/uPA2xmIAEtxaP7LwMBgSEizJAMDw08cBswhxvYGHJq/MTAwyBLSzMbAwPAchwEggwmCGByaXzIwMPASY8BJHAakMRAJ/pOAaWMAMjDAo3kiAxEgAIfmbQwMDCzEGFCARfM5BgYGHgYiQT+a5qfEJCRksB5J82domJAEziNlIH9SNYPAe6gBufhUAQAtF2ORRpImYwAAAABJRU5ErkJggg==", alt: "lightning-bolt" })
                ]
            }),
            span({ className: 'battery-range', children: [getState('batteryRange'), span({ className: 'dashboard-unit', children: [' km'] })] })
        ]
    });

    const batterySegments = Array.from({ length: 4 }, () => div({
        className: 'segment-track',
        children: [div({ className: 'bar-segment' })]
    }));
    const batteryBar = div({
        className: 'segmented-bar-container battery', children: [
            ...batterySegments,
        ]
    });
    const batteryLabels = div({
        className: 'gauge-labels-container',
        children: [span({}, 'E'), span({}, 'F')]
    });

    batteryContainer.appendChild(batteryTop);
    batteryContainer.appendChild(batteryBar);
    batteryContainer.appendChild(batteryLabels);

    // RPM: larger value + compact thin 270° ring (7 segs, 1k–7k; last 2 red; half-seg OK)
    const RPM_SEGMENTS = 7;
    const RPM_SWEEP_DEG = 270;
    // Was 135 (bottom-left); +90° clockwise → starts at bottom-right area, sweeps CW
    const RPM_START_DEG = 225;
    const RPM_GAP_DEG = 3.5;
    const RPM_SEG_DEG = (RPM_SWEEP_DEG - RPM_GAP_DEG * RPM_SEGMENTS) / RPM_SEGMENTS;
    const RPM_R_OUTER = 44;
    const RPM_R_INNER = 40; // thinner ring (~4 units)

    const polar = (cx, cy, r, angleDeg) => {
        // 0° = 12 o'clock, clockwise positive
        const rad = ((angleDeg - 90) * Math.PI) / 180;
        return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
    };

    const describeRingSegment = (cx, cy, rOuter, rInner, startDeg, endDeg) => {
        if (endDeg - startDeg < 0.15) return '';
        const large = endDeg - startDeg > 180 ? 1 : 0;
        const so = polar(cx, cy, rOuter, startDeg);
        const eo = polar(cx, cy, rOuter, endDeg);
        const si = polar(cx, cy, rInner, startDeg);
        const ei = polar(cx, cy, rInner, endDeg);
        return [
            `M ${so.x.toFixed(2)} ${so.y.toFixed(2)}`,
            `A ${rOuter} ${rOuter} 0 ${large} 1 ${eo.x.toFixed(2)} ${eo.y.toFixed(2)}`,
            `L ${ei.x.toFixed(2)} ${ei.y.toFixed(2)}`,
            `A ${rInner} ${rInner} 0 ${large} 0 ${si.x.toFixed(2)} ${si.y.toFixed(2)}`,
            'Z',
        ].join(' ');
    };

    const rpmSvgNS = 'http://www.w3.org/2000/svg';
    const rpmSvg = document.createElementNS(rpmSvgNS, 'svg');
    rpmSvg.setAttribute('class', 'rpm-ring-svg');
    rpmSvg.setAttribute('viewBox', '0 0 100 100');
    rpmSvg.setAttribute('aria-hidden', 'true');

    // Each segment: dim track (full) + fill path (partial / full)
    const rpmSegmentFills = [];
    for (let i = 0; i < RPM_SEGMENTS; i++) {
        const start = RPM_START_DEG + i * (RPM_SEG_DEG + RPM_GAP_DEG);
        const end = start + RPM_SEG_DEG;
        const colorClass = i >= 5 ? 'rpm-seg-red' : 'rpm-seg-white';

        const track = document.createElementNS(rpmSvgNS, 'path');
        track.setAttribute('d', describeRingSegment(50, 50, RPM_R_OUTER, RPM_R_INNER, start, end));
        track.setAttribute('class', 'rpm-ring-seg rpm-ring-track');
        rpmSvg.appendChild(track);

        const fill = document.createElementNS(rpmSvgNS, 'path');
        fill.setAttribute('d', '');
        fill.setAttribute('class', `rpm-ring-seg rpm-ring-fill ${colorClass}`);
        fill.dataset.start = String(start);
        fill.dataset.segDeg = String(RPM_SEG_DEG);
        fill.dataset.level = String(i + 1);
        rpmSvg.appendChild(fill);
        rpmSegmentFills.push(fill);
    }

    const rpmText = div({
        className: 'rpm-text',
        children: [
            div({ className: 'rpm-row-top', children: [
                span({ className: 'rpm-value', children: ['--'] }),
                span({ className: 'rpm-k', children: ['k'] })
            ]}),
            div({ className: 'rpm-row-bottom', children: [
                span({ className: 'rpm-unit', children: ['RPM'] })
            ]})
        ]
    });

    const rpmContainer = div({ className: 'dashboard-rpm-container' });
    const rpmRingWrap = div({ className: 'rpm-ring-wrap' });
    rpmRingWrap.appendChild(rpmSvg);
    rpmRingWrap.appendChild(rpmText);
    rpmContainer.appendChild(rpmRingWrap);

    const updateRpmRing = (rpmV) => {
        // Continuous 0…7k — allows half segments (e.g. 3.5k → 3 full + 50% of 4th)
        const rpmK = Math.max(0, Math.min(RPM_SEGMENTS, (Number(rpmV) || 0) / 1000));
        rpmSegmentFills.forEach((fill, i) => {
            const start = Number(fill.dataset.start);
            const segDeg = Number(fill.dataset.segDeg);
            const amount = Math.max(0, Math.min(1, rpmK - i)); // 0…1 within this 1k band
            if (amount <= 0.001) {
                fill.setAttribute('d', '');
                fill.classList.remove('is-on');
                return;
            }
            const end = start + segDeg * amount;
            fill.setAttribute('d', describeRingSegment(50, 50, RPM_R_OUTER, RPM_R_INNER, start, end));
            fill.classList.add('is-on');
        });
    };
    const { element: odometerElement, cleanup: odometerCleanup } = createOdometerInfo();

    function formatTemp(temp, unit) {
        if (temp === null || temp === undefined || temp === '--' || temp === -1 || isNaN(temp)) {
            return '--';
        }
        const numericTemp = parseFloat(temp);
        if (numericTemp >= 85 || numericTemp <= -40) {
            return '--';
        }
        return temp + (unit || '°C');
    }

    // Temperature Labels
    const externalTempContainer = div({ className: 'external-temp-container' });
    const externalTempValue = span({ className: 'temp-value', children: [formatTemp(getState('outside_temp'), getState('tempUnit'))] });
    const externalTempLabel = span({ className: 'temp-sub-label', children: ['External'] });
    externalTempContainer.appendChild(externalTempValue);
    externalTempContainer.appendChild(externalTempLabel);

    const internalTempContainer = div({ className: 'internal-temp-container' });
    const internalTempValue = span({ className: 'temp-value', children: [formatTemp(getState('inside_temp'), getState('tempUnit'))] });
    const internalTempLabel = span({ className: 'temp-sub-label', children: ['Internal'] });
    internalTempContainer.appendChild(internalTempValue);
    internalTempContainer.appendChild(internalTempLabel);

    // Regen persistent indicator in bottom bar
    const bottomRegenContainer = div({ className: 'dashboard-bottom-regen' });
    const bottomRegenLabel = span({
        className: 'bottom-regen-label',
        children: ['REGEN']
    });
    const bottomRegenBarContainer = div({ className: 'bottom-regen-bars' });
    const bottomBar1 = div({ className: 'bottom-regen-bar', 'data-level': 1 });
    const bottomBar2 = div({ className: 'bottom-regen-bar', 'data-level': 2 });
    const bottomBar3 = div({ className: 'bottom-regen-bar', 'data-level': 3 });
    bottomRegenBarContainer.appendChild(bottomBar1);
    bottomRegenBarContainer.appendChild(bottomBar2);
    bottomRegenBarContainer.appendChild(bottomBar3);

    bottomRegenContainer.appendChild(bottomRegenLabel);
    bottomRegenContainer.appendChild(bottomRegenBarContainer);

    const initRegenBottom = (mode) => {
        let position = 0;
        if (mode === 'Alto') position = 3;
        else if (mode === 'Normal') position = 2;
        else if (mode === 'Baixo') position = 1;
        bottomRegenBarContainer.setAttribute('data-active-level', position);
    };
    initRegenBottom(getState('regenMode'));

    // Warning Label (Red label below right circle)
    const warningLabel = div({
        className: 'dashboard-warning-label',
        children: ['WARN']
    });
    if (!getState('warningActive')) {
        warningLabel.style.display = 'none';
    }

    // Alert Indicators Wrapper
    const alertIndicatorsContainer = div({ className: 'alert-indicators-container' });
    const bsdLeftIndicator = div({ className: 'bsd-indicator left blinking' });
    const bsdRightIndicator = div({ className: 'bsd-indicator right blinking' });

    alertIndicatorsContainer.appendChild(bsdLeftIndicator);
    alertIndicatorsContainer.appendChild(bsdRightIndicator);

    bsdLeftIndicator.style.display = getState('bsdLeft') ? 'block' : 'none';
    bsdRightIndicator.style.display = getState('bsdRight') ? 'block' : 'none';

    const tripAnalysisIndicator = div({
        className: 'trip-analysis-indicator',
        children: [
            div({
                className: 'trip-analysis-indicator-status',
                children: [
                    div({ className: 'trip-analysis-indicator-icon' }),
                    div({ className: 'trip-analysis-indicator-label', children: ['SCORE'] })
                ]
            }),
            div({ className: 'trip-analysis-indicator-value', children: ['--'] })
        ]
    });
    tripAnalysisIndicator.style.display = getState('tripAnalysisActive') ? 'flex' : 'none';

    const updateTripAnalysisScore = (score) => {
        const scoreValue = tripAnalysisIndicator.querySelector('.trip-analysis-indicator-value');
        const numericScore = Number(score);
        scoreValue.textContent = Number.isFinite(numericScore)
            ? Math.round(numericScore).toString().padStart(2, '0')
            : '--';
    };
    updateTripAnalysisScore(getState('tripAnalysisScore'));

    container.appendChild(warningLabel);

    bottomGauges.appendChild(fuelContainer);
    bottomGauges.appendChild(batteryContainer);
    speedContainer.appendChild(rpmContainer);

    container.appendChild(topCenter);
    container.appendChild(speedContainer);
    container.appendChild(bottomGauges);
    container.appendChild(odometerElement);

    container.appendChild(externalTempContainer);
    container.appendChild(internalTempContainer);
    container.appendChild(menuWrapper);
    container.appendChild(cardTitle);
    container.appendChild(alertIndicatorsContainer);
    container.appendChild(tripAnalysisIndicator);
    container.appendChild(bottomEvMode);
    container.appendChild(bottomRegenContainer);

    const fixedOverlay = div({
        className: 'dashboard-fixed-overlay',
        children: [
            div({ className: 'dashboard-sport-ready-text', children: ['READY'] }),
            div({
                className: 'dashboard-sport-right-icon',
                children: [
                    div({ className: 'dashboard-sport-right-lane left' }),
                    div({ className: 'dashboard-sport-right-car' }),
                    div({ className: 'dashboard-sport-right-lane right' })
                ]
            }),
            div({ className: 'dashboard-sport-limit-sign', children: ['30'] })
        ]
    });
    container.appendChild(fixedOverlay);

    // Subscriptions
    const updateBarSegments = (tracks, percent) => {
        tracks.forEach((track, i) => {
            const seg = track.firstChild;
            const segMax = (i + 1) * 25;
            const segMin = i * 25;
            if (percent >= segMax) {
                seg.style.width = '100%';
            } else if (percent > segMin) {
                seg.style.width = ((percent - segMin) / 25 * 100) + '%';
            } else {
                seg.style.width = '0%';
            }
        });
    };

    let prevSpeed = getState('carSpeed') || 0;

    const updateSpeedRotation = (speed) => {
        // Removed rotation logic for minimalist theme
    };

    const updateFuelDisplay = () => {};

    const subscriptions = [
        subscribe('cardId', updateCardTitle),
        subscribe('clockTime', val => clock.textContent = val),
        subscribe('gearState', val => {
            gear.textContent = val;
            updateGearColor(val);
        }),
        subscribe('evModeLabel', val => {
            evMode.textContent = val;
            updateEvModeColor(val);
        }),
        subscribe('regenMode', val => {
            let position = 0;
            if (val === 'Alto') position = 3;
            else if (val === 'Normal') position = 2;
            else if (val === 'Baixo') position = 1;
            bottomRegenBarContainer.setAttribute('data-active-level', position);
        }),
        subscribe('evMode', val => updateBottomEv(val)),
        subscribe('carSpeed', val => {
            speedValue.textContent = val;
            updateSpeedRotation(val);
        }),
        subscribe('fuelPercent', val => {
            updateBarSegments(fuelSegments, val);
            updateFuelDisplay();
        }),
        subscribe('fuelDisplayUnit', updateFuelDisplay),
        subscribe('batteryPercent', val => {
            updateBarSegments(batterySegments, val);
        }),
        subscribe('fuelRange', val => {
            const rangeSpan = fuelTop.querySelector('.fuel-range');
            if (rangeSpan && rangeSpan.childNodes[0]) rangeSpan.childNodes[0].textContent = val;
        }),
        subscribe('batteryRange', val => {
            const rangeSpan = batteryTop.querySelector('.battery-range');
            if (rangeSpan && rangeSpan.childNodes[0]) rangeSpan.childNodes[0].textContent = val;
        }),
        subscribe('outside_temp', val => externalTempValue.textContent = formatTemp(val, getState('tempUnit'))),
        subscribe('inside_temp', val => internalTempValue.textContent = formatTemp(val, getState('tempUnit'))),
        subscribe('tempUnit', unit => {
            externalTempValue.textContent = formatTemp(getState('outside_temp'), unit);
            internalTempValue.textContent = formatTemp(getState('inside_temp'), unit);
        }),
        subscribe('warningActive', val => {
            logger.log('[DashboardInfo Light] warningActive changed to:', val);
            warningLabel.style.display = val ? 'block' : 'none';
        }),
        subscribe('bsdLeft', val => bsdLeftIndicator.style.display = val ? 'block' : 'none'),
        subscribe('bsdRight', val => bsdRightIndicator.style.display = val ? 'block' : 'none'),
        subscribe('tripAnalysisActive', val => tripAnalysisIndicator.style.display = val ? 'flex' : 'none'),
        subscribe('tripAnalysisScore', updateTripAnalysisScore),
        subscribe('evPowerFactor', val => updatePowerBar(val)),
        subscribe('engineRPM', val => {
            const rpmV = val || 0;
            if (rpmV > 10) {
                const rpmVal = (rpmV / 1000).toFixed(1);
                rpmContainer.querySelector('.rpm-value').textContent = rpmVal;
                rpmContainer.style.display = 'flex';
                updateRpmRing(rpmV);
            } else {
                rpmContainer.style.display = 'none';
                updateRpmRing(0);
            }
        })
    ];

    // Initial RPM ring/value
    {
        const rpmV = getState('engineRPM') || 0;
        if (rpmV > 10) {
            rpmContainer.querySelector('.rpm-value').textContent = (rpmV / 1000).toFixed(1);
            rpmContainer.style.display = 'flex';
            updateRpmRing(rpmV);
        } else {
            rpmContainer.style.display = 'none';
            updateRpmRing(0);
        }
    }

    const updatePowerBar = (power) => {
        const powerV = power || 0;
        const maxScale = 100;
        const absVal = Math.min(maxScale, Math.abs(powerV));
        const fillWidth = (absVal / maxScale) * 50;

        if (powerV >= 0) {
            powerBarFill.style.left = '50%';
            powerBarFill.style.width = `${fillWidth}%`;
            powerBarFill.style.backgroundColor = 'var(--graph-power-bar-color, #00C3FF)';
        } else {
            powerBarFill.style.left = `${50 - fillWidth}%`;
            powerBarFill.style.width = `${fillWidth}%`;
            powerBarFill.style.backgroundColor = 'var(--graph-power-regen-color, #33FF33)';
        }

        powerBarValue.textContent = `${Math.round(powerV)}%`;
    };

    updatePowerBar(getState('evPowerFactor'));
    const initialRPM = getState('engineRPM') || 0;
    if (initialRPM > 10) {
        const rpmVal = (initialRPM / 1000).toFixed(1);
        rpmContainer.querySelector('.rpm-value').textContent = rpmVal;
        rpmContainer.style.display = 'flex';
    } else {
        rpmContainer.style.display = 'none';
    }
    updateBarSegments(fuelSegments, getState('fuelPercent'));
    updateBarSegments(batterySegments, getState('batteryPercent'));
    updateSpeedRotation(getState('carSpeed'));
    updateFuelDisplay();

    const cleanup = () => {
        clearInterval(clockInterval);
        subscriptions.forEach(unsubscribe => unsubscribe());
        if (odometerCleanup) odometerCleanup();
    };

    return { element: container, menuWrapper, cleanup };
}
