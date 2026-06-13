# ADR-0001: AI-driven Development Structure

Data: 2026-05-24
Status: Aceita

## Contexto

O projeto envolve Android, WebView, displays secundários, scripts de deploy, patches e investigação operacional. Sessões longas perdem contexto facilmente.

## Decisão

Criar uma estrutura AI-driven com `.ai-context/`, `AGENTS.md`, `.codex/agents/`, `.agents/skills/`, prompts e documentação operacional.

## Consequências

- Próximas sessões devem ler contexto persistente antes de agir.
- Handoff passa a ser parte obrigatória do fluxo.
- Documentação deve ser mantida junto com mudanças relevantes.

## Relação Com Arquivos

- `AGENTS.md`
- `.ai-context/`
- `.codex/agents/`
- `.agents/skills/`
- `prompts/`
- `docs/operations/`
