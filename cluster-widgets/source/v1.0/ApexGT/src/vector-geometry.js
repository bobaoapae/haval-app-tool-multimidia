/* Concept 03 geometry measured on the 1920 × 720 raster. The two boomerang
 * bands are not mirrored: each contour follows its own brushed metal face. */
(() => {
  const rows = [
    [130, 256, 299, 1622, 1665], [150, 235, 280, 1643, 1687],
    [175, 211, 258, 1667, 1713], [200, 186, 236, 1691, 1739],
    [225, 161, 214, 1714, 1764], [245, 142, 197, 1732, 1784],
    [255, 137, 190, 1740, 1790], [270, 136, 187, 1746, 1792],
    [290, 143, 193, 1741, 1787], [310, 149, 199, 1735, 1780],
    [330, 155, 206, 1728, 1775], [350, 162, 213, 1720, 1768],
    [370, 169, 220, 1713, 1761], [390, 175, 227, 1704, 1754],
    [410, 182, 234, 1697, 1747], [430, 188, 241, 1689, 1740],
    [450, 195, 248, 1680, 1733], [470, 203, 256, 1671, 1724],
    [490, 213, 265, 1661, 1711]
  ];
  // Zero moves up 22.5 px from the reference (471.5 -> 449). Chromium measured
  // the full Khand glyph box down to baseline+15: baseline459 ends at y474,
  // strictly above native TSR at y475. Marker, label and host stay consistent.
  const speedMarks = [[0, 449], [40, 402], [80, 330], [120, 259.5], [160, 198], [200, 145]];
  // The reference intentionally omits 100 and is not a linear scale. These
  // explicit knots keep illumination and the visible labels consistent.
  const powerMarks = [[-100, 465], [-50, 408], [0, 351.5], [50, 293.5], [150, 242], [200, 190]];

  function at(points, value, column = 1) {
    if (value <= points[0][0]) return points[0][column];
    for (let index = 1; index < points.length; index++) {
      if (value > points[index][0]) continue;
      const a = points[index - 1];
      const b = points[index];
      return a[column] + (b[column] - a[column]) * (value - a[0]) / (b[0] - a[0]);
    }
    return points[points.length - 1][column];
  }

  window.ApexVectorGeometry = Object.freeze({
    leftBounds: y => ({ outer: at(rows, y, 1), inner: at(rows, y, 2) }),
    rightBounds: y => ({ inner: at(rows, y, 3), outer: at(rows, y, 4) }),
    speedY: value => at(speedMarks, value),
    powerY: value => at(powerMarks, value)
  });
})();
