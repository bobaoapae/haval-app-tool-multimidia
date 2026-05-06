# Headunit Dev Workflow

## Fluxo 1 - Alteração Kotlin

```bash
./tools/headunit-dev/headunit.sh deploy-apk
./tools/headunit-dev/headunit.sh logcat-app
```

`deploy-apk` faz build debug, envia para a central, instala/reinstala e reinicia o app.

## Fluxo 2 - Alteração air-control

```bash
./tools/headunit-dev/headunit.sh deploy-air-control
```

Esse fluxo compila `cluster-widgets/air-control`, envia `app.html` para `/data/local/tmp/app.html` e aplica `chmod 644`, sem reinstalar APK.

Para recarregar:

```bash
./tools/headunit-dev/headunit.sh exec "am force-stop br.com.redesurftank.havalshisuku && am start -n br.com.redesurftank.havalshisuku/.SplashActivity"
```

Para voltar ao empacotado:

```bash
./tools/headunit-dev/headunit.sh exec "rm -f /data/local/tmp/app.html"
```

## Fluxo 3 - Discovery e diagnóstico

```bash
./tools/headunit-dev/headunit.sh discover
./tools/headunit-dev/headunit.sh dump-info
```

As saídas ficam em `tools/headunit-dev/output/`.

## Comandos úteis

```bash
./tools/headunit-dev/headunit.sh ping
./tools/headunit-dev/headunit.sh shell
./tools/headunit-dev/headunit.sh exec "<cmd>"
./tools/headunit-dev/headunit.sh list-packages
./tools/headunit-dev/headunit.sh list-services
./tools/headunit-dev/headunit.sh list-props
./tools/headunit-dev/headunit.sh logcat
./tools/headunit-dev/headunit.sh logcat-app
```

## Observações

- O caminho mais rápido usa HTTP (quando a central alcança o host local).
- Se HTTP falhar, o toolkit usa fallback por Telnet/Base64 (mais lento).
