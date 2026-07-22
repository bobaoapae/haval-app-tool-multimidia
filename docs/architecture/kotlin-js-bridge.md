# Kotlin JS Bridge

Atualizado em: 2026-06-12

## Android Para JavaScript

`InstrumentProjector2` envia comandos para JS com `evaluateJavascript`, normalmente via:

- `evaluateJsIfReady`
- `batchEvaluateJs`
- `updateValuesWebView`
- listeners de `ServiceManager`

O padrão principal é:

```text
control('nomeDaChave', valor)
```

Também existem chamadas para:

- `showScreen(...)`
- `focus(...)`
- `updateWarning(...)`
- `clearWarnings()`

## Politica de Warnings

`InstrumentProjector2` mantém uma separação entre warning visual e warning crítico:

- chaves visuais (`car.ipk_info.warning_tts_notify`,
  `car.ipk_info.bsd_lca_warning_reqleft`, `car.ipk_info.bsd_lca_warning_reqright`) continuam sendo
  enviadas ao frontend por `updateWarning(...)`;
- essas chaves não disparam `syncInitialWarnings()`, `isWarningActive`, dismiss crítico nem
  recomputação de visibilidade do cluster;
- warnings críticos continuam podendo acionar `updateWarningUI(...)` via bridge
  `window.Android.setWarningActive(...)`.

O objetivo é preservar o contrato do frontend, que já tratava essas chaves como visual-only, sem
deixar pulsos nativos de TTS/LCA entrarem no caminho pesado de warning e card flow.

## JavaScript Para Android

`addJavascriptInterface(WebAppInterface(), "Android")` expõe:

- `heartbeat()`
- `setWarningActive(Boolean)`
- `setCardId(Int)`
- `saveSetting(String, String)`

## Readiness e Reload do WebView

`InstrumentProjector2` trata `onPageStarted`, reload por watchdog e troca de tema como estado
`loading`: `webViewsLoaded=false`, fila pendente antiga descartada e heartbeat renovado. Isso
evita que chamadas como `control(...)`, `updateWarning(...)`, `focus(...)` e `showScreen(...)`
sejam executadas contra uma pagina em reload antes de o modulo JS reinstalar `window.control` e
demais funcoes globais.

Em `onPageFinished`, o heartbeat e renovado antes do sync completo para impedir reload prematuro
do watchdog enquanto o primeiro `window.Android.heartbeat()` ainda nao disparou.

## Arquivos Relacionados

- `InstrumentProjector2.kt`
- `cluster-widgets/default/src/core/main.js`
- `cluster-widgets/default/src/core/components/warningHandler.js`
- `cluster-widgets/default/src/core/components/display/themeSelection.js`

## Riscos

- Strings sem escape podem quebrar JS.
- `value.toDoubleOrNull()` em `batchEvaluateJs` é heurística simples.
- Chamadas antes de load precisam entrar em fila.
- Loops de warning podem gerar CPU alta se não houver guard.

## A Confirmar

- Se há contrato formal de todas as chaves `control`.
- Se há testes automatizados para bridge.
