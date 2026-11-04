# HUD Simulator

Browser preview of the Impulse HUD overlay (display 4096, 480x240), for approving layout changes
before touching the app. Open `index.html` in Chrome, or serve the folder:

```bash
python -m http.server 8798 --directory scripts/hud-simulator
```

It is a hand port of `app/src/main/java/br/com/redesurftank/havalshisuku/hud/HudOverlayView.kt`
and `HudOverlayModel.kt`: same geometry, icons and bar rules. Keep the two in step — change the
simulator, get the layout approved, then port the same constants to Kotlin.

The native HUD on the right (speed, icons, lane lines) is a rough mock from a photo, for context
only. The grey bands are outside the optics (only y 40..200 reaches the windshield).
