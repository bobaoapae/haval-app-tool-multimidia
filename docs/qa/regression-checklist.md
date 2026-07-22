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
- Android Auto v250: play/pause pelo Spotify no celular continua sustentando pause.
- Android Auto v250: Media Center nativo da central/player dentro do mapa nativo continua pausando.
- Android Auto v250: card de midia do dashboard Impulse e botao do volante sao superficies
  separadas; nao declarar corrigido por sucesso no player nativo do mapa.
- Android Auto v250: sucesso de pause exige Spotify/telefone pausado de forma sustentada; ACK,
  `sent=true`, resposta Binder ou icone do card nao bastam.
- Android Auto v250: nao reativar `AndroidAutoNowPlayingMonitor` nem `SEND_VEHICLE_INFO`/
  `IfVehicleInfo` como atalho para card/volante sem nova evidencia.
- Android Auto v251 instalada:
  - antes de testar card/volante, confirmar que Spotify/celular ainda sustenta pause;
  - confirmar que Media Center nativo/player dentro do mapa nativo ainda pausa;
  - card do dashboard deve pausar via MediaCenter `402` sem fallback ativo LinkCommand/AAP na mesma
    tentativa;
  - botao do volante deve manter comando imediato app-side bloqueado e usar apenas reconciliacao
    atrasada de play/pause via MediaCenter `402`;
  - next/previous/mute devem manter comportamento anterior.
  - log `transaction=27/28 sent=true` confirma somente rota mecanica; para aprovar regressao,
    Spotify/telefone precisa sair de tocando para pausado e permanecer pausado.
- Android Auto v252:
  - `previous` no card deve trocar a faixa e zerar a timeline;
  - `previous` pelo volante deve trocar a faixa e zerar a timeline no card;
  - `next` tambem deve iniciar progresso visual em `0` na nova faixa;
  - play/pause card/volante nao deve regredir.
- Android Auto v253:
  - confirmar build instalada `versionCode=253`,
    `versionName=1.0.0.253-aa-progress-debug-telemetry`;
  - antes do teste, debug state deve expor `durationMs`, `elapsedMs`, `progressUpdatedAtMs` e
    `canSeek`;
  - apos `previous`/`next` no card, procurar log `Reset Android Auto media progress after dashboard
    Android Auto ...`;
  - apos `previous`/`next` no volante, procurar log `Reset Android Auto media progress after Android
    Auto steering ...` ou fallback equivalente;
  - nao aprovar por ACK, `sent=true` ou troca de faixa isolada: a timeline do card precisa voltar
    para `0`;
  - retestar os quatro fluxos de `play/pause` ja corrigidos: Spotify/celular, Media Center nativo,
    card do dashboard e botao do volante.
  - validacao 2026-06-24 17:59: usuario confirmou bugs sanados; `card_prev` debug resetou
    progresso de `103000ms` para `1000ms`; `card_toggle` debug pausou com
    `USAGE_AAUTO_MEDIA state:stopped` aos 6s; evento manual posterior de `KEYCODE_MEDIA_PLAY_PAUSE`
    nao conta como auto-retomada.
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
