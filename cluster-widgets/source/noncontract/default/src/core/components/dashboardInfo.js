import { getState, setState, subscribe } from '../state.js';
import { div, span, img } from '../../utils/createElement.js';
import { logger } from '../../utils/logger.js';
import { createOdometerInfo } from './display/odometer/odometerInfo.js';
import { createSpeedometerScreen } from './speedometer/speedometer.js';

const fuelIconBase64 = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0id2hpdGUiPjxwYXRoIGQ9Ik0xLDEyTDUsOVYxNVoiLz48cGF0aCBkPSJNMjIsMTBWOGEyLDIsMCwwLDAtMi0yaC0zVjRhMiwyLDAsMCwwLTItMkg5QTIsMiwwLDAsMCw3LDR2MTZhMiwyLDAsMCwwLDIsMmg4YTIsMiwwLDAsMCwyLTJWMTJoMXY0YTIsMiwwLDAsMCw0LDBWMTBaTTksNGg4djZIOVptOCwxNkg5VjEyaDhaIi8+PC9zdmc+";
const batteryIconBase64 = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0id2hpdGUiPjwhLS0gQm9keSAtLT48cGF0aCBkPSJNMyw2aDE4YzEuMSwwLDIsMC45LDIsMnYxMGMwLDEuMS0wLjksMi0yLDJIM2MtMS4xLDAtMi0wLjktMi0yVjhDMSw2LjksMS45LDYsMyw2eiBNMyw4djEwaDE4VjhIM3oiLz48IS0tIFBvbGVzIC0tPjxyZWN0IHg9IjUiIHk9IjMiIHdpZHRoPSI0IiBoZWlnaHQ9IjMiLz48cmVjdCB4PSIxNSIgeT0iMyIgd2lkdGg9IjQiIGhlaWdodD0iMyIvPjwhLS0gTWludXMgc2lnbiAoLSkgLS0+PHJlY3QgeD0iNiIgeT0iMTIiIHdpZHRoPSI0IiBoZWlnaHQ9IjMiLz48IS0tIFBsdXMgc2lnbiAoKykgLS0+PHBhdGggZD0iTTE2LDEwaC0ydjJoLTJ2MmgydjJoMnYtMmgydi0yaC0yVjEweiIvPjwvc3ZnPg==";
const FUEL_TANK_CAPACITY_LITERS = 55;
function isSimulatorRuntime() {
    if (window.Android) return false;

    const urlParams = new URLSearchParams(window.location.search);
    return process.env.NODE_ENV === 'development' ||
        urlParams.get('nativeMocks') === '1' ||
        window.__ENABLE_NATIVE_MOCKS === true ||
        window.__AIR_CONTROL_TEST_MODE === true;
}

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

function formatEvPowerKw(value) {
    const numericValue = Number(value);
    if (!Number.isFinite(numericValue)) {
        return { label: 'EV', value: '--', unit: 'kW', state: 'idle' };
    }

    if (Math.abs(numericValue) < 0.2) {
        return { label: 'EV', value: '0', unit: 'kW', state: 'idle' };
    }

    const absValue = Math.abs(numericValue);
    const precision = absValue < 10 ? 1 : 0;
    const formattedValue = absValue.toFixed(precision);
    const isRegen = numericValue < 0;

    return {
        label: isRegen ? 'REGEN' : 'EV',
        value: `${isRegen ? '-' : '+'}${formattedValue}`,
        unit: 'kW',
        state: isRegen ? 'regen' : 'drive'
    };
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
    if (isSimulatorRuntime()) {
        const sportFixedOverlay = div({
            className: 'dashboard-sport-fixed-overlay',
            children: [
                div({
                    className: 'dashboard-sport-limit-sign',
                    children: [
                        div({ className: 'limit-circle', children: ['30'] })
                    ]
                }),
                div({ className: 'dashboard-sport-ready-text', children: ['READY'] }),
                div({
                    className: 'dashboard-sport-right-icon',
                    children: [
                        div({ className: 'dashboard-sport-right-lane left' }),
                        div({ className: 'dashboard-sport-right-car' }),
                        div({ className: 'dashboard-sport-right-lane right' })
                    ]
                })
            ]
        });
        speedContainer.appendChild(sportFixedOverlay);
    }

    // 3. Bottom Gauges
    const bottomGauges = div({ className: 'dashboard-bottom-gauges' });

    // Fuel Gauge
    const fuelContainer = div({ className: 'dashboard-fuel-container' });
    const initialFuelDisplay = formatFuelDisplay(getState('fuelPercent'), getState('fuelDisplayUnit'));
    const fuelTop = div({
        className: 'gauge-top-info', children: [
            img({ className: 'fuel-icon', src: fuelIconBase64 }),
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
            img({ className: 'battery-icon', src: batteryIconBase64 }),
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
            bottomEvLabel.textContent = 'EV';
        } else if (cleanVal === 'HEV') {
            bottomEvLabel.textContent = 'HEV';
        } else {
            bottomEvLabel.textContent = val;
        }
        bottomEvLabel.setAttribute('data-bottom-ev', cleanVal);
    };
    updateBottomEv(getState('evMode'));

    bottomEvMode.appendChild(bottomEvLabel);

    const evPowerCard = div({ className: 'dashboard-ev-power-card' });
    const evPowerLabel = span({ className: 'ev-power-label' });
    const evPowerValue = span({ className: 'ev-power-value' });
    const evPowerUnit = span({ className: 'ev-power-unit' });

    const updateEvPower = (value) => {
        const formatted = formatEvPowerKw(value);
        evPowerLabel.textContent = formatted.label;
        evPowerValue.textContent = formatted.value;
        evPowerUnit.textContent = formatted.unit;
        evPowerCard.setAttribute('data-ev-power-state', formatted.state);
    };
    updateEvPower(getState('evPowerKw'));

    evPowerCard.appendChild(evPowerLabel);
    evPowerCard.appendChild(evPowerValue);
    evPowerCard.appendChild(evPowerUnit);

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
    container.appendChild(evPowerCard);
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
        subscribe('evPowerKw', updateEvPower),
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
