import { installLegacyKeyboardAdapter } from './legacy-keyboard.js';
import { installLegacyTelemetryHarness } from './legacy-telemetry.js';
import { installFixedClusterForeground } from './fixed-cluster-foreground.js';
import { installSportGaugePolish } from './sport-gauge-polish.js';
import { installProjectionSimulator } from './projection-simulator.js';

const CLUSTER_WIDTH = 1920;
const CLUSTER_HEIGHT = 720;
const STORAGE_KEY = 'haval-theme-lab:selected-theme';

const elements = {
  select: document.getElementById('theme-select'),
  toggle: document.getElementById('theme-toggle'),
  frame: document.getElementById('theme-frame'),
  viewport: document.getElementById('cluster-preview'),
  stage: document.getElementById('preview-stage'),
  folder: document.getElementById('active-folder'),
  version: document.getElementById('active-version'),
  kind: document.getElementById('active-kind'),
  keyboard: document.getElementById('active-keyboard'),
  status: document.getElementById('preview-status'),
  reload: document.getElementById('reload-theme'),
  open: document.getElementById('open-theme'),
  panelActions: document.getElementById('panel-actions'),
  panelButtons: [...document.querySelectorAll('[data-panel]')],
};

let themes = [];
let activeTheme = null;
let legacyAdapter = null;
let legacyTelemetryAdapter = null;
let fixedForegroundAdapter = null;
let sportGaugeAdapter = null;
let projectionSimulatorAdapter = null;
let harnessObserver = null;
let activeHarnessPanel = null;

const harnessPanelIds = [
  'testing-settings-harness',
  'testing-state-harness',
  'testing-shortcuts-harness',
  'testing-console-harness',
];

const harnessControlLabels = {
  'state-range-carSpeed': 'Velocidade em quilômetros por hora',
  'state-range-engineRPM': 'Rotação do motor em RPM',
  'state-range-evPowerKw': 'Potência elétrica em kW; valores negativos simulam regeneração',
  'state-range-evPowerFactor': 'Percentual de potência elétrica e regeneração',
  'state-range-fuelPercent': 'Nível de combustível em percentual',
  'state-range-batteryPercent': 'Nível da bateria em percentual',
  'state-range-inside_temp': 'Temperatura interna',
  'state-range-outside_temp': 'Temperatura externa',
  'state-range-fan': 'Velocidade do ventilador do ar-condicionado',
  'state-range-temp': 'Temperatura do ar-condicionado',
  'state-num-odometer': 'Odômetro',
  'state-txt-clockTime': 'Horário do cluster',
  'state-switch-enableOdometer': 'Exibir odômetro',
  'state-switch-enableRevisionWarning': 'Exibir aviso de revisão',
  'state-num-nextRevisionKm': 'Quilometragem da próxima revisão',
  'state-date-nextRevisionDate': 'Data da próxima revisão',
};

const kindLabels = {
  'source-v1': 'Fonte editável · contrato v1.0',
  'package-v1': 'Pacote OTA · contrato v1.0',
  'package-legacy': 'Pacote legado',
};

function resizePreview() {
  const availableWidth = Math.max(1, elements.viewport.clientWidth);
  const scale = Math.min(1, availableWidth / CLUSTER_WIDTH);
  elements.stage.style.width = `${CLUSTER_WIDTH * scale}px`;
  elements.stage.style.height = `${CLUSTER_HEIGHT * scale}px`;
  elements.frame.style.transform = `scale(${scale})`;
  elements.viewport.style.height = `${CLUSTER_HEIGHT * scale}px`;
}

function getThemeUrl(theme) {
  return new URL(theme.route, window.location.href).href;
}

function setStatus(message) {
  elements.status.textContent = message;
}

function formatLegacyStatus(state) {
  const screenLabels = {
    main_menu: 'Menu principal',
    display_selection: 'Modos do display',
    color_selection: 'Cores do tema',
    aircon: 'Climatização',
    regen: 'Regeneração',
    graph: 'Gráficos',
    now_playing: 'Tocando',
  };
  const parts = [screenLabels[state.screen] || state.screen, `card ${state.cardId}`];
  if (state.display) parts.push(state.display);
  if (state.colorTheme) parts.push(state.colorTheme);
  return parts.join(' · ');
}

function updateSelectionUi(theme) {
  elements.select.value = theme.id;
  elements.folder.textContent = theme.folder;
  elements.version.textContent = `Versão ${theme.version}`;
  elements.kind.textContent = kindLabels[theme.kind] || theme.kind;
  elements.keyboard.textContent = theme.keyboard === 'native'
    ? 'Teclado nativo do simulador'
    : (theme.keyboard === 'legacy-sport' ? 'Teclado adaptado para Sport' : 'Teclado adaptado para legado');
  elements.frame.title = `Prévia do tema ${theme.label}`;

  for (const button of elements.toggle.querySelectorAll('[role="radio"]')) {
    const isActive = button.dataset.themeId === theme.id;
    button.setAttribute('aria-checked', String(isActive));
    button.tabIndex = isActive ? 0 : -1;
  }
}

function focusFrame() {
  window.setTimeout(() => {
    elements.frame.focus();
    try {
      elements.frame.contentWindow.focus();
    } catch {
      // Same-origin is expected under the Theme Lab server. The frame still remains usable.
    }
  }, 0);
}

function loadTheme(theme) {
  if (!theme) return;
  legacyAdapter?.cleanup();
  legacyAdapter = null;
  legacyTelemetryAdapter?.cleanup();
  legacyTelemetryAdapter = null;
  fixedForegroundAdapter?.cleanup();
  fixedForegroundAdapter = null;
  sportGaugeAdapter?.cleanup();
  sportGaugeAdapter = null;
  projectionSimulatorAdapter?.cleanup();
  projectionSimulatorAdapter = null;
  harnessObserver?.disconnect();
  harnessObserver = null;
  activeHarnessPanel = null;
  activeTheme = theme;
  localStorage.setItem(STORAGE_KEY, theme.id);
  updateSelectionUi(theme);
  setStatus(`Carregando ${theme.label}…`);
  elements.frame.src = theme.route;
}

function syncHarnessPanels() {
  let childDocument;
  try {
    childDocument = elements.frame.contentWindow.document;
  } catch {
    return 0;
  }

  let foundPanels = 0;
  for (const [controlId, label] of Object.entries(harnessControlLabels)) {
    childDocument.getElementById(controlId)?.setAttribute('aria-label', label);
  }
  for (const panelId of harnessPanelIds) {
    const panel = childDocument.getElementById(panelId);
    if (!panel) continue;
    foundPanels += 1;
    panel.hidden = panelId !== activeHarnessPanel;
  }

  for (const button of elements.panelButtons) {
    const panelExists = Boolean(childDocument.getElementById(button.dataset.panel));
    button.disabled = !panelExists;
    button.setAttribute('aria-pressed', String(button.dataset.panel === activeHarnessPanel && panelExists));
  }
  return foundPanels;
}

function prepareThemeDocument() {
  const childDocument = elements.frame.contentWindow.document;
  let resetStyle = childDocument.getElementById('theme-lab-document-reset');
  if (!resetStyle) {
    resetStyle = childDocument.createElement('style');
    resetStyle.id = 'theme-lab-document-reset';
    childDocument.head.appendChild(resetStyle);
  }

  resetStyle.textContent = `
    html,
    body {
      width: 100% !important;
      height: 100% !important;
      min-width: 0 !important;
      min-height: 0 !important;
      margin: 0 !important;
      overflow: hidden !important;
      scrollbar-width: none !important;
    }

    html::-webkit-scrollbar,
    body::-webkit-scrollbar {
      display: none !important;
    }
  `;
}

function prepareHarnessPanels() {
  harnessObserver?.disconnect();
  harnessObserver = null;
  activeHarnessPanel = null;
  elements.panelActions.hidden = false;

  const childDocument = elements.frame.contentWindow.document;
  let hiddenStyle = childDocument.getElementById('theme-lab-panel-visibility');
  if (!hiddenStyle) {
    hiddenStyle = childDocument.createElement('style');
    hiddenStyle.id = 'theme-lab-panel-visibility';
    childDocument.head.appendChild(hiddenStyle);
  }
  hiddenStyle.textContent = '[id^="testing-"][hidden] { display: none !important; }';
  syncHarnessPanels();

  harnessObserver = new MutationObserver(() => syncHarnessPanels());
  harnessObserver.observe(childDocument.body, { childList: true, subtree: true });
}

function onThemeFrameLoaded() {
  if (!activeTheme) return;
  legacyAdapter?.cleanup();
  legacyAdapter = null;
  legacyTelemetryAdapter?.cleanup();
  legacyTelemetryAdapter = null;
  fixedForegroundAdapter?.cleanup();
  fixedForegroundAdapter = null;
  sportGaugeAdapter?.cleanup();
  sportGaugeAdapter = null;
  projectionSimulatorAdapter?.cleanup();
  projectionSimulatorAdapter = null;

  try {
    prepareThemeDocument();
    projectionSimulatorAdapter = installProjectionSimulator(elements.frame.contentWindow);
    fixedForegroundAdapter = installFixedClusterForeground(elements.frame.contentWindow);
    sportGaugeAdapter = installSportGaugePolish(elements.frame.contentWindow, activeTheme);
    prepareHarnessPanels();
    legacyAdapter = installLegacyKeyboardAdapter(
      elements.frame.contentWindow,
      activeTheme,
      (state) => setStatus(formatLegacyStatus(state)),
    );
    legacyTelemetryAdapter = installLegacyTelemetryHarness(
      elements.frame.contentWindow,
      activeTheme,
      {
        onReady: () => {
          syncHarnessPanels();
          setStatus(`${activeTheme.label} carregado · telemetria automática e camada nativa fixa ativas`);
        },
        onError: (message) => {
          console.error('[Theme Lab] Simulador de telemetria indisponível:', message);
          setStatus(`${activeTheme.label} carregado · telemetria automática indisponível`);
        },
      },
    );
  } catch (error) {
    console.error('[Theme Lab] Não foi possível instalar o adaptador legado:', error);
    setStatus(`${activeTheme.label} carregado · adaptador legado indisponível`);
  }

  if (activeTheme.keyboard === 'native') {
    setStatus(`${activeTheme.label} carregado · telemetria automática e camada nativa fixa ativas`);
  }
  focusFrame();
}

function renderThemeOptions() {
  elements.select.innerHTML = '';
  elements.toggle.innerHTML = '';

  for (const theme of themes) {
    const option = document.createElement('option');
    option.value = theme.id;
    option.textContent = `${theme.label} · ${theme.folder}`;
    elements.select.appendChild(option);

    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'theme-option';
    button.dataset.themeId = theme.id;
    button.setAttribute('role', 'radio');
    button.setAttribute('aria-checked', 'false');
    button.tabIndex = -1;

    const label = document.createElement('span');
    label.textContent = theme.label;
    const folder = document.createElement('small');
    folder.textContent = theme.editable ? 'Fonte editável' : theme.folder;
    button.append(label, folder);

    button.addEventListener('click', () => loadTheme(theme));
    button.addEventListener('keydown', (event) => {
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
      event.preventDefault();
      const index = themes.findIndex((item) => item.id === theme.id);
      const direction = event.key === 'ArrowRight' ? 1 : -1;
      const nextTheme = themes[(index + direction + themes.length) % themes.length];
      loadTheme(nextTheme);
      const nextButton = elements.toggle.querySelector(`[data-theme-id="${CSS.escape(nextTheme.id)}"]`);
      nextButton?.focus();
    });
    elements.toggle.appendChild(button);
  }

  elements.select.disabled = false;
}

function forwardKeyToFrame(event) {
  const interactive = event.target instanceof Element
    ? event.target.closest('button, select, input, textarea, summary, a')
    : null;
  if (interactive || !activeTheme) return;
  try {
    const forwardedEvent = new KeyboardEvent('keydown', {
      key: event.key,
      code: event.code,
      bubbles: true,
      cancelable: true,
      ctrlKey: event.ctrlKey,
      altKey: event.altKey,
      metaKey: event.metaKey,
      shiftKey: event.shiftKey,
    });
    elements.frame.contentWindow.document.dispatchEvent(forwardedEvent);
    if (forwardedEvent.defaultPrevented) event.preventDefault();
  } catch {
    // The selected page is expected to be same-origin. Ignore transient load boundaries.
  }
}

async function initialize() {
  try {
    const response = await fetch('/__theme_catalog', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const payload = await response.json();
    themes = payload.themes || [];
    if (themes.length === 0) throw new Error('nenhuma pasta de tema válida foi encontrada');

    renderThemeOptions();
    const savedThemeId = localStorage.getItem(STORAGE_KEY);
    const initialTheme = themes.find((theme) => theme.id === savedThemeId)
      || themes.find((theme) => theme.folder === 'source/v1.0/default')
      || themes[0];
    loadTheme(initialTheme);
  } catch (error) {
    console.error('[Theme Lab] Falha ao carregar catálogo:', error);
    setStatus(`Erro ao carregar temas: ${error.message}`);
  }
}

elements.select.addEventListener('change', () => {
  loadTheme(themes.find((theme) => theme.id === elements.select.value));
});
elements.reload.addEventListener('click', () => {
  if (!activeTheme) return;
  setStatus(`Recarregando ${activeTheme.label}…`);
  elements.frame.contentWindow.location.reload();
});
elements.open.addEventListener('click', () => {
  if (activeTheme) window.open(getThemeUrl(activeTheme), '_blank', 'noopener');
});
for (const button of elements.panelButtons) {
  button.addEventListener('click', () => {
    activeHarnessPanel = activeHarnessPanel === button.dataset.panel ? null : button.dataset.panel;
    syncHarnessPanels();
    focusFrame();
  });
}
elements.frame.addEventListener('load', onThemeFrameLoaded);
document.addEventListener('keydown', forwardKeyToFrame);

let resizeFrame = null;
window.addEventListener('resize', () => {
  if (resizeFrame !== null) cancelAnimationFrame(resizeFrame);
  resizeFrame = requestAnimationFrame(() => {
    resizeFrame = null;
    resizePreview();
  });
});
resizePreview();
initialize();
