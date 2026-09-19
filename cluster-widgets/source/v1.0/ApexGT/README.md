# Apex GT · Contour e Vector

Tema dinâmico `v1.0`, versão `1.0.13`, com fonte editável, moldura raster, dados vivos e animação
nos instrumentos laterais. Contour mantém o Concept 02 (`reference/concept-02.png`); Vector usa
a referência Concept 03 (`reference/concept-03.png`). A revisão `1.0.4` acrescentou a seleção de
display e as revisões seguintes refinaram legibilidade, escalas, menus e movimento. O pacote
`1.0.13` foi validado localmente no modo web; publicação e validação no veículo mantêm evidências
separadas. Resultados anteriores permanecem históricos.

## Abrir no modo web

A partir da raiz do repositório:

```bash
cd cluster-widgets
npm install
npm run dev
```

Abra **http://localhost:1234/?theme=ApexGT**. O parâmetro seleciona o tema no Theme Lab, que
oferece controles de telemetria e simulação. A fonte isolada em
`http://localhost:1234/source/v1.0/ApexGT/index.html` inicia com `--` sem a bridge; isso é
intencional. O Lab injeta dados de demonstração através de `dev/apex-gt-simulator.js`.

Os valores simulados não entram no bundle. O adaptador do Lab traduz `evPowerKw` em tensão
simulada de `400 V` e corrente correspondente apenas no navegador. Os mocks de READY, TSR e ESP
são a camada de desenvolvimento já existente do Lab, não elementos do Apex GT em produção.

Na versão `1.0.13`, esses mocks continuam visíveis no Lab para representar a composição nativa.
As reservas OEM `(238,315,104,60)`, `(225,475,90,90)` e `(330,445,100,100)` são opacas: o
layout evita colocar conteúdo essencial nelas, enquanto READY, placa de velocidade e assistência
continuam sendo desenhados pelo módulo do veículo em primeiro plano. Somente o envelope central
`(520,120,880,530)` recebe alpha zero.

Na versão `1.0.10`, o Contour usa a faixa principal de potência em `−50..50 kW`, mantendo
`−100/100` apenas como extremos menores. Hora e propulsão ficam ampliadas, respectivamente à
esquerda e à direita da marcha; o odômetro ocupa o canto superior direito. Percentuais e autonomias
de combustível/bateria ganharam prioridade visual. Main Menu e Menu AC usam um painel lateral
temporário maior de `400 × 430 px`; durante sua exibição, a escala direita fica oculta para evitar
ruído e sobreposição.

Na versão `1.0.11`, a escala de velocidade do Contour passa a ser progressiva. O intervalo
`0–80 km/h` ocupa mais de dois terços do deslocamento útil, e cada faixa sucessiva de `20 km/h`
fica menor. As distâncias visuais são aproximadamente `95/80/64/52/42/32/24/18/15 px`; assim,
o movimento urbano fica evidente, enquanto `140–180 km/h` permanece compacto. A escala passa a
mostrar também `20`, `30`, `50` e `60 km/h`, com tipografia secundária e folga validada no browser.
Tração e regeneração seguem o mesmo princípio: `0–±50 kW` recebe mais do dobro do curso visual de
`±50–±100 kW`, com `±20`, `±30` e `±50 kW` visíveis e os extremos `±100` compactos.
A leitura numérica, o tempo da animação e toda a geometria do Vector permanecem inalterados.

Na versão `1.0.12`, horário e propulsão do Contour descem para `y=54`, abaixo do friso luminoso
do cabeçalho. Posição horizontal, tamanhos, marcha e modo de condução permanecem inalterados;
o Vector conserva suas coordenadas próprias.

Na versão `1.0.13`, a escala progressiva do Contour acrescenta `10 km/h` e `±10 kW` como
rótulos secundários. A velocidade continua chegando ao alvo em `180ms`; potência e regeneração
usam um seguidor exponencial com constante de tempo de `100ms`, delta temporal limitado a `80ms`
e encaixe final em `0,05`, evitando saltos em atualizações sucessivas. O raster mantém opacas as
reservas OEM e deixa transparente somente o envelope central.

## Fonte e pacote

| Caminho relativo a este diretório | Responsabilidade |
|---|---|
| `index.html` | Instrumentos, leitura dinâmica e viewport central vazio. |
| `src/apex-gt.css` | Canvas fixo `1920x720`, posições e fontes. |
| `src/apex-gt.js` | Subscriptions unificadas, normalização, instrumentos, relógio, heartbeat e cleanup. |
| `src/apex-menus.js` / `src/apex-menus.css` | Factory dos menus, navegação por cards/teclas, leituras, comandos e painel lateral. |
| `src/apex-display.js` | Preferência de display Contour/Vector, restauração e evento de troca. |
| `src/vector-display.css` | Composição Vector sobre os mesmos elementos de telemetria. |
| `src/vector-geometry.js` / `src/vector-gauges.js` | Geometria e instrumentos da variante Vector incorporados pelo build. |
| `src/gauge-geometry.js` | Coordenadas medidas no raster, compartilhadas por escala e animação. |
| `src/gauge-scales.js` | Marcas e números SVG estáticos calculados pela geometria compartilhada. |
| `src/gauge-motion.js` | Iluminação do material raster, canal direito, hotspot e ponteiro em Canvas 2D. |
| `assets/apex-gt-frame-master.png` | Master da versão `1.0.0`, mantido como histórico. |
| `assets/apex-gt-frame-refined-master.png` | Master atual: edição do Concept 02 pela ferramenta integrada imagegen. |
| `assets/apex-gt-frame.png` | Derivado RGBA `1920x720`, com alpha zero somente no envelope central. |
| `assets/apex-gt-frame.webp` | Derivado WebP lossless com alpha, usado pelo tema. |
| `assets/fonts.css` | Khand 400/500/600 e Eurostile do acervo local, em data URLs. |
| `reference/concept-02.png` | Imagem fornecida como referência de Contour. |
| `reference/concept-03.png` / `reference/vector-asset-prompt.md` | Referência Vector e origem/especificação resumida do asset. |
| `assets/vector-frame-master.png` | Master Vector editado com imagegen. |
| `assets/vector-frame.png` / `assets/vector-frame.webp` | Derivados Vector RGBA/lossless; reservas OEM laterais permanecem opacas. |
| `scripts/prepare-vector-assets.mjs` | Preparação determinística dos assets Vector. |
| `theme.xml` / `thumbnail.png` | Manifesto e prévia de catálogo. |
| `scripts/build.mjs` | Inlining sem Parcel: produz HTML autocontido e copia o pacote. |
| `scripts/prepare-assets.mjs` | Reprocessamento do master/alpha e extração das fontes locais. |
| `scripts/check-*.mjs` | Verificações de runtime, movimento, pacote e navegador. |

O build grava `dist/app.html` e `../../../Themes/v1.0/ApexGT/{app.html,theme.xml,thumbnail.png}`.
Não copia arquivos para `app/src/main/res/raw` nem para assets do APK. O bundle usa imagem e
fontes embutidas, sem CDN, fetch de telemetria ou dependência de rede. Não editar o HTML gerado;
alterar fonte e executar o build.

## Dados do contrato vigente

O manifesto mantém `contractVersion=v1.0` e exige `minBridgeVersion=1.0.1`. A bridge `1.0.1`
é uma extensão aditiva e retrocompatível da `1.0.0`: expõe as leituras de viagem e pneus já
previstas no contrato, entrega snapshots iniciais após a assinatura e oferece a ação
`RESET_DRIVE_INFO`, sem remover métodos ou alterar as chaves consumidas pelos temas existentes.
A referência de autoria permanece em
[`THEME_GUIDE.md`](../../../Themes/THEME_GUIDE.md) e no
[contrato de temas v1](../../../../docs/architecture/themes-contract-v1.md).

| Leitura | Chave/fonte real | Tratamento |
|---|---|---|
| Velocidade | `car.basic.vehicle_speed` | Valor em km/h; arco progressivo 0–180 em Contour prioriza 0–80 e o Vector mantém 0–200, sem truncar a leitura numérica. |
| Potência / regeneração | `car.ev_info.power_battery_voltage` e `car.ev_info.cur_charge_current` | `kW = V × A / 1000`; tração positiva, regeneração negativa. Sem os dois operandos, ambas ficam `--`. |
| RPM | `car.basic.engine_speed` | Visível somente acima de zero; ausente/zero oculta a leitura. |
| Marcha | `car.basic.gear_status` | `0/1=N`, `2=D`, `3=P`, `4=R`. |
| Modo de condução | `car.drive_setting.drive_mode` | NORMAL, SPORT, ECO, NEVE, AREIA, LAMA ou AWD conforme enum existente. |
| Propulsão | `car.ev_setting.power_model_config` | `0=HEV`, `1=EVP`, `3=EV`; distinto do modo de condução. |
| Temperaturas | `car.basic.inside_temp`, `car.basic.outside_temp`, `car.configure.default_temp_unit` | Rótulos interna/externa distintos; unidade `0=°C`, `1=°F`. |
| Odômetro | `car.basic.total_odometer` | Total em km, separado do Trip A. |
| Combustível | `car.basic.remain_fuel_percentage` | Percentual 0–100; litros estimados como `% × 55 / 100`, identificados por `EST.`. |
| Bateria | `car.ev_info.cur_battery_power_percentage` | Percentual 0–100. |
| Autonomias | `car.ev_info.fuel_mode_remain_odometer`, `car.ev_info.electric_mode_remain_odometer` | Leituras em km; total estimado soma ambas somente quando as duas existem. |
| Trip A | `car.basic.cur_journey_odometer` | Uma casa decimal; não usa odômetro total como substituto. |
| Consumo médio | `car.basic.cur_journey_avg_fuel_consume` | Fonte em L/100 km; positivo convertido por `100 / valor` para km/L; zero real aparece como `0.0 L/100 km`. |
| Hora | Relógio local da WebView | HH:mm com atualização na virada do minuto. |
| Projeção | `carPlayInDash`, `projectionPreparingD3` | Classes de estado; não desenham mapa nem alteram bounds nativos. |

Ausente, inválido ou ainda não recebido é `--`; zero real permanece zero. O arco limita apenas
sua representação a `−100..100 kW` no Contour; a leitura numérica conserva a potência calculada.
O Vector preserva sua calibração própria de `−100..200 kW`. O tema
consulta `getAvailableKeys()`, assina somente chaves veiculares disponíveis, recebe `onDataChanged()` e
consulta o snapshot inicial depois de assinar, preservando um push inicial síncrono. Leituras
canônicas têm prioridade sobre aliases legados posteriores; submodos HEV legados são usados
somente como fallback. Sentinelas de temperatura `-1` e `255` são tratadas como indisponíveis.

`control`, `focus`, `showScreen`, `onCardChanged`, `onKeyEvent` e `cleanup` permanecem disponíveis.
Até `1.0.2`, os handlers de cards/teclas apenas exibiam foco/contexto. Em `1.0.3`, o runtime
delega cards/teclas à factory `ApexMenus` e incorpora suas chaves à subscription única do tema.
`focus` e `showScreen` continuam no-ops de compatibilidade.

## Composição e transparência

O envelope conservador do contrato é `x=520..1399`, `y=120..649` (`880 × 530`). O processamento
do raster zera RGBA desse retângulo; HTML, CSS e Canvas também o deixam livre. O teste de
navegador captura o bundle com fundo omitido e verifica **466.400 pixels com alpha zero por
estado**, cobrindo normal, preparação, projeção ativa e saída. A região não contém simulação de
ADAS. Fundo preto mostrado por um visualizador sem checkerboard não significa pixel opaco.

Superfícies escuras laterais (`x<520` e `x>=1400`) e no rodapé (`y>=650`) mantêm contraste
sobre conteúdo nativo sem entrar no centro protegido. As zonas OEM fixas são preservadas em `(238,315,104,60)`, `(225,475,90,90)` e
`(330,445,100,100)`: o raster é limpo de ornamentos e dados essenciais ficam fora delas.
A superfície lateral escura continua sob essas zonas; o alpha zero da composição completa é
verificado para o envelope central, não para os três retângulos OEM. Por isso a leitura de velocidade fica ligeiramente mais à direita no próprio
instrumento (`left:348px`) e o rodapé central começa em `y=653`. A revisão `1.0.1` preserva as
coordenadas da moldura dos instrumentos e ajusta somente as bordas internas do vidro e o acabamento
inferior, conforme o processamento documentado abaixo. RPM, temperatura interna e litros estimados complementam o perfil mínimo.
Essas são diferenças explícitas frente ao Concept 02: não há promessa de equivalência pixel a
pixel nem reprodução dos ícones nativos na posição sugerida pela imagem.

## Movimento e lifecycle

No Contour, o Canvas 2D interpola velocidade em `180ms`. Potência e regeneração usam um seguidor
exponencial com constante de tempo de `100ms`, delta temporal limitado a `80ms` e epsilon de
`0,05`; isso preserva continuidade quando chega um novo alvo antes de o anterior estabilizar.
A pintura permanece limitada a `30 fps`, e os números atualizam pelo próximo frame de renderização.
O material luminoso esquerdo é preparado
a partir do próprio raster uma vez, preservando seus sulcos e relevos. `gauge-geometry.js` fornece
as mesmas coordenadas para marcas SVG e posição de luz/ponteiro, evitando duas calibrações divergentes. O canvas repinta apenas os dois instrumentos laterais, repousa ao estabilizar e ignora
telemetria repetida. `prefers-reduced-motion` e documento oculto levam diretamente ao destino.
As barras de combustível/bateria usam transição CSS curta de transform, também desabilitada na
preferência de movimento reduzido.

`cleanup()` desinscreve telemetria e cancela frame, relógio, heartbeat de `2s` e listeners do
movimento. O heartbeat existe apenas com o método correspondente na bridge; não é loop gráfico.

## Ampliação inferior do Contour na versão 1.0.8

A parte superior até y=300 permanece; o trecho 300..570 dos instrumentos é ampliado até 624
(+54px, fator 1,2), dentro dos mesmos 1920x720. `gauge-geometry.js` compartilha mapY/inverseY entre
preparo do asset, escalas e movimento. O perímetro inferior fica perto de 718, com transição de
90px junto ao centro; máscaras OEM/ADAS são reaplicadas após remapeamento. Assets normalizados
deterministicamente a partir do master existente, sem nova geração de arte. O material luminoso
é preparado uma vez, mantendo 180ms/30fps.

Odômetro ocupa 632..654, energia começa em 661, barras 690,5..705,5, autonomias 683..713 e regeneração
466..548. Unidade km ganhou 4px de margem. Velocidade, frisos, menus/AC, fontes e Vector preservados.
Assets/build/check PASS, pacote 1723463 bytes e WebP Contour 544428 bytes. Quatro suítes de navegador
PASS: 24 capturas menus/AC, 22 Vector, seis estados de escala/ponteiro e dez trocas de display;
466400 pixels centrais transparentes por estado, sem erros JavaScript. O ajuste final da unidade
foi seguido de check local completo e navegador Contour; luminância sob odômetro 11,1/255.
Evidências em `tools/headunit-dev/output/apex-gt-expanded-20260919/`. Sem deploy ou prova física.

## Frisos e leituras do Contour na versão 1.0.7

Contour recebeu dois frisos luminosos finos acima/abaixo da velocidade e unidade, por CSS
estático; ficam em x=324..488, y=280..289 e 434..443. Propulsão ocupa `(1798,218,102,30)`,
22px acima de TRAÇÃO; relógio termina em `(1885,46)` no canto superior direito. Vector preservado.
Build/check e navegador de instrumentos/menus aprovados; pacote `1711030 bytes`, 12 capturas
de menu e 466400 pixels centrais transparentes por estado, sem erros JavaScript. Teste dedicado
cobre frisos em 0/72/180 km/h e sua ausência no Vector. Evidências em
`tools/headunit-dev/output/apex-gt-speed-trim-20260919/` e
`tools/headunit-dev/output/apex-gt-trim-propulsion-20260919/`. Sem deploy ou prova física.

## Cores do modo de condução na versão 1.0.6

No cabeçalho de Contour e Vector, ECO usa verde `#8fffc1`, NORMAL branco `#ffffff` e SPORT
vermelho `#ff314b`. Modo desconhecido limpa a cor anterior e mostra `--` branco.
Build/check aprovados (17 casos e verificações de display/movimento/pacote); bundle `1710210 bytes`.
Chromium no pacote compilado validou as seis combinações e o fallback, sem erros JavaScript.
Evidências: `tools/headunit-dev/output/apex-gt-drive-mode-colors-20260919/report.json` e seis PNGs.
Geometria, bridge e Android preservados; sem publicação, deploy ou prova física.

## Regeneração verde na versão 1.0.5

Contour e Vector agora usam preenchimento e halos verdes durante regeneração; leituras e
indicadores usam `--regen:#8fffc1`. Velocidade e tração continuam azuis. Geometria, fontes,
assets, timing de 180ms/30fps e contrato permanecem os mesmos.

Build/check aprovados (17 cenários runtime, controlador, dois movimentos e pacote); quatro
suítes de navegador PASS. Amostras RGB médias do preenchimento regenerativo: `(164,241,194)`
em Contour e `(94,172,126)` em Vector, com verde dominante e tração azul preservada.
Bundle: `1709927 bytes`. Evidências em `tools/headunit-dev/output/apex-gt-regeneration-green-20260919/`,
incluindo `demonstracao/apex-gt-regeneracao-verde.mp4`: H.264, 1920x800, 29,32s, 2166995 bytes,
decodificado sem erros. Gravação com dados simulados no navegador; sem deploy ou prova física.

## Dois displays na versão 1.0.4

**Validado no modo web.** O display existente passa a se chamar **Contour**;
**Vector** é a segunda composição, baseada no Concept 03. Ambos compartilham dados, menus/AC,
resolução `1920x720` e a reserva ADAS `(520,120,880,530)`. A escolha aparece no sexto item
`DISPLAY` de AJUSTES e na configuração `Display` do tema/Lab; não é o ajuste global do host.

O manifesto usa os campos já existentes: configuração `id=apex_display_mode`, tipo `combo`,
opções `Contour, Vector`, padrão `Contour`, `stateVariable=apexDisplayMode`, grupo `Exibição`.
`src/apex-display.js` restaura `Android.getPreference('apexDisplayMode', 'Contour')`, aplica a
classe `display-contour`/`display-vector` e salva pela API temática `savePreference`.
A mudança visual é imediata e não escreve telemetria. Sem bridge, vale apenas para a sessão;
falha de persistência não é apresentada como confirmação de gravação.

A chave canônica `app.preferences.apexDisplayMode` é assinada explicitamente, mesmo ausente de
`getAvailableKeys()`: ela pertence ao canal já existente de preferências. Seu snapshot usa
`getPreference`, nunca `getCarData`. Os aliases `app.preferences.apex_display_mode`,
`apexDisplayMode` e `apex_display_mode` são fallback até chegar valor canônico. Valores inválidos
não trocam o display, chamadas repetidas não emitem troca extra e cleanup encerra o controlador.
Não há uso do `display` global, `saveSetting`, alteração de bounds ou mudança nativa.

No Lab, somente Apex GT recebe a opção Contour/Vector em Aparência. Seu adaptador persiste em
`localStorage['haval.ApexGT.apexDisplayMode']` e injeta o valor antes do harness; isso é simulação
local. O Android mantém sua própria preferência temática, sem depender de localStorage.

O asset Vector foi editado pela ferramenta integrada imagegen a partir do anexo Concept 03;
saída `exec-e0ae443c-2d0a-40e5-a09c-68826b6f68fd.png`, preservada em
`assets/vector-frame-master.png`. O [registro do asset](reference/vector-asset-prompt.md) contém
origem e resumo do prompt: preservar silhueta, vidro, fibra, metal e reflexos; remover dados,
ícones, escalas, ponteiros e luz ativa para permitir desenho dinâmico. A referência original
permanece em `reference/concept-03.png`.

`prepare-vector-assets.mjs` gera PNG/WebP lossless em `1920x720`; mantém as bandas metálicas,
acomoda somente vidro interno nas laterais e reposiciona o recesso central inferior abaixo de `y=650`.
O recesso lateral foi ampliado até `y=675` para abrigar níveis e autonomias fora da TSR.
O raster final zera alpha somente no envelope central. As reservas OEM laterais permanecem
opacas e livres de informações essenciais; os ícones do veículo são sobrepostos pela camada
nativa. A composição completa manteve os 466400 pixels centrais transparentes em todos os
estados testados. Nenhuma geração ou
transformação roda no veículo.
`vector-geometry.js` mede separadamente as duas bandas angulares do material, que não são
espelhadas. O zero da escala de velocidade foi elevado `22,5px` (`471,5 → 449`) frente à
referência; o glifo termina em `y=474`, acima da zona TSR que começa em `y=475`. A zona nativa
não muda.

`vector-gauges.js` prepara o material uma vez, mantém escalas estáticas e interpola luz/ponteiro
por `180ms` com pintura limitada a `30fps`. O renderer oculto cancela o frame e conserva apenas
os alvos recebidos; ao voltar ao display, apresenta uma vez a leitura mais recente. Contour
recebe a mesma suspensão por `apex-display-change`. A alternância preserva a telemetria comum;
reduced motion, documento oculto e cleanup também interrompem animação.

Com Vector fechado, potência aparece numa única leitura assinada, acompanhada de `TRAÇÃO` ou
`REGEN` e indicador de direção. Com menu aberto, são usadas as duas leituras compactas de
potência/regeneração para manter dados e controles visíveis. O painel Vector usa `(1404,240,214,306)`;
Contour conserva `(1412,240,232,306)`, ambos fora da reserva central.

**Validação 1.0.4:** `npm run build` e `npm run check` aprovados: 17 cenários de runtime,
controlador `check-display.mjs`, movimentos Contour/Vector e pacote offline. Lab check/build
aprovados. Bundle final: `1708504 bytes`; WebP Vector lossless: `595380 bytes`.

`npm run check:browser` passou nas quatro suítes: instrumentos Contour, menus Contour, menus
Vector e instrumentos/troca Vector. As 24 capturas de menus e todos os estados de instrumentos
verificados mantiveram 466400 pixels centrais alpha zero. Vector produziu 22 screenshots,
seis posições `0/40/80/120/160/200 km/h` com potência `−100..200 kW`, dez trocas de display,
projeções, ausência/zero, execução offline e cleanup, sem erros JavaScript. No Lab real,
Aparência e sexto item DISPLAY sincronizaram a escolha nos dois sentidos; Vector foi restaurado
após recarregar o iframe e após alternar para Obsidian e voltar, sem comandos veiculares.

QA encontrou inicialmente combustível sobre TSR até `y=565`: CSS/asset foram corrigidos. O
detalhe começa em `572`, percentual/litros ocupam `574..591`, autonomia `595..623`, barra
`635..649` e ícone `625..659`; `vector-energy-layout-check.json` aprovou 13 caixas medidas.
O painel Vector foi reduzido de 220 para 214px, terminando em `x=1618`, antes do rótulo `−100`
em `x=1622,23`. A suíte final verifica que nenhum rótulo SVG é coberto em cada captura de menu,
nos dois displays. A marcha Vector usa caixa inline-block para medição separada de SPORT.

Evidências em `tools/headunit-dev/output/apex-gt-displays-20260919/`: `browser-report.json`,
`menus-report.json`, `vector-menus-report.json` e `vector-browser-report.json` PASS, screenshots,
`vector-energy-oem-safe.png` e `vector-animation.webm`. Gradle unit/assemble desta entrega:
`BUILD SUCCESSFUL`, `45 UP-TO-DATE`, com resultados Android existentes reutilizados.
Não houve deploy/push ou teste físico. Persistência Android, comandos reais, composição OEM/ADAS,
fontes e custo de decodificação/CPU/FPS na central continuam a confirmar no veículo.

## Menus e climatização 1.0.3

`src/apex-menus.js` exporta `ApexMenus.create({root, mount})`. A factory não substitui os hooks
públicos nem assina separadamente: `src/apex-gt.js` agrega suas chaves às disponíveis na bridge,
faz uma subscription deduplicada, encaminha dados/cards/teclas e executa cleanup conjunto.
`index.html` e `scripts/build.mjs` incorporam JS/CSS no pacote offline.

O host continua responsável pelo card ativo. O estado inicial/card `0` deixa o menu fechado;
card `1` abre o menu principal; card `3` mostra climatização. O painel ocupa
`left:1412px; top:240px; width:232px; height:306px`, fora do envelope ADAS. Com menu aberto,
potência e regeneração permanecem visíveis compactadas em `y=178`, acima do painel. A navegação
visual adicional dos cards foi recolhida para não competir com esses números; a base do ponteiro
pode ficar parcialmente atrás do painel, sem esconder a escala ou as leituras elétricas.

| Vista | Conteúdo e navegação |
|---|---|
| Menu principal | INFORMAÇÕES, GRÁFICOS, AJUSTES, TRIP A / TRIP B; três linhas visíveis, com seleção por UP/DOWN e abertura por ENTER. |
| Informações | Geral com odômetro, temperaturas, níveis/autonomias; alternância para quatro pneus por UP/DOWN ou abas. |
| Gráficos | Potência assinada ou velocidade; até 18 amostras recebidas, agrupadas em janelas de 250ms, sem timer de polling. |
| Ajustes | Cinco itens veiculares: condução, propulsão, direção, regeneração e estabilidade; UP/DOWN seleciona, ENTER solicita próximo valor. Desde 1.0.4, Display é o sexto item e aplica somente uma preferência visual. |
| Viagens | Distância, tempo, velocidade média e consumo independentes para A/B; UP/DOWN alterna. ENTER_LONG solicita reset somente com B selecionado. |
| Climatização | ENTER alterna foco ventilação/temperatura; UP/DOWN ajusta; ENTER_LONG alterna AUTO; BACK_LONG alterna recirculação. |

BACK retorna de uma vista principal ao menu; a troca de card continua pertencendo ao host.
No AC, a ventilação varia de `0..7`: sair de zero pode ligar o sistema, voltar a zero solicita
seu desligamento. Temperatura usa o intervalo contratual `16..32`, em passos de `0,5`, com LO/HI
nos extremos. Botões/abas aceitam clique para seleção no navegador; reset B exige o evento longo.

As leituras adicionais usam chaves já existentes:

- Pneus: `car.basic.tire_pressure_{front_left,front_right,rear_left,rear_right}` em kPa,
  convertidos para PSI por divisão por `6.89476`; ausente, inválido ou não positivo mostra `--`.
- Trip A/B: grupos independentes descritos no [guia do contrato](../../../Themes/THEME_GUIDE.md),
  incluindo tempo e velocidade média; o reset não escreve diretamente nas chaves de viagem.
- Ajustes: `car.drive_setting.drive_mode`, `car.ev_setting.power_model_config`,
  `car.drive_setting.steering_wheel_assist_mode`, `car.ev_setting.energy_recovery_level` e
  `car.drive_setting.esp_enable`, escritos por `Android.updateCarData`.
- AC: `car.hvac.power_mode`, `fan_speed`, `driver_temperature`, `cycle_mode` e `auto_enable`,
  escritos por `Android.updateCarData`. A alternância AUTO usa `CANCEL_MAX_AC` antes da escrita;
  reset B usa a ação `RESET_DRIVE_INFO`, ambas pela API existente `triggerSystemAction`.

O comando veicular exibe espera por retorno, mas não substitui a leitura recebida. Sem valor conhecido,
incrementos/toggles exibem `AGUARDANDO DADOS`; sem bridge/método, `AÇÃO INDISPONÍVEL`.
Confirmação de envio não prova aplicação no veículo. Prioridade canônica sobre aliases e distinção
entre ausente/zero permanecem. Teclas têm debounce de `50ms`; o render é agrupado por frame só
com painel aberto. Cleanup cancela frame/feedback, remove listener de clique e limpa históricos.

A bridge mínima `1.0.1` deriva de pneus/reset B já existentes. O contrato permanece `v1.0`.
`dev/apex-gt-simulator.js` cria o mock somente no Apex GT do Lab e apenas sem bridge real,
valida as escritas permitidas e devolve telemetria pelo estado do harness. Cleanup restaura a
bridge/controle anterior; o mock não é incorporado no HTML de produção.

**Validação 1.0.3:** build de `882537 bytes`; `npm run check` aprovado (16 cenários de runtime,
movimento e pacote). `npm run check:browser` concluiu as duas suítes de instrumentos e menus,
com `browser-report.json` e `menus-report.json` PASS em
`tools/headunit-dev/output/apex-gt-menus-20260919/`. São 12 capturas de menus/AC, todas com
`466400` pixels centrais alpha zero. O Lab real foi exercitado pelos comandos simulados do volante:
alteração de HVAC e troca de card `3 → 1 → 0` aprovadas.

A suíte cobre as páginas, cinco ajustes, Trip B somente por comando longo, passos/limites de AC,
ordem CANCEL_MAX_AC antes de AUTO, recirculação, eco atrasado, bridge ausente/com erro, ausência
canônica sobre alias, isolamento do card fechado e cleanup. Gradle unit/assemble retornou
`BUILD SUCCESSFUL`, `45 UP-TO-DATE`: resultados existentes reutilizados.

Duas correções do harness foram registradas: a asserção esperava `22.0` enquanto a interface
formatava corretamente `22°C`; em outra rodada, edição concorrente provocou HMR e desconexão do
iframe. A execução final sobre o conjunto estabilizado passou. Nenhum desses eventos comprova
falha no veículo. Comandos reais, pneus, reset, representação térmica da central e navegação do
volante seguem dependentes de prova física; nenhum deploy ou push foi feito.

## Ajuste de layout 1.0.2

A assinatura visível `APEX GT CONCEPT 02` foi removida do cabeçalho. O nome do tema permanece no
manifesto, título do documento e catálogo. No espaço liberado, `INTERNA` ocupa `left:35px` e
`EXTERNA`, `left:205px`; ambos começam em `top:18px`, com limite inferior observado em `y=59`.
Os rótulos continuam distintos e os valores ainda recebem as mesmas chaves/unidades do contrato.

O modo de propulsão foi movido para a antiga região da temperatura externa:
`left:700px; top:40px; width:118px`. A área mostra somente `HEV`, `EVP`, `EV` ou `--`, sem o
rótulo decorativo `PROPULSÃO`. Não houve mudança no mapeamento ou na disponibilidade dos dados.

O odômetro passou para `left:203px; top:580px; width:220px`, terminando em `x=423` e `y=602`
com `line-height:1`, para deixar de cruzar o acabamento diagonal inferior. A área central ADAS,
as reservas OEM daquela revisão, o canvas `1920x720`, o runtime de telemetria e as animações
continuam os mesmos.
Esses ajustes foram solicitados pelo usuário e complementam as adaptações anteriores da referência.

Validações `1.0.2`: build/check do tema aprovados (16 cenários de runtime, movimento e pacote),
checks/build do Lab aprovados com 8 temas. Bundle: `844856 bytes`. Navegador final `PASS` com
`-40°F` nas duas temperaturas e `EVP`, sem colisão no cabeçalho; `466400` pixels centrais com
alpha zero por estado. O odômetro termina em `y=602` e o detalhe de combustível começa em `613`,
com folga de `11px`. A luminância máxima do raster sob o texto mais largo do odômetro (`999999`,
envelope `x=268..423`) foi `10,31/255`, sem linha brilhante atrás da leitura.

Evidências em `tools/headunit-dev/output/apex-gt-layout-refinement-20260919/`: `browser-report.json`,
`apex-gt-driving.png`, `apex-gt-header-extremes.png`, `apex-gt-limits.png`, demais estados e vídeo.
A primeira asserção de luminância mediu toda a caixa de `220px`, incluindo borda onde não há
texto, e falhou em `255`. A correção mede o envelope real dos glifos; foi um ajuste do teste, não
uma alteração do produto para esconder a borda.
Nenhum deploy, push ou teste físico foi executado; legibilidade e composição na central seguem
pendentes de prova no veículo.

## Refinamento visual 1.0.1

A edição foi feita com a ferramenta integrada `imagegen`, usando `reference/concept-02.png` como
imagem de entrada. Resumo da orientação: preservar moldura panorâmica, curvas, metal e reflexos do
Concept 02, preparando uma base limpa para os dados e a iluminação dinâmicos. O resultado
`exec-7638a05d-134e-4bdb-b67c-1a62117591bd.png` foi salvo no repositório como
`assets/apex-gt-frame-refined-master.png`. Não há geração ou chamada de serviço em runtime.
O master anterior continua disponível como histórico.

`prepare-assets.mjs` normaliza o master para `1920x720`, preserva a posição física das faixas
metálicas e interpola somente o vidro interno: a região original esquerda `x=400..575` cabe em
`x=400..519`, com ajuste espelhado à direita e transição por altura entre `y=110..569`.
O acabamento inferior é alongado continuamente abaixo de `y=570`, com deslocamento máximo de
`43px` na região central, para acomodar as três leituras do rodapé. Naquela revisão, o processo
aplicava alpha central e exclusões OEM; desde `1.0.13`, somente o envelope central é transparente
e as reservas laterais OEM permanecem opacas. Essa transformação é de asset; não muda bounds,
resolução ou compositor.
O WebP é exportado com `lossless: true`, evitando perda adicional nos reflexos e chanfros.

O elemento `img#gauge-material` apresenta a moldura e fornece o mesmo material ao renderer do
arco; o bundle contém uma única cópia da imagem. A luz do lado esquerdo segue as nervuras reais do
raster, mantendo os sulcos escuros. O canal direito é estreito e acompanha a escala de potência,
com ponteiro de acabamento chanfrado. `gauge-geometry.js` centraliza as coordenadas usadas pelo
movimento e por `gauge-scales.js` para que marcas, números e hotspots coincidam.

A leitura principal usa `Arial, Roboto, sans-serif`, peso 700: `100px` para um/dois dígitos e
`86px` para três ou mais, em caixa de `140px`. A classe `speed-three-digits` é atualizada pelo
render existente para conservar valores como `180` dentro do vidro; não altera telemetria.
Potência e regeneração usam Khand regular. Unidades e rodapé receberam ajustes de peso, contraste e espaçamento dentro
das zonas existentes. Khand e Eurostile seguem embutidas; Arial/Roboto dependem das fontes do
sistema, portanto a forma exata dos dígitos na WebView do veículo permanece **A confirmar**.

**Validação 1.0.1:** `npm run build` e `npm run check` aprovados (16 cenários de runtime,
material/geometria/lifecycle do movimento e pacote offline). Checks/build do Lab aprovados com
8 temas. Chromium offline: material carregado, seis estados de escala (`0/30/60/90/140/180`),
potência/regeneração, repouso/cleanup e **466.400 pixels centrais com alpha zero por estado**.
Comparação PNG/WebP: nenhuma diferença de alpha nem de RGB visível. Pacote daquela revisão: `845337 bytes`;
WebP lossless: `536494 bytes`. Gradle unit/assemble: `BUILD SUCCESSFUL`, `45 UP-TO-DATE`;
resultados Android existentes reutilizados, sem nova execução de testes. `git diff --check` passou.

Evidências em `tools/headunit-dev/output/apex-gt-refinement-20260919/`: `browser-report.json`,
screenshots, vídeo de animação e checker de transparência. A recaptura final passou após ajuste
de intensidade/luz especular e encaixe dos três dígitos. Foram inspecionados os estados de
condução, regeneração, zero e `180 km/h`; os checks de runtime/movimento/pacote também passaram.

A primeira comparação lossless exigiu igualdade RGBA até nos pixels invisíveis e falhou porque o
codec otimiza RGB sob alpha zero. `browser-report-hidden-rgb-check.json` preserva a tentativa.
O critério corrigido compara todo alpha e RGB somente onde visível; ambos tiveram zero diferenças.
Isso não representou perda visual do asset. A escala possui contorno escuro de `3px` para manter
legibilidade quando o ponteiro passa sob os números.

## Comandos reproduzíveis e evidências

Build e verificações locais não precisam de dependências npm dentro do tema:

```bash
cd cluster-widgets/source/v1.0/ApexGT
npm run build
npm run check
```

`npm run check:browser` exige o Lab ativo, `playwright`, `sharp` e Chromium/Chrome. O executável
padrão é o Google Chrome em `/Applications`; definir `APEX_CHROME` para outro caminho.
`APEX_LAB_URL` troca a origem e `APEX_EVIDENCE_DIR` troca a pasta de saída. As dependências podem
ser fornecidas por `NODE_PATH` de um runtime existente, sem alterar o manifesto do tema.
Para um ambiente novo, a instalação de desenvolvimento é `npm install --no-save --package-lock=false
playwright sharp`. `npm run assets` também precisa de `sharp`; não é necessário para reconstruir o
bundle a partir dos assets já presentes.

```bash
APEX_EVIDENCE_DIR=../../../../tools/headunit-dev/output/apex-gt-displays-20260919 npm run check:browser
```

O teste captura fonte e pacote sem alterar a thumbnail, o bundle ou o pacote OTA; ele falha se esses
artefatos estiverem desatualizados. As evidências históricas
da primeira versão ficam em `tools/headunit-dev/output/apex-gt-validation-20260919/` a partir da raiz: `browser-report.json`,
`apex-gt-driving.png`, `apex-gt-regeneration.png`, `apex-gt-motion-intermediate.png`,
`apex-gt-animation.webm`, `apex-gt-transparency-checker.png`, estados de projeção, zero, dados ausentes e limites. O sufixo usa a data
UTC; a sessão ocorreu em 2026-09-18 no fuso America/Sao_Paulo.

Validações históricas da versão `1.0.0`: 16 cenários de runtime e checks de movimento/pacote aprovados; relatório de navegador `PASS`, sem
erros JS, carregando o pacote por `file://` com rede offline; catálogo do Lab com 8 temas, `npm run check` e `npm run build` aprovados.
`./gradlew :app:testDebugUnitTest :app:assembleDebug` terminou `BUILD SUCCESSFUL`, com 45 tarefas
`UP-TO-DATE`; os resultados existentes foram reutilizados, sem alegação de nova execução dos testes.

A primeira rodada de QA de navegador sofreu reload do Vite porque os artefatos eram gravados
na árvore observada. A saída foi transferida para `tools/headunit-dev/output/`; a rodada final
foi repetida e aprovada. Essa falha de harness não é evidência de falha do compositor no veículo.

**Pendente no veículo:** disponibilidade/valores das chaves, legibilidade e composição com ADAS,
READY/TSR/ESP reais, estados CarPlay D3, custo de renderização e continuidade do lifecycle na WebView
embarcada. A prova de alpha no navegador não valida o compositor Android; hash do pacote, runtime
instalado e prova visual no veículo devem ser registrados separadamente.
