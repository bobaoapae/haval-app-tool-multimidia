# Protocolo Ambient Light LEDLAMP BLE

Data da engenharia reversa: 2026-06-25.

Este documento registra a engenharia reversa inicial do app LEDLAMP para preparar uma integracao opcional com LEDs ambiente instalados pelo usuario. A funcao nao e original do veiculo Haval/GWM e deve permanecer desativada por padrao.

## Escopo e fontes

- APK analisado: `/Users/marcelofp/Downloads/led-lamp-4-3-6.apk`
- Ferramenta usada: `jadx -d /tmp/ledlamp-jadx-20260625 /Users/marcelofp/Downloads/led-lamp-4-3-6.apk`
- Observacao: o JADX terminou com 6 erros de decompilacao, mas gerou os fontes usados abaixo.
- `apktool` nao estava disponivel no ambiente durante esta sessao.
- Dispositivo testado pelo usuario: `LEDCAR-01-00DD`

## BLE

| Item | Valor | Origem no APK | Status |
| --- | --- | --- | --- |
| Service UUID | `0000ffe0-0000-1000-8000-00805f9b34fb` | `sources/com/home/constant/CommonConstant.java:6` | validado pelo usuario |
| Characteristic UUID | `0000ffe1-0000-1000-8000-00805f9b34fb` | `sources/com/home/constant/CommonConstant.java:7` | validado pelo usuario |
| UUID adicional | `0000ffe2-0000-1000-8000-00805f9b34fb` | `sources/com/home/constant/CommonConstant.java:8` | nao validado |
| Escrita GATT | `BluetoothGatt.writeCharacteristic(FFE1)` no servico `FFE0` | `sources/com/home/net/NetConnectBle.java:69-83` | validado pelo fluxo do APK |
| Conversao para bytes | `sendData(int[])` escreve bytes e chama `sendCharacteristic` | `sources/com/home/net/NetConnectBle.java:2768-2777` | validado pelo fluxo do APK |

O catalogo do APK lista as familias `LEDDMX-00-` a `LEDDMX-06-` e `LEDCAR-00-` a `LEDCAR-02-` em `resources/assets/category.json:92-224`. O equipamento real `LEDCAR-01-00DD` corresponde a familia `LEDCAR-01-`; o sufixo final parece ser identificador do dispositivo, a confirmar.

## Frames relevantes

Os frames abaixo usam bytes em hexadecimal. `RR`, `GG`, `BB`, `ZZ`, `CC`, `MM`, `SS`, `BBR`, `DIR` e `IDX` sao campos variaveis. Onde o APK usa `123`, `255` e `191`, isso corresponde a `7B`, `FF` e `BF`.

| Funcao | Payload HEX | Origem no APK | Status |
| --- | --- | --- | --- |
| RGB fixo LEDCAR-01, zona/canal | `7B ZZ 07 RR GG BB CC FF BF` | `NetConnectBle#setCar01Rgb`, `sources/com/home/net/NetConnectBle.java:827-829` | formato validado parcialmente |
| RGB fixo LEDCAR-01 via DMX | `7B 00 07 RR GG BB CC FF BF` | `NetConnectBle#setDmxRgb`, `sources/com/home/net/NetConnectBle.java:742-752` | formato validado parcialmente |
| Vermelho LEDCAR-01 zona 0 canal 0 | `7B 00 07 FF 00 00 00 FF BF` | derivado de `setDmxRgb(255,0,0,0,"LEDCAR-01-")` | validado em hardware real |
| Verde LEDCAR-01 zona 0 canal 0 | `7B 00 07 00 FF 00 00 FF BF` | derivado de `setDmxRgb(0,255,0,0,"LEDCAR-01-")` | nao validado |
| Azul LEDCAR-01 zona 0 canal 0 | `7B 00 07 00 00 FF 00 FF BF` | derivado de `setDmxRgb(0,0,255,0,"LEDCAR-01-")` | nao validado |
| Branco LEDCAR-01 zona 0 canal 0 | `7B 00 07 FF FF FF 00 FF BF` | derivado de `setDmxRgb(255,255,255,0,"LEDCAR-01-")` | nao validado |
| Amarelo LEDCAR-01 zona 0 canal 0 | `7B 00 07 FF FF 00 00 FF BF` | derivado de `setDmxRgb(255,255,0,0,"LEDCAR-01-")` | nao validado |
| Roxo LEDCAR-01 zona 0 canal 0 | `7B 00 07 FF 00 FF 00 FF BF` | derivado de `setDmxRgb(255,0,255,0,"LEDCAR-01-")` | nao validado |
| RGB LEDCAR-01 em caminho nao DMX | `7E FF 05 03 RR GG BB FF EF` | `NetConnectBle#setRgb`, `sources/com/home/net/NetConnectBle.java:522-540` | nao validado |
| RGB LEDCAR-01 em caminho sync/DMX | `7B 00 07 RR GG BB 00 FF BF` ou `7B 01 07 RR GG BB FLAG FF BF` | `NetConnectBle#setRgb`, `sources/com/home/net/NetConnectBle.java:531-538` | nao validado alem do vermelho |
| RGB LEDDMX generico | `7B FF 07 RR GG BB 00 FF BF` | `NetConnectBle#setDmxRgb`, `sources/com/home/net/NetConnectBle.java:749-750` | nao validado |
| RGB LEDDMX-02/04/05/06 | `7B 07 RR GG BB CC FF FF BF` | `NetConnectBle#setDmxRgb`, `sources/com/home/net/NetConnectBle.java:747-748` | nao validado |
| RGB LEDDMX-03 | `7B FF 07 RR GG BB FF FF BF` | `NetConnectBle#setRgb`, `sources/com/home/net/NetConnectBle.java:525-528` | nao validado |
| Liga LEDCAR-01 | `7B FF 04 01 FF FF FF FF BF` | `NetConnectBle#turnOn`, `sources/com/home/net/NetConnectBle.java:202-208` | nao validado |
| Desliga LEDCAR-01 | `7B FF 04 00 FF FF FF FF BF` | `NetConnectBle#turnOff`, `sources/com/home/net/NetConnectBle.java:407-413` | nao validado |
| Liga LEDDMX generico | `7B 04 04 01 FF FF FF FF BF` | `NetConnectBle#turnOn`, `sources/com/home/net/NetConnectBle.java:210-211` | nao validado |
| Desliga LEDDMX generico | `7B 04 04 00 FF FF FF FF BF` | `NetConnectBle#turnOff`, `sources/com/home/net/NetConnectBle.java:413-414` | nao validado |
| Modo RGB LEDCAR-01 DMX | `7B FF 03 MM FF FF FF FF BF` | `NetConnectBle#setRgbMode`, `sources/com/home/net/NetConnectBle.java:838-850` | nao validado |
| Modo RGB LEDCAR-01 nao DMX | `7E FF 03 MM 03 FF FF FF EF` | `NetConnectBle#setRgbMode`, `sources/com/home/net/NetConnectBle.java:841-846` | nao validado |
| Velocidade LEDCAR-01 no caminho DMX | `7B FF 02 SS FF 00 FF FF BF` | `NetConnectBle#setSpeed`, `sources/com/home/net/NetConnectBle.java:976-1009` | nao validado |
| Velocidade LEDDMX-01 | `7B FF 02 SS FF FLAG FF FF BF` | `NetConnectBle#setSpeed`, `sources/com/home/net/NetConnectBle.java:981-983` | nao validado |
| Velocidade LEDDMX-02/04/05/06 | `7B 02 SS 00 FF FF FF FF BF` | `NetConnectBle#setSpeed`, `sources/com/home/net/NetConnectBle.java:1004-1006` | nao validado |
| Brilho LEDCAR-01 | `7B FF 01 SCALED BBR MODE FF FF BF` | `NetConnectBle#setBrightness`, `sources/com/home/net/NetConnectBle.java:1114-1154` | nao validado |
| Brilho musical LEDCAR-01 | `7B FF 01 SCALED BBR 01 FF FF BF` | `NetConnectBle#setMusicBrightness`, `sources/com/home/net/NetConnectBle.java:1066-1087` | nao validado |
| Brilho LEDDMX generico | `7B FF 01 SCALED BBR MODE FF FF BF` | `NetConnectBle#setBrightness`, `sources/com/home/net/NetConnectBle.java:1126-1135` | nao validado |
| Brilho LEDDMX-02/04/05/06 | `7B 01 BBR MODE FF FF FF FF BF` | `NetConnectBle#setBrightness`, `sources/com/home/net/NetConnectBle.java:1131-1133` | nao validado |
| Modo microfone/musica LEDCAR-01/LEDDMX | `7B FF 0B MM 01 FF FF FF BF` | `NetConnectBle#setMusicMicroMode`, `sources/com/home/net/NetConnectBle.java:901-907` | nao validado |
| Modo voz LEDCAR-01/LEDDMX generico | `7B FF 0B MM 00 FF FF FF BF` | `NetConnectBle#setVoiceCtlMode`, `sources/com/home/net/NetConnectBle.java:918-940` | nao validado |
| Modo voz LEDDMX-02/04/05/06 | `7B 0B MM FF FF FF FF FF BF` | `NetConnectBle#setVoiceCtlMode`, `sources/com/home/net/NetConnectBle.java:935-936` | nao validado |
| Direcao LEDCAR-01/LEDDMX generico | `7B FF 0D DIR FF FF FF FF BF` | `NetConnectBle#setDirection`, `sources/com/home/net/NetConnectBle.java:880-892` | nao validado |
| Direcao LEDDMX-02/04/05/06 | `7B 0D DIR FF FF FF FF FF BF` | `NetConnectBle#setDirection`, `sources/com/home/net/NetConnectBle.java:883-887` | nao validado |
| Custom LEDCAR-01 cor/modo/velocidade | `7B 00 07 RR GG BB MM SS BF` | `NetConnectBle#setDmxCustom`, `sources/com/home/net/NetConnectBle.java:791-807` | nao validado |
| Custom LEDDMX generico | `7B FF 07 RR GG BB MM SS BF` | `NetConnectBle#setDmxCustom`, `sources/com/home/net/NetConnectBle.java:802-805` | nao validado |
| Config SPI LEDCAR-01 | `7B FF 05 04 A B VALUE FF BF` | `NetConnectBle#setConfigSPI`, `sources/com/home/net/NetConnectBle.java:1460-1471` | nao validado |
| Config CAR01 | `7B IDX 05 05 A B C FF BF` | `NetConnectBle#setConfigCAR01`, `sources/com/home/net/NetConnectBle.java:1480-1482` | nao validado |
| Smart brightness LEDDMX | `7B FF 08 MODE SCALED BBR FF FF BF` | `NetConnectBle#setSmartBrightness`, `sources/com/home/net/NetConnectBle.java:2034-2039` | nao validado |

`SCALED` no brilho e calculado pelo APK como `(brilho * 32) / 100`. Os significados exatos de `MODE`, `FLAG`, `DIR`, `IDX` e dos canais DMX ainda precisam de validacao no hardware ou por leitura adicional das telas de configuracao do app LEDLAMP.

## Comandos validados ate agora

| Funcao | Payload HEX | Resultado observado | Status |
| --- | --- | --- | --- |
| Vermelho LEDCAR-01/DMX, zona 0 canal 0 | `7B0007FF000000FFBF` | Alterou o LED DMX para vermelho no hardware real | validado |

## Atualizacao v2 - ordem fisica dos canais

Teste real na central mostrou que o canal vermelho esta correto, mas verde e azul ficam invertidos
no controlador `LEDCAR-01-00DD`:

- comando logico verde em ordem `RGB` acendeu azul;
- comando logico azul em ordem `RGB` acendeu verde;
- amarelo e roxo tambem ficaram trocados pelo mesmo motivo;
- vermelho permaneceu correto.

Conclusao: para esse controlador, a ordem logica DMX deve ser mapeada para payload `RBG`. A
implementacao v2 deixa a ordem configuravel (`RGB`, `RBG`, `GRB`, `GBR`, `BRG`, `BGR`) e usa
`RBG` como padrao DMX por ser a ordem fisicamente observada nesse caminho.

| Funcao logica | Payload DMX v2 | Observacao | Status |
| --- | --- | --- | --- |
| Vermelho | `7B0007FF000000FFBF` | canal R nao muda | validado |
| Verde | `7B00070000FF00FFBF` | troca G/B para compensar hardware | corrigido por RBG, a validar pos-deploy |
| Azul | `7B000700FF0000FFBF` | troca G/B para compensar hardware | corrigido por RBG, a validar pos-deploy |
| Branco | `7B0007FFFFFF00FFBF` | todos os canais ligados, ordem indiferente | a validar |
| Amarelo | `7B0007FF00FF00FFBF` | vermelho + verde logico vira R+B no payload | corrigido por RBG, a validar pos-deploy |
| Roxo | `7B0007FFFF0000FFBF` | vermelho + azul logico vira R+G no payload | corrigido por RBG, a validar pos-deploy |

Brilho no caminho LEDCAR-01/DMX segue o APK LEDLAMP:

```text
7B FF 01 SCALED PERCENT MODE FF FF BF
```

Na v2, `setBrightness(100)` gera `7BFF01206400FFFFBF`, com `SCALED=(100*32)/100=0x20` e
`MODE=00`. Esse comando ainda precisa de validacao fisica no controlador instalado.

## Atualizacao v2.1 - ordem separada para BLE

Teste posterior mostrou que o modo `BLE + DMX` deixava a cor final errada quando o payload BLE era
enviado depois do payload DMX. O motivo e que o caminho BLE nao deve reutilizar a ordem fisica DMX
`RBG`: para o controlador atual, o caminho BLE deve permanecer em `RGB`.

Padroes v2.1:

- `DMX`: `RBG`;
- `BLE`: `RGB`;
- `BLE + DMX`: envia primeiro DMX em `RBG` e depois BLE em `RGB`.

| Funcao logica | Payload DMX | Payload BLE | Status |
| --- | --- | --- | --- |
| Vermelho | `7B0007FF000000FFBF` | `7EFF0503FF0000FFEF` | validado/correto |
| Verde | `7B00070000FF00FFBF` | `7EFF050300FF00FFEF` | corrigido por ordem independente |
| Azul | `7B000700FF0000FFBF` | `7EFF05030000FFFFEF` | corrigido por ordem independente |
| Branco | `7B0007FFFFFF00FFBF` | `7EFF0503FFFFFFFFEF` | correto por todos os canais |
| Amarelo | `7B0007FF00FF00FFBF` | `7EFF0503FFFF00FFEF` | corrigido por ordem independente |
| Roxo | `7B0007FFFF0000FFBF` | `7EFF0503FF00FFFFEF` | corrigido por ordem independente |

## Atualizacao v4 - presets Pulse/album inspirados no LED Lamp

Em 2026-06-30 foi feita leitura complementar do APK LED Lamp para a tela `modo` enviada no print do
usuario. O print mostra o seletor `BLE/DMX`, speed `50` e o modo `13.Forward 6 colors BU`
selecionado.

Trechos confirmados no APK:

- `resources/res/values/arrays.xml`, array `ble_mode`:
  - `11:Forward 6 Colors GN,11`;
  - `12:Backward 6 Colors GN,12`;
  - `13:Forward 6 Colors BU,13`;
  - `14:Backward 6 Colors BU,14`;
  - `15:Forward 6 Colors CN,15`;
  - `16:Backward 6 Colors CN,16`.
- `resources/res/values/arrays.xml`, array `Effect_Mode_Array`:
  - `Pulse, 12`.
- `resources/res/values/arrays.xml`, lista DMX:
  - `95:Forward Run 7 Colors,95`;
  - `96:Backward Run 7 Colors,96`;
  - `123:Forward Flow 7 Colors,123`;
  - `124:Backward Flow 7 Colors,124`.

Frames LEDCAR-01 usados como referencia e cobertos por teste:

| Funcao | Payload HEX | Observacao |
| --- | --- | --- |
| Modo BLE, exemplo `MM=13` | `7E FF 03 0D 03 FF FF FF EF` | `NetConnectBle#setRgbMode(..., z=false)` |
| Modo DMX, exemplo `MM=13` | `7B FF 03 0D FF FF FF FF BF` | `NetConnectBle#setRgbMode(..., z=true)` |
| Speed BLE, exemplo `SS=50` | `7E FF 02 32 00 FF FF FF EF` | `NetConnectBle#setSpeed(..., z2=false)` |
| Speed DMX, exemplo `SS=50` | `7B FF 02 32 FF 00 FF FF BF` | `NetConnectBle#setSpeed(..., z2=true)` |
| Custom DMX cor/modo/speed | `7B 00 07 RR GG BB MM SS BF` | `NetConnectBle#setDmxCustom` |

Decisao de implementacao no Haval Tool v277/v284:

- a UI expõe presets inspirados nesses modos dentro de `Onda do album`;
- o loop principal continua app-side e envia RGB com a paleta do album, porque acionar apenas o modo
  nativo do firmware pode trocar para as cores fixas do LED Lamp e perder a cor base da capa;
- o caminho DMX tem frame nativo custom com `RGB + modo + speed`, mas o caminho BLE do `LEDCAR-01`
  nao carrega cor base no mesmo frame;
- a partir da correcao DMX de 2026-07-08 v289, quando `DMX` esta em `Animado` e o preset possui
  `modeId`, o app envia os comandos nativos do LED Lamp para fita DMX: modo
  `7B FF 03 MM FF FF FF FF BF` e speed `7B FF 02 SS FF 00 FF FF BF`, reproduzindo melhor o
  movimento espacial do app LED Car;
- a partir da v290, o DMX animado nao usa mais diretamente a cor fixa do preset escolhido. O app
  classifica a cor base do album na familia DMX mais proxima e troca o `modeId` pelo equivalente
  nativo dessa familia, preservando o movimento: `Run >` usa `97/99/101/103/105/107/109`,
  `Run <` usa `98/100/102/104/106/108/110`, `6 Colors` usa `9..22` e `Flow` usa `125..136`;
- o frame custom DMX `7B 00 07 RR GG BB MM SS BF` continua documentado/testado, mas nao e mais o
  caminho principal da `Onda do album` para presets DMX nativos porque nao reproduz o movimento
  espacial do app LED Car na fita;
- o caminho BLE continua sem alteracao nesta correcao e segue usando simulacao RGB app-side para
  preservar a cor do album;
- `BLE` e `DMX` podem ser configurados separadamente como `Animado` ou `Estatico`. `Estatico` fixa
  a cor base do album; `Animado` segue o preset selecionado quando a saida estiver incluida em
  `Aplicar em`.
- a partir da v286, quando a musica esta pausada mas ainda ha titulo/artista/album/capa ativos, o
  app fixa a cor base do album; a cor do modo de conducao so volta quando nao ha mais
  album/metadata/capa util.

## Atualizacao v5 - identificacao BLE e fallback manual historico

Em 2026-06-30, durante teste na central, o dispositivo conhecido `C0:00:00:00:00:DD` /
`LEDCAR-01-00DD` deixou de aparecer no scan. Outros dispositivos BLE sem nome apareceram com RSSI
forte, mas nao anunciavam `FFE0` e falharam com erros GATT/timeout.

Decisao operacional v280:

- considerar `FFE0` no service UUID ou no advertisement como evidencia forte de LEDLAMP;
- continuar aceitando nomes `LEDCAR`, `LEDDMX` ou `LED` como candidatos;
- marcar dispositivos sem nome/sem `FFE0` como `BLE nao identificado`;
- v280 adicionou temporariamente conexao manual por MAC para tentar o ultimo LED conhecido
  (`C0:00:00:00:00:DD`) quando ele sumiu do scan.

A v281 adicionou `BluetoothGatt.refresh()` antes de fechar o GATT. Na central, o refresh retornou
`true`, mas o MAC `C0:00:00:00:00:DD` continuou caindo imediatamente com GATT `133`. Portanto, nesse
estado a proxima verificacao e fisica: liberar/desligar o controlador LED antes de insistir em novos
payloads/protocolo.

Em seguida, o usuario reportou que o LED voltou a conectar e pediu remover a conexao manual. A v282
removeu da UI os controles `Conexao manual`, `Conectar MAC` e `Ultimo LED`, mantendo o scan/lista e
os logs de identificacao por `FFE0`.

## Atualizacao v3 - sincronizacao com graves no Haval Tool

A primeira implementacao de sincronizacao com graves no Haval Tool nao usa ainda os modos musicais
nativos do LEDLAMP. Ela observa audio local via `android.media.audiofx.Visualizer` e converte batida
grave em pulsos RGB curtos pelo mesmo caminho ja validado de cor.

Motivo:

- os comandos LEDLAMP `setMusicMicroMode`/`setVoiceCtlMode` existem no APK, mas os valores `MM` e o
  comportamento fisico no `LEDCAR-01-00DD` ainda nao foram validados;
- usar `Visualizer(0)` permite testar sem mudar o modo interno do controlador LED;
- se a central bloquear captura de audio, a falha fica restrita ao recurso opt-in.

Comandos LEDLAMP musicais continuam como nao validados:

| Funcao | Payload HEX | Status |
| --- | --- | --- |
| Modo microfone/musica LEDCAR-01/LEDDMX | `7B FF 0B MM 01 FF FF FF BF` | nao validado |
| Modo voz LEDCAR-01/LEDDMX generico | `7B FF 0B MM 00 FF FF FF BF` | nao validado |

## Recomendacao para implementacao inicial

Para reduzir risco no Haval Tool, a implementacao inicial deve usar somente o frame validado/derivado para `LEDCAR-01`:

```text
7B 00 07 RR GG BB 00 FF BF
```

Esse formato cobre `setRgb(r,g,b)` com zona `00` e canal `00`. Os comandos de ligar/desligar, brilho, velocidade, efeitos, musica e configuracao devem ficar documentados e ocultos ate serem testados na central ou no hardware real.

## Pendencias

- Validar a v2.1 com `DMX=RBG` e `BLE=RGB` no LED `LEDCAR-01-00DD`.
- Validar brilho `7BFF01206400FFFFBF` no controlador instalado.
- Confirmar se `FFE1` deve usar Write With Response ou Write Without Response na central Haval; o APK chama `writeCharacteristic` sem setar explicitamente o write type neste caminho.
- Confirmar se `FFE2` e usado para notificacao, timer ou outro fluxo fora dos comandos manuais.
- Confirmar a numeracao real de zonas/canais no LED instalado.
- Confirmar os valores de `MODE`, `DIR` e `FLAG` antes de expor modos/efeitos ao usuario.
