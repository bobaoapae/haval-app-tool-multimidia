# v2.3 Overscan Investigation — Paused Notes

**Paused**: 2026-05-18. Resume after the AA sidebar auto-hide work (v2.4) ships.

## Status

Partially working. The plumbing fires; the visible result still doesn't change.

### What works (confirmed on-vehicle)

- `getEffectiveBounds()` subtracts overscan when `displayId == 0` and the persistent bar is enabled. Confirmed by log line `SAVED bounds for com.ts.androidauto.app on display 0: [0,0,1920,700]` after a Display 3 → Display 0 move via Impulse.
- All four Display-0 restore paths use the new `applyOverscanToDisplay0Height(packageName, y2)` helper:
  - `launchAnyApp` DISPLAY_MOVE RESTORE (cached-bounds branch)
  - `launchAnyApp` DISPLAY_MOVE RESTORE (fallback branch)
  - `evictOtherAppsFromDisplay` RESTORE (both branches)
  - `bringAllToMainDisplay`
- `reapplyDisplay0BoundsForOverscanAsync()` exists and is wired to:
  - `OverscanReceiver` (broadcast)
  - `BottomBarUI` per-app slider's `updateSettings` lambda
  - `BasicSettingsScreen` persistent-bar toggle (both enable and disable branches)
- v2.2 AA APK SurfaceView crop (`height × 1.5`, `topMargin = −height / 4`) works perfectly on Display 3 (cluster, 1920×596 → SurfaceView 1920×894, pos (0,−87)).

### What doesn't work (user-visible)

User reports "overscan still not working" — moving AA to Display 0 with bar enabled
+ AA overscan = 20 does NOT visually shrink the AA video by 20px. The window
DOES shrink (bounds `[0,0,1920,700]`) but the on-screen AA video appears full
1920×720 still.

### Hypotheses to investigate next

1. **SurfaceView resize race**: When `am stack resize` shrinks the window from
   720 → 700, the v2.2 crop math should recompute (height × 1.5, neg topMargin
   /4) on the next `setDisplayParams` call. But maybe `onConfigurationChanged`
   isn't fired for a 20px change — Android might absorb such a small Δ
   silently or not deliver the config change because dp values still round
   the same. Run `am stack resize` from shell with the exact 20px Δ and
   capture `dumpsys SurfaceFlinger` before/after to check whether the
   SurfaceView pos/size actually updates.

2. **SurfaceFlinger compositor lag**: The system overscan may already be
   reserving the 20px bottom strip via `wm overscan`, and the AA window's
   actual rendering region is *already* 700 even when nominal bounds are 720.
   Then our explicit `am stack resize` to 700 produces no visible change
   because the system was already cropping that region. Compare `dumpsys
   SurfaceFlinger` VisibleRegion against the nominal bounds — if the
   VisibleRegion is `[_,_,_,700]` even with nominal bounds 720, this is the
   case and v2.3 is a no-op for AA.

3. **AA SurfaceView clipped by HW composer to 720**: The SurfaceView buffer is
   composed via Hardware Composer (HWC), which on this head unit may have
   its own clipping/window mapping. If HWC is bypassing our LayoutParams and
   showing a fixed 720-row strip, we'd see no change. Check
   `dumpsys SurfaceFlinger --display-id` for layer policies.

4. **Reapply trigger never fires from user UI**: When the user drags the AA
   overscan slider, `BottomBarUI.updateSettings` should call
   `reapplyDisplay0BoundsForOverscanAsync()`. We added the call but never
   actually saw `[v2.3 OVERSCAN_REAPPLY]` logs in any session. Add a
   `Log.w(TAG, "reapply called")` at the top of the method and confirm the
   slider truly routes through `updateSettings`. The slider in
   `BottomBarUI.kt:2217` might commit on a different lambda.

### Reproducer (when resuming)

```powershell
# Make sure v2.2 AA APK is mounted (or bundled — see assets question below).
& "C:\...\adb.exe" -s <CAR_IP>:5555 shell "md5sum /vendor/app/AndroidAutoApp/AndroidAutoApp.apk"
# Expect: 231cbf1cc0f46076be9fabb0156ff156

# Snapshot before any change
$before = dumpsys SurfaceFlinger | grep -A 7 'BufferLayer (SurfaceView - com.ts.androidauto.app'

# Force-resize AA to overscan-adjusted bounds via shell (skip Impulse)
& "C:\...\adb.exe" -s <CAR_IP>:5555 shell "am stack resize <STACK_ID> 0 0 1920 700"

# Snapshot after
$after = dumpsys SurfaceFlinger | grep -A 7 'BufferLayer (SurfaceView - com.ts.androidauto.app'

# Compare pos= and size= lines. With v2.2 crop, after-size.height should be
# 1050 (= 700 × 1.5) and pos.y should be −175 (= −700 / 4). If size stays
# 1080 / pos −180, the smali hooks did not refire — hypothesis 1.
```

### Files involved

- [DisplayAppLauncher.kt](../../app/src/main/java/br/com/redesurftank/havalshisuku/managers/DisplayAppLauncher.kt) — `getEffectiveBounds`, `applyOverscanToDisplay0Height`, `reapplyDisplay0BoundsForOverscan`, all four restore paths.
- [OverscanReceiver.kt](../../app/src/main/java/br/com/redesurftank/havalshisuku/broadcastReceivers/OverscanReceiver.kt) — broadcast hook.
- [BottomBarUI.kt](../../app/src/main/java/br/com/redesurftank/havalshisuku/ui/components/BottomBarUI.kt) — `updateSettings` slider hook.
- [BasicSettingsScreen.kt](../../app/src/main/java/br/com/redesurftank/havalshisuku/ui/screens/BasicSettingsScreen.kt) — persistent-bar toggle hook.
- AA smali: `setDisplayParams` epilogue in [scripts/aa-patches/patch_logic.py](patch_logic.py) (v2.2 milestone).

### Background-broadcast caveat

Android 9 blocks `am broadcast -a ACTION_UPDATE_OVERSCAN` from `adb shell`
when Impulse is in the background — `BroadcastQueue: Background execution not
allowed`. This affects ad-hoc testing only; normal user flow (slider drag,
bar toggle) runs in-process so isn't gated. If we later want external
automation to work, we'd need to register the receiver dynamically inside
`ForegroundService` instead of as a manifest-declared receiver.
