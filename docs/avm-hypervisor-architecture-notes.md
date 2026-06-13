# AVM Hypervisor Architecture Notes

Data: 2026-04-25

## Hipotese Atual

Informacao recebida externamente indica que a multimidia pode rodar sobre uma arquitetura virtualizada/hypervisor:

- Um host/OS isolado com boot rapido teria acesso direto ao hardware real, camera, cluster/HUD e rede CAN/Cambus.
- O Android visivel para desenvolvimento seria um guest/OS virtualizado.
- O Android receberia sinais filtrados de estado, mas nao o fluxo real de video da camera.
- A tela verde vista no teste Camera2 seria comportamento de bloqueio/filtro, nao falha visual comum.

Esta hipotese e coerente com as evidencias ja coletadas:

- `sys.avm.preview_status` muda quando a camera e acionada.
- `bean.pui.scene_notify` muda para a cena de camera.
- Logs vendor citam `SCENE_BACKCAMERA` e `backupCamera`.
- A imagem real nao aparece como `Activity`, `Window` ou layer Android clara.
- Camera2 gerou tela verde.
- `qcarcam_edrm_rvc -noDisplay -dumpFrame=1` falhou sem frame.
- `BeanMultiDisplay` mostra rota de UI/display, mas nao video AVM.
- `tsh.reg` mostra comandos AVM/RVC, mas nao API passiva de frame/surface.

## Implicacao

O app Android provavelmente nao consegue pegar a camera e renderizar no cluster de forma direta e segura.

O que parece estar disponivel ao Android:

- estado logico de camera ativa;
- eventos de cena/foco;
- possiveis configuracoes/leituras AVM via servicos vendor;
- UI propria via WebView/Presentation quando permitido.

O que ainda nao apareceu disponivel:

- frame real da camera;
- Surface Android transferivel;
- stream AVM passivo;
- API oficial para espelhar a camera original no cluster.

## Atualizacao: validacao passiva na central

Captura passiva em `tools/headunit-dev/output/hut-architecture-validate-20260425-100025` confirmou pontos importantes da arquitetura:

- `/proc/cmdline` mostra Android em ambiente virtualizado com `gvmip=192.168.1.3`, `virtio`, root via `dm-verity`, `device_state=unlocked`, `verifiedbootstate=orange` e SELinux permissivo.
- `/proc/interrupts` expõe canais HAB `vm2-hab_cam1`, `vm2-hab_disp1`, `vm2-hab_disp2`, `vm2-hab_ogles` e `vm2-hab_vid`, com `cam1`, `disp2` e `ogles` ativos.
- `dumpsys display` confirma 5 displays Android: main `1920x720`, cluster HDMI `1920x720`, passenger HDMI `1280x720`, quarto HDMI `1920x720` e aux `480x240`.
- `dumpsys media.camera` lista 8 devices Android, mas sem `Active Camera Clients` no momento da captura.
- `mount` confirma exports NFS do QNX em `/data/nfs/*`, incluindo log, OTA, share, bvims, dvr0 e dvr1.

Essas evidencias reforcam que a camera original provavelmente e composta fora do SurfaceFlinger Android, por QNX/hypervisor/overlay dedicado. Para o app, o caminho seguro continua sendo usar apenas o estado logico de camera ativa e nao tentar abrir, duplicar ou mover o stream real.

## Decisao de Engenharia

Nao insistir em Camera2, qcarcam direto, `ExecCmd`, `SetConfig` ou tentativa de mover window/surface para o cluster.

O proximo passo seguro e validar a hipotese com captura passiva:

```bash
AVM_STATE_SECONDS=30 ./tools/headunit-dev/headunit.sh avm-state
./tools/headunit-dev/headunit.sh avm-passive
```

Durante a captura, acionar/desligar a camera manualmente. O comando apenas coleta:

- estado logico via properties curtas;
- logs `SCENE_BACKCAMERA`;
- properties;
- logs;
- `dumpsys`;
- SurfaceFlinger;
- media camera;
- lista de processos/servicos;
- arquivos vendor relevantes.

Ele nao altera propriedades, nao abre camera, nao executa `tsh.reg ExecCmd` e nao reinstala APK.

## Criterio de Confirmacao

A hipotese fica mais forte se, durante camera ativa:

- `sys.avm.preview_status` ou `bean.pui.scene_notify` mudam;
- nao aparece client Android real em `dumpsys media.camera`;
- nao aparece layer de camera clara em `SurfaceFlinger`;
- logs mostram cena/foco vendor, mas nao surface/frame acessivel;
- processos host/vendor aparecem, mas sem interface Android consumivel.

## Caminho Pratico Para o App

Enquanto nao houver API passiva de video:

- manter `CAMERA ATIVA` como alerta visual no cluster;
- usar a camera original do carro no display original;
- usar sinais de estado para overlays/alertas simples;
- nao tentar substituir ou duplicar a camera original;
- documentar qualquer variavel nova descoberta.
