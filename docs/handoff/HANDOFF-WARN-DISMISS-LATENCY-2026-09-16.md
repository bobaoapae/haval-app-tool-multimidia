# Handoff — warn dismiss latency (Minimalist)

**Date:** 2026-09-16  
**Branch:** `feature/new-screen-enhancements-v8`  
**Scope:** BACK → warning dismiss → theme WARN clear delay; then 2.5s lockout after door+seatbelt. No AA / Impulse Drive / PR-merge changes.

## Finding (WARN clear lag)

Backend clears instantly (`DISMISS` + `immediate=true`). Host was calling `updateVirtualClusterVisibility` + `syncSecondaryDisplayApps(3)` on the UI thread **before** `evaluateJavascript(control('warningActive', …))`. Car retest: clear is much faster after reordering.

## Finding (2.5s lockout)

Door + seatbelt are two cards (OEM shows one popup). Seatbelt CAN onset starts first; closing the door leaves seatbelt top with an old onset, so BACK right after door-close could still pass lockout (`timeSinceWarningMs=4380` at 20:00:40). Fix: re-arm lockout when a card **becomes top** (`warning_card_became_top`); dismiss uses max(key onset, top-since). Early BACK logs `dismiss_warning_ignored`.

## Changes (uncommitted)

| File | Change |
|------|--------|
| `InstrumentProjector2.kt` | Theme push first + probes; card-top lockout anchor; `dismiss_warning_ignored` |
| `ClusterWarningPolicy.kt` + unit test | `dismissLockoutOnsetMs` |
| `minimalist` `main.js`, `dashboardInfo.js` + Themes `app.html` | `[warn-diag]` — **version stays 1.0.2** |
| `.cursor/memory/project_warn_dismiss_latency.md` | Durable note |

## Validation

- Theme version **1.0.2** (no bump).
- APK with host reorder installed earlier; lockout fix APK installed ~20:09 to `192.168.33.249:5555` (`Success`), force-stopped — open Impulse from launcher.
- `ClusterWarningPolicyTest` + `assembleDebug` OK.

## Retest (car)

1. Open Impulse; belt off → open door → close door → BACK immediately → seatbelt must stay until ≥2.5s after it became top.
2. Grep: `dismiss_warning|dismiss_warning_ignored|warning_card_became_top|warning_theme_push|warn-diag`

## Not done

- Commit / push (await explicit ask).
