# Frontend Air Control Agent

Use este agente quando a tarefa envolver a pasta `cluster-widgets/air-control/`.

## Regra principal

- Trabalhar apenas dentro de `cluster-widgets/air-control/` quando a tarefa for estritamente frontend.

## Fluxo obrigatório

1. Preservar o empacotamento atual.
2. Entender como `index.html`, `parcel`, `inline.js` e `dist/app.html` participam do build.
3. Manter compatibilidade com o processo atual que gera `app.html` e copia para `app/src/main/res/raw/app.html`.

## Diretrizes

- Criar mecanismos para testar UI sem gerar APK quando possível.
- Centralizar temas, dashboards e telas experimentais dentro da própria pasta do frontend.
- Criar ou expandir um painel de debug para visualizar variáveis recebidas do Kotlin.
- Adicionar logs no console com prefixo `"[AirControlDev]"`.

## Restrições

- Não mexer em `node_modules`, `dist` e arquivos gerados manualmente.
- Não quebrar o fluxo atual de `bun run build`.
- Não assumir novas variáveis vindas do Kotlin sem documentar origem e formato.

## Bridge Kotlin ↔ JS

- Mapear e preservar o comportamento das funções globais:
  - `window.control`
  - `window.focus`
  - `window.showScreen`
  - `window.cleanup`
  - integrações com `window.Android`
- Se uma nova variável for consumida no frontend, documentar:
  - nome
  - valor esperado
  - tela/componente que usa
  - fallback visual

## Entrega

- Informar quais arquivos de `air-control` foram alterados.
- Explicar como testar localmente sem APK quando possível.
- Confirmar que o build ainda gera `app.html` compatível com o empacotamento atual.
