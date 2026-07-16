import { getState, subscribe } from '../../state.js';
import { div, span } from '../../../../../shared/utils/createElement.js';

function createInfoRow({ id, label, initial = '--' }) {
    return div({
        className: 'info-row',
        children: [
            span({ className: 'info-item-label', children: [label] }),
            span({ className: 'info-item-val', id, children: [initial] })
        ]
    });
}

export function createInfoScreen() {
    const container = div({ className: 'info-screen' });
    const list = div({ className: 'info-list' });

    const rows = [
        createInfoRow({ id: 'info-fuel-val', label: 'Gasolina', initial: '-- L' }),
        createInfoRow({ id: 'info-bat-val', label: 'Bateria', initial: '-- %' }),
        createInfoRow({ id: 'info-odo-val', label: 'Odômetro', initial: '-- km' }),
        createInfoRow({ id: 'info-rev-val', label: 'Revisão', initial: '-- km' }),
        createInfoRow({ id: 'info-pwr-inst-val', label: 'Pot. Inst.', initial: '-- kW' }),
        createInfoRow({ id: 'info-pwr-avg-val', label: 'Pot. Média', initial: '-- kW' }),
        createInfoRow({ id: 'info-cons-inst-val', label: 'Cons. EV', initial: '-- kWh' }),
        createInfoRow({ id: 'info-gas-cons-val', label: 'Cons. Comb.', initial: '-- Km/l' }),
    ];

    rows.forEach((row) => list.appendChild(row));
    container.appendChild(list);

    const updateRevisionDisplay = () => {
        const currentKm = getState('odometer') || 0;
        const nextKm = getState('nextRevisionKm') || 15000;
        const nextDateMillis = getState('nextRevisionDate') || 0;
        const remainingKm = nextKm - currentKm;
        let remainingDays = 0;
        if (nextDateMillis > 0) {
            const remainingMillis = nextDateMillis - Date.now();
            remainingDays = Math.ceil(remainingMillis / (1000 * 60 * 60 * 24));
        }

        let text = `${remainingKm}<span class="unit"> km</span>`;
        if (remainingDays > 0) {
            text += ` <span class="range">/ ${remainingDays}<span class="unit">d</span></span>`;
        }
        const valEl = container.querySelector('#info-rev-val');
        if (valEl) valEl.innerHTML = text;
    };

    const updateGasConsumption = () => {
        const mode = getState('gasConsumptionMode') || 'Running';
        let val, metric;
        if (mode === 'Idle') {
            val = getState('gasConsumptionIdle') || 0.0;
            metric = getState('gasConsumptionMetricIdle') || 'L/h';
        } else {
            val = getState('gasConsumption') || 0.0;
            metric = getState('gasConsumptionMetric') || 'Km/l';
        }
        const valEl = container.querySelector('#info-gas-cons-val');
        if (valEl) valEl.innerHTML = `${Number(val).toFixed(1)}<span class="unit"> ${metric}</span>`;
    };

    const subscriptions = [
        subscribe('fuelPercent', (val) => {
            const liters = Math.round((val || 0) * 55 / 100);
            const range = getState('fuelRange') || 0;
            container.querySelector('#info-fuel-val').innerHTML =
                `${liters}<span class="unit"> L</span><span class="range">${range} km</span>`;
        }),
        subscribe('fuelRange', (val) => {
            const pct = getState('fuelPercent') || 0;
            const liters = Math.round(pct * 55 / 100);
            container.querySelector('#info-fuel-val').innerHTML =
                `${liters}<span class="unit"> L</span><span class="range">${val || 0} km</span>`;
        }),
        subscribe('batteryPercent', (val) => {
            const range = getState('batteryRange') || 0;
            container.querySelector('#info-bat-val').innerHTML =
                `${val || 0}<span class="unit"> %</span><span class="range">${range} km</span>`;
        }),
        subscribe('batteryRange', (val) => {
            const pct = getState('batteryPercent') || 0;
            container.querySelector('#info-bat-val').innerHTML =
                `${pct}<span class="unit"> %</span><span class="range">${val || 0} km</span>`;
        }),
        subscribe('odometer', (val) => {
            container.querySelector('#info-odo-val').innerHTML =
                `${val || 0}<span class="unit"> km</span>`;
            updateRevisionDisplay();
        }),
        subscribe('nextRevisionKm', updateRevisionDisplay),
        subscribe('nextRevisionDate', updateRevisionDisplay),
        subscribe('evPowerKw', (val) => {
            container.querySelector('#info-pwr-inst-val').innerHTML =
                `${(val || 0).toFixed(1)}<span class="unit"> kW</span>`;
        }),
        subscribe('evPowerKwAvg', (val) => {
            container.querySelector('#info-pwr-avg-val').innerHTML =
                `${(val || 0).toFixed(1)}<span class="unit"> kW</span>`;
        }),
        subscribe('instantEVConsumption', (val) => {
            container.querySelector('#info-cons-inst-val').innerHTML =
                `${(val || 0).toFixed(1)}<span class="unit"> kWh</span>`;
        }),
        subscribe('gasConsumption', updateGasConsumption),
        subscribe('gasConsumptionIdle', updateGasConsumption),
        subscribe('gasConsumptionMode', updateGasConsumption),
    ];

    const initData = () => {
        const fuelPct = getState('fuelPercent') || 0;
        const fuelRange = getState('fuelRange') || 0;
        const batPct = getState('batteryPercent') || 0;
        const batRange = getState('batteryRange') || 0;
        const odo = getState('odometer') || 0;
        const pwrInst = getState('evPowerKw') || 0;
        const pwrAvg = getState('evPowerKwAvg') || 0;
        const consInst = getState('instantEVConsumption') || 0;

        container.querySelector('#info-fuel-val').innerHTML =
            `${Math.round(fuelPct * 55 / 100)}<span class="unit"> L</span><span class="range">${fuelRange} km</span>`;
        container.querySelector('#info-bat-val').innerHTML =
            `${batPct}<span class="unit"> %</span><span class="range">${batRange} km</span>`;
        container.querySelector('#info-odo-val').innerHTML =
            `${odo}<span class="unit"> km</span>`;
        container.querySelector('#info-pwr-inst-val').innerHTML =
            `${pwrInst.toFixed(1)}<span class="unit"> kW</span>`;
        container.querySelector('#info-pwr-avg-val').innerHTML =
            `${pwrAvg.toFixed(1)}<span class="unit"> kW</span>`;
        container.querySelector('#info-cons-inst-val').innerHTML =
            `${consInst.toFixed(1)}<span class="unit"> kWh</span>`;

        updateRevisionDisplay();
        updateGasConsumption();
    };
    initData();

    const cleanup = () => {
        subscriptions.forEach(unsub => unsub());
    };

    return { element: container, cleanup };
}
