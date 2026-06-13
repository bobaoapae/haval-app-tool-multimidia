# Android Auto Native Stability & Resize Patch

## Objective
Achieve stable, persistent, and correctly-sized Android Auto projection on
secondary MMI displays, with live reflow when the app is moved between
Display 0 / 1 / 3 while running.

## Versioning

Patches in this repo use an `x.y` scheme:

- **x** — major aim of the effort.
- **y** — iteration within that aim.

| Version | Aim | Status |
|---------|-----|--------|
| **v1.x** | Focus-lost patch — keep AA video alive when focus is stolen. | v1.19 is the first fully validated milestone, in `scripts/milestones/v19_focus_bypass/` (folder name kept; conceptually this is v1.19). |
| **v2.x** | Dynamic resize + AAP-buffer cropping — AA UI reflows to the active display's resolution, with encoder padding and the left app rail both clipped away on cluster displays, and always uses the full display on Display 0. | v2.0 = setDisplayParams reads live window bounds (initial layout only — historical). v2.1 = added `onConfigurationChanged` + `updateDisplayParams` passthrough + manifest `configChanges`. v2.2 = SurfaceView Y-crop epilogue, milestone `scripts/milestones/v2_2_dynamic_resize/`. v2.4 = X-crop on `displayId != 0`, milestone `scripts/milestones/v2_4_sidebar_xcrop/`. **v2.5 = current**: Display-0 override using `Display.getRealMetrics()` so the system's 1792-wide stack on shortcut launch is corrected to the full 1920. Validated milestone `scripts/milestones/v2_5_display0_full_dims/`. |
| **v2.3** | Overscan-aware Display 0 bounds. | Code shipped in Impulse but visual confirmation pending and superseded for AA by v2.5 (which bypasses overscan on Display 0). Notes in [`TODO_v2_3_overscan_investigation.md`](TODO_v2_3_overscan_investigation.md). |
| **v2.6+** | AAP `DISPLAY_TYPE_CLUSTER` (planned). | Service-APK patch (new ground — we've only touched App APK so far) to advertise cluster displays as `DISPLAY_TYPE_CLUSTER` in the AAP handshake. Google's AA on the phone then renders cluster-native UI from the start (no sidebar, possibly matching aspect ratio). Supersedes v2.2's Y-crop and v2.4's X-crop. See [`TODO_v2_5_display_type_cluster.md`](TODO_v2_5_display_type_cluster.md) (file name predates the v2.5 reassignment). |

`patch_logic.py` always applies v2.5 — which includes all v1 patches plus
all v2 patches (v2.0, v2.1, v2.2, v2.4, v2.5, v2.5-diag). v2 strictly
supersedes v1 (the v1.19 milestone APK is a strict subset of a v2.5 build).

## Patches Applied

All patches target `com.ts.androidauto.app` and live in `AapActivity.smali`
unless noted otherwise. They are applied by
[`patch_logic.py`](patch_logic.py) and are idempotent (re-running the script
on an already-patched build is a no-op).

### v1.x — Focus and Lifecycle Stability (from v1.19 milestone)

1. **`onPause`** — force `mIsOnPause = false` immediately after
   `super.onPause()`. The internal projection state never sticks in "paused".
2. **`onWindowFocusChanged`** — force `hasFocus = true`. The app always
   believes it owns focus, so the phone keeps streaming video.
3. **`hideSurface`** / **`releaseSurface`** — NOP. Prevents the video
   surface from being torn down when the activity is backgrounded.
4. **`onPauseSetVisibility`** — NOP. Prevents the blank cover view from
   being shown while the app is paused.
5. **`finish()`** — commented out. Stops the app from self-terminating.

### v2.0 — Dynamic Window-Bound Reading

6. **`setDisplayParams`** — inject a prologue that reads
   `Configuration.windowConfiguration.getBounds()` and writes the active
   *window* width/height (not the physical display) into
   `mAapDisplayAreaWidth/Height`, zeroing the margins. The original method
   body then applies those values, so the projection fits the activity's
   current window at every entry into the method — including the cluster
   sub-area when Impulse stack-resizes AA. The earlier v2.0 attempt used
   `getResources().getDisplayMetrics()` which returned the *display* size
   and missed sub-area resizes; the windowConfiguration approach is what
   landed in the validated v2.2 milestone. A `widthPixels <= 0` guard
   short-circuits the injection if bounds aren't ready yet.

### v2.1 — Live Resize on Display Move (NEW)

7. **`AndroidManifest.xml`** — expand
   `android:configChanges="uiMode"` to also include `screenSize`,
   `smallestScreenSize`, `screenLayout`, `orientation`, `density`,
   `navigation`, `keyboard`, `keyboardHidden`, `locale`, `fontScale`.
   Without this, a stack resize or display move would either trigger a full
   activity recreate (race-prone with v1's lifecycle holds) or be silently
   ignored. With it, the activity stays alive and Android delivers the
   change via `onConfigurationChanged`.

8. **`onConfigurationChanged`** — inject a new override that calls
   `super.onConfigurationChanged(newConfig)` and then re-invokes the
   v2.0-patched `setDisplayParams()`. Because `setDisplayParams` now reads
   live window bounds on every call, the layout reflows against the new
   window dimensions whenever the app is moved or resized.

9. **`updateDisplayParams(AapVideoConfig)`** — replace the body with a
   passthrough to `setDisplayParams()`. The original method is dispatched
   asynchronously from a handler message posted in `onResume` (one of the
   480p / 720p / 1080p AAP modes) and resets the SurfaceView to a
   hardcoded per-mode size (1920×1080 for 1080p). Without this passthrough
   it races with v2.0 and wins — overwriting our resize with stale dims.

### v2.2 — AAP Padding Crop

10. **`setDisplayParams` epilogue (Y-crop)** — after applying the resized
    layout, oversize the `mSurfaceProjection` SurfaceView to
    `windowHeight × 1.5` and set its `topMargin` to `−windowHeight / 4`.
    The parent `FrameLayout` (`match_parent`) clips children to the
    window's visible area, so the SurfaceView's overflow at the top and
    bottom falls outside the clip box.

    Why: `VehicleInfoLoader.initAapVideoConfig1080p` declares the AAP
    "1080p" mode as a 1920×1080 buffer where the *logical* video is only
    the inner 1920×720 (rows 180..900); the top and bottom 180 rows are
    encoder padding. The math `windowHeight × 1.5` / `−windowHeight / 4`
    is a generalization of the stock 1080p layout (`720 × 1.5 = 1080`,
    `−720/4 = −180`) and produces visually correct output for any window
    height — including the cluster's 596.

### v2.4 — Sidebar X-Crop on Cluster Displays (validated milestone)

11. **`setDisplayParams` second epilogue (X-crop)** — gated on
    `getDisplay().getDisplayId() != 0`. When the activity is on a non-main
    display:
    - Oversize the SurfaceView width to `windowWidth × 16 / 15`.
    - Set `leftMargin = −windowWidth / 15`.
    - Override `LayoutParams.gravity = LEFT | TOP (0x33)` to disable the
      XML `center_horizontal` that would otherwise center our oversized
      SurfaceView and ruin the offset math.

    Why: Google's Android Auto app on the phone draws a left "app rail"
    (Maps / Music / etc. icons) into the AAP video buffer. On a cluster
    display that strip wastes precious horizontal space. The 1/15 ratio
    matches the AAP 96dp standard rail at the head unit's 213dpi report
    — about 128 source pixels. Skipping on Display 0 keeps the rail
    usable on the main display.

    The v2.4 X-crop produces a clean cluster view with the rail clipped
    away, at the cost of cropping ~6.7% from the right side of the
    source content (acceptable — AA leaves its content well inside the
    safe area). The architecturally-correct fix is to advertise the
    cluster as `DISPLAY_TYPE_CLUSTER` in the AAP handshake so the phone
    never draws the rail in the first place; that's planned for v2.5
    (Service APK patch).

The combined v1 + v2 flow:
- Fresh launch → `onCreate → init() → setDisplayParams` (v2.0 reads window
  bounds; v2.2 Y-crops padding bars; v2.4 X-crops sidebar if on a cluster
  display)
- Background while user uses another app → v1 keeps the surface alive
- `am display move-stack` or `am stack resize` to a different display →
  Android delivers `onConfigurationChanged` (v2.1 manifest enables this) →
  v2.1 override re-calls `setDisplayParams` → v2.0 prologue picks up the
  new window bounds → v2.2 epilogue re-crops to the new height → v2.4
  epilogue re-decides X-crop based on the new `displayId` → layout
  reflows in place, no recreate, no black bars, no rail visible on
  cluster, no projection drop.
- Async AAP mode handler fires → v2.1 passthrough redirects through
  `setDisplayParams` → applies v2.0 + v2.2 + v2.4 again with the live
  window bounds instead of the stale hardcoded mode config.

## Build Pipeline

All commands run from the project root.

```powershell
# 1. Disassemble stock APK
java -jar tools/apktool_3.0.2.jar d -f -o build_v19/app `
    scripts/aa-patches/stock/AndroidAutoApp_stock.apk

# 2. Apply patches (v1.x + v2.0 + v2.1 — all in one run)
python scripts/aa-patches/patch_logic.py

# 3. Reassemble
java -jar tools/apktool_3.0.2.jar b `
    -o build_v19/AndroidAutoApp_v19_unsigned.apk build_v19/app

# 4. Align
& "C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\zipalign.exe" `
    -f 4 build_v19\AndroidAutoApp_v19_unsigned.apk `
    build_v19\AndroidAutoApp_v19_aligned.apk

# 5. Sign with debug keystore
& "C:\Users\vanes\AppData\Local\Android\Sdk\build-tools\36.1.0\apksigner.bat" `
    sign --ks C:\Users\vanes\.android\debug.keystore `
    --ks-pass pass:android `
    --out build_v19\AndroidAutoApp_v19_signed.apk `
    build_v19\AndroidAutoApp_v19_aligned.apk
```

## Deployment

There are two supported paths. Pick based on whether you are iterating on
the patch (path A) or shipping it to users (path B).

### A. Direct push to a developer car (fast iteration loop)

For "I just changed `patch_logic.py` and want to see it on the car":

```powershell
# After build + sign:
python scripts/milestones/v19_focus_bypass/deploy_to_car.py --ip <CAR_IP>
```

This script (intentionally living in the milestone directory because the
v19 milestone owns the deployment recipe) pushes the signed APK to
`/data/local/tmp/v19_app.apk` and bind-mounts it over
`/vendor/app/AndroidAutoApp/AndroidAutoApp.apk` via Telnet. It also
clears the dalvik cache for the package and restarts AA. Idempotent.

### B. Ship inside the Haval app (production path)

End users install the Haval companion app, and
[`AndroidAutoPatchManager`](../../app/src/main/java/br/com/redesurftank/havalshisuku/managers/AndroidAutoPatchManager.kt)
owns the runtime mount lifecycle on the user's car. It expects the signed
patched APK to live at:

```
app/src/main/assets/aa_patches/AndroidAutoApp.apk
```

On the user's car, the manager:
1. Copies the asset to `/data/local/tmp/aa_patches/AndroidAutoApp.apk`.
2. Stamps the SELinux context (`u:object_r:vendor_app_file:s0`) and mode 0644.
3. Bind-mounts it over `/vendor/app/AndroidAutoApp/AndroidAutoApp.apk`.
4. Bind-mounts an empty directory over `/vendor/app/AndroidAutoApp/oat` to
   force the system to use the new `classes.dex` instead of the cached AOT.
5. Wipes `/data/dalvik-cache/arm64/*AndroidAuto*` and force-stops the AA
   processes so the new code loads on next launch.
6. Calls `ensureMounted()` again from `ForegroundService` after boot so
   the mount survives reboots even though `/vendor` is read-only.

So the production release flow is:

```powershell
# 1. Build + sign the patched APK (steps 1-6 of the pipeline above)
# 2. Copy into the Haval app's assets
Copy-Item build_v19\AndroidAutoApp_v19_signed.apk `
          app\src\main\assets\aa_patches\AndroidAutoApp.apk -Force
# 3. Rebuild + deploy the Haval app to the car
.\scripts\Deploy-To-Car.ps1
# 4. On the car, the Haval app surfaces an "Install patches" + "Apply mounts"
#    flow in its Install Apps tab; the user taps once.
```

`Deploy-To-Car.ps1` is *only* for the Haval app itself — it does not push
or mount the AA APK. The AA APK travels inside the Haval APK as an asset.

## Verifying v2.1 on the Car

After deploying, validate that the new resize behavior works:

1. Launch AA on the main display from the Impulse app — it should fill the
   screen (1920 wide).
2. Use the Display App Launcher to send AA to Display 3 (1920×596 with the
   virtual cluster, or 1920×720 without). The video area should reflow to
   the new bounds *without* killing/restarting the projection.
3. Bring AA back to Display 0. The video area should grow back to full
   width.
4. Tail `logcat -s AapActivity` while moving — you should see
   `setDisplayParams enter.` log lines on each move, indicating
   `onConfigurationChanged → setDisplayParams` fired.

If the layout does **not** reflow on a move:
- Check `dumpsys activity activities | grep -A 3 AapActivity` to confirm
  the activity is still the same instance (no recreate).
- Confirm the manifest patch landed: `aapt2 dump xmltree
  /vendor/app/AndroidAutoApp/AndroidAutoApp.apk AndroidManifest.xml` should
  list all the configChanges flags. If it shows only `uiMode`, the bind
  mount didn't take.

## Implementation Notes

### Smali register safety in `setDisplayParams`

The original method declares `.locals 5` (= 6 registers including `p0`).
Our v2.0 injection uses `v0`, `v1`, `v2`:
- `v0` holds the `Resources` then the `DisplayMetrics` (overwritten by the
  body's first instruction, which loads `TAG` into `v0`).
- `v1` holds `widthPixels` then `0` (margin sentinel).
- `v2` holds `heightPixels`.

None of these leak into the body because the body re-initializes them. If
a future stock APK ships `setDisplayParams` with `.locals 4` or fewer,
`apply_v2_setdisplayparams_patch` will automatically bump the declaration
to 5 before injecting.

### Manifest patch survives `apktool b`

The `<activity android:configChanges="...">` attribute is stored as a
single integer bitfield in binary AXML. apktool decodes it to the textual
flag list during disassembly and re-encodes it during reassembly. Verified
by round-tripping a fresh patch through `apktool d` → `patch_logic.py`
→ `apktool b` → `apktool d` and confirming all 11 flags appear in the
output manifest. No special apktool flags or `--no-res` workarounds needed.

### Why `getResources().getDisplayMetrics()` (and not `WindowManager`)

The original v2.0 cut of `setDisplayParams` used
`getWindowManager().getDefaultDisplay().getMetrics(DisplayMetrics)`. This
works but has three downsides on the head unit's Android 9 base:

1. Four chained virtual calls — more opportunities for NPE.
2. `Display.getMetrics()` is deprecated as of API 30; even though the head
   unit is API 28, the lint warning is noisy.
3. The `WindowManager` returned by `getWindowManager()` for an Activity is
   tied to its host display; if Android attaches a transient
   `DisplayManager` instance during a display move, the metrics it returns
   can lag the actual move by a frame.

`getResources().getDisplayMetrics()` is what the framework hands the
activity's view tree for layout purposes. It is updated *before*
`onConfigurationChanged` is delivered, so reading it inside our v2.1 hook
is guaranteed to see the new size. It's also a single chained call and
non-deprecated.

### Idempotency

`patch_logic.py` uses sentinel comments (`# V2_ONPAUSE_INJECTION`,
`# V2_FINISH_BLOCKED`, `# V3_RESIZE_INJECTION`,
`# V4_ONCONFIGCHANGED_INJECTION`) to detect prior runs and short-circuit.
The `V2_/V3_/V4_` prefixes in the sentinel *strings* are kept under their
legacy names on purpose — they're already embedded in patched smali files
in the wild (e.g. the APK in `app/src/main/assets/aa_patches/`), and
renaming them would silently re-apply patches that are already in place.
The constant *names* in the Python source map onto the new x.y scheme via
the section headers; the string values are a stable on-disk format.

Running the script twice in a row applies 0 changes on the second pass.
Safe to chain into a build script.
