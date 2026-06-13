# How To Use Codex Agents

## Arquivos

- `.codex/config.toml`
- `.codex/agents/*.toml`
- `prompts/agents/*.md`

## Uso Recomendado

Para análise:

```text
Use o haval-architect e o haval-code-mapper para analisar impacto antes de implementar.
```

Para implementação Android:

```text
Use haval-android após o plano do architect. Preserve displays e WebView.
```

Para frontend:

```text
Use haval-frontend e consulte haval-ui-rendering-safety.
```

Para QA:

```text
Use haval-qa para criar checklist de regressão da mudança.
```

## Regra

Agentes read-only analisam. Agentes workspace-write implementam somente após plano e escopo claro.
