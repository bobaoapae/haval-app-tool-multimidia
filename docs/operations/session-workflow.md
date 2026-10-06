# Session Workflow

## Início

```text
Leia prompts/start-session.md e continue do handoff atual.
```

O agente deve ler:

- `AGENTS.md`
- `.ai-context/`
- `docs/architecture/`

## Durante a Sessão

- Mapear antes de editar.
- Usar agents para tarefas complexas.
- Usar skills por domínio.
- Implementar somente o necessário.
- Validar com comandos disponíveis.

## Encerramento

```text
Use prompts/end-session.md para encerrar e atualizar o contexto.
```

Atualizar:

- `.ai-context/HANDOFF.md`
- `.ai-context/CHANGELOG-AI.md`
- `.ai-context/NEXT-STEPS.md`
- `.ai-context/KNOWN-ISSUES.md`, se necessário.
