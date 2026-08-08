# Estrategia de Patch Nativo: Android Auto x CarPlay

Atualizado em: 2026-06-18 13:47 -03

## Objetivo

Documentar por que o projeto trata Android Auto e CarPlay com estrategias diferentes na central Haval/GWM, especialmente no contexto de projecao no cluster 3 e da regressao de tela preta ao acionar camera/AVM, AC/HVAC ou apps no display 0.

Este documento nao declara a tela preta do CarPlay resolvida. Ele registra a estrategia atual, as evidencias ja observadas e os limites que devem orientar os proximos testes fisicos.

## Resumo Executivo

Android Auto esta usando um caminho patchado e mais agressivo de recuperacao porque esse fluxo foi implementado, validado como montavel e documentado como melhor para dimensoes/foco no cluster.

Atualizacao operacional de 2026-06-18 19:21: apos regressao de conexao Android Auto, a central
`192.168.15.101` recebeu v216 `rollback-v194` preservando `AndroidAutoService.apk` stock
`54df14713bf26466af55a76382a67ce6`. O APK v194 exato foi encontrado e validado por SHA-256, mas
nao foi reinstalado porque embute `AndroidAutoService.apk` MD5
`4f07b9deeb7097a2b21de33935a702ca`. Ate haver nova evidencia fisica, rollback de app-side deve
preservar o nativo stock e nao trocar o service AA.

CarPlay saiu do estado stock para uma excecao controlada de foco D3 em 2026-05-28/29:
`TsCarPlayApp.apk` mantem `view_state=foreground` durante `onPause` e
`TsCarPlayService.apk` ignora a prioridade HVAC `0x6` e o release simetrico
`priority=0/action=1/borrowId=uiNotification`. O patch visual tambem ignora retorno normal de foco
do display 0 enquanto CarPlay continua no stack, e impede que `FINISH_ACTIVITY` finalize uma
Activity CarPlay que esta em display secundario. A variante atual preserva a protecao visual v7
para apps normais do D0 apos reboot e usa um service de camera condicional: camera/AVM permanece
stock quando o alvo desejado nao e D3, mas envia `sendMessage(6)` quando
`persist.haval.carplay.desired_display == 3`.
As variantes antigas continuam proibidas porque causaram crash, frame sujo ou retorno dos sintomas.

Portanto, a estrategia atual e:

- manter Android Auto no fluxo patchado e isolado;
- manter CarPlay no patch visual D3 v13, com auto-mount v14 `native1904x704`, sustentado por MD5 e sentinels;
- nao misturar comandos de recuperacao Android Auto com CarPlay;
- investigar CarPlay no caminho nativo de foco/video, principalmente `CarPlayManager.requestVideoFocusChange` e `ScreenResourceManager.screenResourceRequest`.
- tratar o foco do proprio app Haval no D0 como excecao app-side: se o D3 fica preto mas
  `am stack list`/WindowManager ainda mostram CarPlay fullscreen no cluster, confirmar
  `SurfaceView activeBuffer=1x1` no SurfaceFlinger e reassertar somente a Activity existente no D3
  com `REFRESH_RENDER` + `am start --display 3`, sem foco de video, sem resize e sem `force-stop`.
- antes de tratar um D3 sujo/cinza como regressao do app, repetir o envio D0 -> D3 com preflight:
  CarPlay aberto e limpo no D0, preparo `PREPARING_D3` pelo orquestrador e envio pelo fluxo do
  Impulse/app. `am start --display 3` direto continua permitido apenas como diagnostico.

Offset visual Android Auto do PR 116:

- `ENABLE_AA_CLUSTER_OFFSET` e default OFF e se aplica somente ao display 3;
- quando habilitado, os bounds passam de `[0,0,1920,720]` para `[offset,0,1920,720]`, preservando
  a borda direita e estreitando apenas a esquerda; o slider limita o ajuste a `0..400px`;
- a reaplicacao ao vivo redimensiona somente a task Android Auto existente no D3;
- nao altera APK/servico nativo, display 0, CarPlay, Surface CarPlay ou o patch v13/v14.

## Estado Atual dos APKs Nativos

| Item | Android Auto | CarPlay |
| --- | --- | --- |
| Pacote visual | `com.ts.androidauto.app` | `com.ts.carplay.app` |
| Pacote/host nativo | `com.ts.androidauto.projectionservice` / `com.ts.androidauto` | `com.ts.carplay` |
| Activity visual | `com.ts.androidauto.app.display.AapActivity` | `com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity` |
| Estrategia atual | APK patchado via bind mount em `/vendor/app/...` | Patch minimo em `/system/app/TsCarPlayApp` + `/vendor/app/TsCarPlayService` |
| Patch runtime | Ativo quando instalado/montado | Ativo para CarPlay visual D3 v13 / auto-mount v14 |
| Recuperacao permitida | Mais agressiva no app visual e foco | Conservadora, baseada em estado real e logs |
| Evidencia recente | v156 valida hardkeys por ACK dentro do processo system AA; efeito fisico ainda depende de teste no carro | HVAC corrigido; app normal no D0 validado por stack + screencap; camera/AVM ainda depende de teste fisico |

MD5s do estado protegido:

- `TsCarPlayApp.apk`: `9d48c33f49dbeeb020c2fdc7e16bbc53`;
- `TsCarPlayService.apk`: `f0269fc640778825843762dcf55a8b83`.

Verificacao estatica obrigatoria:

```bash
python3 scripts/carplay-patches/verify_regression_lock.py
```

## Por Que Android Auto Esta Patchado

Confirmado por codigo:

- `AndroidAutoPatchManager` instala patches a partir de `assets/aa_patches`.
- O patch e montado sobre `/vendor/app/AndroidAutoApp/AndroidAutoApp.apk`.
- O fluxo limpa oat/dalvik e reinicia os processos Android Auto para garantir carregamento do APK montado.
- `DisplayAppLauncher` chama `AndroidAutoPatchManager.ensureMounted()` antes do fluxo de handoff.
- O fluxo de Android Auto envia `ts.car.androidauto.view_state` e `com.ts.androidauto.action.AndroidAutoService` com comando `requestVideoFocus`.
- Se a Activity visual nao aparece no display alvo, o fluxo pode reiniciar `com.ts.androidauto.app` sem reiniciar necessariamente o servico principal de projecao.

Motivo tecnico:

- O Android Auto ja tinha problema conhecido de foco/dimensoes no cluster.
- O patch foi criado especificamente para melhorar dimensoes e foco.
- A propria UI do app descreve o patch como "Ajusta melhor as dimensoes do cluster e nao perde o foco".
- O fluxo de recuperacao Android Auto ficou isolado, sem depender de regras do CarPlay.

### Rota nativa de hardkeys Android Auto

Reverse realizado em 2026-06-17 na central `192.168.15.100` confirmou que os botoes de midia do
Android Auto nao passam apenas por `LinkCommand`/`MediaCenter`.

Artefatos relevantes:

- APKs puxados para `reverse/native-apks/system-dump-20260617/`;
- decompilados JADX em `reverse/jadx-androidauto-app-20260617/`,
  `reverse/jadx-inputservice-20260617/` e `reverse/jadx-ts-framework-20260617/`;
- `CoreSystemServer.vdex` extraido para
  `reverse/native-apks/system-dump-20260617/core-system-vdex-extracted/CoreSystemServer_classes.cdex`;
- disassembly em
  `reverse/dexdump-coresystemserver-cdex-20260617/CoreSystemServer_classes.dexdump.txt`;
- configs nativas em `reverse/native-configs/system-dump-20260617/keycode_config.json` e
  `reverse/native-configs/system-dump-20260617/keycode-ui.xml`.

Fluxo confirmado:

1. `com.ts.androidauto.app.model.hardkey.HardKeyModel` registra callback no
   `HardKeyPolicyManager` quando o link Android Auto fica `LINK_STATUS_ACTIVATED`.
2. O registro usa a cena `SCENE_ANDROID_AUTO = 505` para eventos normais e long press.
3. `keycode_config.json` declara `KEYCODE_MEDIA_LEFT`, `KEYCODE_MEDIA_RIGHT` e
   `KEYCODE_MEDIA_OK` como pertencentes a `scene_android_auto`.
4. `keycode-ui.xml` configura essas teclas com `scene_android_auto,14` e politica `3`
   (`delivery to next scene`).
5. `HardKeyPolicyManager.KEYCODE_MAP` traduz:
   - `KEYCODE_MEDIA_LEFT` -> `1002`;
   - `KEYCODE_MEDIA_RIGHT` -> `1003`;
   - `KEYCODE_MEDIA_OK` -> `1004`;
   - `KEYCODE_MEDIA_MUTE` -> `1006`.
6. `HardKeyInputPolicyManagerService.processHardKeyFromPolicy(KeyEvent, targetDisplay)` valida
   down/up, consulta os mapas carregados do XML/JSON e chama `notifyKeyEvent(event, scene,
   targetDisplay, longPress)`.
7. `HardKeyModel.onKeyEvent(...)` aceita apenas `scene == 505`, exige `mCurrentScene == 0`, e
   mapeia `1002/1003/1004` para `AAP_KEYCODE_MEDIA_PREVIOUS/NEXT/PLAY_PAUSE`.
8. `HardKeyModel.handleMediaKeyEvent(...)` so envia DOWN para o Android Auto quando
   `mIsMediaFocus == true`; esse flag vem de `AudioExtManager` quando algum `AudioFocusInfo`
   reporta `usage == 28`.

Implicacoes:

- A rota mais fiel ao volante nativo para `prev/next/play/pause` e entregar, dentro do processo
  system do Android Auto, um par DOWN/UP para
  `HardKeyPolicyManager.processHardKeyFromPolicy(KeyEvent(ACTION_DOWN/UP, 1002/1003/1004),
  targetDisplay)`, nao chamar diretamente `LinkCommand.pause()` ou `input keyevent 85/87/88`.
- O segundo argumento dessa API e `targetDisplay`, nao a cena. A cena `505` vem do mapa/config e
  do callback registrado pelo Android Auto.
- `KEYCODE_MEDIA_MUTE` nao tem `scene_android_auto` nos configs nativos desta central; mute/unmute
  deve permanecer separado pela rota de volume/adapter ja diagnosticada, salvo nova evidencia.
- Como `AndroidAutoApp.apk` em `/vendor/app/AndroidAutoApp/AndroidAutoApp.apk` esta montado a
  partir de `/data/local/tmp/aa_patches/AndroidAutoApp.apk`, este reverse reflete o estado patchado
  atual da central.

Implementacao atual em 2026-06-17:

- A tentativa direta pelo Impulse/Shizuku/root foi descartada. `input_policy` valida UID system ou
  a permissao protegida `android.car.permission.BIND_CAR_INPUT_SERVICE`; app/UID root fora do
  processo system nao passa nessa checagem.
- `AndroidAutoApp.apk` foi patchado para registrar dinamicamente, no
  `AndroidAutoRemoteUiService`, um receiver interno para
  `br.com.redesurftank.havalshisuku.AA_MEDIA_COMMAND`.
- O receiver aceita somente token conhecido, displays `0`/`3` e keycodes `1002/1003/1004`, chama
  `HardKeyPolicyManager.processHardKeyFromPolicy(...)` para DOWN/UP e retorna
  `resultCode=1` apenas quando despachou o comando.
- O receiver estatico tambem existe no APK, mas o `PackageManager` nao o expôs no mesmo boot apos
  bind mount; a rota efetiva de mesma sessao e o receiver dinamico registrado pelo service.
- O Impulse usa `AndroidAutoHardKeyPolicyBridge`: inicia
  `com.ts.androidauto.app/.AndroidAutoRemoteUiService`, aguarda brevemente, envia broadcast
  ordenado com ACK e registra falha se o resultado nao for `1`.
- APK nativo validado na central `192.168.15.100`:
  `/vendor/app/AndroidAutoApp/AndroidAutoApp.apk` e
  `/data/local/tmp/aa_patches/AndroidAutoApp.apk` com MD5
  `2465e5dcd6fa4dc78d90cf3af5bf21ce`.
- Validacao tecnica v156:
  - broadcast nativo `1003`/D3: `result=1`;
  - broadcast nativo `1004`/D3: `result=1`;
  - broadcast nativo `1006`/D3: `result=0`, esperado;
  - debug Impulse `aa_hardkey_next_d3` e `aa_hardkey_pause_d3`: ACK `resultCode=1`.
- Pendente: validar fisicamente que o ACK resulta em troca de faixa/pausa real no Android Auto em
  D0 e D3. Se `play/pause` ainda falhar com ACK, investigar `mIsMediaFocus`, `mCurrentScene`,
  `USAGE_AAUTO_MEDIA` e o app de audio do telefone.

Atualizacao 2026-06-17 21:13 - card Android Auto sem toggle:

- O decompile do MediaCenter mostrou que o fluxo nativo Android Auto chama
  `DeviceMirrorManager.pause()/play()` e retorna sucesso sem validar efeito fisico.
- A camada `AapController.pause()` so envia `sendPauseKey(true/false)` quando
  `isAapConnected()` esta verdadeiro; com `aaLink=NO_DEVICE_OR_POWER(-1)` o Binder pode aceitar a
  chamada sem o telefone receber pausa.
- Por isso, o card/debug do Impulse nao deve usar `PLAY_PAUSE` como substituto do fluxo nativo.
  A v186 deixa o card em comandos por alvo: `KEYCODE_MEDIA_PAUSE`/AAP hardkey `8` para pausar e
  `KEYCODE_MEDIA_PLAY`/AAP hardkey `7` para tocar.
- Se a sessao AA estiver desconectada (`NO_DEVICE_OR_POWER`, `aaDevices=[]`), `sent=true`/ACK nao
  comprova funcionamento. O teste fisico so e conclusivo com link `ACTIVATED(3)` ou `AAP_FRX(8)`.

Atualizacao 2026-06-17 22:13 - FM/source 12 abandonado para play/pause:

- A hipotese de usar o caminho do radio como "freio" do Spotify/Android Auto foi testada na v193
  com source `12`.
- O resultado nao sustentou pause: o audio AA pausou apos comandos nativos de radio, mas retomou
  sozinho poucos segundos depois.
- A v194 remove esse caminho do fluxo normal e dos debug commands `aa_pause_brake` /
  `aa_pause_fm_brake`.
- OEM audio focus hold tambem nao roda mais automaticamente dentro do `aa_pause`; permanece apenas
  como diagnostico manual.
- Proibicao operacional: nao usar FM/source `12` como fallback de botao Android Auto. Se a UI de
  radio/FM manual resolve algo, mapear o fluxo completo da UI/servicos nativos antes de qualquer
  nova implementacao.

Atualizacao 2026-06-18 13:47 - AndroidAutoService reportAction para play/pause:

- A v205 preservou a guarda nativa contra autoplay apos pause, mas o teste limpo do card/debug
  ainda falhou: `KEYCODE_MEDIA_PAUSE` e AAP hardkey `8` retornaram `sent=true`, enquanto o AA
  seguia em `aaMusic=NOT_START(0)` e o audio real continuava em
  `USAGE_AAUTO_MEDIA state:started` no processo `com.ts.androidauto`.
- A v206 muda o ponto nativo de menor alcance antes da saida do comando: `AapController` passa a
  chamar `mGal.mediaPlaybackStatus.reportAction(126/127)` antes de `play()`, `pause()`,
  `sendKeyEvent(II)` para `ACTION_DOWN` de `126/127`, `sendMediaPlayKey(true)` e
  `sendMediaPauseKey(true)`.
- Essa rota nao tenta trocar source, nao usa FM/source `12`, nao usa `PLAY_PAUSE` generico, nao
  usa `input keyevent` e nao altera `mute`, `next` ou `prev`.
- A validacao fisica deve procurar `IMPULSE_MEDIA_PATCH reportAction action=127 result=` no log e
  so considerar sucesso se o Spotify permanecer pausado depois da janela sustentada. Se o pause
  ainda nao sustentar, a proxima investigacao deve continuar no controlador nativo AA/telefone,
  nao em MediaCenter/radio.

Atualizacao 2026-06-18 14:41 - reload correto do service Android Auto:

- Na central `192.168.33.194`, o pacote real que hospeda o service nativo e
  `com.ts.androidauto.projectionservice`, embora o processo apareca como `com.ts.androidauto`.
- Durante o deploy v207, o APK patchado estava montado com MD5 correto, mas o processo
  `com.ts.androidauto` ainda tinha `ELAPSED` anterior ao mount. `am force-stop com.ts.androidauto`
  nao bastou porque nao era o nome do pacote instalado.
- `AndroidAutoPatchManager` deve parar `com.ts.androidauto.projectionservice`, manter
  `com.ts.androidauto` apenas como compatibilidade legado e tambem parar o app visual
  `com.ts.androidauto.app`.
- Regra operacional: apos trocar `AndroidAutoService.apk`, confirmar nao so o bind mount/hash em
  `/vendor/app`, mas tambem que o processo `com.ts.androidauto` foi reiniciado depois do mount.

Atualizacao 2026-06-18 18:55 - transporte real antes de metadata/comando AA:

- A central pode manter `source=402` no MediaCenter e Activity visual Android Auto mesmo quando a
  sessao de midia real esta em Bluetooth.
- Evidencia v215 na central `192.168.33.206`:
  - `dumpsys media_session` tinha apenas `com.android.bluetooth/A2dpMediaBrowserService`;
  - Bluetooth A2DP/AVRCP estava conectado ao `moto g56 5G`;
  - Android Auto estava sem link/audio real (`NO_DEVICE_OR_POWER`, `mMusicActiveMs=0`, sem
    `USAGE_AAUTO_MEDIA state:started`);
  - o MediaCenter ainda reportava source `402`.
- Regra: `source=402` e Activity visual nao bastam para rotear o card como Android Auto. Para
  publicar metadata nativa AA, preservar estado AA ou enviar comandos AA pelo card, exigir
  transporte real: link/USB AA ativo ou audio `USAGE_AAUTO_MEDIA state:started`.
- Se a sessao real for Bluetooth, o card deve permanecer em `mediaPackage=com.android.bluetooth` e
  usar `MediaController` Bluetooth. Esse caminho foi validado com play e pause sustentado por mais
  de 30s na v215.
- O problema de `play/pause` Android Auto puro continua separado: so deve ser testado quando o
  transporte AA real estiver ativo.

Consequencia:

- Android Auto tem uma combinacao de APK patchado + foco explicito + recuperacao visual dedicada.
- Para hardkeys de midia, a chamada nativa tambem fica encapsulada no APK patchado do AA, porque e
  o contexto que satisfaz as validacoes de `input_policy`.
- Isso explica por que ele pode sobreviver melhor a AC/camera/app no display 0.

## Atualizacao 2026-06-07 - Contrato App-side Android Auto D3

O Android Auto passa a ter contrato app-side proprio para o cluster 3, sem reaproveitar o contrato
nem o watchdog do CarPlay:

- D0 e D3 usam sempre fullscreen fisico resolvido por display: `[0,0][largura,altura]`.
- `getEffectiveBounds()` e `resizeApp()` tratam `com.ts.androidauto.app` como excecao fullscreen,
  evitando que o tema virtual do D3 aplique bounds parciais como `[0,62][1920,658]`.
- O alvo desejado do Android Auto fica separado em `desiredAndroidAutoDisplayId`.
- Mudancas de janela no D0, abertura de apps, AC/HVAC e camera/AVM acionam um guard exclusivo do
  Android Auto quando o AA esta no D3 ou quando D3 e o alvo desejado; se CarPlay estiver ativo no
  D3, o guard de AA fica inerte.
- O guard de AA pode reaplicar fullscreen, enviar `requestVideoFocus` do Android Auto e restaurar a
  Activity visual para o D3. Essa agressividade continua proibida para CarPlay.
- Comandos de midia do volante sao roteados para Android Auto somente quando o AA esta realmente
  ativo no D3. O caminho cobre `KeyEvent` de midia e `ClusterService msgId=135`; para CarPlay, o
  comportamento existente de `msgId=135` permanece inalterado.

Risco residual:

- A direcao exata de `msgId=135` (`1`/`2`) para anterior/proxima musica precisa de validacao fisica
  no Android Auto. A implementacao atual assume `1=previous` e `2=next`.

Atualizacao 2026-06-07 19:00 - Android Auto e paineis nativos:

- Teste fisico reportado pelo usuario na build instalada mostrou que Android Auto nao perde foco no
  D3 ao abrir AC/camera, mas o menu AC fecha em menos de 2s e, ao fechar camera, o D3 pisca preto e
  volta.
- Ajuste local: eventos de AC/HVAC/AVM/camera agora usam contrato passivo de Android Auto
  (`VERIFY_ONLY`), sem `requestVideoFocus` nem resize quando a task do AA ja esta viva no D3.
- O guard forte (`FULLSCREEN_AND_FOCUS`) continua para apps comuns no D0 e restore real de AA D3.
- `AccessibilityService` tambem trata `com.beantechs.hvac` e pacotes com `avm/camera/backcamera`
  como paineis nativos para evitar roubar foco do D0.
- O envio de midia do volante para AA usa o binder nativo `LinkCommand.sendKeyEvent(ordinal, action)`
  com sequencia DOWN/UP; `input keyevent` fica apenas como fallback quando o binder nao estiver
  disponivel.

Atualizacao 2026-06-07 19:40 - Diagnostico persistente Android Auto:

- Novo `AndroidAutoLoopLoggerService` interno replica o modelo observacional do logger CarPlay para
  Android Auto, sem alterar os fluxos de CarPlay.
- O servico fica restrito a `debug`/`leanDebug` via `internalDebug`; release/preview nao deve conter
  esse diagnostico.
- A captura foi direcionada aos sintomas ainda abertos:
  - AC/HVAC fechando rapido de forma intermitente;
  - camera/AVM piscando preto ao fechar;
  - botoes do volante next/previous sem efeito no AA.
- O logger grava estado D0/D3, dumps de WindowManager/SurfaceFlinger/services, configuracao nativa
  das teclas de volante, logcat filtrado AA/HVAC/AVM/media e RAW D0/D4 sob limite.
- A sessao iniciada na central em 2026-06-07 19:42 deve ser usada para a proxima analise antes de
  nova correcao funcional.

Atualizacao 2026-06-07 20:10 - Ajuste por evidencia fisica:

- AC foi reportado pelo usuario como OK e nao recebeu nova mudanca.
- Camera/AVM:
  - logs mostraram AA vivo no D3 com Activity e Surface ativas durante `sys.avm.preview_status`
    `1 -> 0`;
  - ao fechar AVM, o app envia apenas `view_state foreground`/`requestVideoFocus` do Android Auto
    para o display 3, se a task D3 continuar viva;
  - nao faz start, recreate, resize ou limpeza de stacks nesse caminho.
- Volante:
  - a central entregou `KEYCODE_MEDIA_PREVIOUS/NEXT` por `IInputService` com `ACTION_UP`;
  - o roteamento de media do AA agora aceita `ACTION_DOWN` e `ACTION_UP`, mantendo debounce para
    evitar duplicidade caso os dois eventos sejam entregues em outra central/firmware.
- CarPlay permanece isolado e nao foi alterado nesta correcao.

Atualizacao 2026-06-07 20:22 - Ajuste apos teste fisico da build 20:08:

- O comando direto `LinkCommand` `0x18/0x19` retornou `sent=true`, mas nao controlou a midia no
  teste fisico.
- A nova tentativa envia tambem `LinkCommand.sendKeyEvent` com AAP hardkey `DOWN/UP` para
  `previous/next`.
- O pulso pos-camera foi antecipado para imediato + verificacoes curtas, porque o pulso tardio
  executou mas nao eliminou o blink.
- CarPlay continua isolado.

Atualizacao 2026-06-07 20:50 - Fallback OEM e pulso durante AVM:

- A transicao `D3->NONE->D0` observada no logger foi explicada pelo usuario como desconexao USB e
  nao deve ser tratada como regressao do contrato D3.
- Como `LinkCommand.next/previous` e `sendKeyEvent` AAP `DOWN/UP` retornaram sucesso sem efeito
  fisico, o AA agora tenta tambem uma rota OEM de midia:
  - `KEYCODE_MEDIA_NEXT -> input keyevent 1003`;
  - `KEYCODE_MEDIA_PREVIOUS -> input keyevent 1002`;
  - `KEYCODE_MEDIA_PLAY_PAUSE -> input keyevent 1004`.
- O fallback OEM e restrito a Android Auto ativo no D3 e fica logado com `oemInput=true` para
  calibracao no teste fisico. Se causar duplo comando, remover ou condicionar essa rota.
- Camera/AVM agora recebe pulso leve de foco tambem enquanto o painel nativo esta aberto
  (`preview_status=1`), antes dos pulsos pos-fechamento existentes. O caminho continua sem start,
  recreate, resize ou logica CarPlay.

Atualizacao 2026-06-07 21:07 - Loop do fallback OEM:

- Teste fisico da build `20:48:14` confirmou que o fallback OEM causava loop:
  - `input keyevent 1003/1002` retornava no `IInputService` como `KEYCODE_MEDIA_NEXT/PREVIOUS`
    (`87/88`) com `ACTION_UP`;
  - o handler tratava esse eco como novo clique e reenviava fallback.
- A build `21:06:33` mantem o fallback OEM, mas marca uma janela de bloqueio de `2_500ms` para o
  mesmo keycode apos cada envio. Logs esperados:
  - `Blocking OEM media fallback echo...`;
  - `Skipping OEM input fallback echo`.
- Esse bloqueio e isolado ao Android Auto D3 e nao altera CarPlay.

Atualizacao 2026-06-07 21:23 - Fallback OEM desabilitado e telemetria nativa:

- Usuario confirmou que a transicao `D3->NONE->D0->D3` da sessao `21:07` foi causada por
  desconectar/reconectar USB, portanto nao e regressao do contrato D3.
- Como o fallback OEM nao resolveu a troca de musica e ja provou risco de eco, a build local
  desabilita `input keyevent 1003/1002/1004` por padrao.
- O volante permanece restrito ao Android Auto ativo no D3 e usa:
  - `LinkCommand.next/previous`;
  - `LinkCommand.sendKeyEvent` AAP hardkey `DOWN/UP`.
- A nova telemetria le `LinkCommand.getLinkStatus` e `LinkCommand.getMusicStatus` antes/depois de
  cada comando. Isso diferencia "comando aceito pelo binder" de "Android Auto/telefone realmente
  consumiu o comando".
- Camera/AVM nao recebeu nova agressividade nesta etapa; a evidencia atual segue compativel com
  blink transiente da rota nativa de video enquanto a Activity/Surface do AA permanece viva no D3.

Atualizacao 2026-06-09 10:36 - Next/previous em rota unica OEM:

- Usuario reportou que o volante voltou a funcionar no Android Auto, mas cada clique passa 2 ou 3
  musicas.
- A central estava em `172.20.10.2`, com app instalado `lastUpdateTime=2026-06-07 21:06:33`;
  portanto a build local que desabilitava OEM nao estava instalada.
- Logs historicos da build `21:06:33` mostraram `direct=true`, `aap=true` e `oemInput=true` no
  mesmo clique. Quando mais de uma dessas rotas e consumida pelo AA/telefone, o resultado fisico e
  multiplos skips.
- A estrategia foi ajustada: para `KEYCODE_MEDIA_NEXT/PREVIOUS`, usar apenas `OEM_ONLY`
  (`input keyevent 1003/1002`) com bloqueio de eco. Nao enviar `LinkCommand.next/previous` nem AAP
  hardkey `DOWN/UP` para o mesmo clique.
- Se o problema persistir com log `route=OEM_ONLY`, a proxima frente deve investigar repeticao
  fisica do evento e janela de debounce/eco, nao reintroduzir rotas paralelas.

Atualizacao 2026-06-09 10:53 - Botoes fisicos de midia pela rota nativa da headunit:

- Testes fisicos posteriores mostraram que a rota `OEM_ONLY` ainda duplicava `next/previous` e que
  `pause/resume` tambem enviava dois comandos.
- Evidencia: o evento fisico original ja passa pela headunit/Android Auto, e o listener
  `IInputListener.dispatchKeyEvent()` do Impulse nao consome esse evento porque o callback e
  `void`. Qualquer injecao app-side vira um segundo comando.
- A estrategia atual para evento fisico de volante e:
  - `NEXT`, `PREVIOUS`, `PLAY_PAUSE`, `PLAY` e `PAUSE` usam somente a rota nativa da headunit;
  - o Impulse nao envia `LinkCommand`, AAP hardkey nem `input keyevent 1002/1003/1004`;
  - o Impulse apenas registra `using headunit native route only` e envia pulso leve de foco para
    preservar Android Auto no D3.
- `ClusterService msgId=135` permanece separado do evento fisico observado e pode usar o caminho
  app-side quando necessario.
- CarPlay continua isolado e nao foi alterado nesta correcao.

Atualizacao 2026-08-05 - corrida de boot do icone CarPlay na barra nativa:

- Logs da central mostraram o SystemUI tentando resolver `CarPlayService` antes de o host montado
  disponibilizar o servico. Musica/link podiam funcionar depois, mas a barra lateral mantinha o
  bind ausente ate um restart posterior do SystemUI.
- O watchdog agora diferencia `SERVICE_NOT_READY` de `MISSING` e pode preparar o bind antes da
  primeira conexao USB, somente uma vez por `boot_id` e dentro dos primeiros `120s`.
- A confirmacao `MISSING` usa a mesma geracao de host PID, SystemUI PID e `ServiceRecord`, com duas
  releituras de `2s`; qualquer troca de geracao reinicia a contagem.
- Antes de reiniciar somente o SystemUI, o fluxo relê USB, geracao, estado do bind e velocidade.
  Apenas velocidade conhecida no intervalo `0..0,5km/h` e cooldown global expirado autorizam a
  acao.
- O caminho conectado reutiliza o mesmo snapshot de host/servico para relevancia e estado do bind,
  evitando o `dumpsys` duplicado anterior. Fora da janela de boot nao existe polling adicional de
  prewarm.
- Nao houve mudanca nos APKs/patches nativos, Activity CarPlay, Surface, foco, display, handoff
  D0/D3, WebView ou Android Auto. Persistencia do listener prearmado e tempo real de aparicao do
  icone continuam A confirmar na central.

Atualizacao 2026-08-05 - prewarm por restart desabilitado apos regressao v296:

- A v296 executou `BOOT_SERVICE_READY_PREWARM` aos `44s`, matou o SystemUI e terminou a verificacao
  do novo bind em `MISSING/verified=false`.
- No mesmo cold boot, o menu lateral nativo do display 0 desapareceu. O usuario confirmou que um
  gesto de pinca na tela fez o menu voltar, e o WindowManager voltou a mostrar a NavigationBar em
  `[0,0][128,720]`.
- A politica atual mantem o watchdog somente como observador. Boot, conexao e desconexao USB nao
  podem mais executar restart automatico de `com.android.systemui`.
- Recuperacao manual do SystemUI fica restrita a diagnostico explicito com o veiculo parado. O
  atraso do icone CarPlay e menos grave que remover o menu nativo inteiro.
- A camada visual de projecao tambem nao escolhe mais `Mapa`: ela preserva o modo de display atual e
  somente a acao do usuario pode trocar `Normal`, `Reduzido`, `Clean` ou `Mapa`.
- Os bundles precompilados `SportRed`/`SportRedLite` 0.16.44 ainda contem um override interno para
  `Mapa Limpo`. `ProjectionDisplayHtmlPolicy` remove essa assinatura em memoria antes do
  `loadDataWithBaseURL`; o arquivo baixado e a preferencia permanecem intactos e a politica vira
  no-op quando o tema for corrigido na origem.

Atualizacao 2026-06-11 21:52 - Toggle Android Auto sem fallback OEM:

- O fallback OEM de midia Android Auto (`input keyevent 1002/1003/1004`) permanece proibido por
  padrao, inclusive para `PLAY_PAUSE=1004`.
- `1004` deve ser reconhecido como input/eco de `PLAY_PAUSE`, mas apenas observado/consumido pelo
  Impulse.
- Decisao substituida em 2026-06-12 13:42: nesta etapa, o `AccessibilityService` consumia apenas
  repeticao curta de teclas toggle Android Auto ativo; depois passou por uma tentativa de consumir
  o toggle inteiro e por outra de passar `ACTION_DOWN`/consumir `ACTION_UP`. A regra atual nao
  consome toggles pelo Accessibility, mantendo `next/previous` fora dessa trava.
- Essa regra nao toca em CarPlay, Surface, foco, bounds, handoff D0/D3 ou fluxo sem Android Auto
  ativo.

Atualizacao 2026-06-12 01:33 - Pre-start CarPlay e midia Android Auto:

- CarPlay:
  - o atalho D0/D3 pode preparar a UI nativa antes de abrir/mover a Activity visual;
  - o preparo permitido e bindar `com.ts.carplay/.CarPlayService`, chamar `getLinkStatus()` e, se
    o retorno for `2`, chamar `requestUi(0)`;
  - esse comportamento replica o que o fluxo nativo `CarPlayDisplayActivity`/`LinkStatusModel` faz
    ao bindar no servico;
  - a relevancia do icone CarPlay na barra nativa D0 passa a considerar host, servico e link vivo,
    nao apenas a Activity visual;
  - continua proibido usar esse sintoma para enviar `force-stop`, broadcast de foco/video,
    `view_state foreground`, `REFRESH_RENDER`, resize parcial ou restore agressivo.
- Android Auto:
  - eventos fisicos de `pause/play/mute` seguem sem reenvio app-side, porque sao toggles e
    duplicidade desfaz a acao;
  - `next/previous` fisicos usam excecao controlada desde `2026-06-12 10:59`: rota app-side apenas
    no `ACTION_UP`, reaproveitando o caminho do card de midia, porque a rota nativa nao estava
    passando musica no teste fisico;
  - o `AccessibilityService` nao consome toggles Android Auto ativos; `pause/play/mute` fisicos
    ficam sem reenvio app-side e sem consumo parcial pelo Impulse;
  - o keepalive de foco Android Auto so pulsa quando a fonte AA esta tocando e nao esta mutada;
  - midia Android Auto nao deve ser limpa apenas por USB sysfs desconectado se sessao/projecao
    continuar ativa, especialmente em wireless/hotspot;
  - fallback de metadata/capa de Bluetooth/MediaCenter so e aceito quando a frente atual continua
    sendo Android Auto, sem afetar CarPlay.

Atualizacao 2026-06-12 23:18 - Contrato de capa/metadata Android Auto:

- `dumpsys media_session` nao e fonte confiavel para capa/metadata Android Auto nesta central:
  pode mostrar apenas `com.android.bluetooth` desconectado enquanto a musica do Android Auto toca.
- O caminho preferencial do dashboard para capa/metadata Android Auto e o Binder nativo do
  `com.beantechs.mediacenter`, somente quando `IPlayService.getCurrentSource()` ou
  `getCurrentAudioSource()` retorna a fonte Android Auto `402`.
- Nesse estado, o Impulse pode chamar `IPlayService.getPlayMediaInfoBySource(402)` por transacao
  `22` e publicar o retorno como Android Auto (`com.ts.androidauto`), lendo apenas os campos
  necessarios de `MediaInfo`: `title`, `author`, `albumId`, `imageUrl`, `imageBitmap` e `duration`.
- Fora da fonte nativa `402`, `com.beantechs.mediacenter` e
  `com.beantechs.mediacenter.h5.core` continuam proibidos como fallback generico. Isso evita
  regredir o bug em que a radio nativa era sobrescrita pelo Android Auto.
- Se o MediaCenter emitir update posterior sem `imageBitmap` enquanto a fonte segue Android Auto,
  o card deve preservar a ultima capa valida. A capa so deve ser limpa por troca real de fonte,
  desconexao/limpeza Android Auto ou nova metadata com capa valida de outra faixa.
- Esta regra e Android Auto-only: nao altera CarPlay, Surface, foco, bounds, handoff D0/D3 ou
  fluxo sem Android Auto ativo.

Atualizacao 2026-06-13 08:55 - Progresso Android Auto via MediaCenter:

- O polling de metadata do MediaCenter nao pode chamar `updateMediaProgressState(...)` com o mesmo
  `elapsedMs` antigo e um `updatedAtMs` novo. Isso faz o dashboard avancar um segundo e voltar no
  proximo polling, congelando visualmente em dois valores proximos.
- Quando a fonte nativa Android Auto `402` esta ativa, o Impulse tambem consulta
  `IPlayService.getPlayStateBySource(402)` por transacao `19` e usa `duration`/`currentProgress`
  como fonte preferencial do tempo do card.
- Se o estado nativo nao trouxer elapsed, o progresso da mesma faixa e estimado pelo ultimo
  `mediaElapsedMs` + delta desde `mediaProgressUpdatedAtMs`. Se a metadata indicar troca real de
  faixa e nao houver elapsed nativo, o tempo zera.
- A barra de progresso Android Auto continua visual-only: esta regra nao reabilita seek/scrub,
  nao altera comandos de midia, CarPlay, Surface, foco, bounds ou handoff D0/D3.

Atualizacao 2026-06-13 09:19 - Play/pause Android Auto fora do MediaCenter:

- A transacao nativa do MediaCenter para `pauseMediaBySource(402)`/`resumeMediaBySource(402)`
  retornou sucesso no log, mas o teste fisico reportado mostrou que a musica nao pausava pelo card
  nem pelo volante.
- Portanto, o MediaCenter nao deve ser usado para toggles Android Auto. A rota permitida para
  `play/pause` e `AndroidAutoNowPlayingMonitor.pause()/play()`, com fallback para
  `DisplayAppLauncher.sendAndroidAutoDashboardPlaybackCommand(...)` se o Binder do monitor nao
  estiver pronto.
- `next/previous` permanecem separados. A partir de 2026-06-16, somente `next` pode continuar pela
  rota MediaCenter `402`; `previous` deve usar `AndroidAutoNowPlayingMonitor.previous()`/
  `LinkCommand.previous`, porque `playPreviousBySource(402)` retornou sucesso sem efeito fisico em
  teste posterior.
- `clear` do NowPlaying durante sessao Android Auto ativa deve preservar `mediaIsPlaying`; limpar
  esse estado durante a projecao ativa faz o proximo toggle inverter a decisao.
- Esta regra e Android Auto-only e nao altera CarPlay, Surface, foco, bounds, handoff D0/D3 ou seek.

Atualizacao 2026-06-13 09:34 - Next/previous fisico Android Auto nativo-only:

- Logs da build `106` mostraram que um clique fisico de volante em `next/previous` ja chegava ao
  Android Auto pela rota nativa da headunit e, em seguida, o Impulse enviava outro comando via
  MediaCenter `source=402` no `ACTION_UP`.
- A excecao de 2026-06-12 10:59 para skip app-side em evento fisico fica substituida: volante
  `KEYCODE_MEDIA_NEXT/PREVIOUS` deve ser observado pelo Impulse, mas nao deve enviar comando
  app-side.
- O card de midia permanece separado e pode continuar usando sua rota explicita para
  `next/previous`; a proibicao aqui e somente para evento fisico de volante.
- Confirmacao fisica posterior do usuario: `prev/next` ficaram funcionando no Android Auto.
- Regra substituida para playback fisico em 09:45: `play/pause` do volante tambem fica
  nativo-only.
- Esta regra e Android Auto-only e nao altera CarPlay, Surface, foco, bounds, handoff D0/D3 ou seek.

Atualizacao 2026-06-16 - Supersede da reconciliacao MediaCenter v120:

- A tentativa v120 de aplicar alvo tardio `pauseMediaBySource(402)`/`resumeMediaBySource(402)` para
  volante Android Auto fica revertida. Na pratica, esses comandos podem concorrer com o processamento
  nativo do proprio volante e produzir o ciclo observado de pausar e voltar a tocar.
- O contrato v127 e:
  - volante fisico Android Auto fora da fonte MediaCenter `402`: nativo-only para `next`,
    `previous`, `play/pause`, `play`, `pause` e `mute`, sem comando app-side e sem reconciliacao
    tardia;
  - volante fisico Android Auto com fonte MediaCenter `402` ativa: tambem nativo-only. Logs da
    central `192.168.33.49` em v126 mostraram `KEYCODE_MEDIA_NEXT` fisico mais
    `playNextBySource(402)` app-side imediato, seguido por duas trocas de faixa;
  - card Android Auto: `next` pode usar `playNextBySource(402)` quando fonte MediaCenter `402` esta
    ativa;
  - card Android Auto: `previous` usa monitor/LinkCommand, nao `playPreviousBySource(402)`;
  - card Android Auto: `play/pause` com fonte MediaCenter `402` ativa usa
    `pauseMediaBySource(402)`/`resumeMediaBySource(402)` em comando unico;
  - mute fisico Android Auto com fonte `402` usa o toggle nativo de audio e reaplica o alvo somente
    se a central desfizer o mute sozinha.
- MediaCenter `402` segue valido para metadata, capa, progresso e play state consultado.
- Esta regra e Android Auto-only e nao altera CarPlay, Surface, foco, bounds, handoff D0/D3 ou seek.
  Se o volante precisar de fallback em algum estado futuro, ele deve ser tardio e condicionado a
  ausencia real de mudanca nativa, nunca imediato em paralelo com a tecla fisica.
- A v128 implementa esse fallback tardio apenas para `play/pause` quando o Android Auto esta com
  alvo desejado no D3. `next/previous` continuam nativo-only no volante. Antes de enviar
  `pauseMediaBySource(402)`/`resumeMediaBySource(402)`, o app consulta o play state nativo; se o
  alvo ja foi atingido, nao envia comando. Essa regra nao se aplica ao card D0, que continua com
  comandos explicitos MediaCenter.
- A v129 ajusta o calculo do alvo desse fallback tardio: quando a fonte MediaCenter Android Auto
  `402` esta disponivel, o toggle `play/pause` do volante usa o play state nativo como fonte
  primaria, e nao `BottomBarState.mediaIsPlaying`, porque o estado visual pode ficar stale apos o
  handoff D0 -> D3. Se a leitura nativa falhar, o estado visual continua como fallback. O mute
  target-aware tambem passa a considerar qualquer valor real divergente como motivo para reassert.
  Esta regra segue Android Auto-only e nao altera CarPlay, bounds, Surface ou handoff.
- A v130 adiciona dedupe de callback fisico do `InputService` para Android Auto. Logs da central
  mostraram o mesmo `KEYCODE_MEDIA_PLAY_PAUSE`/`KEYCODE_MUTE` chegando duas vezes no mesmo ms apos
  Android Auto ir para D3; no mute isso gerava duas solicitacoes de toggle e podia produzir
  `mute:1 -> mute:0`. O app agora consome callbacks identicos `keyCode + action` dentro de `280ms`
  antes de qualquer hint, reconciliacao ou comando. Esta regra nao altera CarPlay.
- A v131 ajusta a reconciliacao tardia de `play/pause` Android Auto D3: o play state nativo `402`
  pode reportar alvo ja atingido de forma stale, portanto a reconciliacao nao usa mais esse estado
  para abortar o comando. Ela envia o alvo idempotente `pauseMediaBySource(402)` ou
  `resumeMediaBySource(402)` apos o atraso. O reassert de mute tambem passa a agendar as janelas de
  `3.8s` e `7.2s` em paralelo a partir do toque. Esta regra nao altera CarPlay.
- A v139 reativa essa reconciliacao tardia para o caso observado na central `192.168.33.143`: o
  botao fisico `KEYCODE_MEDIA_PLAY_PAUSE` chegou ao MediaCenter com `currentSource=402`, mas o
  nativo registrou `isPhoneLinkCarMediaSource=false` e nao tratou a tecla. A regra continua sem
  rota app-side imediata: o evento fisico aplica hint visual, agenda o alvo e depois envia
  `pauseMediaBySource(402)`/`resumeMediaBySource(402)` se a geracao ainda for atual. `next` e
  `previous` continuam nativo-only para nao recriar duplo skip; mute continua no fluxo proprio de
  audio.
- A v140 supersede a v139 e as excecoes de comando MediaCenter. Logs da central mostraram que o
  MediaCenter `402` aceitava transacoes de comando, mas o audio real seguia tocando. A partir dela,
  o MediaCenter `402` fica restrito a metadata/capa/progresso/play state. Card Android Auto usa
  `AndroidAutoNowPlayingMonitor`/`LinkCommand` para `next`, `previous`, `play` e `pause`; se o
  monitor falhar, o fallback direto tambem e `LinkCommand`. `play/pause` usa alvo explicito
  `PLAY`/`PAUSE`, sem toggle baseado em estado stale. A reconciliacao tardia do volante tambem usa
  `LinkCommand`; `next/previous` do volante so enviam fallback tardio por `LinkCommand` se a faixa
  nao mudar apos a janela de espera.
- A v181 supersede apenas o caso inconclusivo do card. Quando a fonte nativa Android Auto `402`
  esta ativa e o estado retorna `NOT_START(0)` sem audio ativo, o card nao deve inferir `PLAY`.
  Ele envia `MEDIA_PLAY_PAUSE` via `AndroidAutoHardKeyPolicyBridge`/patch
  `AndroidAutoRemoteUiService`, aguardando ACK `dispatched=true`. Essa excecao nao reabilita
  comandos MediaCenter `402` e nao se aplica ao volante fisico.
- A v157 amplia a reconciliacao tardia de `play/pause` fisico para D0 e D3 quando o MediaCenter
  Android Auto esta ativo. Evidencia da central `192.168.15.100`: `KEYCODE_MEDIA_PLAY_PAUSE`
  chegou pelo `InputService`, `currentSource=402`, mas `isPhoneLinkCarMediaSource=false`, e a regra
  anterior nao agendou fallback porque `desiredOnCluster` nao estava ativo. A partir da v157, esse
  caso agenda `_RECONCILE_1` por alvo explicito; `next/previous` e `mute` permanecem isolados.

Atualizacao 2026-06-13 09:45 - Play/pause fisico Android Auto nativo-only:

- Usuario confirmou `prev/next` OK e reportou novo bug: `play/pause` duplicado no volante e no
  card de midia.
- Logs da build `107` mostraram `KEYCODE_MEDIA_PLAY_PAUSE` fisico chegando pelo `IInputService` e
  o Impulse enviando comando app-side em seguida. Como `play/pause` e toggle, app-side apos rota
  nativa pode desfazer a acao esperada.
- Volante fisico `KEYCODE_MEDIA_PLAY_PAUSE/PLAY/PAUSE/1004` passa a ser observado e nativo-only:
  nao reenviar por LinkCommand, MediaCenter, AAP hardkey, OEM input ou shell.
- Card de midia continua separado: usa `AndroidAutoNowPlayingMonitor.pause()/play()` com cooldown.
  Se o monitor ja existe e retorna `false`, nao cair em fallback direto no mesmo toque; fallback
  direto fica reservado para ausencia do monitor.
- Esta regra e Android Auto-only e nao altera CarPlay, Surface, foco, bounds, handoff D0/D3 ou seek.

Atualizacao 2026-06-13 09:52 - Estado visual de playback Android Auto:

- Apos a build `108`, o usuario reportou que o pause funcionou uma vez pelo volante, mas o icone do
  card nao mudou. Em seguida, o card decidiu o proximo comando com estado antigo e o fluxo regrediu.
- Causa encontrada: `IPlayService.getPlayStateBySource(402)` pode retornar
  `MediaPlayStateInfo` com `mSrc=0` mesmo quando o log nativo mostra `currentSrc=402`. O Impulse
  filtrava esse playState fora por nao ser `402`, preservando `mediaIsPlaying` antigo durante
  `clear` continuo do NowPlaying.
- O contrato atual aceita `mSrc=0` como estado da fonte consultada `402`, aplica
  `PLAYING/PAUSED` mesmo quando a metadata vem vazia, e usa esse estado para corrigir o icone do
  card.
- Evento fisico de volante tambem aplica um hint visual imediato no `BottomBarState`; o polling
  nativo corrige logo depois se necessario.
- Esta regra e Android Auto-only e nao altera CarPlay, Surface, foco, bounds, handoff D0/D3 ou seek.

## Por Que CarPlay Nao Esta Mais Stock

Confirmado por codigo:

- `CarPlayPatchManager` tem `PATCH_RUNTIME_ENABLED = true`.
- `ensureMounted()` instala e monta `TsCarPlayApp.apk` e `TsCarPlayService.apk`.
- `ForegroundService` usa a chave `app_visual_d0_focus_service_conditional_camera_native1904x704_v14` para ativar o auto-mount.
- Se o mount muda enquanto a task visual do CarPlay ja esta ativa, o manager recarrega
  `com.ts.carplay.app` e `com.ts.carplay` para carregar o dex novo e reabre a Activity visual no
  display onde ela estava. Isso e restrito ao carregamento de patch, nao ao handoff normal.

Confirmado por historico/logs salvos:

- O APK stock atual esperado e `/system/app/TsCarPlayApp/TsCarPlayApp.apk`.
- MD5 stock esperado: `6c4815c20732b3643b008c85063fead6`.
- Sem `/data/local/tmp/carplay_patches` e sem bind mount ativo.
- Variante patchada `3ce0a58270607f0e854638cfab809a39` crashou com `IllegalAccessError`.
- Variante patchada `9a64672d3f4f69376b8a24c55431b5e9` abriu, mas voltou ao problema com bounds parciais.

Motivo tecnico:

- CarPlay e mais sensivel a recriacao de Activity, foco de video e decoder.
- `VIDEO_FOCUS_CHANGE` pode renegociar rota de video e produzir frames pretos.
- `force-stop com.ts.carplay.app` pode derrubar a sessao visual e exigir reconexao.
- Os testes com patch nativo nao provaram ganho; ao contrario, introduziram crash ou frame sujo/preto.

Consequencia:

- O CarPlay fica em patch minimo e versionado, nao em patches visuais antigos.
- O visual app ignora `priority=0/action=1/borrowId=""` de apps normais do display 0 quando o
  CarPlay ainda esta no stack ou quando `persist.haval.carplay.desired_display == 3`; isso evita
  `changeVideoFocus` para AppList/app normal no D0.
- O visual app ignora `FINISH_ACTIVITY` quando o receiver pertence a uma Activity em display
  secundario; isso evita que o retorno para AppList no D0 remova a task do CarPlay no D3.
- O visual app ignora `requestVideoFocus(1/2)` no display secundario, preservando o finish stock no
  display 0.
- O service patch embarcado trata HVAC e camera condicional:
  entrada `0x6` e fechamento `priority=0/action=1/borrowId=uiNotification` sao roteados para
  `sendMessage(6)`. Camera `0x7` e `backCameraStatusChangedTo(APP_ON/OFF)` ficam stock quando
  `persist.haval.carplay.desired_display != 3`, e tambem sao roteados para `sendMessage(6)` quando
  o alvo desejado e D3.
- A correcao atual nao deve reabilitar restores agressivos nem misturar Android Auto. A excecao
  permitida e objetiva: se o alvo desejado do usuario continua sendo D3 e a central nativa remove a
  Activity visual do CarPlay ou recria o visual no D0, o watchdog pode recriar a Activity no D3 sem
  `force-stop` e limpar duplicata somente depois que o D3 existir.
- Com o patch atual preservando HVAC/apps do D0 e a camera validada sem o service camera v7, a camada `InstrumentProjector2` nao deve
  mais esconder a `Presentation` por `windowAlpha=0` durante painel nativo. O display escolhido pelo
  usuario deve permanecer visivel/transparente sobre o CarPlay no D3; esconder a WebView remove o
  overlay selecionado sem corrigir foco ou decoder.

## Diferenca de Recuperacao Entre Android Auto e CarPlay

Android Auto:

- pode fazer `requestVideoFocus` via comando proprio;
- pode reiniciar a Activity visual `com.ts.androidauto.app` como ultimo recurso;
- pode reaplicar foco em passes posteriores;
- tem patch nativo conhecido no fluxo.

CarPlay:

- nao deve receber `force-stop com.ts.carplay.app` em handoff normal;
- nao deve receber `VIDEO_FOCUS_CHANGE` em eventos de camera/AC quando ja esta vivo no display 3;
- nao deve ser redimensionado exceto quando houver violacao objetiva do contrato fullscreen;
- deve preservar stack/surface sempre que possivel;
- deve usar verificacao/cooldown quando a task esta viva no D3;
- pode recriar a Activity visual no D3 quando o alvo desejado e D3, USB segue configurado e nao ha
  task visual ativa ou a task ficou sustentada no D0.

Essa diferenca e intencional. Copiar o comportamento agressivo do Android Auto para o CarPlay ja mostrou risco de tela preta, bounce entre displays ou perda da task visual.

## Evidencia Que Mudou a Hipotese

O usuario reportou em 2026-05-26 que Android Auto nao sofre o mesmo problema da tela preta ao acionar camera/AC.

Confirmado por codigo:

- A camada comum do cluster (`InstrumentProjector2` + frontend) trata CarPlay e Android Auto de forma muito parecida quando qualquer projecao real esta no display 3.
- Se a causa principal fosse apenas CSS/WebView/Presentation, seria esperado que Android Auto sofresse sintoma semelhante.

Confirmado por logs salvos do CarPlay:

- CarPlay dispara `CarPlayManager.requestVideoFocusChange` durante eventos de camera/AVM/HVAC.
- Ja houve logs `cpScreen` / `NdkMediaCodec` com erro `-38`.
- Ja houve estado ruim de fullscreen: bounds `[0,62][1920,658]`, Window requested `1920x596`, Surface `1920x596`.
- Tambem ja houve estado com fullscreen correto `1920x720`, mas feed visual preto.
- Em 2026-05-31, o usuario confirmou fisicamente que qualquer funcao no display 0 deixava o D3
  preto, enquanto Camera/AVM nao deixava. No mesmo estado, `am stack list` e WindowManager ainda
  mostravam `com.ts.carplay.app/.ui.display.view.CarPlayDisplayActivity` no display 3 com
  `[0,0][1920,720]`, mas o SurfaceFlinger mostrava o `SurfaceView` do CarPlay com
  `activeBuffer=[1x1]`. Ao acionar Camera/AVM, os logs mantiveram o CarPlay na lista de foco junto
  do `backcamera priority: 7`; ao focar o app Haval, o CarPlay saiu da lista de foco e a Surface
  ficou stale.
- Ainda em 2026-05-31, apos build v72, o usuario abriu CarPlay no D0 e um envio direto por Telnet
  colocou a Activity fullscreen no D3 com Surface `1904x704`, mas o D3 ficou fisicamente
  sujo/cinza e continuou sujo apos reconexao USB. O usuario confirmou que AC e camera nao deixaram
  preto. Essa evidencia deve ser repetida pelo fluxo preparado do app antes de escolher nova camada
  de correcao.

Leitura atual:

- A causa mais provavel esta no host nativo do CarPlay, nao na camada generica do cluster.
- A diferenca Android Auto x CarPlay deve ser usada como comparativo para isolar foco/video/surface.
- Para o caso especifico do foco do proprio app no D0, a correcao aceita fica na camada app-side e
  nao no patch nativo: detectar `activeBuffer=1x1` e reassertar a Activity existente no D3 sem
  renegociar foco de video. Camera/AVM permanece fora dessa excecao porque o teste fisico mostrou
  que nao escureceu o D3.

## Estrategia Atual de Teste

Antes do teste:

- Patch CarPlay D3 v7 deve continuar confirmado por MD5.
- `persist.haval.carplay.video.height` deve ser `720`.
- `/data/local/tmp/app.html` deve permanecer ausente para validar HTML embarcado.
- `projectionNativePanelFallbackActive` deve permanecer desabilitado.

Durante teste fisico:

- Para CarPlay, preparar o D0 antes do D3: abrir pelo icone nativo, aguardar feed limpo e enviar
  pelo fluxo do Impulse/app.
- Testar Android Auto no cluster 3 em roteiro separado.
- Acionar AC/HVAC e app no display 0.
- Acionar camera/AVM somente por ultimo e manualmente.
- Coletar diagnostico read-only com `tools/headunit-dev/diagnose-projection-focus-compare.sh`.
- Repetir exatamente o mesmo roteiro com CarPlay.

Comparar:

- `am stack list`;
- `dumpsys window windows`;
- `dumpsys SurfaceFlinger --list`;
- logs de `requestVideoFocus`, `requestVideoFocusChange`, `ScreenResourceManager`, `VideoResource`, `cpScreen`, `NdkMediaCodec`;
- presenca ou ausencia da Activity visual no display 3;
- bounds e Surface reais.

## Direcao Para Uma Correcao Definitiva

A correcao definitiva do CarPlay provavelmente nao vira de CSS, transparencia ou fallback visual.

O proximo caminho tecnico deve ser:

1. confirmar com logs comparativos que Android Auto mantem feed enquanto CarPlay apaga;
2. localizar no APK/smali do CarPlay o ponto exato de `CarPlayManager.requestVideoFocusChange`;
3. verificar a relacao com `ScreenResourceManager.screenResourceRequest`;
4. manter o patch nativo HVAC-only/release enquanto a camera/AVM fisica preservar o feed no display 0 e o
   CarPlay no D3;
5. manter rollback facil para APK stock.

Um novo patch CarPlay so deve ser considerado se:

- nao crashar ao abrir CarPlay;
- nao produzir frame branco/sujo no display 0;
- nao alterar Android Auto;
- preservar `persist.haval.carplay.video.height=720`;
- manter stack/window `1920x720` no cluster 3; a SurfaceView pode usar buffer nativo validado
  `1904x704` escalado para fullscreen;
- passar no roteiro AC/camera/app display 0 sem derrubar a sessao.

## Regras de Preservacao

- Nao misturar Android Auto e CarPlay.
- Nao usar comandos Android Auto para recuperar CarPlay.
- Nao reativar patches CarPlay antigos.
- Nao usar `force-stop com.ts.carplay.app` como correcao normal.
- Nao insistir em fallback visual do cluster para esconder preto do CarPlay.
- Nao declarar resolvido sem teste fisico no veiculo.
- Qualquer patch nativo CarPlay deve ser minimo, documentado, reversivel e validado contra APK stock.

## Arquivos Relacionados

- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/AndroidAutoPatchManager.kt`
- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/CarPlayPatchManager.kt`
- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/DisplayAppLauncher.kt`
- `app/src/main/java/br/com/redesurftank/havalshisuku/projectors/InstrumentProjector2.kt`
- `docs/carplay-cluster-regression-contract.md`
- `tools/headunit-dev/diagnose-projection-focus-compare.sh`

## Renovacao dos clientes OEM apos recarga do host CarPlay (2026-08-07)

O bind externo e o manager interno sao duas camadas diferentes. A troca do processo
`com.ts.carplay` por `SIGTERM` preserva os binds `BIND_AUTO_CREATE` registrados no
ActivityManager, mas o SDK OEM `com.ts.carplay.manager.CarPlay` mantem os objetos em `mServiceMap`.
No callback de desconexao, ele grava `mConnectionState=0` antes de chamar `disconnect()`; a chamada
retorna imediatamente e nao executa `tearDownCarPlayManagers()`. Assim, SystemUI e AppList recebem
o novo `onServiceConnected`, mas `getCarPlayManager()` devolve o manager que ainda aponta para o
Binder morto.

A estrategia da v310 e restrita ao momento em que `CarPlayPatchManager` confirma uma nova geracao
do host:

1. aguardar o host substituto estabilizar;
2. renovar apenas o fragmento da NavigationBar por uma mudanca de recursos do SystemUI;
3. restaurar o estado original do overlay OEM usado como gatilho;
4. confirmar PID do SystemUI preservado e janela `NavigationBar` presente;
5. renovar separadamente o processo do AppList por sinal direto, sem estado force-stopped.

Essa sequencia nao toca no renderer CarPlay, Activity D0/D3, Surface, video focus, WebView,
resolucao ou Android Auto. O teste manual no mesmo boot restaurou os dois icones. O cold boot
seguinte da v310 tambem confirmou a automacao: SystemUI foi renovado e preservado antes da pinca;
quando o AppList, ausente no boot, foi aberto normalmente, criou um manager novo e exibiu o segundo
icone ativo.
