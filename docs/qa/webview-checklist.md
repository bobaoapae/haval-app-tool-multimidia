# WebView Checklist

- HTML correto carregado: `/data/local/tmp/app.html`, tema customizado ou `R.raw.app`.
- `onPageFinished` executou.
- Heartbeat ativo.
- `pendingJsQueues` não acumula indefinidamente.
- `window.control` existe.
- `window.Android` existe apenas em WebView real.
- `onStop` destrói WebView.
- Console/logcat sem erros JS críticos.
- Troca de tema não entra em reload loop.
