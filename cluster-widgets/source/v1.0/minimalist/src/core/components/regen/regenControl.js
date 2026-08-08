import {getState, subscribe} from '../../state.js';
import {div, img, span} from '../../../../../shared/utils/createElement.js';

import { Chart, registerables } from 'chart.js';
import streamingPlugin from 'chartjs-plugin-streaming';
import 'chartjs-adapter-date-fns';
Chart.register(...registerables, ...streamingPlugin);

export const regenItems = [
    {id: 'Alto', displayLabel: 'ALTO'},
    {id: 'Normal', displayLabel: 'NORMAL'},
    {id: 'Baixo', displayLabel: 'BAIXO'},
];

export function createRegenScreen() {
    const regenStatus = getState('regenMode');
    let chartInstance = null;
    let dataUpdater = null;

    const main = div({className: 'main-container'});
    const container = div({className: 'regen-screen'});

    // A clean vertical content layout
    const content = div({className: 'regen-content'});

    // List container
    const listContainer = div({className: 'regen-list'});

    const itemElements = {};
    regenItems.forEach((itemData, index) => {
        const isFocused = itemData.id === regenStatus;

        const itemEl = div({
            id: itemData.id,
            className: `regen-item ${isFocused ? 'focused' : ''}`,
            'data-index': index,
            children: [
                span({
                    className: 'regen-label',
                    children: [itemData.displayLabel]
                })
            ]
        });

        listContainer.appendChild(itemEl);
        itemElements[itemData.id] = itemEl;
    });

    const onePedalInstruction = div({
        className: 'one-pedal-instruction',
        children: [
            'Long press ',
            span({
                className: 'font-bold',
                children: ['OK']
            }),
            ' toggles One-Pedal'
        ]
    });

    const onePedalModeLabel = div({
        id: 'one-pedal-mode-label',
        className: `one-pedal-mode-label ${getState('onepedal') ? '' : 'hidden'}`,
        children: ['One-Pedal']
    });

    // Chart container (positioned absolutely in background)
    const chartContainer = div({className: 'regen-chart-container'});
    const canvas = document.createElement('canvas');
    canvas.className = 'regen-chart';
    canvas.id = 'regen-chart';

    // Instant Regen kW display overlay
    const instantRegenEl = div({className: 'instant-regen-value'});

    // 3 Ruler Dots representing 100%, 0%, -100%
    const dotsContainer = div({className: 'graph-dots'});
    const dotTop = div({className: 'graph-dot top'});
    const dotMid = div({className: 'graph-dot mid'});
    const dotBot = div({className: 'graph-dot bot'});
    dotsContainer.appendChild(dotTop);
    dotsContainer.appendChild(dotMid);
    dotsContainer.appendChild(dotBot);

    chartContainer.appendChild(dotsContainer);
    chartContainer.appendChild(canvas);

    // Assembly — chart first so labels/content always paint above it
    content.appendChild(listContainer);
    content.appendChild(onePedalModeLabel);

    container.appendChild(chartContainer);
    container.appendChild(content);
    container.appendChild(onePedalInstruction);
    container.appendChild(instantRegenEl);
    main.appendChild(container);

    const initChart = (canvasCtx) => {
        if (chartInstance) return;

        const tickColor = 'rgba(255, 255, 255, 0.3)';

        chartInstance = new Chart(canvasCtx, {
            type: 'line',
            data: {
                datasets: [{
                    label: 'EV Power',
                    borderWidth: 1.5,
                    pointRadius: 0,
                    fill: true,
                    tension: 0.3,
                    data: [],
                    segment: {
                        borderColor: ctx => (ctx && ctx.p0 && ctx.p0.parsed && ctx.p0.parsed.y < 0) ? '#00ff88' : '#ffffff',
                        backgroundColor: ctx => (ctx && ctx.p0 && ctx.p0.parsed && ctx.p0.parsed.y < 0) ? 'rgba(0, 255, 136, 0.04)' : 'rgba(255, 255, 255, 0.02)'
                    }
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { enabled: false },
                    streaming: {
                        duration: 30000,
                        refresh: 1000,
                    }
                },
                scales: {
                    x: { type: 'realtime', display: false },
                    y: {
                        display: true,
                        position: 'right',
                        min: -100,
                        max: 100,
                        ticks: {
                            display: false // hide Y-axis ticks, replaced by dots ruler
                        },
                        grid: {
                            display: true,
                            drawOnChartArea: true,
                            drawTicks: false,
                            color: (ctx) => {
                                return ctx.tick.value === 0 ? 'rgba(255, 255, 255, 0.15)' : 'rgba(255, 255, 255, 0.04)';
                            }
                        }
                    }
                }
            }
        });

        dataUpdater = setInterval(() => {
            if (!chartInstance) return;

            const data = chartInstance.data.datasets[0].data;
            const newValue = getState('evPowerKw') || 0;

            data.push({
                x: Date.now(),
                y: newValue
            });

            if (data.length > 30) {
                data.shift();
            }

            if (chartInstance && chartInstance.ctx && chartInstance.canvas) {
                chartInstance.update('quiet');
            }
        }, 1000);
    };

    const updateFocus = (status) => {
        Object.values(itemElements).forEach(el => {
            if (el.id === status) {
                el.classList.add('focused');
            } else {
                el.classList.remove('focused');
            }
        });
    };

    updateFocus(regenStatus);

    const toggleOnePedalView = (isOnePedalActive) => {
        const elementsToHide = [
            ...Object.values(itemElements)
        ];

        if (isOnePedalActive) {
            elementsToHide.forEach(el => el.classList.add('hidden'));
            onePedalModeLabel.classList.remove('hidden');
        } else {
            elementsToHide.forEach(el => el.classList.remove('hidden'));
            onePedalModeLabel.classList.add('hidden');
            updateFocus(getState('regenMode'));
        }
    };
    toggleOnePedalView(getState('onepedal'));

    const updateInstantRegenDisplay = (val) => {
        const value = val || 0;
        if (value < 0) {
            const absoluteKw = Math.abs(value).toFixed(1);
            instantRegenEl.innerHTML = `REGEN <span class="val">${absoluteKw}</span> <span class="unit">kW</span>`;
            instantRegenEl.classList.add('visible');
        } else {
            instantRegenEl.classList.remove('visible');
        }
    };
    updateInstantRegenDisplay(getState('evPowerKw'));

    const updateFocusAreaStyle = () => {
        const area = getState('menuFocusArea');
        const screen = getState('screen');
        const focusedMain = getState('focusedMenuItem');

        // Expanded list when editing from main menu (Enter on Regeneração)
        // or when the dedicated regen screen is open.
        const isSubFocus =
            screen === 'regen' ||
            (area === 'sub' && focusedMain === 'option_6');

        if (isSubFocus) {
            container.classList.add('sub-focus-active');
            main.classList.add('sub-focus-active');
        } else {
            container.classList.remove('sub-focus-active');
            main.classList.remove('sub-focus-active');
        }
    };
    updateFocusAreaStyle();

    const subscriptions = [
        subscribe('regenMode', updateFocus),
        subscribe('onepedal', toggleOnePedalView),
        subscribe('evPowerKw', updateInstantRegenDisplay),
        subscribe('menuFocusArea', updateFocusAreaStyle),
        subscribe('screen', updateFocusAreaStyle),
        subscribe('focusedMenuItem', updateFocusAreaStyle)
    ];

    const cleanup = () => {
        subscriptions.forEach(unsub => unsub());
        if (dataUpdater) {
            clearInterval(dataUpdater);
            dataUpdater = null;
        }
        if (chartInstance) {
            chartInstance.destroy();
            chartInstance = null;
        }
    };

    return {
        element: main,
        cleanup,
        onMount: () => {
            initChart(canvas);
        }
    };
}
