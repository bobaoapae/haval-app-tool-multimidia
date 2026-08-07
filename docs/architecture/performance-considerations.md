# Performance Considerations

Atualizado em: 2026-06-15

## Atualizacao 2026-08-07 - MainMenu atrasado por carga WebView/GPU no Sport

- Em captura fisica da v308, a latencia de `UP/DOWN` variou de menos de `20 ms` para
  `600-800 ms`. O atraso apareceu tanto antes do projetor quanto no callback
  `evaluateJavascript`, confirmando fila compartilhada e nao falha da regra do menu.
- O processo Impulse consumiu `90-94%` de um nucleo em duas janelas, dominado pela thread
  `Chrome_InProcGp` em `61-62%`; renderer WebView ficou em `13-15%` e SurfaceFlinger em `17-18%`.
- O delta de `gfxinfo` em ~457s foi `7061/7172` novos frames janky (`98,45%`), p50 `200 ms`.
  Como havia cinco ViewRoots, o numero nao e FPS isolado, mas a mesma janela acumulou `644`
  eventos de alta latencia de entrada e `6284` frames com UI thread lenta.
- O Sport 0.16.44 possui um canvas de velocimetro Normal que continua desenhando por rAF com
  `shadowBlur` mesmo oculto em outros displays. O Digital mantem um rAF agendado mesmo fora da
  tela, e o Analogico V2 adiciona seu proprio loop SVG a ~30 fps quando velocidade/potencia mudam.
- Durante a captura o estado era `main_menu` e `graph=unknown`; nao houve console error nem
  watchdog reload. O modo Chart.js nao foi o fator primario observado.
- `handleDataChanged` deduplica valores iguais, mas cada chave realmente alterada ainda posta seu
  proprio `ensureUi` e uma ou mais chamadas `evaluateJavascript`. Input de menu usa a mesma
  main thread e pode ficar atras da rajada de telemetria.

Correcao implementada localmente:

- `ProjectionDisplayHtmlPolicy` injeta uma guarda exata no rAF do canvas Normal. Fora de
  `.display-normal`, o frame seguinte continua agendado, mas `clearRect`, gradiente, arcos,
  `shadowBlur` e strokes nao executam. Ao voltar para Normal, o renderer original retoma no mesmo
  handle e seu cleanup permanece valido.
- `SportTelemetryBatchPolicy` limita cinco sinais de gauge a um lote a cada `33 ms`: velocidade,
  RPM, fator/regeneracao, tensao e corrente. Valores repetidos continuam deduplicados; quando mais
  de uma amostra chega no quadro, somente a mais recente e entregue.
- Tensao e corrente do mesmo quadro calculam `evPowerKw` uma unica vez. Fator de potencia produz
  `evPowerFactor` e `evPowerRegen` no mesmo IIFE, reduzindo tarefas e crossings Android -> JS.
- O lote e exclusivo dos dois pacotes Sport legados. Uma chave inscrita via `subscribe()` ignora o
  lote e segue a bridge original, preservando retrocompatibilidade do contrato `v1.0`; demais
  temas nao executam nem a consulta de subscription.
- A reescrita do canvas exige uma unica assinatura conhecida e falha fechada. Os pacotes 0.16.44
  continuam imutaveis e o Theme Lab aplica a mesma regra somente na resposta de desenvolvimento.

Validacao e limite:

- `382` testes, assemble, lint, Theme Lab check/build e browser local: OK. O navegador confirmou
  guarda ativa no V2, retomada visual em Normal e retorno ao estado oculto sem console error.
- A matriz parado/dinamico, Sport/leve e CarPlay conectado/desconectado continua necessaria na
  central; build e browser nao quantificam a reducao real de CPU, jank ou latencia do volante.

## Atualizacao 2026-08-07 - Movimento responsivo dos ponteiros Sport

- SportRed/SportRedLite 0.16.44 possuem um interpolador exponencial compartilhado pelos ponteiros
  de velocidade e potencia/regeneracao, executado sob demanda e limitado a aproximadamente
  `30 fps`.
- A constante original de `180 ms` precisava de cerca de `540 ms` para chegar a 95% do alvo. A
  velocidade ainda somava uma transicao de propriedades SVG de `80 ms`; variacoes maiores que
  `30 km/h` ou `80 kW` ignoravam a suavizacao e saltavam.
- A compatibilidade do host reduz a constante para `100 ms`, remove os dois saltos e limita a
  transicao extra da velocidade a `34 ms`. O limite de `30 fps` e preservado para nao dobrar
  updates de geometria SVG nem o custo dos `drop-shadow` existentes.
- O ajuste e uma reescrita atomica e fail-closed em memoria, restrita aos dois bundles Sport
  conhecidos. Nao altera bridge, contrato `v1.0`, arquivos OTA, resolucao, DOM, listeners,
  observers ou quantidade de loops.
- O Theme Lab aplica a mesma transformacao somente ao servir a previa; os hashes dos pacotes
  legados permanecem intactos.
- Snapshot read-only da central v307: `dumpsys gfxinfo` reportou `9025/11628` frames janky
  (`77,61%`), mediana `250 ms`, seis `ViewRootImpl` e processo com aproximadamente `248 MB` RSS;
  o sandbox WebView apareceu com aproximadamente `185 MB` RSS. Esses valores sao cumulativos e
  agregados, portanto nao isolam a WebView Sport nem provam causalidade do ponteiro.
- Validacao fisica e comparacao temporal controlada na central permanecem **A confirmar**.

## Atualizacao 2026-08-06 - Overlay diagnostico CPU/RAM

- `HeadUnitResourceSampler` le `/proc/stat`, `/proc/meminfo` e `/proc/self/status` sem shell.
- O overlay e avancado, opt-in e default OFF; quando ativo, amostra a cada `2,5s` fora da main
  thread e atualiza somente um `TextView` pequeno.
- A janela e `NOT_FOCUSABLE|NOT_TOUCHABLE`, some durante o Dashboard expandido e e removida junto
  com o job no `BottomBarService.onDestroy`.
- Nao ha WebView, DOM, blur, animacao ou coleta GPU adicional. Custo real prolongado na central
  permanece A confirmar.

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
- Mascaras de contraste sobre projecao devem preferir gradientes estaticos e localizados. O
  full-bleed CarPlay do `Analógico V2` Sport usa dois `linear-gradient` estaticos, um por borda,
  sem `filter`,
  `backdrop-filter`, animacao, listener ou DOM adicional.
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
