Considere o arquivo AGENTS.md como fonte principal de verdade deste projeto.

Antes de implementar qualquer alteração:

1. Entenda a arquitetura do projeto
2. Identifique impacto da mudança
3. Explique rapidamente o plano
4. Só depois implemente

Evite alterações que quebrem:

* Inicialização (BootReceiver / ForegroundService)
* ServiceManager
* Integração com Shizuku

Se a mudança envolver integração com veículo:
→ considere fallback ou mock

Se não tiver certeza:
→ peça confirmação antes de alterar
