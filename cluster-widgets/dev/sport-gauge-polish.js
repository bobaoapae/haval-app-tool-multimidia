const SPORT_GAUGE_STYLE_ID = 'theme-lab-sport-gauge-polish-style';
const SPORT_GAUGE_CLASS = 'theme-lab-sport-gauge-polished';
const SPORT_THEME_FOLDERS = new Set(['Themes/SportRed', 'Themes/SportRedLite']);

export const SPORT_FLOATING_GLOW_ASSET_URL = new URL(
  './assets/sport-floating-data-glow.svg',
  import.meta.url,
).href;

export const SPORT_FLOATING_HEADER_GLOW_ASSET_URL = new URL(
  './assets/sport-floating-header-glow.svg',
  import.meta.url,
).href;

// Simulator-only polish for the opaque legacy Sport bundles. Keep their bytes and
// gauge glow intact; normalize only the explicitly reviewed preview details below.
export const SPORT_GAUGE_POLISH_CSS = `
  html.${SPORT_GAUGE_CLASS} .g20-v2-speed-readout {
    top: 380px !important;
  }

  html.${SPORT_GAUGE_CLASS} .g20-v2-power-readout {
    top: 380px !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-bottom-bar {
    isolation: isolate !important;
    overflow: visible !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-bottom-bar::before {
    content: "" !important;
    display: block !important;
    position: absolute !important;
    left: 460px !important;
    bottom: -8px !important;
    width: 1000px !important;
    height: 114px !important;
    border: 0 !important;
    background-color: transparent !important;
    background-image: url("${SPORT_FLOATING_GLOW_ASSET_URL}") !important;
    background-position: center !important;
    background-repeat: no-repeat !important;
    background-size: 100% 100% !important;
    box-shadow: none !important;
    pointer-events: none !important;
    z-index: 0 !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-bottom-bar::after {
    content: none !important;
    display: none !important;
  }

  html.${SPORT_GAUGE_CLASS} .dashboard-top-center {
    box-sizing: border-box !important;
    left: 50% !important;
    width: 650px !important;
    height: 70px !important;
    padding: 0 !important;
    align-items: center !important;
    justify-content: center !important;
    gap: 72px !important;
    border: 0 !important;
    border-radius: 0 !important;
    background: transparent !important;
    box-shadow: none !important;
    isolation: isolate !important;
    transform: translateX(-50%) !important;
    overflow: visible !important;
  }

  html.${SPORT_GAUGE_CLASS} .dashboard-top-center::before {
    content: "" !important;
    display: block !important;
    position: absolute !important;
    top: -2px !important;
    right: auto !important;
    bottom: auto !important;
    left: 0 !important;
    width: 100% !important;
    height: 74px !important;
    border: 0 !important;
    border-radius: 0 !important;
    background-color: transparent !important;
    background-image: url("${SPORT_FLOATING_HEADER_GLOW_ASSET_URL}") !important;
    background-position: center !important;
    background-repeat: no-repeat !important;
    background-size: 100% 100% !important;
    box-shadow: none !important;
    pointer-events: none !important;
    transform: none !important;
    transform-origin: center !important;
    z-index: 0 !important;
  }

  html.${SPORT_GAUGE_CLASS} .dashboard-top-center::after {
    content: none !important;
    display: none !important;
  }

  html.${SPORT_GAUGE_CLASS} .dashboard-top-center > .dashboard-clock,
  html.${SPORT_GAUGE_CLASS} .dashboard-top-center > .dashboard-gear,
  html.${SPORT_GAUGE_CLASS} .dashboard-top-center > .dashboard-ev-mode {
    position: relative !important;
    z-index: 1 !important;
    text-shadow:
      0 2px 8px rgba(0, 0, 0, 0.98),
      0 0 18px rgba(0, 0, 0, 0.78) !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .external-temp-container,
  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .internal-temp-container {
    width: 120px !important;
    height: 58px !important;
    min-height: 58px !important;
    bottom: 6px !important;
    padding: 7px 10px 6px !important;
    border: 0 !important;
    border-radius: 0 !important;
    background: transparent !important;
    box-shadow: none !important;
    transform: none !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-map-odometer {
    box-sizing: border-box !important;
    flex: 0 0 320px !important;
    width: 320px !important;
    height: 58px !important;
    min-height: 58px !important;
    margin: 0 !important;
    border: 0 !important;
    border-radius: 0 !important;
    background: transparent !important;
    box-shadow: none !important;
    overflow: visible !important;
    transform: none !important;
    z-index: 1 !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .external-temp-container::before,
  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .internal-temp-container::before,
  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-map-odometer::before,
  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .external-temp-container::after,
  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .internal-temp-container::after,
  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-map-odometer::after {
    content: none !important;
    display: none !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .external-temp-container .temp-value,
  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .internal-temp-container .temp-value {
    color: rgba(249, 252, 255, 0.98) !important;
    font-size: 27px !important;
    font-weight: 600 !important;
    font-variant-numeric: tabular-nums !important;
    letter-spacing: 0 !important;
    line-height: 1 !important;
    text-shadow: 0 1px 4px rgba(0, 0, 0, 0.85) !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .external-temp-container .temp-sub-label,
  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .internal-temp-container .temp-sub-label {
    margin-top: 4px !important;
    color: rgba(235, 240, 248, 0.76) !important;
    font-size: 11px !important;
    font-weight: 600 !important;
    letter-spacing: 1.6px !important;
    line-height: 1 !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-map-odometer .odometer-text-wrapper,
  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-map-odometer .odometer-text-wrapper.single-line,
  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-map-odometer .odometer-text-wrapper.double-line {
    box-sizing: border-box !important;
    justify-content: center !important;
    gap: 2px !important;
    width: 100% !important;
    height: 100% !important;
    padding: 5px 18px 4px !important;
    transform: none !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-map-odometer .odometer-line {
    display: flex !important;
    align-items: baseline !important;
    justify-content: center !important;
    gap: 5px !important;
    margin: 0 !important;
    line-height: 1 !important;
    text-shadow: 0 1px 4px rgba(0, 0, 0, 0.85) !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-map-odometer .odometer-label {
    color: rgba(var(--perf-light-rgb), 0.90) !important;
    font-size: 12px !important;
    font-weight: 600 !important;
    letter-spacing: 1.1px !important;
    text-transform: uppercase !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-map-odometer .odometer-value {
    color: rgba(249, 252, 255, 0.98) !important;
    font-size: 27px !important;
    font-weight: 500 !important;
    font-variant-numeric: tabular-nums !important;
    letter-spacing: 1px !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-map-odometer .odometer-unit {
    color: rgba(235, 240, 248, 0.74) !important;
    font-size: 14px !important;
    font-weight: 400 !important;
    letter-spacing: 0.2px !important;
  }

  html.${SPORT_GAUGE_CLASS} #app.display-analogico-v2 .g20-v2-map-odometer .revision-line {
    position: static !important;
    top: auto !important;
    width: 100% !important;
    margin: 0 !important;
    color: rgba(235, 240, 248, 0.82) !important;
    font-size: 13px !important;
    font-weight: 400 !important;
    letter-spacing: 0.15px !important;
    line-height: 1.1 !important;
    text-align: center !important;
    text-shadow: 0 1px 4px rgba(0, 0, 0, 0.85) !important;
    transform: none !important;
  }

  html.${SPORT_GAUGE_CLASS} .g20-v2-scale-comb {
    stroke-dasharray: 1.5 4.5 !important;
    stroke-dashoffset: 0 !important;
  }

  html.${SPORT_GAUGE_CLASS} .g20-v2-scale-comb.soft {
    stroke-dasharray: 3 8 !important;
    stroke-dashoffset: 0 !important;
  }
`;

export function isSportGaugeTheme(theme) {
  return SPORT_THEME_FOLDERS.has(theme?.folder);
}

export function installSportGaugePolish(frameWindow, theme) {
  if (!isSportGaugeTheme(theme)) return null;

  const { document } = frameWindow;
  if (!document?.head || !document?.documentElement) {
    throw new Error('documento do tema Sport ainda não está pronto');
  }

  document.getElementById(SPORT_GAUGE_STYLE_ID)?.remove();
  document.documentElement.classList.add(SPORT_GAUGE_CLASS);

  const style = document.createElement('style');
  style.id = SPORT_GAUGE_STYLE_ID;
  style.textContent = SPORT_GAUGE_POLISH_CSS;
  document.head.appendChild(style);

  return {
    cleanup() {
      style.remove();
      document.documentElement.classList.remove(SPORT_GAUGE_CLASS);
    },
  };
}
