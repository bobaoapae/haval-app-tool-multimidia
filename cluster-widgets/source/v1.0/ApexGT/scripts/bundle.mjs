import fs from 'node:fs';
import path from 'node:path';

const stylesheets = [
    'assets/fonts.css',
    'src/apex-gt.css',
    'src/apex-menus.css',
    'src/vector-display.css',
    'src/apex-projection.css',
];

const images = [
    ['assets/apex-gt-frame.webp', 'image/webp'],
    ['assets/vector-frame.webp', 'image/webp'],
];

const scripts = [
    'src/gauge-geometry.js',
    'src/gauge-scales.js',
    'src/gauge-motion.js',
    'src/vector-geometry.js',
    'src/vector-gauges.js',
    'src/apex-display.js',
    'src/apex-menus.js',
    'src/shared-runtime.js',
    'src/apex-speed.js',
    'src/apex-projection.js',
    'src/apex-gt.js',
];

export function renderBundle(root) {
    let html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');

    for (const file of stylesheets) {
        const css = fs.readFileSync(path.join(root, file), 'utf8');
        html = html.replace(
            `<link rel="stylesheet" href="./${file}">`,
            () => `<style>\n${css}\n</style>`,
        );
    }

    for (const [file, mime] of images) {
        const source = `src="./${file}"`;
        const dataUrl = `src="data:${mime};base64,${fs.readFileSync(path.join(root, file)).toString('base64')}"`;
        html = html.replace(source, dataUrl);
    }

    for (const file of scripts) {
        const js = fs.readFileSync(path.join(root, file), 'utf8');
        html = html.replace(
            `<script src="./${file}" defer></script>`,
            () => `<script>\n${js}\n</script>`,
        );
    }

    if (/\b(?:src|href)=["']\.\//.test(html) || /url\(["']?(?:https?:|\.\.)/.test(html)) {
        throw new Error('External bundle dependency');
    }

    return html;
}
