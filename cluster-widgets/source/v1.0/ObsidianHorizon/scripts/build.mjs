import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const themeRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const widgetsRoot = path.resolve(themeRoot, "..", "..", "..");
const outputDir = path.join(themeRoot, "dist");
const publishedDir = path.join(widgetsRoot, "Themes", "v1.0", "ObsidianHorizon");

const htmlPath = path.join(themeRoot, "index.html");
const cssPath = path.join(themeRoot, "src", "obsidian-horizon.css");
const jsPath = path.join(themeRoot, "src", "obsidian-horizon.js");
const framePath = path.join(themeRoot, "assets", "obsidian-horizon-frame.webp");

let html = fs.readFileSync(htmlPath, "utf8");
const frameDataUri = `data:image/webp;base64,${fs.readFileSync(framePath).toString("base64")}`;
const css = fs.readFileSync(cssPath, "utf8")
    .replace("../assets/obsidian-horizon-frame.webp", frameDataUri);
const javascript = fs.readFileSync(jsPath, "utf8");

html = html
    .replace('<link rel="stylesheet" href="./src/obsidian-horizon.css">', `<style data-obsidian-horizon-style="1">\n${css}\n</style>`)
    .replace('<script src="./src/obsidian-horizon.js" defer></script>', `<script data-obsidian-horizon-runtime="1">\n${javascript}\n</script>`);

if (/\b(?:src|href)=["']\.\/src\//.test(html)) {
    throw new Error("O app.html ainda possui referencias para arquivos de src/");
}

if (!html.includes("data:image/webp;base64,")) {
    throw new Error("A moldura Base64 nao foi incorporada ao app.html");
}

fs.rmSync(outputDir, { recursive: true, force: true });
fs.mkdirSync(outputDir, { recursive: true });
fs.mkdirSync(publishedDir, { recursive: true });

for (const destination of [
    path.join(outputDir, "app.html"),
    path.join(publishedDir, "app.html")
]) {
    fs.writeFileSync(destination, html, "utf8");
}

for (const fileName of ["theme.xml", "thumbnail.png"]) {
    const source = path.join(themeRoot, fileName);
    if (fs.existsSync(source)) fs.copyFileSync(source, path.join(publishedDir, fileName));
}

console.log(`Obsidian Horizon publicado em ${publishedDir}`);
console.log(`HTML autocontido: ${Buffer.byteLength(html)} bytes`);
