# Frontend Flow

Atualizado em: 2026-05-24

## O Que Foi Identificado

O frontend dos widgets fica em `cluster-widgets/`. O tema Default atual está em `cluster-widgets/default`.

Cada tema contém:

- `index.html`
- `src/core/main.js`
- `src/core/state.js`
- `src/core/components/`
- `src/styles/`
- `inline.js`
- `package.json`

O build usa Parcel e depois `inline.js` para gerar HTML único com assets inlined.

## Contrato JS

- `window.control(key, value)`: recebe dados Android e atualiza estado.
- `window.focus(target)`: altera foco de menu/AC/display.
- `window.showScreen(screenName)`: alterna tela.
- `window.cleanup()`: cleanup chamado quando disponível.
- `window.Android`: bridge para Android quando WebView real existe.

## Arquivos Relacionados

- `cluster-widgets/default/src/core/main.js`
- `cluster-widgets/default/src/core/state.js`
- `cluster-widgets/default/src/styles/night.style.css`
- `cluster-widgets/default/src/core/components/`

## Riscos

- Alterar nomes globais quebra bridge.
- CSS de simulador pode vazar para produção se gating falhar.
- Build de tema pode não refletir no APK se `app.html` não for atualizado.

## A Confirmar

- Qual tema é fonte principal para produção em cada versão.
- Se `air-control` ainda é usado como tema ativo ou legado.
