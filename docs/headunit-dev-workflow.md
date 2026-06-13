# Headunit Dev Workflow

## Objetivo

Reduzir o ciclo manual de build, deploy, logs e exploração da central multimídia para tarefas de desenvolvimento controlado.

## Estrutura criada

- `tools/headunit-dev/headunit.sh`
- `tools/headunit-dev/telnet-exec.sh`
- `tools/headunit-dev/deploy-apk.sh`
- `tools/headunit-dev/collect-diagnostics.sh`
- `tools/headunit-dev/static-discovery.sh`

## Fluxo 1: Testar alteração Kotlin

1. Editar o código em `app/`.
2. Rodar:

```bash
./tools/headunit-dev/headunit.sh deploy-apk
```

3. O comando acima:
   - roda `./gradlew :app:assembleDebug`
   - sobe um servidor HTTP local temporário
   - pede para a central baixar o APK via `curl`/`wget` em Wi-Fi
   - faz `pm install -r`
   - reinicia o app
   - cai para upload por Telnet + base64 apenas se o HTTP falhar

4. Acompanhar logs:

```bash
./tools/headunit-dev/headunit.sh logcat-app
```

## Fluxo 2: Testar alteração air-control

1. Editar arquivos em `cluster-widgets/air-control/`.
2. Fazer hot deploy do frontend sem reinstalar APK:

```bash
./tools/headunit-dev/headunit.sh deploy-air-control
```

3. O comando acima:
   - entra em `cluster-widgets/air-control`
   - roda `bun run build` (ou `npm run build` se `bun` não existir)
   - localiza `dist/app.html`
   - envia para `/data/local/tmp/app.html`
   - aplica `chmod 644`
   - não reinstala APK

4. Para recarregar o HTML:

```bash
./tools/headunit-dev/headunit.sh exec "am force-stop br.com.redesurftank.havalshisuku && am start -n br.com.redesurftank.havalshisuku/.SplashActivity"
```

5. Fallback e rollback:
   - em `debug`, `InstrumentProjector2` tenta ler `/data/local/tmp/app.html` antes do `R.raw.app`
   - se o arquivo não existir ou for inválido, o app usa o empacotado normalmente
   - para voltar forçado ao empacotado:

```bash
./tools/headunit-dev/headunit.sh exec "rm -f /data/local/tmp/app.html"
```

6. Para teste local de UI (sem central), usar:

```bash
cd cluster-widgets/air-control
npm run dev
```

## Fluxo 3: Explorar informações da central

1. Rodar:

```bash
./tools/headunit-dev/headunit.sh discover
```

2. Abrir o relatório gerado em:

```text
tools/headunit-dev/output/discovery-yyyyMMdd-HHmmss/report.md
```

3. Revisar:
   - comandos executados
   - arquivos gerados
   - fontes prováveis de dados
   - variáveis candidatas
   - próximos testes recomendados

4. Escolher variáveis candidatas e criar um teste controlado, sem alterar funcionalidades críticas do veículo.

## Fluxo 4: Diagnóstico completo

1. Rodar:

```bash
./tools/headunit-dev/headunit.sh dump-info
```

2. Abrir o diretório gerado em:

```text
tools/headunit-dev/output/diagnostics-yyyyMMdd-HHmmss/
```

3. Anexar os arquivos produzidos ao Codex para análise.

## Fluxo 4.1: Preflight CarPlay D0 -> D3

Antes de enviar CarPlay para o D3, preparar o D0. O envio direto por `am start --display 3` via
Telnet e util para diagnostico, mas nao valida o fluxo real do app.

Roteiro minimo:

1. Capturar estado inicial:

```bash
HEADUNIT_HOST=192.168.15.100 ./tools/headunit-dev/headunit.sh carplay-proof cp-preflight-before-d3
```

2. Abrir o CarPlay no D0 pelo icone nativo e aguardar o feed estabilizar.
3. Confirmar visualmente que o CarPlay esta limpo no D0.
4. Enviar para o D3 pelo fluxo do Impulse/app, para usar `CarPlayDisplayOrchestrator` e
   `projectionPreparingD3`.
5. Capturar a prova D0/D3:

```bash
HEADUNIT_HOST=192.168.15.100 ./tools/headunit-dev/headunit.sh carplay-proof cp-02-d3-clean
```

Se o D3 ficar sujo sem esse preflight completo, registrar como evidencia incompleta e repetir antes
de escolher nova correcao.

## Fluxo 5: Análise offline

1. Gerar um pacote local com código, diffs e coletas já existentes:

```bash
./tools/headunit-dev/headunit.sh offline-bundle
```

2. Abrir o índice gerado em:

```text
tools/headunit-dev/output/offline-analysis-yyyyMMdd-HHmmss/index.md
```

3. Usar o `.tar.gz` gerado no mesmo diretório para análise sem acesso à central.

## Comandos principais

```bash
./tools/headunit-dev/headunit.sh ping
./tools/headunit-dev/headunit.sh shell
./tools/headunit-dev/headunit.sh exec "getprop | grep ro."
./tools/headunit-dev/headunit.sh push-apk app/build/outputs/apk/debug/app-debug.apk
./tools/headunit-dev/headunit.sh install-apk /data/local/tmp/haval-tool-dev.apk
./tools/headunit-dev/headunit.sh deploy-apk
./tools/headunit-dev/headunit.sh logcat
./tools/headunit-dev/headunit.sh logcat-app
./tools/headunit-dev/headunit.sh dump-info
./tools/headunit-dev/headunit.sh discover
./tools/headunit-dev/headunit.sh list-packages
./tools/headunit-dev/headunit.sh list-services
./tools/headunit-dev/headunit.sh list-props
./tools/headunit-dev/headunit.sh dumpsys
./tools/headunit-dev/headunit.sh pull-debug
./tools/headunit-dev/headunit.sh offline-bundle
./tools/headunit-dev/restore-oem-voice-navigation.sh
```

## Fluxo 6: Restaurar voz e navegacao OEM

Durante diagnostico de travamento pode ser necessario desativar navegacao/voz OEM. Para desfazer
essa intervencao:

```bash
HEADUNIT_HOST=192.168.15.100 ./tools/headunit-dev/restore-oem-voice-navigation.sh
```

Procedimento completo: `docs/operations/restore-oem-voice-navigation-services.md`.

## Limitações atuais

- O caminho rápido de `deploy-apk` depende de a central conseguir acessar o host local por HTTP.
- Se o Wi-Fi da central não enxergar o host local, o script volta para Telnet + base64, que é mais lento.
- `logcat` e `logcat-app` retornam dumps recentes, não stream contínuo.
- `pull-debug` depende de `tar` e `base64` estarem disponíveis na central.
- Nenhum script novo altera controles críticos do veículo por padrão.
