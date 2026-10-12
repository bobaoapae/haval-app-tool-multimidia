# Obsidian Horizon

Tema OTA `v1.0` para o Display 3 em `1920x720`.

O layout usa uma faixa superior e duas estruturas laterais opacas. A área central
`x=500..1410`, `y=100..720` permanece transparente para o ADAS nativo e para a
Surface fullscreen do CarPlay quando escolhida pelo usuário.

Todos os dados de condução são elementos HTML atualizados pela bridge. A moldura
original `1920x720` fica em `assets/obsidian-horizon-frame.webp` e o build a incorpora
ao `app.html` como `data:image/webp;base64`, sem arquivo externo no pacote OTA. O tema
não possui SVGs, fontes remotas ou informações dinâmicas fixadas na arte.
Os pictogramas compactos de combustível e bateria fazem parte da moldura; percentuais, barras e
autonomias continuam sendo renderizados dinamicamente pelo tema. A travessa inferior também faz
parte do raster e fica à frente do conteúdo nativo, preservando o rodapé sem fechar a abertura
central destinada ao ADAS/mapa.

O card `Menu` possui quatro submenus: informações gerais, gráficos, ajustes e `Trip A / Trip B`.
O painel de viagens mostra separadamente distância, tempo, velocidade média e consumo médio da
viagem atual e do acumulado desde o reset. Ao abrir qualquer submenu, um ícone discreto indica o
comando Voltar do volante; ele desaparece no menu principal.

A moldura distribuída preserva o raster original em `1920x720`. O ajuste de tamanho dos dois
pictogramas é localizado; frisos, texturas, vincos, cabeçalho, painéis e rodapé não são
regenerados nem redimensionados.


## Comandos

```bash
npm run build
npm run check
```

O build gera `dist/app.html` e publica `app.html`, `theme.xml` e `thumbnail.png` em
`cluster-widgets/Themes/v1.0/ObsidianHorizon/`.

Para carregar dados simulados no navegador mantendo o centro transparente, abra:

```text
index.html?preview=1
```

Para validar também o estado lógico do CarPlay, sem inserir overlay no centro:

```text
index.html?preview=1&projection=carplay
```

Para visualizar o estado de regeneração:

```text
index.html?preview=1&regen=1
```
