# HANDOFF — D1/D3 background sync

Atualizado em: 2026-09-24

## Objetivo

Evitar que os insets (native masks) do D3 apareçam com wallpaper composto enquanto o wallpaper do D1 ainda não pintou no cold start / carga async.

## O que mudou

- Novo `ClusterBackgroundSync`: identidade do wallpaper, ready latch, listener, resolve still compartilhado.
- `InstrumentProjector` marca attached/ready/not-ready e usa o resolve compartilhado.
- `InstrumentProjector2.updateNativeMaskViews` também espera D1 ready (além de theme live); remove fallback assimétrico `car-bg.png`.
- `ProjectorManager` usa `LinkedHashMap` e cria D1 antes de D3.

## Arquivos

- `app/src/main/java/.../managers/ClusterBackgroundSync.kt` (novo)
- `app/src/main/java/.../projectors/InstrumentProjector.kt`
- `app/src/main/java/.../projectors/InstrumentProjector2.kt`
- `app/src/main/java/.../managers/ProjectorManager.java`
- `app/src/test/java/.../managers/ClusterBackgroundSyncTest.kt` (novo)
- `docs/architecture/projector-flow.md`
- `docs/architecture/display-system.md`

## Riscos

- Se D1 falhar ao pintar e never mark ready, máscaras D3 ficam down enquanto still for esperado.
- IMAGE_URL ainda depende de Coil; o gate segura insets até o ready do D1.

## Próximos passos

- Validar visual no carro: cold start Minimalist/THEME — insets só após wallpaper D1.
- Trocar IMAGE_URL e confirmar os dois lados juntos.
- Com app no D1, D3 masks devem continuar ok (hold skipped).

## Comandos / testes

- Unit: `./gradlew :app:testDebugUnitTest --tests br.com.redesurftank.havalshisuku.managers.ClusterBackgroundSyncTest` — **PASS** (7 tests, JDK 17).
- Assemble completo: não rodado nesta sessão.
- Validação no carro: pendente.
