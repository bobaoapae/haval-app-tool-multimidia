# Baseline CarPlay D3

## Objetivo

Criar um baseline reproduzivel do fluxo CarPlay no D3 antes de qualquer nova alteracao funcional.
Esse baseline existe para bloquear regressao cruzada entre AC, camera/AVM, apps do D0, reboot e
ajustes de UI.

## Regras

- Nao misturar Android Auto neste fluxo.
- Nao alterar mais de uma camada por vez:
  - patch nativo CarPlay;
  - watchdog/handoff Kotlin;
  - `InstrumentProjector2`/WebView/UI.
- Nao chamar um estado de "corrigido" sem repetir o roteiro apos reboot.
- Sempre salvar a evidencia do baseline e da tentativa nova em diretorios separados.
- Aplicar as licoes aprendidas antes de cada nova tentativa: separar estado real de alvo desejado,
  tratar `screencap` cinza como evidencia auxiliar, nao aceitar USB `DISCONNECTED` por substring,
  nao usar restore agressivo durante AC/camera/HVAC e manter Android Auto isolado.
- Cada etapa relevante deve gerar evidencia com prints completos do D0 e do D3, alem de
  stack/window/SurfaceFlinger/logs. O comando padrao para isso e `carplay-proof`.
- Camera/AVM e sempre o ultimo teste da sequencia, porque depende de acionamento fisico manual e
  nao deve ser substituido por comando remoto.

## Preflight Obrigatorio Antes do Envio D0 -> D3

O envio para D3 so e uma prova valida quando o terreno foi preparado antes. Um `am start --display 3`
manual direto por Telnet serve para diagnostico pontual, mas nao substitui o fluxo preparado do app,
porque bypassa `CarPlayDisplayOrchestrator`, `projectionPreparingD3`, defocus do D0 e reconciliacao
do alvo real.

Antes de capturar `cp-02-d3-clean`, `cp-field-02-first-d3-dirty` ou qualquer novo D3:

1. Confirmar patch/mount e propriedades antes da troca:

```bash
HEADUNIT_HOST=192.168.15.100 ./tools/headunit-dev/headunit.sh carplay-proof cp-preflight-before-d3
```

2. Abrir CarPlay no D0 primeiro, preferencialmente pelo icone nativo no D0, e esperar alguns
   segundos ate o feed ficar estavel.
3. Confirmar que o D0 esta fisicamente limpo. Screenshot e SurfaceFlinger ajudam, mas nao substituem
   a confirmacao visual quando o buffer de video e protegido.
4. So entao acionar o envio ao D3 pelo fluxo do Impulse/app, para passar pelo orquestrador e pelo
   preparo `PREPARING_D3`.
5. Capturar imediatamente a evidencia D3 com `carplay-proof`.

Se a captura mostrar D3 sujo mas algum passo acima nao foi executado, classificar como
"teste sem preflight completo" e repetir antes de escolher nova camada de correcao.

## Comandos

Captura de evidencia completa por cenario:

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=192.168.15.35 \
  ./tools/headunit-dev/headunit.sh carplay-proof cp-02-d3-clean
```

Com expectativa explicita:

```bash
HEADUNIT_HOST=192.168.15.100 HEADUNIT_LOCAL_HOST=192.168.15.35 \
  BASELINE_EXPECTED="CarPlay permanece visivel no D3 com AC aberto" \
  ./tools/headunit-dev/headunit.sh carplay-proof cp-03-ac-open
```

Comparacao entre duas capturas:

```bash
./tools/headunit-dev/headunit.sh carplay-compare \
  tools/headunit-dev/output/carplay-baseline-<baseline> \
  tools/headunit-dev/output/carplay-baseline-<candidate>
```

Trava estatica antes de qualquer deploy CarPlay:

```bash
python3 scripts/carplay-patches/verify_regression_lock.py
```

## Matriz Obrigatoria

| Cenario | Quando capturar | Esperado |
| --- | --- | --- |
| `cp-01-d0-clean` | CarPlay aberto no D0 | CarPlay limpo no D0 |
| `cp-02-d3-clean` | Apos enviar D0 -> D3 | CarPlay limpo no D3 |
| `cp-03-ac-open` | Com AC/HVAC aberto no D0 | CarPlay permanece visivel no D3 |
| `cp-04-app-d0` | Com app comum aberto no D0 | CarPlay permanece visivel no D3 |
| `cp-05-camera` | Ultimo teste, com camera/AVM fisica ativa manualmente | CarPlay permanece visivel no D3 |
| `cp-06-reboot-d3-clean` | Apos reboot e reconexao | CarPlay volta limpo no D3 |
| `cp-07-reboot-ac-open` | Apos reboot, com AC/HVAC aberto | CarPlay permanece visivel no D3 |
| `cp-08-reboot-app-d0` | Apos reboot, com app comum no D0 | CarPlay permanece visivel no D3 |
| `cp-09-reboot-camera` | Ultimo teste pos-reboot, com camera/AVM fisica ativa manualmente | CarPlay permanece visivel no D3 |

## Matriz de Campo Para Primeiro Handoff Frio

Use esta matriz quando o usuario reportar diferenca entre a primeira conexao USB apos repouso da
central e a segunda conexao USB.

| Cenario | Quando capturar | Esperado/objetivo |
| --- | --- | --- |
| `cp-field-00-arrival-before-touch` | Antes de conectar/reconectar USB ou acionar CarPlay | Estado frio real da central, patch, props e processos |
| `cp-field-01-first-usb-d0-clean` | Primeira conexao USB com CarPlay limpo no D0 | Provar que D0 inicial esta limpo e patch/mount ja estao ativos |
| `cp-field-02-first-d3-dirty` | Imediatamente apos primeiro D0 -> D3 sujo | Capturar o estado ruim sem reiniciar nem reconectar |
| `cp-field-03-second-usb-d0-clean` | Apos reconexao USB manual com D0 limpo | Capturar diferenca de enumeracao/renderer |
| `cp-field-04-second-d3-clean` | Segundo D0 -> D3 limpo | Comparar contra o primeiro D3 sujo |

Comparacao obrigatoria antes de corrigir:

```bash
./tools/headunit-dev/headunit.sh carplay-compare \
  tools/headunit-dev/output/carplay-baseline-<cp-field-02-first-d3-dirty> \
  tools/headunit-dev/output/carplay-baseline-<cp-field-04-second-d3-clean>
```

Nao considerar AC/HVAC ou camera/AVM como causa principal se eles permanecem sem tela preta no
mesmo estado. Nesse caso, tratar AC/camera como regressao obrigatoria depois de mexer no handoff
frio.

## Evidencia Minima

Cada captura deve conter:

- `manifest.txt` com branch, commit, lock, MD5s e hosts.
- `local/verify-regression-lock.txt`.
- `remote/` com props, packages, mounts, processos, USB, `am stack list`, `dumpsys` e `logcat`.
- `filtered/` com foco nos arquivos comparaveis.
- `screenshots/` com prints completos do display 0 e do cluster fisico D3. Nesta central,
  `screencap -d 4` e a captura primaria do D3 fisico; `-d 3` fica como sanidade quando disponivel.

## Critério de Aprovação

Uma mudanca so pode substituir o baseline anterior se:

- passar `verify_regression_lock.py`;
- tiver capturas `carplay-proof` de cada cenario relevante com D0 e D3 completos;
- passar a matriz obrigatoria antes do reboot;
- passar a matriz relevante apos reboot;
- executar Camera/AVM por ultimo e manualmente;
- nao puxar CarPlay do D3 para o D0;
- nao introduzir tela preta, frame sujo sustentado ou loop do Impulse.

## Limites

- `screencap` continua sendo evidencia auxiliar, nao prova visual absoluta.
- A confirmacao final de camera/AVM continua dependendo do teste fisico do usuario.
- Se um cenario falhar, coletar a captura do estado ruim antes de reiniciar, desconectar ou
  implantar novo patch.
