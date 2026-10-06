# Changelog AI

## 2026-10-06 — acentos PT-BR

- Branch: `feat/readability-text`.
- Escopo: apenas literais exibidos no Compose; 27 ocorrências em 3 arquivos de produção.
- Alterados: `ui/screens/InstallAppsScreen.kt` (13), `ui/screens/BasicSettingsScreen.kt` (1), `ui/components/BottomBarUI.kt` (13), sob `app/src/main/java/br/com/redesurftank/havalshisuku/`.
- Teste atualizado: `app/src/test/java/br/com/redesurftank/havalshisuku/ui/readability/ReadabilitySnapshotTest.kt` (`Não instalado`).
- MainActivity e strings.xml revisados, sem correções necessárias.
- Preservados: logs `[NAV_PANE]` (NAO/botao/mantem), IDs `video`, comentários, nomes de classes/arquivos, chaves, rotas, lógica, URLs e JSON. `Pos` é abreviação; `esta tela` e `sozinha` estão corretos.
- Sem mudanças em layout, resolução, bridge, serviços ou temas. Sem adb, push, PR ou stash.
- `.ai-context/` e `.agents/skills/` não existiam neste checkout nem no diretório principal; documentação arquitetural consultada. Estes registros foram criados para atender AGENTS.md.
- Plano executado: varrer literais, conferir uso visual, corrigir acentos, atualizar literal de teste, validar e regravar snapshots.
- Risco: pequena alteração na largura dos textos; validação física não realizada (sem adb, conforme pedido).

## Validação

- `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug`: passou.
- `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*Readability*' -Proborazzi.test.record=true`: passou (26 testes Readability, sem falhas).
- `git diff --check`: passou.
- Varreduras por regex/Python em literais Kotlin e leitura de strings.xml; diff revisado para excluir lógica/logs.
- Próximos passos: 32 PNGs copiados e conferidos byte a byte em `$SP/snaps/app/after/`, mantendo nomes; commit local solicitado.
