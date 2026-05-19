import {getState, subscribe} from '../../state.js';
import {div, img, span} from '../../../utils/createElement.js';

import { Chart, registerables } from 'chart.js';
import streamingPlugin from 'chartjs-plugin-streaming';
import 'chartjs-adapter-date-fns';
Chart.register(...registerables, streamingPlugin);

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

    const regenProgressRing = div({className: 'regen-progress-ring'});
    regenProgressRing.id = 'regen-progress-ring';
    container.appendChild(regenProgressRing);

    const divider = div({className: 'regen-selector-line'});
    const outerRing = div({className: 'regen-outer-ring'});
    const innerRingShadow = div({className: 'regen-inner-ring-shadow'});
    const innerRing = div({className: 'regen-inner-ring'});

    const canvas = document.createElement('canvas');
    canvas.className = 'regen-chart';
    canvas.id = 'regen-chart';
    innerRing.appendChild(canvas);

    container.appendChild(outerRing);
    container.appendChild(innerRingShadow);
    container.appendChild(innerRing);
    main.appendChild(container);

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

        innerRing.appendChild(itemEl);
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
    innerRing.appendChild(onePedalInstruction);

    const onePedalModeLabel = div({
        id: 'one-pedal-mode-label',
        className: `one-pedal-mode-label ${getState('onepedal') ? '' : 'hidden'}`,
        children: ['One-Pedal']
    });
    innerRing.appendChild(onePedalModeLabel);

    innerRing.appendChild(divider);

    const initChart = (canvasCtx) => {
        if (chartInstance) return;

        const style = getComputedStyle(document.documentElement);
        const lineColor = style.getPropertyValue('--color-cyan-20').trim() || 'rgba(0, 195, 255, 0.3)';
        const fillColor = style.getPropertyValue('--color-dial-blue-20').trim() || 'rgba(0, 120, 255, 0.1)';
        const tickColor = style.getPropertyValue('--color-ring-blue-70').trim() || 'rgba(100,172,255,0.7)';
        const gridColor = style.getPropertyValue('--color-blue-glow-20').trim() || 'rgba(0,160,255,0.1)';

        chartInstance = new Chart(canvasCtx, {
            type: 'line',
            data: {
                datasets: [{
                    label: 'Regen Power',
                    backgroundColor: fillColor,
                    borderColor: lineColor,
                    borderWidth: 2,
                    pointRadius: 0,
                    fill: true,
                    tension: 0.3,
                    data: []
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { enabled: true },
                    streaming: {
                        duration: 30000,
                        refresh: 1000,
                    }
                },
                scales: {
                    x: { type: 'realtime', display: false },
                    y: {
                        min: -10,
                        max: 110,
                        grace: 20,
                        display: true,
                        ticks: {
                            stepSize: 10,
                            padding: 17,
                            color: tickColor,
                            callback: function(value) { return value >= 30 && value <= 70 ? value : ''; },
                        },
                        grid: {
                            display: true,
                            drawOnChartArea: true,
                            drawTicks: true,
                            color: gridColor
                        },
                    }
                }
            }
        });

        dataUpdater = setInterval(() => {
            if (!chartInstance) return;

            const data = chartInstance.data.datasets[0].data;
            const newValue = getState('lastRegenValue') || 0;

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
        updateProgressRings();
    };

    updateFocus(regenStatus);
    const unsubscribe = subscribe('regenMode', updateFocus);

    const toggleOnePedalView = (isOnePedalActive) => {
        const elementsToHide = [
            ...Object.values(itemElements),
            divider,
            regenProgressRing
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
    const unsubscribeOnePedal = subscribe('onepedal', toggleOnePedalView);
    toggleOnePedalView(getState('onepedal'));

    const cleanup = () => {
        unsubscribe();
        unsubscribeOnePedal();
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
            updateProgressRings(); 
            initChart(canvas);
        }
    };
}


export function updateProgressRings() {
    const regenRing = document.getElementById('regen-progress-ring');

    var position = 0;
    const regenMode = getState('regenMode');
    regenItems.forEach((itemData, index) => {
        if (itemData.id === regenMode) {
            position = 3 - index;
        }
    });

    if (regenRing) {
        regenRing.style.setProperty('--regen-segment-active-level', position);
    }

}
