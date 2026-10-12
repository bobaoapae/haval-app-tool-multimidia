# Numerais alinhados · revisão 10

Prévia local executável com dados e controles simulados. Extraia a pasta e abra `index.html`. Scripts e fontes são locais. Não há login, conexão com o carro, Android bridge ou envio de comandos.

Esta revisão altera somente o alinhamento, tamanho progressivo e distribuição dos numerais das escalas. Fontes das demais informações, menus, galáxia, material do vidro e controles permanecem como na revisão 9. As áreas de proteção das estrelas acompanham as novas posições dos numerais. Não foi adicionada a opção com/sem rastros.

## Alterações solicitadas

A faixa esquerda agora representa **velocidade em km/h**, com a mesma leitura do velocímetro central. Seus numerais são exatamente: **0, 20, 40, 50, 60, 80, 100, 120, 140, 160 e 200**.

- 0–60 ocupa 310 dos 660 pixels de percurso útil; sua densidade média é 2,07 vezes a de 60–200
- Acima de 60, a densidade diminui gradualmente: 2,90; 2,85; 2,825; 2,80; 2,775 e 1,675 pixels por km/h
- Uma única interpolação contínua em `scales.js` posiciona números e fronteira do vidro; não existem curvas diferentes para desenho e leitura
- Vermelho à esquerda cresce de zero em 120 km/h até intensidade total em 200, conforme o pedido mais recente
- Os numerais da velocidade crescem continuamente de 26 a 40 px em direção ao espectador e seguem uma linha inclinada paralela ao vidro, com separação mínima entre as caixas de tinta de 10,85 px. A escala da bateria acompanha a mesma perspectiva. O número central continua Rajdhani SemiBold

A bateria permanece independente da velocidade e da tração híbrida: consumo positivo azul, recuperação negativa verde e zero neutro. O valor é assinado; os números da escala indicam magnitude.

## Curva real do Apex Contour

A fonte foi inspecionada em `src/gauge-geometry.js`, `src/gauge-scales.js` e `src/gauge-motion.js` do Apex GT. Após a transformação `mapY`, a regeneração observada ocupa estas distâncias a partir de zero:

| Magnitude kW | Distância no Apex |
|---|---:|
| 0 | 0 |
| 10 | 39 |
| 20 | 74 |
| 30 | 106 |
| 40 | 134 |
| 50 | 159 |
| 75 | 186 |
| 100 | 207,6 |

Essas proporções foram normalizadas para a faixa horizontal: **76,59% do percurso para 0–50 kW**, 23,41% para 50–100. A mesma escala fixa de magnitude é usada para os dois sinais, evitando deslocar os números quando o fluxo muda de direção. Essa simetrização é uma adaptação: o Apex original tem diferenças pequenas entre seus lados positivo e negativo. O estilo Vector usa outra curva e não foi tomado como base para essa expansão.

±100 kW é o limite visual do Contour, não uma alegação sobre a potência máxima do veículo. Valores numéricos acima da escala são preservados, enquanto a geometria e a opacidade são saturadas no extremo. O vídeo usa valores dentro do domínio visual. Corrente real, polaridade, calibração e host continuam não validados.

## Galáxia e material

As estrelas são projetadas em profundidade e se afastam do centro para as bordas. Seu deslocamento integra a velocidade ao longo do tempo; não usa potência nem velocidade multiplicada pelo tempo absoluto. A zero, ficam paradas; em movimento, a dispersão cresce continuamente com a velocidade. Roda livre mantém movimento; desaceleração o reduz; parar e reiniciar não reinicia o campo. Rastros são limitados a 14 pixels e textos recebem proteção localizada.

O vidro, aresta alta e pontiaguda, névoa irregular e partículas internos da revisão 8 foram preservados. A nova relação velocidade/fronteira/vermelho substitui intencionalmente o movimento medido do vídeo original; esta revisão não afirma ter o mesmo roteiro de luz da referência.

Origem visual: [AmazingUI / Dribbble 24751128](https://dribbble.com/shots/24751128-Automotive-HMI-Rolls-Royce-Car-Cluster). Não há pixels ou áudio dessa referência no pacote. O material é uma reconstrução Canvas 2D independente; Three.js não foi usado.

## Painel e menus

Mantidos: Trip A e média nos cantos superiores; combustível, litros estimados, autonomias e bateria maiores embaixo; total estimado central; RPM entre os anéis. Textos flutuantes com sombra, sem faixa permanente inferior ou marca no topo.

Menu principal, gráficos e AC permanecem na região central abaixo do velocímetro, agora sem fundo opaco. Seleção usa marcas finas e texto com sombra. Os gráficos usam polilinhas cinza discretas com amostras e linha zero, sem barras preenchidas. Conteúdo e ações locais seguem o contrato real do Apex GT: informações/pneus, gráficos, ajustes, Trip A/B e AC. O rodapé dos menus agora repete velocidade em km/h. Os controles acessíveis abaixo do Canvas, ponteiro, setas, Enter, ações longas e fechar continuam disponíveis. `MENU-COVERAGE.md` descreve os contratos e limitações; `APEX-COVERAGE.md` relaciona os campos.

## Vídeo final de 60 segundos

`Impulse_Motion_drive.mp4` contém 50 s de escalas e mais 10 s de menus:

1. 0–2 s: velocidade zero e estrelas paradas
2. 2–13 s: 20, 40, 50 e 60 km/h, com maior percurso urbano e consumo azul em pequenas variações
3. 13–21 s: 80, 100 e 120 km/h
4. 21–32 s: 140, 160 e 200 km/h, mostrando aumento gradual do vermelho
5. 32–43 s: desaceleração, mudanças de recuperação entre 10 e 50 kW, depois 75 e 100 kW na região comprimida
6. 43–50 s: desaceleração final e retorno a zero
7. 50–60 s: cena pausada separada a 60 km/h, mostrando menu transparente, gráfico cinza de linhas finas, AC e fechamento

É um roteiro ilustrativo comprimido no tempo, não uma simulação física de desempenho ou gravação de condução. Legendas identificam cada etapa. O painel/menu interativo completo permanece no código; o trecho principal concentra-se nas escalas e animações pedidas, seguido da demonstração dos menus transparentes.

## Executar e validar

A prévia HTML não precisa instalar dependências. Para testes/exportação nativa: Node.js, FFmpeg e `npm install` com `@napi-rs/canvas` na versão fixada.

- `npm test`: suíte completa de dados, geometria, material, galáxia, estados, interação e legibilidade
- `npm run render:panel`: PNG atualizado
- `npm run render:final`: vídeo combinado de 60 s
- `npm run render:menus`: roteiro opcional de menus/AC de 40 s, salvo em outro arquivo

Os testes usam Node/Canvas e uma fixture DOM explícita. **Navegador real/WebView, GPU, desempenho em tempo real, veículo e integração ADAS não validados.** A posição central ainda conflita com a área nativa original de ADAS/mapa. Composição e indicadores necessários precisam ser resolvidos antes de instalação. A exportação a 30 fps não comprova desempenho embarcado. Fontes e licenças estão em `assets/`.
