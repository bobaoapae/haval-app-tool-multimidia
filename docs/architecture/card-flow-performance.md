# Card Flow Performance

Atualizado em: 2026-08-01

## Problema

O evento `CLUSTER_CARD_CHANGED` acumulou responsabilidades demais:

- navegacao do card no WebView;
- protecao de classes de projecao D3 antes de `cardId`;
- permissao temporizada de overlay de `main_menu`/AC durante projecao;
- calculo de cobertura `appInDash`;
- sincronizacao/resize de apps em display secundario;
- resync completo de telemetria do cluster.

Esse acoplamento torna um toque simples no volante dependente de consultas caras como
`am stack list` via Shizuku e de multiplas chamadas Android -> JS. Em hardware embarcado, isso pode
transformar transicoes AC/MainMenu em atrasos perceptiveis.

## Direcao Arquitetural

O fluxo de card deve ser dividido em dois caminhos:

1. **Fast path de navegacao**
   - usado quando nao ha projecao ativa, estado de projecao stale ou app secundario gerenciado que
     precise mudar bounds;
   - envia `control('cardId', ...)`;
   - sincroniza apenas os valores minimos da tela visivel (`main_menu` ou `aircon`);
   - nao consulta `am stack list`;
   - nao limpa cache de configs/bounds;
   - nao executa resync completo de telemetria.

2. **Protected path D3/display**
   - usado quando ha CarPlay/Android Auto/preparacao D3, estado de projecao stale ou app
     secundario gerenciado com mudanca de bounds;
   - preserva a regra de enviar estado de projecao para JS antes de `cardId`;
   - recalcula `appInDash` e visibilidade somente quando necessario;
   - sincroniza apps em display secundario apenas quando o card pode alterar bounds.

## Estrutura

- `ClusterCardIds`: IDs centrais do carrossel (`0` neutro, `1` MainMenu, `3` AC).
- `ClusterCardFlowPolicy`: politica pura e testavel que decide o trabalho de cada card change.
- `InstrumentProjector2.handleClusterCardChanged(...)`: orquestra efeitos Android/WebView a partir
  da decisao da politica.
- `DisplayAppLauncher.findFirstTasksForPackages(...)`: permite ler tasks de varios pacotes com um
  unico `am stack list` quando uma inspecao de display realmente for necessaria.

## Regras de Preservacao

- O contrato D3 continua mandatorio: com projecao ativa, estado de projecao deve chegar ao JS antes
  de `cardId`.
- `main_menu`/AC podem aparecer no tema Mapa e sobre projecao D3 sem depender de flag temporizada
  por input fisico. A navegacao do card direito continua funcional em `display=Mapa`.
- CarPlay e Android Auto continuam isolados; o fast path nao altera Surface, foco, bounds, handoff,
  `am start`, `move-stack`, `force-stop` ou patches nativos.
- O callback JS `window.Android.setCardId(...)` deve ignorar eco do mesmo card para evitar trabalho
  duplicado.
- `cardId=0` deve encerrar a sessão visual do MainMenu/AC e voltar para um estado neutro do tema.
  O card direito nao pode ficar mascarado por `main_menu`, `graph`, `regen` ou
  `display_selection`.
- `cardId=0` nao deve usar pass-through nativo no fluxo atual. A `Presentation`/WebView deve
  continuar viva e visivel, com ou sem CarPlay/Android Auto, para preservar o tema aplicado,
  widgets do `Mapa`, velocimetro, barra lateral e rodape.
- Se o painel nativo precisar voltar a aparecer em algum estado futuro, a solucao deve ser uma
  representacao neutra leve dentro do proprio tema, nao esconder a `Presentation` inteira no
  `cardId=0`.
- O `InputService` deve registrar apenas o wildcard `-1`; registrar wildcard + codigos explicitos
  pode duplicar o mesmo evento de volante.
- A navegacao sintetica esquerda/direita deve usar `clusterCardView` como fonte primaria para
  preservar a sequencia real `0 -> 1 -> 3`.
- Quando a navegacao sintetica selecionar o card AC (`3`), o app passa a ser o dono desse card ate
  uma nova intencao fisica de saida (`LEFT`, `RIGHT`, `BACK` ou `HOME`). Durante esse ownership,
  callbacks OEM atrasados `msgId=133` com `3 -> 1`/`3 -> 0` sao ecos e nao podem derrubar o AC.
- O ownership do AC nao usa timeout: os ecos reais de 2026-08-01 chegaram entre `1,527s` e
  `4,462s`. `UP`, `DOWN`, `ENTER` e long-presses de climatizacao preservam o card; uma nova
  navegacao fisica libera ou substitui o alvo imediatamente.

## Sinais de Validacao

Logs esperados em teste fisico:

- sem projecao/app secundario: `[CARD_FLOW]` deve aparecer apenas se o fluxo passar de `80ms` ou
  sair do fast path; transicoes normais podem nem logar;
- com projecao D3: `[PROJECTION_STATE_PUSH]` deve continuar antes de `control('cardId', ...)`;
- se houver atraso: `[CARD_FLOW]` registra `elapsedMs`, `fastPath`, `projectionActive`,
  `managedSecondary`, `boundsMayChange` e a decisao aplicada.
- ao sair do My Menu/AC para `cardId=0` sem projecao, o frontend deve ocultar o card direito e o
  Android deve manter a `Presentation`/WebView visivel.
- ao passar para `cardId=0` com CarPlay/Android Auto no D3, o menu direito deve sumir, mas os
  widgets do `Mapa` devem permanecer sobre a projecao.
- no `display=Mapa`, `cardId=1` deve permitir navegar MainMenu e `cardId=3` deve permitir navegar
  AC normalmente.
- Em build debug/leanDebug, a mesma sequencia critica tambem fica registrada no log persistente em
  `cluster-diagnostics/cluster-events-YYYYMMDD.log`, com eventos como `cluster_input_key`,
  `synthetic_cluster_card_navigation`, `native_cluster_card_changed`,
  `native_cluster_card_ignored`, `cluster_card_change`, `screen_update` e
  `steering_wheel_ac_control`. Esse arquivo deve ser coletado antes de reiniciar a central quando
  houver bug de AC voltando ao MainMenu.
- Para validar ownership, os eventos `native_cluster_card_ignored` devem registrar
  `protectSyntheticAirconExit=true` enquanto o card 3 estiver aberto pelo app. Ao sair por tecla
  fisica, o flag deve ser liberado e a navegacao seguinte continuar funcional.
- Validacao real v292 em 2026-08-01: com CarPlay efetivamente no D3, `RIGHT` abriu
  `card=3/screen=aircon` e dois heartbeats mantiveram esse estado por mais de `100s`, sem retorno
  automatico, reload de WebView ou `3 -> 1/0` aceito. D0 e saidas fisicas continuam A confirmar.

## Risco Residual

Se `isAnyAppOnDisplay3` ou `isAnyAppOnDisplay1` ficar stale por falta de evento do
`DisplayAppLauncher`, o fast path pode adiar uma recomputacao de app gerenciado ate o proximo evento
de display, watchdog ou mudanca que force o caminho protegido. A confirmar em teste real com app
nao-projecao no display 3.

Como o pass-through nativo do `cardId=0` esta desabilitado, o painel nativo puro deixa de aparecer
por tras nesse estado. Essa troca foi feita para preservar o tema aplicado e evitar queda visual do
D3; se o painel nativo puro voltar a ser requisito, tratar como nova decisao de produto/arquitetura.
