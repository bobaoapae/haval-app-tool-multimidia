/* Development-only seam: explicit decoded input, never the temporal demo sampler.
 * No Android access, subscriptions, preferences, commands, or installed callbacks. */
(function (root, factory) {
  const api = factory(typeof module === 'object' ? require('./renderer') : root.MotionStudy);
  if (typeof module === 'object') module.exports = api;
  else root.StarlightContractRenderer = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function (renderer) {
  'use strict';
  function state(decoded) {
    const result = renderer.functionalState({
      speedRaw: decoded.speed, batteryPowerKw: decoded.powerKw, mode: 'contract-lab'
    });
    // Geometry saturates at the scale endpoint, but absence is not zero and the
    // numeric readout must never silently clamp a valid above-scale reading.
    result.speed = decoded.speed;
    result.speedRaw = decoded.speed;
    result.readouts = decoded;
    result.phase = 'DADOS DE TESTE DO CONTRATO';
    return result;
  }
  function render(ctx, decoded, options = {}) {
    const value = state(decoded);
    return renderer.renderPanel(ctx, options.time || 0, 'contract-lab', {
      state: value,
      starMotion: { speed: decoded.speed || 0, travel: options.travel || 0 }
    });
  }
  return { state, render };
}));
