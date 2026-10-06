# HUD Native Navigation Reverse Engineering

Atualizado em: 2026-06-01 21:40 -03

## Escopo

Esta nota documenta a engenharia reversa passiva do APK nativo de mapas Haval/GWM para entender como
ele envia informacoes de navegacao ao HUD fisico. Nenhum componente do APK nativo foi iniciado ou
reativado durante a analise.

## Artefatos

- APK remoto: `/system/app/GreatWallNav-0.03.02.18.24.22.2a.28-release/GreatWallNav-0.03.02.18.24.22.2a.28-release.apk`
- Pacote: `com.neusoft.na.navigation`
- Versao: `0.03.02.18.24.22.2a.28_stab-0-gbfbd20d-release`
- SHA-256 local do APK: `e821409eb0ccb4d761981593c531ff7368b8d3fe714d433d520c859f114ca1f9`
- Copia local: `artifacts/reverse/GreatWallNav/GreatWallNav.apk`
- Decompile local: `artifacts/reverse/GreatWallNav/jadx`

O `jadx` terminou com erros parciais, mas gerou fontes e recursos suficientes para mapear o fluxo.

## Estado na Central

O APK esta presente em `/system/app`, assinado como app de sistema:

- `sharedUserId=android.uid.system`
- `userId=1000`
- `android:persistent=true`
- `GreatWallSDKService` declarado como `exported=true`

Na central testada em `192.168.15.100`, o pacote esta desinstalado para o usuario 0:

- `User 0: installed=false`
- `stopped=true`
- `notLaunched=true`

Impacto: o bind direto em `com.neusoft.na.navigation/.ocservice.greatwallsdk.GreatWallSDKService`
provavelmente falha enquanto o pacote estiver nesse estado. Nao foi executado `pm install-existing`
para evitar alterar o estado da central.

## Fluxo Nativo Encontrado

O caminho nativo nao desenha uma Activity Android no display do HUD. Ele usa sinais OneCore/GreatWall
e escreve propriedades veiculares.

1. O app assina `GreatWallNavApi.GuidanceInfoStatus`.
2. O struct `GuidedInfoStatus` carrega:
   - `TurnId`
   - `TurnDistance`
   - `TurnDistanceStr`
   - `NextRoadName`
   - `NextTurnId`
   - `NextTurnDistance`
   - `NextTurnDistanceStr`
   - `RemainingDistance`
   - `RemainingTime`
   - `ArrivalTimeStr`
3. `GreatWallV61System.sendGuidedInfoStatus()` escreve no HUD com `CAR_DATA_PARAM_AREA` inferido
   como `0` no decompile:
   - seta/conversao: `CarInternalControlManager.setIntProperty(557848110, area, turnId)`
   - distancia ate a conversao: `CarInternalControlManager.setLongProperty(558896683, area, turnDistance)`
   - proxima via: `CarARHudManager.setBytesProperty(561004095, area, chunkUtf8)`

Constantes decompiladas:

- `AUTO_LINK_HUT28_HUT_TURNARROW = 557848110`
- `AUTO_LINK_HUT27_HUT_DISTANCETOTURN = 558896683`
- `AUTO_LINK_HUT31_HUT_PHONENEXTROAD = 561004095`

`NextRoadName` e enviado em blocos de 8 bytes. O formato observado:

- byte 0: indice do bloco, iniciando em `0x81`
- byte 1: tamanho total do texto UTF-8
- bytes 2..7: ate 6 bytes do nome da via
- bytes faltantes: `0xFF`
- intervalo entre blocos: `200 ms`

## GreatWall SDK

O APK tambem expoe um SDK AIDL:

- classe cliente: `com.neusoft.greatwallsdk.GreatWallOpenApi`
- servico: `com.neusoft.na.navigation/com.neusoft.na.ocservice.greatwallsdk.GreatWallSDKService`
- interface: `IAidlGreatWallInterface`
- metodos principais:
  - `sync_request(String json)`
  - `async_request(String json, callback)`
  - `setListener(listener)`
  - `setSurface(Surface, callback)`

O TBT normalizado sai em JSON com:

- `moduleType=20000`
- `actionType=2`
- `auth=com.neusoft.navigation`
- `data.firstTBT`
- `data.secondTBT`
- `data.ETA`

Exemplo de campos em `firstTBT`:

- `turnId`
- `distance`
- `distanceString`
- `roadName`
- `exit`

Esse SDK e util para consumir dados nativos quando o pacote estiver ativo, mas nao e necessario para
escrever propriedades HUD se o app conseguir acessar `CarInternalControlManager` e `CarARHudManager`
por outro caminho privilegiado.

## PoC Manual Implementada

Em 2026-06-01 foi criada uma PoC manual no app Impulse para escrever as mesmas propriedades nativas
do HUD, sem iniciar `com.neusoft.na.navigation` e sem abrir Activity no display `4096`.

Arquivos:

- `app/src/main/java/br/com/redesurftank/havalshisuku/hud/HudNativeTbtSender.kt`
- `app/src/main/java/br/com/redesurftank/havalshisuku/broadcastReceivers/HudTbtTestReceiver.kt`

O receiver fica exposto somente para comando manual:

```bash
am broadcast --receiver-foreground \
  -n br.com.redesurftank.havalshisuku/.broadcastReceivers.HudTbtTestReceiver \
  -a br.com.redesurftank.havalshisuku.action.HUD_TBT_TEST \
  --es hudToken haval-hud-poc \
  --ei turnId 0 \
  --ei distance 440 \
  --es road 'Raposo Tavares'
```

Extras:

- `hudToken`: deve ser `haval-hud-poc`.
- `turnId`: codigo numerico da seta/manobra; `-1` ou ausente nao escreve seta.
- `distance`: distancia em metros; `-1` ou ausente nao escreve distancia.
- `road`: nome da proxima via; enviado em chunks UTF-8 de 8 bytes.
- `clear=true`: envia o pacote zero para o nome da via.
- `dumpStatus=true`: registra leitura de propriedades HUD/ARHUD antes/depois.
- `enableHud=true`: tenta habilitar o HUD antes do envio TBT.
- `enableNavDisplay=true`: tenta habilitar a exibicao de navegacao no HUD antes do envio TBT.

Build instalada para teste na central:

- `versionCode=77`
- `versionName=1.0.0.76-hud-native-tbt-poc`
- `lastUpdateTime=2026-06-01 20:50:49`

Comandos enviados para validacao fisica:

- `turnId=0`, `distance=440`, `road=Raposo Tavares`
- `turnId=1`, `distance=180`, `road=Paulista`
- `turnId=2`, `distance=50`, `road=Marginal`
- `turnId=3`, `distance=1200`, `road=Bandeirantes`
- `clear=true`

`dumpsys activity broadcasts` confirmou entrega dos broadcasts. O usuario confirmou fisicamente que
nada apareceu no HUD nessa rodada. Portanto, a falha atual nao e entrega do broadcast; a confirmar
se a escrita direta em `android.car.Car` falhou por permissao, conexao ou manager nulo.

## Diagnostico Persistente

A build diagnostica grava diagnostico persistente em:

```text
/data/data/br.com.redesurftank.havalshisuku/files/hud-tbt-poc.log
```

Extras adicionais do receiver:

- `clearLog=true`: limpa o arquivo de diagnostico e nao escreve HUD se nao houver cue.
- `dumpLog=true`: registra o caminho do log e nao escreve HUD.
- `dumpStatus=true`: le propriedades selecionadas via `getIntProperty`.
- `enableHud=true`: tenta `car.hud_setting.enable_state=1`, `HUT25_HUD_SWTREQ_VR=557848101`
  e `HUT30_HUD_SWTREQ=557858350`.
- `enableNavDisplay=true`: tenta `car.hud_setting.navigation_display_enable=1` e
  `HUT33_NAVIDISPSWT=557858357`.
- `useUserService=true`: executa a escrita pelo `UserService` do Shizuku em processo root, mantendo
  log persistente do resultado.

Achados da v80/v81 na central `192.168.15.100`:

- `CarInternalControlManager` e `CarARHudManager` foram obtidos pelo app.
- Escritas de conteudo TBT (`557848110`, `558896683`, `561004095`) retornam sucesso.
- `HUT30_HUD_SWTREQ=557858350` falha com
  `SecurityException: requires android.car.permission.CAR_VENDOR_EXTENSION`.
- `HUT33_NAVIDISPSWT=557858357` pode ser chamado, mas a leitura posterior permaneceu `0`.
- Respostas `ARHUD_NAVIDISPSWTRESP=557858397`, `ARHUD_ADASDISPSWTRESP=557858396` e
  `ARHUD_BTPHONEDISPSWTRESP=557858398` permaneceram `0`.
- `turnId` OneCore real inclui `102` esquerda, `103` direita e `109` seguir em frente; `turnId`
  `1..6` nao deve ser usado como principal para validar seta.

Achados da v83 na central `192.168.15.100`:

- Build instalada:
  - `versionCode=83`;
  - `versionName=1.0.0.82-hud-user-service-timeout-poc`;
  - `lastUpdateTime=2026-06-01 22:26:34`.
- O `UserService` HUD rodou como `uid=0`, contexto `u:r:busybox:s0`.
- `Shizuku.checkRemotePermission("android.car.permission.CAR_VENDOR_EXTENSION")` retornou
  `granted`.
- O processo root abriu `car_service` e obteve `CarInternalControlManager` e `CarARHudManager`.
- Escritas retornaram sucesso para:
  - `HUT30_HUD_SWTREQ=557858350`;
  - `HUT33_NAVIDISPSWT=557858357`;
  - `HUT28_TURNARROW=557848110`;
  - `HUT27_DISTANCETOTURN=558896683`;
  - `HUT31_PHONENEXTROAD=561004095`.
- Mesmo com sucesso de chamada, leituras posteriores permaneceram `0` para `HUT25_HUD_SWTREQ_VR`,
  `HUT33_NAVIDISPSWT`, `HUT28_TURNARROW` e `ARHUD_NAVIDISPSWTRESP`.
- Usuario reportou que o HUD foi desativado sozinho apos a tentativa que usou `enableHud=true` e
  `enableNavDisplay=true`; ele reativou manualmente. Portanto esses extras nao devem ser repetidos
  ate mapear a semantica correta.

Comando seguro para teste de conteudo apenas, sem tentar ativar HUD/nav display:

```bash
am broadcast --receiver-foreground \
  -n br.com.redesurftank.havalshisuku/.broadcastReceivers.HudTbtTestReceiver \
  -a br.com.redesurftank.havalshisuku.action.HUD_TBT_TEST \
  --es hudToken haval-hud-poc \
  --ez clearLog true \
  --ez dumpStatus true \
  --ez useUserService true \
  --ei turnId 102 \
  --ei distance 180 \
  --es road PAULISTA
```

Depois ler:

```bash
cat /data/data/br.com.redesurftank.havalshisuku/files/hud-tbt-poc.log
```

## Implicacoes Para Evolucao

O caminho mais fiel ao sistema nativo e testar uma PoC pequena que escreva as mesmas propriedades
veiculares, em vez de colocar uma Activity no display logico `4096`.

Proximos pontos tecnicos:

1. Mapear visualmente `turnId` -> icone de seta/manobra.
2. Confirmar se `distance` e interpretado como metros.
3. Confirmar se o nome da via aparece e se o pacote zero limpa o texto.
4. A escrita direta por `android.car.Car`, mesmo via `UserService` root, nao deve ser tratada como
   suficiente para renderizar TBT no HUD.
5. Investigar o evento/fonte nativa que arma o ARHUD, especialmente OneCore,
   `GreatWallNavApi.GuidanceInfoStatus` e `GreatWallSDKService`.
6. So depois da PoC manual validar captura/parse de dados de CarPlay/Android Auto.

## Riscos

- Escrever propriedades do HUD pode alterar diretamente a apresentacao nativa do veiculo.
- O app atual nao roda como `android.uid.system`; pode ser necessario Shizuku, hidden API/reflection
  ou user service privilegiado.
- O pacote nativo de mapas esta `installed=false` para o usuario 0, entao reativar esse app pode
  mudar estado do sistema e nao deve ser feito sem aprovacao explicita.
- O mapeamento de `TurnId` para icone deve ser validado em HUD fisico. O APK contem muitos assets de
  manobra, mas o HUD parece receber codigo numerico, nao bitmap.

## Referencias Locais

- `artifacts/reverse/GreatWallNav/jadx/sources/com/neusoft/p008na/greatwallv61/GreatWallV61System.java`
- `artifacts/reverse/GreatWallNav/jadx/sources/android/car/hardware/bcm/CarInternalControlManager.java`
- `artifacts/reverse/GreatWallNav/jadx/sources/android/car/hardware/hud/CarARHudManager.java`
- `artifacts/reverse/GreatWallNav/jadx/sources/api/structs/com/neusoft/greatwall/GuidedInfoStatus.java`
- `artifacts/reverse/GreatWallNav/jadx/sources/com/neusoft/greatwallsdk/GreatWallOpenApi.java`
