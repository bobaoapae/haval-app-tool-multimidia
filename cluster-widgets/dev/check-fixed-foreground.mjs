import assert from 'node:assert/strict';
import {
  CLUSTER_CANVAS,
  FIXED_CLUSTER_FOREGROUND,
  SPORT_THEME_FIXED_MOCK_SELECTOR,
} from './fixed-cluster-foreground.js';

assert.deepEqual(Object.keys(FIXED_CLUSTER_FOREGROUND), ['ready', 'trafficSign', 'esp']);

for (const [name, geometry] of Object.entries(FIXED_CLUSTER_FOREGROUND)) {
  const visible = geometry.visibleBounds;
  const safe = geometry.safeZone;

  assert.ok(visible.x >= 0 && visible.y >= 0, `${name}: área visível começa fora do canvas`);
  assert.ok(
    visible.x + visible.width <= CLUSTER_CANVAS.width
      && visible.y + visible.height <= CLUSTER_CANVAS.height,
    `${name}: área visível termina fora do canvas`,
  );
  assert.ok(
    safe.x <= visible.x
      && safe.y <= visible.y
      && safe.x + safe.width >= visible.x + visible.width
      && safe.y + safe.height >= visible.y + visible.height,
    `${name}: zona reservada não contém todo o ícone`,
  );
}

assert.equal(
  SPORT_THEME_FIXED_MOCK_SELECTOR,
  '.g20-v2-native-status-mock',
  'o grupo duplicado READY/ESP/placa dos dois temas Sport deve permanecer identificado',
);

console.log('Camada frontal fixa válida: READY, placa e ESP dentro do canvas 1920x720.');
