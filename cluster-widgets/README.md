# Cluster Widgets Theme Lab

Use o Theme Lab para testar todas as pastas de tema pelo mesmo servidor, mantendo o viewport real
do Display 3 em `1920x720`.

## Iniciar

```bash
cd cluster-widgets
npm install
npm run dev
```

Abra `http://localhost:1234/`.

O seletor descobre automaticamente:

- fontes editáveis em `source/v1.0/<tema>/`;
- pacotes OTA sem fonte correspondente em `Themes/v1.0/<tema>/`;
- pacotes legados em `Themes/<tema>/`, incluindo Sport Colors e Sport Colors Leve.

O launcher e o adaptador de teclado são somente de desenvolvimento. Eles não são copiados para o
APK nem para os pacotes OTA.

O Theme Lab também aplica a todos os temas uma única simulação da camada nativa frontal do cluster:
`READY`, placa de velocidade e ícone ESP. Esses elementos permanecem nas coordenadas físicas do
Display 3, acima de todo o conteúdo e sem capturar cliques. As áreas reservadas ajudam a identificar
linhas, mostradores ou informações que um tema posicionou indevidamente atrás dos ícones nativos.
Nos pacotes Sport Colors, o grupo legado `.g20-v2-native-status-mock` é ocultado pelo simulador
para não duplicar `READY`, ESP e a placa OEM; o TSR grande do próprio tema continua disponível.
O mesmo adaptador local mantém a espessura, o brilho e o serrilhado original das escalas, alterando
somente a cadência variável dos dentes para um espaçamento uniforme e simétrico. Nos dois Sport,
os blocos `.g20-v2-speed-readout` e `.g20-v2-power-readout` usam `top: 380px`. O RPM mantém sua
posição original.
Na faixa inferior do Analógico V2, temperaturas e odômetro usam o Modelo 2 "Dados Flutuantes":
o SVG estático `sport-floating-data-glow.svg` desenha somente três traços luminosos brancos
atrás dos dados, sem faixa, cápsula ou texto embutido. O cabeçalho usa o SVG independente
`sport-floating-header-glow.svg`, permitindo que cada traço fique centralizado sob horário, marcha
e modo sem deslocar as linhas das temperaturas e do odômetro. Os dois SVGs também fornecem uma
vinheta preta difusa atrás das informações, sem borda visível. Todos os textos continuam dinâmicos
e separados da ornamentação, com numerais tabulares.
Esse teste vetorial existe somente no Theme Lab. O APK, os pacotes legados e seus hashes permanecem
inalterados.

No Sport Colors/Analógico V2, o mock nativo de projeção — e, nos temas que expõem os estados, o
CarPlay ativado — substitui a antiga janela central por uma camada de navegação em todo o canvas
lógico `1920x720`. A imagem usa `cover`, sem distorção, com gradiente horizontal estático: laterais
quase pretas para preservar os instrumentos e corredor central transparente para manter o mapa
legível. Essa adaptação existe somente no Theme Lab e não modifica o pacote do tema, o APK ou a
projeção real da central.

## Tema, modo e configuração

- **Tema** é a pasta carregada, por exemplo `source/v1.0/default` ou `Themes/SportRed`.
- **Modo do display** é uma opção interna do tema. No Sport Colors, `Analógico V2` fica no item
  `Display` do menu.
- **Configuração** altera a aparência do tema. Nas fontes v1.0, use o botão `Aparência`; no Sport
  Colors, use o item `Cores` do menu.
- **Climatização** é o card 3. Use `]`/`Page Down` até chegar ao card 3 ou selecione `AC / dados`
  nos temas v1.0.

## Teclado

| Tecla | Ação |
|---|---|
| `[` / `]` ou `Page Up` / `Page Down` | Alterna cards 0, 1 e 3 |
| `↑` / `↓` | Navega ou ajusta o controle em foco |
| `Enter` | Seleciona; no AC alterna ventilador/temperatura |
| `Backspace` / `Escape` | Volta ao menu principal |
| `Espaço` | Alterna AUTO no AC |
| `A` / `P` / `R` | MAX AUTO / power / recirculação nos pacotes legados |

Nos temas v1.0, os botões `Aparência`, `AC / dados`, `Atalhos` e `Testes` abrem apenas o painel
necessário. Todos ficam fechados por padrão para preservar uma prévia limpa.

## Verificar catálogo

```bash
npm run check
```

Esse comando valida que Default, Minimalist, SportRed e SportRedLite continuam detectáveis.
