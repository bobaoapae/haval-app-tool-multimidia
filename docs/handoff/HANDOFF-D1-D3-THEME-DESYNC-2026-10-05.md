# HANDOFF — D1 background vs D3 insets desync on power-on (2026-10-05 night)

Atualizado em: 2026-10-05 (branch `feature/new-screen-enhancements-v8.1` = HAV-26 tip + D1 ready-clear)

## Sintoma

No ligar do carro (~18:09): wallpaper do **D1 ausente**, insets (native masks) do **D3 presentes**. Depois recuperou (screencap D1 ~18:38 já com `car-bg.png`).

## Timeline do boot (cluster-events-20261005.log)

| t | event |
|---|--------|
| 18:09:51 | `app_start` |
| 18:09:59.656 | `projector_created` displayId=**1** |
| 18:10:00.355 | `projector_created` displayId=**3** |
| 18:10:02.717 | `cluster_webview_page_finished` Minimalist |
| 18:10:02.744 | `startup_report_reconcile` **carPoweredOff=true** readyState=0 |
| 18:10:10.678 | `POWER_ON_EVENT` (driving ready) — **~8s depois** do theme live |

Prefs/theme OK: `minimalist` + `THEME`/`car-bg.png`. `ClusterBackgroundSync` no APK.

## Causa

Mesma família do bug documentado em `ServiceManager.isMainScreenOn()` (2026-08-24): insets framing nothing.

Buraco residual: `carMainScreenOff` / `!isScreenOn` escondia o D1 **sem** `markD1NotReady()`. Latch mentiroso → hold aberto → D3 podia reaparecer via `page_finished` com insets e D1 vazio.

## Fix implementado (`feature/new-screen-enhancements-v8.1`)

- `InstrumentProjector`: `clearD1Ready` em `carMainScreenOff` e no ramo screen-off de `updateBackgroundVisibility`; events `d1_bg_ready` / `d1_bg_not_ready`.
- `InstrumentProjector2`: `native_masks_hold` durable quando o gate segura as máscaras.
- `docs/architecture/projector-flow.md` atualizado.

Arquivos: `InstrumentProjector.kt`, `InstrumentProjector2.kt`, `projector-flow.md`, este handoff.

## Landing em PR do Marcelo → preview

**Sim, é doable** — o mesmo bug existe em `bobaoapae/preview` e na head do [#154](https://github.com/bobaoapae/haval-app-tool-multimidia/pull/154) (`mumu/hav-26-virtual-cluster-toggle`, HAV-26, draft, autor marcelofp). `ClusterBackgroundSync` já está no preview.

| Opção | Prós | Contras |
|-------|------|---------|
| **PR satélite → head do #154** | Viaja junto no merge HAV-26; toca a mesma área (projectors) | Precisa ok do Marcelo / push na branch `mumu/...`; mistura escopos (HAV-26 diz “D1 permanece independente”) |
| **PR separado netseek → preview** (recomendado) | Review limpo; não atrasa #154; patch pequeno | Dois merges |
| Empurrar direto no #154 sem falar | — | Não fazer |

[#152](https://github.com/bobaoapae/haval-app-tool-multimidia/pull/152) (HAV-24 AA) não tem relação.

## Consolidação com PR #154 (HAV-26)

`feature/new-screen-enhancements-v8.1` foi **fast-forward** para `bobaoapae/mumu/hav-26-virtual-cluster-toggle` (`87d998a`), depois o fix D1 foi reaplicado (stash pop, sem conflitos).

Estado atual (uncommitted): tip HAV-26 + diffs D1 ready-clear / traces / handoff.

### Testes locais

| Suite | Resultado |
|-------|-----------|
| `python -m unittest discover -s scripts/virtual-cluster-tests -v` (JAVA_HOME=JBR21, PYTHONUTF8=1) | **PASS** (6 tests / 15 host lifecycle) |
| `node cluster-widgets/dev/check-cluster-visibility.mjs` | **PASS** |
| `:app:assembleDebug` | **PASS** (`BUILD SUCCESSFUL`, APK `app-debug.apk` ~128 MB, 2026-10-05 19:29) |
| `:app:testDebugUnitTest --tests …ClusterBackgroundSyncTest` | **PASS** |
| Verify `clearD1Ready` in `InstrumentProjector.class` | **FOUND** |

Gradle 9.3.1 zip was completed via resume curl into wrapper dists (~137 MB) after earlier timeouts. JDK: Android Studio JBR21 + AVG truststore when present.

Notas: o suite Python falha com o `javac` do Oracle PATH (`invalid source release: 17`) e com encoding cp1252 em `TelasScreen.kt` sem `PYTHONUTF8=1`. Usar JBR.

### Também neste PR (AA same-size layout refresh) — já commitado `91a8c37`

Código + unit tests + `scripts/aa-patches/patch_guide.md`: nudge 1px ao devolver AA para display 0 com o mesmo tamanho (sem `onConfigurationChanged`), para resetar o sidebar crop do cluster. **Falta:** validação no carro (cluster → D0 same 1920×720).

### Fora deste PR

- `SilentApkInstall*` / `InstallAppsScreen.kt` — stash `wip-silent-apk-unrelated`.

Commit/push/PR: D1 ready-clear + HAV-26 tip + AA `91a8c37` → `bobaoapae/preview` (consolida #154).
