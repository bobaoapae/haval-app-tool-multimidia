# Projector Flow

Atualizado em: 2026-05-31

## O Que Foi Identificado

`BaseProjector` estende `Presentation` e oferece `ensureUi`. Existem duas implementações:

- `InstrumentProjector`: camada transparente simples no display 1.
- `InstrumentProjector2`: camada principal no display 3 com WebView e bridge.

`ProjectorManager.initialize()` cria presentations para displays configurados e registra listener para displays ausentes.

Em 2026-05-31, a inicializacao do `ProjectorManager` passou a ser postada na main thread a partir
do `ServiceManager`. `Presentation` e `WebView` precisam nascer no Looper principal; inicializar a
partir de thread de bootstrap pode travar/crashar a UI.

## Eventos Relevantes

- `CAR_BASIC_ENGINE_STATE` desliga ou religa visibilidade dos projectors.
- `DISPLAY_3_APP_STATE_CHANGED` e `DISPLAY_1_APP_STATE_CHANGED` alteram visibilidade e sync.
- `CLUSTER_CARD_CHANGED` sincroniza card atual e apps no display.

## Arquivos Relacionados

- `BaseProjector.kt`
- `InstrumentProjector.kt`
- `InstrumentProjector2.kt`
- `ProjectorManager.java`
- `ServiceManagerEventType.java`

## Riscos

- Presentation criada com contexto errado pode vazar ou não renderizar.
- Presentation/WebView criada fora da main thread pode falhar em runtime.
- Remover listeners incorretamente pode deixar callbacks vivos.
- Alterações em visibilidade podem cobrir ou esconder projeções.

## A Confirmar

- Todos os caminhos que chamam `ProjectorManager.refresh()`.
