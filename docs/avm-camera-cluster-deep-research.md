# AVM Camera on Cluster - Deep Research

Data: 2026-04-25

## Objetivo

Investigar se existe alguma rota tecnica para exibir a camera AVM/re no cluster usando a interface Android da HUT, sem quebrar o funcionamento original do veiculo.

## Limites de Seguranca

Nao executar nesta fase:

- `tsh.reg ExecCmd` ou `SetConfig`.
- `qcarcam_edrm_rvc` em modo display.
- abertura ativa de `/dev/video*`.
- Camera2 dentro do app em conducao.
- injecao em processo vendor, `ptrace`, `LD_PRELOAD`, Frida ou hook de SurfaceFlinger.
- qualquer alteracao persistente em properties, init, HAL, AVM, RVC ou display vendor.

Tudo abaixo e analise, coleta passiva ou proposta de prototipo controlado.

## Fontes Locais

Capturas e artefatos relevantes:

- `tools/headunit-dev/output/hut-architecture-validate-20260425-100025`
- `tools/headunit-dev/output/evs-route-check-raw-20260425-100635`
- `tools/headunit-dev/output/evs-config-check-20260425-100806`
- `tools/headunit-dev/output/hal-manifest-check-20260425-100853`
- `tools/headunit-dev/output/video-device-map-20260425-101039`
- `tools/headunit-dev/output/offline-analysis-20260425-014656/offline-avm-analysis.md`
- `tools/headunit-dev/output/avm-vendor-investigation-20260425-005015/tsh-reg-static-analysis.md`

## Fontes Tecnicas Externas

- Android Automotive EVS: `https://source.android.com/docs/automotive/camera/evs`
- EVS Camera HAL: `https://source.android.com/docs/automotive/camera/evs/camera-hal`
- EVS Display Proxy: `https://source.android.com/docs/automotive/camera/evs/display_proxy`
- Android Vehicle System Isolation: `https://source.android.com/docs/automotive/security/vehicle_system_isolation`

## Achados Confirmados

### 1. EVS Android existe no sistema, mas nao esta ativo como rota padrao

Arquivo:

- `tools/headunit-dev/output/evs-config-check-20260425-100806/android-evs-manager.rc.txt`

Conteudo relevante:

```text
#service evs_manager /system/bin/android.automotive.evs.manager@1.0
#    class hal
#    priority -20
#    user automotive_evs
#    group automotive_evs
#    onrestart restart evs_app
```

Interpretacao: o manager EVS padrao do Android esta presente, mas comentado no init. Isso sugere que a montadora nao usa a rota AOSP EVS pura para a camera principal, ou substituiu essa integracao por stack vendor.

### 2. QCarCam HAL vendor esta declarado e ativo

Arquivos:

- `tools/headunit-dev/output/evs-config-check-20260425-100806/qcarcam-service.rc.txt`
- `tools/headunit-dev/output/hal-manifest-check-20260425-100853/vendor-vintf-manifest.xml`

Conteudo relevante:

```text
service qcarcam_hal /vendor/bin/hw/vendor.qti.automotive.qcarcam@1.0-service
    class hal
    priority -20
    user system
    group system camera input
```

Manifesto:

```text
vendor.qti.automotive.qcarcam@1.0::IQcarCamera/default
```

Interpretacao: existe uma interface HIDL Qualcomm para camera automotiva. Esta e a rota tecnicamente mais promissora, mas nao e uma API Java/WebView comum. Um cliente teria que falar HIDL/vendor, respeitar permissoes e nao competir com o pipeline original.

### 3. qcarcam_edrm_rvc existe, mas esta desabilitado por init

Arquivo:

- `tools/headunit-dev/output/evs-config-check-20260425-100806/qcarcam-edrm-rvc.rc.txt`

Conteudo relevante:

```text
#service vendor.qcarcam_edrm_rvc /vendor/bin/qcarcam_edrm_rvc
#service vendor.qcarcam_edrm_rvc /vendor/bin/qcarcam_edrm_rvc -noDisplay -dumpFrame=90
#    class core
#    user system
#    group camera input graphics
#    oneshot
#    disabled
```

Interpretacao: ha ferramenta Qualcomm para RVC, inclusive com modo dump frame, mas ela nao esta ativa. O teste anterior com `qcarcam_edrm_rvc -noDisplay -dumpFrame=1` falhou em `qcarcam_open()`. Isso pode indicar ausencia de permissao, estado de camera nao autorizado pelo QNX, parametros errados, ou filtro do hypervisor.

### 4. Android Camera2 nao parece ser a rota correta

`dumpsys media.camera` mostra 8 devices, mas sem clientes ativos quando capturado:

```text
Active Camera Clients:
[]
```

Camera2 ja foi testado e retornou tela verde. Isso e coerente com camera virtual/loopback filtrada, buffer protegido ou ausencia de permissao de cena.

### 5. Existem devices V4L2, mas acesso ativo ainda nao foi testado

Arquivo:

- `tools/headunit-dev/output/video-device-map-20260425-101039/dev-video-list.txt`

Conteudo:

```text
crw-rw---- 1 system camera 81, 51 /dev/video51
crw-rw---- 1 system camera 81, 52 /dev/video52
```

Interpretacao: existem endpoints V4L2 expostos ao Android. Eles podem ser loopbacks do AIS/QCarCam. A abertura desses devices pode iniciar stream ou competir com provider, entao deve ser tratada como teste ativo e feito apenas parado.

### 6. Display routing Android funciona, video routing nao esta provado

`dumpsys display` confirma displays Android:

- Display 0: main `1920x720`.
- Display 1: cluster HDMI `1920x720`.
- Display 2: passenger HDMI `1280x720`.
- Display 3: quarto HDMI `1920x720`.
- Display 4096: aux `480x240`.

Nosso app ja usa Presentation/WebView no cluster. O problema nao e desenhar no cluster; o problema e obter o frame real da camera original.

## Matriz de Rotas Possiveis

| Rota | Viabilidade | Risco | Status |
|------|-------------|-------|--------|
| WebView/Presentation no cluster | Alta | Baixo | Ja funciona para UI propria |
| Indicador `CAMERA ATIVA` por estado logico | Alta | Baixo | Ja funciona |
| Android Camera2 | Baixa | Medio | Tela verde, nao insistir |
| EVS AOSP manager | Media baixa | Medio | Binario existe, init comentado, nao ativo |
| QCarCam HIDL `IQcarCamera` | Media | Alto | Melhor candidato tecnico, exige cliente nativo privilegiado |
| `/dev/video51/52` V4L2 | Media baixa | Medio/alto | Possivel loopback, precisa teste parado |
| `qcarcam_edrm_rvc -dumpFrame` | Baixa/media | Alto | Falhou antes, pode competir com RVC |
| `tsh.reg GetConfig` leitura | Media | Medio | Pode revelar estado; precisa cliente controlado |
| `tsh.reg ExecCmd/SetConfig` | Descartada agora | Alto | Pode alterar AVM/RVC |
| Hook/injecao em processo vendor | Tecnicamente possivel | Muito alto | Nao recomendado no veiculo |
| Hook SurfaceFlinger/screencap | Baixa | Medio | Camera provavelmente fora do SF |
| QNX/NFS direto | Baixa | Alto | Sem shell QNX, NFS expõe dados, nao pipeline grafico |

## O Que Um "Injetor" Conseguiria

### Injetor em SurfaceFlinger

Poderia capturar/republicar surfaces Android comuns. Nao resolve se a camera e overlay QNX/hardware fora do SurfaceFlinger. Risco alto de derrubar UI do cockpit.

### Injetor no app/servico que recebe estado AVM

Poderia interceptar estados como `SCENE_BACKCAMERA`, `backupCamera`, `sys.avm.preview_status`. Isso ja temos por caminhos mais seguros. Nao entrega frame.

### Injetor em `vendor.qti.automotive.qcarcam@1.0-service`

Tecnicamente e o ponto mais proximo do frame Android. Poderia observar chamadas, buffers e parametros. Risco muito alto: processo HAL de camera, permissao system/camera, possivel impacto na camera original.

### Injetor em `tshregserver`

Poderia observar comandos/eventos AVM vindos do QNX/FDB. Bom para estado e enums. Pouco provavel que transporte frame de video. Risco alto se alterar fluxo.

### Cliente HIDL proprio em vez de injecao

Mais limpo que hook. Criar um binario nativo separado que conecta em `vendor.qti.automotive.qcarcam@1.0::IQcarCamera/default`, enumera entradas e tenta dump de 1 frame em condicao controlada. Ainda e teste ativo e deve ser feito parado.

## Plano Tecnico Recomendado

### Fase 1 - Offline, sem central

1. Extrair localmente:
   - `/vendor/bin/hw/vendor.qti.automotive.qcarcam@1.0-service`
   - `/vendor/lib64/vendor.qti.automotive.qcarcam@1.0.so`
   - `/vendor/lib64/libais_client.so`
   - `/system/bin/qcarcam_hidl_test`
   - `/vendor/bin/qcarcam_test`
   - `/vendor/bin/qcarcam_edrm_rvc`
2. Fazer `strings`, `readelf`, `nm` quando possivel e Ghidra/JADX onde aplicavel.
3. Mapear nomes de metodos, enums, camera IDs, usos de `/dev/video51/52`, formatos e parametros.
4. Confirmar se `qcarcam_hidl_test` aceita argumentos seguros de enum/list sem abrir stream.

### Fase 2 - Probe passivo na central

1. Coletar `logcat`/`dmesg` durante ativacao/desativacao de camera.
2. Coletar deltas de `/proc/interrupts` para `vm2-hab_cam1`, `vm2-hab_vid`, `vm2-hab_disp*`.
3. Coletar `dumpsys media.camera` antes/durante/depois.
4. Coletar `SurfaceFlinger --list` antes/durante/depois.

Essa fase nao abre camera nem chama comando vendor.

### Fase 3 - Probe ativo minimo, somente parado

Somente se a fase offline indicar uma chamada de leitura segura:

1. Criar binario nativo separado, nao integrado ao app.
2. Rodar manualmente, com carro parado.
3. Fazer apenas enum/list/open de uma entrada por poucos segundos.
4. Nunca alterar configuracao persistente.
5. Encerrar processo e validar que camera original continua normal.

### Fase 4 - Integracao possivel

Se e somente se houver frame valido:

1. Nao integrar direto no app principal.
2. Criar servico experimental separado, desligado por padrao.
3. Expor para o cluster como textura/surface local ou endpoint local somente em modo debug.
4. Fallback sempre para UI original e indicador de estado.

## Conclusao Atual

Nao e correto dizer que e fisicamente impossivel. Existe pelo menos uma rota tecnica candidata: `vendor.qti.automotive.qcarcam@1.0::IQcarCamera/default`, talvez combinada com V4L2 loopback.

Mas tambem nao e correto dizer que da para fazer pelo nosso app Android atual. WebView/Kotlin nao tem acesso direto ao frame. O caminho exigiria engenharia reversa nativa de HAL vendor, permissao privilegiada e testes controlados.

Minha recomendacao: avancar para analise offline do QCarCam/EVS antes de qualquer novo teste ativo na central.
