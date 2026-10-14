# Starlight

Tema instalável do contrato `v1.0`, bridge mínima `1.0.1`, em canvas `1920×720`.
Derivado do desenho revisão 10 do estudo do PR #161, head
`819d327b0ba311709aa7151832c1f383de9d61d6`. O estudo de desenvolvimento permanece
separado em `cluster-widgets/dev/studies/Starlight/`.

O proprietário autorizou expressamente em 2026-10-11 manter o desenho original
cobrindo o ADAS no estado normal. É uma exceção visual ao perfil de novos temas:
não representa aprovação física da composição OEM. A velocidade, marcha, modos,
escalas, efeitos e menus mantêm as coordenadas do estudo.

O runtime recebe telemetria da bridge. Não inclui controlador de demonstração,
seed de dados ou ponte simulada. Leituras ausentes aparecem como `--`; somente
a geometria satura nos limites das escalas. Potência vem de tensão × corrente,
sem inferência pelo movimento. A velocidade usa a calibração compartilhada do
host/Apex. Combustível em litros é estimado a partir do tanque de referência de 55 L.

Os comandos de ajustes e climatização usam a allowlist existente e aguardam
telemetria de retorno. Reset Trip B requer OK longo com essa viagem em foco.
Somente `onCardChanged` altera o card. Cleanup libera subscriptions, listeners,
timers, frames e buffers; desenho oculto não continua em segundo plano.

Em projeção/preparo ou app nativo no cluster, o canvas cede espaço e leituras
compactas ficam fora do corredor central. `warningActive` do host cede a composição
inteira. Não há máscara nativa definida no manifesto nem alteração de bounds,
serviços, patches ou protocolo do host. Alertas não publicados e prioridade física
de READY/TSR/ESP continuam a confirmar na central.

## Revisão visual 1.0.4 (local)

Em2026-10-12 o usuário pediu remover numerais das escalas, leitura na ponta das barras e
rodapé transparente com preto atrásdas letras. Tipreadouts usam a fronteira geométrica
apresentada pelo últimoACKválido do BEAMS worker; número/sinal/cor/caption acompanham essa
barra, sem bloquear velocímetro central ou outras leituras atuais. null/zero invalida
valor antigo porlado imediatamente, conservando posição pintada atéo próximoACK.
Pipeline fornece getPresentedTipState/onTipChange locais e defensivos; apenas mudança
de conteúdo/posição marca dirty no RAFexistente. Fase igual não repinta UI.

Backgroundbase é transparente de y620 até720; materiais e geometria das barras não mudam.
Texto tem contorno preto2px semblur; caixas inferiores de projeção têm fundo transparente
etext-shadowpreto semblur. Canvas1920×720 econtrato/bridge não mudam.

Build/pacote/catálogo/runtime31/pipeline18 e31renders de material/áreaspreservadas passam;
previewbrowser eprints locais mostram aceleração/regeneração/zero/ausência.1.0.4 incorpora
culling1.0.3 preparado. Não instalada/publicada nesta etapa; performance ecomposição física
A confirmar. A fixture dev/starlight-ui-review.html e dados fictícios não entram emapp.html.

## Ajuste de proximidade 1.0.5 (local)

Após feedback visual, a leitura usa bx/by da ponta luminosa, em vez do vértice alto
pointX/topY do vidro. O bbox do texto fica8px acima da linha ou abaixo para evitar TSR/ESP, com centro horizontal
no endpoint salvo clamp/ajuste lateral mínimo. QA256estados não colidem com os ícones.
A margem OEM é4px; o máximo desvio lateral foi23,04px e fallback inferior não passa de620. Material, ACK, dados, rodapé e bridge preservados.
Build/check/catálogo e browser de aceleração/regen/zero/ausência passam. Prints e rollback
1.0.4 em tools/headunit-dev/output/starlight-ui-20261012/tip-anchor. Sem deploy nesta etapa.

## Ponta superior 1.0.6 (local)

A imagem marcada pelo usuário rejeita a linha inferior da1.0.5. O numeral usa o início
visível superior da lâmina no stop.24 do gradiente intacto: em109km/h,(255,135;342,603).
Texto ao lado com4px, sem afastar da ponta; outro lado/ajuste vertical mínimo libera
READY e legendas. QA608estados sem colisões; exemplo109 tem centro vertical no ponto.
Build/check/catálogo e browser de cinco cenários passam. Fixture externa inclui OEM com
cleanup. Evidências/rollback1.0.5 em output/starlight-ui-20261012/upper-tip. Sem deploy.

## Céu completo 1.0.7 (aplicada na central)

Após a foto da central1.0.6, o usuário pediu céu atéaúltimalinha e remoção dafaixa superior.
BG normal opaco1920×720 cobre OEM sobreadouts; texto conserva contornopreto e posição,
sem placa própria. Campo existente2800stars usa fade/precullaté728 e5guardas dosglifos
inferiores. Seed/travel/barras/tips não mudam. Hostcontinuaocultando todas ascamadas em
warning/projeção. Build/check/catálogo eQAparidade passam; preview em output/starlight-full-sky-20261013.
1.0.7aplicada em13out apósnova confirmação parada;hash/XML/workers/erros0 conferidos.
Evidências output/starlight-central-107-20261013. Aceite físico/custo em movimento a confirmar.

## Menus legíveis 1.0.8 (local)

My Menu e climatização mantêm a área original 880×180 abaixo do velocímetro. Título/opções
28 px, descrições16, rótulos AC18, valores44, estados26–28 e instruções14. Painel azul-preto
alpha .72 apenas com menu aberto; contorno preto sem blur. Em projeção, card1 recebe círculos
locais .36 e AC mantém transparência. Ações, hitboxes e dados confirmados permanecem iguais.

Build/check/catálogo e browser dos dois menus passam; QA37cenários/74composições sem overlap
ou pixels fora da área, fontes reais mínimo14px. Runtime31/pipeline18 e renderer PASS. Preview,
prints e rollback1.0.7 em output/starlight-menu-legibility-20261013.1.0.8 ainda não instalada:
central1.0.7 consultada a37km/h, aguardando confirmação atual de parada. Validação física A confirmar.

## Build e validação

```bash
npm ci
npm run thumbnail
npm run build
npm run check
```

O build transpila a sintaxe para Chrome 60, incorpora JS e fontes locais em
`dist/app.html` e publica `app.html`, `theme.xml`, `thumbnail.png` e licenças em
`cluster-widgets/Themes/v1.0/Starlight/`. O adaptador de simulação pertence somente
ao Theme Lab e não acompanha o pacote. As dependências npm são ferramentas locais
de build/QA, não bibliotecas carregadas pela central.

Para testar no navegador, execute `npm run dev` em `cluster-widgets/` e escolha
Starlight em `http://localhost:1234`. A simulação do Lab não comprova comandos CAN,
visibilidade nativa ou fluidez na central Android 9.

## Camadas e estado da 1.0.2

Background/estrelas e barras/fumaça rodam em dois workersCanvas2D. Velocímetro,
leituras, escalas, lente, menus e bridge permanecem na thread principal. O pacote
embute o worker em Blob offline, sem importações externas. Ambos fazem probe2D real
antes da transferência; as camadas só aparecem após dois primeiros ACKs válidos.
Cada canal limita a fila a um frame em execução e um pendente substituível. Warning,
projeção/ocultação suspendem as camadas, gerações rejeitam ACKs antigos; cleanup
termina workers e revoga o BlobURL. Sem suporte/erro explícito, usa o renderer original.
O canvasUI não é transferido. Não existe ainda timeout de ACK de frame (P2).

A1.0.2 foi instalada na central em2026-10-11 23:02:41, sem novo APK324. Hash/pacote,
workers/bridge/ausência de erros confirmados. Parado: UI4,1ms e barras16,32fps.
Na volta com19→52km/h e aceleração/regeneração, estrelas4,15–4,84fps e barras2,17–2,78fps;
pinturaUI5,3–21ms. A lentidão dos efeitos persiste; fluidez física **não aprovada**.
ProducerFrames mede o scheduler, workers.frames mede ACKs de desenho, uiPaintMs
mede custo de pinturaUI; nenhum equivale à latência CAN→display. Profiling anterior
com profiler ligado não forma A/B equivalente. Próximo: reduzir custo material.

## Candidata 1.0.3 (local)

Culling conservador elimina wisps fora do comprimento visível e cálculos de estrelas
fora do bbox existente, sem diminuir partículas visíveis, fase ou geometria. Staging
limpo antes de clip até comprimento+2px; clipfinal original conservado. Comparação
contra1.0.2 congelada passou31 estados completos pixel0 e12frames contínuos, além de
8400comparações da API sampleStar. Em três casos da volta, brushdraws836→280/329/166.
Benchmark curtoNode:7,5–18,3% menos tempo, sem extrapolar para hardware. Build/pacote/catálogo/browser/checks passaram. Ainda não instalada: central permanece
1.0.2, próxima atualização exige confirmação atual de veículo parado. Ganho físico A confirmar.

## Performance e histórico físico

O runtime agrupa as leituras em um único scheduler com limite de 30 quadros/s.
Estrelas e fases dos materiais avançam a cada quadro; somente fundo, lente, ruído,
brush e máscaras imutáveis são reutilizados. Quando oculto ou sem velocidade/potência,
o desenho pausa. Não há freeze permanente de 4 Hz após um primeiro quadro caro.

Na central Android9/WebView91, a versão1.0.0 apresentou lentidão real em movimento:
thread renderer81,4% de um núcleo,98–99% de quadros atrasados no agregado Android,
mediana200–300ms. Velocímetro e menus aguardam o material no mesmo Canvas. A
performance física ainda não está aprovada; perfil e redução de trabalho por quadro
são pendentes. Esses números são do app com quatro ViewRoots, não FPS isolado do tema.
Build, comparação de pixels e fluidez no Mac não substituem validação no veículo.

A candidata1.0.1 elimina cálculos de partículas fora do alcance da barra antes
de trigonometria e compartilha por quadro o movimento comum das duas asas.
16 estados preservaram os pixels do estudo exatamente, com fases contínuas.
A 8 km/h/0 kW, cálculos desse componente caíram de8200 para2166. Não há redução
de quantidade de partículas visíveis ou frequência dos efeitos. A versão1.0.1
ficou instalada na central em2026-10-11 22:28:59, antes da1.0.2. O ganho físico ainda precisa
de validação em movimento. Gradientes/fumaça e comandos Canvas dos elementos visíveis permanecem.

Após instalar1.0.1, o perfil real confirmou custo do Canvas: estrelas e barras
ocuparam~63% do período amostrado. Mesmo parado, potência pequena mantém RAF;
uma janela com profiler ativo teve33frames em3,67s (~9FPS), speed0 e sem erros
JS. A versão não está aprovada quanto à fluidez; culling foi uma melhoria parcial.
O próximo passo é reduzir chamadas de desenho/usar composição em lote, preservando
fases e geometria. Métricas/profile estão no relatório de handoff2026-10-11.
