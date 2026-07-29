#!/usr/bin/env python3
"""
Bake the minimalist theme's left/right mask gradients directly into the
Display-1 cluster wallpaper.

Why: the theme (and therefore `.mask-panel.left` / `.mask-panel.right`) runs on
display 3, which sits ON TOP of the native card 0 cluster info. The panels have
to be hidden on card 0 or they cover the gauges. The Display-1 wallpaper, on the
other hand, sits BEHIND card 0 — so a gradient painted into the wallpaper keeps
the "integrated" look on every card without ever obscuring card 0.

Geometry is taken 1:1 from cluster-widgets/source/v1.0/minimalist/src/styles/night.style.css:

    .mask-panel            { width: 800px; height: 100%; }
    .mask-panel.left       { left: 0;  background: linear-gradient(to right, #000 50%, transparent 100%); }
    .mask-panel.right      { right: 0; background: linear-gradient(to left,  #000 50%, transparent 100%); }

The source art is 1696x624 and the panel is InstrumentProjector's ImageView with
scaleType=CENTER_CROP over a 1920x720 display, so we replicate that fit first —
otherwise the baked gradient stops would land a few pixels off from the CSS ones.

Usage:
    python scripts/bake-cluster-bg.py                 # src/assets -> Themes/v1.0/minimalist
    python scripts/bake-cluster-bg.py --bars          # also bake the top/bottom bars
    python scripts/bake-cluster-bg.py --preview out.png
"""

import argparse
import os
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("Pillow is required:  python -m pip install pillow")

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SRC = os.path.join(REPO, "cluster-widgets", "source", "v1.0", "minimalist", "src", "assets", "car-bg.png")
DST = os.path.join(REPO, "cluster-widgets", "Themes", "v1.0", "minimalist", "car-bg.png")

# Display 1 / cluster viewport
DISPLAY_W, DISPLAY_H = 1920, 720

# .mask-panel geometry (CSS px == display px, the theme canvas is 1920x720)
PANEL_W = 800          # .mask-panel width
SOLID_STOP = 0.50      # linear-gradient(..., #000 50%, transparent 100%)

# .mask-top-bar / .mask-bottom-bar (only with --bars)
TOP_BAR_H = 95
TOP_BAR_SOLID = 0.80   # linear-gradient(to bottom, #000 80%, rgba(0,0,0,.6) 85%)
TOP_BAR_TAIL_ALPHA = 0.6
BOTTOM_BAR_H = 60


def center_crop_fit(img, w, h):
    """Replicate Android ImageView.ScaleType.CENTER_CROP into a w x h canvas."""
    sw, sh = img.size
    scale = max(w / sw, h / sh)
    rw, rh = round(sw * scale), round(sh * scale)
    resized = img.resize((rw, rh), Image.LANCZOS)
    left = (rw - w) // 2
    top = (rh - h) // 2
    return resized.crop((left, top, left + w, top + h))


def side_alpha(x, width):
    """Alpha of the left mask panel at column x (mirrored for the right side)."""
    if x >= width:
        return 0.0
    t = x / width                      # 0 at the screen edge, 1 at the inner edge
    if t <= SOLID_STOP:
        return 1.0
    return 1.0 - (t - SOLID_STOP) / (1.0 - SOLID_STOP)


def bake(img, bars=False):
    """Composite black at the computed alpha, in sRGB space (matches the browser)."""
    img = img.convert("RGB")
    w, h = img.size
    px = img.load()

    # Per-column alpha for the L/R panels, combined the way two stacked
    # black layers would combine: 1 - (1-a_l)(1-a_r).
    col_alpha = []
    for x in range(w):
        al = side_alpha(x, PANEL_W)
        ar = side_alpha(w - 1 - x, PANEL_W)
        col_alpha.append(1.0 - (1.0 - al) * (1.0 - ar))

    # Per-row alpha for the bars (opt-in).
    row_alpha = [0.0] * h
    if bars:
        for y in range(h):
            a = 0.0
            if y < TOP_BAR_H:
                t = y / TOP_BAR_H
                a = 1.0 if t <= TOP_BAR_SOLID else TOP_BAR_TAIL_ALPHA
            elif y >= h - BOTTOM_BAR_H:
                a = 1.0
            row_alpha[y] = a

    for y in range(h):
        ra = row_alpha[y]
        for x in range(w):
            a = col_alpha[x]
            if ra:
                a = 1.0 - (1.0 - a) * (1.0 - ra)
            if a <= 0.0:
                continue
            if a >= 1.0:
                px[x, y] = (0, 0, 0)
                continue
            k = 1.0 - a
            r, g, b = px[x, y]
            px[x, y] = (int(r * k + 0.5), int(g * k + 0.5), int(b * k + 0.5))

    return img


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--src", default=SRC, help="pristine source artwork")
    ap.add_argument("--out", default=DST, help="baked wallpaper shipped with the theme")
    ap.add_argument("--bars", action="store_true", help="also bake .mask-top-bar / .mask-bottom-bar")
    args = ap.parse_args()

    src = Image.open(args.src)
    print(f"source  : {args.src}  {src.size[0]}x{src.size[1]}")

    fitted = center_crop_fit(src, DISPLAY_W, DISPLAY_H)
    baked = bake(fitted, bars=args.bars)
    baked.save(args.out, "PNG", optimize=True)

    print(f"output  : {args.out}  {DISPLAY_W}x{DISPLAY_H}  ({os.path.getsize(args.out) / 1024:.0f} KB)")
    print(f"panels  : {PANEL_W}px each side, solid to {int(PANEL_W * SOLID_STOP)}px, fade to 0 at {PANEL_W}px"
          + ("  + top/bottom bars" if args.bars else ""))


if __name__ == "__main__":
    main()
