const fs = require('fs');
const path = require('path');

const cssPath = path.join(__dirname, 'src/styles/night.style.css');
let css = fs.readFileSync(cssPath, 'utf8');

const colorMap = {
  // Base
  "#000000": "--bg-dark",
  "#000": "--bg-dark",
  "#ffffff": "--text-main",
  "#FFFFFF": "--text-main",
  "#fff": "--text-main",
  "#FFF": "--text-main",
  "rgba(0, 0, 0, 0)": "--color-transparent",
  "#00000000": "--color-transparent",

  // Blues
  "#3b82f6": "--primary-blue",
  "#60a5fa": "--blue-400",
  "#2563eb": "--blue-600",
  "#1d4ed8": "--blue-700",
  "#0051ff": "--color-needle",
  "#006dff": "--color-ac-blue",
  "#053477": "--color-ac-dark-blue",
  "#4d97ff": "--color-temp",
  "#afd3ff": "--text-light-blue",
  "#00A0FF": "--color-ring-glow",
  "#4a90e2": "--color-ring-accent",

  // Semantics
  "#ff4500": "--color-power-off",
  "#ff0000": "--color-danger",
  "#FF4D4D": "--color-red-light",
  "#ff3b30": "--color-red-alt",
  "#3bf676": "--color-success",
  "#4CAF50": "--color-green-main",
  "#00ff88": "--color-green",
  "#10b981": "--color-emerald",
  "#4cd964": "--color-green-alt",
  "#ff5500": "--color-orange",
  "#ff9500": "--color-orange-alt",
  "#00b7ff": "--color-cyan",
  "#00c3ff": "--color-light-cyan",

  // Grays
  "#9ca3af": "--text-gray",
  "#6b7280": "--gray-500",

  // Special Hex
  "#0a2345": "--menu-bg",
  "#A3AED0": "--menu-text",
  "#0a2345": "--menu-bg",
  "#edbba8": "--color-power-glow",
  "#9ac2ff": "--color-impulse",
  "#0074ff44": "--color-ring-bg",
  "#e7ebff": "--color-light-bg",
  "#00aeff85": "--color-cyan-glow",
  "#00ff8885": "--color-green-glow",
  "#6477ff85": "--dial-shadow-color",
  "#006aff20": "--color-blue-glow-20",
  "#4d97ff6b": "--color-ac-divider",

  // Blacks Alpha
  "rgba(0, 0, 0, 1)": "--bg-black-100",
  "rgba(0, 0, 0, 0.8)": "--bg-mask-80",
  "rgba(0, 0, 0, .8)": "--bg-black-80",
  "rgba(0, 0, 0, 0.7)": "--bg-black-70",
  "rgba(0, 0, 0, 0.6)": "--bg-black-60",
  "rgba(0, 0, 0, .6)": "--bg-black-60",
  "rgba(0, 0, 0, 0.5)": "--bg-black-50",
  "rgba(0, 0, 0, .5)": "--bg-black-50",
  "rgb(0 0 0 / 50%)": "--shadow-black-50",
  "rgba(0, 0, 0, 0.3)": "--bg-black-30",
  "rgba(0, 0, 0, .2)": "--bg-black-20",
  "rgba(0, 0, 0, 0.1)": "--shadow-black-10",
  "rgba(0, 0, 0, 0.05)": "--shadow-black-5",

  // Whites Alpha
  "rgba(255, 255, 255, 0.8)": "--color-white-80",
  "rgba(255, 255, 255, 0.3)": "--color-white-30",
  "rgba(255, 255, 255, 0.2)": "--color-white-20",
  "rgba(255, 255, 255, 0.15)": "--color-white-15",

  // Blues Alpha
  "rgba(59, 130, 246, 0.5)": "--accent-glow",
  "rgba(160, 180, 255, .6)": "--dial-gradient-blue",
  "rgba(160, 180, 255, 0.5)": "--dial-border",
  "rgba(48, 128, 255, 0.3)": "--shadow-blue-30",
  "rgba(29, 78, 216, 0.8)": "--glow-blue-80",
  "rgba(29, 78, 216, 0.7)": "--glow-blue-70",
  "rgba(29, 78, 216, 0.6)": "--glow-blue-60",
  "rgba(29, 78, 216, 0.4)": "--glow-blue-40",
  "rgba(29, 78, 216, 0.2)": "--glow-blue-20",
  "rgb(85, 128, 165, 0.8)": "--color-tick-blue",
  "rgba(0, 120, 255, 0.2)": "--color-blue-20",
  "rgba(0, 0, 255, 0.8)": "--color-blue-dark-80",
  "rgba(0, 160, 255, 0.7)": "--color-ring-blue-70",
  "rgba(0, 195, 255, 0.2)": "--color-cyan-20",
  "rgba(0, 120, 255, 0.3)": "--color-blue-30",
  "rgba(205, 216, 255, 0.2)": "--color-purple-20",
  "rgba(160, 180, 255, 0.2)": "--color-dial-blue-20",
};

// First, strip out the old :root variable area safely to prevent duplicating existing var declarations
const originalCss = css;

css = css.replace(/:root\s*\{[^}]+\}/, `/* _OLD_ROOT_REPLACED_ */`);

let newRoot = `/* ==========================================================================
   THEME CONFIGURATION
   ==========================================================================
   This section contains all color and layout variables. To create a new theme
   (e.g., light.style.css), simply duplicate this file and modify the values 
   in this :root section.
========================================================================== */

:root {
  /* --- 1. BASE COLORS --- */
  --bg-dark: #000000;
  --text-main: #ffffff;
  --color-transparent: rgba(0, 0, 0, 0);

  /* --- 2. PRIMARY & BRAND COLORS (Blues) --- */
  --primary-blue: #3b82f6;
  --blue-400: #60a5fa;
  --blue-600: #2563eb;
  --blue-700: #1d4ed8;
  --color-needle: #0051ff;
  --color-ac-blue: #006dff;
  --color-ac-dark-blue: #053477;
  --color-temp: #4d97ff;
  --text-light-blue: #afd3ff;
  --color-ring-glow: #00A0FF;
  --color-ring-accent: #4a90e2;
  --text-glow-blue: #00beff;

  /* --- 3. SEMANTIC COLORS (Success, Warning, Danger) --- */
  --color-success: #3bf676;
  --color-green-main: #4CAF50;
  --color-green: #00ff88;
  --color-emerald: #10b981;
  --color-green-alt: #4cd964;
  --color-danger: #ff0000;
  --color-red-light: #FF4D4D;
  --color-red-alt: #ff3b30;
  --color-power-off: #ff4500;
  --color-orange: #ff5500;
  --color-orange-alt: #ff9500;
  --color-cyan: #00b7ff;
  --color-light-cyan: #00c3ff;

  /* --- 4. GRAYS --- */
  --text-gray: #9ca3af;
  --gray-500: #6b7280;

  /* --- 5. COMPONENT SPECIFIC COLORS --- */
  --menu-bg: #0a2345;
  --menu-text: #A3AED0;
  --color-power-glow: #edbba8;
  --color-impulse: #9ac2ff;
  --color-ring-bg: #0074ff44;
  --color-light-bg: #e7ebff;
  --color-cyan-glow: #00aeff85;
  --color-green-glow: #00ff8885;
  --dial-shadow-color: #6477ff85;
  --color-blue-glow-20: #006aff20;
  --color-ac-divider: #4d97ff6b;

  /* --- 6. SHADOWS & GLOWS (Alpha Values) --- */
  /* Black/Shadows */
  --bg-black-100: rgba(0, 0, 0, 1);
  --bg-mask-80: rgba(0, 0, 0, 0.8);
  --bg-black-80: rgba(0, 0, 0, 0.8);
  --bg-black-70: rgba(0, 0, 0, 0.7);
  --bg-black-60: rgba(0, 0, 0, 0.6);
  --bg-black-50: rgba(0, 0, 0, 0.5);
  --shadow-black-50: rgb(0 0 0 / 50%);
  --bg-black-30: rgba(0, 0, 0, 0.3);
  --bg-black-20: rgba(0, 0, 0, 0.2);
  --shadow-black-10: rgba(0, 0, 0, 0.1);
  --shadow-black-5: rgba(0, 0, 0, 0.05);

  /* White Alphas */
  --color-white-80: rgba(255, 255, 255, 0.8);
  --color-white-30: rgba(255, 255, 255, 0.3);
  --color-white-20: rgba(255, 255, 255, 0.2);
  --color-white-15: rgba(255, 255, 255, 0.15);

  /* Blue Alphas */
  --accent-glow: rgba(59, 130, 246, 0.5);
  --dial-gradient-blue: rgba(160, 180, 255, 0.6);
  --dial-border: rgba(160, 180, 255, 0.5);
  --shadow-blue-30: rgba(48, 128, 255, 0.3);
  --glow-blue-80: rgba(29, 78, 216, 0.8);
  --glow-blue-70: rgba(29, 78, 216, 0.7);
  --glow-blue-60: rgba(29, 78, 216, 0.6);
  --glow-blue-40: rgba(29, 78, 216, 0.4);
  --glow-blue-20: rgba(29, 78, 216, 0.2);
  --color-tick-blue: rgb(85, 128, 165, 0.8);
  --color-blue-20: rgba(0, 120, 255, 0.2);
  --color-blue-dark-80: rgba(0, 0, 255, 0.8);
  --color-ring-blue-70: rgba(0, 160, 255, 0.7);
  --color-cyan-20: rgba(0, 195, 255, 0.2);
  --color-blue-30: rgba(0, 120, 255, 0.3);
  --color-purple-20: rgba(205, 216, 255, 0.2);
  --color-dial-blue-20: rgba(160, 180, 255, 0.2);

  /* --- 7. MASK & DIAL LAYOUT --- */
  --mask-radius: 228px;
  --mask-center-y: 430px;
  --mask-left-x: 290px;
  --mask-right-x: 1630px;
  --mask-bg-color: var(--bg-mask-80);
  --mask-border-color: var(--primary-blue);
  --mask-shadow: 0 0 60px var(--bg-black-100);
  
  --dial-inner-bg: radial-gradient(circle, var(--bg-black-80) 0%, var(--bg-black-60) 80%, var(--dial-gradient-blue) 100%);
  --dial-inner-border: var(--dial-border);
  --dial-shadow: 0 0 50px 10px var(--dial-shadow-color);
  
  --menu-bg-gradient: radial-gradient(circle at center, var(--menu-bg) 0%, var(--color-transparent) 80%);
  --dashboard-text-color: var(--text-main);

  /* --- 8. DISPLAY MODES --- */
  --display-scale: 1;
  --display-left-offset: 0px;
  --display-right-offset: 0px;
  --display-opacity: 1;
}
`;

// Sort keys by length descending so that we replace longer matches first
// (e.g. rgba(255, 255, 255, 0.8) before #fff)
const sortedKeys = Object.keys(colorMap).sort((a, b) => b.length - a.length);

for (const key of sortedKeys) {
  // Regex to match exact color (case insensitive if hex)
  // For rgba/rgb/hsl avoid matching inside another string
  let pattern;
  if (key.startsWith('#')) {
    pattern = new RegExp(key + '(?![0-9a-fA-F])', 'gi'); // Note: 'i' for case-insensitive hex
  } else {
    // Escape parens
    const escaped = key.replace(/[()]/g, '\\$&').replace(/\s+/g, '\\s*');
    pattern = new RegExp(escaped, 'g');
  }
  css = css.replace(pattern, `var(${colorMap[key]})`);
}

// Ensure the new :root is at the top (after optional @import)
css = css.replace(`/* _OLD_ROOT_REPLACED_ */`, '');

// If there are other :root blocks that got left over, remove them by regex
css = css.replace(/:root\s*\{\s*\}/g, '');

const finalCss = newRoot + '\n\n/* ==========================================================================\n   1. RESETS & UTILITIES\n   ========================================================================== */\n' + css;

fs.writeFileSync(cssPath, finalCss, 'utf8');
console.log('CSS Colors refactored successfully.');
