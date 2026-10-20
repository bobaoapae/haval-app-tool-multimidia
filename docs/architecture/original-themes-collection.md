# Original instrument themes (HAV-28)

Five independent compositions, built on `feature/new-screen-enhancements-v9` at
`52c991a72c2cd516468b490f689486ed2ef344e1`:

| Theme | Visual identity |
| --- | --- |
| Horizonte | Travel-oriented horizontal energy and range hierarchy |
| Pulso | Angular speed and signed electrical-power instrumentation |
| Órbita (`Orbita` folder) | Circular gauges and satellite energy information |
| Sereno | Light, minimal navigation-first composition |
| Eclipse | Warm, low-luminance essential instrumentation |

These compositions, SVG marks and catalog icons are original. No third-party
commercial dashboard artwork, code, fonts or theme templates were used. Existing
Impulse code is reused only for the canonical speed-calibration contract. The
catalog icons are **vector artwork, not screenshots or evidence of vehicle validation**.

## Scope and safety boundaries

- Additive `v1.0` packages in `cluster-widgets/source/v1.0/<Name>` and
  `cluster-widgets/Themes/v1.0/<Name>`. Each package is self-contained, about 32–34 KB.
- No Kotlin/Java, bridge methods, telemetry dictionary, vehicle commands, existing
  themes, APK resources, native masks, wallpaper, permissions or release settings change.
- Fixed 1920×720 canvas. Four independently clipped surfaces leave
  `(520,120,880,530)` entirely transparent for the native ADAS view. Typography is
  kept away from READY, speed-sign and ESP foreground zones. These are intended
  bounds, with an executable raster test; actual vehicle composition is unverified.
- Native card 0 yields the entire theme. Card 1 offers summary, trip and navigation
  pages; UP/DOWN/ENTER cycle, BACK/HOME return to summary. LEFT/RIGHT and long presses
  do nothing. Card 3 displays **read-only** climate data; BACK does not leave its root.
  This collection intentionally does not reproduce writable vehicle-setting menus.
- While any preparing/CarPlay/AA projection flag is active, the entire theme becomes
  transparent and noninteractive. Full-display CarPlay/AA can bypass theme bounds,
  so this conservative policy avoids covering or cropping a map. It restores when
  all flags are false. It never requests a map, changes dimensions or rewrites settings.
- Existing `AppDefaultPosition` describes the transparent window for ordinary apps;
  it is not a claim that full-display projection follows those bounds.
- No invented default telemetry. Missing, malformed and explicitly invalidated values
  display `--`; real zero remains zero. Range needs both components. Signed electrical
  power needs valid voltage and current; negative power is regeneration. Nonfinite
  derived power and unrenderable calibrated speed are unknown, not fabricated values.
- Canonical speed is raw and uses `shared/car/carDerivations.js#getAdjustedSpeed`.
  Friendly `carSpeed` is already calibrated and is not calibrated a second time.
  First canonical delivery wins even when numerically equal to the earlier alias.
- Canonical subscriptions are intersected with advertised keys. Subscribe precedes
  cache reads, and a synchronous push cannot be overwritten by a stale seed. Preferences
  use their existing preference channel, never `getCarData`. No card reseeding.
- Climate setpoints at the existing LO/HI endpoints use those labels. Navigation text
  is written with `textContent`, bounded in length, and unknown maneuvers do not invent
  a direction. An inactive/invalid route clears guidance.
- No telemetry polling or continuous animation. Rendering is coalesced into one pending
  rAF; only the clock has a minute-boundary timer. The v9 host owns heartbeat. Cleanup
  cancels pending work, removes handlers and unsubscribes once.
- No staleness timeout is invented: the host deduplicates unchanged readings and does
  not provide a per-signal freshness contract. Silence alone must not turn valid stable
  telemetry into a fabricated disconnect.

## Build and checks

From the repository root:

```sh
node cluster-widgets/source/v1.0/shared/original-themes/build.mjs
node cluster-widgets/source/v1.0/shared/original-themes/check.mjs
npm --prefix cluster-widgets run check
python3 -m unittest discover -s scripts/virtual-cluster-tests -v
```

Each theme also supports `npm run build` in its own directory. Build regenerates the
small calibration adapter from the canonical shared function, then inlines CSS and
scripts. **Inlined scripts execute after the body DOM**, not synchronously in the head
where `defer` would be lost. Committed package/source equality is checked.

Browser and pixel evidence (desktop Chromium, **not Android/WebView certification**):

```sh
cd cluster-widgets/source/v1.0/shared/original-themes
npm ci --ignore-scripts
npx playwright install --with-deps chromium
npm run build
npm test
npm run test:browser
```

Set `IMPULSE_CHROME` to an existing Chromium executable when appropriate. Evidence goes
to `test-artifacts/original-themes/` (or `IMPULSE_EVIDENCE_DIR`). The suite opens the real
packaged `app.html` offline, checks initial missing readings, calibrated speed, signed
power, zeros, invalidation, long values, navigation safety, key/card transitions,
projection preparation/restore, disable/enable and cleanup. It checks every pixel of
the ADAS window, full transparency in yielded states, and telemetry exclusion-zone
collisions. Five `*-preview-simulado.png` files are labelled **simulated data**; their
checkerboard is QA-only, never part of a production theme.

A separate read-only CI workflow runs these checks and uploads evidence. No secrets,
release, deployment or merge steps are present.

## Theme Lab

The existing catalog discovers all five source folders. The injected simulator supplies
friendly speed/gear/energy data without a duplicate keyboard binding. Its legacy
`evPowerKw` does not supply canonical voltage/current, so electrical power can remain
`--` in that generic simulator. Use the browser suite's explicitly synthetic canonical
fixture for complete power/navigation previews. Production never consumes fake power
or seeds demonstration data.

## Validation on 2026-10-20

- Five package builds and exact source/package/manifest comparisons passed.
- 184 Node/VM runtime scenarios passed, including malformed values, real zero,
  alias-to-canonical equality, bootstrap ordering, card ownership, LO/HI, derived
  overflow and idempotent cleanup.
- Existing Theme Lab checks passed (13 discovered catalog entries).
- Existing virtual-cluster suite passed 6 tests, including 15 host lifecycle scenarios
  compiling production Java against JVM collaborator fakes; this is not Android validation.
- Local raster/browser execution was blocked before rendering: Chromium process
  creation failed on sandbox IPC; the isolated HTTP server was not reachable from the
  cloud browser; browser download also failed at the gateway. No local screenshots
  were presented as validated output. The browser suite and CI provide reproduction.
- Android Gradle build/unit tests could not start compilation locally because the
  Gradle 9.3.1 distribution download was unavailable. No APK or Android test pass claimed.
- Physical central, older Android 9 WebView, compositor/native alert coexistence,
  full projection and prolonged driving performance remain required review gates.
  Conservative classic-script/CSS syntax and no heavy graphics are design choices,
  not evidence of hardware homologation.

Keep the PR in draft until rendered output and the appropriate target-device gates
are reviewed. Do not publish these packages to the release/catalog branch as part of HAV-28.
