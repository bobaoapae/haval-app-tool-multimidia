# Fixed Cluster Icons Diagnostic Session

Atualizado em: 2026-05-24

## Objetivo

Preparar uma sessao de diagnostico para identificar se os icones fixos do cluster 3
em arco ao redor do painel vem de:

1. uma camada Android visivel no `SurfaceFlinger`;
2. uma janela/superficie nativa do cluster/IPK;
3. composicao fora do Android, por exemplo MCU/QNX/display do painel;
4. o proprio WebView/app Haval em modo simulacao.

A saida esperada e uma matriz de evidencias que diga, por icone, se e possivel
mover/esconder diretamente, se so e possivel mascarar, ou se o melhor caminho e
recriar uma copia no tema usando sinais do veiculo.

## Regras de Seguranca

- Sessao read-only: nao enviar comandos de controle veicular.
- Nao forcar warning lamps, falhas, TPMS, airbag, ABS ou qualquer alerta real.
- Mudancas manuais como farol, seta ou pisca-alerta devem ser feitas somente pelo
  usuario, com o carro parado e em condicao segura.
- Nao alterar display bounds, resolucao, tema, CarPlay, Android Auto ou WebView
  durante a coleta.
- Nao usar esta sessao para corrigir. A etapa e apenas identificar origem e
  sinais candidatos.

## Evidencias Ja Identificadas No Codigo

- `CarConstants` expoe sinais basicos para READY e luzes:
  - `car.basic.driving_ready_state`
  - `car.basic.head_light_status`
  - `car.basic.high_beam_light_status`
  - `car.basic.low_beam_light_status`
  - `car.basic.low_light_status`
  - `car.basic.front_fog_light_status`
  - `car.basic.rear_fog_light_status`
  - `car.basic.left_turn_light_status`
  - `car.basic.right_turn_light_status`
- `CarConstants` tambem expoe sinais de warning/IPK em `car.ipk_light.*`, por
  exemplo ABS, airbag, bateria, freio, porta, oleo, TPMS, combustivel baixo,
  VCU ready e limit 120.
- Existem sinais candidatos para leitor de placas/limite:
  - `car.configure.traffic_sign_warning`
  - `car.intelligent_driving_setting.las.tsi_state`
  - `car.intelligent_driving_setting.sras.rsa_state`
  - `car.intelligent_driving_setting.sras.rsa_rsb_state`
  - `car.map.tsr.nav_speed_limit`
  - `car.map.tsr.nav_speed_limit_sign_status`
  - `car.map.tsr.nav_trafic_sign`
- `ServiceManager.DEFAULT_KEYS` ja busca parte dos sinais de warning e IPK,
  incluindo READY, TPMS, BSD/LCA, DOW, FCTA, FCW, door warning, oil warning,
  seat belt e fuel low.
- `InstrumentProjector2.monitoredWarningKeys` ja monitora um subconjunto de
  warnings para a experiencia do projector.
- `TelasScreen` diferencia display 1 e display 3: display 1 fica atras de ADAS
  e outras informacoes; display 3 fica por cima de ADAS e outras informacoes.
  Isso e importante para diagnosticar z-order.

Arquivos relacionados:

- `app/src/main/java/br/com/redesurftank/havalshisuku/models/CarConstants.java`
- `app/src/main/java/br/com/redesurftank/havalshisuku/managers/ServiceManager.java`
- `app/src/main/java/br/com/redesurftank/havalshisuku/projectors/InstrumentProjector2.kt`
- `app/src/main/java/br/com/redesurftank/havalshisuku/ui/screens/TelasScreen.kt`
- `tools/headunit-dev/headunit.sh`

## Hipoteses

### H1: Icones sao camadas Android separadas

Sinais esperados:

- `screencap -d 3` captura os icones.
- `dumpsys SurfaceFlinger --list` lista layers com nomes associados a cluster,
  IPK, ADAS, warning, autolink ou beantechs.
- `dumpsys window` mostra uma janela no display 3 perto do momento em que o
  icone aparece.

Impacto: pode existir algum caminho Android para esconder ou reposicionar, mas
isso ainda depende de permissao/z-order/pacote de origem.

### H2: Icones sao desenhados dentro de uma superficie nativa unica

Sinais esperados:

- `screencap -d 3` captura os icones.
- `SurfaceFlinger` nao separa os icones em layers individuais.
- Os icones aparecem dentro da mesma superficie do cluster/IPK/ADAS.

Impacto: nao ha movimentacao individual por CSS/WebView. O caminho provavel e
evitar a area, mascarar uma regiao, ou recriar os icones usando sinais do carro.

### H3: Icones sao desenhados fora da composicao Android

Sinais esperados:

- Foto do painel mostra os icones.
- `screencap -d 3` nao mostra esses icones.
- `SurfaceFlinger` e `dumpsys window` nao apontam uma layer correspondente.

Impacto: o app nao consegue mover esses icones diretamente. A opcao e ajustar o
layout para nao conflitar, ou criar uma copia propria baseada em sinais logicos.

### H4: Icones sao overlays do app/simulador

Sinais esperados:

- Icones aparecem no simulador ou em DOM/CSS do tema.
- Nao aparecem na central quando o app esta em modo producao.

Impacto: corrigir condicao de exibicao no frontend, sem mexer no cluster nativo.

## Preparacao

Definir o IP atual da central antes da sessao:

```bash
export HEADUNIT_HOST=172.x.x.x
./tools/headunit-dev/headunit.sh ping
```

Criar uma pasta local para saidas:

```bash
mkdir -p "tools/headunit-dev/output/fixed-icons-$(date '+%Y%m%d-%H%M%S')"
```

Registrar manualmente:

- foto do cluster com os icones visiveis;
- tema/modo ativo no app Haval;
- se existe mapa, CarPlay, Android Auto ou projector ativo;
- estado do veiculo: READY, P, farol, seta, pisca-alerta e qualquer warning
  naturalmente presente.

## Coleta 1: Estado De Displays, Janelas E Superficies

Rodar sem alterar estado do carro:

```bash
./tools/headunit-dev/headunit.sh exec "date; wm size; wm density"
./tools/headunit-dev/headunit.sh exec "dumpsys display | grep -Ei 'Display|mDisplayId|real|app|1920|720' -A 6 -B 2"
./tools/headunit-dev/headunit.sh exec "am stack list"
./tools/headunit-dev/headunit.sh exec "dumpsys activity activities | grep -Ei 'displayId=3|Stack|Task|haval|cluster|ipk|adas|carplay|android.auto|projection|autolink|beantech' -A 8 -B 4"
./tools/headunit-dev/headunit.sh exec "dumpsys window windows | grep -Ei 'Display #[0-9]|mDisplayId=3|haval|cluster|ipk|adas|warning|SurfaceView|carplay|android.auto|autolink|beantech' -A 8 -B 4"
./tools/headunit-dev/headunit.sh exec "dumpsys SurfaceFlinger --list | grep -Ei 'haval|cluster|ipk|adas|warning|lamp|light|SurfaceView|carplay|android|auto|autolink|beantech|navigation|map'"
./tools/headunit-dev/headunit.sh exec "service list | grep -Ei 'cluster|ipk|vehicle|beantech|autolink|display|adas|warning'"
./tools/headunit-dev/headunit.sh exec "dumpsys activity services | grep -Ei 'cluster|ipk|vehicle|beantech|autolink|display|adas|warning' -A 4 -B 2"
```

Salvar os outputs localmente no diretorio criado. Se `telnet-exec.sh` truncar
comandos longos, rodar em partes menores.

## Coleta 2: Screencap Do Display 3

Primeiro descobrir a sintaxe suportada:

```bash
./tools/headunit-dev/headunit.sh exec "screencap -h 2>&1 | head -n 40"
```

Depois tentar capturar o display 3:

```bash
./tools/headunit-dev/headunit.sh exec "screencap -d 3 -p /data/local/tmp/cluster3-fixed-icons.png"
./tools/headunit-dev/headunit.sh pull-file /data/local/tmp/cluster3-fixed-icons.png fixed-icons-cluster3.png
```

Se `-d 3` falhar, capturar sem `-d` apenas como controle, mas marcar como
incerto porque pode ser display 0:

```bash
./tools/headunit-dev/headunit.sh exec "screencap -p /data/local/tmp/default-display-fixed-icons.png"
./tools/headunit-dev/headunit.sh pull-file /data/local/tmp/default-display-fixed-icons.png fixed-icons-default-display.png
```

Comparar:

- foto do celular do painel;
- `fixed-icons-cluster3.png`;
- lista de layers do `SurfaceFlinger`.

Essa comparacao e a decisao principal da sessao.

## Coleta 3: Logs Durante Mudancas Manuais Seguras

Abrir uma janela curta de logs e, manualmente, alternar apenas estados seguros
se o carro estiver parado:

- farol ligado/desligado;
- farol baixo/alto, se seguro;
- seta esquerda/direita;
- pisca-alerta;
- READY apenas se a condicao do carro permitir com seguranca.

Comando:

```bash
HEADUNIT_TELNET_WAIT=25 ./tools/headunit-dev/telnet-exec.sh "logcat -v time | grep -Ei 'ServiceManager|InstrumentProjector2|cluster|ipk|warning|lamp|light|head|beam|turn|traffic|tsr|rsa|tsi|autolink|beantech'"
```

Nao usar `logcat -c` se for importante preservar historico.

## Coleta 4: Matriz De Mapeamento

Preencher uma linha por icone observado:

| Icone | Area aproximada | Estado que liga/desliga | Aparece na foto | Aparece no screencap display 3 | Layer/window candidata | Sinal candidato | Conclusao |
| --- | --- | --- | --- | --- | --- | --- | --- |
| READY | esquerda inferior do arco | READY on/off | A confirmar | A confirmar | A confirmar | `car.basic.driving_ready_state`, `car.ipk_light.vcu_ready_state` | A confirmar |
| Leitor de placas/limite | esquerda/centro, conforme foto | placa/limite detectado | A confirmar | A confirmar | A confirmar | `car.map.tsr.*`, `car.intelligent_driving_setting.las.tsi_state`, `sras.rsa*` | A confirmar |
| Farol | arco esquerdo/direito, conforme foto | farol on/off | A confirmar | A confirmar | A confirmar | `car.basic.head_light_status`, `low_beam`, `high_beam`, `low_light` | A confirmar |
| Setas/pisca | arco lateral | seta/pisca | A confirmar | A confirmar | A confirmar | `car.basic.left_turn_light_status`, `right_turn_light_status`, `hazard_light_status` | A confirmar |
| TPMS/pressao | arco lateral | warning natural | A confirmar | A confirmar | A confirmar | `car.basic.tpms_warning`, `car.ipk_light.tpms_warning` | A confirmar |
| Freio/EPB/RBS | arco direito | warning natural | A confirmar | A confirmar | A confirmar | `car.basic.epb_state`, `car.basic.hand_brake_status`, `car.ipk_light.braking_system_indicator`, `car.ipk_light.brake_energe_recycle` | A confirmar |

## Arvore De Decisao

- Foto mostra icone, screencap display 3 mostra icone, e ha layer separada:
  investigar pacote/janela de origem antes de qualquer tentativa de esconder.
- Foto mostra icone, screencap display 3 mostra icone, mas nao ha layer separada:
  icone provavelmente e desenhado dentro de superficie nativa; nao da para mover
  isoladamente pelo app.
- Foto mostra icone, screencap display 3 nao mostra icone:
  icone provavelmente esta fora da composicao Android; nao da para mover via app.
- Sinal candidato muda junto com o icone:
  da para criar copia propria no tema e, se necessario, reposicionar a UI para
  evitar conflito com o icone nativo.
- Nenhum sinal candidato muda:
  proximo passo e criar logger temporario read-only de chaves especificas ou
  procurar AIDL/servico nativo adicional.

## Condicao De Encerramento

Encerrar a sessao quando pelo menos uma destas condicoes for verdadeira:

- origem visual dos icones foi classificada em H1, H2, H3 ou H4;
- existe matriz com evidencia suficiente para READY, leitor de placas e farol;
- ficou claro que faltam logs/sinais e qual instrumentacao read-only deve ser
  adicionada em uma proxima tarefa;
- comandos de captura nao funcionam na central e isso foi documentado.

## Proxima Etapa Depois Do Diagnostico

Somente depois da matriz preenchida:

1. decidir se vale criar um overlay proprio para os icones;
2. decidir se alguma mascara visual e segura no display 3;
3. se necessario, adicionar logger temporario read-only de sinais em
   `ServiceManager`/`InstrumentProjector2`;
4. validar no cluster real antes de qualquer mudanca visual permanente.
