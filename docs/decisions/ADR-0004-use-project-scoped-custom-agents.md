# ADR-0004: Use Project-scoped Custom Agents

Data: 2026-05-24
Status: Aceita

## Contexto

Tarefas neste projeto frequentemente exigem separação entre análise, mapeamento, implementação, QA e documentação.

## Decisão

Criar custom agents em `.codex/agents/` com escopos específicos e sandbox conservador.

## Consequências

- Análise e QA devem ser read-only por padrão.
- Implementação deve ocorrer apenas após mapeamento.
- Agente de documentação não altera código funcional.

## Relação Com Arquivos

- `.codex/config.toml`
- `.codex/agents/haval-architect.toml`
- `.codex/agents/haval-code-mapper.toml`
- `.codex/agents/haval-android.toml`
- `.codex/agents/haval-webview.toml`
- `.codex/agents/haval-docs-scribe.toml`
