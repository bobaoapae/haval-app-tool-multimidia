// GERADO por scripts/shared-runtime.mjs a partir de cluster-widgets/source/v1.0/shared — não edite à mão.
// Cartão de turn-by-turn e derivações do carro compartilhados com Default e Minimalist; `npm run build` regenera.
(function () {
"use strict";
// ---- source/v1.0/shared/car/carDerivations.js (getAdjustedSpeed) ----
function getAdjustedSpeed(rawSpeed, enableAdjustment = false, offsetPercent = 0.0) {
    const speed = parseFloat(rawSpeed) || 0.0;

    // Formula to match the physical instrument cluster
    const adjustedSpeed = speed * 1.07 - (speed / 180.0) * 0.02;

    const finalSpeed = enableAdjustment
        ? adjustedSpeed * (1.0 + (parseFloat(offsetPercent) / 100.0))
        : adjustedSpeed;

    return String(Math.floor(finalSpeed));
}
window.ApexShared = { getAdjustedSpeed: getAdjustedSpeed };
})();
