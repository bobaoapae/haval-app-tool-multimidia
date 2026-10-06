# ADR-0003: Use Repo-scoped Codex Skills

Data: 2026-05-24
Status: Aceita

## Contexto

O projeto tem cuidados recorrentes: bridge Kotlin/JS, lifecycle de WebView, display secundário, performance do cluster, QA e handoff.

## Decisão

Criar skills em `.agents/skills/` para orientar tarefas recorrentes dentro do repositório.

## Consequências

- Agentes e sessões futuras devem consultar skills aplicáveis.
- Skills devem ser práticas, com checklist e referências.
- Não substituem leitura do código real.

## Relação Com Arquivos

- `.agents/skills/haval-kotlin-js-bridge/`
- `.agents/skills/haval-webview-lifecycle/`
- `.agents/skills/haval-cluster-performance/`
- `.agents/skills/haval-display-resolution-safety/`
- `.agents/skills/haval-qa-regression/`
