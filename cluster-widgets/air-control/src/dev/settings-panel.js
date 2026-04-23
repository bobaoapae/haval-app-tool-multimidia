// Painel esquerdo — réplica do app Android (MainActivity)
// 3 abas: Configurações | Cluster | Diagnóstico

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

function row(lbl, ctrl, sub = null) {
    const r = el('div', { class: 'sp-row' },
        el('label', { class: 'sp-label' }, lbl),
        ctrl,
    );
    if (sub) {
        const wrap = el('div', { class: 'sp-sub-wrap' }, r, sub);
        return wrap;
    }
    return r;
}

function toggle(key, initialVal = false, onChange) {
    const btn = el('button', { class: 'sp-toggle', onclick: () => {
        const on = btn.dataset.on === 'true';
        btn.dataset.on = !on;
        btn.textContent = !on ? 'ON' : 'OFF';
        btn.classList.toggle('active', !on);
        onChange(!on);
    }});
    btn.dataset.on = initialVal;
    btn.textContent = initialVal ? 'ON' : 'OFF';
    btn.classList.toggle('active', initialVal);
    return btn;
}

function slider(label, min, max, step, initial, onChange) {
    const val = el('span', { class: 'sp-slider-val' }, String(initial));
    const inp = el('input', { type: 'range', class: 'sp-slider', min, max, step, value: initial,
        oninput: () => { val.textContent = inp.value; onChange(parseFloat(inp.value)); }
    });
    return el('div', { class: 'sp-slider-row' },
        el('span', { class: 'sp-sub-label' }, label),
        el('div', { class: 'sp-slider-wrap' }, inp, val),
    );
}

function settingToggle(key, label, sub = null) {
    const eng = window.vehicleEngine;
    const initial = eng.settings[key];
    const subWrap = sub ? el('div', { class: 'sp-sub', style: initial ? '' : 'display:none' }, sub) : null;
    const btn = toggle(key, initial, (on) => {
        eng.setSetting(key, on);
        if (subWrap) subWrap.style.display = on ? '' : 'none';
    });
    const r = row(label, btn);
    if (subWrap) return el('div', {}, r, subWrap);
    return r;
}

function settingSlider(key, label, min, max, step) {
    const eng = window.vehicleEngine;
    return slider(label, min, max, step, eng.settings[key], (v) => eng.setSetting(key, v));
}

// ── Tab 1: Configurações ──────────────────────────────────────────────────────
function buildConfigTab() {
    const wrap = el('div', { class: 'sp-tab-content' });

    // Seção janelas
    wrap.appendChild(el('div', { class: 'sp-group-title' }, 'Janelas & Teto Solar'));
    wrap.appendChild(settingToggle('CLOSE_WINDOW_ON_POWER_OFF',   'Fechar janelas ao desligar'));
    wrap.appendChild(settingToggle('CLOSE_WINDOW_ON_FOLD_MIRROR', 'Fechar janelas ao dobrar espelhos'));
    wrap.appendChild(settingToggle('CLOSE_SUNROOF_ON_POWER_OFF',  'Fechar teto ao desligar'));
    wrap.appendChild(settingToggle('CLOSE_SUNROOF_ON_FOLD_MIRROR','Fechar teto ao dobrar espelhos'));
    wrap.appendChild(settingToggle('CLOSE_WINDOWS_ON_SPEED', 'Fechar janelas por velocidade',
        settingSlider('WINDOWS_SPEED_THRESHOLD', 'Velocidade (km/h)', 10, 120, 5)));
    wrap.appendChild(settingToggle('CLOSE_SUNROOF_ON_SPEED', 'Fechar teto por velocidade',
        settingSlider('SUNROOF_SPEED_THRESHOLD', 'Velocidade (km/h)', 10, 120, 5)));

    // Seção A/C
    wrap.appendChild(el('div', { class: 'sp-group-title' }, 'Clima'));
    wrap.appendChild(settingToggle('ENABLE_MAX_AC_ON_UNLOCK', 'Max A/C ao desbloquear',
        el('div', {},
            settingSlider('MAX_AC_ON_UNLOCK_THRESHOLD', 'Temp. mínima (°C)', 20, 45, 1),
            settingSlider('MAX_AC_TARGET_TEMP',         'Temp. alvo (°C)',    18, 35, 1),
        )
    ));
    wrap.appendChild(settingToggle('ENABLE_SEAT_VENTILATION_ON_AC_ON', 'Ventilação bancos com A/C'));

    // Seção veículo
    wrap.appendChild(el('div', { class: 'sp-group-title' }, 'Veículo'));
    wrap.appendChild(settingToggle('DISABLE_BLUETOOTH_ON_POWER_OFF', 'Desligar BT ao desligar carro'));
    wrap.appendChild(settingToggle('DISABLE_HOTSPOT_ON_POWER_OFF',   'Desligar Hotspot ao desligar carro'));

    return wrap;
}

// ── Tab 2: Cluster ────────────────────────────────────────────────────────────
function buildClusterTab() {
    const wrap = el('div', { class: 'sp-tab-content' });

    // Telas
    wrap.appendChild(el('div', { class: 'sp-group-title' }, 'Telas'));
    const screenRow = el('div', { class: 'sp-btn-row' });
    [['main_menu','Menu'],['aircon','A/C'],['regen','Regen'],['graph','Gráficos']].forEach(([s, l]) => {
        screenRow.appendChild(el('button', { class: 'sp-action-btn', onclick: () => {
            if (window.showScreen) window.showScreen(s);
        }}, l));
    });
    wrap.appendChild(screenRow);

    // Volante (botões físicos simulados)
    wrap.appendChild(el('div', { class: 'sp-group-title' }, 'Botões do Volante'));

    const dpad = el('div', { class: 'sp-dpad' });

    function steerBtn(label, key) {
        return el('button', { class: 'sp-dpad-btn', onmousedown: () => {
            document.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
        }, onmouseup: () => {
            document.dispatchEvent(new KeyboardEvent('keyup', { key, bubbles: true }));
        }}, label);
    }

    dpad.appendChild(steerBtn('↑', 'ArrowUp'));
    dpad.appendChild(el('div', { class: 'sp-dpad-mid' },
        steerBtn('←', 'ArrowLeft'),
        steerBtn('OK', 'Enter'),
        steerBtn('→', 'ArrowRight'),
    ));
    dpad.appendChild(steerBtn('↓', 'ArrowDown'));
    dpad.appendChild(steerBtn('← Voltar', 'Backspace'));

    wrap.appendChild(dpad);

    // Foco menu
    wrap.appendChild(el('div', { class: 'sp-group-title' }, 'Foco no Menu'));
    const focusRow = el('div', { class: 'sp-btn-row' });
    [['option_1','ESP'],['option_2','EV'],['option_3','Modo'],['option_4','A/C'],['option_5','Direção'],['option_6','Regen'],['option_7','Gráficos']].forEach(([id, lbl]) => {
        focusRow.appendChild(el('button', { class: 'sp-action-btn sp-action-btn--sm', onclick: () => {
            if (window.focus) window.focus(id);
        }}, lbl));
    });
    wrap.appendChild(focusRow);

    return wrap;
}

// ── Tab 3: Diagnóstico ────────────────────────────────────────────────────────
const DIAG_KEYS = [
    'CAR_BASIC_VEHICLE_SPEED', 'CAR_BASIC_INSIDE_TEMP', 'CAR_BASIC_OUTSIDE_TEMP',
    'CAR_BASIC_DRIVING_READY_STATE', 'CAR_BASIC_GEAR_STATUS',
    'CAR_BASIC_WINDOW_STATUS', 'CAR_BASIC_SUNROOF_STATUS',
    'CAR_DMS_WORK_STATE', 'CAR_DRIVE_SETTING_OUTSIDE_VIEW_MIRROR_FOLD_STATE',
    'CAR_HVAC_POWER_MODE', 'CAR_HVAC_FAN_SPEED', 'CAR_HVAC_DRIVER_TEMPERATURE',
    'CAR_HVAC_AUTO_ENABLE', 'CAR_HVAC_CYCLE_MODE', 'CAR_HVAC_ANION_ENABLE',
    'CAR_EV_SETTING_POWER_MODEL_CONFIG', 'CAR_EV_SETTING_ENERGY_RECOVERY_LEVEL',
    'CAR_CONFIGURE_PEDAL_CONTROL_ENABLE', 'CAR_DRIVE_SETTING_ESP_ENABLE',
    'CAR_DRIVE_SETTING_DRIVE_MODE', 'CAR_DRIVE_SETTING_STEERING_WHEEL_ASSIST_MODE',
    'CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE', 'CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL',
    'SYS_EV_CONSUMPTION', 'SYS_GAS_CONSUMPTION', 'SYS_LAST_REGEN_VALUE',
];

function buildDiagTab() {
    const wrap = el('div', { class: 'sp-tab-content' });
    const cells = {};

    const table = el('div', { class: 'sp-diag-table' });
    DIAG_KEYS.forEach(k => {
        const valCell = el('span', { class: 'sp-diag-val' }, String(window.vehicleEngine.state[k]));
        cells[k] = valCell;
        const short = k.replace('CAR_', '').replace('SYS_', '').replace(/_/g, ' ').toLowerCase();
        table.appendChild(el('div', { class: 'sp-diag-row' },
            el('span', { class: 'sp-diag-key', title: k }, short),
            valCell,
        ));
    });

    window.vehicleEngine.subscribe((k, v) => {
        if (cells[k]) {
            cells[k].textContent = String(v);
            cells[k].classList.add('sp-diag-flash');
            setTimeout(() => cells[k].classList.remove('sp-diag-flash'), 400);
        }
    });

    wrap.appendChild(table);
    return wrap;
}

// ── Build full panel ──────────────────────────────────────────────────────────
function buildPanel(container) {
    const styles = document.createElement('style');
    styles.textContent = `
        #settings-panel { display:flex; flex-direction:column; background:#111; border-right:1px solid #1f2937; }
        .sp-tabs { display:flex; background:#0a0a0a; border-bottom:1px solid #1f2937; flex-shrink:0; }
        .sp-tab-btn { flex:1; font-size:11px; padding:10px 4px; border:none; background:transparent;
            color:#6b7280; cursor:pointer; letter-spacing:0.5px; border-bottom:2px solid transparent; }
        .sp-tab-btn.active { color:#60a5fa; border-bottom-color:#3b82f6; background:#111; }
        .sp-tab-btn:hover:not(.active) { color:#9ca3af; }
        .sp-tab-content { display:none; flex:1; overflow-y:auto; padding:14px; }
        .sp-tab-content.visible { display:block; }
        .sp-group-title { font-size:10px; letter-spacing:2px; text-transform:uppercase; color:#6b7280;
            border-bottom:1px solid #1f2937; padding-bottom:5px; margin:14px 0 8px; }
        .sp-group-title:first-child { margin-top:0; }
        .sp-row { display:flex; align-items:center; justify-content:space-between; margin-bottom:7px; gap:8px; }
        .sp-label { font-size:12px; color:#d1d5db; flex:1; }
        .sp-toggle { font-size:11px; padding:3px 10px; border-radius:4px; border:1px solid #374151;
            background:#1f2937; color:#e5e7eb; cursor:pointer; min-width:46px; }
        .sp-toggle.active { background:#1d4ed8; border-color:#3b82f6; color:#fff; }
        .sp-sub { padding-left:10px; border-left:2px solid #1f2937; margin:4px 0 8px; }
        .sp-sub-label { font-size:11px; color:#9ca3af; min-width:120px; }
        .sp-slider-row { display:flex; align-items:center; gap:8px; margin-bottom:5px; }
        .sp-slider-wrap { display:flex; align-items:center; gap:6px; flex:1; }
        .sp-slider { flex:1; accent-color:#3b82f6; }
        .sp-slider-val { font-size:11px; color:#60a5fa; min-width:32px; text-align:right; }
        .sp-btn-row { display:flex; gap:5px; flex-wrap:wrap; margin-bottom:8px; }
        .sp-action-btn { font-size:11px; padding:5px 10px; border-radius:4px; border:1px solid #374151;
            background:#1f2937; color:#e5e7eb; cursor:pointer; }
        .sp-action-btn:hover { background:#374151; }
        .sp-action-btn--sm { padding:3px 7px; }
        .sp-dpad { display:flex; flex-direction:column; align-items:center; gap:4px; margin-bottom:10px; }
        .sp-dpad-mid { display:flex; gap:4px; align-items:center; }
        .sp-dpad-btn { font-size:12px; padding:6px 14px; border-radius:4px; border:1px solid #374151;
            background:#1f2937; color:#e5e7eb; cursor:pointer; min-width:56px; user-select:none; }
        .sp-dpad-btn:active { background:#374151; }
        .sp-diag-table { font-family:monospace; }
        .sp-diag-row { display:flex; justify-content:space-between; padding:3px 0;
            border-bottom:1px solid #0d0d0d; gap:8px; }
        .sp-diag-key { font-size:11px; color:#6b7280; flex:1; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
        .sp-diag-val { font-size:11px; color:#34d399; min-width:60px; text-align:right; flex-shrink:0; }
        @keyframes sp-flash { 0%{color:#fde68a} 100%{color:#34d399} }
        .sp-diag-flash { animation:sp-flash 0.4s ease-out; }
        #settings-panel::-webkit-scrollbar { width:5px; }
        #settings-panel::-webkit-scrollbar-thumb { background:#374151; border-radius:3px; }
        .sp-tab-content::-webkit-scrollbar { width:5px; }
        .sp-tab-content::-webkit-scrollbar-thumb { background:#374151; border-radius:3px; }
    `;
    document.head.appendChild(styles);

    const tabs = [
        { id: 'config',  label: 'Config',      build: buildConfigTab  },
        { id: 'cluster', label: 'Cluster',      build: buildClusterTab },
        { id: 'diag',    label: 'Diagnóstico',  build: buildDiagTab    },
    ];

    const tabBar = el('div', { class: 'sp-tabs' });
    const contents = {};

    tabs.forEach(({ id, label, build }, i) => {
        const content = build();
        content.id = 'sp-tab-' + id;
        if (i === 0) content.classList.add('visible');
        contents[id] = content;

        const btn = el('button', { class: 'sp-tab-btn' + (i === 0 ? ' active' : ''), onclick: () => {
            tabBar.querySelectorAll('.sp-tab-btn').forEach(b => b.classList.remove('active'));
            Object.values(contents).forEach(c => c.classList.remove('visible'));
            btn.classList.add('active');
            content.classList.add('visible');
        }}, label);
        tabBar.appendChild(btn);
        container.appendChild(content);
    });

    container.insertBefore(tabBar, container.firstChild);
}

window.addEventListener('load', () => {
    const panel = document.getElementById('settings-panel');
    if (!panel || !window.vehicleEngine) return;
    buildPanel(panel);
});
