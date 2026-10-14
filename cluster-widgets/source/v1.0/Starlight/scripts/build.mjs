import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { transformSync } from 'esbuild';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const published = path.resolve(root, '../../../Themes/v1.0/Starlight');
let html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const resolveSource = (relative) => {
    const resolved = path.resolve(root, relative);
    if (!resolved.startsWith(`${root}${path.sep}`)) throw new Error(`Outside theme: ${relative}`);
    return resolved;
};

const compile = (code) => transformSync(code, {
    target: 'chrome60', loader: 'js', minify: true,
    legalComments: 'inline', charset: 'utf8',
}).code;
const workerDependencies = ['apex-data.js', 'scales.js', 'galaxy-stars.js', 'glass-smoke.js', 'renderer.js'];
const workerEntry = fs.readFileSync(resolveSource('src/material-worker.js'), 'utf8');
const devImports = /\/\/\s*STARLIGHT_DEV_IMPORTS_START[\s\S]*?\/\/\s*STARLIGHT_DEV_IMPORTS_END/g;
if ([...workerEntry.matchAll(devImports)].length !== 1) throw new Error('Worker development import boundary missing');
const workerCode = compile('if(typeof globalThis==="undefined")self.globalThis=self;\n' +
    workerDependencies.map((file) => fs.readFileSync(resolveSource(`src/${file}`), 'utf8')).join('\n') + '\n' +
    workerEntry.replace(devImports, ''));
if (/\bimportScripts\s*\(/.test(workerCode)) throw new Error('Worker bundle contains external imports');

html = html.replace(/<script\b[^>]*\bsrc=["']([^"']+)["'][^>]*>\s*<\/script>/gi, (_, file) => {
    const code = fs.readFileSync(resolveSource(file), 'utf8');
    const compiled = compile(code);
    const workerBootstrap = file === 'src/material-pipeline.js'
        ? `<script>window.StarlightWorkerSource=${JSON.stringify(workerCode).replace(/<\/script/gi, '<\\/script')};</script>\n`
        : '';
    return workerBootstrap + `<script>\n${compiled.replace(/<\/script/gi, '<\\/script')}\n</script>`;
});
html = html.replace(/(<style\b[^>]*>)([\s\S]*?)(<\/style>)/gi, (_, open, css, close) => open +
    css.replace(/url\(["']?([^)'"\s]+)["']?\)/g, (original, file) => {
        if (file.startsWith('data:')) return original;
        const bytes = fs.readFileSync(resolveSource(file));
        const mime = file.endsWith('.ttf') ? 'font/ttf' : file.endsWith('.webp') ? 'image/webp' : 'image/png';
        return `url("data:${mime};base64,${bytes.toString('base64')}")`;
    }) + close);
html = html.replace('</head>', '<script>if(typeof globalThis==="undefined")window.globalThis=window;</script>\n</head>');
const css = [...html.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/gi)].map((match) => match[1]).join('\n');
const externalCss = [...css.matchAll(/url\(\s*["']?([^)'"\s]+)["']?\s*\)/gi)]
    .some((match) => !match[1].startsWith('data:'));
if (externalCss || /<script\b[^>]*\bsrc=|\b(?:src|href)=["'](?:\.\/|\.\.\/|https?:)/i.test(html)) {
    throw new Error('Bundle contains an external dependency');
}

for (const directory of [path.join(root, 'dist'), published]) {
    fs.mkdirSync(directory, { recursive: true });
    fs.writeFileSync(path.join(directory, 'app.html'), html);
}
for (const file of ['theme.xml', 'thumbnail.png']) {
    fs.copyFileSync(path.join(root, file), path.join(published, file));
}
for (const file of fs.readdirSync(path.join(root, 'assets')).filter((name) => name.endsWith('.txt'))) {
    // The host accepts flat packages only and rejects dotfiles. Markdown keeps
    // license contents intact while avoiding the repository's global *.txt rule.
    const destination = `LICENSE-${path.parse(file).name}.md`;
    fs.copyFileSync(path.join(root, 'assets', file), path.join(published, destination));
}
const { version } = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
console.log(`Starlight ${version}: ${Buffer.byteLength(html)} bytes, self-contained, Chrome 60 syntax.\n${published}`);
