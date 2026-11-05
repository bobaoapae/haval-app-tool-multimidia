# Revisão 10: somente alinhamento dos numerais

Resultado: suíte npm test aprovada. Revisão visual independente aprovada para os painéis renderizados em tamanho nativo e reduzido.

## Escopo

- Lista de velocidade preservada: 0, 20, 40, 50, 60, 80, 100, 120, 140, 160, 200
- Numerais seguem a perspectiva do vidro e crescem de 26 a 40 px em direção ao espectador
- Separação mínima entre caixas orientadas de tinta: 10,85 px, incluindo 120/140/160/200
- A escala da bateria acompanha a mesma perspectiva; magnitude continua baseada no Apex Contour
- A fronteira do vidro e os numerais usam o mesmo mapeamento horizontal
- A distribuição 0–60 conserva 2,07 vezes a densidade média de pixels por km/h de 60–200
- Galáxia, partículas, névoa, vidro, regras de vermelho/verde, menus e demais tipografias preservados da revisão 9
- Somente as áreas de proteção das estrelas acompanham os numerais reposicionados
- Nenhuma opção com/sem rastros adicionada

## Verificação

- Contrato de escala/cores/independência: 14.589 asserções
- Geometria/perspectiva/fontes: 13.262 asserções
- Galáxia/proteção de texto: 43.987 asserções, 58 quadros, zero interseção com glyphs + 2 px
- Estados de menu, AC, entradas, controller e demais suítes existentes continuam aprovados
- Fonte congelada para a integração em draft PR; manifesto de hashes em qa/final-source-manifest.json

O YouTube indicado abriu, porém o player não carregou frames. Seus primeiros 20 segundos não foram inspecionados, e esta revisão não altera o movimento com base nele. Recuperação interrompida conforme a instrução mais recente.

Validação somente da prévia Canvas/Node. Navegador real, WebView, central, integração de dados e composição ADAS não validados. Não há medição de desempenho no veículo.
