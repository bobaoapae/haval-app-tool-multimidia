/* Read-only fixture runner. Never installs the host's global callback names. */
(function () {
  'use strict';
  if ('Android' in window) return;
  document.documentElement.classList.add('starlight-browser-lab');
  const keys = ApexData.KEYS;
  const canvas = document.getElementById('panel');
  const output = document.getElementById('contract-reading');
  const select = document.getElementById('contract-scenario');
  const button = document.getElementById('contract-apply');
  const ctx = canvas.getContext('2d');
  let disposed = false, last = null;
  const fixtureBridge = {
    __themeLabMock: StarlightContractAdapter.MOCK_SENTINEL,
    getAvailableKeys: () => JSON.stringify(Object.values(keys)),
    subscribe: () => {}, unsubscribe: () => {}, getCarData: () => null
  };
  function draw(value) {
    if (disposed || 'Android' in window) return;
    last = value;
    StarlightContractRenderer.render(ctx, value.decoded);
    output.textContent = JSON.stringify({
      source: 'fixture local', rawCanSpeed: value.decoded.speed,
      batteryPowerKw: value.decoded.powerKw, fuelPct: value.decoded.fuelPct,
      batteryPct: value.decoded.batteryPct, hardwareReady: false
    }, null, 2);
  }
  const adapter = StarlightContractAdapter.create({
    labMode: true, window, bridge: fixtureBridge, onSnapshot: draw,
    decodeOptions: { clockText: '12:34', tankCapacityL: 55 }
  });
  function apply() {
    if (disposed) return;
    const selected = select.value;
    const data = selected === 'missing' ? {} : {
      speed: selected === 'zero' ? 0 : selected === 'above-scale' ? 231 : 83,
      voltage: 400, current: selected === 'zero' ? 0 : selected === 'regen' ? -100 : 100,
      fuelPct: 0, batteryPct: 74, gear: 2, rpm: 0, tripA: 56.7,
      insideTemp: 22, outsideTemp: 24, odometer: 18654, consumption: 0,
      fuelRange: 0, electricRange: 84, driveMode: 0, propulsionMode: 3
    };
    if (selected === 'missing-current') data.current = null;
    Object.keys(keys).forEach(field => adapter.hooks.onDataChanged(keys[field], data[field] ?? null));
  }
  function cleanup() {
    if (disposed) return;
    disposed = true;
    button.removeEventListener('click', apply);
    window.removeEventListener('pagehide', cleanup);
    adapter.cleanup();
  }
  button.addEventListener('click', apply);
  window.addEventListener('pagehide', cleanup);
  window.addEventListener('pageshow', function (event) { if (event.persisted) window.location.reload(); });
  window.starlightContractLab = Object.freeze({
    apply, cleanup, adapter, get last() { return last; }, get disposed() { return disposed; }
  });
  Promise.all(['Scale Digits', 'Speed Digits', 'Panel Sans', 'Label Sans', 'Header Sans']
    .map(font => document.fonts.load('16px "' + font + '"'))).then(() => {
      if (disposed || 'Android' in window) return;
      adapter.start();
      apply();
    }).catch(() => { if (!disposed) output.textContent = 'Não foi possível carregar as fontes locais'; });
}());
