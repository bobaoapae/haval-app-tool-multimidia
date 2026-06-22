# WebView Flow

Atualizado em: 2026-06-15

## Fluxo Identificado

`InstrumentProjector2` cria uma WebView transparente fullscreen no display 3.

1. `onCreate` registra callbacks/listeners e chama `setupControlView`.
2. `setupControlView` configura WebView com JavaScript e DOM storage.
3. `readAppContent` carrega HTML de:
   - `/data/local/tmp/app.html` em app debuggable, se válido;
   - tema customizado em `files/themes`;
   - `R.raw.app` como fallback.
4. `loadDataWithBaseURL` injeta o HTML.
5. `onPageFinished` marca a WebView como carregada, sincroniza estado e injeta heartbeat JS.
6. `evaluateJsIfReady` envia JS imediatamente ou coloca em fila.
7. `WebChromeClient.onConsoleMessage` captura `console.log/warn/error` do WebView e grava eventos
   `webview_console` no log persistente debug-only do dia quando a captura persistente esta ativa.
8. `onStop` remove callbacks/listeners e destrói WebView.

## Arquivos Relacionados

- `InstrumentProjector2.kt`
- `app/src/main/res/raw/app.html`
- `cluster-widgets/default/src/core/main.js`
- `cluster-widgets/default/inline.js`

## Riscos

- Chamar JS antes de load sem fila.
- Recarregar WebView em loop.
- Não destruir WebView em `onStop`.
- HTML em `/data/local/tmp/app.html` inválido afetar debug.
- Console do WebView pode ficar ruidoso em tema com erro em loop; por isso a captura fica restrita
  a builds debug/internal e cada campo e truncado pelo logger persistente.

## A Confirmar

- Política desejada para `WebView.setWebContentsDebuggingEnabled(true)` em release.
- Se todos os temas customizados usam o mesmo contrato JS.
