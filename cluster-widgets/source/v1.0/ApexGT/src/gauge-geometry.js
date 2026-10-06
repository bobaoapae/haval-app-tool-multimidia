/* Internal visual geometry shared by the scales and illuminated material.
 * Coordinates are measured in the theme's fixed 1920 × 720 reference plane. */
(() => {
  const scope = typeof window === 'undefined' ? globalThis : window;
  // The asset builder uses inverseY before clearing the fixed native cutouts.
  // Browser marks and material masks use the same remap without CSS scaling.
  // Preserve the upper instruments and extend only their lower half.
  const TOP = 300;
  const SOURCE_JOIN = 570;
  const DISPLAY_JOIN = 624;
  const BOTTOM = 720;

  function mapY(y) {
    if (y <= TOP || y >= BOTTOM) return y;
    if (y <= SOURCE_JOIN) {
      return TOP + (y - TOP) * (DISPLAY_JOIN - TOP) / (SOURCE_JOIN - TOP);
    }
    return DISPLAY_JOIN + (y - SOURCE_JOIN) * (BOTTOM - DISPLAY_JOIN) / (BOTTOM - SOURCE_JOIN);
  }

  function inverseY(y) {
    if (y <= TOP || y >= BOTTOM) return y;
    if (y <= DISPLAY_JOIN) {
      return TOP + (y - TOP) * (SOURCE_JOIN - TOP) / (DISPLAY_JOIN - TOP);
    }
    return SOURCE_JOIN + (y - DISPLAY_JOIN) * (BOTTOM - SOURCE_JOIN) / (BOTTOM - DISPLAY_JOIN);
  }

  const leftRows = [
    [110, 166, 210],
    [115, 162, 205],
    [132, 148, 193],
    [140, 142, 188],
    [180, 118, 166],
    [198, 109, 158],
    [220, 98, 150],
    [260, 84, 137],
    [275, 79, 134],
    [300, 73, 131],
    [330, 69, 128],
    [350, 68, 127],
    [356, 68, 128],
    [380, 72, 130],
    [417, 82, 137],
    [420, 83, 138],
    [435, 88, 142],
    [460, 98, 151],
    [500, 118, 167],
    [503, 119, 169],
    [512, 124, 173],
    [540, 142, 189],
    [565, 161, 204],
    [570, 166, 210],
  ];
  const rightRows = [
    [110, 1688],
    [115, 1692],
    [132, 1703],
    [140, 1709],
    [180, 1731],
    [198, 1740],
    [220, 1748],
    [260, 1760],
    [275, 1763],
    [300, 1767],
    [330, 1770],
    [350, 1769],
    [356, 1769],
    [380, 1766],
    [417, 1758],
    [420, 1757],
    [435, 1753],
    [460, 1744],
    [500, 1726],
    [503, 1724],
    [512, 1720],
    [540, 1703],
    [565, 1682],
    [570, 1678],
  ];
  // Urban speeds use most of the physical arc so 0–80 km/h remains visibly
  // animated. Each successive 20 km/h interval is shorter; 140–180 stays
  // compact at the top without changing the numeric speed reading.
  const speedMarks = [
    [0, 512], [20, 432.5], [40, 365.8333333333], [60, 312.5], [80, 263],
    [100, 221], [120, 189], [140, 165], [160, 147], [180, 132]
  ];
  // Traction and regeneration use the same progressive principle: 0–50 kW
  // receives most of each half-arc, while 50–100 remains a compact reserve.
  const powerMarks = [
    [-100, 503], [-75, 485], [-50, 462.5], [-40, 441.6666666667],
    [-30, 418.3333333333], [-20, 391.6666666667], [-10, 362.5], [0, 330],
    [10, 300], [20, 268], [30, 240], [40, 216], [50, 195], [75, 172], [100, 153]
  ];

  function interpolate(rows, value, column = 1) {
    if (value <= rows[0][0]) return rows[0][column];
    for (let index = 1; index < rows.length; index++) {
      if (value > rows[index][0]) continue;
      const before = rows[index - 1];
      const after = rows[index];
      const fraction = (value - before[0]) / (after[0] - before[0]);
      return before[column] + (after[column] - before[column]) * fraction;
    }
    return rows[rows.length - 1][column];
  }

  scope.ApexGaugeGeometry = Object.freeze({
    mapY,
    inverseY,
    leftBounds: y => ({ outer: interpolate(leftRows, inverseY(y)), inner: interpolate(leftRows, inverseY(y), 2) }),
    rightEdge: y => interpolate(rightRows, inverseY(y)),
    speedY: value => mapY(interpolate(speedMarks, value)),
    powerY: value => mapY(interpolate(powerMarks, value)),
    powerPivot: () => ({ x: 1631, y: mapY(309) })
  });
})();
