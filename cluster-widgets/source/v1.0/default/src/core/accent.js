/**
 * Accent color derivation.
 *
 * The theme ships a hand-tuned blue palette: bright ring highlights, dark navies,
 * faint glows. Flattening all of it to a single picked color would throw that depth
 * away, so instead we *rotate* the palette - every accent token keeps its lightness
 * and alpha, and only its hue (and saturation scale) moves to follow the user's pick.
 *
 * The base values stay in night.style.css: this module reads them out of the computed
 * :root once at boot and caches them, so the stylesheet remains the single source of
 * truth and this file only has to know token *names*.
 *
 * Semantic colors (green success/regen, red danger, orange power, the speedometer's
 * green->red RPM gradient) are deliberately absent from the list below - they carry
 * meaning and must not follow the accent.
 */

/** Reference accent the shipped palette was designed around (--color-ring-glow). */
const REFERENCE_ACCENT = '#00A0FF';

/** Fallback when the setting is missing or unparseable. */
export const DEFAULT_ACCENT = REFERENCE_ACCENT;

/**
 * Every :root custom property that carries the blue accent.
 * Tokens that are defined as var() aliases of these (the --graph-* family,
 * --mask-border-color, --progress-color) follow automatically and are not listed.
 *
 * Deliberately EXCLUDED - text fills. Rotating these tinted the readouts along with the
 * furniture, and a pale, saturated text color reads as pink/salmon on a warm accent no
 * matter how faithfully it is rotated:
 *   --text-light-blue (#afd3ff)  --menu-text (#A3AED0)  --text-cold-gray (#B0B8C4)
 * They are neutral enough to sit under any accent, so they stay as designed.
 */
const ACCENT_TOKENS = [
    // Primary & brand blues
    '--primary-blue',
    '--blue-200',
    '--blue-400',
    '--blue-600',
    '--blue-700',
    '--color-needle',
    '--color-ac-blue',
    '--color-ac-dark-blue',
    '--color-temp',
    '--color-ring-glow',
    '--color-ring-accent',
    // A glow, not a text fill - it stays on the accent so a red theme does not keep a
    // blue halo. The tokens that set actual glyph color are excluded below.
    '--text-glow-blue',
    '--color-cyan',
    '--color-light-cyan',

    // Component specific
    '--menu-bg',
    '--menu-glow-bg',
    '--color-impulse',
    '--color-ring-bg',
    '--color-light-bg',
    '--color-cyan-glow',
    '--dial-shadow-color',
    '--color-blue-glow-20',
    '--color-ac-divider',
    '--color-theme-blue',
    '--shadow-theme-blue',

    // Alpha glows & tints
    '--accent-glow',
    '--dial-gradient-blue',
    '--dial-border',
    '--shadow-blue-30',
    '--glow-blue-80',
    '--glow-blue-70',
    '--glow-blue-60',
    '--glow-blue-40',
    '--glow-blue-20',
    '--color-tick-blue',
    '--color-blue-20',
    '--color-blue-dark-80',
    '--color-ring-blue-70',
    '--color-cyan-20',
    '--color-blue-30',
    '--color-purple-20',
    '--color-dial-blue-20',

    // Section 6b - tints lifted out of the stylesheet rule body
    '--accent-ring-18',
    '--accent-ring-42',
    '--accent-ring-45',
    '--accent-ring-48',
    '--accent-ring-88',
    '--accent-azure-30',
    '--accent-sky-24',
    '--accent-skybright-40',
    '--accent-skybright-60',
    '--accent-cobalt-14',
    '--accent-cobalt-76',
    '--accent-cobaltlt-96',
    '--accent-cerulean-25',
    '--accent-royal-82',
    '--accent-cornflower-36',
    '--accent-aqua-18',
    '--accent-arctic-26',
    '--accent-arctic-62',
    '--accent-arctic-98',
    '--accent-powder-78',
    '--accent-mist-20',
    '--accent-ice-60',
    '--accent-ice-72',
    '--accent-ice-74',
    '--accent-ice-78',
    '--accent-ice-86',
    '--accent-frost-09',
    '--accent-frost-18',
    '--accent-steel-14',
    '--accent-denim-22',
    '--accent-slateblue-20',
    '--accent-navy-90',
    '--accent-midnight-85',
];

/**
 * Tokens holding a bare "R, G, B" triple rather than a full color, because their
 * consumer supplies a live alpha - rgba(var(--segment-1-rgb), var(--segment-1-alpha)).
 */
const ACCENT_RGB_TOKENS = [
    '--segment-1-rgb',
    '--segment-2-rgb',
    '--segment-3-rgb',
];

/** name -> {r, g, b, a}, captured from the stylesheet before we ever override it. */
let capturedBases = null;
/** name -> {r, g, b, a} for the bare-triple tokens above. */
let capturedRgbBases = null;
/** "r,g,b" of the accent currently applied, so repeat picks short-circuit. */
let lastAppliedAccent = null;

/* ------------------------------------------------------------------ parsing */

/**
 * Parses a CSS color into {r, g, b, a}. Handles the notations actually used by
 * this stylesheet: #RGB, #RRGGBB, #RRGGBBAA, and rgb()/rgba() with 3 or 4 args.
 * Returns null for anything else (keywords, gradients, var() chains).
 */
function parseColor(input) {
    if (!input) return null;
    const value = String(input).trim();

    if (value.charAt(0) === '#') {
        const hex = value.slice(1);
        if (hex.length === 3 || hex.length === 4) {
            const expand = (c) => parseInt(c + c, 16);
            return {
                r: expand(hex[0]),
                g: expand(hex[1]),
                b: expand(hex[2]),
                a: hex.length === 4 ? expand(hex[3]) / 255 : 1,
            };
        }
        if (hex.length === 6 || hex.length === 8) {
            const pair = (i) => parseInt(hex.substr(i, 2), 16);
            if (/[^0-9a-f]/i.test(hex)) return null;
            return {
                r: pair(0),
                g: pair(2),
                b: pair(4),
                a: hex.length === 8 ? pair(6) / 255 : 1,
            };
        }
        return null;
    }

    // rgb(...) / rgba(...) - note the stylesheet also has a legal 4-arg rgb()
    const match = value.match(/^rgba?\(([^)]+)\)$/i);
    if (!match) return null;
    const parts = match[1].split(',').map((p) => p.trim());
    if (parts.length < 3) return null;
    const channel = (p) => (p.endsWith('%') ? (parseFloat(p) / 100) * 255 : parseFloat(p));
    const r = channel(parts[0]);
    const g = channel(parts[1]);
    const b = channel(parts[2]);
    if ([r, g, b].some((n) => !isFinite(n))) return null;
    let a = 1;
    if (parts.length > 3) {
        a = parts[3].endsWith('%') ? parseFloat(parts[3]) / 100 : parseFloat(parts[3]);
        if (!isFinite(a)) a = 1;
    }
    return { r, g, b, a };
}

/* ----------------------------------------------------------- color space */

function rgbToHsl({ r, g, b, a }) {
    const rn = r / 255;
    const gn = g / 255;
    const bn = b / 255;
    const max = Math.max(rn, gn, bn);
    const min = Math.min(rn, gn, bn);
    const l = (max + min) / 2;
    let h = 0;
    let s = 0;

    if (max !== min) {
        const d = max - min;
        s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
        if (max === rn) h = ((gn - bn) / d) % 6;
        else if (max === gn) h = (bn - rn) / d + 2;
        else h = (rn - gn) / d + 4;
        h *= 60;
        if (h < 0) h += 360;
    }
    return { h, s, l, a };
}

function hslToRgb({ h, s, l }) {
    const c = (1 - Math.abs(2 * l - 1)) * s;
    const hp = ((h % 360) + 360) % 360 / 60;
    const x = c * (1 - Math.abs((hp % 2) - 1));
    let rgb;
    if (hp < 1) rgb = [c, x, 0];
    else if (hp < 2) rgb = [x, c, 0];
    else if (hp < 3) rgb = [0, c, x];
    else if (hp < 4) rgb = [0, x, c];
    else if (hp < 5) rgb = [x, 0, c];
    else rgb = [c, 0, x];
    const m = l - c / 2;
    return rgb.map((v) => Math.round(Math.min(255, Math.max(0, (v + m) * 255))));
}

function toRgbaString({ h, s, l, a }) {
    const [r, g, b] = hslToRgb({ h, s, l });
    const alpha = Math.round(Math.min(1, Math.max(0, a)) * 1000) / 1000;
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function rgbaString({ r, g, b }, a) {
    const alpha = Math.round(Math.min(1, Math.max(0, a == null ? 1 : a)) * 1000) / 1000;
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/* ----------------------------------------------------------------- oklab */

/*
 * The palette is rotated in OKLCH, not HSL, because HSL saturation is not a measure of
 * how colorful something actually looks - it varies enormously with hue.
 *
 * Measured on this theme's own tokens: HSL "S=100%" at hue 229 (the cyan accent) is only
 * 0.151 of real chroma, while HSL "S=100%" at hue 26 (red) is 0.256. Rotating in HSL
 * holds the *number* at 100% and so silently inflates real colorfulness by ~70%, while
 * perceptual lightness drops ~0.13. Across the palette that turned every mid-tone into an
 * over-saturated, too-dark version of itself - which is what read as "pink".
 *
 * OKLCH separates the three properly: rotate H, and L and C only move as far as we
 * deliberately move them.
 */

const srgbToLinear = (c) => (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
const linearToSrgb = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055);

/** {r,g,b} 0-255 -> {L, C, H} with H in degrees. */
function rgbToOklch({ r, g, b }) {
    const lr = srgbToLinear(r / 255), lg = srgbToLinear(g / 255), lb = srgbToLinear(b / 255);
    const l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
    const m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
    const s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);
    const L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
    const A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
    const B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
    return { L, C: Math.hypot(A, B), H: (Math.atan2(B, A) * 180 / Math.PI + 360) % 360 };
}

/** Raw OKLCH -> linear sRGB, may land outside 0..1 when the color is out of gamut. */
function oklchToLinear(L, C, H) {
    const rad = H * Math.PI / 180;
    const A = Math.cos(rad) * C, B = Math.sin(rad) * C;
    const l = (L + 0.3963377774 * A + 0.2158037573 * B) ** 3;
    const m = (L - 0.1055613458 * A - 0.0638541728 * B) ** 3;
    const s = (L - 0.0894841775 * A - 1.2914855480 * B) ** 3;
    return [
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    ];
}

const inGamut = (rgb) => rgb.every((c) => c >= -0.0001 && c <= 1.0001);

/**
 * OKLCH -> {r,g,b} 0-255. Out-of-gamut colors have their chroma reduced until they fit,
 * which keeps hue and lightness intact; naive channel clipping would skew both.
 */
function oklchToRgb(L, C, H) {
    let rgb = oklchToLinear(L, C, H);
    if (!inGamut(rgb)) {
        let lo = 0, hi = C;
        for (let i = 0; i < 12; i += 1) {
            const mid = (lo + hi) / 2;
            if (inGamut(oklchToLinear(L, mid, H))) lo = mid; else hi = mid;
        }
        rgb = oklchToLinear(L, lo, H);
    }
    return {
        r: Math.round(Math.min(255, Math.max(0, linearToSrgb(rgb[0]) * 255))),
        g: Math.round(Math.min(255, Math.max(0, linearToSrgb(rgb[1]) * 255))),
        b: Math.round(Math.min(255, Math.max(0, linearToSrgb(rgb[2]) * 255))),
    };
}

/**
 * Builds the palette mapping for one picked color.
 *
 * `w` is how "accent-like" a token already is, measured as its share of the reference's
 * chroma. A token sitting on the reference gets w=1 and lands exactly on the pick; near
 * neutral tokens get w~0 and stay put. Lightness and chroma deltas are both scaled by it,
 * so the pale ice/frost tints keep their own airiness instead of being dragged down into
 * a saturated salmon, and the dark navies keep their depth.
 */
/** Signed shortest way round the hue circle, in degrees. */
const hueDiff = (a, b) => ((a - b + 540) % 360) - 180;

function makeOklchRotator(referenceRgb, pickedRgb) {
    const ref = rgbToOklch(referenceRgb);
    const pick = rgbToOklch(pickedRgb);
    const chromaDelta = pick.C - ref.C;
    const lightDelta = pick.L - ref.L;

    /*
     * Perceptual hue categories are not equally wide. This palette's blues span ~27 deg
     * (OKLCH 233..260) and every one of them still reads as "blue". Rotated rigidly onto
     * red that same spread becomes 16..43, which straddles pink-red, red and orange - red
     * only reads as red across roughly 20 deg. So the spread itself has to shrink as we
     * rotate away from the palette's native hue.
     *
     * The factor is 1.0 at zero rotation, which keeps the shipped blue bit-identical, and
     * eases to 0.45 for a half-turn.
     */
    const spread = 1 - 0.55 * Math.min(1, Math.abs(hueDiff(pick.H, ref.H)) / 90);

    return (rgb) => {
        const base = rgbToOklch(rgb);
        const w = Math.min(1, base.C / (ref.C || 1));
        return oklchToRgb(
            Math.min(1, Math.max(0, base.L + lightDelta * w)),
            Math.max(0, base.C + chromaDelta * w),
            pick.H + hueDiff(base.H, ref.H) * spread,
        );
    };
}

/* --------------------------------------------------------------- capture */

/**
 * Reads the shipped value of every accent token off the computed :root and caches
 * it as HSL. Must run before the first override, otherwise we would capture our
 * own output and drift a little further on every color change.
 */
function captureBases() {
    const computed = getComputedStyle(document.documentElement);
    const bases = {};
    // Kept as raw rgb - the OKLCH rotator converts per call, and holding the original
    // bytes lets the default accent restore verbatim instead of round-tripping.
    ACCENT_TOKENS.forEach((token) => {
        const parsed = parseColor(computed.getPropertyValue(token));
        if (parsed) bases[token] = parsed;
    });

    const rgbBases = {};
    ACCENT_RGB_TOKENS.forEach((token) => {
        const parsed = parseColor(`rgb(${computed.getPropertyValue(token).trim()})`);
        if (parsed) rgbBases[token] = parsed;
    });

    return { bases, rgbBases };
}

/* --------------------------------------------------------------- artwork */

/*
 * The blue in the bar/dial artwork is baked into the pixels, so it has to be recolored
 * separately from the CSS tokens.
 *
 * CSS `filter: hue-rotate()` is the cheap way to do that, but it is a fixed linear
 * matrix rather than a real hue rotation, and it is badly wrong over large angles: the
 * theme's reference blue #00A0FF rotated onto red comes out #FF5946, a salmon, because
 * the matrix holds luma constant and blue is far darker than red. A corrective
 * saturate() can pull that single color back to #FF0000 but then blows out every other
 * tone (the dark navy #0A2345 lands on #AA0100).
 *
 * So we recolor the pixels ourselves with the same rotation the CSS tokens get. That is
 * affordable here because the artwork is 87-98% near-gray with only a narrow blue band
 * (hues clustered at ~215 deg); the near-gray majority takes a cheap early-out.
 *
 * The CSS filter stays wired up as the first-frame approximation and the fallback if
 * any of this fails - see the --accent-hue-rotate reset at the end of recolorArtwork().
 */

/** CSS custom properties holding a url(data:...) artwork image. */
const ARTWORK_VARS = [
    '--mask-top-bar-image',
    '--mapa-top-bar-image',
    '--bottom-gauges-image',
    '--mapa-speed-badge-image',
];

/** <img> artwork is tagged with data-accent-artwork="<id>" by the component that builds it. */
const ARTWORK_IMG_IDS = ['dial-ring'];

/** Chroma below this (0-255 max-min) is treated as gray and left untouched. */
const GRAY_CUTOFF = 12;

/** key -> pristine {width, height, data} captured before any recolor. */
let artworkOriginals = null;
/** key -> original url(...) / src string, so the default accent can restore verbatim. */
const artworkSourceUrls = {};
/** key -> recolored data URL currently applied, or undefined when showing the pristine source. */
const artworkRecolored = {};
/** Guards against a slow run landing after a newer pick. */
let artworkRun = 0;

/*
 * hex -> { key: dataUrl }, so flipping back to a color we have already rendered is instant
 * instead of another full pass. This matters because driveModeColors ties the accent to
 * drivingMode, which flips Sport/Eco/Normal while the car is moving.
 *
 * Bounded because each entry holds ~5 base64 surfaces. Nothing to revoke - these are data
 * URLs, so dropping the reference is the whole cleanup.
 */
const artworkCache = new Map();
const ARTWORK_CACHE_LIMIT = 4;

function cacheArtwork(hex, urls) {
    artworkCache.set(hex, urls);
    while (artworkCache.size > ARTWORK_CACHE_LIMIT) {
        artworkCache.delete(artworkCache.keys().next().value);
    }
}

/**
 * Lets a component that builds an <img> ask for the currently applied artwork, so an
 * element created after a recolor (a re-render) still gets the recolored source.
 * Also records the pristine source the first time it is seen.
 */
export function registerArtworkImage(id, originalSrc) {
    if (!artworkSourceUrls[id]) artworkSourceUrls[id] = originalSrc;
    return artworkRecolored[id] || artworkSourceUrls[id];
}

function collectArtworkSources() {
    const computed = getComputedStyle(document.documentElement);
    const sources = {};
    ARTWORK_VARS.forEach((token) => {
        const match = computed.getPropertyValue(token).trim().match(/url\(\s*"?(data:[^")]+)"?\s*\)/);
        if (match) {
            if (!artworkSourceUrls[token]) artworkSourceUrls[token] = match[1];
            sources[token] = artworkSourceUrls[token];
        }
    });
    // Durable breadcrumb: the head unit routes console output to cluster-diagnostics,
    // which is the only way to see what this resolved to on the car.
    const missing = ARTWORK_VARS.filter((token) => !sources[token]);
    if (missing.length) console.warn(`[accent] artwork vars unresolved: ${missing.join(', ')}`);

    ARTWORK_IMG_IDS.forEach((id) => {
        const el = document.querySelector(`[data-accent-artwork="${id}"]`);
        if (!artworkSourceUrls[id] && el) artworkSourceUrls[id] = el.src;
        if (artworkSourceUrls[id]) sources[id] = artworkSourceUrls[id];
    });
    return sources;
}

function loadImage(src) {
    return new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => resolve(img);
        img.onerror = () => reject(new Error('artwork load failed'));
        img.src = src;
    });
}

/**
 * Captures pristine pixels once, so repeated picks never compound.
 *
 * Per-asset try/catch on purpose: one unreadable surface (a tainted canvas, a decode
 * failure) used to reject the whole capture and leave every other asset unrecolored.
 * Better to recolor what we can and report the rest.
 */
async function captureArtworkOriginals() {
    const sources = collectArtworkSources();
    const originals = {};
    const failed = [];
    for (const key of Object.keys(sources)) {
        try {
            originals[key] = await captureOne(sources[key]);
        } catch (err) {
            failed.push(`${key}(${err && err.name ? err.name : err})`);
        }
    }
    if (failed.length) console.warn(`[accent] artwork capture failed for: ${failed.join(', ')}`);
    console.warn(`[accent] artwork captured ${Object.keys(originals).length}/${Object.keys(sources).length}`);
    return originals;
}

async function captureOne(src) {
    const img = await loadImage(src);
    const canvas = document.createElement('canvas');
    canvas.width = img.naturalWidth;
    canvas.height = img.naturalHeight;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    ctx.drawImage(img, 0, 0);
    return ctx.getImageData(0, 0, canvas.width, canvas.height);
}

/**
 * Rotates one image's colored pixels in place.
 *
 * Unlike the CSS tokens, pixels get hue and saturation only - no lightness shift. The
 * artwork's luminance structure *is* the artwork (glow falloff, shading), and dragging
 * it toward a light or dark pick would wash the whole panel out rather than recolor it.
 */
function rotatePixels(source, rotate) {
    const src = source.data;
    const out = new ImageData(source.width, source.height);
    const dst = out.data;
    const cache = new Map();

    for (let i = 0; i < src.length; i += 4) {
        const r = src[i], g = src[i + 1], b = src[i + 2];
        dst[i + 3] = src[i + 3];

        const max = r > g ? (r > b ? r : b) : (g > b ? g : b);
        const min = r < g ? (r < b ? r : b) : (g < b ? g : b);

        // Grays carry no hue to rotate - the overwhelming majority of these images.
        if (max - min < GRAY_CUTOFF) {
            dst[i] = r; dst[i + 1] = g; dst[i + 2] = b;
            continue;
        }

        // These are near-monochrome glows, so the same few thousand colors repeat across
        // millions of pixels; memoizing keeps the OKLCH round trip off the hot path.
        const key = (r << 16) | (g << 8) | b;
        let mapped = cache.get(key);
        if (mapped === undefined) {
            mapped = rotate({ r, g, b });
            cache.set(key, mapped);
        }
        dst[i] = mapped.r; dst[i + 1] = mapped.g; dst[i + 2] = mapped.b;
    }
    return out;
}

/*
 * WebP rather than PNG: measured on this theme's largest asset (1586x992), PNG encoding
 * costs ~1050 ms against ~225 ms for WebP q0.92, and five assets on the PNG path pushed a
 * color change to nearly six seconds. WebP keeps alpha exactly (verified: max alpha error
 * 0) and the lossy term is negligible on these soft glows - mean channel error 0.16/255.
 */
/*
 * Emits a data: URL, NOT a blob: URL.
 *
 * The head unit serves the theme from file:///android_asset/, and blob: URLs minted in a
 * file:// origin are unreliable in that WebView - the symptom on the car was the artwork
 * tinting (the CSS filter landing) and then snapping straight back to blue as the recolored
 * source failed to apply. data: URLs have no origin story at all, so they always apply.
 *
 * This used to be prohibitively expensive, but only because of PNG: the same surface is
 * ~250 KB as PNG against ~16 KB as WebP, so base64 now costs about 21 KB per asset. It also
 * removes the whole object-URL lifetime problem - nothing to revoke, nothing to leak.
 *
 * toBlob + FileReader rather than toDataURL, because toDataURL encodes synchronously and
 * would stall the cluster for ~1 s across five surfaces.
 */
function toDataUrl(imageData) {
    const canvas = document.createElement('canvas');
    canvas.width = imageData.width;
    canvas.height = imageData.height;
    canvas.getContext('2d').putImageData(imageData, 0, 0);

    const encode = (type) => new Promise((resolve) => canvas.toBlob(resolve, type, 0.92));
    const asDataUrl = (blob) => new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(new Error('data url encode failed'));
        reader.readAsDataURL(blob);
    });

    return encode('image/webp')
        // Older WebViews ignore the type hint and hand back PNG, which is still correct.
        .then((blob) => blob || encode('image/png'))
        .then(asDataUrl);
}

function applyArtworkUrl(key, url) {
    if (url !== artworkSourceUrls[key]) artworkRecolored[key] = url;
    else delete artworkRecolored[key];

    if (key.startsWith('--')) {
        document.documentElement.style.setProperty(key, `url("${url}")`);
    } else {
        document.querySelectorAll(`[data-accent-artwork="${key}"]`).forEach((el) => { el.src = url; });
    }
}

/** Marks the artwork as carrying the rotation, so the CSS approximation stands down. */
function clearArtworkFilter() {
    document.documentElement.style.setProperty('--accent-hue-rotate', '0deg');
    document.documentElement.style.setProperty('--accent-saturate', '1');
}

async function recolorArtwork(hex, rotate, identity) {
    const run = ++artworkRun;
    try {
        // Already rendered this color once - reapply the stored blobs and skip the work.
        const cached = artworkCache.get(hex);
        if (cached) {
            Object.entries(cached).forEach(([key, url]) => applyArtworkUrl(key, url));
            clearArtworkFilter();
            return;
        }

        if (!artworkOriginals) artworkOriginals = await captureArtworkOriginals();
        if (run !== artworkRun) return;

        /*
         * An empty capture used to be silently fatal: the loop below did nothing, then
         * clearArtworkFilter() stripped the CSS approximation too, so the artwork sat at
         * its shipped blue with no error anywhere. Worse, `{}` is truthy, so it stuck for
         * the rest of the session and every later pick was a no-op.
         *
         * Bail with the filter still applied (approximate, but visibly tinted) and null
         * the cache so the next pick re-captures - by then the stylesheet or the dial
         * <img> that was missing has usually arrived.
         */
        if (!Object.keys(artworkOriginals).length) {
            artworkOriginals = null;
            console.warn('[accent] no artwork sources captured; keeping CSS filter, will retry');
            return;
        }

        const rendered = {};
        for (const key of Object.keys(artworkOriginals)) {
            const url = identity
                ? artworkSourceUrls[key]
                : await toDataUrl(rotatePixels(artworkOriginals[key], rotate));
            if (run !== artworkRun) return;
            rendered[key] = url;
            applyArtworkUrl(key, url);
        }
        cacheArtwork(hex, rendered);

        // The pixels now carry the rotation, so the CSS approximation MUST go to identity.
        // Leaving it at hueDelta rotates everything a second time (red -> pink/orange).
        clearArtworkFilter();
    } catch (err) {
        // Leave the CSS filter in place as the fallback - approximate, but not blue.
        console.warn('[accent] artwork recolor failed, keeping CSS filter', err);
    }
}

/* ----------------------------------------------------------------- public */

/**
 * Recolors the whole accent palette to follow `hex`.
 *
 * Sets --accent-hue-rotate / --accent-saturate, which the stylesheet feeds into `filter:`
 * on the baked-in artwork, then kicks off the precise pixel recolor that supersedes it.
 *
 * @param {string} hex - "#RRGGBB" (or any notation parseColor understands).
 */
export function applyAccent(hex) {
    const target = parseColor(hex) || parseColor(DEFAULT_ACCENT);

    // Normalize to the resolved rgb so "#f00" and "#FF0000" are one key, then bail if
    // nothing actually changed. drivingMode pushes land here on every mode flip, and a
    // repeat pick would otherwise restart the whole artwork pass for no visible gain.
    const targetKey = `${target.r},${target.g},${target.b}`;
    if (targetKey === lastAppliedAccent) return;
    lastAppliedAccent = targetKey;

    const reference = rgbToHsl(parseColor(REFERENCE_ACCENT));
    const picked = rgbToHsl(target);

    if (!capturedBases) {
        const captured = captureBases();
        capturedBases = captured.bases;
        capturedRgbBases = captured.rgbBases;
    }

    // Hue/saturation deltas here drive only the CSS `filter:` approximation on the
    // artwork, which lives in its own color space. The palette itself uses OKLCH below.
    const hueDelta = picked.h - reference.h;
    const satScale = Math.min(1.6, picked.s / (reference.s || 1));

    const rotate = makeOklchRotator(parseColor(REFERENCE_ACCENT), target);
    const root = document.documentElement;

    Object.keys(capturedBases).forEach((token) => {
        const base = capturedBases[token];
        const out = rotate(base);
        root.style.setProperty(token, rgbaString(out, base.a));
    });
    Object.keys(capturedRgbBases).forEach((token) => {
        const out = rotate(capturedRgbBases[token]);
        root.style.setProperty(token, `${out.r}, ${out.g}, ${out.b}`);
    });

    // Immediate, approximate: the artwork tracks the pick on the very next frame.
    root.style.setProperty('--accent-hue-rotate', `${Math.round(hueDelta * 10) / 10}deg`);
    root.style.setProperty('--accent-saturate', String(Math.round(satScale * 100) / 100));

    // Precise: recolors the pixels with the same OKLCH mapping the tokens just got, then
    // zeroes the filter above. Deliberately not awaited - the CSS palette must not wait
    // on a ~5 MP decode to reach the screen.
    recolorArtwork(targetKey, rotate, Math.abs(hueDelta) < 0.5 && Math.abs(satScale - 1) < 0.02);
}
