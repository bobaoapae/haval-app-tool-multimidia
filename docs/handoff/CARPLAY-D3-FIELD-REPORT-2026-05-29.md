# CarPlay D3 Field Report - 2026-05-29 18:22

## Estado Preservado

- Commit salvo: `290341e fix: stabilize CarPlay D3 native buffer baseline`.
- PR aberto para `preview`: `https://github.com/bobaoapae/haval-app-tool-multimidia/pull/84`.
- Baseline funcional: `native1904x704_v12`.
- `TsCarPlayApp.apk`: `ec5053d91d8364d9451937981e08a04a`.
- `TsCarPlayService.apk`: `f0269fc640778825843762dcf55a8b83`.
- D3 permanece com stack/window `[0,0][1920,720]`.
- Buffer nativo CarPlay validado: `1904x704`.

## Observacao de Campo do Usuario

Depois de a central ficar desligada por um periodo:

1. Primeira conexao USB: CarPlay abriu limpo no display 0.
2. Primeiro envio D0 -> D3: CarPlay apareceu sujo/cinza no D3.
3. Usuario desconectou e reconectou o USB.
4. Segunda conexao USB: CarPlay abriu limpo no display 0.
5. Segundo envio D0 -> D3: CarPlay apareceu limpo no D3.
6. No estado mais recente, AC/HVAC e camera/AVM nao geraram tela preta.

Em deslocamento anterior do mesmo dia, camera/AVM nao gerou tela preta, mas AC/HVAC ainda gerou
tela preta. Portanto, a falha parece depender de timing/estado frio e ainda nao esta declarada como
resolvida definitivamente.

## Captura Real Do Estado Sujo

Capturado em 2026-05-29 18:52, antes de reiniciar, reconectar USB ou alterar codigo:

- Baseline:
  `tools/headunit-dev/output/carplay-baseline-20260529-185249-cp-field-02-first-d3-dirty`
- Visual:
  `tools/headunit-dev/output/carplay-visual-20260529-185311-cp-field-02-first-d3-dirty`

Evidencia principal:

- Worktree limpo no momento da captura, commit `578732301f222939ea4c36e4740f8830e22ab51b`.
- `verify_regression_lock=pass`.
- `persist.haval.carplay.desired_display=3`.
- `persist.haval.carplay.video.height=720`.
- MD5 remoto:
  - `/system/app/TsCarPlayApp/TsCarPlayApp.apk` =
    `ec5053d91d8364d9451937981e08a04a`;
  - `/vendor/app/TsCarPlayService/TsCarPlayService.apk` =
    `f0269fc640778825843762dcf55a8b83`.
- `am stack list`:
  - CarPlay no display 3, stack `44`, task `10653`, bounds `[0,0][1920,720]`,
    visible `true`;
  - sem task visual CarPlay sustentada no display 0.
- `dumpsys activity`:
  - uma unica `CarPlayDisplayActivity` em display 3.
- `SurfaceFlinger`:
  - duas `SurfaceView` sob a mesma Activity:
    - `SurfaceView#0`: `size=(1920,720)`, `activeBuffer=[1x1:64,RGBx_8888]`,
      regiao visivel vazia;
    - `SurfaceView#1`: `size=(1904,704)`, regiao visivel `[0,0,1920,720]`,
      `activeBuffer=[1904x704:1920,Unknown 0x7fa30c06]`, transform aproximado
      `tr=[1.01,0][0,1.02]`.

Leitura inicial:

- O patch v12 estava carregado e o contrato de stack/window D3 estava correto.
- O estado sujo nao parece causado por AC/camera nem por CarPlay ter ido para D0.
- O ponto suspeito e a criacao/recriacao inicial da Surface no primeiro handoff frio: a Activity
  fica correta, mas o compositor preserva uma SurfaceView antiga `1x1` e a Surface real
  `1904x704` aparece como segunda SurfaceView sob a mesma Activity.
- A confirmacao depende de comparar esta captura com o segundo D0 -> D3 limpo apos reconexao USB.

## Bug Separado Observado - Volante/Audio

Usuario reportou que, ao mudar musica pelo volante, a central mostra a mensagem "sem entrada de
audio", mas a faixa muda mesmo assim.

Evidencia inicial coletada:

- Log/dumpsys salvos em `tools/headunit-dev/output/audio-key-issue-20260529-1854`.
- Busca local nao encontrou a string da mensagem no app Impulse; provavel origem nativa.
- `dumpsys media_session` mostrou `A2dpMediaBrowserService` ativo e audio playback com pacotes
  nativos/CarPlay.
- `dumpsys audio` mostrou players de `com.ts.carplay` (`uid/pid:1000/6795`) e eventos de USB-Audio
  iPhone.
- Preferencias remotas:
  - `enableInstrumentCustomMediaIntegration=true`;
  - `enableCustomMenu=true`;
  - `enableSteeringWheelCustomButtons=false`.
- Codigo candidato: `ServiceManager.callbackMsg(msgId=135)` responde `clusterService.setMsg(135,
  1/2)` quando a integracao customizada de midia do cluster esta ativa. Hipotese: a central nativa
  interpreta o comando de midia do cluster como troca de fonte/entrada sem audio, enquanto o CarPlay
  tambem recebe o evento e troca a faixa corretamente.

Proxima coleta para este bug deve isolar uma reproducao curta:

1. limpar logcat;
2. pressionar uma vez o comando fisico de proxima/anterior musica;
3. capturar `logcat`, `dumpsys media_session`, `dumpsys audio` e mensagens `ClusterService`/`BeanInputService`;
4. testar, se necessario, uma build/flag com `ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION` desligado
   para confirmar se o aviso some sem afetar CarPlay.

## Hipoteses A Confirmar

- O auto-mount v12 pode completar depois de alguma parte da primeira inicializacao do CarPlay.
- O primeiro renderer pode nascer com Surface/buffer/crop stale e so alinhar apos nova enumeracao
  USB.
- O primeiro handoff D0 -> D3 pode reutilizar instancia top-most ou dimensao herdada do D0.
- A reconexao USB pode forcar reabertura do host/decoder com `setSurfaceSize(1904,704)` no momento
  correto.

## Plano Para Quando A Central Estiver Acessivel

Nao alterar codigo antes de capturar as quatro fases:

```bash
HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-field-00-arrival-before-touch

HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-field-01-first-usb-d0-clean

HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-field-02-first-d3-dirty

HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-visual cp-field-02-first-d3-dirty

HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-field-03-second-usb-d0-clean

HEADUNIT_HOST=<ip> HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-baseline cp-field-04-second-d3-clean
```

Depois comparar:

```bash
./tools/headunit-dev/headunit.sh carplay-compare \
  tools/headunit-dev/output/carplay-baseline-<cp-field-02-first-d3-dirty> \
  tools/headunit-dev/output/carplay-baseline-<cp-field-04-second-d3-clean>
```

## Evidencia Que Deve Decidir A Proxima Correcao

- MD5s montados e `carPlayPatchAutoMountPatchVersion`.
- `persist.haval.carplay.desired_display` e `persist.haval.carplay.video.height`.
- `am stack list` e se houve duplicata D0/D3.
- `dumpsys window` com bounds reais da Activity.
- `SurfaceFlinger` com `SurfaceView` e `activeBuffer`.
- Logcat de `CarPlayPatchManager`, `DisplayAppLauncher`, `CarPlayDisplayActivity`,
  `SurfaceHolder`, `setSurfaceSize`, `onSurfaceChanged`, `CarPlayManager`,
  `requestVideoFocusChange`, `ScreenResourceManager`, `cpScreen`, `NdkMediaCodec` e
  `ActivityTaskManager`.

## Atualizacao 2026-05-29 19:55 - v13, boot staging e logs antes de ajuste

Regra operacional reforcada nesta rodada: antes de qualquer nova adequacao funcional, capturar
baseline/logs/screenshot do estado atual. O pacote usado para o estado "D3 fisicamente limpo, mas
captura por software cinza" ficou em:

- `tools/headunit-dev/output/manual-logs-20260529-1950-d3-clean-physical-capture-grey`
- `tools/headunit-dev/output/carplay-baseline-20260529-194555-cp-v13-auto-d3-clean-current`
- `tools/headunit-dev/output/carplay-visual-20260529-194621-cp-v13-auto-d3-clean-current`

Achados que motivaram o v13:

- Comparacao entre o primeiro D3 sujo e o segundo D3 limpo mostrou que ambos tinham patch montado,
  desired display `3`, video height `720`, stack D3 fullscreen e buffer nativo `1904x704`.
- O D3 sujo tinha uma `SurfaceView` antiga/invisivel `1x1` junto da Surface real `1904x704`.
- O D3 limpo tinha apenas a Surface real, com a mesma window/stack `1920x720`.

Mudancas aplicadas:

- `TsCarPlayApp.apk` v13:
  - chama `SurfaceHolder.setFixedSize(1904,704)` em display secundario antes de registrar o
    callback da Surface;
  - remove o `setFixedSize` que era executado dentro de `surfaceChanged`;
  - preserva a window/stack fisica do cluster em `[0,0][1920,720]`.
- `ForegroundService`:
  - atualiza a chave de auto-mount para
    `app_visual_d0_focus_service_conditional_camera_native1904x704_v13`;
  - dispara `CarPlayPatchManager.ensureMounted()` mais cedo, logo apos Shizuku pronto/bypassado,
    para evitar reboot com asset novo mas bind mount antigo.
- `DisplayAppLauncher`:
  - quando o alvo salvo e D3, permite que o boot/USB use D0 apenas como staging sem gravar
    `desiredCarPlayDisplayId=0`;
  - preserva `persist.haval.carplay.desired_display=3` durante uma janela curta de boot;
  - evita apagar o alvo D3 imediatamente quando a task visual ainda nao nasceu no inicio do boot.

MD5s protegidos atuais:

- `TsCarPlayApp.apk`: `9d48c33f49dbeeb020c2fdc7e16bbc53`.
- `TsCarPlayService.apk`: `f0269fc640778825843762dcf55a8b83`.

Evidencia remota apos deploy:

- `verify_regression_lock=pass`.
- `carPlayPatchAutoMountPatchVersion=app_visual_d0_focus_service_conditional_camera_native1904x704_v13`.
- `desiredCarPlayDisplayId=3`.
- `persist.haval.carplay.desired_display=3`.
- `persist.haval.carplay.video.height=720`.
- CarPlay no D3: stack `58`, task `10672`, bounds `[0,0][1920,720]`.
- App comum no D0 validado com Settings:
  - `tools/headunit-dev/output/carplay-baseline-20260529-195400-cp-v13-settings-d0-after-handoff`
  - `tools/headunit-dev/output/carplay-visual-20260529-195400-cp-v13-settings-d0-after-handoff`
  - Settings visivel no D0 e CarPlay permanecendo no D3.

Limites e riscos atuais:

- O usuario reportou que, ao conectar o cabo, o CarPlay apareceu no D0, foi para D3
  automaticamente e ficou limpo. Esse relato fisico e o melhor sinal atual do v13.
- `screencap -d 4` ainda pode sair cinza/washed mesmo com stack e Surface corretas. Tratar esse
  screenshot como evidencia de composicao/readback a investigar, nao como prova isolada de falha
  fisica quando o usuario reporta D3 limpo.
- Tentativa remota de abrir HVAC por `am start -n com.beantechs.hvac/.MainActivity` nao foi
  conclusiva porque a captura do D0 continuou em Impulse/Settings; HVAC fisico segue dependendo de
  acionamento real.
- Camera/AVM nao foi retestada nesta etapa do v13 por automacao; manter como validacao manual
  separada.

## Guardrails

- Nao reintroduzir `VIDEO_FOCUS_CHANGE` em evento de AC/camera/app enquanto CarPlay real segue no
  D3.
- Nao usar `force-stop com.ts.carplay.app` em handoff normal.
- Nao redimensionar a window do D3 para `1904x704`; o ajuste validado e apenas no buffer nativo.
- Nao mexer em Android Auto.
- AC/HVAC e camera/AVM devem permanecer testes de regressao obrigatorios, porque no melhor estado
  atual nao estao gerando tela preta.
