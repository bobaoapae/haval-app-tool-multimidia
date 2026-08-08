# Android Auto Loop Logger

Atualizado em: 2026-06-07

## Objetivo

`AndroidAutoLoopLoggerService` e um servico temporario de diagnostico para capturar evidencias do
Android Auto no D3 quando AC/HVAC, camera/AVM, apps no D0 ou botoes do volante interferem na
projecao. Ele nao move, reinicia, redimensiona nem recupera o Android Auto; apenas observa e grava
evidencias.

## Seguranca

- Desligado por padrao.
- Disponivel apenas em builds internas `debug`/`leanDebug`; nao deve entrar em APK
  `release`/preview publicado no GitHub.
- Nao inicia no boot.
- Nao usa nem altera CarPlay.
- Nao chama `am force-stop com.ts.androidauto.app`.
- Nao envia `requestVideoFocus`, nao faz resize e nao recria Activity.
- A amostragem padrao e de 1,5s e dura ate 7 dias.
- A cada start/dump/stop e periodicamente durante a amostragem, o servico remove sessoes com mais
  de 3 dias para evitar ocupar o armazenamento da central.
- Captura RAW de D0/D4 apenas no inicio, em dump manual e quando detecta loop provavel.

## Iniciar

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=<ip_local_do_mac> \
./tools/headunit-dev/headunit.sh exec \
"am startservice -n br.com.redesurftank.havalshisuku/.services.AndroidAutoLoopLoggerService -a br.com.redesurftank.havalshisuku.action.ANDROID_AUTO_LOOP_LOGGER_START --el durationMs 900000 --el intervalMs 1500"
```

Se a central rejeitar `startservice` por politica de background, usar:

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=<ip_local_do_mac> \
./tools/headunit-dev/headunit.sh exec \
"am start-foreground-service -n br.com.redesurftank.havalshisuku/.services.AndroidAutoLoopLoggerService -a br.com.redesurftank.havalshisuku.action.ANDROID_AUTO_LOOP_LOGGER_START --el durationMs 900000 --el intervalMs 1500"
```

Para deixar gravando por 7 dias com retencao automatica de 3 dias:

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=<ip_local_do_mac> \
./tools/headunit-dev/headunit.sh exec \
"am startservice -n br.com.redesurftank.havalshisuku/.services.AndroidAutoLoopLoggerService -a br.com.redesurftank.havalshisuku.action.ANDROID_AUTO_LOOP_LOGGER_START --el durationMs 604800000 --el intervalMs 1500"
```

## Forcar Dump Manual

Use quando AC fechar rapido, camera piscar preto ou botoes do volante falharem:

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=<ip_local_do_mac> \
./tools/headunit-dev/headunit.sh exec \
"am startservice -n br.com.redesurftank.havalshisuku/.services.AndroidAutoLoopLoggerService -a br.com.redesurftank.havalshisuku.action.ANDROID_AUTO_LOOP_LOGGER_DUMP"
```

## Parar

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=<ip_local_do_mac> \
./tools/headunit-dev/headunit.sh exec \
"am startservice -n br.com.redesurftank.havalshisuku/.services.AndroidAutoLoopLoggerService -a br.com.redesurftank.havalshisuku.action.ANDROID_AUTO_LOOP_LOGGER_STOP"
```

## Onde Fica

O servico grava em:

```text
/sdcard/Android/data/br.com.redesurftank.havalshisuku/files/android-auto-loop/session-<timestamp>/
```

Se o storage externo nao estiver disponivel, cai para `filesDir/android-auto-loop` do app.

Arquivos principais:

- `state.log`: amostras a cada intervalo.
- `events.log`: inicio, transicoes e loop detectado.
- `filtered-logcat.log`: logcat deduplicado coletado periodicamente.
- `*-stack.txt`: `am stack list`.
- `*-usb.txt`: estado USB.
- `*-window-androidauto.txt`: WindowManager do Android Auto.
- `*-window-impulse.txt`: WindowManager do Impulse/Presentation.
- `*-surface-androidauto.txt`: SurfaceFlinger da `SurfaceView` do Android Auto.
- `*-services-androidauto.txt`: services/binder do Android Auto.
- `*-settings-media-keys.txt`: configuracao nativa dos botoes de volante.
- `*-logcat.txt`: logcat filtrado.
- `*-d0.raw` e `*-d4.raw`: prints RAW, quando capturados.

## Retencao

O servico apaga automaticamente pastas `session-*` mais antigas que 3 dias dentro de:

```text
/sdcard/Android/data/br.com.redesurftank.havalshisuku/files/android-auto-loop/
```

A data principal vem do proprio nome da sessao (`session-yyyyMMdd-HHmmss`). Se o nome nao puder ser
interpretado, o servico usa `lastModified` como fallback. A sessao atual nunca e removida pela
limpeza.

## Interpretacao Rapida

No `state.log`, observe:

```text
visual=D3 desiredPref=3 tasks="d=3,s=...,t=...,b=[0,0][1920,720]"
visual=D0 desiredPref=3 ...
visual=NONE desiredPref=3 ...
```

Se `visual` sair de `D3` no momento de AC/camera/app, investigar guard app-side ou transicao nativa.
Se `visual=D3` permanecer estavel mas o D3 piscar preto, comparar `*-surface-androidauto.txt` e
`filtered-logcat.log` para procurar eventos nativos de camera/AVM/HVAC, `requestVideoFocus`,
`view_state` ou recriacao de buffer.

Para botoes do volante, procurar no `filtered-logcat.log`:

- `msgId=135`;
- `Cluster media`;
- `steering media`;
- `media input probe`;
- `KEYCODE_MEDIA`;
- `LinkCommand`.

Se o evento do volante nao aparecer, a falha esta antes do roteamento do Impulse. Se aparecer e o
Android Auto nao responder, investigar o binder nativo `LinkCommand.sendKeyEvent` ou fallback
`input keyevent`.

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

1. Rodar dump manual logo apos o sintoma, se possivel.
2. Parar o servico.
3. Puxar a pasta da sessao ou compactar por Telnet.
4. Atualizar `.ai-context/HANDOFF.md` com AC/camera/volante, arquivos e classificacao.
