import { getState, setState, subscribe } from '../state.js';
import { div, span, img } from '../../../../shared/utils/createElement.js';
import { logger } from '../../../../shared/utils/logger.js';
import { createOdometerInfo } from './display/odometer/odometerInfo.js';
import { createSpeedometerScreen } from './speedometer/speedometer.js';

function createFuelIcon() {
    const el = div({ className: 'fuel-icon-wrapper' });
    el.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="fuel-icon"><path d="M1,12L5,9V15Z"/><path d="M22,10V8a2,2,0,0,0-2-2h-3V4a2,2,0,0,0-2-2H9A2,2,0,0,0,7,4v16a2,2,0,0,0,2,2h8a2,2,0,0,0,2-2V12h1v4a2,2,0,0,0,4,0V10a2,2,0,0,0-2-2M9,4h8v6H9Zm8,16H9V12h8Z"/></svg>`;
    return el.firstChild;
}

function createBatteryIcon() {
    const el = div({ className: 'battery-icon-wrapper' });
    el.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="battery-icon"><path d="M3,6h18c1.1,0,2,0.9,2,2v10c0,1.1-0.9,2-2,2H3c-1.1,0-2-0.9-2-2V8C1,6.9,1.9,6,3,6z M3,8v10h18V8H3z"/><rect x="5" y="3" width="4" height="3"/><rect x="15" y="3" width="4" height="3"/><rect x="6" y="12" width="4" height="3"/><path d="M16,10h-2v2h-2v2h2v2h2v-2h2v-2h-2V10z"/></svg>`;
    return el.firstChild;
}
const FUEL_TANK_CAPACITY_LITERS = 55;

function formatFuelLiters(percent) {
    const value = Number(percent);
    if (!Number.isFinite(value)) return '--';
    const clamped = Math.max(0, Math.min(100, value));
    return (clamped * FUEL_TANK_CAPACITY_LITERS / 100).toFixed(1);
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

    // 2. Speed Gauge Elements (Left Circle)
    const speedDial = div({ className: 'dashboard-speed-dial' });
    const speedOrbital = div({ className: 'speed-orbital' });
    const speedInnerCircle = div({ className: 'speed-inner-circle' });
    const speedNeedleContainer = div({ className: 'speed-needle-container' });
    const speedNeedle = div({ className: 'speed-needle' });

    speedNeedleContainer.appendChild(speedNeedle);
    speedDial.appendChild(speedOrbital);

    const trails = [];
    const total_trails = 8;
    for (let i = 0; i < total_trails; i++) {
        const isMainTrail = i === total_trails - 1;
        const trailContainer = div({ className: 'needle-trail-wrapper' });
        const trailLoader = div({ className: 'needle-trail' + (isMainTrail ? ' main-trail' : '') });

        if (isMainTrail) {
            trailLoader.style.borderWidth = '3px';
            trailLoader.style.borderColor = 'white transparent transparent transparent';
        } else {
            trailLoader.style.borderWidth = '15px';
            // Apply a blue tint that increases towards the tail
            const blueFactor = (total_trails - 2 - i) / (total_trails - 2);
            const r = Math.round(255 - (255 - 100) * (blueFactor > 0 ? blueFactor : 0));
            const g = Math.round(255 - (255 - 100) * (blueFactor > 0 ? blueFactor : 0));
            const b = 255;
            trailLoader.style.borderColor = `rgb(${r}, ${g}, ${b}) transparent transparent transparent`;
        }

        const sizeReduction = i * 2.5;
        trailContainer.style.width = isMainTrail ? '90%' : `calc(95% - ${sizeReduction}px)`;
        trailContainer.style.height = isMainTrail ? '90%' : `calc(95% - ${sizeReduction}px)`;
        trailContainer.style.opacity = isMainTrail ? '1' : (0.5 - (i * 0.02)).toString();
        trailContainer.style.transition = `transform ${0.1 + (i * 0.02)}s cubic-bezier(0.1, 0, 0.3, 1)`;
        trailLoader.style.animationDelay = `${i * 0.04}s`;

        trailContainer.appendChild(trailLoader);
        speedDial.appendChild(trailContainer);
        trails.push(trailContainer);
    }

    speedDial.appendChild(speedNeedleContainer);
    speedDial.appendChild(speedInnerCircle);

    const speedContent = div({ className: 'dashboard-speed-content' });
    const speedValue = div({ className: 'dashboard-speed-value', children: [getState('carSpeed')] });
    const speedMetric = div({ className: 'dashboard-speed-metric', children: ['km/h'] });
    speedContent.appendChild(speedValue);
    speedContent.appendChild(speedMetric);

    const speedContainer = div({ className: 'dashboard-speed-container' });
    speedContainer.appendChild(speedDial);
    speedContainer.appendChild(speedContent);
    const sportSpeedometer = createSpeedometerScreen();
    sportSpeedometer.element.classList.add('dashboard-speed-esportivo-widget');
    speedContainer.appendChild(sportSpeedometer.element);
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

    // 3. Bottom Gauges
    const bottomGauges = div({ className: 'dashboard-bottom-gauges' });

    // Fuel Gauge
    const fuelContainer = div({ className: 'dashboard-fuel-container' });
    const initialFuelDisplay = formatFuelDisplay(getState('fuelPercent'), getState('fuelDisplayUnit'));
    const fuelTop = div({
        className: 'gauge-top-info', children: [
            createFuelIcon(),
            span({ className: 'fuel-range', children: [getState('fuelRange'), span({ className: 'dashboard-unit', children: [' km'] })] }),
            span({ className: 'fuel-liters', children: [initialFuelDisplay.value, span({ className: 'dashboard-unit', children: [` ${initialFuelDisplay.unit}`] })] })
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
            createBatteryIcon(),
            span({ className: 'battery-range', children: [getState('batteryRange'), span({ className: 'dashboard-unit', children: [' km'] })] }),
            span({ className: 'battery-percent', children: [getState('batteryPercent') + '%'] })
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

    // EV Mode Display (Bottom Right)
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

    batteryContainer.appendChild(batteryBar);
    batteryContainer.appendChild(batteryLabels);
    container.appendChild(warningLabel);

    bottomGauges.appendChild(batteryContainer);

    container.appendChild(topCenter);
    container.appendChild(speedContainer);

    bottomGauges.appendChild(fuelContainer);
    bottomGauges.appendChild(odometerElement);
    bottomGauges.appendChild(batteryContainer);
    container.appendChild(bottomGauges);
    container.appendChild(externalTempContainer);
    container.appendChild(internalTempContainer);
    container.appendChild(bottomEvMode);
    container.appendChild(menuWrapper);
    container.appendChild(alertIndicatorsContainer);
    container.appendChild(tripAnalysisIndicator);

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
        const rotation = (speed * 1);
        speedNeedleContainer.style.transform = `rotate(${rotation}deg)`;

        const speedDelta = speed - prevSpeed;
        const dynamicSpacing = 2 + Math.max(0, speedDelta);

        const headAlignmentOffset = 125 - (speedDelta * 5);
        trails.forEach((trail, i) => {
            const isMainTrail = i === trails.length - 1;
            const effectiveIndex = isMainTrail ? (trails.length - 1) / 2 : i;
            const trailSpacing = effectiveIndex * dynamicSpacing;
            trail.style.transform = `translate(-50%, -50%) rotate(${rotation + headAlignmentOffset + trailSpacing}deg)`;
        });

        prevSpeed = speed;
    };

    const updateFuelDisplay = () => {
        const display = formatFuelDisplay(getState('fuelPercent'), getState('fuelDisplayUnit'));
        const fuelDisplaySpan = fuelTop.querySelector('.fuel-liters');
        const fuelUnitSpan = fuelDisplaySpan?.querySelector('.dashboard-unit');
        if (fuelDisplaySpan && fuelDisplaySpan.childNodes[0]) {
            fuelDisplaySpan.childNodes[0].textContent = display.value;
        }
        if (fuelUnitSpan) {
            fuelUnitSpan.textContent = ` ${display.unit}`;
        }
    };

    const subscriptions = [
        subscribe('clockTime', val => clock.textContent = val),
        subscribe('gearState', val => {
            gear.textContent = val;
            updateGearColor(val);
        }),
        subscribe('evModeLabel', val => {
            evMode.textContent = val;
            updateEvModeColor(val);
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
            batteryTop.querySelector('.battery-percent').textContent = val + '%';
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
        subscribe('tripAnalysisScore', updateTripAnalysisScore)
    ];

    updateBarSegments(fuelSegments, getState('fuelPercent'));
    updateBarSegments(batterySegments, getState('batteryPercent'));
    updateSpeedRotation(getState('carSpeed'));
    updateFuelDisplay();

    const cleanup = () => {
        clearInterval(clockInterval);
        subscriptions.forEach(unsubscribe => unsubscribe());
        if (sportSpeedometer?.cleanup) sportSpeedometer.cleanup();
        if (odometerCleanup) odometerCleanup();
    };

    return { element: container, menuWrapper, cleanup };
}
