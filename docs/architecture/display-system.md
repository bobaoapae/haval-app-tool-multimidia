# Display System

Atualizado em: 2026-09-24

## O Que Foi Identificado

O app usa displays Android secundários para renderizar camadas e apps:

- Display 0: central principal, 1920x720.
- Display 1: HDMI 1920x720 usado por `InstrumentProjector` como camada de wallpaper / refresh HUD.
- Display 2: HDMI 1280x720. Na central testada em 2026-06-01, não é o HUD físico.
- Display 3: cluster principal, HDMI 1920x720, usado por `InstrumentProjector2` e por apps/projeções.
- Display 4096: HUD físico, built-in 480x240, `uniqueId=local:5`, `FLAG_PRIVATE`.

`ProjectorManager` procura displays via `DisplayManager` e instancia presentations quando o display
alvo existe. Se um display ainda não existe, registra listener. Na central testada em 2026-06-01,
`DisplayManager.getDisplays()` expôs apenas 0, 1, 2 e 3; o HUD 4096 é privado e não ficou disponível
para `Presentation`.

Criação ordenada: HUD (D1) antes das máscaras (D3). Wallpaper D1 e insets nativos D3 compartilham
`ClusterBackgroundSync` — ver `docs/architecture/projector-flow.md`.

O shell/ActivityManager conseguiu iniciar Activities no HUD privado com `am start --display 4096`.
Isso deve ser tratado como caminho manual/experimental, não como fluxo automático do app, porque uma
Activity fullscreen pode cobrir/substituir visualmente a projeção nativa do HUD.

## Arquivos Relacionados

- `ProjectorManager.java`
- `BaseProjector.kt`
- `InstrumentProjector.kt`
- `InstrumentProjector2.kt`
- `ClusterBackgroundSync.kt`
- `DisplayAppLauncher.kt`
- `HudNavigationActivity.kt`

## Regras Observadas

- O cluster usa fullscreen lógico de 1920x720 em fluxos críticos.
- Apps em display 3 podem exigir bounds customizados, exceto CarPlay que deve ficar fullscreen físico.
- A WebView do cluster é transparente e pode coexistir com apps nativos.
- Native masks do D3 só sobem quando a theme WebView está live **e** (se still wallpaper for esperado)
  o D1 já pintou o wallpaper atual — senão os insets framing nada voltam.
- Quando uma janela externa no D0 ganha foco enquanto a bottom bar/dashboard estava ativo, o
  `BottomBarService` deve restaurar pelo menos a barra inferior e colapsar o dashboard fullscreen
  sem roubar foco da janela nativa. Isso cobre janelas como AC/HVAC, que podem esconder o dashboard
  e deixar o usuario sem caminho rapido de volta.
- Esse colapso por foco externo nao deve disparar para focos passivos/transitorios enquanto o
  dashboard esta aberto, como `com.android.systemui`, MediaCenter nativo, VehicleCenter, CarPlay e
  Android Auto. `com.beantechs.launcher` tambem e tratado como passivo enquanto o dashboard segue
  expandido, porque apareceu em eventos indiretos de D0 apos interacoes com a tela. Esses eventos
  podem ocorrer ao tocar em volume/midia e nao devem fechar o dashboard.
- Controles do dashboard que podem acionar UI nativa transitoria, como midia e volume, devem
  suprimir temporariamente o restore por foco externo para absorver eventos indiretos de
  `com.beantechs.launcher`/SystemUI sem prender o dashboard permanentemente.
- Quando Android Auto ou CarPlay e enviado para o display 3 e a bottom bar/dashboard ja estava
  ativo, o display 0 pode reabrir automaticamente o dashboard apos um pequeno atraso. Esse fluxo
  nao altera bounds do cluster e nao deve misturar logica Android Auto com CarPlay.
- O HUD físico usa 480x240. Qualquer overlay Android nele deve ser compacto, transparente, sem foco
  e sem toque.
- `screencap -d 5` captura camadas Android do HUD físico (`local:5`), mas não captura a projeção
  HUD nativa do carro. Quando não há janela Android no HUD, a captura pode sair preta mesmo com o
  HUD visível fisicamente.
- `screencap -d 4096` gerou arquivo vazio na central testada.
- `am start --display 4` falhou com `SecurityException`; referências antigas ao HUD como display 4
  devem ser consideradas obsoletas para esta central.

## Riscos

- Bounds incorretos podem deslocar ou ocultar projeções.
- `mAppBounds` pode diferir de `mBounds`.
- Remover stack errada pode tirar app do display 3.
- Reabrir dashboard no D0 apos handoff D3 deve respeitar o estado previo da bottom bar; se ela nao
  estava ativa, nao abrir dashboard automaticamente.
- Abrir o próprio Impulse no HUD 4096 substitui visualmente o HUD nativo e não é um teste válido de
  sobreposição.
- Como o HUD nativo não aparece em `screencap`, a coexistência overlay + HUD nativo precisa de
  validação visual/manual no carro.

## HUD 4096: Teste Físico (2026-10-14)

Feito no carro do dono com um APK descartável (`br.com.marcel.hudtest`, uma Activity com
`Theme.Translucent.NoTitleBar.Fullscreen` e `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`), aberto por
`am start --display 4096`. Confirmado visualmente pelo dono:

- **Fundo preto opaco:** a seta e o texto aparecem, mas o HUD nativo (velocidade etc.) some.
- **Fundo transparente:** a seta e o texto aparecem **por cima** do HUD nativo, que continua
  visível. É o modo a usar.
- **Área visível:** só a faixa `y=40..200` dos 480x240 (480x160, centralizada). 40 px de cima e
  40 px de baixo ficam fora da óptica. Bordas laterais não foram medidas (a confirmar).
- **Foco:** no primeiro teste o foco continuou no D0, mas depois o foco foi para o 4096 (AA pausado no D0). Ver "Overlay do Impulse no HUD" abaixo.
- O `pm install` de um pacote novo é barrado pelo Bean (`beantechs disallow apk`), mas
  `pm install -i com.android.vending -r -t <apk>` pelo shell passou.

### Overlay do Impulse no HUD (`hud/HudOverlay*`)

- Opt-in em Ajustes > "HUD do Impulse" (`hudOverlayEnabled`, desligado por padrão).
- Desenha só no canto livre (x 10..168, y 58..195): barra de potência/regeneração sempre, barra de
  RPM só com o motor ligado (`haval.power.ice`), TBT do Android Auto enquanto houver guia.
- **Uma Activity parada no 4096 dá ANR.** Ela vira o app focado com uma janela não-focável, e a
  próxima tecla espera "no window has focus" por 5 s (dropbox `data_app_anr`, 2026-10-14). Por
  isso a `HudOverlayActivity` só abre a porta: o controller adiciona uma janela
  `TYPE_APPLICATION_OVERLAY` no Display dela (o WindowManager aceita janelas do nosso uid num
  display privado enquanto há uma Activity nossa nele) e a Activity termina na hora. O overlay
  continua depois que ela sai, e o foco fica no D0 sem precisar devolver.
- Se o overlay falhar, a Activity hospeda a view e o controller devolve o foco ao topo do D0
  (`moveTaskToFront`, permissão `REORDER_TASKS`).
- O HUD congela o último quadro quando a janela some: antes de remover, pinta um quadro vazio.
- Escalas das barras (100 kW tração, 50 kW regeneração, 5000 rpm): a confirmar no carro.

## A Confirmar

- Mapeamento completo dos displays em todas as versões da central.
- Comportamento do display 1 além da camada de refresh descrita no código.
- Limites laterais visíveis do HUD 4096.
- Se o foco continua no D0 quando o launch acontece com teclado/IME ativo ou durante CarPlay/AA.
