# Performance Considerations

Atualizado em: 2026-06-15

## Pontos Identificados

- `InstrumentProjector2` tem deduplicação de valores com `lastSentValues`.
- `batchEvaluateJs` agrupa sincronização inicial.
- `updateWarningUI` tem guard para evitar loop de warning.
- `ClusterWarningPolicy` impede que warnings apenas visuais (`warning_tts_notify`/BSD/LCA)
  acionem o fluxo pesado de warning, dismiss e visibilidade do cluster.
- O frontend renderiza dentro de WebView no cluster, então DOM/CSS afetam fluidez diretamente.
- Heartbeat roda a cada 2 segundos no JS; watchdog Android checa a cada 5 segundos.
- Modo grafico usa Chart.js/canvas/SVG e deve ter limite explicito de frequencia.
- `ClusterPerfEventLogger` registra eventos operacionais com snapshot de CPU/memoria no logcat
  usando tag `ClusterPerf` e prefixo `[PERF_EVENT]` somente em builds debug/internal.
- `ClusterPersistentEventLogger` grava uma trilha leve de diagnostico em arquivo por dia em
  builds debug/leanDebug e em releases de preview com
  `IMPULSE_REPORT_DIAGNOSTICS_ENABLED=true`:
  `/sdcard/Android/data/br.com.redesurftank.havalshisuku/files/cluster-diagnostics/cluster-events-YYYYMMDD.log`.
  A retencao preserva hoje + dois dias anteriores e remove arquivos mais antigos.
- A captura persistente pode ser desligada pelo usuario na tela `Reportar problema`. Quando
  desligada, o logger retorna antes de enfileirar qualquer escrita em `Dispatchers.IO`.
- `WebChromeClient.onConsoleMessage` grava `webview_console` nesse mesmo log persistente diario,
  permitindo correlacionar erros do frontend com `WEBVIEW_STATE_SYNC`, cards e projecao.
- Release final sem `preview` nao deve emitir logs de diagnostico do app por padrao:
  `ClusterPerfEventLogger` retorna imediatamente quando `BuildConfig.DEBUG=false`, logs CarPlay de
  Now Playing usam lazy debug logging e o R8 remove chamadas `android.util.Log` por
  `-maximumremovedandroidloglevel 7`. A excecao operacional e a trilha persistente leve de
  `Reportar problema` em builds `*-preview`, controlada por
  `IMPULSE_REPORT_DIAGNOSTICS_ENABLED=true` e pelo toggle do usuario.

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
  heartbeat devem continuar espaçados e eventos JS devem ser pontuais. Em release final, essa
  instrumentacao deve permanecer desligada salvo decisao explicita.
- O log persistente nao deve capturar `logcat` inteiro nem snapshots pesados a cada tick. Usar
  apenas eventos ja existentes, console do WebView e mudancas de estado para preservar I/O baixo
  durante viagens longas.
- A tela `Reportar problema` le apenas o arquivo persistente do dia atual e adiciona um snapshot
  recente, filtrado e limitado de `logcat` no momento do envio/copia. O fluxo nao inicia coleta
  continua, nao consulta stacks/displays e nao altera WebView/Presentation.

## Arquivos Relacionados

- `InstrumentProjector2.kt`
- `cluster-widgets/default/src/core/main.js`
- `cluster-widgets/default/src/styles/night.style.css`
- `cluster-widgets/default/src/core/components/`
- `cluster-widgets/default/src/core/components/graphs/graphs.js`
- `cluster-widgets/default/src/core/components/graphs/warpTunnel.js`
- `ClusterPerfEventLogger.kt`
- `ClusterPerfEventLoggerTest.kt`
- `ClusterPersistentEventLogger.kt`
- `ClusterPersistentEventLoggerTest.kt`
- `ProblemReportBuilder.kt`
- `ProblemReportSubmitter.kt`
- `ProblemReportScreen.kt`

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
  seguintes usam delta entre eventos. Em release final esses eventos nao sao emitidos por padrao.
- Para diagnostico pos-reboot de eventos de cluster, puxar os arquivos persistentes:

```bash
adb -s <ip>:5555 shell "ls -l /sdcard/Android/data/br.com.redesurftank.havalshisuku/files/cluster-diagnostics"
adb -s <ip>:5555 pull /sdcard/Android/data/br.com.redesurftank.havalshisuku/files/cluster-diagnostics
```

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
- `webview_console`: mensagens do console JS capturadas pelo `WebChromeClient` em build debug,
  com nivel, fonte, linha e mensagem truncada.
- `warning_tts_notify` pode aparecer em log como pulso nativo (`1121 -> 0`); em build atual isso
  deve chegar ao JS como visual-only, sem gerar `warning_state_changed`.
- Eventos persistentes adicionais para diagnostico de CarPlay/AC:
  `app_start`, `foreground_service_started`, `webview_state_sync`, `projection_state_push`,
  `projection_usb_configured_changed`, `carplay_watchdog_*`, `cluster_input_key`,
  `native_cluster_card_changed`, `native_cluster_card_ignored`,
  `synthetic_cluster_card_navigation`, `steering_wheel_ac_control` e `cluster_card_change`.

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
