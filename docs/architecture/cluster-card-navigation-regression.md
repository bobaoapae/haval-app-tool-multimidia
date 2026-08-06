# Cluster card navigation — RESOLVED 2026-07-30

Status: **fixed** in `52a6b08`. Investigated 2026-07-18, -25, -28 and -30 on-car.

## v301 + contract-v1 integration note (2026-08-06)

The PR #117 card-dispatch improvements were integrated without discarding the v301
ownership guards. `msgId=135` acknowledgement remains unconditional and early; native
callbacks are fanned out away from the binder thread and retain the liveness recovery.
For AC and CarPlay D3 transitions, `ClusterCardSyncPolicy` still rejects delayed OEM
callbacks while an explicit synthetic transition owns the card. LEFT/RIGHT creates the
immediate synthetic transition, while later `msgId=133` remains eventual confirmation.
Non-navigation input releases synthetic ownership. This combined policy must be preserved
in future contract or theme changes.

## Root cause

The car emits `msgId=135` alongside every cluster card change and expects a
`setMsg(135, val)` acknowledgement. v7's handler returned early whenever Android Auto was
considered active, so the reply was never sent — and the car then **stopped emitting
`msgId=133` for card changes entirely**, which made cluster navigation appear dead.

Proven by running the same key sequence on both branches with identical instrumentation:

```
v6 (works):   RX 133 value=1  ->  TX setMsg(135, 1) within 1-9ms, on every press
v7 (broken):  RX 135          ->  "Android Auto handled cluster media command", no TX
                                  ...and no further 133 at all
```

The fix makes the acknowledgement **unconditional** and sends it **before** Android Auto
handling. Both parts matter: an earlier attempt removed the early return but still ran the
AA path first, and that path can `startService` + `delay(120)` + wait on a 900ms ack
timeout, so the reply went out far too late. After the fix v7 matches v6 exactly.

Why it produced such a confusing symptom set:

- **Intermittent** — gated on AA-active state, which flapped because DCM keeps reporting a
  wirelessly paired phone even after the user disconnects it.
- **Worked briefly after a fresh start** — before that state settled.
- **Theme-independent** — reproduced on Minimalist, Default and legacy Basic.
- **Immune to every card-logic change** — the defect was upstream of card handling.
- **HOME often still worked** — different path, not always gated.

Everything below is the investigation record, kept because the ruled-out list and the
methodology traps are worth not repeating.

## Symptom (as originally observed)

With v7 running, the cluster's card ring behaves as **linear** instead of cyclic:

```
3  <-- 0 -->  1          (v7: LEFT/RIGHT only reach card 3 or 1 via card 0)
0 -> 1 -> 3 -> 0 -> ...  (v6 and a9b31f1: full cycle, including 1 -> 3 directly)
```

Concretely: from card 1, pressing RIGHT does not reach card 3. The **car itself** does
not navigate — this is not a lost-notification problem (see "How to tell the two defects
apart" below).

## Bracketing

| Commit | 1 -> 3 via RIGHT | Notes |
| --- | --- | --- |
| `21f5a01` (v6 branch tip) | works | full cycle, every press reported |
| `98b4e2f` (v6/v7 merge base) | works | 16 car reports, no prediction in this build |
| `a9b31f1` refactor/card-flow-performance-architecture | works | full cycle `0b -> 1 -> 3 -> 0a` confirmed on-car |
| `5668965` Prepare preview projection and dashboard stability | partial | 1 -> 3 worked, 3 -> 0 did not |
| HEAD (v7) | **broken** | stuck on card 1; only HOME/LEFT escape via card 0 |

`5668965` is a ~10k-line snapshot commit, so the commit id alone does not identify the
mechanism. Its ServiceManager change is only `ClusterCardSyncPolicy` filtering plus
persistent logging — neither can stop the car from navigating.

## Test oracle (important)

Earlier bisects produced two **invalid** verdicts (`386797c`, then `c2b2abc`) because the
oracle was unsound. Use this one:

> From card 1, press RIGHT once, wait ~3s. **Did the back layer move to card 3?**

Requirements:

- Watch the **back layer** (the car's own cluster UI), not our WebView. Use a transparent
  theme with no wallpaper — the minimalist theme with no background selected lets both
  layers be seen at once. Our front layer can move independently of the car.
- Do **not** judge by whether the UI "cycles". While local prediction existed, the front
  layer cycled convincingly even when the car never moved. That is what invalidated the
  first bisect.
- Validate any bisect verdict against the accused commit's **parent** with the identical
  protocol before believing it.

## Car behaviour worth knowing

- Cards **1 and 3 are Android-owned** (the car renders "loading" there when our app is not
  feeding them). Card 0 is the car's own.
- Card 0 has **sub-pages** in some vehicle states (observed while charging: `0a`, `0b`).
  The car reports `msgId=133` with cardId `0` for **both**, so a sub-page move looks like
  "no change" to us. Any logic assuming a fixed `{0, 1, 3}` ring is therefore wrong — this
  is why local card prediction was removed.

## How to tell the two defects apart

Two distinct problems were conflated for hours:

- **Defect A — lost reports.** The car navigates (back layer moves) but `msgId=133` stops
  arriving. Re-registering the cluster callback restores delivery within ~35ms, reliably.
  Handled reactively in `ServiceManager.refreshClusterCallbackIfStale()`. Root cause still
  unknown; the callback is *not* slow (fan-out timing showed no overruns) and it is not
  caused by duplicate registration (a clean start registers exactly once).
- **Defect B — this file.** The car does **not** navigate; the back layer stays put. No
  amount of callback recovery helps.

If the back layer moves, it is A. If it does not, it is B.

## Ruled out by measurement (do not re-litigate without new evidence)

- Presentation window flags (`FLAG_NOT_FOCUSABLE` / `NOT_TOUCHABLE` / `LAYOUT_NO_LIMITS`)
- Blocking the input service's binder thread (`ensureUi` posts; a threading fix changed nothing)
- Blocking the cluster service's binder thread (fan-out measured; zero overruns >50ms)
- Accessibility service key filtering (identical config and auto-enable in v6)
- Android Auto (connected during the working v6 test; disconnecting changed nothing)
- Duplicate key-listener or cluster-callback registration (clean start: 1 init, 1 registration)
- Accumulated dead callbacks from prior processes (v6 works flawlessly with the same corpses present)
- Car-side state degradation (v6 reinstalled mid-session works immediately, no reboot)

## Session 3 (2026-07-28) — symptom currently absent, cause still unproven

Full cycle observed working on-car, confirmed in the car's own reports:

```
21:08:43  Cluster card changed: 0 -> 1  (RIGHT)
21:08:44  Cluster card changed: 1 -> 3  (RIGHT)   <- the transition dead all of session 2
21:08:45  Cluster card changed: 3 -> 0  (RIGHT)
21:08:46  Cluster card changed: 0 -> 3  (LEFT)
```

**Do not assume this is fixed by the committed changes.** Two hypotheses tested and
disproven in the same session, and the working state appeared without a clear cause:

- **msgId=135 acknowledgement.** The car emits `msgId=135` alongside a wheel press, and
  v7 returned early without replying whenever Android Auto was considered active (DCM
  reports `aaWirelessState=4` even after the user disconnects the phone). Making it always
  reply did **not** restore 1 -> 3. Reverted.
- **Cluster heartbeat.** `isClusterHeartbeatRunning` is never reset during cleanup while
  `initializeServices()` destroys the handler the heartbeat runnable lives on, so after any
  re-init `startClusterHeartbeat()` short-circuits forever and the 1s `msgId=134` signal
  stops permanently. This is a **real bug** and is fixed, but it is **not** the cause here:
  a liveness probe showed zero heartbeats while navigation was working.

The strongest remaining shape is **degradation over process lifetime**, not a static
v6/v7 difference:

- Navigation works immediately after a fresh app process and degrades later.
- At 15:36 (session 2) the first press after a restart reported; subsequent ones did not.
- v6 "always worked" partly because it was usually tested right after being installed —
  that testing bias may have distorted the whole v6-vs-v7 framing.

If the symptom returns, capture the log **at the moment of failure without restarting the
app**; what precedes the degradation is the missing evidence.

## Session 4 (2026-07-28, evening) — sharper characterisation, cause still open

The symptom is **not** "cards don't work". Precisely, once the car is on card 1 or 3:

| Key | Car's response |
| --- | --- |
| HOME | moves to card 0 (works; looks dead only when our theme is desynced from it) |
| LEFT / RIGHT | reports the **same** card back (`3 -> 3`), or nothing at all |

So the car keeps honouring HOME while specifically declining wheel navigation between
two Android-owned cards.

### Degradation is time-based, captured live

Working, then stuck ~1 minute later, same process, no restart:

```
21:47:29  Cluster card changed: 3 -> 1   (RIGHT)     working
21:47:30  [CARD_PUSH] card=1 delivered
21:47:32  Cluster card changed: 1 -> 3   (LEFT)      working
21:47:32  [CARD_PUSH] card=3 delivered
21:48:31  Ignoring stale: 3 -> 3  lastInputKey=BACK  stuck from here on
21:48:47  Ignoring stale: 3 -> 3  lastInputKey=HOME
```

### Eliminated this session

- **The theme.** Reproduced identically on Minimalist *and* Default.
- **Backend -> theme delivery.** `pushActiveCardToTheme()` evaluates the contract call and
  reports the JS result; it logs `delivered` for every card change. Telemetry
  (`onDataChanged`) and card pushes take different paths, but both are working.
- **Our sync/filtering.** When the car reports, the backend accepts and forwards correctly
  (61 reports from 64 presses in one sample).
- **Android Auto.** Tested with phone Bluetooth *and* WiFi off, so DCM no longer reports a
  paired device. Symptom unchanged. Note an earlier "AA disconnected" test was invalid:
  DCM still reported `aaWirelessState=4` and the app still believed AA was active.
- **msgId=135 acknowledgement** and **cluster heartbeat** (see session 3).

### Still unexplained / worth pulling on

- Our app can believe Android Auto is active while the car reports
  `isAndroidAutoConnected=false`. Not the cause of this defect, but a real state
  disagreement that drives media-key forwarding (the "music skipping next constantly"
  symptom) and the repeated `Keeping Android Auto visual stack` decision (~230 times in one
  session, every ~11s, while display 3 has 0 stacks).
- With the cluster master toggle **off**, the car offers card 0 plus a **single** "loading"
  card rather than two. So the Android-owned card *set* is dynamic and tied to our
  integration, and it does not fully revert without an MMI restart. Understanding how that
  set is negotiated is probably the shortest path to why 1 <-> 3 adjacency disappears.
- The WebView reloads frequently (17 page loads in one session). `evaluateJsIfReady()`
  queues while not loaded and `markWebViewLoading()` discards the queue; `onPageFinished`
  replays it. Not implicated in this defect but a real fragility.

## Session 5 (2026-07-28, late) — instrumented; symptom is the car ignoring most presses

The app now logs both directions of the cluster protocol (`[CLUSTER_RX]` / `[CLUSTER_TX]`,
msgIds 75/133/134/135 only), the heartbeat lifecycle, and `[INPUT_HOLD]` when we hold the
input service's thread >15ms. Use these first next session — they answer most questions
without a new build.

### What the instrumentation established

- **We are not blocking input.** `[INPUT_HOLD]` measured 0-6ms across ~65 presses, zero
  above 15ms. The blocking theory is dead by measurement, not argument.
- **Our callback is healthy.** The car sends ~100 msgIds on registration and continues
  sending 82/83/86/87 etc. throughout, including *after* a re-init.
- **The heartbeat is steady** (1/s, thousands of beats) even while navigation is broken.
- **Card delivery to the theme works.** `[CARD_PUSH] ... delivered` for every card change.
- **Shizuku drops trigger `initializeServices()`** (`Shizuku binder is null` immediately
  precedes `Initializing services`). This happens repeatedly during normal use.

### The symptom, precisely

The car ignores the majority of wheel presses while on cards 1/3, and acts on a minority.
Same key from the same card succeeds and fails minutes apart:

```
23:50:18.010 LEFT  (car on 1) -> nothing
23:50:22.710 LEFT  (car on 1) -> CAR SAYS card=3   (succeeded)
```

No timing, ordering or direction pattern has been found. HOME behaves the same way. It is
*not* a wrap-around/adjacency problem (an earlier reading suggested that; it was an
artefact of deriving transitions from our own `previousCard`, which goes stale precisely
when the car does not report — always use the raw `msgId=133 value=` instead).

### Also eliminated this session

- **Input listener doubling.** A real leak (unbinding does not drop the listener) that made
  every press dispatch twice after a re-init, and correlated suspiciously well with the
  failures. Fixed and verified gone across re-inits — the car's behaviour did not change.
- **Cluster callback lost on re-init.** Disproven: `onServiceConnected` does fire and
  msgId=133 arrives after re-init.

### Recommended next experiment

Port this same instrumentation onto a **v6** build and capture both RX streams for an
identical key sequence. v6 navigates reliably, so whatever differs at the protocol level —
a message we send that it does not, a handshake we skip, a message the car sends v6 and
never sends us — becomes directly visible rather than inferred. Everything cheaper than
this has been tried.

## Suggested next step

Bisect `a9b31f1..HEAD` using the oracle above, validating each verdict against the parent
commit. `5668965` is the prime suspect but is too large to blame without narrowing —
consider reverting its sub-areas (DisplayAppLauncher AA additions, BottomBarService,
InstrumentProjector2) individually against a fixed base rather than bisecting commits.
