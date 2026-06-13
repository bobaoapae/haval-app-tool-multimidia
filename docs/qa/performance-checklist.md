# Performance Checklist

- Verificar se houve novo loop ou interval.
- Verificar cleanup de listeners.
- Verificar frequência de `evaluateJavascript`.
- Verificar assets adicionados ao HTML.
- Verificar CSS com blur, filter ou shadow grande.
- Verificar layout shift em 1920x720.
- Observar flickering no cluster.
- Quando possível, coletar `top`, logcat e `dumpsys meminfo`.

Comandos úteis:

```bash
./tools/headunit-dev/headunit.sh logcat-app
./tools/headunit-dev/headunit.sh exec "top -n 1 | head -n 20"
```
