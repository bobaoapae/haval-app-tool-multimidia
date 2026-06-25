# Android Flow

Atualizado em: 2026-06-25

## Fluxo Identificado

1. `App.onCreate()` salva application context, aplica mounts Android Auto quando instalados e inicia `ForegroundService`.
2. `ForegroundService` inicia como foreground service e tenta inicializar Shizuku via telnet local.
3. Após Shizuku, o serviço inicia rotinas como ADB/SSH, iptables, patches e demais serviços internos.
4. `ServiceManager` integra serviços veiculares, cache de dados e eventos para UI/projectors.
5. `ProjectorManager` cria projectors nos displays secundários.
6. Telas Compose em `ui/screens` expõem configurações para usuário.

## Atualizacao 2026-06-18 - Rollback Android Auto v216

- A v216 `1.0.0.216-rollback-v194-debug` foi instalada na central real `192.168.15.101` como
  rollback operacional apos regressao de conexao Android Auto na v215.
- O APK v194 exato existe em `/data/local/tmp/haval-v194-debug.apk`, mas nao foi reinstalado porque
  embute `AndroidAutoService.apk` MD5 `4f07b9deeb7097a2b21de33935a702ca`. A v216 preserva o
  nativo stock `54df14713bf26466af55a76382a67ce6`.
- Temporariamente, o card volta a aceitar evidencia de sessao/projecao Android Auto como no
  baseline anterior, em vez de exigir apenas transporte AA estrito. Isso reverte a regra v215 ate
  o teste fisico confirmar a conexao USB/Android Auto.
- O preparo de comando de midia Android Auto volta a enviar `requestVideoFocus` tambem para
  `pause`; o fallback AAP e o fallback generico de media button voltam a ficar disponiveis.
- Esta v216 nao declara `play/pause` corrigido. Ela e rollback de regressao/conexao; `pause`
  continua dependente de validacao sustentada no Spotify/telefone.

## Atualizacao 2026-06-18 - Atalho Volante Dashboard Impulse

- `SteeringWheelCustomActionType` inclui a acao `TOGGLE_IMPULSE_DASHBOARD`, chave
  `toggle_impulse_dashboard`, exibida automaticamente no dropdown de botoes personalizados do
  volante em `BasicSettingsScreen`.
- Quando `ServiceManager` recebe o keycode do botao customizado configurado para essa acao, ele
  chama `BottomBarService.requestImpulseDashboardToggleFromSteeringWheel(...)`.
- `BottomBarService` alterna `BottomBarState.isDashboardExpanded`, limpa menus/sliders ativos,
  mantem a barra inferior visivel e abre `ImpulseDashboardActivity` quando o destino e expandido.
- O fluxo tem deduplicacao de `800ms` por botao para evitar que um mesmo toque abra e feche o
  dashboard se o InputService entregar evento duplicado.
- Esta acao nao move apps para D0/D3 e nao altera os fluxos de CarPlay ou Android Auto.

## Atualizacao 2026-06-18 - Card de midia AA x Bluetooth

- O card de midia nao deve tratar `source=402` ou Activity visual do Android Auto como prova
  suficiente de transporte AA.
- Para metadata/comandos do card serem roteados como Android Auto, deve existir transporte real:
  link AA ativo, USB/projecao pronto ou `USAGE_AAUTO_MEDIA state:started`.
- Se `dumpsys media_session` mostra apenas `com.android.bluetooth/A2dpMediaBrowserService`, o card
  deve manter `mediaPackage=com.android.bluetooth` e usar `MediaController` Bluetooth.
- A v215 validou esse fluxo na central `192.168.33.206`: com Bluetooth A2DP/AVRCP conectado e AA
  sem link/audio real, `card_toggle` deu play e depois pause na sessao Bluetooth, sustentando pause
  por mais de 30s.
- O fallback AAP de pause Android Auto nao roda quando o `PAUSE` direto ja foi enviado e nao
  verificou, para evitar duas rotas no mesmo toque.

## Atualizacao 2026-06-15 - Reporte de Problemas

- A tela Compose `Reportar problema` envia relatos para o backend Supabase do Impulse, nao para o
  GitHub diretamente.
- Backend ativo:
  - organizacao Supabase: `xovgvoffyomafawsquie`;
  - projeto: `Mumu`;
  - ref: `eymyarugcfcwkezqjiba`;
  - URL: `https://eymyarugcfcwkezqjiba.supabase.co`;
  - Edge Function: `impulse-report-problem`.
- O app usa apenas a publishable key no APK. Service role, secret key e token GitHub ficam
  exclusivamente no backend Supabase.
- Fluxo:
  1. usuario abre `Reportar problema`;
  2. preenche descricao obrigatoria citando data, hora, minutos aproximados e detalhamento do
     incidente;
  3. a tela mostra um exemplo com data/hora, app/tela em uso e comportamento observado;
  4. o botao `Enviar relatório` fica logo apos o campo de descricao;
  5. app monta versao, build, data/hora de geracao, timezone, trecho do log persistente do dia
     atual e snapshot recente/filtrado de `logcat`;
  6. app faz `POST` para a Edge Function com publishable key;
  7. Edge Function valida payload/rate limit, grava em `public.impulse_problem_reports` com
     service role e, se secrets GitHub existirem, cria issue server-side;
  8. Edge Function salva uma copia `.txt` do corpo do relatorio/logs no bucket privado
     `impulse-problem-report-logs` e grava `log_storage_bucket`, `log_storage_path` e
     `log_storage_size_bytes` na linha do report.
- A tabela `public.impulse_problem_reports` tem RLS habilitado e policy apenas para `service_role`.
  `anon`/`authenticated` nao tem permissao direta na tabela; envio publico passa somente pela Edge
  Function.
- O bucket `impulse-problem-report-logs` e privado. O APK nao acessa Storage diretamente; upload
  do arquivo e feito somente pela Edge Function usando service role.
- Rate limit inicial: ate `8` relatos por `installationId` em 24h.
- Se o envio falhar ou a build nao estiver configurada, o app copia o relatorio completo para a
  area de transferencia.
- O log persistente diario tambem inclui `webview_console`, capturado via `WebChromeClient`, para
  registrar `console.log/warn/error` do dashboard sem alterar o contrato Kotlin/JS.
- A captura persistente fica disponivel em `debug`/`leanDebug` e em builds release com
  `IMPULSE_REPORT_DIAGNOSTICS_ENABLED=true`. Por padrao, esse campo fica ativo quando
  `appVersionName` contem `preview`; release final sem `preview` continua sem diagnostico, salvo
  override explicito por `-PimpulseReportDiagnosticsEnabled=true`.
- A tela mostra o status do log persistente do dia atual: captura ativa/desativada, arquivo,
  tamanho e ultima atualizacao. O usuario pode desligar a captura persistente; isso para novas
  escritas no arquivo diario, mas nao executa limpeza retroativa.
- O `logcat` nao fica em captura continua. Ele e lido somente quando o usuario toca em
  `Enviar relatório` ou `Copiar relatório`, naquele momento.
- A tela nao mostra mais checkboxes de contexto; o detalhamento do cenario fica no texto livre do
  relato. O payload tecnico ainda mantem campos booleanos como `false` por compatibilidade com o
  contrato atual da Edge Function.

## Atualizacao 2026-05-31

- `ForegroundService` usa lock dedicado para serializar bootstrap/restart sem segurar o monitor do
  service em chamadas lentas de Shizuku/telnet.
- `SplashActivity` redireciona imediatamente para `MainActivity`; tela "Inicializando sistema"
  persistente nao deve mais bloquear a UI quando o service demora.
- `ServiceManager` tolera a ausencia temporaria de `VoiceAdapterService` e inicializa
  `ProjectorManager` na main thread. `IntelligentVehicleControlService` continua obrigatorio.
- `com.beantechs.voice.adapter` deve ser restaurado para dados completos de veiculo/DVR/modelo,
  mesmo que a UI de voz esteja desativada durante diagnostico.

## Atualizacao 2026-06-01

- Auto-start Shizuku depende do Impulse instalado com UID baixo. O `ForegroundService` recalcula o
  UID real no boot e invalida `selfInstallationIntegrityCheck` se `uid > 10999`.
- Quando o UID nao permite bootstrap automatico, o foreground service encerra explicitamente em vez
  de ficar rodando em estado parcial.
- A execucao de `libshizuku.so` captura stderr e valida `shizuku_server` apos o starter. Saidas com
  `fatal:` ou `Can't find service` sao tratadas como falha de bootstrap.
- `pm install -r` nao deve ser usado como tentativa de corrigir UID, porque preserva o usuario
  Linux do pacote. Para recuperar auto-start quando o UID estiver alto, usar reinstall limpo pelo
  fluxo com hook/exploit e depois validar `dumpsys package` e `pidof shizuku_server`.

## Atualizacao 2026-06-11/12

- O card de midia do dashboard D0 roteia comandos pela fonte ativa:
  - CarPlay usa o Binder nativo `ICarPlayService` via `CarPlayNowPlayingMonitor`;
  - Android Auto usa `AndroidAutoNowPlayingMonitor`;
  - fontes Android comuns usam `MediaController` quando a sessao publica suporte.
- Para CarPlay, `prev`, `next` e `play/pause` devem usar HID IAP por
  `sendHidEventOverIap`: `PLAY=1`, `PAUSE=2`, `NEXT=4`, `PREV=8`. A tentativa com
  `PLAY_PAUSE=0x40` nao pausou no teste fisico de 2026-06-11.
- Metadata/capa CarPlay no dashboard vem do Binder nativo
  `com.ts.carplay/.CarPlayService` via `INowPlayingUpdateCallback`. A partir de 2026-06-12 11:36,
  o monitor tenta bind explicito leve a cada `5s` quando nao ha Binder vivo, para cobrir reboot em
  que o `BottomBarService` sobe antes do servico nativo. Esse bind e somente para metadata: nao
  chama `requestUi(0)`, nao abre Activity, nao altera Surface/foco/display e nao mexe no handoff
  D0/D3.
- Metadata/capa CarPlay no dashboard nao deve depender apenas da queda do Binder
  `INowPlayingUpdateCallback` para limpar. `BottomBarService` tambem observa
  `/sys/class/android_usb/android0/state` e, quando o USB deixa de estar `CONFIGURED`/`CONNECTED`,
  limpa somente o estado visual CarPlay do `BottomBarState`.
- Metadata/capa Android Auto nao deve ser limpa apenas porque
  `/sys/class/android_usb/android0/state` aparece desconectado. Em Android Auto wireless/hotspot,
  a sessao de projecao/midia pode continuar ativa sem USB configurado. A limpeza AA deve depender
  de ausencia de sessao/projecao real, nao apenas do sysfs.
- Enquanto a fonte atual for Android Auto, metadata/capa vindas de sessoes Bluetooth/MediaCenter
  podem ser usadas como fallback controlado para o card de midia. Esse fallback nao pode sobrescrever
  CarPlay nem fontes desconhecidas.
- A partir de 2026-06-12 23:18, o fallback preferencial para capa/metadata Android Auto e o Binder
  nativo do `com.beantechs.mediacenter`: `IPlayService.getPlayMediaInfoBySource(402)` via
  transacao `22`. O Impulse le apenas quando `getCurrentSource` ou `getCurrentAudioSource` esta em
  `402`; fora desse estado, `com.beantechs.mediacenter` continua proibido como fallback para nao
  reabrir o conflito com radio nativa.
- A partir de 2026-06-13 08:55, o progresso Android Auto vindo do MediaCenter tambem consulta
  `IPlayService.getPlayStateBySource(402)` via transacao `19`. Quando o estado nativo traz
  `duration`/`currentProgress`, esses valores vencem. Quando chega apenas refresh de metadata/capa,
  o Impulse estima o elapsed a partir do ultimo `mediaElapsedMs` e `mediaProgressUpdatedAtMs` sem
  regravar timestamp novo com elapsed antigo, para evitar o relogio oscilar entre dois segundos.
  Em troca real de faixa sem elapsed nativo, o progresso zera em vez de carregar tempo da faixa
  anterior.
- A partir de 2026-06-15 22:56, o progresso Android Auto tambem protege o caso em que o status
  `playing` chega temporariamente falso. Amostras nativas regressivas para `0..1s` sao preservadas
  quando ha progresso anterior valido, mesmo se o status momentaneo vier como nao tocando. Resets
  continuam permitidos em troca real de faixa ou por uma janela curta apos comando explicito de
  `next/previous`, para nao bloquear a semantica nativa do MediaCenter.
- A partir de 2026-06-15 23:04, o `previous` explicito do card Android Auto nao deve usar
  `IPlayService.playPreviousBySource(402)`. Logs da central mostraram o toque chegando e o comando
  MediaCenter sendo enviado com `source=402`, mas sem efeito fisico. O card usa
  `AndroidAutoNowPlayingMonitor.previous()`/`LinkCommand.previous` para `previous`; `next` continua
  podendo usar `playNextBySource(402)` quando a fonte MediaCenter Android Auto esta ativa.
- A partir de 2026-06-15 23:11, `play/pause` explicito do card Android Auto tambem nao pode usar
  MediaCenter quando a fonte `402` esta ativa. A rota do card deve ser
  `AndroidAutoNowPlayingMonitor.pause()/play()` e, se o monitor nao existir, o fallback direto
  `LinkCommand` controlado por `DisplayAppLauncher.sendAndroidAutoDashboardPlaybackCommand(...)`.
  Logs da v116 mostraram `KEYCODE_MEDIA_PLAY_PAUSE` fisico seguido de app-side route e
  `Native MediaCenter Android Auto pause command sent`, padrao que duplica toggles.
- A partir de 2026-06-16 22:01, a v140 supersede as excecoes de comando por MediaCenter: nenhum
  comando Android Auto do card deve chamar `IPlayService.playNextBySource(402)`,
  `playPreviousBySource(402)`, `pauseMediaBySource(402)` ou `resumeMediaBySource(402)`. O
  MediaCenter fica restrito a leitura de metadata/capa/progresso/play state. Comandos explicitos do
  card usam `AndroidAutoNowPlayingMonitor`/`LinkCommand`; se o monitor retornar `false`, o fallback
  direto tambem e `LinkCommand`. `play/pause` deve enviar alvo explicito `PLAY`/`PAUSE`, nao toggle
  baseado no estado nativo stale.
- A partir de 2026-06-17 18:05, apos teste fisico confirmar `mute`, `next` e `prev` OK mas
  `play/pause` ainda sem pausar, o caminho Android Auto de `play/pause` passa a resolver o estado
  efetivo por fonte nativa/status do monitor antes de escolher `PLAY` ou `PAUSE`. Se as rotas
  existentes nao atingirem o alvo, ha fallback final por
  `AudioManager.dispatchMediaKeyEvent(KEYCODE_MEDIA_PLAY/KEYCODE_MEDIA_PAUSE)`, restrito a
  Android Auto `play/pause`. `mute`, `next` e `prev` permanecem nos caminhos ja validados.
- A partir de 2026-06-17 18:13, logs na central `192.168.15.100` mostraram que o teste reprovado
  de `play/pause` vinha do `InputService` fisico (`KEYCODE_MEDIA_PLAY_PAUSE`) e nao do card. O
  MediaCenter registrava `currentSource=402` com `isPhoneLinkCarMediaSource=false`, e o Impulse
  ficava em `native headunit route only`. A v157 passa a agendar reconciliacao tardia por alvo
  tambem fora do D3 quando a rota MediaCenter Android Auto esta ativa. O comando continua tardio e
  explicito (`PLAY`/`PAUSE`), sem mexer em `next/previous` nem em `mute`.
- A partir de 2026-06-17 21:13, a v186 remove `PLAY_PAUSE` do caminho de `play/pause` do card
  Android Auto. O card/debug nao usa mais HardKeyPolicy `1004` como primeiro fallback e o fallback
  AAP passa a usar `KEYCODE_MEDIA_PAUSE`/AAP hardkey `8` para pausar e
  `KEYCODE_MEDIA_PLAY`/AAP hardkey `7` para tocar. O MediaCenter nativo continua util para
  metadata/progresso/estado, mas o fluxo `DeviceMirrorManager.pause()/play()` nao deve ser tratado
  como prova de efeito fisico, pois retorna sucesso mesmo quando o Android Auto Service esta em
  `NO_DEVICE_OR_POWER(-1)`.
- A partir de 2026-06-17 22:13, a v194 remove o experimento FM/source `12` do `play/pause`
  Android Auto. A v193 mostrou que chamar `resumeMediaBySource(12)` seguido de
  `pauseMediaBySource(12)` pausava o audio por poucos segundos e depois o Android Auto/Spotify
  retomava sozinho. Portanto FM/source `12`, `aa_pause_brake` e `aa_pause_fm_brake` nao sao rotas
  validas para o card; qualquer comparacao com o radio nativo deve ser feita apenas em diagnostico
  isolado, mapeando o fluxo real da UI/servicos de radio antes de nova implementacao.
- A partir de 2026-06-18, a v195 exige verificacao sustentada de `6s` para `pause` Android Auto
  antes de marcar sucesso visual no card. O fallback generico por
  `AudioManager.dispatchMediaKeyEvent(KEYCODE_MEDIA_PAUSE)` tambem fica bloqueado para `pause`,
  porque pode atingir a sessao local da central. Para `play`, o fallback generico permanece como
  ultimo recurso. Essa mudanca preserva `mute`, `next` e `prev`.
- A partir de 2026-06-18, a v197 altera somente o preparo de comando Android Auto com alvo
  `pause`: ele continua enviando `VehicleInfo`, mantendo o bind/recuperacao de `LinkCommand` e
  publicando `ts.car.androidauto.view_state=foreground`, mas nao envia mais
  `requestVideoFocus`. Evidencia: na v196 o pause explicito retornava `sent=true`, mas o Spotify
  voltava a tocar sozinho ate quando pausado no telefone; o foco de video antes do pause pode estar
  reassertando a fonte AA. `play`, `next`, `prev`, hardkey policy e handoff visual preservam
  `requestVideoFocus`.
- A partir de 2026-06-18 13:47, a v206 prepara um patch nativo em `AndroidAutoService.apk` para
  sincronizar o estado interno AA antes de enviar `PLAY`/`PAUSE`. O patch adiciona
  `AapController.reportImpulseMediaPlaybackAction(I)` e chama
  `MediaPlaybackStatus.reportAction(126/127)` antes de `play()`, `pause()`,
  `sendKeyEvent(II)` para `ACTION_DOWN` de `126/127`, `sendMediaPlayKey(true)` e
  `sendMediaPauseKey(true)`. A motivacao e a falha observada em v205: o card/debug enviava
  `KEYCODE_MEDIA_PAUSE` e AAP hardkey `8` com `sent=true`, mas o estado do AA permanecia
  `NOT_START(0)` enquanto `dumpsys audio` mostrava `USAGE_AAUTO_MEDIA state:started` em
  `com.ts.androidauto`. Esta e uma correcao candidata ainda pendente de teste fisico sustentado.
- A partir de 2026-06-18 14:41, a v207 corrige o reload pos-mount do service Android Auto. O
  pacote real do service nesta central e `com.ts.androidauto.projectionservice`, embora o processo
  apareca como `com.ts.androidauto`; portanto `AndroidAutoPatchManager` para
  `com.ts.androidauto.projectionservice`, mantem `com.ts.androidauto` como compatibilidade legado e
  tambem para `com.ts.androidauto.app`. Depois de trocar `AndroidAutoService.apk`, validar hash do
  bind mount e confirmar que o processo `com.ts.androidauto` reiniciou apos o mount.
- O `AndroidAutoNowPlayingMonitor` ignora status `NOT_START` transitorio somente quando havia status
  `PLAYING`, metadata valida e progresso recente dentro de uma janela curta. Status `PAUSED`
  continua sendo aceito imediatamente.
- O MediaCenter do Android Auto pode emitir um evento atrasado sem `imageBitmap`. Nesse caso, se o
  card ja esta em Android Auto, a capa anterior deve ser preservada ate chegar nova capa ou uma
  desconexao/limpeza real de Android Auto.
- No Android Auto, a barra de progresso do dashboard e somente visual. Nao usar seek/scrub por
  `fastRewind/fastForward`, porque esse caminho pode retroceder a musica se um evento for
  interpretado incorretamente.
- `ClusterService msgId=135` durante Android Auto ativo e callback ambiguo: consumir sem enviar
  `previous/next` automatico. `prev`/`next` devem vir de comando explicito do card ou evento fisico
  de input comprovado.
- Eventos fisicos de `mute` do Android Auto vindos de `IInputService`/volante nao devem gerar
  comando app-side nem consumo parcial pelo Impulse. `mute` e toggle e qualquer duplicidade desfaz a
  acao.
- A partir de 2026-06-13 09:34, eventos fisicos de `next/previous` do volante no Android Auto
  voltam a ser nativo-only. Logs da build `106` mostraram o mesmo clique chegando como
  `KEYCODE_MEDIA_NEXT/PREVIOUS` pela headunit e o Impulse ainda enviando comando MediaCenter
  `source=402` no `ACTION_UP`; o resultado fisico foi duplo skip. O card de midia continua com rota
  explicita propria, mas o volante nao pode reutilizar essa rota para skip. Usuario confirmou em
  2026-06-13 que `prev/next` ficaram funcionando no Android Auto.
- A partir de 2026-06-13 09:45, eventos fisicos de `play/pause` do volante tambem ficam
  nativo-only. Logs da build `107` mostraram `KEYCODE_MEDIA_PLAY_PAUSE` vindo da headunit e o
  Impulse ainda enviando comando app-side; como `play/pause` e toggle, qualquer segundo disparo
  pode pausar e retomar. O Impulse observa o evento fisico, mas nao reenviar por LinkCommand,
  MediaCenter, AAP hardkey, OEM input ou shell.
- A v117 reforca esse contrato: `shouldUseAndroidAutoSteeringAppCommandRoute(...)` retorna sempre
  `false`, entao `IInputService`/volante Android Auto apenas observa e deixa a rota nativa da
  headunit agir. Isso vale para `next`, `previous`, `play/pause`, `play`, `pause` e codigo OEM
  `1004`.
- A partir de 2026-06-16, a v119 reafirma esse contrato apos logs da v118 na central
  `192.168.33.101`: `KEYCODE_MEDIA_PLAY_PAUSE` fisico estava entrando em `using app command route`
  e depois em `DIRECT_PLAYBACK command=pause sent=true`; `KEYCODE_MUTE` tambem caia em
  `MUTE_PAUSE`. Esse caminho impactou ate o MediaCenter nativo, entao evento fisico de volante
  Android Auto nao pode chamar `LinkCommand`, MediaCenter, AAP hardkey, OEM input, shell ou pause
  auxiliar.
- A tentativa v120 de reconciliar `play/pause` fisico com alvo tardio do MediaCenter
  (`pauseMediaBySource(402)`/`resumeMediaBySource(402)`) fica supersedida a partir de
  2026-06-16. Logs e teste fisico mostraram que a central pode auto-retomar ou reportar estado
  stale; portanto o volante Android Auto volta ao contrato estrito: observar o evento, aplicar no
  maximo hint visual e nao enviar comando app-side nem reconciliacao tardia.
- A v125 ajusta esse contrato para o estado real observado na central `192.168.33.2`: quando o
  MediaCenter nativo esta em fonte Android Auto `402`, o evento fisico do volante chega como
  `KEYCODE_MEDIA_NEXT/PLAY_PAUSE`, mas o proprio MediaCenter registra
  `isPhoneLinkCarMediaSource=false` e nao repassa a tecla. Nesse estado, o volante usa rota
  app-side controlada no `ACTION_UP`: `next` chama a mesma rota efetiva do card
  (`playNextBySource(402)`), `previous` usa monitor/LinkCommand, e `play/pause` usa a rotina direta
  que le estado antes/depois antes de enviar pause/play. Fora da fonte `402`, o volante continua
  sem injecao app-side.
- A v126 supersede somente a parte de `play/pause` da v125. Logs da v125 mostraram que o caminho
  direto `LinkCommand` retornava `sent=true`, mas o estado nativo ficava `NOT_START(0)` antes e
  depois, sem pausar a musica. Quando a fonte MediaCenter Android Auto `402` esta ativa, volante
  `play/pause` e botao `play/pause` do card usam `IPlayService.pauseMediaBySource(402)` ou
  `resumeMediaBySource(402)` em comando unico. Isto nao reativa a reconciliacao tardia v120:
  continua sem scheduler pos-evento, sem comando duplicado atrasado e com cooldown da rota
  `native_mc_playback`.
- Na v126, `mute` fisico Android Auto com fonte `402` usa o toggle nativo de audio da central
  (`SYS_SETTINGS_AUDIO_MUTE_ADJUST_ACTION=2`) e reaplica o alvo depois de `3.8s`/`7.2s` somente se
  a central desfizer o estado sozinha. Essa reaplicacao e restrita a mute e nao envia `play/pause`.
- A v127 supersede o volante da v126. Logs da central `192.168.33.49` mostraram que, em
  `KEYCODE_MEDIA_NEXT`, a tecla fisica chegava ao MediaCenter nativo e o Impulse tambem enviava
  `playNextBySource(402)`, produzindo duas trocas de faixa. Portanto, evento fisico de volante
  Android Auto volta a ser nativo-only mesmo com fonte MediaCenter `402` ativa. O MediaCenter `402`
  permanece a rota dos botoes explicitos do card D0; se um volante nao funcionar em algum estado,
  a proxima abordagem deve ser fallback tardio somente apos observar ausencia real de
  `onMediaPlayInfoChange`/play state, nunca envio imediato em paralelo.
- A v128 aplica essa abordagem para o caso especifico de Android Auto desejado no D3. Logs apos
  handoff `0->3` mostraram `KEYCODE_MEDIA_PLAY_PAUSE` chegando ao Impulse, mas a rota nativa-only
  nao gerou mudanca real de play state. Nesse estado, somente `play/pause` do volante agenda um
  fallback tardio e condicionado: apos a espera, o Impulse consulta o play state nativo da fonte
  `402`; se o alvo ja foi atingido, nao envia nada; se nao foi, envia
  `pauseMediaBySource(402)`/`resumeMediaBySource(402)`. `next/previous` continuam sem fallback
  app-side imediato para nao recriar duplo skip.
- Na v128, a reaplicacao de `mute` tambem passa a ler o estado real
  `sys.settings.audio.media_mute_state`/`sys.settings.audio.mute_state` antes de decidir. Logs
  mostraram que a central podia mutar e desmutar sozinha poucos segundos depois; usar somente o
  estado visual do Impulse podia impedir a reaplicacao.
- Na v129, o fallback tardio de `play/pause` do volante Android Auto D3 calcula o alvo a partir do
  play state nativo do MediaCenter `402` quando disponivel. Isso evita repetir `pause` porque
  `BottomBarState.mediaIsPlaying` ficou stale apos o handoff. A leitura de mute real tambem ficou
  target-aware para reassertar quando qualquer chave observada divergir do alvo.
- Na v130, callbacks fisicos Android Auto duplicados do `InputService` sao deduplicados por
  `keyCode + action` em uma janela curta de `280ms`. O objetivo e impedir que um unico toque no
  volante gere duas reconciliacoes ou duas chamadas de mute.
- Na v131, o fallback tardio de `play/pause` do volante Android Auto D3 deixou de abortar quando o
  play state nativo `402` reporta que o alvo ja foi atingido. Logs pos-v130 mostraram esse estado
  como stale/inconsistente; como `pauseMediaBySource(402)` e `resumeMediaBySource(402)` sao comandos
  de alvo, a reconciliacao envia o comando idempotente mesmo assim. A mesma versao corrige o
  reassert de mute para agendar as janelas de `3.8s` e `7.2s` em paralelo desde o toque original.
- Na v139, esse fallback tardio de `play/pause` fisico foi reativado apos a v138 mostrar uma falha
  oposta: o MediaCenter recebia `KEYCODE_MEDIA_PLAY_PAUSE` com `currentSource=402`, mas logava
  `isPhoneLinkCarMediaSource=false` e nao executava a tecla. O Impulse continua sem enviar comando
  app-side imediato; ele apenas agenda o alvo tardio para teclas de playback (`play/pause`, `play`,
  `pause`, `1004`) quando a rota MediaCenter Android Auto esta ativa e o alvo desejado e D3.
  `next/previous` permanecem sem fallback app-side para evitar duplo skip, e mute permanece em
  fluxo separado.
- A partir de 2026-06-13 09:52, o estado visual de `play/pause` Android Auto aceita
  `MediaPlayStateInfo.mSrc=0` como resposta valida da consulta feita para a fonte `402`. Logs
  nativos mostraram `currentSrc=402` com `mSrc=0`; descartar esse estado fazia o card preservar
  `mediaIsPlaying` antigo durante `clear` continuo e o icone nao mudava apos pause fisico.
- Quando o volante fisico envia `play/pause` nativo-only, o Impulse aplica um hint visual imediato
  em `BottomBarState.mediaIsPlaying`; o polling do MediaCenter nativo corrige o estado em seguida
  com `PLAYING/PAUSED` real.
- `KEYCODE_MUTE` e `KEYCODE_VOLUME_MUTE` entram no mesmo contrato fisico Android Auto. Mute e
  play/pause sao toggles; duplicidade desfaz o estado esperado.
- O codigo OEM `1004` tambem representa `PLAY_PAUSE` no Android Auto. Se aparecer como input/eco,
  deve ser tratado como evento observado, sem `LinkCommand`, AAP hardkey, fallback OEM ou shell
  `input keyevent`.
- O fallback OEM de midia Android Auto (`input keyevent 1002/1003/1004`) deve ficar desligado por
  padrao. A funcao `shouldUseAndroidAutoOemOnlyMediaRoute(...)` precisa respeitar a flag geral
  `ANDROID_AUTO_OEM_INPUT_MEDIA_FALLBACK_ENABLED=false`.
- O `AccessibilityService` nao consome toggles de midia enquanto Android Auto esta ativo
  (`play/pause`, `play`, `pause`, `mute`, `volume_mute`, `1004`). A regra anterior de passar
  `ACTION_DOWN` e consumir `ACTION_UP` foi substituida em `2026-06-12 13:42` porque o teste fisico
  ainda mostrou pause/mute voltando sozinho. Ela nao se aplica a `next/previous`, CarPlay ou estado
  sem Android Auto ativo.
- Botoes explicitos do dashboard D0 para Android Auto usam
  `AndroidAutoNowPlayingMonitor`/`LinkCommand` para `next`, `previous`, `play` e `pause`. A v140
  removeu comandos de card por MediaCenter `402`, mesmo quando essa fonte esta ativa, porque logs da
  central mostraram transacao aceita sem efeito fisico. Se o monitor existir e retornar `false`, o
  fallback direto tambem e `LinkCommand`, com alvo explicito para playback.
- A v181 adiciona uma excecao ao alvo explicito do card: quando a fonte nativa Android Auto `402`
  esta ativa mas o estado vem inconclusivo (`musicStatus=NOT_START(0)` e sem evidencia de audio
  ativo), o card nao deve inferir `PLAY`. Ele envia `MEDIA_PLAY_PAUSE` via
  `AndroidAutoHardKeyPolicyBridge`/`AndroidAutoRemoteUiService` e aceita somente ACK
  `dispatched=true`. Essa excecao e exclusiva do card; volante fisico continua nativo-only.
- Essa regra do card nao deve ser reutilizada pelo volante. O card nao possui evento fisico nativo
  paralelo; o volante possui. Essa diferenca explica por que `playNextBySource(402)` e correto no
  card, mas duplicou quando chamado imediatamente apos `KEYCODE_MEDIA_NEXT` fisico.
- Enquanto o dashboard fullscreen esta expandido, a janela overlay da bottom bar nao deve publicar
  faixa inferior tocavel. A barra real fica oculta nesse estado; manter a regiao tocavel de `60/80dp`
  no rodape cria uma faixa invisivel sobre os botoes inferiores do card de midia, observada quando
  Android Auto esta no D3.
- O gesto de recolher o dashboard deve ficar restrito a uma faixa superior dedicada. Controles do
  card de midia, especialmente `previous` e volume `+/-`, nao podem ficar dentro de area de drag ou
  acionar colapso por foco passivo/transitorio do SystemUI/MediaCenter. Toques nesses controles
  tambem suprimem por uma janela curta o restore por foco externo, porque algumas centrais reportam
  `com.beantechs.launcher` logo apos o volume/midia mesmo sem o usuario querer sair do dashboard.
- Historico: em 2026-06-13, `pauseMediaBySource(402)` retornou sucesso sem pausar em uma janela de
  teste. Tentativas posteriores de recuperar comandos por MediaCenter voltaram a produzir estado
  stale, auto-retomada ou duplicidade. A regra atual preserva MediaCenter `402` somente para
  metadata/capa/progresso/play state. Nao usar MediaCenter `402` para `next`, `previous`,
  `play/pause` ou reconciliacao atrasada.
- `clear` transitorio do `AndroidAutoNowPlayingMonitor` durante uma projecao ainda ativa nao deve
  derrubar `BottomBarState.mediaIsPlaying`. Esse clear pode chegar em loop enquanto o MediaCenter
  ainda esta com fonte Android Auto `402`; se ele marcar `isPlaying=false`, o proximo toggle pode
  enviar `play` quando o usuario esperava `pause`.
- O keepalive de foco do Android Auto so deve pulsar quando a fonte atual e Android Auto, a midia
  esta tocando e o audio nao esta mudo. Nao pulsar durante pause/mute, para evitar reativar o
  estado que o usuario acabou de pedir.
- Esses comandos sao restritos a midia. Eles nao autorizam abrir/mover Activity, alterar Surface,
  foco, display, watchdog ou handoff D0/D3 do CarPlay.
- Para release final sem `preview`, logs de diagnostico do app continuam desligados por padrao. O
  build release usa R8 com `-maximumremovedandroidloglevel 7`; `ClusterPerfEventLogger` nao executa
  fora de debug e os logs do `CarPlayNowPlayingMonitor` ficam em lazy debug logging. Em
  2026-06-12, `dexdump` do `minifyReleaseWithR8` confirmou ausencia de chamadas de emissao
  `Log.e/w/d/i` do app e ausencia dos marcadores `ClusterPerf`, `[PERF_EVENT]`,
  `CarPlay now playing...` e loop loggers no dex. Builds `*-preview` agora podem manter a trilha
  persistente leve de `Reportar problema` via `IMPULSE_REPORT_DIAGNOSTICS_ENABLED=true`.

## Arquivos Relacionados

- `App.java`
- `ForegroundService.java`
- `BootReceiver.java`
- `ServiceManager.java`
- `services/BottomBarService.kt`
- `services/CarPlayNowPlayingMonitor.kt`
- `services/AndroidAutoNowPlayingMonitor.kt`
- `MainActivity.kt`
- `SplashActivity.kt`
- `ui/screens/TelasScreen.kt`
- `ui/screens/InstallAppsScreen.kt`
- `ui/screens/ProblemReportScreen.kt`
- `diagnostics/ProblemReportBuilder.kt`
- `diagnostics/ProblemReportSubmitter.kt`
- `supabase/functions/impulse-report-problem/index.ts`

## Riscos

- Falha em Shizuku impede comandos privilegiados.
- Alterar `ForegroundService` pode quebrar boot.
- Alterar receivers pode impedir start após atualização/reboot.
- `ServiceManager` tem grande superfície de integração veicular.
- Endpoint publico de reporte pode receber spam. Mitigacao atual: validacao de publishable key,
  limite de tamanho, rate limit por installationId e tabela sem permissao direta para `anon`.

## A Confirmar

- Sequência exata de inicialização em cold boot real na central.
- Quais permissões precisam ser concedidas manualmente em cada instalação.
