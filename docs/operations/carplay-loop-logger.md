# CarPlay Loop Logger

Atualizado em: 2026-06-05

## Objetivo

`CarPlayLoopLoggerService` e um servico temporario de diagnostico para capturar o loop em que o
CarPlay aparece no D0, e enviado automaticamente ao D3, volta ao D0 e repete. Ele nao move,
reinicia, redimensiona nem recupera o CarPlay; apenas observa e grava evidencias.

## Segurança

- Desligado por padrao.
- Disponivel apenas em builds internas `debug`/`leanDebug`; nao deve entrar em APK
  `release`/preview publicado no GitHub.
- Nao inicia no boot.
- Nao usa Android Auto.
- Nao chama `am force-stop com.ts.carplay.app`.
- Nao altera `persist.haval.carplay.video.height`.
- A amostragem padrao e de 1,5s e dura ate 7 dias.
- A cada start/dump/stop e periodicamente durante a amostragem, o servico remove sessoes com mais
  de 3 dias para evitar ocupar o armazenamento da central.
- Captura RAW de D0/D4 apenas no inicio, em dump manual e quando detecta loop provavel.

## Iniciar

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=<ip_local_do_mac> \
./tools/headunit-dev/headunit.sh exec \
"am startservice -n br.com.redesurftank.havalshisuku/.services.CarPlayLoopLoggerService -a br.com.redesurftank.havalshisuku.action.CARPLAY_LOOP_LOGGER_START --el durationMs 900000 --el intervalMs 1500"
```

Se a central rejeitar `startservice` por politica de background, usar:

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=<ip_local_do_mac> \
./tools/headunit-dev/headunit.sh exec \
"am start-foreground-service -n br.com.redesurftank.havalshisuku/.services.CarPlayLoopLoggerService -a br.com.redesurftank.havalshisuku.action.CARPLAY_LOOP_LOGGER_START --el durationMs 900000 --el intervalMs 1500"
```

Para deixar gravando por 7 dias com retencao automatica de 3 dias:

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=<ip_local_do_mac> \
./tools/headunit-dev/headunit.sh exec \
"am startservice -n br.com.redesurftank.havalshisuku/.services.CarPlayLoopLoggerService -a br.com.redesurftank.havalshisuku.action.CARPLAY_LOOP_LOGGER_START --el durationMs 604800000 --el intervalMs 1500"
```

## Forçar Dump Manual

Use quando o loop estiver acontecendo fisicamente:

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=<ip_local_do_mac> \
./tools/headunit-dev/headunit.sh exec \
"am startservice -n br.com.redesurftank.havalshisuku/.services.CarPlayLoopLoggerService -a br.com.redesurftank.havalshisuku.action.CARPLAY_LOOP_LOGGER_DUMP"
```

## Parar

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=<ip_local_do_mac> \
./tools/headunit-dev/headunit.sh exec \
"am startservice -n br.com.redesurftank.havalshisuku/.services.CarPlayLoopLoggerService -a br.com.redesurftank.havalshisuku.action.CARPLAY_LOOP_LOGGER_STOP"
```

## Onde Fica

O servico grava em:

```text
/sdcard/Android/data/br.com.redesurftank.havalshisuku/files/carplay-loop/session-<timestamp>/
```

Se o storage externo nao estiver disponivel, cai para `filesDir/carplay-loop` do app.

Arquivos principais:

- `state.log`: amostras a cada intervalo.
- `events.log`: inicio, transicoes e loop detectado.
- `*-stack.txt`: `am stack list`.
- `*-usb.txt`: estado USB.
- `*-window-carplay.txt`: WindowManager do CarPlay.
- `*-window-impulse.txt`: WindowManager do Impulse/Presentation.
- `*-surface-carplay.txt`: SurfaceFlinger da `SurfaceView` do CarPlay.
- `*-logcat.txt`: logcat filtrado.
- `*-d0.raw` e `*-d4.raw`: prints RAW, quando capturados.

## Retencao

O servico apaga automaticamente pastas `session-*` mais antigas que 3 dias dentro de:

```text
/sdcard/Android/data/br.com.redesurftank.havalshisuku/files/carplay-loop/
```

A data principal vem do proprio nome da sessao (`session-yyyyMMdd-HHmmss`). Se o nome nao puder ser
interpretado, o servico usa `lastModified` como fallback. A sessao atual nunca e removida pela
limpeza.

## Interpretacao Rapida

No `state.log`, observe:

```text
visual=D0 desiredPref=3 desiredProp=3
visual=D3 desiredPref=3 desiredProp=3
visual=D0 desiredPref=3 desiredProp=3
```

Se esse padrao alternar em poucos segundos, o problema tende a ser watchdog/restore/handoff. Se
`desiredPref` e `desiredProp` mudarem junto com a alternancia, investigar quem esta regravando o
alvo. Se `visual=D0+D3`, investigar duplicata sustentada.

## Evidencia Complementar Esperada

Quando o D3 estiver sujo mas o `state.log` mostrar `visual=D3`, comparar os dumps capturados pelo
logger:

- `state.log`/`events.log`: comparar a rota de reconexao. Na sessao limpa observada em
  2026-06-03, a rota foi `NONE -> D3`. Na sessao suja seguinte, a rota foi `NONE -> D0 -> D3`.
- `*-logcat.txt`: procurar `cpScreen`, `AMediaCodec_dequeueInputBuffer`, `NdkMediaCodec` e
  `MediaCodec`.
- `*-logcat.txt`: procurar tambem `CARPLAY_CLUSTER_WATCHDOG_DIRECT`,
  `CARPLAY_CLUSTER_WATCHDOG_NO_TASK` e broadcasts `REFRESH_RENDER`/`VIDEO_FOCUS_CHANGE` no mesmo
  intervalo dos erros de codec.
- `*-surface-carplay.txt`: confirmar se a `SurfaceView` real existe e tem buffer valido, mas nao
  usar `activeBuffer=1920x720` como causa isolada.
- `*-window-impulse.txt`: confirmar se a Presentation continua transparente/fullscreen, sem tratar
  sua presenca acima do CarPlay como causa por si so.

Na ocorrencia suja de 2026-06-03 13:43, a pista forte foi a combinacao:

- rota `NONE -> D0 -> D3`;
- restore direto do watchdog `CARPLAY_CLUSTER_WATCHDOG_DIRECT` poucos segundos apos o USB voltar;
- erros `AMediaCodec_dequeueInputBuffer invalid bufidx-1` durante a renegociacao.

Observacao importante: a captura limpa de 2026-06-03 13:42 tambem mostrou
`SurfaceView activeBuffer=1920x720`, entao essa dimensao sozinha nao diferencia limpo vs sujo.

## Converter RAW

O RAW de `screencap` tem 16 bytes de header little-endian seguidos de RGBA.

Para D4/cluster:

```bash
ffmpeg -hide_banner -loglevel error -y \
  -skip_initial_bytes 16 -f rawvideo -pixel_format rgba -video_size 1920x720 \
  -i <arquivo>-d4.raw -frames:v 1 <arquivo>-d4.jpg
```

Para validar header/tamanho:

```bash
python3 - <<'PY'
from pathlib import Path
import struct
for path in Path(".").glob("*.raw"):
    w, h, fmt, _ = struct.unpack("<IIII", path.read_bytes()[:16])
    print(path, w, h, fmt, "expected", w * h * 4 + 16, "actual", path.stat().st_size)
PY
```

## Encerramento

Ao terminar uma sessao fisica:

1. Rodar dump manual se o loop ocorreu.
2. Parar o servico.
3. Puxar a pasta da sessao ou compactar por Telnet.
4. Atualizar `.ai-context/HANDOFF.md` com estado D0/D3, arquivos e classificacao.
