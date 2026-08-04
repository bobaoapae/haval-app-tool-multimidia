# Kotlin JS Bridge

Atualizado em: 2026-08-04

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

- `getInitialClusterDisplay()`
- `getInitialClusterColor()`
- `heartbeat()`
- `setWarningActive(Boolean)`
- `setCardId(Int)`
- `saveSetting(String, String)`

`saveSetting` continua restrito a uma allowlist. Os únicos campos aceitos do frontend são
`currentClusterDisplay` e `currentClusterColor`; qualquer outra chave é ignorada. Os valores de
modo e cor também são normalizados antes de persistir.

## Contrato dos temas Sport

`SportRed` e `SportRedLite` consomem, além das chaves legadas:

- aparência: `colorTheme`, `hideSpeedometerOnMaps` e `v2TripInfo`;
- viagem/TPMS: `tripAvgConsumption`, `tripDriveTime`, `tripOdometer`, `tripAvgSpeed`,
  `tirePressures` e `tireTemperatures`;
- reconhecimento de placa: `speedLimit` e `speedLimitActive`;
- mídia: `nowPlayingTitle`, `nowPlayingArt`, `nowPlayingDurationMs`, `nowPlayingElapsedMs` e
  `nowPlayingPlaying`.

Strings novas são serializadas com JSON quoting. Capa é limitada a 320 px, JPEG 80 e enviada
somente quando a referência muda. TPMS roda a cada 5 s apenas com o recurso habilitado; TSR roda a
cada 1,5 s apenas nos temas Sport. Todos os jobs, callback de mídia e listener de dados são
cancelados no `onStop`.

O valor de TSR vem diretamente do VHAL `557847281`: codigos `1..40` permanecem crus para a
conversao em passos de 5 km/h feita pelo tema, e valores `41..200` ja representam km/h. Ausencia,
zero, `255` e status explicitamente inativo limpam `speedLimit`; uma classe de visibilidade injetada
pelo Android esconde os overlays Sport nesses estados para impedir que o fallback interno de 30
km/h do bundle apareca como dado real.

## Readiness e Reload do WebView

`InstrumentProjector2` trata `onPageStarted`, reload por watchdog e troca de tema como estado
`loading`: `webViewsLoaded=false`, fila pendente antiga descartada e heartbeat renovado. Isso
evita que chamadas como `control(...)`, `updateWarning(...)`, `focus(...)` e `showScreen(...)`
sejam executadas contra uma pagina em reload antes de o modulo JS reinstalar `window.control` e
demais funcoes globais.

Em `onPageFinished`, o heartbeat e renovado antes do sync completo para impedir reload prematuro
do watchdog enquanto o primeiro `window.Android.heartbeat()` ainda nao disparou.

O timer de heartbeat usa `window.__havalHeartbeatTimer`: um timer anterior é cancelado antes de
instalar o novo. No teardown, o timer é removido e `window.cleanup()` é chamado quando existir.
As filas de JS permanecem limitadas a 250 comandos durante loading e são substituídas pelo sync
completo no `onPageFinished`.

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
