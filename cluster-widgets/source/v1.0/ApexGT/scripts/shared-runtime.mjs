import fs from 'node:fs';
import path from 'node:path';

// Código compartilhado pelos temas do contrato v1.0 (Default e Minimalist), em
// cluster-widgets/source/v1.0/shared: o cartão de turn-by-turn e as derivações do carro (a velocidade
// calibrada que bate com o HUD). Esses arquivos são módulos ES; o Apex GT não passa por um empacotador
// de módulos — o bundle só cola scripts clássicos no index.html — então os import/export são
// removidos e tudo entra num único IIFE que expõe window.ApexShared. Uma fonte só: quando o código
// compartilhado mudar, `npm run build` regenera src/shared-runtime.{js,css} e o check-package acusa
// se o gerado ficou para trás.
// `only` recorta funções específicas: carDerivations.js traz também o redutor de gráficos com chaves
// legadas (evPowerKw etc.) que o pacote do Apex GT não admite; só a calibração de velocidade entra.
const SOURCES = [
    { file: '../shared/car/carDerivations.js', only: ['getAdjustedSpeed'] },
];
const EXPORTS = [
    'getAdjustedSpeed',
];

function stripModuleSyntax(source) {
    return source
        .replace(/^import[\s\S]*?from\s+['"][^'"]+['"];[ \t]*$/gm, '')
        .replace(/^export\s+(?=(?:async\s+)?function\b|const\b|let\b|var\b|class\b)/gm, '');
}

// Uma função exportada de nível superior, do `export function nome(` até a chave de fechamento na
// coluna zero. Falha alto se a fonte compartilhada mudar de forma, em vez de gerar um bundle quebrado.
function pickFunction(source, name, file) {
    const match = new RegExp(`^export function ${name}\\([\\s\\S]*?^\\}$`, 'm').exec(source);
    if (!match) throw new Error(`${file}: function ${name} not found`);
    return match[0];
}

export function renderSharedRuntimeScript(root) {
    const parts = SOURCES.map(({ file, only }) => {
        const source = fs.readFileSync(path.resolve(root, file), 'utf8');
        const selected = only ? only.map((name) => pickFunction(source, name, file)).join('\n\n') : source;
        const label = file.replace(/^\.\.\//, 'source/v1.0/') + (only ? ` (${only.join(', ')})` : '');
        return `// ---- ${label} ----\n${stripModuleSyntax(selected).trim()}`;
    });
    return [
        '// GERADO por scripts/shared-runtime.mjs a partir de cluster-widgets/source/v1.0/shared — não edite à mão.',
        '// Cartão de turn-by-turn e derivações do carro compartilhados com Default e Minimalist; `npm run build` regenera.',
        '(function () {',
        '"use strict";',
        ...parts,
        `window.ApexShared = { ${EXPORTS.map((name) => `${name}: ${name}`).join(', ')} };`,
        '})();',
        '',
    ].join('\n');
}

