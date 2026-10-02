# Projector Flow

Atualizado em: 2026-09-24

## O Que Foi Identificado

`BaseProjector` estende `Presentation` e oferece `ensureUi`. Existem duas implementações:

- `InstrumentProjector`: camada transparente simples no display 1 (wallpaper / HUD refresh).
- `InstrumentProjector2`: camada principal no display 3 com WebView, bridge e native masks (insets).

`ProjectorManager.initialize()` cria presentations para displays configurados e registra listener para displays ausentes.
A ordem de criação é **D1 antes de D3** (`LinkedHashMap`), para o wallpaper existir antes das máscaras compostas.

Em 2026-05-31, a inicializacao do `ProjectorManager` passou a ser postada na main thread a partir
do `ServiceManager`. `Presentation` e `WebView` precisam nascer no Looper principal; inicializar a
partir de thread de bootstrap pode travar/crashar a UI.

## Sync D1 wallpaper ↔ D3 insets

As máscaras nativas do D3 compõem o mesmo wallpaper still do D1. Sem coordenação, o D3 pode mostrar
insets com textura enquanto o D1 ainda está vazio.

`ClusterBackgroundSync` concentra:

- identidade do wallpaper atual (prefs `enable|type|value|activeTheme`);
- sinal `markD1Ready` / `markD1NotReady` emitido por `InstrumentProjector`;
- gate `shouldHoldNativeMasks` usado por `InstrumentProjector2.updateNativeMaskViews` (além do gate
  `isThemeLiveOnDisplay3`);
- resolução compartilhada do still (THEME/FILE/PRESET/COLOR/IMAGE_URL) para os dois lados não
  divergirem (ex.: fallback `car-bg.png` só no D3).

O hold **não** se aplica quando o D1 não está attached, quando um app cobre o D1 de propósito, ou
quando não há still esperado (desligado, WEB_URL, THEME sem wallpaper).

## Eventos Relevantes

- `CAR_BASIC_ENGINE_STATE` desliga ou religa visibilidade dos projectors.
- `DISPLAY_3_APP_STATE_CHANGED` e `DISPLAY_1_APP_STATE_CHANGED` alteram visibilidade e sync.
- `CLUSTER_CARD_CHANGED` sincroniza card atual e apps no display.
- Listener de `ClusterBackgroundSync` re-executa `updateNativeMaskViews` quando o D1 fica ready.

## Arquivos Relacionados

- `BaseProjector.kt`
- `InstrumentProjector.kt`
- `InstrumentProjector2.kt`
- `ClusterBackgroundSync.kt`
- `ProjectorManager.java`
- `ServiceManagerEventType.java`

## Riscos

- Presentation criada com contexto errado pode vazar ou não renderizar.
- Presentation/WebView criada fora da main thread pode falhar em runtime.
- Remover listeners incorretamente pode deixar callbacks vivos.
- Alterações em visibilidade podem cobrir ou esconder projeções.
- Se o D1 falhar ao pintar e nunca marcar ready, as máscaras do D3 ficam down enquanto still wallpaper
  for esperado — comportamento intencional para evitar insets framing nothing.

## A Confirmar

- Todos os caminhos que chamam `ProjectorManager.refresh()`.
