# Frontend Flow

Updated: 2026-08-06

## Layout

Active contract themes live under `cluster-widgets/source/v1.0/<theme>/`.
Legacy packages remain in `cluster-widgets/source/noncontract/`.
Packaged OTA output is `cluster-widgets/Themes/v1.0/<theme>/`.

Typical theme package:

- `index.html` / entry used by Parcel
- `src/` (JS components, styles)
- `theme.xml` (name, version, `minBridgeVersion`, `contractVersion`, settings)
- `inline.js` + `package.json`
- Build emits a single self-contained `app.html`

See [`themes-contract-v1.md`](themes-contract-v1.md) and [`THEME_GUIDE.md`](../../cluster-widgets/Themes/THEME_GUIDE.md).

## JS contract (v1.0)

- `window.Android.*` — host bridge (`subscribe`, telemetry, prefs, wallpaper, …)
- `window.onKeyEvent(key)` — raw steering keys from the host
- Theme-defined screens/menus — not a fixed Android screen enum

Legacy globals (`window.control`, `window.showScreen`, `window.focus`) may still appear in older packages; do not treat them as the v1.0 authoring model.

## Build

Parcel bundles the theme; `inline.js` produces one HTML with inlined CSS/JS/assets.

- **Default** → APK `res/raw/app.html` + `assets/Default/theme.xml`
- **OTA themes** → `Themes/v1.0/<theme>/` (commit + push to the ThemeManager catalog branch)

## Local Theme Lab

O fluxo local unificado fica na raiz de `cluster-widgets/`:

```bash
cd cluster-widgets
npm install
npm run dev
```

`http://localhost:1234/` descobre os temas pelas pastas canônicas, carrega cada um em um `iframe`
de `1920x720` e mantém a simulação por teclado. Temas v1.0 usam o próprio harness; pacotes legados
recebem adaptadores locais para cards, menus, display, telemetria dinâmica e AC. Painéis avançados
ficam ocultos até o usuário abrir `Aparência`, `Telemetria / AC`, `Atalhos` ou `Testes`.

Todos os previews recebem ainda a mesma camada frontal simulada de `READY`, placa de velocidade e
ESP. Ela usa as coordenadas físicas documentadas no contrato, substitui mocks duplicados e fica
acima do conteúdo do tema sem capturar interação.

Em SportRed/SportRedLite, o Theme Lab oculta especificamente `.g20-v2-native-status-mock`
(`READY`, ESP e placa OEM antigos). `.g20-v2-simulated-tsr`, que representa o TSR temático grande,
permanece ativo e continua recebendo telemetria.

Esses dois pacotes também recebem um polimento estritamente local do serrilhado SVG. O adaptador
preserva espessura, cor, opacidade, brilho e as duas camadas originais; somente substitui as
sequências variáveis de `stroke-dasharray` por cadências uniformes e com o mesmo offset nos dois
lados. O mesmo override posiciona `.g20-v2-speed-readout` e `.g20-v2-power-readout` em
`top: 380px` nos dois Sport, sem mover `.g20-v2-rpm-readout`. Não há novo DOM, `setInterval`,
observer ou animação.

Na faixa inferior do Analógico V2, o adaptador remove as superfícies individuais e deixa
temperaturas e odômetro flutuantes em blocos de `58px`, sem fundo, borda, raio ou pseudo-elemento
próprio. Não há superfície visual, asset raster, brilho, linha ou efeito adicional. O aviso de
manutenção volta ao fluxo normal da segunda linha em vez de depender do deslocamento legado
`top: -20px`. A composição usa somente tipografia estática, sem gradiente CSS, `clip-path`, filtro,
blur, animação ou novo nó no DOM.

O mesmo adaptador desativa os pseudo-elementos decorativos de `.dashboard-top-center` e a sombra
colorida dos seus textos. Horário, marcha e modo de condução mantêm as coordenadas originais, mas
ficam sem cápsula, contorno, fundo ou brilho. A limpeza é CSS estático e exclusiva do simulador.

Para a central, `ProjectionDisplayHtmlPolicy` replica esse CSS no carregamento de `SportRed` e
`SportRedLite`. A injeção ocorre somente em memória, exige o nome e a assinatura estrutural dos
pacotes 0.16.44 conhecidos e não reescreve os HTMLs baixados. Além das duas faixas limpas, ela
mantém velocidade/potência em `top: 380px`, a cadência aprovada do serrilhado e oculta o mock OEM
duplicado. Não há mudança de bridge, contrato, DOM, listener, timer, animação ou filtro.

O Theme Lab é somente de desenvolvimento: não entra em `res/raw`, assets do APK ou pacotes OTA e
não altera resolução, bridge ou contrato.

## Related paths

- `cluster-widgets/source/v1.0/default/`
- `cluster-widgets/source/v1.0/minimalist/`
- `cluster-widgets/source/v1.0/shared/`
- `cluster-widgets/Themes/v1.0/`

## Risks

- Renaming bridge globals breaks the host contract.
- Default build that skips APK copy leaves the car on stale HTML.
- OTA build that skips `Themes/v1.0/` never reaches the in-app catalog.
- Simulator-only CSS must stay gated out of production bundles.
