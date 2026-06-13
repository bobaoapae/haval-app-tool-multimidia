# Estrategia de Patch Nativo: Android Auto x CarPlay

Atualizado em: 2026-06-12 01:33 -03

## Objetivo

Documentar por que o projeto trata Android Auto e CarPlay com estrategias diferentes na central Haval/GWM, especialmente no contexto de projecao no cluster 3 e da regressao de tela preta ao acionar camera/AVM, AC/HVAC ou apps no display 0.

Este documento nao declara a tela preta do CarPlay resolvida. Ele registra a estrategia atual, as evidencias ja observadas e os limites que devem orientar os proximos testes fisicos.

## Resumo Executivo

Android Auto esta usando um caminho patchado e mais agressivo de recuperacao porque esse fluxo foi implementado, validado como montavel e documentado como melhor para dimensoes/foco no cluster.

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
- manter CarPlay no patch D3 v13 `native1904x704`, sustentado por MD5 e sentinels;
- nao misturar comandos de recuperacao Android Auto com CarPlay;
- investigar CarPlay no caminho nativo de foco/video, principalmente `CarPlayManager.requestVideoFocusChange` e `ScreenResourceManager.screenResourceRequest`.
- tratar o foco do proprio app Haval no D0 como excecao app-side: se o D3 fica preto mas
  `am stack list`/WindowManager ainda mostram CarPlay fullscreen no cluster, confirmar
  `SurfaceView activeBuffer=1x1` no SurfaceFlinger e reassertar somente a Activity existente no D3
  com `REFRESH_RENDER` + `am start --display 3`, sem foco de video, sem resize e sem `force-stop`.
- antes de tratar um D3 sujo/cinza como regressao do app, repetir o envio D0 -> D3 com preflight:
  CarPlay aberto e limpo no D0, preparo `PREPARING_D3` pelo orquestrador e envio pelo fluxo do
  Impulse/app. `am start --display 3` direto continua permitido apenas como diagnostico.

## Estado Atual dos APKs Nativos

| Item | Android Auto | CarPlay |
| --- | --- | --- |
| Pacote visual | `com.ts.androidauto.app` | `com.ts.carplay.app` |
| Pacote/host nativo | `com.ts.androidauto.projectionservice` / `com.ts.androidauto` | `com.ts.carplay` |
| Activity visual | `com.ts.androidauto.app.display.AapActivity` | `com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity` |
| Estrategia atual | APK patchado via bind mount em `/vendor/app/...` | Patch minimo em `/system/app/TsCarPlayApp` + `/vendor/app/TsCarPlayService` |
| Patch runtime | Ativo quando instalado/montado | Ativo para CarPlay D3 v13 |
| Recuperacao permitida | Mais agressiva no app visual e foco | Conservadora, baseada em estado real e logs |
| Evidencia recente | Usuario reportou que nao reproduz a tela preta | HVAC corrigido; app normal no D0 validado por stack + screencap; camera/AVM ainda depende de teste fisico |

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

Consequencia:

- Android Auto tem uma combinacao de APK patchado + foco explicito + recuperacao visual dedicada.
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

Atualizacao 2026-06-11 21:52 - Toggle Android Auto sem fallback OEM:

- O fallback OEM de midia Android Auto (`input keyevent 1002/1003/1004`) permanece proibido por
  padrao, inclusive para `PLAY_PAUSE=1004`.
- `1004` deve ser reconhecido como input/eco de `PLAY_PAUSE`, mas apenas observado/consumido pelo
  Impulse.
- Decisao substituida em 2026-06-12 10:59: nesta etapa, o `AccessibilityService` consumia apenas
  repeticao curta de teclas toggle Android Auto ativo; depois passou por uma tentativa de consumir
  o toggle inteiro. A regra atual passa `ACTION_DOWN` e consome `ACTION_UP`, mantendo
  `next/previous` fora dessa trava.
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
  - o `AccessibilityService` passa `ACTION_DOWN` e consome `ACTION_UP` dos toggles Android Auto
    ativos para permitir o primeiro evento nativo e bloquear o eco mais provavel;
  - midia Android Auto nao deve ser limpa apenas por USB sysfs desconectado se sessao/projecao
    continuar ativa, especialmente em wireless/hotspot;
  - fallback de metadata/capa de Bluetooth/MediaCenter so e aceito quando a frente atual continua
    sendo Android Auto, sem afetar CarPlay.

## Por Que CarPlay Nao Esta Mais Stock

Confirmado por codigo:

- `CarPlayPatchManager` tem `PATCH_RUNTIME_ENABLED = true`.
- `ensureMounted()` instala e monta `TsCarPlayApp.apk` e `TsCarPlayService.apk`.
- `ForegroundService` usa a chave `app_visual_d0_focus_service_conditional_camera_native1904x704_v13` para ativar o auto-mount.
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
  mais esconder a `Presentation` por `windowAlpha=0` durante painel nativo. O display efetivo
  `Mapa` deve permanecer visivel/transparente sobre o CarPlay no D3; esconder a WebView remove os
  widgets do Mapa sem corrigir foco ou decoder.

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
