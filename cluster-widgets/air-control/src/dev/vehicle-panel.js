// Painel direito — controles do veículo, simulação e log de automações

const GEAR_LABELS = { 0: 'N', 1: 'D', 2: 'B', 3: 'P', 4: 'R' };
const GEAR_COLORS = { 0: '#9ca3af', 1: '#34d399', 2: '#60a5fa', 3: '#f59e0b', 4: '#f87171' };

function ts() {
    const n = new Date();
    return n.toLocaleTimeString('pt-BR', { hour12: false }) + '.' + String(n.getMilliseconds()).padStart(3, '0');
}

// ── DOM helpers ───────────────────────────────────────────────────────────────
function el(tag, attrs = {}, ...children) {
    const n = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
        if (k === 'class') n.className = v;
        else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
        else n.setAttribute(k, v);
    }
    children.forEach(c => n.appendChild(typeof c === 'string' ? document.createTextNode(c) : c));
    return n;
}
const sec   = (title, ...ch) => el('div', { class: 'vp-section' }, el('div', { class: 'vp-title' }, title), ...ch);
const row   = (lbl, ctrl)    => el('div', { class: 'vp-row' }, el('label', { class: 'vp-label' }, lbl), ctrl);

function slider(key, min, max, step, initial) {
    const val = el('span', { class: 'vp-val' }, String(initial));
    const inp = el('input', { type: 'range', class: 'vp-slider', min, max, step, value: initial,
        oninput: () => { val.textContent = inp.value; window.vehicleEngine.set(key, parseFloat(inp.value)); }
    });
    const wrap = el('div', { class: 'vp-slider-wrap' }, inp, val);
    window.vehicleEngine.subscribe((k, v) => { if (k === key) { inp.value = v; val.textContent = v; } });
    return wrap;
}

function toggle(key, labelOn = 'ON', labelOff = 'OFF', valOn = 1, valOff = 0) {
    const btn = el('button', { class: 'vp-btn', onclick: () => {
        const on = btn.dataset.on === 'true';
        btn.dataset.on = !on;
        btn.textContent = !on ? labelOn : labelOff;
        btn.classList.toggle('active', !on);
        window.vehicleEngine.set(key, !on ? valOn : valOff);
    }});
    btn.dataset.on = 'false';
    btn.textContent = labelOff;
    window.vehicleEngine.subscribe((k, v) => {
        if (k !== key) return;
        const on = v == valOn;
        btn.dataset.on = on;
        btn.textContent = on ? labelOn : labelOff;
        btn.classList.toggle('active', on);
    });
    return btn;
}

function selectCtrl(key, opts) {
    const sel = el('select', { class: 'vp-select', onchange: () => window.vehicleEngine.set(key, parseFloat(sel.value) || sel.value) });
    opts.forEach(([v, l]) => { const o = document.createElement('option'); o.value = v; o.textContent = l; sel.appendChild(o); });
    window.vehicleEngine.subscribe((k, v) => { if (k === key) sel.value = v; });
    return sel;
}

// ── Gear indicator ────────────────────────────────────────────────────────────
function buildGearIndicator() {
    const gears = [
        ['P', 3], ['R', 4], ['N', 0], ['D', 1],
    ];
    const wrap = el('div', { class: 'vp-gear-row' });
    const btns = {};
    gears.forEach(([lbl, val]) => {
        const b = el('button', { class: 'vp-gear-btn', onclick: () => {
            window.vehicleEngine.set('CAR_BASIC_GEAR_STATUS', val);
        }}, lbl);
        b.style.borderColor = GEAR_COLORS[val];
        btns[val] = b;
        wrap.appendChild(b);
    });
    function highlight(v) {
        Object.entries(btns).forEach(([k, b]) => {
            const active = parseInt(k) === parseInt(v);
            b.classList.toggle('selected', active);
            b.style.background = active ? GEAR_COLORS[v] + '33' : '';
        });
    }
    highlight(3); // P default
    window.vehicleEngine.subscribe((k, v) => { if (k === 'CAR_BASIC_GEAR_STATUS') highlight(v); });
    return wrap;
}

// ── Log ───────────────────────────────────────────────────────────────────────
let logEl = null;

function renderLog() {
    if (!logEl) return;
    logEl.innerHTML = window.vehicleEngine.logEntries
        .map(e => `<div class="vp-log-row"><span class="vp-log-ts">${e.ts}</span><span class="vp-log-msg">${e.msg}</span></div>`)
        .join('');
}

// ── Simulation section ────────────────────────────────────────────────────────
function buildSimSection() {
    let mult = 1;
    let running = false;

    const playBtn = el('button', { class: 'vp-btn vp-btn-wide', onclick: () => {
        running = !running;
        if (running) { window.vehicleEngine.startSimulation(mult); playBtn.textContent = '⏸ Pausar'; playBtn.classList.add('active'); }
        else          { window.vehicleEngine.stopSimulation();       playBtn.textContent = '▶ Simular'; playBtn.classList.remove('active'); }
    }}, '▶ Simular');

    const multSel = el('select', { class: 'vp-select', onchange: () => {
        mult = parseFloat(multSel.value);
        if (running) { window.vehicleEngine.startSimulation(mult); }
    }});
    [['0.5','0.5×'],['1','1×'],['2','2×'],['5','5×']].forEach(([v,l]) => {
        const o = document.createElement('option'); o.value = v; o.textContent = l; if (v === '1') o.selected = true;
        multSel.appendChild(o);
    });

    const scenarioRow = el('div', { class: 'vp-scenario-row' });
    Object.entries(window.vehicleEngine.SCENARIOS).forEach(([key, { label }]) => {
        scenarioRow.appendChild(el('button', { class: 'vp-btn', onclick: () => {
            if (running) { window.vehicleEngine.stopSimulation(); running = false; playBtn.textContent = '▶ Simular'; playBtn.classList.remove('active'); }
            window.vehicleEngine.loadScenario(key);
        }}, label));
    });

    return sec('Simulação',
        row('Auto-drive', el('div', { class: 'vp-sim-row' }, playBtn, multSel)),
        row('Cenários', scenarioRow),
    );
}

// ── Build full panel ──────────────────────────────────────────────────────────
function buildPanel(container) {
    const styles = document.createElement('style');
    styles.textContent = `
        #vehicle-panel { overflow-y:auto; padding:16px; background:#0f0f0f; display:flex; flex-direction:column; gap:0; }
        .vp-section { margin-bottom:18px; }
        .vp-title { font-size:10px; letter-spacing:2px; text-transform:uppercase; color:#6b7280;
            border-bottom:1px solid #1f2937; padding-bottom:5px; margin-bottom:10px; }
        .vp-row { display:flex; align-items:center; justify-content:space-between; margin-bottom:7px; gap:10px; }
        .vp-label { font-size:12px; color:#9ca3af; min-width:130px; }
        .vp-btn { font-size:11px; padding:4px 10px; border-radius:4px; border:1px solid #374151;
            background:#1f2937; color:#e5e7eb; cursor:pointer; white-space:nowrap; }
        .vp-btn:hover { background:#374151; }
        .vp-btn.active { background:#1d4ed8; border-color:#3b82f6; color:#fff; }
        .vp-btn-wide { flex:1; }
        .vp-select { font-size:12px; padding:4px 6px; border-radius:4px; border:1px solid #374151;
            background:#1f2937; color:#e5e7eb; cursor:pointer; }
        .vp-slider-wrap { display:flex; align-items:center; gap:7px; flex:1; }
        .vp-slider { flex:1; accent-color:#3b82f6; }
        .vp-val { font-size:12px; color:#60a5fa; min-width:38px; text-align:right; }
        .vp-sim-row { display:flex; gap:6px; align-items:center; flex:1; }
        .vp-scenario-row { display:flex; gap:5px; flex-wrap:wrap; }
        .vp-gear-row { display:flex; gap:6px; }
        .vp-gear-btn { font-size:13px; font-weight:600; padding:4px 14px; border-radius:4px;
            border:1px solid #374151; background:#1f2937; color:#e5e7eb; cursor:pointer; }
        .vp-gear-btn.selected { color:#fff; font-weight:700; }
        .vp-log-row { display:flex; gap:8px; font-size:11px; padding:2px 0; border-bottom:1px solid #111; font-family:monospace; }
        .vp-log-ts  { color:#6b7280; min-width:90px; flex-shrink:0; }
        .vp-log-msg { color:#a3e635; }
        #vehicle-panel::-webkit-scrollbar { width:5px; }
        #vehicle-panel::-webkit-scrollbar-thumb { background:#374151; border-radius:3px; }
    `;
    document.head.appendChild(styles);

    container.appendChild(buildSimSection());

    container.appendChild(sec('Veículo',
        row('Velocidade (km/h)', slider('CAR_BASIC_VEHICLE_SPEED', 0, 200, 1, 0)),
        row('Temp. interna (°C)', slider('CAR_BASIC_INSIDE_TEMP', -10, 60, 1, 24)),
        row('Temp. externa (°C)', slider('CAR_BASIC_OUTSIDE_TEMP', -20, 55, 1, 28)),
        row('Marcha', buildGearIndicator()),
        row('Janelas', toggle('CAR_BASIC_WINDOW_STATUS', 'Aberto', 'Fechado', 1, 0)),
        row('Teto solar', toggle('CAR_BASIC_SUNROOF_STATUS', 'Aberto', 'Fechado', 1, 0)),
        row('Espelhos', toggle('CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_FOLD_STATE', 'Abertos', 'Dobrados', 1, 0)),
        row('Ignição', toggle('CAR_BASIC_DRIVING_READY_STATE', 'Ligado', 'Desligado', 1, 0)),
        row('DMS State', toggle('CAR_DMS_WORK_STATE', 'ON', 'OFF', 1, 0)),
    ));

    container.appendChild(sec('A/C',
        row('Power', toggle('CAR_HVAC_POWER_MODE', 'ON', 'OFF')),
        row('Ventilador (0–7)', slider('CAR_HVAC_FAN_SPEED', 0, 7, 1, 1)),
        row('Temperatura (°C)', slider('CAR_HVAC_DRIVER_TEMPERATURE', 16, 32, 0.5, 22)),
        row('Auto', toggle('CAR_HVAC_AUTO_ENABLE', 'ON', 'OFF')),
        row('Recircular', toggle('CAR_HVAC_CYCLE_MODE', 'ON', 'OFF')),
        row('Ionizador', selectCtrl('CAR_HVAC_ANION_ENABLE', [['0','Desligado'],['1','Íon'],['2','Só ventoinha']])),
    ));

    container.appendChild(sec('Drive',
        row('ESP', toggle('CAR_DRIVE_SETTING_ESP_ENABLE', 'ON', 'OFF', 1, 0)),
        row('Modo direção', selectCtrl('CAR_DRIVE_SETTING_DRIVE_MODE', [['0','Normal'],['1','Sport'],['2','Eco'],['3','Neve'],['4','Areia'],['5','Lama'],['11','AWD']])),
        row('Direção', selectCtrl('CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE', [['2','Conforto'],['0','Normal'],['1','Esportiva']])),
        row('EV Mode', selectCtrl('CAR_EV_SETTING_POWER_MODEL_CONFIG', [['1','HEV'],['0','PHEV'],['3','EV']])),
        row('Regen', selectCtrl('CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL', [['2','Normal/Baixo'],['0','Normal/Médio'],['1','Alto']])),
        row('One-Pedal', toggle('CAR_CONFIGURE_PEDAL_CONTROL_ENABLE', 'ON', 'OFF', 1, 0)),
    ));

    container.appendChild(sec('EV / Consumo',
        row('Bateria (%)', slider('CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE', 0, 100, 1, 75)),
        row('Consumo EV', slider('SYS_EV_CONSUMPTION', -125, 115, 1, 0)),
        row('Consumo combustão', slider('SYS_GAS_CONSUMPTION', 0, 30, 0.1, 0)),
        row('Modo consumo', selectCtrl('SYS_GAS_CONSUMPTION_MODE', [['Running','Em movimento'],['Idle','Parado']])),
    ));

    logEl = el('div', { style: 'max-height:240px;overflow-y:auto;' });
    container.appendChild(sec('Log de automações', logEl));

    window.vehicleEngine.subscribe((k) => { if (k === '__log__') renderLog(); });
    renderLog();
}

window.addEventListener('load', () => {
    const panel = document.getElementById('vehicle-panel');
    if (!panel || !window.vehicleEngine) return;
    buildPanel(panel);
});
