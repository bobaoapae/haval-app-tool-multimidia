/**
 * `car.hvac.cycle_mode` reaches the theme as the car's raw value, and the car's convention
 * is the opposite of the one the name suggests:
 *
 *   0 -> recirculation (inner loop) ACTIVE
 *   1 -> fresh air (outer loop)
 *
 * The head unit's own AC panel already compensates for this — BottomBarUI.kt inverts the
 * value on the way in and back out again ("car uses opposite convention"). The theme read
 * it straight, so every recirculation readout was showing ON for OFF and OFF for ON.
 */
export function isRecirculating(rawCycleMode) {
    return Number(rawCycleMode) === 0;
}
