# v2.5 — AAP `DISPLAY_TYPE_CLUSTER` (planned)

## Goal

Stop relying on the SurfaceView X-crop trick in v2.4 to hide Google's
AA app rail. Instead, tell Google's Android Auto on the phone that our
cluster display is `DISPLAY_TYPE_CLUSTER`, so the phone renders cluster
UI from the start — no sidebar, no wasted encoder bandwidth, and the
buffer aspect ratio likely matches the cluster zone naturally (no Y-
padding bars either, which would also retire v2.2).

## Why this is the right architectural fix

The AAP protocol defines three display types in
`com.ts.androidauto.projectionservice` (Service APK strings dump shows
`DISPLAY_TYPE_MAIN`, `DISPLAY_TYPE_CLUSTER`, `DISPLAY_TYPE_AUXILIARY`).
Google's AA app on the phone *natively* renders different UI per type:

- `MAIN` → full interactive UI (sidebar, app grid, etc.)
- `CLUSTER` → minimal projection (typical: navigation turn arrows, brief
  media now-playing, no sidebar, simplified scoreboard)
- `AUXILIARY` → custom secondary UI

So the fix is "lie to the phone about which display type the projection
is for" when AA is on Display 1 or 3.

## Where the work lives

Different APK from v1.x–v2.4. We've only ever patched
`AndroidAutoApp.apk` (`com.ts.androidauto.app`); v2.5 needs
`AndroidAutoService.apk` (`com.ts.androidauto.projectionservice`).

The Service APK is ~6.5 MB vs the App's 348 KB. Larger surface to
navigate but the entry points should be identifiable.

## Reproducer scaffold for the next session

```powershell
# 1. Disassemble the Service APK
java -jar tools/apktool_3.0.2.jar d -f -o build_v25/service `
    /vendor/app/AndroidAutoService/AndroidAutoService.apk      # pull from car first

# 2. Search for the display-type enum and its usage
grep -rn "DISPLAY_TYPE_MAIN\|DISPLAY_TYPE_CLUSTER" build_v25/service/smali/
```

The strings we already saw in the on-vehicle Service APK:

```
DISPLAY_TYPE_AUXILIARY
DISPLAY_TYPE_AUXILIARY_VALUE
DISPLAY_TYPE_CLUSTER
DISPLAY_TYPE_CLUSTER_VALUE
DISPLAY_TYPE_FIELD_NUMBER
DISPLAY_TYPE_MAIN
DISPLAY_TYPE_MAIN_VALUE
```

Those look like protobuf-generated names. Likely the AAP protocol uses
protobuf; the `*_VALUE` suffix is a standard Java protoc pattern, and
`*_FIELD_NUMBER` indicates a proto field. So the display type is a
proto field set somewhere in the handshake/service-discovery message.

## Open design questions

1. **How does Service know which physical display to advertise?**
   It needs to know "AA is going to be projected on Display 1/3, advertise
   CLUSTER" vs "AA on Display 0, advertise MAIN". Options:
   - Read a SharedPreferences value Impulse writes on display moves.
   - Listen for an in-process broadcast / IPC from Impulse.
   - Inspect the activity's current display at handshake time (might not
     be possible from Service if it doesn't know about the activity).

2. **Mid-session re-negotiation.** The AAP handshake happens once per
   session. To switch from MAIN to CLUSTER mid-session (e.g., when the
   user moves AA from Display 0 to cluster), we'd need to *force-restart
   the AAP session*. That means `am force-stop com.ts.androidauto` plus
   `com.ts.androidauto.app` and re-launch. Loses the v2.1 "no recreate"
   benefit — brief flicker on each move. Trade-off worth quantifying.

3. **What does the phone actually do with a CLUSTER type?** Need empirical
   testing — possibly the phone:
   - Sends video at a different resolution (probably better — matches
     our cluster geometry without our crop math).
   - Renders entirely different UI (likely — no sidebar).
   - May refuse to project if the head unit doesn't advertise it
     correctly (handshake validation).

4. **Should v2.5 replace v2.4 or coexist?** Ideal end state: v2.5 fully
   replaces v2.4 (no SurfaceView X-crop needed because the phone never
   draws the sidebar). But while building/testing v2.5, keeping v2.4 in
   the AA APK is harmless (X-crop only applies when displayId != 0, and
   if the AAP video is already 1920×596 cluster-native, oversizing by
   16/15 + leftMargin will just cut into actual content — bad). So v2.5
   build should also remove v2.4 from the patch script, OR gate v2.4 on
   a "fallback when MAIN" condition. Cleanest: a v2.5 patch script
   that drops v2.4 entirely.

## Estimated effort

- Disassemble Service APK + locate DISPLAY_TYPE_* references: 1–2 hr.
- Identify proto message that carries the field and how it flows to the
  phone: 2–4 hr (may need wire-level capture).
- Wire Impulse to signal "current AA display type" to the Service:
  1–2 hr (pref + receiver).
- Service-side patch to read the signal + force re-handshake on change:
  3–5 hr.
- Test + iterate: indeterminate; AAP behavior may surprise.

Total: maybe **2–3 working days**. Substantially more than v2.4's ~1
hour because Service APK is new ground and AAP handshake mechanics are
opaque.

## Recommended order when resuming

1. Pull the Service APK fresh from the car (versions may diverge from
   stock dumps).
2. Disassemble + grep for DISPLAY_TYPE usage.
3. Read the handshake / ServiceDiscoveryResponse code path; trace where
   the display type is set.
4. Decide: hardcode CLUSTER for all displays vs read from a pref.
5. Prototype the simplest version: hardcode CLUSTER, test what happens.
6. If CLUSTER feels right, build the per-display selection logic.
