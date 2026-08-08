const PROJECTION_STYLE_ID = 'theme-lab-projection-simulator-style';
const PROJECTION_ROOT_CLASS = 'theme-lab-projection-simulator';

export const PROJECTION_MAP_URL = new URL(
  '../source/v1.0/default/src/assets/mapa.jpeg',
  import.meta.url,
).href;

export const PROJECTION_SIMULATOR_CSS = `
  html.${PROJECTION_ROOT_CLASS} #app.carplay-in-dash .dev-background,
  html.${PROJECTION_ROOT_CLASS} #app.projection-mirror-in-dash .dev-background,
  html.${PROJECTION_ROOT_CLASS} #app.native-mock-enabled.display-analogico-v2 .dev-background {
    opacity: 0 !important;
    visibility: hidden !important;
    background-image: none !important;
  }

  html.${PROJECTION_ROOT_CLASS} #app.carplay-in-dash .cluster-mask-bg,
  html.${PROJECTION_ROOT_CLASS} #app.projection-mirror-in-dash .cluster-mask-bg,
  html.${PROJECTION_ROOT_CLASS} #app.native-mock-enabled.display-analogico-v2 .cluster-mask-bg {
    position: absolute !important;
    inset: 0 !important;
    width: 1920px !important;
    height: 720px !important;
    border: 0 !important;
    background-color: #000000 !important;
    background-image: url("${PROJECTION_MAP_URL}") !important;
    background-position: center !important;
    background-repeat: no-repeat !important;
    background-size: cover !important;
  }

  html.${PROJECTION_ROOT_CLASS} #app.carplay-in-dash .cluster-mask-bg::before,
  html.${PROJECTION_ROOT_CLASS} #app.projection-mirror-in-dash .cluster-mask-bg::before,
  html.${PROJECTION_ROOT_CLASS} #app.native-mock-enabled.display-analogico-v2 .cluster-mask-bg::before {
    content: none !important;
    display: none !important;
  }

  html.${PROJECTION_ROOT_CLASS} #app.carplay-in-dash .cluster-mask-bg::after,
  html.${PROJECTION_ROOT_CLASS} #app.projection-mirror-in-dash .cluster-mask-bg::after,
  html.${PROJECTION_ROOT_CLASS} #app.native-mock-enabled.display-analogico-v2 .cluster-mask-bg::after {
    content: "" !important;
    display: block !important;
    position: absolute !important;
    inset: 0 !important;
    border: 0 !important;
    border-radius: 0 !important;
    clip-path: none !important;
    pointer-events: none !important;
    box-shadow: none !important;
    background: linear-gradient(
      90deg,
      rgba(0, 0, 0, 0.98) 0%,
      rgba(0, 0, 0, 0.94) 12%,
      rgba(0, 0, 0, 0.78) 22%,
      rgba(0, 0, 0, 0.46) 30%,
      rgba(0, 0, 0, 0.16) 36%,
      rgba(0, 0, 0, 0) 41%,
      rgba(0, 0, 0, 0) 59%,
      rgba(0, 0, 0, 0.16) 64%,
      rgba(0, 0, 0, 0.46) 70%,
      rgba(0, 0, 0, 0.78) 78%,
      rgba(0, 0, 0, 0.94) 88%,
      rgba(0, 0, 0, 0.98) 100%
    ) !important;
  }
`;

export function installProjectionSimulator(frameWindow) {
  const { document } = frameWindow;
  if (!document?.head || !document?.documentElement) {
    throw new Error('documento do tema ainda não está pronto');
  }

  document.getElementById(PROJECTION_STYLE_ID)?.remove();

  const style = document.createElement('style');
  style.id = PROJECTION_STYLE_ID;
  style.textContent = PROJECTION_SIMULATOR_CSS;
  document.head.appendChild(style);
  document.documentElement.classList.add(PROJECTION_ROOT_CLASS);

  return {
    cleanup() {
      style.remove();
      document.documentElement.classList.remove(PROJECTION_ROOT_CLASS);
    },
  };
}
