/* Static scale marks share the exact calibration used by the moving light. */
(() => {
    const geometry = window.ApexGaugeGeometry;
    const svg = document.querySelector('.gauge-scales');
    if (!geometry || !svg) return;
    const ticks = [], speedLabels = [], powerLabels = [];
    const speedLabelsToShow = [0, 10, 20, 30, 40, 50, 60, 80, 100, 140, 180];
    for (const value of [0, 10, 20, 30, 40, 50, 60, 70, 80, 100, 120, 140, 160, 180]) {
        const y = geometry.speedY(value), edge = geometry.leftBounds(y).inner;
        const major = speedLabelsToShow.includes(value);
        ticks.push(`M${edge + 5} ${y}h${major ? 15 : 10}`);
        if (major) {
            const kind = value >= 140 ? 'scale-compressed'
                : [40, 100].includes(value) ? 'scale-medium'
                : [10, 20, 30, 50, 60].includes(value) ? 'scale-secondary' : 'scale-primary';
            const labelY = y + (value === 180 ? 10
                : value === 100 ? 7
                : value === 60 ? 2
                : value === 50 ? 3 : 12);
            speedLabels.push(`<text class="${kind}" x="${edge + 34}" y="${labelY}">${value}</text>`);
        }
    }
    const powerTicks = [];
    const powerLabelsToShow = [-100, -50, -30, -20, -10, 0, 10, 20, 30, 50, 100];
    for (const value of [-100, -75, -50, -40, -30, -20, -10, 0, 10, 20, 30, 40, 50, 75, 100]) {
        const y = geometry.powerY(value), edge = geometry.rightEdge(y);
        const major = powerLabelsToShow.includes(value);
        powerTicks.push(`M${edge - 1} ${y}h-${major ? 17 : 10}`);
        if (major) {
            const kind = Math.abs(value) === 100 ? 'scale-endpoint'
                : value === 0 || [10, 20, 30].includes(Math.abs(value)) ? 'scale-secondary' : 'scale-primary';
            const labelY = y + (value === 100 ? 10 : value === 30 ? 7 : value === -30 ? 13 : value === 10 ? 13 : 12);
            powerLabels.push(`<text class="${kind}" x="${edge - 32}" y="${labelY}" text-anchor="end">${value < 0 ? '−' + -value : value}</text>`);
        }
    }
    svg.innerHTML = `<g class="ticks speed-ticks"><path d="${ticks.join(' ')}"/></g><g class="ticks power-ticks"><path d="${powerTicks.join(' ')}"/></g><g class="scale-labels speed-scale-labels">${speedLabels.join('')}</g><g class="scale-labels power-scale-labels">${powerLabels.join('')}</g>`;
})();
