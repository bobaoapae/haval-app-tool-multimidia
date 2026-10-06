import assert from 'node:assert/strict';
import fs from 'node:fs';
import {
  SPORT_GAUGE_POLISH_CSS,
  SPORT_FLOATING_GLOW_ASSET_URL,
  SPORT_FLOATING_HEADER_GLOW_ASSET_URL,
  isSportGaugeTheme,
} from './sport-gauge-polish.js';

assert.equal(isSportGaugeTheme({ folder: 'Themes/SportRed' }), true);
assert.equal(isSportGaugeTheme({ folder: 'Themes/SportRedLite' }), true);
assert.equal(isSportGaugeTheme({ folder: 'Themes/Default' }), false);
assert.equal(isSportGaugeTheme({ folder: 'source/v1.0/default' }), false);
assert.match(SPORT_FLOATING_GLOW_ASSET_URL, /sport-floating-data-glow\.svg$/);
assert.match(SPORT_FLOATING_HEADER_GLOW_ASSET_URL, /sport-floating-header-glow\.svg$/);
const floatingGlowSvg = fs.readFileSync(new URL(SPORT_FLOATING_GLOW_ASSET_URL), 'utf8');
const floatingHeaderGlowSvg = fs.readFileSync(new URL(SPORT_FLOATING_HEADER_GLOW_ASSET_URL), 'utf8');
assert.match(floatingGlowSvg, /viewBox="0 0 1000 114"/);
assert.match(floatingGlowSvg, /id="white-left"/);
assert.match(floatingGlowSvg, /id="white-center"/);
assert.match(floatingGlowSvg, /id="white-right"/);
assert.doesNotMatch(floatingGlowSvg, /M60 73H81L101 84/);
assert.doesNotMatch(floatingGlowSvg, /M939 84L959 73H980/);
assert.match(floatingGlowSvg, /<ellipse cx="224"/);
assert.match(floatingGlowSvg, /<ellipse cx="500"/);
assert.match(floatingGlowSvg, /<ellipse cx="776"/);
assert.match(floatingGlowSvg, /M120 94H328/);
assert.match(floatingGlowSvg, /M350 99\.5H650/);
assert.match(floatingGlowSvg, /M672 94H880/);
assert.match(floatingHeaderGlowSvg, /viewBox="0 0 1000 114"/);
assert.match(floatingHeaderGlowSvg, /<ellipse cx="274"/);
assert.match(floatingHeaderGlowSvg, /<ellipse cx="500"/);
assert.match(floatingHeaderGlowSvg, /<ellipse cx="698"/);
assert.match(floatingHeaderGlowSvg, /M170 94H378/);
assert.match(floatingHeaderGlowSvg, /M350 99\.5H650/);
assert.match(floatingHeaderGlowSvg, /M594 94H802/);
assert.doesNotMatch(floatingHeaderGlowSvg, /<text\b/);
assert.doesNotMatch(floatingGlowSvg, /<text\b/);

assert.match(
  SPORT_GAUGE_POLISH_CSS,
  /\.g20-v2-speed-readout\s*\{[^}]*top: 380px !important/,
);
assert.match(
  SPORT_GAUGE_POLISH_CSS,
  /\.g20-v2-power-readout\s*\{[^}]*top: 380px !important/,
);
assert.match(SPORT_GAUGE_POLISH_CSS, /\.g20-v2-map-odometer/);
assert.match(SPORT_GAUGE_POLISH_CSS, /height: 58px !important/);
assert.match(SPORT_GAUGE_POLISH_CSS, /\.g20-v2-bottom-bar::before/);
assert.match(SPORT_GAUGE_POLISH_CSS, /\.g20-v2-bottom-bar::after/);
assert.match(
  SPORT_GAUGE_POLISH_CSS,
  /\.g20-v2-bottom-bar::before\s*\{[^}]*left: 460px !important;[^}]*bottom: -8px !important;[^}]*width: 1000px !important;[^}]*height: 114px !important;[^}]*background-image:/s,
);
assert.match(
  SPORT_GAUGE_POLISH_CSS,
  /\.dashboard-top-center\s*\{[^}]*width: 650px !important;[^}]*height: 70px !important;[^}]*background: transparent !important;/s,
);
assert.match(
  SPORT_GAUGE_POLISH_CSS,
  /\.dashboard-top-center::before\s*\{[^}]*width: 100% !important;[^}]*height: 74px !important;[^}]*background-image:[^}]*sport-floating-header-glow[^}]*transform: none !important;/s,
);
assert.match(
  SPORT_GAUGE_POLISH_CSS,
  /\.dashboard-top-center > \.dashboard-ev-mode\s*\{[^}]*text-shadow:[^}]*rgba\(0, 0, 0, 0\.98\)/s,
);
assert.doesNotMatch(SPORT_GAUGE_POLISH_CSS, /theme-lab\/sport/);
assert.doesNotMatch(SPORT_GAUGE_POLISH_CSS, /mask-image/);
assert.doesNotMatch(SPORT_GAUGE_POLISH_CSS, /linear-gradient/);
assert.doesNotMatch(SPORT_GAUGE_POLISH_CSS, /clip-path/);
assert.match(SPORT_GAUGE_POLISH_CSS, /content: none !important/);
assert.match(SPORT_GAUGE_POLISH_CSS, /font-variant-numeric: tabular-nums/);
assert.match(SPORT_GAUGE_POLISH_CSS, /\.revision-line/);
assert.doesNotMatch(SPORT_GAUGE_POLISH_CSS, /backdrop-filter/);
assert.doesNotMatch(SPORT_GAUGE_POLISH_CSS, /animation:/);
assert.doesNotMatch(SPORT_GAUGE_POLISH_CSS, /border-radius: 16px/);
assert.doesNotMatch(SPORT_GAUGE_POLISH_CSS, /border: 1px/);
assert.match(SPORT_GAUGE_POLISH_CSS, /stroke-dasharray: 1\.5 4\.5/);
assert.match(SPORT_GAUGE_POLISH_CSS, /stroke-dasharray: 3 8/);
assert.match(SPORT_GAUGE_POLISH_CSS, /stroke-dashoffset: 0/);

for (const preservedProperty of [
  'stroke-width',
  'stroke-linecap',
  'stroke-linejoin',
  'filter',
  'opacity',
]) {
  assert.doesNotMatch(
    SPORT_GAUGE_POLISH_CSS,
    new RegExp(`\\.g20-v2-scale-comb(?:\\.soft)?\\s*\\{[^}]*${preservedProperty}`, 's'),
    `${preservedProperty} deve continuar pertencendo ao visual original do tema`,
  );
}

for (const untouchedSelector of [
  'g20-v2-scale-rim',
  'g20-v2-speed-tick',
  'g20-v2-power-tick',
]) {
  assert.doesNotMatch(SPORT_GAUGE_POLISH_CSS, new RegExp(untouchedSelector));
}

console.log('Preview Sport válido: serrilhado preservado e dados flutuantes vetoriais estáticos.');
