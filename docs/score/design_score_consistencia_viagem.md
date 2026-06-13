# Design — Score de Consistência da Viagem

## 1. Visão geral

O **Score de Consistência da Viagem** é uma experiência do Haval Tools / app Impulse para avaliar a qualidade do trajeto de forma mais inteligente do que um eco score tradicional.

A proposta mede o quanto a condução foi suave, previsível, eficiente e estável. O sistema analisa variação de velocidade, acelerações fortes, frenagens/regeneração, consumo e estabilidade para classificar a viagem como:

- **Viagem suave**;
- **Viagem esportiva**;
- **Trânsito pesado**.

A gestão principal acontece no **app do Impulse**. No cluster, a feature deve aparecer apenas como um ícone discreto de viagem ativa, sem score detalhado.

## 2. Objetivo da experiência

O objetivo é transformar dados técnicos de condução em um feedback simples, premium e útil para o usuário.

A feature deve responder a três perguntas:

1. **Como estou dirigindo agora?**
   O app mostra uma prévia do score em tempo real.

2. **Essa viagem está sendo suave, esportiva ou travada por trânsito?**
   O app classifica o padrão do trajeto.

3. **Como foi minha última viagem?**
   Ao encerrar, o app gera um relatório persistente até a próxima viagem.

## 3. Princípios de design

### 3.1 Cluster limpo

O cluster não deve virar uma tela de relatório. Durante a condução, o cluster exibe apenas um ícone discreto indicando que a análise de viagem está ativa.

Sugestão visual:

- ícone pequeno de trajeto, onda suave ou medidor;
- acento azul/ciano;
- sem números grandes;
- sem distração;
- opcional: tooltip curto na ativação, por poucos segundos: `Análise de viagem ativa`.

### 3.2 App como centro de controle

O app do Impulse é onde o usuário:

- inicia a análise;
- acompanha o score em tempo real;
- responde ao desligamento do veículo;
- encerra a viagem;
- consulta relatório.

### 3.3 Score como prévia durante a viagem

Durante a viagem, o score é uma leitura em tempo real, mas ainda não é o resultado final. Usar linguagem clara:

> Prévia do score — o resultado final será consolidado ao encerrar a viagem.

### 3.4 Contexto importa

Trânsito pesado não deve ser tratado automaticamente como direção ruim. A feature deve diferenciar:

- direção agressiva;
- condução suave;
- contexto de congestionamento.

## 4. Fluxo principal

### 4.1 Sem viagem ativa

Estado inicial da tela.

Elementos:

- título: `Score de Consistência da Viagem`;
- subtítulo: `Avalie suavidade, estabilidade e eficiência da sua condução.`;
- botão primário: `Iniciar análise de viagem`;
- card da última viagem, se existir.

Card da última viagem:

- score final;
- classificação;
- início e fim;
- duração;
- botão `Ver relatório`.

### 4.2 Início da análise

Ao iniciar:

- registrar data/hora de início;
- capturar odômetro inicial, se disponível;
- ativar coleta de telemetria;
- ativar ícone discreto no cluster;
- abrir tela de viagem ativa.

Microcopy sugerida:

> Análise iniciada. O score será atualizado durante o trajeto.

### 4.3 Viagem ativa

Tela principal de acompanhamento em tempo real.

Hierarquia sugerida:

1. **Header/status**
   - `Viagem em análise`;
   - horário de início;
   - tempo decorrido.

2. **Gauge principal**
   - score atual: `82/100`;
   - label: `Viagem suave`;
   - descrição curta: `Condução estável e previsível`.

3. **Cards de métricas**
   - `Variação de velocidade`;
   - `Acelerações fortes`;
   - `Frenagens / regeneração`;
   - `Consumo`;
   - `Estabilidade`.

4. **Classificação atual**
   - chips: `Viagem suave`, `Viagem esportiva`, `Trânsito pesado`;
   - destacar apenas o estado atual.

5. **Ações**
   - botão primário/alerta moderado: `Encerrar viagem`;
   - ação opcional: `Adicionar observação`.

### 4.4 Desligamento do veículo

Quando o veículo é desligado com viagem ativa, a viagem não deve ser encerrada automaticamente.

Exibir modal:

**Título:** `Você ainda está em viagem?`

**Mensagem:**
`O veículo foi desligado. Deseja manter esta análise aberta para continuar quando ligar o carro novamente?`

**Ações:**

- `Continuar viagem`;
- `Finalizar e gerar relatório`;
- `Ver depois`.

Comportamento:

- `Continuar viagem`: mantém sessão aberta e pausada;
- `Finalizar e gerar relatório`: encerra e cria relatório;
- `Ver depois`: mantém sessão pendente e mostra card na home.

Estado visual de sessão pausada:

- label: `Viagem pausada`;
- descrição: `Aguardando religamento do veículo ou decisão do usuário.`;
- botões: `Continuar viagem`, `Finalizar e gerar relatório`.

### 4.5 Religamento do veículo

Se o veículo for ligado novamente e ainda existir uma viagem pendente:

- retomar análise;
- registrar evento de retomada;
- manter score e métricas anteriores;
- atualizar status para `Viagem em análise`.

Microcopy sugerida:

> Viagem retomada. Continuaremos a análise do mesmo trajeto.

### 4.6 Encerramento manual

O usuário pode encerrar pelo app a qualquer momento.

Ao tocar em `Encerrar viagem`, exibir confirmação:

**Título:** `Encerrar análise da viagem?`

**Mensagem:**
`O score final será calculado e o relatório ficará disponível até a próxima viagem.`

**Ações:**

- `Encerrar e gerar relatório`;
- `Cancelar`.

### 4.7 Relatório final

Após encerrar, mostrar relatório da última viagem.

Elementos principais:

- título: `Relatório da última viagem`;
- período: `Iniciada em [data/hora] • Finalizada em [data/hora]`;
- score final;
- classificação final;
- resumo textual;
- duração;
- distância;
- eventos relevantes;
- métricas finais;
- ações: `Iniciar nova viagem`, `Compartilhar relatório`.

Resumo textual por classificação:

- **Viagem suave:** `Condução estável e previsível, com poucas variações bruscas.`
- **Viagem esportiva:** `Trajeto com perfil mais dinâmico, marcado por acelerações e frenagens intensas.`
- **Trânsito pesado:** `Trajeto com muitas paradas e baixa velocidade média, indicando trânsito intenso.`

## 5. Estrutura visual da tela no app

### 5.1 Tela sem viagem ativa

```text
┌────────────────────────────────────┐
│ Score de Consistência da Viagem    │
│ Avalie suavidade, estabilidade...  │
│                                    │
│ [ Iniciar análise de viagem ]      │
│                                    │
│ Última viagem                      │
│ 82/100 • Viagem suave              │
│ 12/03/2026 08:15 → 09:02          │
│ [ Ver relatório ]                  │
└────────────────────────────────────┘
```

### 5.2 Tela ativa

```text
┌────────────────────────────────────┐
│ Viagem em análise                  │
│ Início 08:15 • 24 min              │
│                                    │
│              82/100                │
│           VIAGEM SUAVE             │
│    Condução estável e previsível   │
│                                    │
│ [Velocidade estável] [Acelerações] │
│ [Frenagem/regen]    [Consumo]      │
│ [Estabilidade]                    │
│                                    │
│ Prévia do score...                 │
│                                    │
│ [ Encerrar viagem ]                │
└────────────────────────────────────┘
```

### 5.3 Modal no desligamento

```text
┌────────────────────────────────────┐
│ Você ainda está em viagem?         │
│ O veículo foi desligado. Deseja    │
│ manter esta análise aberta para    │
│ continuar quando ligar novamente?  │
│                                    │
│ [ Continuar viagem ]               │
│ [ Finalizar e gerar relatório ]    │
│ Ver depois                         │
└────────────────────────────────────┘
```

### 5.4 Relatório final

```text
┌────────────────────────────────────┐
│ Relatório da última viagem         │
│ 08:15 → 09:02 • 47 min             │
│                                    │
│              82/100                │
│           VIAGEM SUAVE             │
│                                    │
│ Condução estável e previsível...   │
│                                    │
│ Distância: 32 km                   │
│ Acelerações fortes: 2              │
│ Frenagens fortes: 1                │
│ Regeneração: controlada            │
│ Consumo: eficiente                 │
│                                    │
│ [ Iniciar nova viagem ]            │
│ [ Compartilhar relatório ]         │
└────────────────────────────────────┘
```

## 6. Estados da funcionalidade

| Estado | Descrição | UI principal |
|---|---|---|
| `idle` | Sem viagem ativa | CTA para iniciar e última viagem |
| `active` | Viagem em análise | Gauge em tempo real e cards |
| `paused_after_ignition_off` | Veículo desligado durante viagem | Modal/card de continuidade |
| `waiting_user_confirmation` | Aguardando decisão do usuário | Card persistente de viagem pendente |
| `completed` | Viagem finalizada | Relatório da última viagem |
| `error` | Falha parcial de dados | Aviso discreto e fallback |

## 7. Métricas avaliadas

### 7.1 Variação de velocidade

Mede oscilações bruscas e frequência de mudanças rápidas de velocidade.

Estados de UI:

- `Baixa`;
- `Moderada`;
- `Alta`.

### 7.2 Acelerações fortes

Conta eventos de aceleração acima do limite definido.

Estados de UI:

- `Poucas`;
- `Moderadas`;
- `Frequentes`.

### 7.3 Frenagens / regeneração

Avalia intensidade e frequência de frenagens, incluindo regeneração quando disponível.

Estados de UI:

- `Controlada`;
- `Moderada`;
- `Intensa`.

### 7.4 Consumo

Mede eficiência relativa do trajeto com base nos dados disponíveis.

Estados de UI:

- `Eficiente`;
- `Regular`;
- `Elevado`.

### 7.5 Estabilidade

Resume previsibilidade da condução combinando velocidade, aceleração, frenagem e contexto.

Estados de UI:

- `Alta`;
- `Média`;
- `Baixa`.

## 8. Modelo de score

Score de 0 a 100.

Pesos iniciais sugeridos:

| Dimensão | Peso |
|---|---:|
| Velocidade estável | 25% |
| Aceleração controlada | 20% |
| Frenagem/regeneração | 20% |
| Consumo | 15% |
| Estabilidade | 15% |
| Contexto | 5% |

O algoritmo deve ser modular e ajustável. A UI não deve conter lógica de cálculo.

## 9. Classificações

### Viagem suave

Critérios indicativos:

- score alto;
- pouca variação de velocidade;
- poucas acelerações fortes;
- frenagens controladas;
- estabilidade alta.

Mensagem:

> Condução estável e previsível.

### Viagem esportiva

Critérios indicativos:

- maior frequência de acelerações fortes;
- frenagens mais intensas;
- variação de velocidade elevada;
- score pode ser médio, mas perfil é dinâmico.

Mensagem:

> Condução mais dinâmica, com respostas intensas.

### Trânsito pesado

Critérios indicativos:

- velocidade média baixa;
- muitas paradas;
- acelerações curtas e repetidas;
- baixa distância por tempo;
- padrão stop-and-go.

Mensagem:

> Muitas paradas e baixa velocidade média indicam trânsito intenso.

## 10. Microcopy recomendada

### CTA inicial

`Iniciar análise de viagem`

### Status ativo

`Analisando em tempo real`

### Aviso de score parcial

`Prévia do score — o resultado final será consolidado ao encerrar a viagem.`

### Desligamento

`O veículo foi desligado. Deseja manter esta análise aberta para continuar quando ligar o carro novamente?`

### Relatório salvo

`Relatório salvo até a próxima viagem.`

### Falha parcial de dados

`Alguns dados do veículo não estão disponíveis. O score será calculado com as informações recebidas.`

## 11. Comportamento do cluster

O cluster deve exibir apenas o status mínimo.

Estado ativo:

- mostrar ícone pequeno de análise de viagem;
- opcional: texto curto por alguns segundos ao ativar: `Análise de viagem ativa`.

Estado pausado:

- opcional: ícone com estado discreto de pausa, se suportado.

Estado finalizado:

- remover ícone.

Não exibir no cluster:

- score numérico;
- cards de métrica;
- relatório;
- botões de ativação/encerramento.

## 12. Regras de persistência

- Sessão ativa deve sobreviver a reinício do app.
- Sessão pausada por desligamento deve permanecer recuperável.
- Último relatório deve permanecer visível até nova viagem.
- Ao iniciar nova viagem, manter ou arquivar relatório anterior conforme padrão do produto. No mínimo, substituir o card de última viagem pelo novo relatório ao finalizar.

## 13. Edge cases

1. **Usuário inicia viagem e fecha o app**
   A sessão deve continuar se a coleta de telemetria permitir; ao reabrir, mostrar estado correto.

2. **Veículo desliga rapidamente em uma parada**
   Não finalizar automático. Perguntar ao usuário.

3. **Usuário não responde ao modal**
   Manter card persistente de viagem pendente por período configurável.

4. **Telemetria incompleta**
   Calcular score parcial ou mostrar dados indisponíveis sem quebrar a experiência.

5. **Viagem muito curta**
   Mostrar aviso: `Dados insuficientes para um score completo.`

6. **Nova viagem iniciada com sessão pendente**
   Perguntar se deseja retomar a anterior ou finalizar e começar nova.

## 14. Critérios de aceite de UX

- A ativação ocorre no app em poucos toques.
- O usuário entende que o score em tempo real é uma prévia.
- O cluster não fica poluído.
- O desligamento do veículo não causa perda de dados.
- O usuário pode encerrar quando quiser.
- O relatório final mostra início, fim, duração, score, classificação e principais eventos.
- A última viagem fica disponível até a próxima.
- A classificação `Trânsito pesado` é tratada como contexto, não como falha do motorista.

## 15. Direção visual

Usar a mesma linguagem premium das telas conceituais:

- fundo dark/grafite;
- cards arredondados;
- bordas sutis;
- brilho azul/ciano;
- gauge circular para score;
- chips de classificação;
- ícones minimalistas;
- pouco texto por bloco;
- aparência de cockpit inteligente.

Paleta sugerida:

- fundo: `#07111C`, `#0B1624`, `#101B2A`;
- acento principal: ciano/azul elétrico;
- texto principal: branco ou quase branco;
- texto secundário: cinza azulado;
- sucesso: verde discreto;
- alerta: âmbar discreto;
- erro: vermelho moderado, usar com parcimônia.

## 16. Componentes sugeridos

- `TripConsistencyHomeCard`;
- `TripConsistencyActiveView`;
- `TripConsistencyGauge`;
- `TripMetricCard`;
- `TripClassificationChips`;
- `ContinueTripAfterIgnitionOffModal`;
- `LastTripReportCard`;
- `TripConsistencyReportView`;
- `ClusterTripActiveIndicator`.
