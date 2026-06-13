# Android Flow

Atualizado em: 2026-06-12

## Fluxo Identificado

1. `App.onCreate()` salva application context, aplica mounts Android Auto quando instalados e inicia `ForegroundService`.
2. `ForegroundService` inicia como foreground service e tenta inicializar Shizuku via telnet local.
3. Após Shizuku, o serviço inicia rotinas como ADB/SSH, iptables, patches e demais serviços internos.
4. `ServiceManager` integra serviços veiculares, cache de dados e eventos para UI/projectors.
5. `ProjectorManager` cria projectors nos displays secundários.
6. Telas Compose em `ui/screens` expõem configurações para usuário.

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
- No Android Auto, a barra de progresso do dashboard e somente visual. Nao usar seek/scrub por
  `fastRewind/fastForward`, porque esse caminho pode retroceder a musica se um evento for
  interpretado incorretamente.
- `ClusterService msgId=135` durante Android Auto ativo e callback ambiguo: consumir sem enviar
  `previous/next` automatico. `prev`/`next` devem vir de comando explicito do card ou evento fisico
  de input comprovado.
- Eventos fisicos de `pause/play/mute` do Android Auto vindos de `IInputService`/volante nao devem
  gerar comando app-side pelo Impulse. Esses comandos sao toggles e qualquer duplicidade desfaz a
  acao. Para `next/previous`, a build de `2026-06-12 10:59` abre excecao controlada: usar a rota
  app-side somente no `ACTION_UP`, reaproveitando a mesma rota do card de midia, porque a rota
  nativa observada nao estava passando musica enquanto o card funcionava.
- `KEYCODE_MUTE` e `KEYCODE_VOLUME_MUTE` entram no mesmo contrato fisico Android Auto. Mute e
  play/pause sao toggles; duplicidade desfaz o estado esperado.
- O codigo OEM `1004` tambem representa `PLAY_PAUSE` no Android Auto. Se aparecer como input/eco,
  deve ser tratado como evento observado, sem `LinkCommand`, AAP hardkey, fallback OEM ou shell
  `input keyevent`.
- O fallback OEM de midia Android Auto (`input keyevent 1002/1003/1004`) deve ficar desligado por
  padrao. A funcao `shouldUseAndroidAutoOemOnlyMediaRoute(...)` precisa respeitar a flag geral
  `ANDROID_AUTO_OEM_INPUT_MEDIA_FALLBACK_ENABLED=false`.
- O `AccessibilityService` passa `ACTION_DOWN` e consome `ACTION_UP` dos toggles de midia enquanto
  Android Auto esta ativo (`play/pause`, `play`, `pause`, `mute`, `volume_mute`, `1004`). Essa
  protecao deixa o primeiro evento chegar na central e bloqueia o eco mais provavel. Ela nao se
  aplica a `next/previous`, CarPlay ou estado sem Android Auto ativo.
- Botoes explicitos do dashboard D0 para Android Auto usam `AndroidAutoNowPlayingMonitor`, com
  cooldown local para evitar reentrada/duplo clique. `next/previous` usam janela curta; `play/pause`
  usa janela maior por ser toggle. Esse caminho nao se aplica a evento fisico do volante.
- Esses comandos sao restritos a midia. Eles nao autorizam abrir/mover Activity, alterar Surface,
  foco, display, watchdog ou handoff D0/D3 do CarPlay.
- Para preview/release, logs de diagnostico do app ficam desligados. O build release usa R8 com
  `-maximumremovedandroidloglevel 7`; `ClusterPerfEventLogger` nao executa fora de debug e os logs
  do `CarPlayNowPlayingMonitor` ficam em lazy debug logging. Em 2026-06-12, `dexdump` do
  `minifyReleaseWithR8` confirmou ausencia de chamadas de emissao `Log.e/w/d/i` do app e ausencia
  dos marcadores `ClusterPerf`, `[PERF_EVENT]`, `CarPlay now playing...` e loop loggers no dex.

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

## Riscos

- Falha em Shizuku impede comandos privilegiados.
- Alterar `ForegroundService` pode quebrar boot.
- Alterar receivers pode impedir start após atualização/reboot.
- `ServiceManager` tem grande superfície de integração veicular.

## A Confirmar

- Sequência exata de inicialização em cold boot real na central.
- Quais permissões precisam ser concedidas manualmente em cada instalação.
