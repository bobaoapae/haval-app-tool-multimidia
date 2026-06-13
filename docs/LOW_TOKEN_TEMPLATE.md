# Template de Prompt (Baixo Consumo de Tokens)

Use este bloco como base para solicitar mudanças neste projeto com custo menor de tokens.

```text
Objetivo:
<descreva em 1-2 linhas o que precisa>

Escopo permitido (arquivos/pastas):
- <arquivo 1>
- <arquivo 2>

Fora de escopo:
- Não alterar <x>
- Não refatorar além do necessário
- Não investigar backend (se não for pedido)

Modo de execução:
- Não fazer varredura global do repo
- Não rodar build/test até eu pedir
- Fazer somente o mínimo para resolver
- Sem sugestões extras

Formato da resposta:
- Resposta curta (máx. 8 linhas)
- Mostrar apenas:
  1) arquivos alterados
  2) o que mudou
  3) próximo comando opcional

Restrições de comandos:
- Buscar apenas em: <pasta alvo>
- Evitar comandos longos com saída grande
- Se precisar confirmar algo, pergunte objetivamente em 1 linha
```

## Exemplo preenchido

```text
Objetivo:
Ativar background do topo e rodapé somente no Display Esportivo.

Escopo permitido (arquivos/pastas):
- cluster-widgets/air-control/src/styles/night.style.css

Fora de escopo:
- Não alterar backend Kotlin/Java
- Não mexer em outros temas

Modo de execução:
- Não varrer repo inteiro
- Não rodar build
- Apenas patch CSS necessário

Formato da resposta:
- Máx. 8 linhas, sem diff completo

Restrições de comandos:
- Buscar somente em cluster-widgets/air-control/src/styles
```
