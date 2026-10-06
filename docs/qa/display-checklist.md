# Display Checklist

- `am stack list` mostra display esperado.
- Bounds do cluster 3 são coerentes com 1920x720.
- CarPlay em display 3 fica fullscreen.
- Android Auto em display 3 não fica atrás de overlay indevido.
- WebView transparente não cobre projeção quando não deve.
- Main menu e AC aparecem no cluster 3.
- Modo mapa preserva legibilidade.
- Display 1 continua sem regressão aparente.

Comandos:

```bash
./tools/headunit-dev/headunit.sh exec "am stack list"
./tools/headunit-dev/headunit.sh exec "dumpsys activity activities"
./tools/headunit-dev/headunit.sh exec "dumpsys SurfaceFlinger --list"
```
