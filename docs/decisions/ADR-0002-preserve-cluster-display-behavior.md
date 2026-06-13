# ADR-0002: Preserve Cluster Display Behavior

Data: 2026-05-24
Status: Aceita

## Contexto

O cluster usa WebView transparente no display 3 e pode coexistir com CarPlay, Android Auto e apps nativos. Alterações em bounds, CSS ou z-order podem causar tela preta, sobreposição ou perda de informações.

## Decisão

Toda mudança em cluster/display deve preservar comportamento existente e exigir validação explícita quando alterar resolução, layout, bounds ou projeções.

## Consequências

- Mudanças visuais devem ser pequenas e testáveis.
- CarPlay segue contrato próprio em `docs/carplay-cluster-regression-contract.md`.
- Display 3 exige checklist visual e técnico.

## Relação Com Arquivos

- `ProjectorManager.java`
- `InstrumentProjector2.kt`
- `DisplayAppLauncher.kt`
- `cluster-widgets/default/src/styles/night.style.css`
- `docs/carplay-cluster-regression-contract.md`
