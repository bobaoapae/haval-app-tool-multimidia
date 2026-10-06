# CarPlay Visual Capture Agent

Atualizado em: 2026-06-03

## Objetivo

O agente `haval-carplay-visual-capture` existe para coletar prints e dumps do CarPlay no D0 e no
D3 sem alterar o funcionamento do veiculo. Ele deve ser usado quando o CarPlay aparece sujo,
cinza, preto, no display errado ou quando for necessario comparar a primeira conexao USB com a
reconexao.

## Regras

- Nao fazer deploy.
- Nao alterar APK, HTML, tema, preferencias ou props persistentes.
- Nao usar Android Auto.
- Nao usar `am force-stop com.ts.carplay.app`.
- Rodar comandos Telnet curtos e em serie; comandos longos podem ser ecoados/quebrados pela shell
  remota e parecer que `curl` ou `wget` caiu.
- Salvar tudo em `tools/headunit-dev/output/carplay-visual-agent-<timestamp>`.

## Ambiente

```bash
export HEADUNIT_HOST=192.168.15.100
export HEADUNIT_LOCAL_HOST=<ip_local_do_mac>
export HEADUNIT_TELNET_WAIT=5
```

Descobrir o IP local do Mac, se necessario:

```bash
ipconfig getifaddr en0
```

## Coleta Minima

1. Confirmar conectividade:

```bash
./tools/headunit-dev/headunit.sh exec "echo ok; date"
```

2. Coletar estado de display e CarPlay:

```bash
./tools/headunit-dev/headunit.sh exec "am stack list"
./tools/headunit-dev/headunit.sh exec "dumpsys window windows | grep -A24 -B4 'com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity'"
./tools/headunit-dev/headunit.sh exec "dumpsys window windows | grep -A18 -B4 'br.com.redesurftank.havalshisuku'"
./tools/headunit-dev/headunit.sh exec "dumpsys SurfaceFlinger | grep -A18 'SurfaceView - com.ts.carplay.app'"
./tools/headunit-dev/headunit.sh exec "dumpsys SurfaceFlinger | grep -A24 -B4 'BufferLayer (#4)'"
```

3. Capturar RAW de D0 e D4. Este e o caminho principal, porque em 2026-06-03 o PNG com `-p`
   saiu truncado, enquanto o RAW veio completo:

```bash
./tools/headunit-dev/headunit.sh exec "screencap -d 0 /data/local/tmp/cap0.raw"
./tools/headunit-dev/headunit.sh exec "screencap -d 4 /data/local/tmp/cap4.raw"
```

O display `-d 4` foi o framebuffer fisico util do cluster em 2026-06-03. O `-d 3` retornou RAW
`1280x720` preto, portanto nao deve ser assumido como print do CarPlay sem validacao visual.

4. Puxar os arquivos com `tools/headunit-dev/pull-remote-file.sh` e validar header/tamanho:

```bash
xxd -g 4 -l 32 tools/headunit-dev/output/carplay-visual-agent-*/cap0.raw
xxd -g 4 -l 32 tools/headunit-dev/output/carplay-visual-agent-*/cap4.raw
python3 - <<'PY'
from pathlib import Path
import struct
for path in Path("tools/headunit-dev/output").glob("carplay-visual-agent-*/cap*.raw"):
    w, h, fmt, _ = struct.unpack("<IIII", path.read_bytes()[:16])
    print(path, w, h, fmt, "expected", w * h * 4 + 16, "actual", path.stat().st_size)
PY
```

5. Converter localmente para JPEG:

```bash
ffmpeg -hide_banner -loglevel error -y \
  -skip_initial_bytes 16 -f rawvideo -pixel_format rgba -video_size 1920x720 \
  -i tools/headunit-dev/output/carplay-visual-agent-*/cap4.raw \
  -frames:v 1 tools/headunit-dev/output/carplay-visual-agent-*/cap4.jpg
```

Se for necessario usar PNG como fallback:

```bash
./tools/headunit-dev/headunit.sh exec "screencap -d 0 -p /data/local/tmp/cp-d0.png"
./tools/headunit-dev/headunit.sh exec "screencap -d 4 -p /data/local/tmp/cp-d4.png"
```

Um PNG sem chunk final `IEND`, com tamanho muito pequeno ou que o visualizador nao abre deve ser
registrado como falha de readback, nao como print valido.

## Quando o CarPlay Nao Aparece

Executar o mesmo caminho usado pelo icone do desktop do CarPlay:

```bash
./tools/headunit-dev/headunit.sh exec "am start -f 0x14000000 -n com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity"
```

Depois repetir `am stack list`, `dumpsys window`, `SurfaceFlinger` e os prints.

## Quando Aparecer Loop D0/D3

Usar `CarPlayLoopLoggerService` em vez de tentar corrigir manualmente durante o loop. O runbook
esta em `docs/operations/carplay-loop-logger.md`.

Resumo:

```bash
./tools/headunit-dev/headunit.sh exec \
"am startservice -n br.com.redesurftank.havalshisuku/.services.CarPlayLoopLoggerService -a br.com.redesurftank.havalshisuku.action.CARPLAY_LOOP_LOGGER_START --el durationMs 900000 --el intervalMs 1500"
```

Quando o loop estiver acontecendo fisicamente:

```bash
./tools/headunit-dev/headunit.sh exec \
"am startservice -n br.com.redesurftank.havalshisuku/.services.CarPlayLoopLoggerService -a br.com.redesurftank.havalshisuku.action.CARPLAY_LOOP_LOGGER_DUMP"
```

## Classificacao

- `handoff D0/D3`: CarPlay esta no display errado, duplicado ou sem stack no D3.
- `Surface/buffer`: Activity esta no D3, mas a SurfaceView esta ausente, stale ou `activeBuffer=1x1`.
- `overlay WebView/Presentation`: CarPlay esta fullscreen e com SurfaceView saudavel, mas a
  `Presentation` do Impulse aparece acima no D3 com buffer RGBA fullscreen.
- `patch nativo CarPlay`: logs de decoder/host mostram renegociacao, `cpScreen`, `NdkMediaCodec` ou
  frame invalido mesmo com overlay transparente.
- `readback falso`: o display fisico esta bom ou a Surface esta saudavel, mas `screencap` do D3
  retorna PNG truncado/cinza/vazio.

## Achado Registrado em 2026-06-03 13:20 - D3 Sujo Real

Na central `192.168.15.100`, o usuario avisou que o D3 estava sujo e a coleta foi feita sem
reconectar USB nem recuperar a tela.

Artefatos:

- `tools/headunit-dev/output/carplay-dirty-live-20260603-1320/d4.jpg`
- `tools/headunit-dev/output/carplay-dirty-live-20260603-1320/d0.jpg`
- `tools/headunit-dev/output/carplay-dirty-live-20260603-1320/ds.txt`
- `tools/headunit-dev/output/carplay-dirty-live-20260603-1320/du.txt`
- `tools/headunit-dev/output/carplay-dirty-live-20260603-1320/dwcp.txt`
- `tools/headunit-dev/output/carplay-dirty-live-20260603-1320/dwimp.txt`
- `tools/headunit-dev/output/carplay-dirty-live-20260603-1320/dsfcp.txt`
- `tools/headunit-dev/output/carplay-dirty-live-20260603-1320/dsf4.txt`
- `tools/headunit-dev/output/carplay-dirty-live-20260603-1320/dlograw.txt`

Leitura:

- D4 fisico mostrou CarPlay/mapa cinza/posterizado/sujo por baixo dos widgets normais.
- D0 mostrou AppList/launcher; CarPlay nao estava focado no D0.
- `am stack list`: CarPlay no D3, stack `41`, task `9070`, bounds `[0,0][1920,720]`,
  `visible=true`.
- `dumpsys usb`: `connected=true`, `configured=true`, `host_connected=true`,
  `kernel_state=CONFIGURED`.
- DOM do Impulse: `theme-mirror-cluster`, `projection-mirror-in-dash`, `carplay-in-dash`,
  menu/main ocultos e `projectionCardOverlayActive=false`.
- SurfaceFlinger do CarPlay no estado sujo:
  `activeBuffer=[1920x 720:1920,Unknown 0x7fa30c06]`.
- Logcat no mesmo intervalo:
  `cpScreen: AMediaCodec_dequeueInputBuffer invalid bufidx-1`.

Classificacao provisoria: `patch nativo CarPlay`/decoder-buffer. Neste caso especifico, a evidencia
nao aponta para Activity fora do display, USB desconectado ou overlay WebView opaco. A proxima
comparacao deve validar se o estado limpo apos reconexao volta para `activeBuffer=1904x704`.

## Achado Registrado em 2026-06-03 13:05 - Captura Limpa

Na central `192.168.15.100`, o D0 estava com navegacao e o CarPlay estava realmente no D3:

- stack D3 `39`, task `9068`, bounds `[0,0][1920,720]`;
- `SurfaceView - com.ts.carplay.app/...CarPlayDisplayActivity` visivel em layerStack `3`, com
  `activeBuffer=[1904x 704:1920,...]`;
- `br.com.redesurftank.havalshisuku` aparecia como `PRESENTATION` no display 3, acima do CarPlay,
  com buffer RGBA fullscreen `1920x720`;
- `screencap -d 0` gerou arquivo plausivel, mas `screencap -d 3` gerou PNG truncado de 49 bytes.
- O caminho corrigido foi RAW:
  - `screencap -d 0 /data/local/tmp/cap0.raw` gerou `1920x720` completo;
  - `screencap -d 3 /data/local/tmp/cap3.raw` gerou `1280x720` preto;
  - `screencap -d 4 /data/local/tmp/cap4.raw` gerou `1920x720` completo do cluster fisico.
- O `cap4.jpg` convertido mostrou CarPlay/mapa limpo no cluster, com overlay esperado do modo Mapa.

Classificacao provisoria: o CarPlay nativo estava geometricamente correto e com Surface saudavel;
se a tela fisica estava suja, a causa mais provavel fica na camada `overlay WebView/Presentation`
ou no timing de aplicacao de `theme-mirror-cluster`. A confirmar com print fisico ou log DOM
`__havalProjectionDebug` no momento exato da sujeira.

## Encerramento

O agente deve terminar sempre com:

- lista dos artefatos gerados;
- estado D0/D3 observado;
- classificacao por camada;
- comandos executados;
- testes que falharam e motivo;
- registro em `.ai-context/HANDOFF.md` e `.ai-context/CHANGELOG-AI.md`.
