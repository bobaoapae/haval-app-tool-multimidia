# Handoff Inicial

Atualizado em: 2026-05-24

## Estado Atual

Projeto Android Haval/GWM com dashboard WebView no cluster, integração Shizuku, serviços veiculares e toolkit de deploy/logs. Esta etapa criou apenas estrutura documental e operacional AI-driven.

## Arquitetura Identificada

- Android Kotlin/Java + Compose.
- `ForegroundService` inicializa ambiente privilegiado.
- `ServiceManager` distribui dados/eventos veiculares.
- `ProjectorManager` cria presentations.
- `InstrumentProjector2` renderiza WebView no display 3.
- `cluster-widgets/` gera HTML inlined.
- `tools/headunit-dev/` faz deploy e diagnóstico.

## Módulos Principais

- Android: `app/`
- Frontend cluster: `cluster-widgets/default/`, `basic/`, `basic-light/`, `air-control/`
- Deploy: `tools/headunit-dev/`
- Patches: `scripts/aa-patches/`, `scripts/carplay-patches/`
- Docs existentes: `docs/` em minúsculo é o caminho canônico.

## Arquivos Críticos

Ver `.ai-context/CRITICAL-FILES.md`.

## Riscos Atuais

- CarPlay e Android Auto no cluster 3 exigem validação cuidadosa.
- WebView pode gerar flickering ou CPU alta se receber loops.
- Referências antigas a `DOCS/` devem ser migradas para `docs/`.
- Worktree já continha alterações locais funcionais.

## Próximos Passos Sugeridos

1. Validar estrutura criada.
2. Rodar build/testes seguros.
3. Revisar docs geradas pelo mantenedor.
4. Usar prompts de início/fim em próximas sessões.

## Skills Criadas

- `haval-kotlin-js-bridge`
- `haval-webview-lifecycle`
- `haval-cluster-performance`
- `haval-display-resolution-safety`
- `haval-android-secondary-display`
- `haval-ui-rendering-safety`
- `haval-build-validation`
- `haval-qa-regression`
- `haval-docs-handoff`

## Agents Criados

- `haval-architect`
- `haval-code-mapper`
- `haval-frontend`
- `haval-android`
- `haval-webview`
- `haval-performance`
- `haval-qa`
- `haval-docs-scribe`

## Como Iniciar Nova Sessão

```text
Leia prompts/start-session.md e continue do handoff atual.
```

## Como Encerrar Sessão

```text
Use prompts/end-session.md para encerrar e atualizar o contexto.
```

## Comandos Identificados

- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`
- `cd cluster-widgets/default && npm run build`
- `./tools/headunit-dev/headunit.sh deploy-apk`
- `./tools/headunit-dev/headunit.sh deploy-air-control`

## Dúvidas a Confirmar

- Pasta canonical de docs.
- Temas ativos em produção.
- Matriz mínima de regressão antes de deploy.
