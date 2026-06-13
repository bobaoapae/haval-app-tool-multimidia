# Regression Checklist

## Android

- Build debug passa.
- App inicia sem crash.
- `ForegroundService` sobe.
- Shizuku inicializa.
- `ServiceManager` recebe dados.

## Cluster

- Display 3 renderiza WebView.
- Card 3 mostra main menu quando esperado.
- AC aparece e responde a foco.
- Modo mapa aparece quando selecionado.
- Sem overlay de simulação em produção.

## Projeções

- CarPlay display 0 -> cluster 3.
- CarPlay cluster 3 -> display 0.
- Android Auto display 0 -> cluster 3.
- CarPlay e Android Auto não usam recuperação cruzada.
- Antes de alterar CarPlay, capturar baseline e candidato com `headunit.sh carplay-baseline` e
  comparar com `headunit.sh carplay-compare`.
- Para validar uma mudanca CarPlay, preferir `headunit.sh carplay-proof <label>` apos cada etapa,
  porque ele captura evidencia completa com prints de D0 e D3, stack/window/SurfaceFlinger e logs.
- Antes de enviar CarPlay do D0 para o D3, preparar o terreno: abrir CarPlay no D0 pelo icone/fluxo
  nativo, aguardar feed D0 limpo, acionar o envio pelo Impulse/app e so entao capturar D3. Envio
  direto por `am start --display 3` e diagnostico, nao substitui o fluxo preparado.
- Com CarPlay no cluster 3, abrir AC/HVAC no display 0 e tocar na tela do CarPlay: D3 continua
  mostrando CarPlay sem tela preta.
- Com CarPlay no cluster 3, abrir câmera/AVM física no display 0: D3 continua mostrando CarPlay.
- Com CarPlay no cluster 3, abrir app comum no D0: D3 continua mostrando CarPlay sem piscar, sem
  ir para D0 e sem perder `Mapa`.
- Repetir AC, app comum no D0 e câmera/AVM após reboot da central.
- Com CarPlay no cluster 3, navegar pelos cards fisicos do volante:
  - `cardId=1` mostra o main menu sobre o CarPlay sem fundo preto;
  - `cardId=3` mostra o card de AC sobre o CarPlay sem fundo preto;
  - card original/neutro sem tecla fisica recente fica no Mapa/CarPlay limpo;
  - card original/neutro depois de overlay armado nao deve apagar o overlay transparente.
- Camera/AVM deve ser o ultimo teste da matriz e precisa de acionamento fisico manual; nao usar
  comando remoto como substituto da validacao final.
- Antes de deploy/merge que toque CarPlay, rodar:

```bash
python3 scripts/carplay-patches/verify_regression_lock.py
```

## Deploy

- APK enviado completo via curl.
- HTML hot deploy substitui `/data/local/tmp/app.html`.
- Rollback remove `/data/local/tmp/app.html`.

## Evidência

- Comandos executados.
- Logs relevantes.
- Screenshots se houver UI.
- Prints completos do D0 e do D3 quando a mudanca tocar CarPlay, cluster ou projecao.
- Diretorios de baseline/candidato/prova e comparação salvos em `tools/headunit-dev/output/`.
