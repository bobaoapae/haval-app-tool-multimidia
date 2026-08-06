# Visão Geral

Este projeto é um app Android não oficial para a central multimídia Haval/GWM. O código combina Kotlin, Java, Jetpack Compose, serviços Android, Shizuku, comandos shell/telnet e um dashboard WebView renderizado no cluster por `Presentation`.

O objetivo provável é controlar/estender funções da central, renderizar um cluster customizado, gerenciar temas HTML, integrar dados veiculares e enviar apps/projeções como CarPlay e Android Auto para displays secundários.

Stack principal: Android Kotlin/Java, Compose, WebView, JavaScript/HTML/CSS, Parcel, Shizuku, scripts shell e PowerShell.

# Regras Obrigatórias

- Sempre ler `.ai-context/` antes de implementar.
- Sempre preservar comportamento existente.
- Nunca alterar resolução/layout do cluster sem validação explícita.
- Nunca quebrar integração Android/WebView.
- Nunca introduzir renderização pesada sem análise.
- Nunca alterar bridge Kotlin ↔ JS sem validar impactos.
- Nunca apagar documentação AI.
- Nunca criar decisões inventadas; use "A confirmar" quando não houver evidência.
- Nunca reverter alterações locais não relacionadas.
- Para CarPlay, ler `docs/carplay-cluster-regression-contract.md` antes de qualquer mudança.
- Manter CarPlay e Android Auto isolados, salvo pedido explícito para mexer nos dois.

# Fluxo Obrigatório de Trabalho

1. Ler `AGENTS.md`.
2. Ler `.ai-context/`.
3. Ler `docs/architecture/`.
4. Entender estado atual.
5. Propor plano.
6. Implementar somente o necessário.
7. Validar.
8. Atualizar documentação.
9. Atualizar handoff.

# Padrões de Código

- Manter estilo existente.
- Evitar duplicação.
- Preservar nomes e convenções.
- Evitar mudanças amplas sem necessidade.
- Preferir alterações pequenas e seguras.
- Não refatorar código funcional durante tarefas documentais.
- Não editar arquivos gerados se a fonte real estiver em outro lugar.

# Performance

- Evitar flickering.
- Evitar loops desnecessários.
- Evitar `setInterval` excessivo.
- Evitar listeners sem cleanup.
- Evitar memory leak.
- Evitar imagens/assets pesados.
- Preservar fluidez do cluster.
- Medir risco antes de adicionar animações, filtros, shadows ou DOM frequente.

# WebView

- Preservar compatibilidade.
- Validar timing de carregamento.
- Validar `evaluateJavascript`, se usado.
- Evitar chamadas concorrentes sem controle.
- Validar cleanup.
- Documentar mudanças em bridge.
- Preservar `window.control`, `window.focus`, `window.showScreen`, `window.cleanup` e `window.Android`.

# Android

- Preservar lifecycle.
- Evitar memory leaks.
- Respeitar limites de hardware embarcado.
- Preservar integração com display secundário.
- Validar logs e erros.
- Não mexer em Shizuku, receivers, services ou patches sem mapear impacto.

# Frontend/UI

- Preservar resolução esperada.
- Preservar responsividade no cluster.
- Evitar layout shift.
- Evitar animações pesadas.
- Evitar alterações visuais não solicitadas.
- Validar em simulador e, quando necessário, na central real.

# Testes e Validação

- Rodar comandos disponíveis.
- Se algum comando falhar, documentar motivo.
- Não afirmar que testou se não testou.
- Registrar validações em `.ai-context/CHANGELOG-AI.md`.
- Para Android: `./gradlew :app:assembleDebug`.
- Para testes unitários: `./gradlew :app:testDebugUnitTest`.
- Para temas: build do tema alterado em `cluster-widgets/<tema>`.

# Handoff

- Toda sessão deve terminar atualizando `.ai-context/HANDOFF.md`.
- Registrar arquivos alterados.
- Registrar riscos.
- Registrar próximos passos.
- Registrar comandos executados.
- Registrar testes feitos.

# Skills

- Usar skills em `.agents/skills` quando aplicável.
- Se uma tarefa envolver WebView, consultar `haval-webview-lifecycle` e `haval-kotlin-js-bridge`.
- Se envolver performance, consultar `haval-cluster-performance`.
- Se envolver QA, consultar `haval-qa-regression`.
- Se envolver documentação/handoff, consultar `haval-docs-handoff`.
- Se envolver display/cluster, consultar `haval-display-resolution-safety` e `haval-android-secondary-display`.
- Se envolver CarPlay no cluster 3, tela preta ou handoff display 0/3, consultar `haval-carplay-cluster-diagnostics`.

# Custom Agents

- Usar agents em `.codex/agents` quando a tarefa for complexa.
- Preferir agents read-only para análise, arquitetura e QA.
- Usar agent de implementação apenas após análise.
- `haval-architect` e `haval-code-mapper` devem preceder mudanças amplas.

---

# Agent Guidelines — Haval Impulse / Shisuku

This document defines core operating conventions and safety rules for AI coding agents (Antigravity, Gemini, Claude, Cursor, Copilot, etc.) working in this repository.

## 🚨 Interface Contract Safeguard (CRITICAL)

> [!CAUTION]
> **INTERFACE CONTRACT SAFEGUARD**
> Any agent attempting to modify or extend the interface contract between the Android backend host and frontend themes (bridge methods, telemetry keys, payload schemas, `theme.xml` specification, key event contracts, or status bar layout boundaries) **MUST** verify retro-compatibility first.
>
> If 100% backwards compatibility cannot be guaranteed for existing themes, the agent **MUST STOP** and explicitly ask the user if a new contract version (e.g. `v2.0`) needs to be created instead of modifying the existing `v1.0` contract.

---

## 🏗️ Architectural Separation: Bridge vs. Contract

When working on cluster widgets or Android bridge code, distinguish between:

1. **Bridge Version (`minBridgeVersion` / `CURRENT_BRIDGE_VERSION = "1.0.0"`)**:
   - Represents the native Java/Kotlin `@JavascriptInterface` API methods on `window.Android`.
   - Controls runtime feature support (`window.Android.subscribe()`, `setClusterBackground()`, etc.).
   - Managed in `CompatTranslationLayer.kt` using JavaScript polyfills for backwards compatibility.

2. **Contract Version (`contractVersion = "v1.0"`)**:
   - Represents the standardized telemetry key dictionary (`car.basic.vehicle_speed`), steering key event protocol, and `theme.xml` manifest structure.
   - Used by `ThemeManager.kt` and `TelasScreen.kt` to filter compatible themes.

---

## 🎨 Theme Building & Deployment Rules

1. **Target Contract Versions**:
   - `cluster-widgets/source/noncontract/` holds legacy, non-contract themes.
   - Active, supported themes are under contract version directories (e.g. `cluster-widgets/source/v1.0/`).
   - **Rule**: Unless explicitly specified otherwise by the user, agents **MUST** always target the latest contract version folder (`cluster-widgets/source/v1.0/`).

2. **Default Theme (Bundled in APK)**:
   - `cluster-widgets/source/v1.0/default/` is deployed **bundled directly inside the APK**.
   - Running `npm run build` in `cluster-widgets/source/v1.0/default/` automatically compiles `app.html` to `app/src/main/res/raw/app.html` and `theme.xml` to `app/src/main/assets/Default/theme.xml`.

3. **Dynamic Themes (e.g. Minimalist - Downloaded OTA)**:
   - Dynamic themes like `Minimalist` (`cluster-widgets/source/v1.0/minimalist/`) are downloaded on-demand by the app over-the-air from GitHub (`ThemeManager.kt`).
   - The target release branch is defined in app code (`ThemeManager.kt`, currently `codex/carplay-v301-stable-20260806` in the MarceloFP repository).
   - **Build output (unlike Default)**: `npm run build` in the theme source runs Parcel + `inline.js`, which:
     1. Produces a self-contained minified `dist/app.html` (CSS/JS inlined).
     2. Copies `dist/app.html` → `cluster-widgets/Themes/v1.0/<theme>/app.html`.
     3. Copies `theme.xml` → `cluster-widgets/Themes/v1.0/<theme>/theme.xml` (must travel with `app.html` — the updater compares `<version>` here).
   - Dynamic themes are **not** copied into the APK (`res/raw` / `assets/`). `Themes/` on the release branch is the only publish destination the in-app catalog crawls.
   - **Rule**: When requested to build or deploy a dynamic theme like Minimalist, agents **MUST**:
     1. Bump the theme version in `cluster-widgets/source/v1.0/minimalist/theme.xml`.
     2. Run `npm run build` in `cluster-widgets/source/v1.0/minimalist/`.
     3. **Commit and push** the updated source **and** the compiled `cluster-widgets/Themes/v1.0/minimalist/` assets (`app.html`, `theme.xml`, and any other package files) to the designated release branch (currently `feature/new-screen-enhancements-v7`) so it appears for in-app download and update detection.

---

## 📋 Pre-Flight Checklist Before Modifying Theme Interfaces

Before making any change to `ThemeBridgeImpl.kt`, `BridgeContractTranslator.kt`, `InstrumentProjector2.kt`, or `cluster-widgets/source/`:
- [ ] Check if the change introduces new keys or modifies existing key names.
- [ ] Verify if legacy themes or `v1.0` themes will continue to operate without missing data.
- [ ] Update `THEME_GUIDE.md` and `docs/architecture/themes-contract-v1.md` if new bridge methods or telemetry keys are added.
- [ ] If backwards compatibility is broken, **STOP** and ask the user to declare a new contract version (e.g. `v2.0`).
