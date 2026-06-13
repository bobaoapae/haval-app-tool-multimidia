# Camera Discovery Report (Cluster / AVM)

## Escopo

Este documento consolida as descobertas realizadas para tentar exibir câmera no cluster a partir do app Android (`haval-app-tool-multimidia`), sem alterar funções críticas do veículo.

## Resumo executivo

- Foi possível detectar **estado de câmera** (ex.: AVM ativo/inativo) no Android.
- Não foi possível obter **frame de vídeo real** da câmera no pipeline do app.
- As evidências indicam que o vídeo é renderizado no domínio **QNX/host** (overlay), fora do controle do app Android guest.

## Evidências coletadas

1. Ativação/desativação de câmera correlaciona com sinais de estado:
- `sys.avm.preview_status` alternando `0/1`.
- `bean.pui.scene_notify` alternando para cena de câmera e retorno.

2. Tentativas de roteamento de Activity para display do cluster:
- `am start --display <id>` em activity candidata executa, porém não fixa a activity no display alvo de forma útil para vídeo de câmera.
- `dumpsys activity activities` mostrou atividades predominantemente no display principal.

3. Inspeção de serviços/processos:
- Serviços e processos de câmera/vendor ativos (`qcarcam`, `ais_v4l2_proxy`, `camera provider`) sem exposição direta de stream utilizável no app.
- Nenhuma API Android acessível encontrada que entregue o frame AVM para renderização custom no cluster.

4. Sintoma de tela verde:
- Em cenários de teste, o cluster/multimídia pode mostrar verde quando o conteúdo esperado depende de caminho protegido/overlay não acessível pelo app.

## Hipótese técnica mais forte

- A arquitetura do HUT usa hypervisor (QNX host + Android guest).
- O host controla gating e composição do vídeo de câmera (overlay de alta prioridade).
- Android recebe sinais de estado/eventos, mas não necessariamente recebe o buffer de vídeo final para desenhar no WebView/app.

## O que foi tentado (sem alteração crítica)

- Capturas sincronizadas ON/OFF com logs e dumps (`logcat`, `dumpsys`, services, displays).
- Tentativas de launch em displays secundários.
- Correlação entre propriedades de estado de câmera e comportamento de UI.

## Limites atuais

- Sem integração oficial no host/QNX, não há evidência de caminho confiável para exibir o frame real da câmera no cluster via app Android.
- O que é viável no app Android hoje:
- alerta visual de “câmera ativa”
- lógica de estado baseada em propriedades/sinais
- observabilidade e diagnóstico

## Recomendação prática

1. Manter no app somente funcionalidades de observabilidade/estado para câmera.
2. Preservar fallback seguro no cluster/UI quando frame não estiver disponível.
3. Se houver retomada de vídeo real, priorizar via integração oficial de baixo nível (host/QNX/Tier1), não via WebView.

## Estado do código após limpeza

- Removidas do app as tentativas experimentais de exibição de câmera/TSR no `InstrumentProjector2`.
- Mantido o mecanismo de **hot deploy** do frontend (`/data/local/tmp/app.html` em debug, com fallback para `R.raw.app`).
