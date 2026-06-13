# Síntese CarPlay D3 Blackout (HVAC/AVM) — atualizada 2026-05-27 00:30

Documento conjunto codex + Claude. Reescrito após o codex investigar mais tarde em 2026-05-26 22:20–22:29 e identificar a **causa cadeia abaixo** que muda parte do plano.

## 1. Bug — definição refinada

CarPlay no cluster 3 fica preto durante HVAC/AVM e **volta com frame sujo** quando o painel fecha. Activity e Surface seguem vivas, fullscreen `[0,0][1920,720]`. Confirmado em logs físicos (codex 22:20:29) que a sequência exata é:

1. AC fecha (ou câmera fecha)
2. **Host nativo CarPlay (vendor) executa `CarPlayManager: surface hide from ShowProjection`**
3. Surface é destruída: `jsurface is NULL`, `BufferQueue has been abandoned` para `SurfaceView - com.ts.carplay.app/...CarPlayDisplayActivity`
4. ~1 segundo depois, o **watchdog do nosso app** dispara `CARPLAY_CLUSTER_WATCHDOG_NO_TASK` ou `CARPLAY_CLUSTER_WATCHDOG_DIRECT`
5. O watchdog chama `am start --display 3 ... CarPlayDisplayActivity` + `REFRESH_RENDER` + `view_state foreground` + `VIDEO_FOCUS_CHANGE`
6. Activity nova sobe com Surface "suja" porque decoder do vendor ainda está em estado instável pós-destruição

**Insight crítico:** o "frame sujo" não é só o decoder pausado — é a **interação tóxica entre destruição da Surface pelo vendor e recriação acelerada da Activity pelo nosso watchdog**.

## 2. Diagnóstico convergente (codex + Claude)

### Camada 1: o que faz o vendor service pausar/destruir Surface (Claude 2026-05-26)
`build_carplay/ts-service/smali/com/ts/carplay/lib/resourcemanager/ScreenResourceManager.smali:1201-1327`, método `accessoryScreenChange(I priority, ...)`. Sparse-switch:
- priority 1 (CarPlay) → `InCarPlay` state ✅
- priority 6 (HVAC) → `InBorrowUser` ❌ pausa decoder
- priority 7 (backupCam) → `InBorrowNever` ❌ pausa decoder

### Camada 2: o que faz a Surface ficar suja ao recuperar (Codex 2026-05-26 22:20)
- Vendor chama `CarPlayManager: surface hide from ShowProjection` no fechamento do painel
- `BufferQueue abandoned` no `SurfaceView`
- Watchdog do app recria a Activity prematuramente → frame sujo

**A causa raiz envolve as duas camadas. Atacar uma só não é suficiente.**

## 3. Tentativas anteriores (consolidado codex 2026-05-24 → 2026-05-26 + Claude noite)

Todas testadas em hardware. Status pós-rodada:

| Data/hora | Estratégia | Resultado |
|---|---|---|
| 2026-05-25 14:10 | Frontend não força `projection`; remove dimming | Preto persistiu |
| 2026-05-25 19:21 | Guard conservador (`VIDEO_FOCUS_CHANGE` leve) | Preto persistiu |
| 2026-05-25 22:00 | Verify-only + double-check; force-push state; CSS transparente | Preto persistiu |
| 2026-05-26 09:50 | Suprimir `showScreen('aircon')` no WebView | Preto persistiu |
| 2026-05-26 10:08 | `RENDER_REFRESH_ONLY` tardio (4000ms) | Preto persistiu |
| 2026-05-26 10:17 | Remove `LAYER_TYPE_SOFTWARE` da WebView | Preto persistiu |
| 2026-05-26 10:26 | Bypass `Presentation` com `windowAlpha=0.0` | Preto persistiu |
| 2026-05-26 10:45 | `projectionNativePanelFallbackActive` (render Normal) | REGRESSÃO |
| 2026-05-26 12:07 | Reverte fallback visual | Restaura baseline |
| 2026-05-26 20:54 | Fallback experimental por foreground não-passivo do D0 | Não validado |
| 2026-05-26 22:20 (codex) | Remove `VIDEO_FOCUS_CHANGE`/`REFRESH_RENDER` específicos de HVAC + hold | Falhou: AC fechou, perdeu Mapa, voltou sujo |
| 2026-05-26 22:25 (codex) | Desabilita `WATCHDOG_NO_TASK` auto-start | Falhou: `WATCHDOG_DIRECT` ainda restaurou e voltou sujo |
| **2026-05-26 22:29 (codex)** | **Desabilita AMBOS `WATCHDOG_NO_TASK` e `WATCHDOG_DIRECT`** | **Aguardando validação física** |
| **2026-05-26 noite (Claude)** | Fix duplo click + grace period em `sendToDisplay` | Resolveu handoff 0→3 sujo; não afeta HVAC/AVM |

**Variantes patchadas `TsCarPlayApp.apk`:**
- MD5 `3ce0a58270607f0e854638cfab809a39` → crashou `IllegalAccessError`
- MD5 `9a64672d3f4f69376b8a24c55431b5e9` → abriu, voltou a bounds parciais
- v2.4 atual MD5 `551ee6b0bb31a6c43847a8452ea527cb` → marcado UNSAFE (frame branco D0); Claude reativou em 23:40 → regressão voltou

## 4. Plano consolidado para 2026-05-27 (revisado com descoberta codex)

### Fase 0 — limpeza inicial (15 min)
1. Reboot headunit se ainda estiver com patch v2.4 montado da experiência de Claude.
2. Desinstalar patch v2.4 pela UI ("Uninstall CarPlay patch").
3. Confirmar baseline: APK stock `6c4815c20732b3643b008c85063fead6`, `persist.haval.carplay.video.height=720`.
4. **Confirmar build instalada `lastUpdateTime=2026-05-26 22:29:23`** (a do codex sem watchdog auto-restore). Se não estiver, redeploy.

### Fase 1 — sanity check rápido da build 22:29:23 (15 min) — NÃO É APOSTA PRINCIPAL

**Correção de prioridade (codex 2026-05-27):** o problema já evoluiu para outro estágio. Não está mais só "voltando do D3 para D0" — agora o vendor pausa/destrói Surface e CarPlay volta sujo. A build 22:29:23 só desabilita restore automático do watchdog; **não toca a causa raiz no vendor**. Por isso este teste é validação rápida, não aposta.

1. CarPlay no cluster 3 limpo.
2. Acionar AC físico, aguardar fechar.
3. Acionar câmera, aguardar fechar.
4. Se continuar preto/sujo nos dois casos: **não insistir**, ir direto para Fase 2.

**Resultados possíveis:**

**1.A — Build 22:29:23 resolve totalmente (improvável dada a evolução do bug):**
- Confirmar com Claude's fix de duplo click instalada (não conflitam).
- Commitar codex 22:29:23 + Claude fix de duplo click juntos.

**1.B (esperado) — Ainda volta sujo:**
- Coletar logcat curto sem reiniciar e procurar quem ainda recria Activity:
  - `startCarPlayOnDisplay`
  - `am start --display 3 ... CarPlayDisplayActivity`
  - `REFRESH_RENDER`
  - `VIDEO_FOCUS_CHANGE`
- Pular para Fase 2.

**1.C — Cluster fica permanentemente sem CarPlay:**
- Zombie task conhecida (Claude viu em sessão anterior).
- Reconectar cabo USB resolve. Aceitável como trade-off temporário enquanto Fase 3 não está pronta.

### Fase 2 — auditoria smali OBRIGATÓRIA antes de qualquer patch (45 min) — GATING

**Correção do codex (2026-05-27):** esta fase deve acontecer **antes de qualquer teste físico demorado de patch**. Sem saber o que `InBorrowUser` e `InBorrowNever` fazem além de hide de Surface, patchar o sparse-switch pode pular efeitos necessários de áudio/foco/recurso.

Ler em ordem:
1. `build_carplay/ts-service/smali/com/ts/carplay/lib/resourcemanager/ScreenResourceManager$ScreenStateMachine$InBorrowUser.smali`
2. `ScreenResourceManager$ScreenStateMachine$InBorrowNever.smali`
3. `ScreenResourceManager$ScreenStateMachine$InCarPlay.smali` (referência: o estado bom)

Para cada um, mapear:
- O método `enter()` faz o quê?
- Chama `surfaceHide`/`ShowProjection`? (em qual ponto)
- Chama release de áudio? (procurar `AudioResource`, `releaseFocus`)
- Notifica algum listener externo? (callbacks `ICarPlayResource*`)
- Atualiza algum estado global que outros componentes leem?

Critério de saída da Fase 2:
- **Se `InBorrowUser`/`InBorrowNever` só fazem `surfaceHide` + log:** patch do sparse-switch é seguro, ir para Fase 3.
- **Se fazem release de áudio que CarPlay precisa retomar:** patch direto no sparse-switch quebra áudio. Pular para Fase 4 (patch direto em `InBorrowUser.smali` removendo só `surfaceHide`).
- **Se fazem callback que algum componente espera:** documentar e decidir caso a caso.

### Fase 3 — patch cirúrgico no vendor em DUAS etapas separadas (3-4h, condicionada a Fase 2)

**Recomendação do codex (00:11):** não fazer os dois patches juntos. HVAC e câmera têm risco assimétrico — câmera (`0x7`) é mais sensível por safety. Quebrar `0x7` errado pode afetar AVM/política de foco da central.

**Fase 3.A — HVAC-only (1.5h):** primeiro patch isolado em `ScreenResourceManager.smali:1320-1326`:
```
0x6 -> :sswitch_2  (HVAC tratado como CarPlay; era :sswitch_1)
0x7 -> :sswitch_0  (MANTÉM original: câmera → InBorrowNever)
```
Validar:
- AC físico deixa de sujar/pretear cluster 3?
- Áudio do CarPlay continua funcionando durante AC?
- Câmera/AVM continua se comportando igual antes (ainda preto, esperado nesta fase)?

Se 3.A passou e nada regrediu, prosseguir:

**Fase 3.B — adicionar câmera (1.5h):** segundo patch:
```
0x7 -> :sswitch_2  (backupCam tratado como CarPlay; era :sswitch_0)
```
Validar:
- Câmera/AVM deixa de pretear cluster 3?
- AVM continua aparecendo no display 0 normalmente?
- Backup camera continua acionando ao engatar ré (safety crítica)?
- Sem regressão em HVAC já corrigido?

**Procedimento técnico comum às duas fases:**
1. Extrair `TsCarPlayService.apk` (vendor) da central — caminho `/vendor/app/TsCarPlayService/TsCarPlayService.apk`.
2. apktool desmonta, patch smali, rebuild, sign.
3. Estender `CarPlayPatchManager.kt`:
   - Manter patch separado do app principal (recomendação codex).
   - Novo path constant `VENDOR_SERVICE_APK_PATH = "/vendor/app/TsCarPlayService/TsCarPlayService.apk"`.
   - Novo método `applyVendorMount()` independente do `applyMounts()` atual.
   - Documentar claramente que é patch em **vendor service**, não no app CarPlay visual.
4. **NÃO usar `am force-stop com.ts.carplay`** (vendor) — lição de Claude noite confirma estado podre irrecuperável.
5. Reboot do headunit obrigatório para vendor recarregar (PID do `com.ts.carplay` precisa nascer novo lendo o APK montado).
6. Manter rollback stock pronto: APK original em `/data/local/tmp/carplay_patches/TsCarPlayService_stock.apk` antes do mount.

### Fase 4 — fallback se Fase 3.A quebrar (1h)
Se patchar `0x6` quebrar áudio do CarPlay durante HVAC ou regredir de outra forma:
- Desfazer mount imediatamente via UI.
- Tentar patchar `InBorrowUser.smali` para que ele **não chame `surfaceHide`** mas mantenha outros side-effects (release de áudio, callbacks ao cliente, etc).
- Mais cirúrgico ainda: trocar só a chamada `ShowProjection` por no-op, deixando state machine entrar normalmente em `InBorrowUser`.

### Fase 5 — alternativa de última instância: AapActivity
Codex notou que o baseline `d032e4c` (que parecia funcionar em 21/05) **apontava `DisplayAppConfig` para `com.ts.carplay.app.display.AapActivity`** (Activity antiga) em vez da atual `com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity`. Esse é uma diferença histórica relevante mas o contrato proíbe usar `AapActivity`. Reservar como último recurso experimental controlado.

## 5. Convergências e divergências codex × Claude

### Convergências
1. Causa é no vendor service `com.ts.carplay`, não no app cliente.
2. Tentativas em CSS/WebView/Presentation/overlay foram esgotadas.
3. Patches no APK cliente (`TsCarPlayApp.apk`) sempre regridem em frame branco.
4. Estratégia futura deve ser cirúrgica (poucas linhas) e reversível.

### Divergências (complementares, não conflitantes)
- **Claude** focou em onde a decisão de pausa é tomada (`ScreenResourceManager.accessoryScreenChange`).
- **Codex** focou em onde a Surface é destruída (`ShowProjection` → `surfaceHide`) e em como o nosso watchdog amplifica o bug ao recriar Activity cedo demais.
- **Síntese:** se o vendor não pausar (Claude's fix), `ShowProjection` não chama `surfaceHide`, e o watchdog não tem oportunidade de criar Activity suja. As duas camadas estão ligadas em cadeia.

### Lições novas (codex 22:20–22:29)
- O **watchdog de restore automático é parte do bug**, não da solução. Ele foi criado para impedir CarPlay preso no D0, mas durante HVAC/AVM ele recria Surface em momento ruim.
- A build `22:29:23` desabilita os dois caminhos (`NO_TASK` e `DIRECT`). Trade-off aceito: cluster pode ficar sem CarPlay temporariamente se vendor remover task; usuário precisa reenviar manualmente.

### Validação cruzada da hipótese cirúrgica (codex 2026-05-27 00:11)

O codex revisou a proposta de Claude do patch sparse-switch e confirmou tecnicamente:

- **Confirmado por código:** o sparse-switch existe em `ScreenResourceManager.smali:1201`. Mapeamento bate exatamente: `0x1→InCarPlay`, `0x6→InBorrowUser`, `0x7→InBorrowNever`.
- **Confirmado o encadeamento:** quando AC/câmera entram, vendor entende que precisa "emprestar" tela. Esse borrow casa com os logs `surface hide from ShowProjection`/`jsurface NULL`/`BufferQueue abandoned`.
- **Confirmado por comparação:** Android Auto não sofre porque o fluxo nativo dele provavelmente não entra nessa mesma state machine.
- **Confirmado o alvo:** patch deve ser em `TsCarPlayService.apk` (vendor service), não em `TsCarPlayApp.apk` visual. Explica por que todos os patches anteriores no APK visual falharam.

**Ressalva técnica do codex (incorporada na Fase 3):**
> "Eu não chamaria ainda de patch seguro de 3 bytes. Pode até ser pequeno no DEX, mas semanticamente ele muda uma regra de prioridade do vendor. O risco maior é no 0x7, porque câmera pode ter política de prioridade/safety mais rígida."

Por isso a Fase 3 foi dividida em 3.A (HVAC-only) e 3.B (câmera), com validação física entre elas. Câmera é último porque envolve safety (backup camera).

## 6. Estado de código atual (não-commited)

**Working tree (Claude 2026-05-26 noite):**
- `DisplayAppLauncher.kt`: cooldown + Mutex em `sendToDisplay`; grace period em `rememberObservedCarPlayDisplayTarget`
- `BottomBarUI.kt`: cooldown 1500ms no botão seta-esquerda
- `CarPlayPatchManager.kt`: `PATCH_RUNTIME_ENABLED=false` (revertido)

**Working tree (codex 22:29:23, pré-existente):**
- `DisplayAppLauncher.kt`: watchdog `NO_TASK` e `DIRECT` não fazem mais auto-restore
- `restoreOrRefreshCarPlayClusterContract` aborta se `carPlayNativePanelVisualRestoreHoldUntil` ativo
- `ProjectorManager.java`, `ServiceManager.java`, `InstrumentProjector2.kt` — mudanças correlatas

**Importante**: ambas as mudanças estão no mesmo arquivo (`DisplayAppLauncher.kt`) e não conflitam funcionalmente — codex mexe em watchdog automático, Claude mexe em `sendToDisplay` (clicks do usuário). Mas precisam ser revisadas juntas antes de commit.

## 7. Tempo total estimado

- Fase 0: 15 min
- Fase 1 (validar 22:29:23): 30 min — pode resolver o caso!
- Fases 2-4 só se Fase 1 falhar: +4-6h adicionais
- **Cenário otimista (Fase 1 resolve): 45 min de sessão amanhã**
- Cenário realista: 4-6h

## 8. Arquivos relevantes

**Documentação do codex (ler em ordem):**
1. `.ai-context/HANDOFF.md` (seção "Atualizacao Online 2026-05-26 22:29")
2. `.ai-context/KNOWN-ISSUES.md` (seção "2026-05-27 - AC/HVAC destroi Surface CarPlay")
3. `.ai-context/CURRENT-STATE.md` (seção "Atualizacao 2026-05-27")
4. `.ai-context/RISKS.md` (seção "2026-05-27 - Risco confirmado")
5. `.ai-context/CHANGELOG-AI.md`
6. `docs/architecture/projection-native-patch-strategy.md`

**Logs salvos pelo codex:**
- `artifacts/headunit/ac-regression-20260526-222206/` (build 22:20:29 falha)
- `artifacts/headunit/ac-regression-20260526-222706/` (build 22:25:17 falha)

**Memória do Claude:**
- `~/.claude/projects/-Users-marcelofp-Projetos-haval-app-tool-multimidia/memory/`
  - `project_carplay_dossie_2026_05_27.md` (versão anterior, este documento é a fonte canônica agora)
  - `feedback_carplay_force_stop_vendor.md`

**Smali do vendor:**
- `build_carplay/ts-service/smali/com/ts/carplay/lib/resourcemanager/ScreenResourceManager.smali`
- `ScreenResourceManager$ScreenStateMachine$InBorrowUser.smali`
- `ScreenResourceManager$ScreenStateMachine$InBorrowNever.smali`

Bom dia amanhã. Comece por Fase 1 — pode ser que já esteja resolvido.

## 9. Atualizacao codex 2026-05-28 - HVAC-only aplicado

Resultado da retomada:

- Patch vendor-service HVAC-only foi gerado, assinado e instalado na central.
- Alvo ativo: `/vendor/app/TsCarPlayService/TsCarPlayService.apk`.
- MD5 ativo: `4a76e74c5f9fc119287c5cc0f823856a`.
- `TsCarPlayApp.apk` visual permaneceu stock: `6c4815c20732b3643b008c85063fead6`.
- Camera `0x7` nao foi patchada; apenas HVAC `0x6`.

Deploy/ambiente:

- IP final da central: `192.168.15.100`.
- Host local HTTP: `192.168.15.71`.
- APK principal instalado: `lastUpdateTime=2026-05-28 21:10:25`.
- Central reiniciada depois da instalacao.

Validacao remota:

- CarPlay aberto no display 3: stack `2`, task `10057`, bounds `[0,0][1920,720]`.
- Abrir Settings no display 0 manteve CarPlay no display 3, sem duplicata no display 0.
- Abrir HVAC no display 0 gerou `priority: 6 borrowId: uiNotification` e manteve CarPlay no display 3.
- Logs nao mostraram `surface hide`, `ShowProjection`, `InBorrowUser`, `jsurface is NULL` nem `BufferQueue abandoned`.
- Guard do app ficou em verify-only e nao enviou `VIDEO_FOCUS_CHANGE` para eventos de D0.

Pendente:

- Teste manual de camera/AVM fisica.
- Se camera falhar, coletar log antes de qualquer restart e tratar prioridade `0x7` como fase separada.
