# Protocolo Ambient Light BLE

Este arquivo resume o estado implementado no Haval Tool. A engenharia reversa detalhada permanece
em `docs/ambient-light-ledlamp-protocol.md`.

## Hardware validado

- Dispositivo: `LEDCAR-01-00DD`
- Service UUID: `0000ffe0-0000-1000-8000-00805f9b34fb`
- Characteristic UUID: `0000ffe1-0000-1000-8000-00805f9b34fb`
- Saida padrao no Haval Tool: `DMX`
- Ordem de cor padrao DMX: `RBG`
- Ordem de cor padrao BLE: `RGB`

## RGB DMX v2

Formato:

```text
7B 00 07 C1 C2 C3 00 FF BF
```

`C1 C2 C3` recebe a cor logica depois do `ColorOrderMapper`. No controlador testado, a ordem
fisica observada e `RBG`, porque vermelho funciona e verde/azul ficam invertidos em `RGB`.

| Cor logica | Payload v2 | Status |
| --- | --- | --- |
| Vermelho | `7B0007FF000000FFBF` | validado |
| Verde | `7B00070000FF00FFBF` | corrigido por RBG, a validar pos-deploy |
| Azul | `7B000700FF0000FFBF` | corrigido por RBG, a validar pos-deploy |
| Branco | `7B0007FFFFFF00FFBF` | a validar |
| Amarelo | `7B0007FF00FF00FFBF` | corrigido por RBG, a validar pos-deploy |
| Roxo | `7B0007FFFF0000FFBF` | corrigido por RBG, a validar pos-deploy |

## Brilho

Formato DMX LEDCAR-01 derivado do APK LEDLAMP:

```text
7B FF 01 SCALED PERCENT 00 FF FF BF
```

Para 100%:

```text
7BFF01206400FFFFBF
```

O brilho e enviado automaticamente apos conectar e tambem pode ser alterado pelo slider da tela
Ambient Light BLE. Status: a validar fisicamente.

## Saidas

- `DMX`: envia o frame `7B ... BF`, padrao atual.
- `BLE`: envia o frame nao DMX `7E FF 05 03 ... FF EF`.
- `BLE + DMX`: envia ambos; falha em um payload nao bloqueia tentativa do outro.

No controlador atual, DMX e BLE usam ordens diferentes:

| Cor logica | DMX `RBG` | BLE `RGB` |
| --- | --- | --- |
| Verde | `7B00070000FF00FFBF` | `7EFF050300FF00FFEF` |
| Azul | `7B000700FF0000FFBF` | `7EFF05030000FFFFEF` |
| Amarelo | `7B0007FF00FF00FFBF` | `7EFF0503FFFF00FFEF` |
| Roxo | `7B0007FFFF0000FFBF` | `7EFF0503FF00FFFFEF` |

## Expansao futura

Arquitetura passiva criada para eventos e musica:

- `AmbientLightEventRouter`
- `AmbientLightEvent`
- `AmbientLightEventType`
- `AudioBeatDetector`
- `BassAnalyzer`
- `MusicVisualizerController`

Esses pontos ainda nao estao conectados a porta, re, seta, ADAS, velocidade, RPM ou captura real de
audio.
