import { getState, setState, subscribe } from '../../state.js';
import { div, img, span } from '../../../utils/createElement.js';

import { Chart, registerables } from 'chart.js';
import streamingPlugin from 'chartjs-plugin-streaming';
import 'chartjs-adapter-date-fns';
import { WarpTunnelAnimation } from './warpTunnel.js';
Chart.register(...registerables, streamingPlugin);

const HISTORY_DURATION = 30000; //ms
const TIMER_HIDE_DELAY = 30000; //ms
const UI_UPDATE_INTERVAL = 250; //ms
const CHART_UPDATE_INTERVAL = 500; //ms
const ACCELERATION_THRESHOLD = 10; //s (ie 100km/h in 5s = 5, 100km/h in 10s = 10)
const GRAPH_SCREENS = new Set(['graph', 'graphs']);

const isGraphScreenActive = () => GRAPH_SCREENS.has(getState('screen'));

const emitPerfEvent = (name, details = '') => {
    try {
        if (window.Android && window.Android.perfEvent) {
            window.Android.perfEvent(name, details);
        }
    } catch (e) {
        // Perf logging must never affect the cluster UI.
    }
};

const hexToRgba = (hex, alpha) => {
    let r = 255, g = 255, b = 255;
    if (hex.startsWith('#')) {
        const h = hex.replace('#', '');
        if (h.length === 3) {
            r = parseInt(h[0] + h[0], 16);
            g = parseInt(h[1] + h[1], 16);
            b = parseInt(h[2] + h[2], 16);
        } else if (h.length >= 6) {
            r = parseInt(h.substring(0, 2), 16);
            g = parseInt(h.substring(2, 4), 16);
            b = parseInt(h.substring(4, 6), 16);
        }
    }
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
};

export const graphList = [
    {
        id: 'evConsumption',
        displayLabel: 'Potência EV',
        decimalPlaces: 1,
        secondaryDecimalPlaces: 1,
        yAxis: {
            min: -100,
            max: 140,
            stepSize: 50
        },
        y1Axis: {
            min: -100,
            max: 140,
            stepSize: 50
        },
        datasets: [
            {
                label: 'Potência EV',
                dataKey: 'evPowerKw',
                smooth: true,
                smoothFactor: 100, // Join data close by 100ms
                unity: 'kWatts',
                yAxisID: 'y',
                lineColor: '--graph-evpower-line-color',
                positiveColor: '--graph-evpower-positive-color',
                negativeColor: '--graph-evpower-negative-color'
            },
            {
                label: 'Média',
                dataKey: 'evPowerKw',
                type: 'average',
                avgConfig: { seconds: 30 },
                smooth: true,
                unity: 'kWavg',
                yAxisID: 'y1',
                lineColor: '--graph-evpower-avg-line-color'
            }
        ],
        secondaryTooltipColor: '--graph-evpower-secondary-tooltip-color'
    },
    {
        id: 'gasConsumption',
        displayLabel: 'Consumo Misto',
        decimalPlaces: 1,
        secondaryDecimalPlaces: 1,
        yAxis: {
            min: -43,
            max: 120,
            stepSize: 15
        },
        y1Axis: {
            min: -10,
            max: 30,
            stepSize: 10
        },
        datasets: [
            {
                label: 'Consumo Elétrico',
                dataKey: 'instantEVConsumption',
                smooth: true,
                smoothFactor: 100, // Join data close by 100ms
                unity: 'KWh/100km',
                yAxisID: 'y',
                lineColor: '--graph-mixed-electric-line-color',
                valueFilter: 'positive'
            },
            {
                label: 'Consumo Instantâneo',
                dataKey: 'gasConsumption',
                smooth: true,
                unity: 'Km/L',
                yAxisID: 'y1',
                idleKey: 'gasConsumptionIdle',
                idleUnity: 'l/100km',
                lineColor: '--graph-mixed-instant-line-color',
                valueFilter: 'positive'
            },
            {
                label: 'Média EV',
                dataKey: 'instantEVConsumption',
                type: 'average',
                avgConfig: { seconds: 30 },
                smooth: true,
                unity: 'KWh/100km',
                yAxisID: 'y2',
                followAxis: 'y',
                lineColor: '--graph-mixed-ev-avg-line-color',
                valueFilter: 'positive'
            }
        ],
        secondaryTooltipColor: '--graph-mixed-secondary-tooltip-color'
    },
    {
        id: 'carSpeed',
        displayLabel: 'Velocidade',
        decimalPlaces: 0,
        secondaryDecimalPlaces: 1,
        yAxis: {
            min: -72,
            max: 200,
            stepSize: 40
        },
        y1Axis: {
            min: -10,
            max: 30,
            stepSize: 10
        },
        datasets: [
            {
                label: 'Velocidade',
                dataKey: 'carSpeed',
                smooth: true,
                unity: 'km/h',
                yAxisID: 'y',
                lineColor: '--graph-speed-line-color'
            },
            {
                label: 'Consumo',
                dataKey: 'gasConsumption',
                smooth: true,
                unity: 'km/L',
                yAxisID: 'y1',
                lineColor: '--graph-consumption-line-color'
            }
        ],
        secondaryTooltipColor: '--graph-speed-secondary-tooltip-color'
    },
];

const historicalData = {};

const getChartColor = (dataset, colorType) => {
    let colorVal = dataset[colorType];
    if (colorVal && colorVal.startsWith('--')) {
        const computed = getComputedStyle(document.documentElement).getPropertyValue(colorVal).trim();
        if (computed) return computed;
        return undefined; // Return undefined if variable is not found
    }
    return colorVal;
};

const addDataPoint = (dataKey, value, datasetConfig = {}) => {
    if (value === undefined || value === null) return;
    const now = Date.now();
    if (!historicalData[dataKey]) historicalData[dataKey] = [];

    const data = historicalData[dataKey];
    const smoothFactor = datasetConfig.smoothFactor || 0;

    if (data.length > 0) {
        const lastPoint = data[data.length - 1];
        const timeGap = now - lastPoint.x;


        // Data Thinning
        if (value !== 0 && lastPoint.y !== 0 && timeGap < smoothFactor) {
            lastPoint.y = (lastPoint.y + value) / 2;
            return;
        }

        // Bridge Logic for 0 value
        if (lastPoint.y === 0 && Math.abs(value) > 0 && timeGap > 500) {
            data.push({ x: now - 50, y: 0 });
        }
        else if (Math.abs(lastPoint.y) > 0 && value === 0 && timeGap > 500) {
            data.push({ x: now - 50, y: lastPoint.y });
        }
    }

    data.push({ x: now, y: value });

    const DURATION = HISTORY_DURATION;
    while (data.length > 0 && now - data[0].x > DURATION) {
        data.shift();
    }
};

function initializeGlobalDataStore() {
    graphList.forEach(graph => {
        graph.datasets.forEach(dataset => {
            if (dataset.dataKey) {
                historicalData[dataset.dataKey] = [];

                let currentKey = dataset.dataKey;
                if (dataset.valueFilter) {
                    const filterKey = `${dataset.dataKey}_filtered_${dataset.valueFilter}`;
                    historicalData[filterKey] = [];
                    dataset.filterInternalKey = filterKey;
                    currentKey = filterKey;
                }

                if (dataset.type === 'average') {
                    const avgKey = `${currentKey}_avg_${dataset.avgConfig.samples || dataset.avgConfig.seconds || '10'}`;
                    historicalData[avgKey] = [];
                    dataset.avgInternalKey = avgKey;
                }
            }
            if (dataset.idleKey) {
                dataKeys.add(dataset.idleKey); // This will handle gasConsumptionIdle
                historicalData[dataset.idleKey] = [];
            }
        });
    });
}

const dataKeys = new Set();
const averagesToTrack = []; // List of { sourceKey, targetKey, config }
const filtersToTrack = []; // List of { sourceKey, targetKey, filterType }

function startGlobalDataCollector() {
    initializeGlobalDataStore();

    const allDatasets = [];
    graphList.forEach(g => allDatasets.push(...g.datasets));

    graphList.forEach(graph => {
        graph.datasets.forEach(dataset => {
            if (dataset.dataKey) {
                dataKeys.add(dataset.dataKey);

                if (dataset.filterInternalKey) {
                    filtersToTrack.push({
                        sourceKey: dataset.dataKey,
                        targetKey: dataset.filterInternalKey,
                        filterType: dataset.valueFilter
                    });
                }

                if (dataset.avgInternalKey) {
                    averagesToTrack.push({
                        sourceKey: dataset.filterInternalKey || dataset.dataKey,
                        targetKey: dataset.avgInternalKey,
                        config: dataset.avgConfig
                    });
                }
            }
        });
    });

    dataKeys.forEach(dataKey => {
        subscribe(dataKey, (value) => {
            if (!isGraphScreenActive()) return;

            const datasetCfg = allDatasets.find(d =>
                d.dataKey === dataKey ||
                d.filterInternalKey === dataKey ||
                d.avgInternalKey === dataKey
            ) || {};

            addDataPoint(dataKey, value, datasetCfg);

            // Handle Filters
            filtersToTrack.forEach(filter => {
                if (filter.sourceKey === dataKey) {
                    let passes = true;
                    if (filter.filterType === 'positive') passes = value >= 0;
                    else if (filter.filterType === 'negative') passes = value <= 0;
                    const currentFilterCfg = allDatasets.find(d => d.filterInternalKey === filter.targetKey) || {};

                    if (passes) {
                        addDataPoint(filter.targetKey, value, currentFilterCfg);
                        setState(filter.targetKey, value);
                    } else {
                        addDataPoint(filter.targetKey, 0, currentFilterCfg);
                        setState(filter.targetKey, 0);
                    }
                }
            });

            // Generic average handling
            averagesToTrack.forEach(avg => {
                if (avg.sourceKey === dataKey || filtersToTrack.some(f => f.targetKey === avg.sourceKey && f.sourceKey === dataKey)) {
                    // Check if this avg relies on the filtered key or raw key
                    const activeKey = (filtersToTrack.find(f => f.sourceKey === dataKey && f.targetKey === avg.sourceKey)) ? avg.sourceKey : dataKey;

                    // If it matches either raw or the specific filtered key we just updated
                    if (avg.sourceKey === activeKey) {
                        const sourceData = historicalData[activeKey];

                        if (sourceData && sourceData.length > 0) {
                            let filteredData = sourceData;

                            if (avg.config.samples) {
                                filteredData = sourceData.slice(-avg.config.samples);
                            } else if (avg.config.seconds) {
                                const now = Date.now();
                                const threshold = now - (avg.config.seconds * 1000);
                                filteredData = sourceData.filter(p => p.x >= threshold);
                            }

                            if (filteredData.length > 0) {
                                const sum = filteredData.reduce((acc, point) => acc + point.y, 0);
                                const denominator = avg.config.samples || filteredData.length;
                                const avgVal = sum / denominator;
                                const avgCfg = allDatasets.find(d => d.avgInternalKey === avg.targetKey) || {};
                                addDataPoint(avg.targetKey, avgVal, avgCfg);
                                setState(avg.targetKey, avgVal);
                            }
                        }
                    }
                }
            });
        });
    });


    // Pulse timer for smoothing logic that depends on time/samples
    const smoothingStates = new Map();

    graphList.forEach(graph => {
        graph.datasets.forEach(dataset => {
            if (dataset.smooth && dataset.dataKey) {
                const rawKey = dataset.dataKey;
                const smoothedKey = `${rawKey}_smoothed`;

                dataset.dataKey = smoothedKey;

                if (!smoothingStates.has(rawKey)) {
                    smoothingStates.set(rawKey, {
                        samples: 0,
                        targetKey: smoothedKey
                    });
                    dataKeys.add(rawKey);
                }
            }
        });
    });

    setInterval(() => {
        if (!isGraphScreenActive()) return;

        smoothingStates.forEach((state, rawKey) => {
            const rawValue = getState(rawKey) || 0;

            if (state.emaValue === undefined) {
                state.emaValue = rawValue;
            }

            // Exponential Moving Average (EMA) for noise reduction
            // Alpha 0.3 provides a good balance between responsiveness and smoothness
            const alpha = 0.3;
            state.emaValue = (state.emaValue * (1 - alpha)) + (rawValue * alpha);

            setState(state.targetKey, state.emaValue);
            addDataPoint(state.targetKey, state.emaValue);
        });
    }, 200);
}

startGlobalDataCollector();

export function createGraphScreen() {
    let chartInstance = null;
    let currentGraphId = getState('currentGraph') || graphList[0].id;
    let lastCarSpeed = getState('carSpeed') || 0.0;
    let warpTunnel = null;
    let isSpeedTimerRunning = false;
    let speedTimerStartTime = 0;
    let timerHideTimeoutId = null;
    let last0To100Time = null;
    let flashTriggered = false;
    let uiUpdateInterval = null;
    let lastChartUpdateAt = 0;
    let lastGraphPerfEventAt = 0;
    let lastRpmLabelValue = null;
    let lastPowerLabelValue = null;
    let lastHasRpm = null;

    const container = div({ className: 'graph-screen' });

    const graphProgressRing = div({ className: 'graph-progress-ring' });
    graphProgressRing.id = 'graph-progress-ring';
    container.appendChild(graphProgressRing);

    const graphPowerRpmRing = div({ className: 'graph-power-rpm-ring' });
    graphPowerRpmRing.id = 'graph-power-rpm-ring';
    container.appendChild(graphPowerRpmRing);

    const svgNS = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(svgNS, "svg");
    svg.setAttribute("viewBox", "0 0 500 500");
    svg.classList.add("graph-curved-labels");
    svg.style.position = "absolute";
    svg.style.width = "500px";
    svg.style.height = "500px";
    svg.style.top = "50%";
    svg.style.left = "50%";
    svg.style.transform = "translate(-50%, -50%)";
    svg.style.pointerEvents = "none";
    svg.style.zIndex = "10";

    const powerBar = document.createElementNS(svgNS, "path");
    powerBar.setAttribute("id", "graph-power-bar-svg");
    powerBar.setAttribute("fill", "none");
    svg.appendChild(powerBar);

    const rpmBar = document.createElementNS(svgNS, "path");
    rpmBar.setAttribute("id", "graph-rpm-bar-svg");
    rpmBar.setAttribute("fill", "none");
    svg.appendChild(rpmBar);

    container.appendChild(svg);

    const labelContainer = div({ className: 'graph-vertical-labels-wrapper' });
    const rpmLabel = div({ className: 'graph-vertical-label rpm', id: 'graph-rpm-label-html' });
    rpmLabel.innerHTML = '<span class="unit">kRPM</span><span class="val">0.0</span>';
    const powerLabel = div({ className: 'graph-vertical-label power', id: 'graph-power-label-html' });
    powerLabel.innerHTML = '<span class="val">0</span><span class="symbol">%</span>';

    labelContainer.appendChild(rpmLabel);
    labelContainer.appendChild(powerLabel);
    container.appendChild(labelContainer);

    const divider = div({ className: 'graph-selector-line' });
    const outerRing = div({ className: 'graph-outer-ring' });
    const innerBorder = div({ className: 'graph-inner-border' });
    const innerRingShadow = div({ className: 'graph-inner-ring-shadow' });
    const innerRing = div({ className: 'graph-inner-ring' });

    const dynamicTooltip = div({ className: 'dynamic-tooltip primary' });
    const tooltipValue = span({ className: 'tooltip-value' });
    const tooltipUnity = span({ className: 'tooltip-unity' });
    dynamicTooltip.appendChild(tooltipValue);
    dynamicTooltip.appendChild(tooltipUnity);

    const secondaryTooltip = div({ className: 'dynamic-tooltip secondary' });
    const secondaryTooltipValue = span({ className: 'tooltip-value' });
    const secondaryTooltipUnity = span({ className: 'tooltip-unity' });
    secondaryTooltip.appendChild(secondaryTooltipValue);
    secondaryTooltip.appendChild(secondaryTooltipUnity);

    const dynamicTooltipLine = div({ className: 'dynamic-tooltip-line primary' });
    const secondaryTooltipLine = div({ className: 'dynamic-tooltip-line secondary' });

    const tooltipMask = div({ className: 'graph-tooltip-mask' });
    innerRing.appendChild(tooltipMask);
    innerRing.appendChild(dynamicTooltip);
    innerRing.appendChild(secondaryTooltip);
    innerRing.appendChild(dynamicTooltipLine);
    innerRing.appendChild(secondaryTooltipLine);

    const timerTooltip = div({ className: 'timer-tooltip', id: 'timer-tooltip' });
    const timerTooltipValue = span({ className: 'timer-tooltip-value', id: 'timer-tooltip-value' });
    const timerTooltipUnity = span({ className: 'timer-tooltip-unity' });
    timerTooltip.appendChild(timerTooltipValue);
    timerTooltip.appendChild(timerTooltipUnity);
    timerTooltipValue.textContent = '--.-s';
    timerTooltipUnity.textContent = '0 - 100 km/h';

    innerRing.appendChild(timerTooltip);

    const warpCanvas = document.createElement('canvas');
    warpCanvas.id = 'warp-tunnel-canvas';
    warpCanvas.className = 'warp-tunnel-canvas';
    container.appendChild(warpCanvas);

    const canvas = document.createElement('canvas');
    canvas.className = 'graph-chart';
    canvas.id = 'graph-chart';
    canvas.width = 452;
    canvas.height = 452;
    innerRing.appendChild(canvas);

    container.appendChild(outerRing);
    container.appendChild(innerBorder);
    container.appendChild(innerRingShadow);
    container.appendChild(innerRing);

    const flashOverlay = div({ className: 'graph-flash-overlay' });
    container.appendChild(flashOverlay);

    // container.appendChild(flashOverlay); // Re-added below if needed


    const bulletContainer = div({ className: 'graph-bullet-container' });
    const bulletElements = {};

    graphList.forEach((itemData) => {
        const bulletEl = div({
            id: `bullet-${itemData.id}`,
            className: 'graph-bullet',
            'data-id': itemData.id
        });
        bulletContainer.appendChild(bulletEl);
        bulletElements[itemData.id] = bulletEl;
    });

    container.appendChild(bulletContainer);

    const graphTitleLabel = div({ className: 'graph-title-label' });
    container.appendChild(graphTitleLabel);

    const triggerFlash = (color = 'white') => {
        if (!flashOverlay || flashTriggered) return;
        flashTriggered = true;
        flashOverlay.style.background = `radial-gradient(circle, white 0%, ${color} 100%)`;
        flashOverlay.classList.add('screen-flash-animation');

        const onAnimationEnd = () => {
            flashOverlay.classList.remove('screen-flash-animation');
            flashOverlay.style.background = '';
            flashOverlay.removeEventListener('animationend', onAnimationEnd);
        };
        flashOverlay.addEventListener('animationend', onAnimationEnd);
    };

    const setWarpAnimation = (visible) => {
        if (!warpTunnel || !warpCanvas) return;
        if (visible) {
            if (!warpCanvas.classList.contains('visible')) {
                container.classList.add('warp-active');
                warpCanvas.classList.add('visible');
                warpTunnel.start();
            }
        } else {
            if (warpCanvas.classList.contains('visible')) {
                warpCanvas.classList.remove('visible');
                setTimeout(() => {
                    if (!warpCanvas.classList.contains('visible')) {
                        container.classList.remove('warp-active');
                        warpTunnel.stop();
                    }
                }, 2000);
            }
        }
    };

    const setChronometer = (action) => {
        switch (action) {
            case 'start':
                isSpeedTimerRunning = true;
                speedTimerStartTime = Date.now();
                last0To100Time = null;
                flashTriggered = false;
                if (timerHideTimeoutId) {
                    clearTimeout(timerHideTimeoutId);
                    timerHideTimeoutId = null;
                }
                timerTooltip.classList.add('visible');
                timerTooltipValue.textContent = '0.0s';
                break;
            case 'stop':
                isSpeedTimerRunning = false;
                flashTriggered = false;
                timerTooltip.classList.remove('visible');
                break;
            case 'success_hold':
                isSpeedTimerRunning = false;
                if (!timerHideTimeoutId) {
                    timerHideTimeoutId = setTimeout(() => {
                        setChronometer('stop');
                        timerHideTimeoutId = null;
                    }, TIMER_HIDE_DELAY);
                }
                break;
        }
    };

    const switchTo = (graphId) => {
        if (!chartInstance) return;
        const graphInfo = graphList.find(g => g.id === graphId);
        if (!graphInfo) return;

        const scales = chartInstance.options.scales;
        const hasSecondaryAxis = graphInfo.datasets.some(ds => ds.yAxisID === 'y1');
        scales.y1.display = hasSecondaryAxis;

        if (hasSecondaryAxis) {
            bulletContainer.classList.add('position-left');
            bulletContainer.classList.remove('position-right');
            dynamicTooltipLine.style.width = '80%';
            secondaryTooltipLine.style.width = '80%';
        } else {
            bulletContainer.classList.add('position-right');
            bulletContainer.classList.remove('position-left');
            dynamicTooltipLine.style.width = '95%';
            secondaryTooltipLine.style.width = '95%';
        }

        if (graphInfo.yAxis) {
            scales.y.min = graphInfo.yAxis.min;
            scales.y.max = graphInfo.yAxis.max;
            scales.y.ticks.stepSize = graphInfo.yAxis.stepSize;
        }
        if (graphInfo.y1Axis) {
            scales.y1.min = graphInfo.y1Axis.min;
            scales.y1.max = graphInfo.y1Axis.max;
            scales.y1.ticks.stepSize = graphInfo.y1Axis.stepSize;
        }

        const yDS = graphInfo.datasets.find(ds => ds.yAxisID === 'y');
        if (yDS && getChartColor(yDS, 'lineColor')) scales.y.ticks.color = getChartColor(yDS, 'lineColor') + 'B3';

        const y1DS = graphInfo.datasets.find(ds => ds.yAxisID === 'y1');
        if (y1DS && getChartColor(y1DS, 'lineColor')) scales.y1.ticks.color = getChartColor(y1DS, 'lineColor') + 'B3';

        // Set initial tooltip colors
        if (yDS) {
            const txtColor = getChartColor(yDS, 'tooltipColor') || getChartColor(yDS, 'lineColor');
            dynamicTooltip.querySelector('.tooltip-value').style.color = txtColor;
            dynamicTooltip.querySelector('.tooltip-unity').style.color = txtColor;
            dynamicTooltipLine.style.backgroundColor = getChartColor(yDS, 'lineColor');
        }
        if (y1DS) {
            const txtColor = getChartColor(graphInfo, 'secondaryTooltipColor') || getChartColor(y1DS, 'tooltipColor') || getChartColor(y1DS, 'lineColor');
            secondaryTooltip.querySelector('.tooltip-value').style.color = txtColor;
            secondaryTooltip.querySelector('.tooltip-unity').style.color = txtColor;
            secondaryTooltipLine.style.backgroundColor = getChartColor(y1DS, 'lineColor');
        }

        const newDatasets = [];
        graphInfo.datasets.forEach((datasetInfo) => {
            const dataKey = datasetInfo.avgInternalKey || datasetInfo.filterInternalKey || datasetInfo.dataKey;
            if (dataKey) {
                let color = getChartColor(datasetInfo, 'lineColor') || '#ffffff';
                if (datasetInfo.yAxisID === 'y2' && datasetInfo.followAxis) {
                    const followScale = datasetInfo.followAxis === 'y1' ? scales.y1 : scales.y;
                    scales.y2.min = followScale.min;
                    scales.y2.max = followScale.max;
                }
                const ds = {
                    label: datasetInfo.label,
                    data: historicalData[dataKey] || [],
                    yAxisID: datasetInfo.yAxisID,
                    borderColor: color,
                    backgroundColor: hexToRgba(color, datasetInfo.yAxisID === 'y' ? 0.15 : 0.1),
                    borderWidth: 2,
                    pointRadius: 0,
                    fill: datasetInfo.fill !== undefined ? datasetInfo.fill : (datasetInfo.yAxisID === 'y' || datasetInfo.yAxisID === 'y1'),
                    cubicInterpolationMode: 'monotone',
                    tension: 0.4,
                };
                Object.keys(datasetInfo).forEach(key => {
                    if (!['label', 'dataKey', 'type', 'avgConfig', 'unity', 'yAxisID', 'lineColor', 'fill', 'avgInternalKey', 'followAxis', 'idleKey', 'idleUnity', 'tooltipColor'].includes(key)) {
                        ds[key] = datasetInfo[key];
                    }
                });
                if (datasetInfo.positiveColor || datasetInfo.negativeColor) {
                    const posColor = getChartColor(datasetInfo, 'positiveColor') || color;
                    const negColor = getChartColor(datasetInfo, 'negativeColor') || color;
                    ds.segment = {
                        borderColor: ctx => ctx.p0.parsed.y < 0 ? negColor : posColor,
                        backgroundColor: ctx => ctx.p0.parsed.y < 0 ? hexToRgba(negColor, 0.15) : hexToRgba(posColor, 0.15)
                    };
                }
                newDatasets.push(ds);
            }
        });
        chartInstance.data.datasets = newDatasets;
        currentGraphId = graphId;
        chartInstance.update({ duration: 400 });
        emitPerfEvent('graph_switch', `graph=${graphId}`);
    };

    const updateFocus = (id) => {
        Object.values(bulletElements).forEach(bullet => {
            if (bullet.dataset.id === id) {
                bullet.classList.add('active');
            } else {
                bullet.classList.remove('active');
            }
        });
        const currentItem = graphList.find(item => item.id === id);
        if (currentItem) {
            graphTitleLabel.textContent = currentItem.displayLabel;
        }
    };

    const initChart = (ctx) => {
        if (chartInstance) return;
        warpTunnel = new WarpTunnelAnimation(warpCanvas);
        chartInstance = new Chart(ctx, {
            type: 'line',
            data: { datasets: [] },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false }, tooltip: { enabled: false } },
                layout: { padding: { left: 18, right: 18, top: 0, bottom: 0 } },
                scales: {
                    x: { type: 'realtime', display: false, realtime: { duration: HISTORY_DURATION, refresh: 250 } },
                    y: {
                        min: -20, max: 120,
                        ticks: { display: true, mirror: true, padding: 0, stepSize: 20, color: getComputedStyle(document.documentElement).getPropertyValue('--graph-tick-label-color').trim() || 'rgba(100,172,255,0.7)' },
                        grid: { display: true, drawOnChartArea: true, drawTicks: false, color: (ctx) => (ctx.tick.value === 0 ? getComputedStyle(document.documentElement).getPropertyValue('--graph-zero-grid-line-color').trim() || 'rgba(0, 195, 255, 1)' : getComputedStyle(document.documentElement).getPropertyValue('--graph-grid-line-color').trim() || 'rgba(0,160,255,0.3)'), lineWidth: (ctx) => (ctx.tick.value === 0 ? 4 : 1) },
                    },
                    y1: { id: 'y1', position: 'right', min: 0, max: 200, ticks: { display: true, mirror: true, padding: 0, align: 'start', stepSize: 2, color: getComputedStyle(document.documentElement).getPropertyValue('--graph-zero-grid-line-color').trim() || 'rgba(0, 255, 187, 0.5)' }, grid: { drawOnChartArea: false } },
                    y2: { id: 'y2', display: false, grid: { drawOnChartArea: false } }
                }
            }
        });

        uiUpdateInterval = setInterval(() => {
            try {
                if (!chartInstance || !currentGraphId) return;
                const graphInfo = graphList.find(g => g.id === currentGraphId);
                if (!graphInfo) return;
                const now = Date.now();
                if (now - lastGraphPerfEventAt >= 60_000) {
                    emitPerfEvent('graph_runtime', `graph=${currentGraphId}`);
                    lastGraphPerfEventAt = now;
                }

                const LINE_OFFSET = 13;
                dynamicTooltip.style.display = 'none';
                dynamicTooltip.style.opacity = '0';
                dynamicTooltipLine.style.opacity = '0';
                secondaryTooltip.style.display = 'none';
                secondaryTooltip.style.opacity = '0';
                secondaryTooltipLine.style.opacity = '0';

                const primaryDS = graphInfo.datasets.find(ds => ds.yAxisID === 'y');
                const secondaryDS = graphInfo.datasets.find(ds => ds.yAxisID === 'y1');

                let activeValue, activeUnity, secValue, secUnity;

                if (primaryDS) {
                    const dataKey = primaryDS.avgInternalKey || primaryDS.filterInternalKey || primaryDS.dataKey;
                    const val = getState(dataKey);
                    if (graphInfo.id === 'gasConsumption' && primaryDS.idleKey && val <= 0) {
                        activeValue = getState(primaryDS.idleKey);
                        activeUnity = primaryDS.idleUnity;
                    } else {
                        activeValue = val;
                        activeUnity = primaryDS.unity;
                    }
                }
                if (secondaryDS) {
                    const dataKey = secondaryDS.avgInternalKey || secondaryDS.filterInternalKey || secondaryDS.dataKey;
                    secValue = getState(dataKey);
                    secUnity = secondaryDS.unity;
                }

                const yAxis = chartInstance.scales.y;
                const y1Axis = chartInstance.scales.y1;

                if (primaryDS) {
                    const activeVal = activeValue !== undefined ? activeValue : 0;
                    const decimalPlaces = graphInfo.decimalPlaces !== undefined ? graphInfo.decimalPlaces : 1;
                    const vEl = dynamicTooltip.querySelector('.tooltip-value');
                    const uEl = dynamicTooltip.querySelector('.tooltip-unity');

                    if (primaryDS.positiveColor || primaryDS.negativeColor) {
                        let dsColor = getChartColor(primaryDS, 'lineColor') || '#ffffff';
                        let txtColor = getChartColor(primaryDS, 'tooltipColor') || dsColor;

                        if (primaryDS.positiveColor && activeVal >= 0) {
                            dsColor = getChartColor(primaryDS, 'positiveColor') || dsColor;
                            txtColor = getChartColor(primaryDS, 'tooltipColor') || dsColor;
                        } else if (primaryDS.negativeColor && activeVal < 0) {
                            dsColor = getChartColor(primaryDS, 'negativeColor') || dsColor;
                            txtColor = getChartColor(primaryDS, 'tooltipColor') || dsColor;
                        }
                        vEl.style.color = txtColor;
                        uEl.style.color = txtColor;
                        dynamicTooltipLine.style.backgroundColor = dsColor;
                    }

                    dynamicTooltip.style.display = 'flex';
                    dynamicTooltip.style.opacity = '1';
                    vEl.textContent = activeVal.toFixed(decimalPlaces);
                    uEl.textContent = activeUnity;
                    dynamicTooltipLine.style.top = `${yAxis.getPixelForValue(activeVal) + LINE_OFFSET}px`;
                    dynamicTooltipLine.style.opacity = 0.5;
                }
                if (secondaryDS) {
                    const val = secValue !== undefined ? secValue : 0;
                    const decimalPlaces = graphInfo.secondaryDecimalPlaces !== undefined ? graphInfo.secondaryDecimalPlaces : 1;
                    const vEl = secondaryTooltip.querySelector('.tooltip-value');
                    const uEl = secondaryTooltip.querySelector('.tooltip-unity');

                    if (secondaryDS.positiveColor || secondaryDS.negativeColor) {
                        let dsColor = getChartColor(secondaryDS, 'lineColor') || '#ffffff';
                        let txtColor = getChartColor(graphInfo, 'secondaryTooltipColor') || getChartColor(secondaryDS, 'tooltipColor') || dsColor;

                        if (secondaryDS.positiveColor && val >= 0) {
                            dsColor = getChartColor(secondaryDS, 'positiveColor') || dsColor;
                            txtColor = getChartColor(graphInfo, 'secondaryTooltipColor') || getChartColor(secondaryDS, 'tooltipColor') || dsColor;
                        } else if (secondaryDS.negativeColor && val < 0) {
                            dsColor = getChartColor(secondaryDS, 'negativeColor') || dsColor;
                            txtColor = getChartColor(graphInfo, 'secondaryTooltipColor') || getChartColor(secondaryDS, 'tooltipColor') || dsColor;
                        }
                        vEl.style.color = txtColor;
                        uEl.style.color = txtColor;
                        secondaryTooltipLine.style.backgroundColor = dsColor;
                    }

                    secondaryTooltip.style.display = 'flex';
                    secondaryTooltip.style.opacity = '1';
                    vEl.textContent = val.toFixed(decimalPlaces);
                    uEl.textContent = secUnity;
                    secondaryTooltipLine.style.top = `${y1Axis.getPixelForValue(val) + LINE_OFFSET}px`;
                    secondaryTooltipLine.style.opacity = 0.5;
                }

                if (graphInfo.id === 'carSpeed') {
                    const currentSpeed = getState('carSpeed') || 0.0;
                    const drivingMode = getState('drivingMode');
                    const acceleration = parseFloat(currentSpeed - lastCarSpeed) * (1000 / UI_UPDATE_INTERVAL);
                    const isFastAcc = acceleration >= (100 / ACCELERATION_THRESHOLD);

                    if (isSpeedTimerRunning) {
                        const elapsed = (now - speedTimerStartTime) / 1000;
                        if (elapsed > 15) { triggerFlash('red'); setWarpAnimation(false); setChronometer('stop'); }
                        else if (currentSpeed >= 100) {
                            if (!last0To100Time) last0To100Time = elapsed.toFixed(1);
                            timerTooltipValue.textContent = `${last0To100Time}s`;
                            if (drivingMode === 'Sport') triggerFlash('white');
                            setChronometer('success_hold');
                        } else if (currentSpeed === 0) { setWarpAnimation(false); setChronometer('stop'); }
                        else {
                            timerTooltipValue.textContent = `${elapsed.toFixed(1)}s`;
                            timerTooltip.classList.add('visible');
                            if (acceleration > 0 && drivingMode === 'Sport') { setWarpAnimation(true); if (warpTunnel) warpTunnel.setSpeed(currentSpeed); }
                        }
                    } else if (currentSpeed === 0) { last0To100Time = null; }
                    else if (isFastAcc && currentSpeed < 10 && currentSpeed > 1 && drivingMode === 'Sport') { setChronometer('start'); }

                    if (lastCarSpeed === 0 && currentSpeed > 0 && isFastAcc && drivingMode === 'Sport') { triggerFlash('orange'); setChronometer('start'); setWarpAnimation(true); }
                    else if (currentSpeed >= 100 && currentSpeed < lastCarSpeed) { setWarpAnimation(false); }
                    lastCarSpeed = currentSpeed;
                } else { setWarpAnimation(false); setChronometer('stop'); }

                if (chartInstance && chartInstance.ctx && chartInstance.canvas) {
                    if (now - lastChartUpdateAt >= CHART_UPDATE_INTERVAL) {
                        chartInstance.update('quiet');
                        lastChartUpdateAt = now;
                    }
                }

                // Update Rings
                const powerV = getState('evPowerFactor');
                const rpmV = getState('engineRPM');
                const powerBarEl = powerBar;
                const rpmBarEl = rpmBar;
                if (powerBarEl && rpmBarEl) {
                    const isRegen = powerV < 0;
                    if (isRegen) powerBarEl.classList.add('regen-active'); else powerBarEl.classList.remove('regen-active');
                    const wrapAngle = (a) => ((a % 360) + 360) % 360;
                    const pAngleWidth = powerV >= 0 ? (powerV / 100) * 180 : (powerV / 100) * 90;
                    const rAngleWidth = (rpmV / 7000) * 90;
                    const pStart = 270;
                    const tipAngle = wrapAngle(pStart + pAngleWidth);
                    const rStart = (powerV >= 0 ? tipAngle : 270) + 2;
                    const rEnd = wrapAngle(rStart + rAngleWidth);
                    const radius = 217, cx = 250, cy = 250;
                    const getCoords = (deg) => { const rad = (deg - 90) * Math.PI / 180; return { x: cx + radius * Math.cos(rad), y: cy + radius * Math.sin(rad) }; };
                    const pS = getCoords(pStart), pE = getCoords(tipAngle), pLarge = Math.abs(pAngleWidth) > 180 ? 1 : 0, pSweep = powerV >= 0 ? 1 : 0;
                    if (Math.abs(pAngleWidth) > 0.1) {
                        powerBarEl.setAttribute("d", `M ${pS.x} ${pS.y} A ${radius} ${radius} 0 ${pLarge} ${pSweep} ${pE.x} ${pE.y}`);
                        powerBarEl.style.opacity = 1; powerBarEl.setAttribute("stroke-width", "8");
                        const dash = isRegen ? 30.2 : 64.4; powerBarEl.setAttribute("stroke-dasharray", `${dash} 4.0`);
                    } else powerBarEl.style.opacity = 0;
                    const rS = getCoords(rStart), rE = getCoords(rEnd), rLarge = Math.abs(rAngleWidth) > 180 ? 1 : 0;
                    if (rpmV > 1) {
                        rpmBarEl.setAttribute("d", `M ${rS.x} ${rS.y} A ${radius} ${radius} 0 ${rLarge} 1 ${rE.x} ${rE.y}`);
                        rpmBarEl.style.opacity = 1; rpmBarEl.setAttribute("stroke-width", "8");
                        rpmBarEl.setAttribute("stroke-dasharray", "44.9 4.01");
                    } else rpmBarEl.style.opacity = 0;
                    if (rpmLabel) {
                        const rpmVal = (rpmV / 1000).toFixed(1);
                        if (lastRpmLabelValue !== rpmVal) {
                            rpmLabel.innerHTML = `<span class="val">${rpmVal}</span><span class="unit">RPM</span>`;
                            lastRpmLabelValue = rpmVal;
                        }
                        rpmLabel.style.opacity = rpmV > 1 ? 1 : 0;
                    }
                    if (powerLabel) {
                        const pwrVal = Math.abs(Math.round(powerV));
                        if (lastPowerLabelValue !== pwrVal) {
                            powerLabel.innerHTML = `<span class="val">${pwrVal}</span><span class="symbol">%</span>`;
                            lastPowerLabelValue = pwrVal;
                        }
                        powerLabel.style.opacity = pwrVal > 0 ? 1 : 0;
                        if (isRegen) powerLabel.classList.add('regen'); else powerLabel.classList.remove('regen');
                    }
                    if (labelContainer) {
                        const hasRpm = rpmV > 1;
                        if (lastHasRpm !== hasRpm) {
                            if (hasRpm) labelContainer.classList.add('has-rpm'); else labelContainer.classList.remove('has-rpm');
                            lastHasRpm = hasRpm;
                        }
                    }
                }
            } catch (e) { console.error('Error: ', e); }
        }, UI_UPDATE_INTERVAL);

        switchTo(currentGraphId);
        emitPerfEvent('graph_mount', `graph=${currentGraphId}`);
    };

    const unsubCurrentGraph = subscribe('currentGraph', (id) => {
        switchTo(id);
        updateFocus(id);
    });

    const cleanup = () => {
        emitPerfEvent('graph_cleanup', `graph=${currentGraphId}`);
        if (uiUpdateInterval) { clearInterval(uiUpdateInterval); uiUpdateInterval = null; }
        if (warpTunnel) { warpTunnel.stop(); }
        if (timerHideTimeoutId) { clearTimeout(timerHideTimeoutId); timerHideTimeoutId = null; }
        if (chartInstance) { chartInstance.destroy(); chartInstance = null; }
        unsubCurrentGraph();
    };

    updateFocus(currentGraphId);

    return {
        element: container,
        onMount: () => {
            initChart(canvas);
        },
        cleanup
    };
}
