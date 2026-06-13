# Performance Considerations

Atualizado em: 2026-06-12

## Pontos Identificados

- `InstrumentProjector2` tem deduplicação de valores com `lastSentValues`.
- `batchEvaluateJs` agrupa sincronização inicial.
- `updateWarningUI` tem guard para evitar loop de warning.
- O frontend renderiza dentro de WebView no cluster, então DOM/CSS afetam fluidez diretamente.
- Heartbeat roda a cada 2 segundos no JS; watchdog Android checa a cada 5 segundos.
- Modo grafico usa Chart.js/canvas/SVG e deve ter limite explicito de frequencia.
- `ClusterPerfEventLogger` registra eventos operacionais com snapshot de CPU/memoria no logcat
  usando tag `ClusterPerf` e prefixo `[PERF_EVENT]` somente em builds debug/internal.
- Builds preview/release nao devem emitir logs de diagnostico do app: `ClusterPerfEventLogger`
  retorna imediatamente quando `BuildConfig.DEBUG=false`, logs CarPlay de Now Playing usam lazy
  debug logging e o R8 remove chamadas `android.util.Log` por `-maximumremovedandroidloglevel 7`.

## Riscos de Performance

- `evaluateJavascript` frequente.
- `setInterval` sem cleanup no frontend.
- CSS com blur/filtros/shadows pesados em fullscreen.
- Layout shift de cards/gauges.
- Assets grandes inlined no HTML.
- Reload de WebView durante direção/projeção.
- `chartInstance.update(...)` em intervalos curtos por muitas horas de viagem.
- Canvas/`requestAnimationFrame` com erro repetido em `try/catch`, gerando log/GC continuo.
- Instrumentacao de performance em frequencia alta demais tambem pode virar custo; logs de
  heartbeat devem continuar espaçados e eventos JS devem ser pontuais. Em preview/release, essa
  instrumentacao deve permanecer desligada.

## Arquivos Relacionados

- `InstrumentProjector2.kt`
- `cluster-widgets/default/src/core/main.js`
- `cluster-widgets/default/src/styles/night.style.css`
- `cluster-widgets/default/src/core/components/`
- `cluster-widgets/default/src/core/components/graphs/graphs.js`
- `cluster-widgets/default/src/core/components/graphs/warpTunnel.js`
- `ClusterPerfEventLogger.kt`
- `ClusterPerfEventLoggerTest.kt`

## Recomendações

- Preferir updates por evento e deduplicados.
- Medir antes de adicionar animações contínuas.
- Usar classes CSS estáveis e dimensões fixas para componentes do cluster.
- Validar na central real para alterações visuais.
- Em telas de grafico, manter update visual em baixa frequencia:
  - UI geral em torno de `250 ms`;
  - Chart.js em torno de `500 ms` ou por evento significativo;
  - coletor historico ativo somente quando `screen` for `graph`/`graphs`.
- Nao deixar `requestAnimationFrame` ativo fora da tela de grafico.
- Para correlacionar travamentos/lentidao com recursos, usar build debug/internal e coletar logcat
  filtrando `ClusterPerf`/`[PERF_EVENT]`. A primeira amostra de CPU pode vir como `n/a`; as
  seguintes usam delta entre eventos. Em preview/release esses eventos nao sao emitidos.

## Diagnostico por Evento

Eventos principais registrados:

- `card_change`: troca AC/MainMenu/cards, com `elapsedMs`, `fastPath`, `projectionActive`,
  `managedSecondary` e `syncApps`.
- `screen_update`, `menu_item_navigation`, `graph_navigation`: navegacao interna do MainMenu.
- `js_graph_mount`, `js_graph_switch`, `js_graph_runtime`, `js_graph_cleanup`: ciclo do modo
  grafico no frontend.
- `display1_app_state`, `display3_app_state`, `app_geometry_changed`: mudancas que podem acionar
  recomputacao de display/bounds.
- `webview_page_finished`, `webview_heartbeat`, `warning_state_changed`, `projector_on_stop`.

Observacao: `menu_item_navigation` e amostrado no Android com intervalo minimo de `2s`, porque foco
de menu pode disparar varias vezes em sequencia e nao deve executar coleta pesada em cada passo.

Comando recomendado na central com build debug/internal:

```bash
logcat -d -v time | grep -Ei 'ClusterPerf|PERF_EVENT|CARD_FLOW|chromium|Console|InstrumentProjector2' | tail -n 500
```

Campos uteis:

- `cpuProcPct`: CPU do processo como percentual aproximado de um nucleo.
- `cpuSystemPct`: CPU ocupada do sistema no intervalo.
- `cpuIntervalMs`: janela entre a amostra atual e anterior.
- `pssKb`, `dalvikPssKb`, `nativePssKb`, `otherPssKb`: memoria PSS.
- `heapUsedKb`, `heapTotalKb`, `heapMaxKb`, `nativeHeapKb`, `threads`: memoria/threads do
  processo.

## A Confirmar

- Métricas aceitáveis de CPU/memória na central.
- Ferramenta padrão para profiling no ambiente do carro.
- Custo real da propria instrumentacao em viagem longa na central.
