# Prompt para Codex — Implementar Score de Consistência da Viagem no Haval Tools / app Impulse

Você é um agente de desenvolvimento atuando no projeto Haval Tools. Antes de codar, leia a estrutura do repositório, identifique stack, padrões de componentes, serviços, store/state management, tema visual, rotas e integração com telemetria. Depois implemente a melhoria abaixo mantendo compatibilidade com o padrão do projeto.

## Feature

Implementar a funcionalidade **Score de Consistência da Viagem**.

A ideia não é criar apenas um “eco score”. A feature deve avaliar a suavidade e consistência da condução durante uma viagem, considerando:

- variação de velocidade;
- acelerações fortes;
- frenagens e regeneração;
- consumo;
- estabilidade da condução;
- leitura contextual do trajeto.

O sistema deve classificar a viagem em uma destas leituras principais:

- **Viagem suave**;
- **Viagem esportiva**;
- **Trânsito pesado**.

## Diretriz central de UX

A gestão da funcionalidade deve acontecer no **app do Impulse**, não no cluster.

No cluster, onde fica o velocímetro, deve aparecer apenas um ícone discreto indicando que o modo de análise de viagem está ativo. O cluster não deve mostrar o score detalhado, cards, relatórios ou controles de ativação. O objetivo é não poluir a área de condução.

O app do Impulse deve permitir:

1. ativar uma nova viagem;
2. acompanhar o score em tempo real;
3. encerrar a viagem a qualquer momento;
4. lidar com desligamento do veículo sem perder a análise;
5. gerar e exibir relatório da última viagem, com data/hora de início e fim, até que uma nova viagem seja iniciada.

## Fluxos obrigatórios

### 1. Estado sem viagem ativa

Criar uma tela ou seção no app do Impulse para “Score de Consistência da Viagem”.

Quando não houver viagem ativa, exibir:

- título: `Score de Consistência da Viagem`;
- texto curto: `Avalie suavidade, estabilidade e eficiência da sua condução.`;
- botão primário: `Iniciar análise de viagem`;
- se existir relatório anterior, mostrar card `Última viagem` com:
  - data/hora de início;
  - data/hora de fim;
  - score final;
  - classificação final;
  - botão/link `Ver relatório`.

### 2. Ativação da viagem

Ao tocar em `Iniciar análise de viagem`:

- criar uma nova sessão de viagem;
- registrar `startedAt` com data/hora local;
- registrar odômetro inicial, se disponível;
- iniciar coleta de telemetria;
- mostrar status `Viagem em análise`;
- solicitar/emitir ao cluster apenas o ícone discreto de modo ativo, se o projeto tiver integração com o cluster;
- navegar para a tela de gestão da viagem ativa.

### 3. Tela de viagem ativa no app

Na tela ativa, exibir:

- medidor/gauge do score em tempo real, por exemplo `82/100`;
- classificação atual: `Viagem suave`, `Viagem esportiva` ou `Trânsito pesado`;
- status: `Analisando em tempo real`;
- início da viagem: data/hora;
- tempo decorrido;
- distância percorrida, se disponível;
- cards resumidos para:
  - `Velocidade estável` ou `Variação de velocidade`;
  - `Acelerações fortes`;
  - `Frenagens / regeneração`;
  - `Consumo`;
  - `Estabilidade`;
- botão secundário/menos destrutivo: `Encerrar viagem`;
- ação opcional: `Adicionar observação`.

O score em tempo real deve ser tratado como prévia. Usar texto do tipo: `Prévia do score — o resultado final será consolidado ao encerrar a viagem.`

### 4. Desligamento do veículo durante uma viagem ativa

Quando houver viagem ativa e o veículo for desligado/ignição off:

- não finalizar automaticamente a viagem;
- marcar a sessão como `waiting_user_confirmation` ou `paused_after_ignition_off`;
- exibir modal no app e, se possível, push/in-app notification:

Título: `Você ainda está em viagem?`

Texto: `O veículo foi desligado. Deseja manter esta análise aberta para continuar quando ligar o carro novamente?`

Ações:

- botão primário: `Continuar viagem`;
- botão secundário: `Finalizar e gerar relatório`;
- link/ação discreta: `Cancelar` ou `Ver depois`.

Comportamento:

- `Continuar viagem`: mantém a sessão aberta, pausa coleta enquanto o veículo está desligado e retoma quando o veículo ligar novamente;
- `Finalizar e gerar relatório`: consolida o score, salva relatório final e remove o ícone de modo ativo do cluster;
- `Ver depois`: mantém a sessão pausada e mostra um card persistente na home do app avisando que existe uma viagem aguardando confirmação.

Se o usuário não responder, manter a sessão pausada por um período configurável. Sugestão inicial: 12 horas. Após esse período, finalizar automaticamente como `finalizada por inatividade` ou manter pendente se o produto preferir não finalizar sem ação do usuário. Implementar isso como constante/configuração, não hardcoded espalhado pelo código.

### 5. Religamento do veículo

Se a viagem estava pausada por desligamento e o veículo for ligado novamente:

- se a sessão ainda estiver dentro do período permitido, retomar a mesma viagem;
- registrar um evento interno `vehicle_restarted_during_trip`;
- continuar a análise sem apagar os dados anteriores;
- atualizar a UI para `Viagem em análise`.

### 6. Encerramento manual

O usuário pode encerrar a viagem a qualquer momento pelo app.

Ao encerrar:

- registrar `endedAt`;
- registrar odômetro final, se disponível;
- calcular distância, duração, médias e eventos;
- consolidar score final;
- gerar relatório;
- salvar como `lastTripReport`;
- desativar ícone do cluster;
- navegar para relatório final.

### 7. Relatório da última viagem

O relatório deve permanecer disponível até a próxima viagem ser iniciada.

Exibir:

- título: `Relatório da última viagem`;
- data/hora de início;
- data/hora de término;
- duração;
- distância;
- score final;
- classificação final;
- eventos relevantes:
  - acelerações fortes;
  - frenagens fortes;
  - picos de velocidade/variação relevante;
  - regenerações relevantes, se disponível;
- consumo médio ou estimado, se disponível;
- estabilidade;
- resumo textual curto, exemplo:
  - `Condução estável e previsível, com poucas variações bruscas.`;
  - `Trajeto com perfil mais esportivo, marcado por acelerações e frenagens intensas.`;
  - `Trajeto em trânsito pesado, com muitas paradas e baixa velocidade média.`.

Adicionar ações:

- `Iniciar nova viagem`;
- `Exportar/Compartilhar relatório`, se o projeto já tiver padrão para isso;
- `Limpar relatório`, opcional.

## Modelo de dados sugerido

Adapte nomes e formato aos padrões do repositório.

### TripConsistencySession

Campos sugeridos:

- `id: string`;
- `status: 'idle' | 'active' | 'paused_after_ignition_off' | 'waiting_user_confirmation' | 'completed' | 'cancelled'`;
- `startedAt: string`;
- `endedAt?: string`;
- `initialOdometerKm?: number`;
- `finalOdometerKm?: number`;
- `distanceKm?: number`;
- `elapsedSeconds: number`;
- `currentScore: number`;
- `currentClassification: 'smooth' | 'sporty' | 'heavy_traffic'`;
- `metrics: TripConsistencyMetrics`;
- `events: TripConsistencyEvent[]`;
- `notes?: string`;
- `createdAt: string`;
- `updatedAt: string`.

### TripConsistencyMetrics

Campos sugeridos:

- `speedVariationScore: number`;
- `strongAccelerationCount: number`;
- `strongBrakeCount: number`;
- `regenEventsCount: number`;
- `consumptionScore?: number`;
- `stabilityScore: number`;
- `averageSpeedKmh?: number`;
- `maxSpeedKmh?: number`;
- `stopAndGoIndex?: number`;
- `smoothnessScore: number`;
- `energyEfficiencyScore?: number`.

### TripConsistencyEvent

Campos sugeridos:

- `id: string`;
- `type: 'strong_acceleration' | 'strong_brake' | 'regen_event' | 'speed_variation' | 'vehicle_off' | 'vehicle_on' | 'manual_end' | 'auto_end'`;
- `timestamp: string`;
- `value?: number`;
- `label: string`;
- `severity?: 'low' | 'medium' | 'high'`.

### TripConsistencyReport

Campos sugeridos:

- `id: string`;
- `sessionId: string`;
- `startedAt: string`;
- `endedAt: string`;
- `durationSeconds: number`;
- `distanceKm?: number`;
- `score: number`;
- `classification: 'smooth' | 'sporty' | 'heavy_traffic'`;
- `classificationLabel: string`;
- `summaryText: string`;
- `metrics: TripConsistencyMetrics`;
- `events: TripConsistencyEvent[]`;
- `createdAt: string`.

## Algoritmo inicial sugerido

Criar um módulo isolado para cálculo, para facilitar ajustes futuros. Não espalhar regra de score nos componentes visuais.

Sugestão de pesos iniciais:

- velocidade estável / baixa variação: 25%;
- aceleração controlada: 20%;
- frenagem/regeneração controlada: 20%;
- consumo/eficiência: 15%;
- estabilidade geral: 15%;
- leitura contextual: 5%.

O score final deve ser de 0 a 100.

### Classificação

Implementar heurística inicial:

- `Viagem suave`: score >= 75, baixa variação de velocidade, poucas acelerações/frenagens fortes;
- `Viagem esportiva`: acelerações fortes e frenagens intensas acima do limite definido, mesmo que score ainda seja razoável;
- `Trânsito pesado`: velocidade média baixa, muitas paradas, alto índice de stop-and-go e baixa velocidade máxima.

Importante: trânsito pesado não deve ser tratado como “direção ruim”. Ele é um contexto. Evite penalizar demais o motorista quando o padrão indicar congestionamento.

Criar constantes configuráveis para thresholds, por exemplo:

- aceleração forte;
- frenagem forte;
- variação brusca de velocidade;
- velocidade média baixa;
- quantidade de paradas por minuto;
- período máximo de pausa após desligamento.

## Telemetria

Use a fonte de telemetria existente no projeto. Se ainda não houver dados reais para todos os itens, crie uma camada adaptadora com fallback/mock controlado por flag de desenvolvimento.

Dados desejados:

- velocidade atual;
- odômetro;
- aceleração, se disponível;
- eventos de frenagem/regeneração, se disponível;
- consumo instantâneo/médio, se disponível;
- ignição on/off;
- modo de condução, se disponível;
- timestamp de cada amostra.

Nunca trave a feature se algum dado não existir. Quando um dado estiver indisponível, mostrar `Indisponível` ou calcular score parcial com pesos redistribuídos, conforme o padrão mais seguro do projeto.

## Componentes e arquivos esperados

Crie/adapte conforme a stack:

- tela/rota do app: `TripConsistencyScreen` ou equivalente;
- componente de gauge: `TripConsistencyGauge`;
- componente de cards de métricas: `TripMetricCard`;
- modal de confirmação no desligamento: `ContinueTripModal`;
- relatório: `TripConsistencyReportScreen` ou seção `LastTripReport`;
- service/hook/store: `tripConsistencyService`, `useTripConsistency`, `tripConsistencyStore` ou equivalente;
- módulo de cálculo: `tripConsistencyScoring`;
- integração com telemetria: `tripTelemetryAdapter`;
- integração discreta com cluster: função/evento para ligar/desligar apenas o ícone de “viagem ativa”.

## Estados visuais importantes

1. `idle`: sem viagem ativa;
2. `active`: viagem em análise;
3. `paused_after_ignition_off`: veículo desligado, viagem aguardando continuidade;
4. `waiting_user_confirmation`: modal/notificação aguardando decisão;
5. `completed`: relatório gerado;
6. erro/falha de telemetria: mostrar aviso discreto sem quebrar tela.

## Requisitos de UI

Seguir o design premium dark/ciano do Haval Tools:

- fundo escuro;
- cards arredondados;
- acento azul/ciano;
- tipografia limpa;
- hierarquia clara;
- visual de cockpit/app premium;
- boa leitura em telas pequenas;
- botões claros para iniciar, continuar e encerrar.

Textos principais em PT-BR.

## Segurança e privacidade

- Não salvar localização/GPS se o projeto não tiver consentimento explícito.
- Não expor dados sensíveis em logs.
- Persistir apenas dados necessários para o relatório.
- Garantir que a feature não controle ou bloqueie o veículo. Ela apenas monitora, calcula e exibe.

## Critérios de aceite

- O usuário consegue iniciar uma análise de viagem pelo app Impulse.
- Com viagem ativa, o app mostra score em tempo real e classificação atual.
- No cluster, aparece apenas um ícone discreto de modo ativo.
- Ao desligar o veículo durante uma viagem ativa, o app pergunta se o usuário quer continuar a viagem.
- O usuário pode continuar, finalizar e gerar relatório, ou deixar para decidir depois.
- Ao religar o veículo dentro do período permitido, a viagem pode ser retomada sem perder dados anteriores.
- O usuário pode encerrar a viagem manualmente a qualquer momento.
- Ao encerrar, é gerado relatório com início, fim, score, classificação, métricas e eventos.
- O relatório da última viagem fica disponível até a próxima viagem.
- A lógica de score está isolada em módulo testável.
- Adicionar testes unitários para o cálculo de score, classificação e transições de estado principais.
- Adicionar testes de componente/integração para início, desligamento, retomada e finalização, conforme padrão do projeto.

## Entrega esperada

1. Implementação funcional da tela no app Impulse.
2. Serviço/store para sessão da viagem.
3. Cálculo de score em tempo real e final.
4. Relatório da última viagem.
5. Ícone simples no cluster quando ativo, se a integração existir.
6. Testes relevantes.
7. Atualização de documentação, incluindo `design.md` ou seção equivalente para esta feature.
