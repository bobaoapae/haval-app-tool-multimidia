# Vector / Concept 03 — origem do asset

Referência do usuário: `codex-clipboard-1d57f808-dae8-4e3a-9018-d764c1ef3fc0.png`,
preservada neste diretório como `concept-03.png`.

O master foi editado com a ferramenta integrada imagegen, tendo a imagem fornecida
pelo usuário como alvo. O resultado aprovado foi preservado no repositório em
`../assets/vector-frame-master.png`; esse arquivo é a origem reproduzível dos derivados e não
depende do diretório local temporário usado durante a geração.

## Especificação resumida do prompt

Este resumo documenta o pedido visual; não é uma transcrição literal do prompt:

- Fazer edição precisa e extração do fundo a partir do protótipo, mantendo a proporção 8:3,
  a silhueta, facetas de vidro, fibra de carbono, chanfros e escovado metálico.
- Preservar os reflexos, ranhuras das bandas segmentadas, detalhes vermelhos das pontas e
  frisos estruturais cianos, com acabamento nítido e sem recorte ou estilização cartoon.
- Remover textos, dígitos, assinatura, unidades, marcas de escala, ícones, preenchimentos
  de combustível/bateria e divisórias de informação.
- Remover ponteiros e bandas azuis ativas existentes, conservando a superfície prateada
  neutra, as ranhuras e os detalhes vermelhos decorativos.
- Deixar os recessos do cabeçalho, marcha e rodapé livres para dados dinâmicos. Manter
  o vidro lateral escuro e eliminar cenário, horizonte e colchetes da área central.

## Preparação determinística

`scripts/prepare-vector-assets.mjs` normaliza a imagem para 1920×720 e gera PNG/WebP
lossless. A imagem gerada é tratada como material; nenhum texto do protótipo é usado
como telemetria.

As bandas metálicas permanecem nas coordenadas da referência normalizada. Somente
o vidro interno é comprimido em `x=400..519` e no trecho espelhado. O recesso inferior
central é reposicionado abaixo de `y=650` mediante remapeamento contínuo, preservando
o acabamento em vez de cortá-lo na borda da reserva nativa.
Nesse rodapé, o recesso livre entre aproximadamente `x=660..1260` é ampliado para
`x=520..1400`, com transição contínua nas extremidades. Isso acomoda as quatro leituras
obrigatórias sem colocar TRIP ou odômetro sobre as diagonais metálicas.

Após a preparação, o processo força alpha zero somente em `(520,120,880,530)`. A área central
tem exatamente 466400 pixels transparentes. As três reservas OEM READY/TSR/ESP permanecem
opacas; o layout não coloca dados essenciais nelas e os ícones reais são compostos pela camada
nativa em primeiro plano. Fontes, números, menus, escalas e luzes são componentes dinâmicos
separados do raster.
