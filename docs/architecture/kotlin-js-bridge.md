# Kotlin JS Bridge

Atualizado em: 2026-05-24

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

## JavaScript Para Android

`addJavascriptInterface(WebAppInterface(), "Android")` expõe:

- `heartbeat()`
- `setWarningActive(Boolean)`
- `setCardId(Int)`
- `saveSetting(String, String)`

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
