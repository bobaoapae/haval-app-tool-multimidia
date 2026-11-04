# Handoff — HUD roundabout missing on live drive (2026-10-22)

## What happened

Live drive on Av. Pref. Silvio Picanço: user saw no dynamic roundabout icon, exit countdown, or exit chevrons. Logs still showed `hud_rb_entered` at 19:57:40 (`exit=6`, `angle=315`, `start_m=157`, `demo=false`).

## Root cause

At entry AA switched `ROUNDABOUT` → `ROUNDABOUT_EXIT` with `turn_angle=0` / `exit_number=null`. That wiped the approach pair the HUD needs for `dynamicRoundabout` / chevrons, and changed tracker identity to `rb|id|46`, clearing `RoundaboutTracker` right after enter.

## Fix (local, not deployed)

- `AndroidAutoNavigationTelemetry.Accumulator.setRoundaboutExit` — hold last exit/angle while still on a roundabout maneuver
- `HudOverlayController.roundaboutProgress` — once entered, progress from AA distance even if angle briefly missing
- Unit test: `roundaboutExitKeepsApproachPairWhenExitStepZerosAngle`

## Validation

- `./gradlew :app:testDebugUnitTest` — AndroidAutoNavigationTelemetryTest + Hud* — BUILD SUCCESSFUL
- Not yet installed on car

## Next

Deploy + re-drive the same roundabout; confirm `directions` keep `exit_number`/`turn_angle` through `ROUNDABOUT_EXIT`, and HUD shows ring + countdown + chevrons on the lap.
