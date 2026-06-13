# Backend Kotlin Agent

Use este agente quando a tarefa envolver `app/`, código Android, Kotlin, Java, services, managers, projetores, integração com a central ou bridge com o WebView.

## Prioridade de trabalho

1. Ler primeiro a arquitetura já documentada em `docs/README.md`, `docs/inicializacao.md`, `docs/projetores.md` e `docs/servicos-e-propriedades.md`.
2. Mapear antes de editar as classes relacionadas a:
   - vehicle data
   - services
   - managers
   - screens
   - WebView
   - comunicação com JS
3. Explicar rapidamente o impacto da mudança antes de fazer alterações maiores.

## Escopo técnico esperado

- Identificar fluxos que passam por `ServiceManager`, `ProjectorManager`, `InstrumentProjector2`, `MainUiManager`, receivers e services.
- Procurar integrações por:
  - `evaluateJavascript`
  - `JavascriptInterface`
  - `loadDataWithBaseURL`
  - listeners de `IDataChanged`
  - listeners de `IServiceManagerEvent`
- Documentar cada nova variável, propriedade ou evento descoberto antes de expandir o uso no frontend.

## Restrições

- Nunca alterar código de segurança, condução, freio, airbag, ADAS, powertrain ou qualquer função crítica sem pedido explícito.
- Nunca introduzir automações destrutivas ou comandos que alterem serviços sensíveis por padrão.
- Evitar mudanças grandes sem explicar antes o arquivo afetado, o risco e o fallback.

## Observabilidade

- Sempre que adicionar logs, usar prefixo `"[HavalDev]"`.
- Priorizar observabilidade, diagnóstico, dump de estado e deploy rápido.
- Criar modo debug quando necessário, mas mantê-lo explícito, reversível e isolado.

## Bridge com frontend

- Expor dados para o WebView apenas de forma controlada.
- Preferir variáveis bem nomeadas, documentadas e com origem conhecida.
- Ao adicionar novas variáveis para JS, registrar:
  - origem no Kotlin/Java
  - chave no `ServiceManager` ou evento
  - formato esperado no frontend

## Entrega

- Informar os arquivos alterados.
- Dizer quais variáveis/eventos novos foram identificados.
- Explicar como validar sem tocar em funcionalidades críticas do veículo.
